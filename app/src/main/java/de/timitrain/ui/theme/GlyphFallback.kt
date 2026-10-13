package de.timitrain.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign

/**
 * Characters present in TimiHand, taken from its cmap. Digits, comma, colon,
 * hyphen, plus, period, and umlauts are included. × · % ß and „ are not.
 */
private const val TIMIHAND_GLYPHS =
    " !\"'()*+,-./0123456789:;<=>?" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
        "abcdefghijklmnopqrstuvwxyz" +
        "\u00A0ÄÖÜäöü\u2010\u2013\u2018\u2019\u201C\u201D\u2212"

private val sansFallback = SpanStyle(fontFamily = FontFamily.SansSerif)

/**
 * Draws every character TimiHand contains in that face. Characters it does not
 * contain use sans-serif, including on API 26 where the system cannot fall
 * back per glyph. The text and its sizes stay the same.
 */
fun String.withGlyphFallback(): AnnotatedString {
    val spans = ArrayList<AnnotatedString.Range<SpanStyle>>()
    var index = 0
    while (index < length) {
        if (this[index] in TIMIHAND_GLYPHS) {
            index++
            continue
        }
        val start = index
        while (index < length && this[index] !in TIMIHAND_GLYPHS) index++
        spans += AnnotatedString.Range(sansFallback, start, index)
    }
    return AnnotatedString(text = this, spanStyles = spans)
}

private object GlyphFallbackTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(text.text.withGlyphFallback(), OffsetMapping.Identity)
    }
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = text.withGlyphFallback(),
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        style = style,
    )
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
) {
    MaterialOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        singleLine = singleLine,
        visualTransformation = GlyphFallbackTransformation,
    )
}
