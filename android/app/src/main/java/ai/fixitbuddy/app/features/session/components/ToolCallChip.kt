package ai.fixitbuddy.app.features.session.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ai.fixitbuddy.app.design.theme.StatusListening
import kotlinx.coroutines.delay

/**
 * Shows a brief animated chip when the agent calls a tool.
 * Auto-dismisses after 3 seconds.
 * Judges need to SEE function calling happening — this is the proof.
 *
 * @param toolCallCount monotonically increasing counter so the chip retriggers
 *                      even when the same tool is called twice in a row.
 */
@Composable
fun ToolCallChip(
    toolName: String?,
    toolCallCount: Int = 0,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf<ImageVector>(Icons.Default.Build) }

    // Key on toolCallCount so repeated calls to the same tool still retrigger
    LaunchedEffect(toolCallCount) {
        if (toolName != null) {
            val (name, ic) = formatToolCall(toolName)
            displayName = name
            icon = ic
            visible = true
            delay(3000)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = StatusListening.copy(alpha = 0.85f),
            shadowElevation = 4.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatToolCall(toolName: String): Pair<String, ImageVector> {
    return when {
        toolName.contains("safety", ignoreCase = true) ->
            "Checking safety warnings" to Icons.Default.Shield
        toolName.contains("knowledge", ignoreCase = true) || toolName.contains("lookup", ignoreCase = true) ->
            "Searching knowledge base" to Icons.Default.Search
        toolName.contains("log", ignoreCase = true) || toolName.contains("diagnostic", ignoreCase = true) ->
            "Logging diagnostic step" to Icons.Default.Build
        else -> toolName to Icons.Default.Build
    }
}
