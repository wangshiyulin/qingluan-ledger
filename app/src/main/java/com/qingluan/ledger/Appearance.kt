package com.qingluan.ledger

import android.content.Context
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

val paletteNames =
    linkedMapOf(
        "system" to "默认",
        "jade" to "青竹",
        "blue" to "海蓝",
        "violet" to "暮紫",
        "amber" to "暖杏",
        "rose" to "蔷薇",
        "mint" to "薄荷",
        "sky" to "晴空",
        "lilac" to "浅丁香",
        "peach" to "蜜桃",
        "oat" to "燕麦",
        "sakura" to "樱花",
    )
val fontSizes = linkedMapOf("system" to "跟随系统", "custom" to "自定义")

data class AppAppearance(
    val palette: String = "system",
    val pureBlack: Boolean = false,
    val fontSize: String = "system",
    val customFontPercent: Int = 100,
    val fontFamily: String = "system",
    val customFontId: String = "",
) {
    init {
        require(customFontPercent in 75..160)
    }

    val scale
        get() =
            when (fontSize) {
                "custom" -> customFontPercent / 100f
                "small" -> .9f
                "large" -> 1.15f
                "extra" -> 1.3f
                else -> 1f
            }
}

fun appearanceFromPreferences(
    prefs: androidx.datastore.preferences.core.Preferences
): AppAppearance {
    val palette =
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("palette")].takeIf {
            it in paletteNames
        } ?: "system"
    val font =
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("fontSize")].takeIf {
            it in fontSizes || it in legacyFontPercents
        } ?: "system"
    val (mode, percent) =
        normalizeFontSize(
            font,
            prefs[androidx.datastore.preferences.core.intPreferencesKey("customFontPercent")] ?: 100,
        )
    val family =
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("fontFamily")]?.takeIf {
            it in fontFamilies
        } ?: "system"
    val fontId =
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("customFontId")]?.takeIf {
            it.matches(Regex("[a-f0-9]{32}"))
        } ?: ""
    return AppAppearance(
        palette,
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("pureBlack")] ?: false,
        mode,
        percent,
        family,
        fontId,
    )
}

val LocalAppearance = staticCompositionLocalOf { AppAppearance() }

fun paletteColor(key: String) =
    when (key) {
        "blue" -> Color(0xFF245BA8)
        "violet" -> Color(0xFF6C4AB6)
        "amber" -> Color(0xFF935B14)
        "rose" -> Color(0xFFAD4669)
        "mint" -> Color(0xFF32695B)
        "sky" -> Color(0xFF356480)
        "lilac" -> Color(0xFF695685)
        "peach" -> Color(0xFF85553F)
        "oat" -> Color(0xFF70603F)
        "sakura" -> Color(0xFF87546B)
        else -> Color(0xFF276B60)
    }

fun palettePreviewColor(key: String) =
    when (key) {
        "mint" -> Color(0xFFB8E5D5)
        "sky" -> Color(0xFFBFDEF2)
        "lilac" -> Color(0xFFDDCEEE)
        "peach" -> Color(0xFFF4D4C2)
        "oat" -> Color(0xFFE9DFBF)
        "sakura" -> Color(0xFFF0CFDE)
        else -> paletteColor(key)
    }

/** Strengthen accent saturation once per theme, keeping light/dark luminance appropriate. */
private fun vividAccent(color: Color, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.22f + .035f).coerceAtMost(.88f)
    hsv[2] = if (dark) hsv[2].coerceIn(.76f, .97f) else (hsv[2] * .94f).coerceAtMost(.76f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun readableAccent(accent: Color, background: Color): Color {
    var result = accent
    val base = background.luminance()
    val target = if (base < .35f) Color.White else Color.Black
    repeat(12) {
        val foreground = result.luminance()
        val contrast = (maxOf(base, foreground) + .05f) / (minOf(base, foreground) + .05f)
        if (contrast >= 4.5f) return result
        result = lerp(result, target, .12f)
    }
    return target
}

fun richerLightColors(scheme: ColorScheme): ColorScheme {
    val primary = readableAccent(vividAccent(scheme.primary, false), scheme.surface)
    val secondary = readableAccent(vividAccent(scheme.secondary, false), scheme.surface)
    val primaryContainer = lerp(scheme.primaryContainer, primary, .18f)
    val secondaryContainer = lerp(scheme.secondaryContainer, secondary, .18f)
    return scheme.copy(
        primary = primary,
        secondary = secondary,
        onPrimaryContainer = readableAccent(primary, primaryContainer),
        onSecondaryContainer = readableAccent(secondary, secondaryContainer),
        onSurface = lerp(scheme.onSurface, Color.Black, .12f),
        onSurfaceVariant = lerp(scheme.onSurfaceVariant, primary, .16f),
        primaryContainer = primaryContainer,
        secondaryContainer = secondaryContainer,
        surfaceContainerLow = lerp(scheme.surfaceContainerLow, primary, .025f),
        surfaceContainer = lerp(scheme.surfaceContainer, primary, .025f),
        surfaceContainerHigh = lerp(scheme.surfaceContainerHigh, primary, .025f),
    )
}

fun richerDarkColors(scheme: ColorScheme): ColorScheme {
    val primary = readableAccent(vividAccent(scheme.primary, true), scheme.surface)
    val secondary = readableAccent(vividAccent(scheme.secondary, true), scheme.surface)
    val liftedLow = lerp(scheme.surfaceContainerLow, Color.White, .025f)
    val liftedHigh = lerp(scheme.surfaceContainerHigh, Color.White, .035f)
    val primaryContainer = lerp(liftedHigh, primary, .30f)
    val secondaryContainer = lerp(liftedHigh, secondary, .28f)
    return scheme.copy(
        primary = primary,
        secondary = secondary,
        primaryContainer = primaryContainer,
        secondaryContainer = secondaryContainer,
        onPrimaryContainer = readableAccent(primary, primaryContainer),
        onSecondaryContainer = readableAccent(secondary, secondaryContainer),
        onSurface = lerp(scheme.onSurface, Color.White, .12f),
        onSurfaceVariant =
            readableAccent(lerp(scheme.onSurfaceVariant, Color.White, .08f), liftedLow),
        surfaceContainerLow = liftedLow,
        surfaceContainer = lerp(scheme.surfaceContainer, Color.White, .025f),
        surfaceContainerHigh = liftedHigh,
        surfaceContainerHighest = lerp(scheme.surfaceContainerHighest, Color.White, .03f),
    )
}

@Composable
fun appearanceColors(context: Context, dark: Boolean, appearance: AppAppearance): ColorScheme {
    // Font changes do not invalidate the colour scheme or query platform dynamic colours again.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(context, dark, appearance.palette, appearance.pureBlack, configuration) {
        val seed = paletteColor(appearance.palette)
        val primary = if (dark) lerp(seed, Color.White, .5f) else seed
        val container = if (dark) lerp(seed, Color.Black, .65f) else lerp(seed, Color.White, .85f)
        val scheme =
            if (appearance.palette == "system" && Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (dark)
                darkColorScheme(
                    primary = primary,
                    onPrimary = Color.Black,
                    primaryContainer = container,
                    onPrimaryContainer = Color.White,
                    secondary = primary,
                    secondaryContainer = container,
                    onSecondaryContainer = Color.White,
                )
            else
                lightColorScheme(
                    primary = seed,
                    primaryContainer = container,
                    onPrimaryContainer = lerp(seed, Color.Black, .5f),
                    secondary = seed,
                    secondaryContainer = container,
                    onSecondaryContainer = lerp(seed, Color.Black, .5f),
                    background = lerp(seed, Color.White, .97f),
                    surface = lerp(seed, Color.White, .97f),
                )
        val themed =
            if (dark && appearance.pureBlack)
                scheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF101010),
                    surfaceContainer = Color(0xFF141414),
                    surfaceContainerHigh = Color(0xFF1A1A1A),
                    surfaceContainerHighest = Color(0xFF222222),
                )
            else if (
                !dark &&
                    appearance.palette in setOf("mint", "sky", "lilac", "peach", "oat", "sakura")
            ) {
                val pastel = palettePreviewColor(appearance.palette)
                scheme.copy(
                    background = lerp(pastel, Color.White, .84f),
                    surface = lerp(pastel, Color.White, .84f),
                    primaryContainer = lerp(pastel, Color.White, .2f),
                    secondaryContainer = lerp(pastel, Color.White, .3f),
                    surfaceContainerLowest = lerp(pastel, Color.White, .96f),
                    surfaceContainerLow = lerp(pastel, Color.White, .76f),
                    surfaceContainer = lerp(pastel, Color.White, .65f),
                    surfaceContainerHigh = lerp(pastel, Color.White, .52f),
                    surfaceContainerHighest = lerp(pastel, Color.White, .42f),
                )
            } else scheme
        if (dark) richerDarkColors(themed) else richerLightColors(themed)
    }
}

fun appearanceTypography(
    appearance: AppAppearance,
    family: FontFamily = systemFontFamily(appearance.fontFamily),
): Typography {
    val base = Typography()
    fun font(style: TextStyle) =
        style.copy(
            fontFamily = family,
            fontSize = style.fontSize * appearance.scale,
            lineHeight = style.lineHeight * appearance.scale,
        )
    return Typography(
        displayLarge = font(base.displayLarge),
        displayMedium = font(base.displayMedium),
        displaySmall = font(base.displaySmall),
        headlineLarge = font(base.headlineLarge),
        headlineMedium = font(base.headlineMedium),
        headlineSmall = font(base.headlineSmall),
        titleLarge = font(base.titleLarge),
        titleMedium = font(base.titleMedium),
        titleSmall = font(base.titleSmall),
        bodyLarge = font(base.bodyLarge),
        bodyMedium = font(base.bodyMedium),
        bodySmall = font(base.bodySmall),
        labelLarge = font(base.labelLarge),
        labelMedium = font(base.labelMedium),
        labelSmall = font(base.labelSmall),
    )
}

fun fontSizeLabel(style: AppAppearance) =
    if (style.fontSize == "custom") "自定义 ${style.customFontPercent}%"
    else fontSizes[style.fontSize] ?: "跟随系统"
