package ai.fixitbuddy.app.features.session.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun `markdown renderer keeps content and formats common list syntax`() {
        val rendered = markdownToAnnotatedString(
            """
            ## Quick Fix
            - Press **Power**
            - Open [the manual](https://example.com)
            """.trimIndent()
        )

        assertEquals(
            "Quick Fix\n• Press Power\n• Open the manual",
            rendered.text
        )

        val powerStart = rendered.text.indexOf("Power")
        assertTrue(
            rendered.spanStyles.any {
                it.start <= powerStart &&
                    it.end >= powerStart + "Power".length &&
                    it.item.fontWeight == FontWeight.Bold
            }
        )

        val manualStart = rendered.text.indexOf("the manual")
        assertTrue(
            rendered.spanStyles.any {
                it.start <= manualStart &&
                    it.end >= manualStart + "the manual".length &&
                    it.item.textDecoration == TextDecoration.Underline
            }
        )
    }

    @Test
    fun `markdown renderer formats inline code italics and cursor`() {
        val rendered = markdownToAnnotatedString(
            markdown = "Try `Power` then *wait*",
            accentColor = Color(0xFFFFB36B),
            showCursor = true,
        )

        assertEquals("Try Power then wait ▍", rendered.text)

        val codeStart = rendered.text.indexOf("Power")
        assertTrue(
            rendered.spanStyles.any {
                it.start <= codeStart &&
                    it.end >= codeStart + "Power".length &&
                    it.item.fontFamily == FontFamily.Monospace
            }
        )

        val waitStart = rendered.text.indexOf("wait")
        assertTrue(
            rendered.spanStyles.any {
                it.start <= waitStart &&
                    it.end >= waitStart + "wait".length &&
                    it.item.fontStyle != null
            }
        )
    }
}
