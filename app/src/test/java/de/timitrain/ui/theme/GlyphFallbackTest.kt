package de.timitrain.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphFallbackTest {
    @Test
    fun coveredGlyphsStayInTimiHand() {
        listOf(
            "Bankdrücken",
            "12,5",
            "1:30",
            "Zone-2-Cardio",
            "Satz 1 von 3",
            "Dienstag, 13. Oktober",
            "ÄÖÜäöü",
            "+ –",
        ).forEach { text ->
            val annotated = text.withGlyphFallback()
            assertEquals(text, annotated.text)
            assertTrue(annotated.spanStyles.isEmpty())
        }
    }

    @Test
    fun missingGlyphsUseSans() {
        assertSansRanges("8 × 12,5 kg", listOf(2..2))
        assertSansRanges("5 km · 20 min", listOf(5..5))
        assertSansRanges("größer", listOf(3..3))
        assertSansRanges("„Name“", listOf(0..0))
        assertSansRanges("100%", listOf(3..3))
        val annotated = "8 × 12,5 kg".withGlyphFallback()
        annotated.spanStyles.forEach { span ->
            assertEquals(FontFamily.SansSerif, span.item.fontFamily)
            assertEquals(TextUnit.Unspecified, span.item.fontSize)
        }
    }

    @Test
    fun textFieldKeepsTheSameCharactersAndCursor() {
        val transformed = "12,5".withGlyphFallback()
        assertEquals("12,5", transformed.text)
        assertTrue(transformed.spanStyles.isEmpty())
        val field = AnnotatedString("12,5")
        assertEquals(field.text, field.text.withGlyphFallback().text)
    }

    private fun assertSansRanges(text: String, ranges: List<IntRange>) {
        val annotated = text.withGlyphFallback()
        assertEquals(text, annotated.text)
        assertEquals(ranges.map { it.first to it.last + 1 }, annotated.spanStyles.map { it.start to it.end })
    }
}
