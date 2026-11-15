package com.qingluan.ledger

import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

internal enum class LedgerPalette(
    val key: String,
    val title: String,
    val seed: Color,
    val night: Color,
) {
    Jade("jade", "青玉", Color(0xFF08735F), Color(0xFF9FE3EB)),
    Iris("iris", "鸢尾", Color(0xFF6557A8), Color(0xFFC6BCFF)),
    Ocean("ocean", "海盐蓝", Color(0xFF32689A), Color(0xFFA2CAEF)),
    Rose("rose", "玫瑰", Color(0xFF9B526C), Color(0xFFF2B3CB)),
    Amber("amber", "琥珀", Color(0xFF8C641E), Color(0xFFE9C17E)),
    Slate("slate", "石墨", Color(0xFF52616D), Color(0xFFBACAD7)),
}

val paletteNames =
    linkedMapOf("system" to "系统动态").apply {
        LedgerPalette.entries.forEach { put(it.key, it.title) }
    }
internal val legacyPalettes =
    mapOf(
        "blue" to "ocean",
        "violet" to "iris",
        "mint" to "jade",
        "sky" to "ocean",
        "lilac" to "iris",
        "peach" to "amber",
        "oat" to "amber",
        "sakura" to "rose",
    )

internal fun normalizePalette(key: String): String =
    if (key in paletteNames) key else legacyPalettes[key] ?: "system"

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
        normalizePalette(
            prefs[androidx.datastore.preferences.core.stringPreferencesKey("palette")] ?: "system"
        )
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
internal val LocalDarkAppearance = staticCompositionLocalOf { false }

fun paletteColor(key: String): Color =
    (LedgerPalette.entries.firstOrNull { it.key == normalizePalette(key) } ?: LedgerPalette.Jade)
        .seed

internal fun dynamicBackdrop(colors: ColorScheme, dark: Boolean): ColorScheme =
    if (!dark) colors
    else
        colors.copy(
            surface = Color.Black,
            background = Color.Black,
            onSurface = Color.White,
            onBackground = Color.White,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF252B2D),
            surfaceContainerLowest = Color(0xFF080B0C),
            surfaceContainerLow = Color(0xFF23292B),
            surfaceContainer = Color(0xFF292F31),
            surfaceContainerHigh = Color(0xFF303638),
            surfaceContainerHighest = Color(0xFF384043),
        )

internal fun ledgerColorScheme(key: String, dark: Boolean): ColorScheme {
    val palette =
        LedgerPalette.entries.firstOrNull { it.key == normalizePalette(key) } ?: LedgerPalette.Jade
    val seed = palette.seed
    val primary = if (dark) palette.night else seed
    fun tone(color: Long, amount: Float = .035f) = lerp(Color(color), seed, amount)
    val surface = if (dark) Color(0xFF101416) else Color(0xFFF8FAFC)
    val container = if (dark) lerp(Color(0xFF20242B), seed, .16f) else lerp(Color.White, seed, .13f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = if (dark) Color(0xFF111820) else Color.White,
        primaryContainer = container,
        onPrimaryContainer =
            if (dark) lerp(Color.White, primary, .16f) else lerp(Color(0xFF151B22), seed, .28f),
        secondary = if (dark) Color(0xFFC0C8D4) else Color(0xFF58616F),
        onSecondary = if (dark) Color(0xFF26303D) else Color.White,
        secondaryContainer = container,
        onSecondaryContainer = if (dark) Color(0xFFE4E9F3) else Color(0xFF273442),
        tertiary = if (dark) Color(0xFFB5C8FF) else Color(0xFF435CA9),
        onTertiary = if (dark) Color(0xFF102655) else Color.White,
        tertiaryContainer = if (dark) Color(0xFF293C67) else Color(0xFFDEE6FF),
        onTertiaryContainer = if (dark) Color(0xFFE0E8FF) else Color(0xFF172A58),
        background = surface,
        onBackground = if (dark) Color(0xFFE7EAF0) else Color(0xFF1A1D24),
        surface = surface,
        onSurface = if (dark) Color(0xFFE7EAF0) else Color(0xFF1A1D24),
        onSurfaceVariant = if (dark) Color(0xFFC0C6D1) else Color(0xFF535C69),
        surfaceDim = if (dark) tone(0xFF0D1015) else tone(0xFFDFE2E9),
        surfaceBright = if (dark) tone(0xFF353B45) else surface,
        surfaceContainerLowest = if (dark) tone(0xFF0B0E12) else Color.White,
        surfaceContainerLow = if (dark) Color(0xFF23292B) else Color(0xFFEDF1F2),
        surfaceContainer = if (dark) tone(0xFF21262F) else tone(0xFFEDF0F6),
        surfaceContainerHigh = if (dark) tone(0xFF292F39) else tone(0xFFE7EBF2),
        surfaceContainerHighest = if (dark) tone(0xFF343C47) else tone(0xFFE1E6EF),
        outline = if (dark) Color(0xFF65707F) else Color(0xFFA8B0BD),
        outlineVariant = if (dark) tone(0xFF3D4653) else tone(0xFFD6DDE6),
    )
}

@Composable
fun appearanceColors(context: Context, dark: Boolean, appearance: AppAppearance): ColorScheme {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(context, dark, appearance.palette, appearance.pureBlack, configuration) {
        val key = normalizePalette(appearance.palette)
        val scheme =
            if (key == "system")
                dynamicBackdrop(
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                    dark,
                )
            else ledgerColorScheme(key, dark)
        if (dark && appearance.pureBlack)
            scheme.copy(background = Color.Black, surface = Color.Black, surfaceDim = Color.Black)
        else scheme
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
