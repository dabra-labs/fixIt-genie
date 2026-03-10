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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var glassesCameraManager: GlassesCameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glassesCameraManager.initialize()
        glassesCameraManager.register(this)
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
    }
}
