package com.qingluan.ledger

import java.util.Locale

/** Unicode region indicators rendered by the system font; no bundled flag artwork. */
private val currencyRegions =
    mapOf(
        "EUR" to "EU",
        "BOV" to "BO",
        "CHE" to "CH",
        "CHW" to "CH",
        "CLF" to "CL",
        "COU" to "CO",
        "MXV" to "MX",
        "USN" to "US",
        "UYI" to "UY",
        "UYW" to "UY",
    )
private val isoRegions = Locale.getISOCountries().toSet()

fun currencyFlag(code: String): String? {
    // X-prefixed codes include shared currencies, precious metals and accounting units.
    if (code.length != 3 || code.any { it !in 'A'..'Z' } || code.startsWith("X")) return null
    val region = currencyRegions[code] ?: code.take(2).takeIf { it in isoRegions } ?: return null
    return buildString { region.forEach { appendCodePoint(0x1F1E6 + (it - 'A')) } }
}
