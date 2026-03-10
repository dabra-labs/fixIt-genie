package ai.fixitbuddy.app.features.session.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.fixitbuddy.app.features.session.SessionState

@Composable
fun GenieAvatar(
    sessionState: SessionState,
    agentState: String,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "genie")

    // Aura breathe — always gentle
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura"
    )

    // Hover float — always on
    val hoverOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hover"
    )

    // Ripple rings master progress — drives all 3 rings via phase offset
    val ringProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing)
        ),
        label = "ring"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp)
        ) {
            // Golden aura — breathes behind the face
            Canvas(modifier = Modifier.size(72.dp).scale(auraScale)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x46FF9100), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.minDimension / 2
                    ),
                    radius = size.minDimension / 2
                )
            }

            // Genie face — golden circle with emoji, floats up and down
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { translationY = hoverOffset }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFF8F0), Color(0xFFEF8C00)),
                            center = Offset(21f, 18f),
                            radius = 56f
                        )
                    )
                    .border(2.dp, Color(0xCCFFC83C), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🧞", fontSize = 26.sp)
            }
        }

        // Purple smoke tail — overlaps the face slightly
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 18.dp)
                .offset(y = (-4).dp)
                .clip(
                    RoundedCornerShape(
                        topStartPercent = 50, topEndPercent = 50,
                        bottomStartPercent = 50, bottomEndPercent = 50
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xCC7E57C2), Color.Transparent)
                    )
                )
        )

        // Ripple rings — only when session is active
        if (sessionState == SessionState.Active) {
            val amplitude = (0.4f + audioLevel * 0.6f).coerceIn(0.4f, 1f)
            Canvas(modifier = Modifier.size(56.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val maxRadius = size.minDimension * 0.48f * amplitude

                // 3 rings staggered by 1/3 phase each
                listOf(0f, 0.333f, 0.667f).forEachIndexed { i, phaseOffset ->
                    val progress = (ringProgress + phaseOffset) % 1f
                    val radius = progress * maxRadius
                    val alpha = (1f - progress) * amplitude
                    val color = when (i) {
                        0 -> Color(0xBFFF9100)
                        1 -> Color(0x99CE93D8)
                        else -> Color(0x807E57C2)
                    }
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}
