package ai.fixitbuddy.app.features.session.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.fixitbuddy.app.design.theme.StatusError
import ai.fixitbuddy.app.design.theme.StatusIdle
import ai.fixitbuddy.app.design.theme.StatusListening
import ai.fixitbuddy.app.design.theme.StatusSpeaking
import ai.fixitbuddy.app.design.theme.StatusThinking
import ai.fixitbuddy.app.features.session.SessionState

@Composable
fun StatusIndicator(
    sessionState: SessionState,
    agentState: String,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (sessionState) {
        SessionState.Idle -> "Ready" to StatusIdle
        SessionState.Connecting -> "Connecting…" to StatusThinking
        SessionState.Error -> "Error" to StatusError
        SessionState.Active -> when (agentState) {
            "listening" -> "Listening" to StatusListening
            "thinking" -> "Thinking…" to StatusThinking
            "speaking" -> "Speaking" to StatusSpeaking
            else -> agentState.replaceFirstChar { it.uppercase() } to StatusListening
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            PulsingDot(
                color = color,
                animate = sessionState == SessionState.Active || sessionState == SessionState.Connecting
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun PulsingDot(
    color: Color,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    if (animate) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        Box(
            modifier = modifier
                .size(8.dp)
                .alpha(alpha)
                .background(color, CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
    }
}
