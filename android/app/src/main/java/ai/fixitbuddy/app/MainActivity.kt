package ai.fixitbuddy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import ai.fixitbuddy.app.core.camera.GlassesCameraManager
import ai.fixitbuddy.app.design.theme.FixItBuddyTheme
import ai.fixitbuddy.app.navigation.FixItBuddyNavHost
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var glassesCameraManager: GlassesCameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FixItBuddyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FixItBuddyNavHost()
                }
            }
        }
        // Initialize after window is set up so any SDK dialog has a valid window to attach to.
        // register() is gated on savedInstanceState == null to avoid re-triggering the
        // registration dialog on every Activity recreation (e.g. screen rotation).
        glassesCameraManager.initialize()
        if (savedInstanceState == null && glassesCameraManager.isInitialized) {
            try {
                glassesCameraManager.register(this)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register with Meta Wearables SDK — glasses unavailable", e)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
