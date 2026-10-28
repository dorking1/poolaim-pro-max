package com.poolaim.promax

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.poolaim.promax.capture.ScreenCaptureService
import com.poolaim.promax.ui.theme.PoolAimTheme
import com.poolaim.promax.ui.navigation.PoolAimNavHost

class MainActivity : ComponentActivity() {
    private var captureRequestInFlight = false

    private val capturePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            captureRequestInFlight = false
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "PoolAim live analysis started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screen capture permission was not granted", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoolAimTheme { PoolAimNavHost(activity = this) }
        }
        handleCaptureRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCaptureRequest(intent)
    }

    private fun handleCaptureRequest(intent: Intent?) {
        if (intent?.action != ACTION_REQUEST_LIVE_ANALYSIS || captureRequestInFlight) return
        captureRequestInFlight = true
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        capturePermissionLauncher.launch(manager.createScreenCaptureIntent())
    }

    companion object {
        const val ACTION_REQUEST_LIVE_ANALYSIS = "com.poolaim.promax.action.REQUEST_LIVE_ANALYSIS"
    }
}
