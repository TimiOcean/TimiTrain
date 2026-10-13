package de.timitrain.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import de.timitrain.R

@Composable
fun TimiTrainTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val typography = remember(context) { myHandTypography(context) }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

/**
 * MyHand covers letters, including German umlauts, and not digits.
 * Numbers are drawn in sans-serif by [Text] and [OutlinedTextField], which works
 * from API 26. Android 10 and newer also fall back per glyph for any other
 * character this font does not contain. Sizes and line heights stay the defaults.
 */
private fun myHandTypography(context: Context): Typography {
    val family = myHandFamily(context)
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

private fun myHandFamily(context: Context): FontFamily {
    val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val font = android.graphics.fonts.Font.Builder(context.resources, R.font.myhand_regular).build()
        val family = android.graphics.fonts.FontFamily.Builder(font).build()
        Typeface.CustomFallbackBuilder(family)
            .setSystemFallback("sans-serif")
            .build()
    } else {
        ResourcesCompat.getFont(context, R.font.myhand_regular) ?: Typeface.SANS_SERIF
    }
    return FontFamily(typeface)
}
