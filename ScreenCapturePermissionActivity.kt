package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Transparent trampoline Activity that requests Android's official `MediaProjection`
 * screen-capture consent from the user when activating Pro Assistant or Headshot Vision
 * from the floating Game Space HUD.
 */
class ScreenCapturePermissionActivity : ComponentActivity() {

    private var requestedFeature: String = FEATURE_PRO_ASSISTANT

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            GameMonitorService.deliverMediaProjectionGrant(
                context = applicationContext,
                resultCode = result.resultCode,
                data = result.data!!,
                feature = requestedFeature
            )
        } else {
            GameMonitorService.notifyMediaProjectionDenied(applicationContext)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedFeature = intent.getStringExtra(EXTRA_FEATURE) ?: FEATURE_PRO_ASSISTANT
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    companion object {
        private const val EXTRA_FEATURE = "extra_feature"
        const val FEATURE_PRO_ASSISTANT = "pro_assistant"
        const val FEATURE_HEADSHOT_VISION = "headshot_vision"

        fun launch(context: Context, feature: String) {
            val intent = Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_FEATURE, feature)
            }
            context.startActivity(intent)
        }
    }
}
