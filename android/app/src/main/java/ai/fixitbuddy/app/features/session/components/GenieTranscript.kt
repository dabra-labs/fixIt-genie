package ai.fixitbuddy.app.features.session.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
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
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
        if (text.isBlank()) {
            // Listening indicator — 3 animated dots
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(3) { i -> ListeningDot(delayMs = i * 150) }
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 3.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text = text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), lineHeight = 18.sp)
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
        Box(
            modifier = Modifier
                .widthIn(max = 230.dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(Color(0x467E57C2))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (showCursor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 18.sp)
                    Spacer(modifier = Modifier.size(width = 3.dp, height = 0.dp))
                    TypingCursor()
                }
            } else {
                Text(text = text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 18.sp)
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
            .size(width = 6.dp, height = 11.dp)
            .background(Color(0xFFCE93D8).copy(alpha = alpha))
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
