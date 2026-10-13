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

private val sansNumber = SpanStyle(fontFamily = FontFamily.SansSerif)

/**
 * MyHand has no digits. These runs are drawn in sans-serif so a number stays
 * readable on API 26, where the system cannot fall back per glyph.
 * The characters and their sizes are unchanged.
 */
fun String.withReadableNumbers(): AnnotatedString {
    val spans = ArrayList<AnnotatedString.Range<SpanStyle>>()
    var index = 0
    while (index < length) {
        if (!isNumberGlyph(index)) {
            index++
            continue
        }
        val start = index
        while (index < length && isNumberGlyph(index)) index++
        spans += AnnotatedString.Range(sansNumber, start, index)
    }
    return AnnotatedString(text = this, spanStyles = spans)
}

private fun String.isNumberGlyph(index: Int): Boolean {
    val character = this[index]
    if (character.isDigit()) return true
    if (character == '×' || character == '·' || character == '–' || character == '+' || character == '%') {
        return true
    }
    if (character == ',' || character == '.' || character == ':' || character == '-') {
        return neighborIsDigit(index - 1) || neighborIsDigit(index + 1)
    }
    return false
}

private fun String.neighborIsDigit(index: Int): Boolean {
    return index in indices && this[index].isDigit()
}

object ReadableNumbersTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(text.text.withReadableNumbers(), OffsetMapping.Identity)
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
        text = text.withReadableNumbers(),
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
        visualTransformation = ReadableNumbersTransformation,
    )
}
