package com.example.service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppRepository
import com.example.data.BoostEngine
import com.example.data.PermissionManagerHelper
import com.example.data.PrivilegeDetector
import com.example.data.SettingsDataStore
import com.example.data.SystemTelemetryRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.TelemetrySnapshot
import com.example.domain.model.ThermalLevel
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class FloatingHudMode {
    ORB,
    CORNER_DOCKED,
    EXPANDED
}

data class ActiveSessionState(
    val isRunning: Boolean = false,
    val activePackage: String? = null,
    val activeGameName: String? = null,
    val overlayVisible: Boolean = false,
    val hudMode: FloatingHudMode = FloatingHudMode.ORB,
    val dndActive: Boolean = false,
    val proAssistantActive: Boolean = false,
    val headshotAssistActive: Boolean = false,
    val headshotAnchorLocked: Boolean = false
)

object ActiveSessionTracker {
    private val _state = MutableStateFlow(ActiveSessionState())
    val state: StateFlow<ActiveSessionState> = _state.asStateFlow()

    fun update(transform: (ActiveSessionState) -> ActiveSessionState) {
        _state.value = transform(_state.value)
    }
}

/**
 * Foreground service (`specialUse|mediaProjection`) managing:
 * 1. Real-time hardware telemetry & automatic foreground game exit restoration.
 * 2. Translucent Floating Orb -> Corner-Docked Ghost Circle -> Expanded Red Magic Game Space HUD.
 * 3. Optional MediaProjection + ML Kit Vision "Pro Assistant" (target circles/boxes)
 *    & "Headshot Assist" (3-tap draggable calibration + touch-pass-through anchor + Head Lock reticle).
 */
class GameMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var telemetryJob: Job? = null
    private var foregroundWatchJob: Job? = null

    private lateinit var telemetryRepo: SystemTelemetryRepository
    private lateinit var permissionHelper: PermissionManagerHelper
    private lateinit var dataStore: SettingsDataStore
    private lateinit var boostEngine: BoostEngine

    private var windowManager: WindowManager? = null
    private var screenWidth = 1080
    private var screenHeight = 1920

    // Main floating orb / expanded HUD window
    private var hudContainer: FrameLayout? = null
    private var hudParams: WindowManager.LayoutParams? = null
    private var orbView: LinearLayout? = null
    private var orbFpsText: TextView? = null
    private var expandedPanel: LinearLayout? = null
    private var hudMode: FloatingHudMode = FloatingHudMode.ORB
    private var currentKeepScreenOn = false

    // Expanded panel live labels
    private var fpsTextView: TextView? = null
    private var tempTextView: TextView? = null
    private var ramTextView: TextView? = null
    private var batteryTextView: TextView? = null
    private var thermalTextView: TextView? = null
    private var dndChipView: TextView? = null
    private var proAssistBtnView: TextView? = null
    private var headshotBtnView: TextView? = null
    private var resetAnchorBtnView: TextView? = null

    // Full-screen click-through target/headshot reticle overlay
    private var targetOverlayView: TargetOverlayView? = null
    private var screenVisionAnalyzer: ScreenVisionAnalyzer? = null
    private var hasActiveMediaProjection = false
    private var pendingVisionFeature: String? = null
    private var proAssistantOn = false
    private var headshotAssistOn = false

    // Headshot 3-tap calibration / locked pass-through widget
    private var headshotAnchorContainer: FrameLayout? = null
    private var headshotAnchorParams: WindowManager.LayoutParams? = null
    private var headshotPromptOverlay: LinearLayout? = null
    private var headshotPromptParams: WindowManager.LayoutParams? = null
    private var headshotTapBadge: TextView? = null
    private var headshotConfirmBtn: TextView? = null
    private var headshotHintText: TextView? = null
    private var calibrationTapCount = 0
    private var isAnchorLocked = false

    private var localizedContext: Context? = null

    override fun onCreate() {
        super.onCreate()
        telemetryRepo = SystemTelemetryRepository(applicationContext)
        permissionHelper = PermissionManagerHelper(applicationContext)
        dataStore = SettingsDataStore(applicationContext)
        val appRepo = AppRepository(applicationContext)
        val privilegeDetector = PrivilegeDetector(applicationContext)
        boostEngine = BoostEngine(applicationContext, dataStore, permissionHelper, appRepo, privilegeDetector)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        refreshScreenBounds()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAndRestore()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_DND -> {
                toggleDndFromOverlay()
                return START_STICKY
            }
            ACTION_MEDIA_PROJECTION_RESULT -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val feature = intent.getStringExtra(EXTRA_VISION_FEATURE)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startMediaProjectionSession(resultCode, data, feature)
                } else {
                    pendingVisionFeature = null
                    updateVisionButtonsUi()
                }
                return START_STICKY
            }
            ACTION_MEDIA_PROJECTION_DENIED -> {
                pendingVisionFeature = null
                updateVisionButtonsUi()
                return START_STICKY
            }
            ACTION_START, null -> {
                val pkg = intent?.getStringExtra(EXTRA_PACKAGE)
                val gameName = intent?.getStringExtra(EXTRA_GAME_NAME) ?: "Apex Telemetry"
                val showOverlay = intent?.getBooleanExtra(EXTRA_SHOW_OVERLAY, false) ?: false
                val keepScreenOn = intent?.getBooleanExtra(EXTRA_KEEP_SCREEN_ON, false) ?: false
                val dndActive = intent?.getBooleanExtra(EXTRA_DND_ACTIVE, false) ?: false
                currentKeepScreenOn = keepScreenOn

                // CRITICAL FIX: Start foreground synchronously within onStartCommand (Android 12+/14+ requirement)
                createNotificationChannel(this)
                startForegroundCompat(
                    notification = buildNotification(this, gameName),
                    includeMediaProjection = hasActiveMediaProjection
                )

                ActiveSessionTracker.update {
                    it.copy(
                        isRunning = true,
                        activePackage = pkg,
                        activeGameName = gameName,
                        overlayVisible = showOverlay && Settings.canDrawOverlays(this),
                        hudMode = hudMode,
                        dndActive = dndActive
                    )
                }

                serviceScope.launch {
                    val prefs = runCatching { dataStore.stateFlow.first() }.getOrNull()
                    val lang = prefs?.language ?: AppLanguage.SYSTEM
                    localizedContext = buildLocalizedContext(lang)

                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.notify(NOTIFICATION_ID, buildNotification(localizedContext ?: this@GameMonitorService, gameName))

                    if (showOverlay && Settings.canDrawOverlays(this@GameMonitorService)) {
                        attachOrUpdateHudOverlay(keepScreenOn, dndActive)
                        // Restore locked headshot anchor automatically from normalized or pixel coordinates
                        val profile = prefs?.savedGames?.firstOrNull { it.packageName == pkg }
                        if (profile != null && profile.headshotAnchorLocked &&
                            profile.headshotAnchorX != null && profile.headshotAnchorY != null
                        ) {
                            isAnchorLocked = true
                            val restoredX = if (profile.headshotAnchorX <= 1.0f) {
                                (profile.headshotAnchorX * screenWidth).toInt()
                            } else {
                                profile.headshotAnchorX.toInt()
                            }
                            val restoredY = if (profile.headshotAnchorY <= 1.0f) {
                                (profile.headshotAnchorY * screenHeight).toInt()
                            } else {
                                profile.headshotAnchorY.toInt()
                            }
                            attachHeadshotAnchorWidget(
                                initialX = restoredX,
                                initialY = restoredY,
                                locked = true
                            )
                            ActiveSessionTracker.update { it.copy(headshotAnchorLocked = true) }
                        }
                    } else {
                        detachAllOverlays()
                    }

                    startTelemetryCollection()
                    if (!pkg.isNullOrBlank() && permissionHelper.hasUsageStatsPermission()) {
                        startForegroundAppWatcher(pkg)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVisionAnalyzer()
        detachAllOverlays()
        serviceScope.cancel()
        ActiveSessionTracker.update { ActiveSessionState() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshScreenBounds() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels.coerceAtLeast(720)
        screenHeight = metrics.heightPixels.coerceAtLeast(1080)
    }

    private fun startForegroundCompat(notification: Notification, includeMediaProjection: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (includeMediaProjection) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTelemetryCollection() {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            telemetryRepo.observeTelemetry(1100L).collect { snap ->
                updateOverlayViews(snap)
            }
        }
    }

    private fun startForegroundAppWatcher(targetPkg: String) {
        foregroundWatchJob?.cancel()
        foregroundWatchJob = serviceScope.launch(Dispatchers.IO) {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@launch
            var gameSeenInForeground = false
            var outsideGameTicks = 0

            while (true) {
                delay(2500L)
                val now = System.currentTimeMillis()
                val events = usm.queryEvents(now - 6000L, now)
                val event = UsageEvents.Event()
                var lastResumedPkg: String? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        lastResumedPkg = event.packageName
                    }
                }

                if (lastResumedPkg == targetPkg) {
                    gameSeenInForeground = true
                    outsideGameTicks = 0
                } else if (gameSeenInForeground && lastResumedPkg != null && lastResumedPkg != packageName) {
                    outsideGameTicks++
                    if (outsideGameTicks >= 4) {
                        stopAndRestore()
                        break
                    }
                }
            }
        }
    }

    private fun toggleDndFromOverlay() {
        if (!permissionHelper.hasDndAccess()) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val currentlyOn = ActiveSessionTracker.state.value.dndActive
        runCatching {
            if (currentlyOn) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            val nextState = !currentlyOn
            ActiveSessionTracker.update { it.copy(dndActive = nextState) }
            updateDndChip(nextState)
        }
    }

    private fun stopAndRestore() {
        serviceScope.launch {
            stopVisionAnalyzer()
            boostEngine.restoreSystemSettings()
            detachAllOverlays()
            ActiveSessionTracker.update { ActiveSessionState() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // -------------------------------------------------------------------------
    // Floating Orb / Translucent Corner Dock / Expanded Red Magic HUD
    // -------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOrUpdateHudOverlay(keepScreenOn: Boolean, dndActive: Boolean) {
        refreshScreenBounds()
        if (hudContainer != null && hudParams != null) {
            val params = hudParams!!
            params.flags = buildOverlayFlags(keepScreenOn, touchable = true)
            runCatching { windowManager?.updateViewLayout(hudContainer, params) }
            updateDndChip(dndActive)
            return
        }

        val ctx = localizedContext ?: this
        val root = FrameLayout(ctx)

        // 1. Floating Translucent Circle (ORB & CORNER_DOCKED)
        val orb = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val sizePx = dp(56)
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC0A0A0F"))
                setStroke(dp(2), Color.parseColor("#FF1E3C"))
            }
        }

        val fpsOrbTv = TextView(ctx).apply {
            text = "--\nHZ"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setLineSpacing(0f, 0.9f)
        }
        orbFpsText = fpsOrbTv
        orb.addView(fpsOrbTv)
        orbView = orb

        // 2. Expanded Red Magic HUD Panel
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            visibility = View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#F00A0A0F"))
                setStroke(dp(1), Color.parseColor("#FF1E3C"))
                cornerRadii = floatArrayOf(
                    dpF(16f), dpF(16f),
                    dpF(4f), dpF(4f),
                    dpF(16f), dpF(16f),
                    dpF(4f), dpF(4f)
                )
            }
        }
        expandedPanel = panel

        // Header row: Game Name + Hide-to-Corner button + Exit button
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(ctx).apply {
            text = (ActiveSessionTracker.state.value.activeGameName ?: "APEX BOOST")
                .take(15).uppercase(Locale.getDefault())
            setTextColor(Color.parseColor("#F4F6FA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val hideBtn = makeSmallChipButton(
            ctx = ctx,
            label = ctx.getString(R.string.overlay_hide_corner),
            textColorHex = "#00E5FF",
            bgHex = "#2200E5FF",
            strokeHex = "#00E5FF"
        ) {
            dockOrbToNearestCorner()
        }

        val closeBtn = makeSmallChipButton(
            ctx = ctx,
            label = ctx.getString(R.string.overlay_exit),
            textColorHex = "#FF4D67",
            bgHex = "#33FF1E3C",
            strokeHex = "#FF1E3C"
        ) {
            stopAndRestore()
        }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(6)
        }

        headerRow.addView(titleTv)
        headerRow.addView(hideBtn)
        headerRow.addView(closeBtn)
        panel.addView(headerRow)

        // Live Telemetry Grid
        val metricsRow1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(4))
        }
        fpsTextView = makeMetricLabel(ctx, "#00E5FF").also { metricsRow1.addView(it) }
        tempTextView = makeMetricLabel(ctx, "#FFB300").also { metricsRow1.addView(it) }
        ramTextView = makeMetricLabel(ctx, "#00E676").also { metricsRow1.addView(it) }
        panel.addView(metricsRow1)

        val metricsRow2 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        batteryTextView = makeMetricLabel(ctx, "#F4F6FA").also { metricsRow2.addView(it) }
        thermalTextView = makeMetricLabel(ctx, "#FF4D67").also { metricsRow2.addView(it) }
        val dndChip = makeSmallChipButton(
            ctx = ctx,
            label = "",
            textColorHex = "#00E5FF",
            bgHex = "#2200E5FF",
            strokeHex = "#00E5FF"
        ) {
            toggleDndFromOverlay()
        }
        dndChipView = dndChip
        updateDndChip(dndActive)
        metricsRow2.addView(dndChip)
        panel.addView(metricsRow2)

        // Tactical Buttons Row: "مساعد المحترف" & "الهيد شوت"
        val tacticalRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val proBtn = makeTacticalActionButton(ctx, ctx.getString(R.string.overlay_pro_assistant)) {
            onProAssistantTapped()
        }
        proAssistBtnView = proBtn

        val hsBtn = makeTacticalActionButton(ctx, ctx.getString(R.string.overlay_headshot)) {
            onHeadshotTapped()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dp(8) }
        }
        headshotBtnView = hsBtn

        tacticalRow.addView(proBtn)
        tacticalRow.addView(hsBtn)
        panel.addView(tacticalRow)

        // Reset Anchor row (visible when headshot anchor is locked or calibrated)
        val resetRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val visionNoteTv = TextView(ctx).apply {
            text = ctx.getString(R.string.overlay_vision_note)
            setTextColor(Color.parseColor("#8892A6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val resetBtn = makeSmallChipButton(
            ctx = ctx,
            label = ctx.getString(R.string.overlay_headshot_reset),
            textColorHex = "#FFB300",
            bgHex = "#22FFB300",
            strokeHex = "#FFB300"
        ) {
            resetHeadshotCalibration()
        }.apply {
            visibility = if (isAnchorLocked) View.VISIBLE else View.GONE
        }
        resetAnchorBtnView = resetBtn
        resetRow.addView(visionNoteTv)
        resetRow.addView(resetBtn)
        panel.addView(resetRow)

        updateVisionButtonsUi()

        root.addView(orb)
        root.addView(panel)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            buildOverlayFlags(keepScreenOn, touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(140)
        }

        // Smooth drag & tap behavior for the orb and expanded panel
        var initX = 0
        var initY = 0
        var downX = 0f
        var downY = 0f
        var moved = false

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        moved = true
                        params.x = (initX + dx).coerceIn(0, (screenWidth - dp(56)).coerceAtLeast(0))
                        params.y = (initY + dy).coerceIn(dp(20), (screenHeight - dp(80)).coerceAtLeast(0))
                        runCatching { windowManager?.updateViewLayout(root, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        when (hudMode) {
                            FloatingHudMode.ORB, FloatingHudMode.CORNER_DOCKED -> setHudMode(FloatingHudMode.EXPANDED)
                            FloatingHudMode.EXPANDED -> setHudMode(FloatingHudMode.ORB)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        hudContainer = root
        hudParams = params
        runCatching { windowManager?.addView(root, params) }
        setHudMode(FloatingHudMode.ORB)
    }

    private fun setHudMode(mode: FloatingHudMode) {
        hudMode = mode
        val root = hudContainer ?: return
        val params = hudParams ?: return
        when (mode) {
            FloatingHudMode.ORB -> {
                orbView?.visibility = View.VISIBLE
                orbView?.alpha = 0.88f
                expandedPanel?.visibility = View.GONE
            }
            FloatingHudMode.CORNER_DOCKED -> {
                orbView?.visibility = View.VISIBLE
                orbView?.alpha = 0.35f
                expandedPanel?.visibility = View.GONE
            }
            FloatingHudMode.EXPANDED -> {
                orbView?.visibility = View.GONE
                expandedPanel?.visibility = View.VISIBLE
                root.alpha = 1f
                // Keep expanded panel inside screen bounds
                params.x = params.x.coerceIn(dp(8), (screenWidth - dp(280)).coerceAtLeast(dp(8)))
                params.y = params.y.coerceIn(dp(24), (screenHeight - dp(240)).coerceAtLeast(dp(24)))
                runCatching { windowManager?.updateViewLayout(root, params) }
            }
        }
        ActiveSessionTracker.update { it.copy(hudMode = mode) }
    }

    private fun dockOrbToNearestCorner() {
        val root = hudContainer ?: return
        val params = hudParams ?: return
        refreshScreenBounds()
        val orbSize = dp(56)
        val margin = dp(10)
        val targetX = if (params.x + orbSize / 2 < screenWidth / 2) margin else (screenWidth - orbSize - margin)
        val targetY = if (params.y + orbSize / 2 < screenHeight / 2) dp(36) else (screenHeight - orbSize - dp(48))
        params.x = targetX.coerceAtLeast(0)
        params.y = targetY.coerceAtLeast(0)
        setHudMode(FloatingHudMode.CORNER_DOCKED)
        runCatching { windowManager?.updateViewLayout(root, params) }
    }

    // -------------------------------------------------------------------------
    // Pro Assistant & Headshot 3-Tap Calibration + Vision Pipeline
    // -------------------------------------------------------------------------

    private fun onProAssistantTapped() {
        if (proAssistantOn) {
            proAssistantOn = false
            updateTargetOverlayState()
            updateVisionButtonsUi()
            if (!headshotAssistOn) {
                stopVisionAnalyzer()
            }
            return
        }

        if (hasActiveMediaProjection) {
            proAssistantOn = true
            ensureTargetOverlayAttached()
            updateTargetOverlayState()
            updateVisionButtonsUi()
        } else {
            pendingVisionFeature = ScreenCapturePermissionActivity.FEATURE_PRO_ASSISTANT
            ScreenCapturePermissionActivity.launch(this, ScreenCapturePermissionActivity.FEATURE_PRO_ASSISTANT)
        }
    }

    private fun onHeadshotTapped() {
        // Step 1: If anchor isn't calibrated & locked yet, spawn the 3-tap draggable calibration ring!
        if (!isAnchorLocked) {
            if (headshotAnchorContainer == null) {
                attachHeadshotAnchorWidget(
                    initialX = screenWidth / 2 - dp(36),
                    initialY = screenHeight / 2 - dp(36),
                    locked = false
                )
            }
            // Collapse HUD to orb so user has unobstructed screen to drag onto fire/aim button
            setHudMode(FloatingHudMode.ORB)
            return
        }

        // Step 2: Anchor is locked -> toggle Headshot Vision Lock reticle
        if (headshotAssistOn) {
            headshotAssistOn = false
            updateTargetOverlayState()
            updateVisionButtonsUi()
            if (!proAssistantOn) {
                stopVisionAnalyzer()
            }
            return
        }

        if (hasActiveMediaProjection) {
            headshotAssistOn = true
            ensureTargetOverlayAttached()
            updateTargetOverlayState()
            updateVisionButtonsUi()
        } else {
            pendingVisionFeature = ScreenCapturePermissionActivity.FEATURE_HEADSHOT_VISION
            ScreenCapturePermissionActivity.launch(this, ScreenCapturePermissionActivity.FEATURE_HEADSHOT_VISION)
        }
    }

    private fun resetHeadshotCalibration() {
        val pkg = ActiveSessionTracker.state.value.activePackage
        isAnchorLocked = false
        headshotAssistOn = false
        calibrationTapCount = 0
        detachHeadshotAnchorWidget()
        updateTargetOverlayState()
        updateVisionButtonsUi()
        ActiveSessionTracker.update {
            it.copy(headshotAnchorLocked = false, headshotAssistActive = false)
        }
        if (!pkg.isNullOrBlank()) {
            serviceScope.launch {
                dataStore.updateHeadshotAnchor(pkg, null, null, false)
            }
        }
        // Re-open calibration widget immediately so user can recalibrate
        attachHeadshotAnchorWidget(
            initialX = screenWidth / 2 - dp(36),
            initialY = screenHeight / 2 - dp(36),
            locked = false
        )
        setHudMode(FloatingHudMode.ORB)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachHeadshotAnchorWidget(initialX: Int, initialY: Int, locked: Boolean) {
        detachHeadshotAnchorWidget()
        refreshScreenBounds()
        val ctx = localizedContext ?: this

        val ringSize = dp(64)
        val ringBox = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ringSize, ringSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (locked) Color.parseColor("#1AFF1E3C") else Color.parseColor("#550A0A0F"))
                setStroke(dp(2), Color.parseColor(if (locked) "#8800E5FF" else "#FF1E3C"))
            }
        }

        val innerDot = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(if (locked) "#9900E5FF" else "#00E5FF"))
            }
        }

        val tapCounterTv = TextView(ctx).apply {
            text = "0/3"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(4) }
            visibility = if (locked) View.GONE else View.VISIBLE
        }
        headshotTapBadge = tapCounterTv

        ringBox.addView(innerDot)
        ringBox.addView(tapCounterTv)

        val clampedX = initialX.coerceIn(0, (screenWidth - ringSize).coerceAtLeast(0))
        val clampedY = initialY.coerceIn(0, (screenHeight - ringSize).coerceAtLeast(0))

        val params = WindowManager.LayoutParams(
            ringSize,
            ringSize,
            overlayWindowType(),
            buildOverlayFlags(keepScreenOn = false, touchable = !locked),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = clampedX
            y = clampedY
        }

        headshotAnchorContainer = ringBox
        headshotAnchorParams = params
        runCatching { windowManager?.addView(ringBox, params) }

        if (!locked) {
            val promptBox = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val hintTv = TextView(ctx).apply {
                text = ctx.getString(R.string.overlay_calibrate_hint)
                setTextColor(Color.parseColor("#00E5FF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E60A0A0F"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                    cornerRadius = dpF(6f)
                }
            }
            headshotHintText = hintTv

            val confirmBtn = TextView(ctx).apply {
                text = ctx.getString(R.string.overlay_confirm_btn)
                setTextColor(Color.BLACK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(Typeface.DEFAULT_BOLD)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                visibility = View.GONE
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#00E676"))
                    cornerRadius = dpF(8f)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
                setOnClickListener {
                    lockHeadshotAnchorInPlace()
                }
            }
            headshotConfirmBtn = confirmBtn

            promptBox.addView(hintTv)
            promptBox.addView(confirmBtn)

            val promptParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayWindowType(),
                buildOverlayFlags(keepScreenOn = false, touchable = true),
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (clampedX - dp(40)).coerceIn(dp(8), (screenWidth - dp(210)).coerceAtLeast(dp(8)))
                y = if (clampedY > screenHeight * 0.60f) {
                    (clampedY - dp(72)).coerceAtLeast(dp(12))
                } else {
                    (clampedY + ringSize + dp(8)).coerceAtMost((screenHeight - dp(80)).coerceAtLeast(0))
                }
            }

            headshotPromptOverlay = promptBox
            headshotPromptParams = promptParams
            runCatching { windowManager?.addView(promptBox, promptParams) }

            fun syncPromptPosition() {
                promptParams.x = (params.x - dp(40)).coerceIn(dp(8), (screenWidth - dp(210)).coerceAtLeast(dp(8)))
                promptParams.y = if (params.y > screenHeight * 0.60f) {
                    (params.y - dp(76)).coerceAtLeast(dp(12))
                } else {
                    (params.y + ringSize + dp(8)).coerceAtMost((screenHeight - dp(84)).coerceAtLeast(0))
                }
                runCatching { windowManager?.updateViewLayout(promptBox, promptParams) }
            }

            calibrationTapCount = 0
            var initX = 0
            var initY = 0
            var downX = 0f
            var downY = 0f
            var moved = false

            ringBox.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = params.x
                        initY = params.y
                        downX = event.rawX
                        downY = event.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (abs(dx) > 6 || abs(dy) > 6) {
                            moved = true
                            params.x = (initX + dx).coerceIn(0, (screenWidth - ringSize).coerceAtLeast(0))
                            params.y = (initY + dy).coerceIn(0, (screenHeight - ringSize).coerceAtLeast(0))
                            runCatching { windowManager?.updateViewLayout(ringBox, params) }
                            syncPromptPosition()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            calibrationTapCount++
                            tapCounterTv.text = "${calibrationTapCount.coerceAtMost(3)}/3"
                            if (calibrationTapCount >= 3) {
                                confirmBtn.visibility = View.VISIBLE
                                syncPromptPosition()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun lockHeadshotAnchorInPlace() {
        val params = headshotAnchorParams ?: return
        refreshScreenBounds()
        isAnchorLocked = true

        // Save exact normalized coordinates (0f..1f) so calibration survives rotation across sessions
        val normX = (params.x.toFloat() / screenWidth.toFloat()).coerceIn(0f, 1f)
        val normY = (params.y.toFloat() / screenHeight.toFloat()).coerceIn(0f, 1f)
        val pkg = ActiveSessionTracker.state.value.activePackage
        if (!pkg.isNullOrBlank()) {
            serviceScope.launch {
                dataStore.updateHeadshotAnchor(pkg, normX, normY, true)
            }
        }

        // Convert widget into 100% non-intrusive, touch-pass-through locked anchor!
        attachHeadshotAnchorWidget(params.x, params.y, locked = true)

        ActiveSessionTracker.update { it.copy(headshotAnchorLocked = true) }
        resetAnchorBtnView?.visibility = View.VISIBLE

        // Automatically arm Headshot Vision analysis
        if (hasActiveMediaProjection) {
            headshotAssistOn = true
            ensureTargetOverlayAttached()
            updateTargetOverlayState()
            updateVisionButtonsUi()
        } else {
            pendingVisionFeature = ScreenCapturePermissionActivity.FEATURE_HEADSHOT_VISION
            ScreenCapturePermissionActivity.launch(this, ScreenCapturePermissionActivity.FEATURE_HEADSHOT_VISION)
        }
    }

    private fun detachHeadshotAnchorWidget() {
        headshotAnchorContainer?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        headshotPromptOverlay?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        headshotAnchorContainer = null
        headshotAnchorParams = null
        headshotPromptOverlay = null
        headshotPromptParams = null
        headshotTapBadge = null
        headshotConfirmBtn = null
        headshotHintText = null
    }

    private fun startMediaProjectionSession(resultCode: Int, data: Intent, feature: String?) {
        val ctx = localizedContext ?: this
        val gameName = ActiveSessionTracker.state.value.activeGameName ?: "Apex Vision"
        // Must promote foreground service to MEDIA_PROJECTION before calling getMediaProjection on Android 14+
        startForegroundCompat(buildNotification(ctx, gameName), includeMediaProjection = true)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = runCatching { mpm.getMediaProjection(resultCode, data) }.getOrNull()
        if (projection == null) {
            pendingVisionFeature = null
            updateVisionButtonsUi()
            return
        }

        hasActiveMediaProjection = true
        ensureTargetOverlayAttached()

        val analyzer = ScreenVisionAnalyzer(
            context = applicationContext,
            onTargetsDetected = { targets ->
                targetOverlayView?.updateTargets(targets)
            },
            onProjectionStopped = {
                hasActiveMediaProjection = false
                proAssistantOn = false
                headshotAssistOn = false
                updateTargetOverlayState()
                updateVisionButtonsUi()
            }
        )
        screenVisionAnalyzer = analyzer
        analyzer.start(projection)

        when (feature ?: pendingVisionFeature) {
            ScreenCapturePermissionActivity.FEATURE_HEADSHOT_VISION -> headshotAssistOn = true
            else -> proAssistantOn = true
        }
        pendingVisionFeature = null
        updateTargetOverlayState()
        updateVisionButtonsUi()
    }

    private fun stopVisionAnalyzer() {
        screenVisionAnalyzer?.stop()
        screenVisionAnalyzer = null
        hasActiveMediaProjection = false
        proAssistantOn = false
        headshotAssistOn = false
        targetOverlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
        targetOverlayView = null
        ActiveSessionTracker.update {
            it.copy(proAssistantActive = false, headshotAssistActive = false)
        }
        updateVisionButtonsUi()
    }

    private fun ensureTargetOverlayAttached() {
        if (targetOverlayView != null) return
        val ctx = localizedContext ?: this
        val view = TargetOverlayView(ctx).apply {
            headLockLabel = ctx.getString(R.string.overlay_head_lock_badge)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        targetOverlayView = view
        runCatching { windowManager?.addView(view, params) }
    }

    private fun updateTargetOverlayState() {
        screenVisionAnalyzer?.headshotZoomPassEnabled = headshotAssistOn
        val hp = hudParams
        val hc = hudContainer
        if (hp != null && hc != null && hc.width > 0 && hc.height > 0) {
            screenVisionAnalyzer?.excludedHudRect = android.graphics.RectF(
                hp.x.toFloat(),
                hp.y.toFloat(),
                (hp.x + hc.width).toFloat(),
                (hp.y + hc.height).toFloat()
            )
        }
        targetOverlayView?.apply {
            proAssistantEnabled = proAssistantOn
            headshotAssistEnabled = headshotAssistOn
            invalidate()
        }
        ActiveSessionTracker.update {
            it.copy(
                proAssistantActive = proAssistantOn,
                headshotAssistActive = headshotAssistOn
            )
        }
    }

    // -------------------------------------------------------------------------
    // UI Helpers & Telemetry Updates
    // -------------------------------------------------------------------------

    private fun updateOverlayViews(snap: TelemetrySnapshot) {
        val hz = snap.displayRefreshRateHz.toInt()
        orbFpsText?.text = if (snap.measuredCompositorFps > 0) {
            "${snap.measuredCompositorFps}\nFPS"
        } else {
            "$hz\nHZ"
        }
        fpsTextView?.text = "FPS ${snap.measuredCompositorFps}/${hz}Hz"
        tempTextView?.text = String.format(Locale.US, "%.1f°C", snap.batteryTempCelsius)
        ramTextView?.text = "RAM ${snap.ramUsagePercent}%"
        batteryTextView?.text = "BAT ${snap.batteryPercent}%"
        thermalTextView?.text = when (snap.thermalLevel) {
            ThermalLevel.NONE -> "COOL"
            ThermalLevel.LIGHT -> "WARM"
            ThermalLevel.MODERATE -> "MOD"
            ThermalLevel.SEVERE -> "HOT"
            ThermalLevel.CRITICAL -> "CRIT"
        }
    }

    private fun updateDndChip(dndOn: Boolean) {
        val ctx = localizedContext ?: this
        dndChipView?.apply {
            text = ctx.getString(if (dndOn) R.string.overlay_dnd_on else R.string.overlay_dnd_off)
            setTextColor(Color.parseColor(if (dndOn) "#00E5FF" else "#A0A8B8"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (dndOn) "#2200E5FF" else "#221A1A26"))
                setStroke(dp(1), Color.parseColor(if (dndOn) "#00E5FF" else "#2B2B3D"))
                cornerRadius = dpF(6f)
            }
        }
    }

    private fun updateVisionButtonsUi() {
        val ctx = localizedContext ?: this
        proAssistBtnView?.apply {
            text = ctx.getString(if (proAssistantOn) R.string.overlay_pro_assistant_on else R.string.overlay_pro_assistant)
            setTextColor(if (proAssistantOn) Color.WHITE else Color.parseColor("#F4F6FA"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (proAssistantOn) "#66FF1E3C" else "#221E1E2E"))
                setStroke(dp(1), Color.parseColor(if (proAssistantOn) "#FF1E3C" else "#2B2B3D"))
                cornerRadius = dpF(8f)
            }
        }
        headshotBtnView?.apply {
            text = ctx.getString(if (headshotAssistOn) R.string.overlay_headshot_on else R.string.overlay_headshot)
            setTextColor(if (headshotAssistOn) Color.BLACK else Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (headshotAssistOn) "#00E5FF" else "#2200E5FF"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
                cornerRadius = dpF(8f)
            }
        }
        resetAnchorBtnView?.visibility = if (isAnchorLocked || headshotAnchorContainer != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun detachAllOverlays() {
        hudContainer?.let { view -> runCatching { windowManager?.removeView(view) } }
        hudContainer = null
        hudParams = null
        orbView = null
        orbFpsText = null
        expandedPanel = null
        fpsTextView = null
        tempTextView = null
        ramTextView = null
        batteryTextView = null
        thermalTextView = null
        dndChipView = null
        proAssistBtnView = null
        headshotBtnView = null
        resetAnchorBtnView = null
        detachHeadshotAnchorWidget()
        targetOverlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
        targetOverlayView = null
    }

    private fun makeMetricLabel(ctx: Context, colorHex: String): TextView = TextView(ctx).apply {
        setTextColor(Color.parseColor(colorHex))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        setPadding(0, 0, dp(10), 0)
    }

    private fun makeSmallChipButton(
        ctx: Context,
        label: String,
        textColorHex: String,
        bgHex: String,
        strokeHex: String,
        onClick: () -> Unit
    ): TextView = TextView(ctx).apply {
        text = label
        setTextColor(Color.parseColor(textColorHex))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setTypeface(Typeface.DEFAULT_BOLD)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = GradientDrawable().apply {
            setColor(Color.parseColor(bgHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
            cornerRadius = dpF(6f)
        }
        setOnClickListener { onClick() }
    }

    private fun makeTacticalActionButton(
        ctx: Context,
        label: String,
        onClick: () -> Unit
    ): TextView = TextView(ctx).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTypeface(Typeface.DEFAULT_BOLD)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { onClick() }
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun buildOverlayFlags(keepScreenOn: Boolean, touchable: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (keepScreenOn) {
            flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }
        return flags
    }

    private fun createNotificationChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.service_notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(ctx: Context, gameName: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, GameMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(R.string.service_notif_title, gameName))
            .setContentText(ctx.getString(R.string.service_notif_body))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, ctx.getString(R.string.service_action_stop), stopIntent)
            .build()
    }

    private fun buildLocalizedContext(lang: AppLanguage): Context {
        val locale = when (lang) {
            AppLanguage.ARABIC -> Locale("ar")
            AppLanguage.ENGLISH -> Locale("en")
            AppLanguage.SYSTEM -> Locale.getDefault()
        }
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        return createConfigurationContext(config)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dpF(value: Float): Float =
        value * resources.displayMetrics.density

    companion object {
        private const val CHANNEL_ID = "apex_boost_telemetry_channel"
        private const val NOTIFICATION_ID = 4109
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_TOGGLE_DND = "com.example.service.ACTION_TOGGLE_DND"
        const val ACTION_MEDIA_PROJECTION_RESULT = "com.example.service.ACTION_MEDIA_PROJECTION_RESULT"
        const val ACTION_MEDIA_PROJECTION_DENIED = "com.example.service.ACTION_MEDIA_PROJECTION_DENIED"

        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_GAME_NAME = "extra_game_name"
        private const val EXTRA_SHOW_OVERLAY = "extra_show_overlay"
        private const val EXTRA_KEEP_SCREEN_ON = "extra_keep_screen_on"
        private const val EXTRA_DND_ACTIVE = "extra_dnd_active"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val EXTRA_VISION_FEATURE = "extra_vision_feature"

        fun startSession(
            context: Context,
            packageName: String?,
            gameName: String,
            showOverlay: Boolean,
            keepScreenOn: Boolean,
            dndActive: Boolean
        ) {
            val intent = Intent(context, GameMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_GAME_NAME, gameName)
                putExtra(EXTRA_SHOW_OVERLAY, showOverlay)
                putExtra(EXTRA_KEEP_SCREEN_ON, keepScreenOn)
                putExtra(EXTRA_DND_ACTIVE, dndActive)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopSession(context: Context) {
            val intent = Intent(context, GameMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun deliverMediaProjectionGrant(
            context: Context,
            resultCode: Int,
            data: Intent,
            feature: String
        ) {
            val intent = Intent(context, GameMonitorService::class.java).apply {
                action = ACTION_MEDIA_PROJECTION_RESULT
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_VISION_FEATURE, feature)
            }
            context.startService(intent)
        }

        fun notifyMediaProjectionDenied(context: Context) {
            val intent = Intent(context, GameMonitorService::class.java).apply {
                action = ACTION_MEDIA_PROJECTION_DENIED
            }
            context.startService(intent)
        }
    }
}
