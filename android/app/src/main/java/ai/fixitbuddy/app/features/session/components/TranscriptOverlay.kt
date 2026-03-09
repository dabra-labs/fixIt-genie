package ai.fixitbuddy.app.features.session.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TranscriptOverlay(
    transcript: String,
    modifier: Modifier = Modifier
) {
    if (transcript.isNotBlank()) {
        val scrollState = rememberScrollState()

        // Auto-scroll to bottom when transcript changes
        LaunchedEffect(transcript) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.75f),
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 160.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = transcript,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
