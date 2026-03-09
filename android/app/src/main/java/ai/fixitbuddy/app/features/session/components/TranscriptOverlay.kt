package ai.fixitbuddy.app.features.session.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TranscriptOverlay(
    transcript: String,
    modifier: Modifier = Modifier
) {
    if (transcript.isNotBlank()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.75f),
            modifier = modifier
        ) {
            Text(
                text = transcript,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
