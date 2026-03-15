package ai.fixitbuddy.app.features.session.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.fixitbuddy.app.features.session.ChatRole
import ai.fixitbuddy.app.features.session.ChatTurn

@Composable
fun GenieTranscript(
    chatTurns: List<ChatTurn>,
    isGenieStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Instant scroll to bottom — animateScrollToItem causes jank during streaming
    LaunchedEffect(chatTurns.size, chatTurns.lastOrNull()?.text) {
        if (chatTurns.isNotEmpty()) {
            listState.scrollToItem(chatTurns.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(chatTurns) { index, turn ->
            val isLast = index == chatTurns.lastIndex
            val showCursor = isLast && isGenieStreaming && turn.role == ChatRole.GENIE

            when (turn.role) {
                ChatRole.USER -> UserBubble(text = turn.text)
                ChatRole.GENIE -> GenieBubble(text = turn.text, showCursor = showCursor)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "YOU",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.44f)
            )
            Spacer(modifier = Modifier.height(3.dp))
            if (text.isBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                        .background(Color(0xFF1F2634))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(3) { i -> ListeningDot(delayMs = i * 150) }
                }
            } else {
                Box(
                    modifier = Modifier
                        .widthIn(max = 312.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                        .background(Color(0xFF1B2331))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                        )
                        .padding(horizontal = 15.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = text,
                        fontSize = 16.sp,
                        color = Color(0xFFE9EEF8),
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GenieBubble(text: String, showCursor: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "GENIE",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFB36B)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 324.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xCC7A2D0E), Color(0xE6411A0A))
                        )
                    )
                    .border(
                        1.dp,
                        Color(0x55FFB36B),
                        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                    )
                    .padding(horizontal = 15.dp, vertical = 12.dp)
            ) {
                if (showCursor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = text, fontSize = 16.sp, color = Color.White.copy(alpha = 0.96f), lineHeight = 22.sp)
                        Spacer(modifier = Modifier.size(width = 4.dp, height = 0.dp))
                        TypingCursor()
                    }
                } else {
                    Text(text = text, fontSize = 16.sp, color = Color.White.copy(alpha = 0.96f), lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun TypingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Box(
        modifier = Modifier
            .size(width = 7.dp, height = 13.dp)
            .background(Color(0xFFFFB36B).copy(alpha = alpha))
    )
}

@Composable
private fun ListeningDot(delayMs: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot$delayMs")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = delayMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha$delayMs"
    )
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = alpha))
    )
}
