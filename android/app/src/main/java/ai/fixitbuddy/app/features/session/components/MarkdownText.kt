package ai.fixitbuddy.app.features.session.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFFFB36B),
    showCursor: Boolean = false,
) {
    val rendered = remember(markdown, accentColor, showCursor) {
        markdownToAnnotatedString(
            markdown = markdown,
            accentColor = accentColor,
            showCursor = showCursor,
        )
    }

    Text(
        text = rendered,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
        )
    )
}

internal fun markdownToAnnotatedString(
    markdown: String,
    accentColor: Color = Color(0xFFFFB36B),
    showCursor: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    var inCodeBlock = false
    var emittedLine = false

    markdown.lines().forEach { rawLine ->
        val trimmedLine = rawLine.trim()
        if (trimmedLine.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            return@forEach
        }

        if (emittedLine) {
            append("\n")
        }
        emittedLine = true

        when {
            inCodeBlock -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.White.copy(alpha = 0.12f),
                    )
                ) {
                    append(rawLine.trimEnd())
                }
            }
            trimmedLine.startsWith("#") -> {
                val heading = trimmedLine.trimStart('#', ' ')
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendMarkdownInline(heading, accentColor)
                }
            }
            BULLET_PATTERN.containsMatchIn(trimmedLine) -> {
                val content = trimmedLine.replaceFirst(BULLET_PATTERN, "")
                withStyle(ParagraphStyle(textIndent = TextIndent(restLine = 14.sp))) {
                    append("• ")
                    appendMarkdownInline(content, accentColor)
                }
            }
            ORDERED_LIST_PATTERN.containsMatchIn(trimmedLine) -> {
                val number = ORDERED_LIST_PATTERN.find(trimmedLine)?.groupValues?.get(1).orEmpty()
                val content = trimmedLine.replaceFirst(ORDERED_LIST_PATTERN, "")
                withStyle(ParagraphStyle(textIndent = TextIndent(restLine = 18.sp))) {
                    append("$number. ")
                    appendMarkdownInline(content, accentColor)
                }
            }
            BLOCKQUOTE_PATTERN.containsMatchIn(trimmedLine) -> {
                val content = trimmedLine.replaceFirst(BLOCKQUOTE_PATTERN, "")
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.82f))) {
                    append("│ ")
                    appendMarkdownInline(content, accentColor)
                }
            }
            else -> appendMarkdownInline(rawLine.trimEnd(), accentColor)
        }
    }

    if (showCursor) {
        withStyle(
            SpanStyle(
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
        ) {
            append(" ▍")
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdownInline(
    text: String,
    accentColor: Color,
) {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val marker = text.substring(index, index + 2)
                val end = text.indexOf(marker, index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendMarkdownInline(text.substring(index + 2, end), accentColor)
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendMarkdownInline(text.substring(index + 2, end), accentColor)
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.White.copy(alpha = 0.12f),
                        )
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text[index] == '[' -> {
                val closingBracket = text.indexOf(']', index + 1)
                val openingParen = if (closingBracket != -1) text.indexOf('(', closingBracket + 1) else -1
                val closingParen = if (openingParen != -1) text.indexOf(')', openingParen + 1) else -1
                if (closingBracket != -1 && openingParen == closingBracket + 1 && closingParen != -1) {
                    val label = text.substring(index + 1, closingBracket)
                    val fallback = text.substring(openingParen + 1, closingParen)
                    withStyle(
                        SpanStyle(
                            color = accentColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium,
                        )
                    ) {
                        append(if (label.isNotBlank()) label else fallback)
                    }
                    index = closingParen + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text[index] == '*' || text[index] == '_' -> {
                val marker = text[index]
                val end = text.indexOf(marker, index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        appendMarkdownInline(text.substring(index + 1, end), accentColor)
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            else -> {
                append(text[index])
                index += 1
            }
        }
    }
}

private val BULLET_PATTERN = Regex("""^\s*[-*+]\s+""")
private val ORDERED_LIST_PATTERN = Regex("""^\s*(\d+)\.\s+""")
private val BLOCKQUOTE_PATTERN = Regex("""^\s*>\s+""")
