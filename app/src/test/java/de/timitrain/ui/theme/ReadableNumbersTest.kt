package de.timitrain.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableNumbersTest {
    @Test
    fun wordsStayUntouched() {
        val text = "Bankdrücken"
        val annotated = text.withReadableNumbers()
        assertEquals(text, annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun germanDecimalAndTimerUseSans() {
        assertSansRanges("12,5", listOf(0..3))
        assertSansRanges("1:30", listOf(0..3))
        assertSansRanges("–", listOf(0..0))
    }

    @Test
    fun setLineKeepsUnitWords() {
        val text = "8 × 12,5 kg"
        assertSansRanges(text, listOf(0..0, 2..2, 4..7))
        val annotated = text.withReadableNumbers()
        assertEquals(text, annotated.text)
        annotated.spanStyles.forEach { span ->
            assertEquals(FontFamily.SansSerif, span.item.fontFamily)
            assertEquals(TextUnit.Unspecified, span.item.fontSize)
        }
    }

    @Test
    fun sentenceWordsStayInPlaceAroundDigits() {
        assertSansRanges("Satz 1 von 3", listOf(5..5, 11..11))
        assertSansRanges("Dienstag, 13. Oktober", listOf(10..12))
        assertSansRanges("Zone-2-Cardio", listOf(4..6))
    }

    @Test
    fun textFieldKeepsTheSameCharactersAndCursor() {
        val transformed = ReadableNumbersTransformation.filter(AnnotatedString("12,5"))
        assertEquals("12,5", transformed.text.text)
        assertEquals(2, transformed.offsetMapping.originalToTransformed(2))
        assertEquals(4, transformed.offsetMapping.transformedToOriginal(4))
    }

    private fun assertSansRanges(text: String, ranges: List<IntRange>) {
        val annotated = text.withReadableNumbers()
        assertEquals(text, annotated.text)
        assertEquals(ranges.map { it.first to it.last + 1 }, annotated.spanStyles.map { it.start to it.end })
    }
}
