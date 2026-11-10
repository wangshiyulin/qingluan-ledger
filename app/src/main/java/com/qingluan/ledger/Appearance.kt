package com.qingluan.ledger

import android.content.Context
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
val fontSizes =
    linkedMapOf(
        "system" to "跟随系统",
        "small" to "小",
        "large" to "大",
        "extra" to "特大",
        "custom" to "自定义",
    )

data class AppAppearance(
    val palette: String = "system",
    val pureBlack: Boolean = false,
    val fontSize: String = "system",
    val customFontPercent: Int = 100,
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
            it in fontSizes
        } ?: "system"
    return AppAppearance(
        palette,
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("pureBlack")] ?: false,
        font,
        (prefs[androidx.datastore.preferences.core.intPreferencesKey("customFontPercent")] ?: 100)
            .coerceIn(75, 160),
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

/** Lift icon containers above dark cards without losing their theme tint. */
fun readableDarkContainers(scheme: ColorScheme) =
    scheme.copy(
        primaryContainer = lerp(scheme.surfaceContainerHigh, scheme.primary, .30f),
        onPrimaryContainer = scheme.onSurface,
        secondaryContainer = lerp(scheme.surfaceContainerHigh, scheme.secondary, .26f),
        onSecondaryContainer = scheme.onSurface,
    )

/** Moderate tint increase, including system dynamic colours and the pastel palettes. */
fun richerLightColors(scheme: ColorScheme): ColorScheme =
    scheme.copy(
        primary = lerp(scheme.primary, Color.Black, .05f),
        secondary = lerp(scheme.secondary, Color.Black, .05f),
        primaryContainer = lerp(scheme.primaryContainer, scheme.primary, .14f),
        secondaryContainer = lerp(scheme.secondaryContainer, scheme.secondary, .14f),
        surfaceContainerLow = lerp(scheme.surfaceContainerLow, scheme.primary, .025f),
        surfaceContainer = lerp(scheme.surfaceContainer, scheme.primary, .025f),
        surfaceContainerHigh = lerp(scheme.surfaceContainerHigh, scheme.primary, .025f),
    )

fun richerDarkColors(scheme: ColorScheme): ColorScheme =
    scheme.copy(
        primary = lerp(scheme.primary, Color.Black, .04f),
        secondary = lerp(scheme.secondary, Color.Black, .04f),
        primaryContainer = lerp(scheme.primaryContainer, Color.Black, .04f),
        secondaryContainer = lerp(scheme.secondaryContainer, Color.Black, .04f),
    )

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
        val adjusted = if (dark) readableDarkContainers(scheme) else scheme
        val themed =
            if (dark && appearance.pureBlack)
                adjusted.copy(
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
                adjusted.copy(
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
            } else adjusted
        if (dark) richerDarkColors(themed) else richerLightColors(themed)
    }
}

fun appearanceTypography(appearance: AppAppearance): Typography {
    val base = Typography()
    fun font(style: TextStyle) =
        style.copy(
            fontFamily = FontFamily.Default,
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
    else fontSizes.getValue(style.fontSize)
