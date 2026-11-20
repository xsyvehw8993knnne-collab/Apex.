package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * High-precision on-device screen vision analyzer powered by:
 * 1. Official `MediaProjection` + `VirtualDisplay` + `ImageReader` with automatic portrait/landscape
 *    rotation adaptation so bounding boxes always align with games like GTA San Andreas in landscape.
 * 2. Parallel ML Kit Pose Detection (full frame + zoomed center aim-crop when Headshot Assist is ON)
 *    + ML Kit Object Detection + 32x18 pixel-array motion clustering with global camera-pan rejection.
 * 3. `VisionTargetTracker` IoU fusion & temporal EMA smoothing to eliminate flicker.
 */
class ScreenVisionAnalyzer(
    private val context: Context,
    private val onTargetsDetected: (List<DetectedScreenTarget>) -> Unit,
    private val onProjectionStopped: () -> Unit
) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val tracker = VisionTargetTracker()
    private val isBusy = AtomicBoolean(false)
    private var lastAnalyzedTimeMs = 0L

    @Volatile var headshotZoomPassEnabled: Boolean = false
    @Volatile var excludedHudRect: RectF? = null

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var densityDpi = 320
    private var captureWidth = 640
    private var captureHeight = 360

    private val poseDetector by lazy {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()
        )
    }

    private val objectDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .build()
        )
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stop()
            mainHandler.post { onProjectionStopped() }
        }
    }

    fun start(projection: MediaProjection) {
        stop()
        mediaProjection = projection
        tracker.reset()

        val thread = HandlerThread("ApexVisionWorker").apply { start() }
        workerThread = thread
        val handler = Handler(thread.looper)
        workerHandler = handler

        projection.registerCallback(projectionCallback, handler)
        val (w, h, dpi) = queryCurrentScreenSize()
        setupVirtualDisplayAndReader(projection, w, h, dpi, handler)
    }

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplayAndReader(
        projection: MediaProjection,
        w: Int,
        h: Int,
        dpi: Int,
        handler: Handler
    ) {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }

        screenWidth = w
        screenHeight = h
        densityDpi = dpi

        // 640px max edge provides strong ML Kit character & head accuracy with low latency
        val maxCaptureDim = 640f
        val scale = maxCaptureDim / max(screenWidth, screenHeight).toFloat()
        captureWidth = (screenWidth * scale).toInt().coerceAtLeast(240)
        captureHeight = (screenHeight * scale).toInt().coerceAtLeast(240)
        tracker.reset()

        val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            val now = System.currentTimeMillis()
            if (isBusy.get() || now - lastAnalyzedTimeMs < 65L) {
                image.close()
                return@setOnImageAvailableListener
            }

            // Detect portrait <-> landscape rotation dynamically and rebuild display if needed
            val (curW, curH, curDpi) = queryCurrentScreenSize()
            if (curW != screenWidth || curH != screenHeight) {
                image.close()
                val proj = mediaProjection
                if (proj != null && workerHandler != null) {
                    setupVirtualDisplayAndReader(proj, curW, curH, curDpi, workerHandler!!)
                }
                return@setOnImageAvailableListener
            }

            isBusy.set(true)
            lastAnalyzedTimeMs = now

            val bitmap = runCatching { imageToBitmap(image, captureWidth, captureHeight) }.getOrNull()
            image.close()

            if (bitmap == null) {
                isBusy.set(false)
                return@setOnImageAvailableListener
            }

            analyzeFrame(bitmap)
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
            "ApexVisionDisplay",
            captureWidth,
            captureHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
    }

    fun stop() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { imageReader?.close() }
        imageReader = null

        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null

        runCatching { workerThread?.quitSafely() }
        workerThread = null
        workerHandler = null
        tracker.reset()
        isBusy.set(false)

        mainHandler.post { onTargetsDetected(emptyList()) }
    }

    private fun queryCurrentScreenSize(): Triple<Int, Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            val dpi = context.resources.configuration.densityDpi.coerceAtLeast(160)
            Triple(bounds.width().coerceAtLeast(480), bounds.height().coerceAtLeast(480), dpi)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay?.getRealMetrics(metrics)
            Triple(
                metrics.widthPixels.coerceAtLeast(480),
                metrics.heightPixels.coerceAtLeast(480),
                metrics.densityDpi.coerceAtLeast(160)
            )
        }
    }

    private fun imageToBitmap(image: android.media.Image, targetW: Int, targetH: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        buffer.rewind()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * targetW
        val paddedW = targetW + rowPadding / pixelStride
        val rawBmp = Bitmap.createBitmap(paddedW, targetH, Bitmap.Config.ARGB_8888)
        rawBmp.copyPixelsFromBuffer(buffer)
        return if (paddedW != targetW) {
            val cropped = Bitmap.createBitmap(rawBmp, 0, 0, targetW, targetH)
            rawBmp.recycle()
            cropped
        } else {
            rawBmp
        }
    }

    private fun analyzeFrame(bitmap: Bitmap) {
        val scaleX = screenWidth.toFloat() / bitmap.width.toFloat()
        val scaleY = screenHeight.toFloat() / bitmap.height.toFloat()

        // 1. Fast pixel-array spatial motion clustering (with HUD exclusion & camera-pan rejection)
        val motionTargets = tracker.detectMotionClusters(
            bitmap = bitmap,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            excludedHudRect = excludedHudRect
        )

        val fullInput = InputImage.fromBitmap(bitmap, 0)
        val poseTask: Task<Pose> = poseDetector.process(fullInput)
        val objectTask: Task<List<DetectedObject>> = objectDetector.process(fullInput)

        // 2. Optional center aim-zone zoomed crop pass for distant humanoid head detection
        var cropBitmap: Bitmap? = null
        var cropOffsetX = 0f
        var cropOffsetY = 0f
        var cropScaleX = scaleX
        var cropScaleY = scaleY

        val centerPoseTask: Task<Pose>? = if (headshotZoomPassEnabled) {
            val cropW = (bitmap.width * 0.50f).toInt().coerceAtLeast(120)
            val cropH = (bitmap.height * 0.56f).toInt().coerceAtLeast(120)
            val left = ((bitmap.width - cropW) / 2).coerceAtLeast(0)
            val top = ((bitmap.height - cropH) / 2).coerceAtLeast(0)
            cropOffsetX = left * scaleX
            cropOffsetY = top * scaleY

            val sliced = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
            val upscaled = Bitmap.createScaledBitmap(sliced, cropW * 2, cropH * 2, true)
            sliced.recycle()
            cropBitmap = upscaled
            cropScaleX = scaleX / 2f
            cropScaleY = scaleY / 2f
            poseDetector.process(InputImage.fromBitmap(upscaled, 0))
        } else {
            null
        }

        val tasksToAwait = listOfNotNull(poseTask, objectTask, centerPoseTask)
        Tasks.whenAllComplete(tasksToAwait).addOnCompleteListener {
            val rawCombined = ArrayList<DetectedScreenTarget>(motionTargets)

            if (poseTask.isSuccessful) {
                poseTask.result?.let { pose ->
                    extractPoseTarget(pose, scaleX, scaleY, 0f, 0f)?.let { rawCombined.add(it) }
                }
            }

            if (centerPoseTask != null && centerPoseTask.isSuccessful) {
                centerPoseTask.result?.let { cropPose ->
                    extractPoseTarget(
                        pose = cropPose,
                        scaleX = cropScaleX,
                        scaleY = cropScaleY,
                        offsetX = cropOffsetX,
                        offsetY = cropOffsetY
                    )?.let { rawCombined.add(it) }
                }
            }

            if (objectTask.isSuccessful) {
                objectTask.result?.forEach { obj ->
                    val box = obj.boundingBox
                    val areaRatio = (box.width() * box.height()).toFloat() /
                        (bitmap.width * bitmap.height).toFloat()
                    if (areaRatio in 0.004f..0.55f) {
                        val mapped = RectF(
                            box.left * scaleX,
                            box.top * scaleY,
                            box.right * scaleX,
                            box.bottom * scaleY
                        )
                        if (excludedHudRect == null || !excludedHudRect!!.contains(mapped.centerX(), mapped.centerY())) {
                            rawCombined.add(
                                DetectedScreenTarget(
                                    bounds = mapped,
                                    headCenter = PointF(mapped.centerX(), mapped.top + mapped.height() * 0.16f),
                                    kind = TargetKind.MOTION_OBJECT,
                                    confidence = 0.80f
                                )
                            )
                        }
                    }
                }
            }

            cropBitmap?.recycle()
            bitmap.recycle()
            val smoothed = tracker.fuseAndSmooth(rawCombined)
            isBusy.set(false)
            mainHandler.post { onTargetsDetected(smoothed) }
        }
    }

    private fun extractPoseTarget(
        pose: Pose,
        scaleX: Float,
        scaleY: Float,
        offsetX: Float,
        offsetY: Float
    ): DetectedScreenTarget? {
        val visibleLandmarks = pose.allPoseLandmarks.filter { it.inFrameLikelihood >= 0.40f }
        if (visibleLandmarks.size < 3) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (lm in visibleLandmarks) {
            if (lm.position.x < minX) minX = lm.position.x
            if (lm.position.y < minY) minY = lm.position.y
            if (lm.position.x > maxX) maxX = lm.position.x
            if (lm.position.y > maxY) maxY = lm.position.y
        }

        val padX = (maxX - minX) * 0.16f + 6f
        val padY = (maxY - minY) * 0.14f + 8f
        val bounds = RectF(
            ((minX - padX) * scaleX + offsetX).coerceAtLeast(0f),
            ((minY - padY) * scaleY + offsetY).coerceAtLeast(0f),
            ((maxX + padX) * scaleX + offsetX).coerceAtMost(screenWidth.toFloat()),
            ((maxY + padY) * scaleY + offsetY).coerceAtMost(screenHeight.toFloat())
        )

        val headPoint = extractHeadPoint(pose, scaleX, scaleY, offsetX, offsetY)
            ?: PointF(bounds.centerX(), bounds.top + bounds.height() * 0.15f)

        return DetectedScreenTarget(
            bounds = bounds,
            headCenter = headPoint,
            kind = TargetKind.HUMANOID,
            confidence = 0.96f
        )
    }

    private fun extractHeadPoint(
        pose: Pose,
        scaleX: Float,
        scaleY: Float,
        offsetX: Float,
        offsetY: Float
    ): PointF? {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        if (nose != null && nose.inFrameLikelihood >= 0.42f) {
            return PointF(nose.position.x * scaleX + offsetX, nose.position.y * scaleY + offsetY)
        }
        val leftEye = pose.getPoseLandmark(PoseLandmark.LEFT_EYE)
        val rightEye = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE)
        if (leftEye != null && rightEye != null &&
            leftEye.inFrameLikelihood >= 0.35f && rightEye.inFrameLikelihood >= 0.35f
        ) {
            return PointF(
                ((leftEye.position.x + rightEye.position.x) * 0.5f) * scaleX + offsetX,
                ((leftEye.position.y + rightEye.position.y) * 0.5f) * scaleY + offsetY
            )
        }
        val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
        val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)
        if (leftEar != null && rightEar != null &&
            leftEar.inFrameLikelihood >= 0.35f && rightEar.inFrameLikelihood >= 0.35f
        ) {
            return PointF(
                ((leftEar.position.x + rightEar.position.x) * 0.5f) * scaleX + offsetX,
                ((leftEar.position.y + rightEar.position.y) * 0.5f) * scaleY + offsetY
            )
        }
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        if (leftShoulder != null && rightShoulder != null &&
            leftShoulder.inFrameLikelihood >= 0.35f && rightShoulder.inFrameLikelihood >= 0.35f
        ) {
            val shoulderWidth = hypot(
                leftShoulder.position.x - rightShoulder.position.x,
                leftShoulder.position.y - rightShoulder.position.y
            )
            val cx = (leftShoulder.position.x + rightShoulder.position.x) * 0.5f
            val cy = min(leftShoulder.position.y, rightShoulder.position.y) - (shoulderWidth * 0.46f)
            return PointF(cx * scaleX + offsetX, cy * scaleY + offsetY)
        }
        return null
    }
}
