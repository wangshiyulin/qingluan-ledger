package com.qingluan.ledger

import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Release113Test {
    @Test
    fun palettesAndDynamicColorsFollowScheduleReference() {
        assertEquals(
            listOf("系统动态", "青玉", "鸢尾", "海盐蓝", "玫瑰", "琥珀", "石墨"),
            paletteNames.values.toList(),
        )
        val context = RuntimeEnvironment.getApplication()
        val platform = androidx.compose.material3.dynamicDarkColorScheme(context)
        val dark = dynamicBackdrop(platform, true)
        assertEquals(platform.primary, dark.primary)
        assertEquals(androidx.compose.ui.graphics.Color.Black, dark.background)
        assertEquals(0xFF23292B.toInt(), dark.surfaceContainerLow.toArgb())
        val jade = ledgerColorScheme("jade", false)
        assertEquals(0xFF08735F.toInt(), jade.primary.toArgb())
        assertEquals(0xFFEDF1F2.toInt(), jade.surfaceContainerLow.toArgb())
    }

    @Test
    fun legacyBackupPaletteRestoresAsNewChoiceWithoutChangingBills() {
        val cats = listOf(Category(1, "餐饮", "EXPENSE"), Category(2, "工资", "INCOME"))
        val entry = Entry(id = 1, amount = 125, categoryId = 1)
        val raw = JSONObject(CompleteBackup.encode(cats, listOf(entry), PersonalBackup()))
        raw.getJSONObject("personal").put("palette", "blue")
        val restored = CompleteBackup.decode(raw.toString())
        assertEquals("ocean", restored.personal!!.appearance.palette)
        assertEquals(listOf(entry), restored.entries)
        assertEquals(
            "ocean",
            JSONObject(CompleteBackup.encode(cats, listOf(entry), restored.personal))
                .getJSONObject("personal")
                .getString("palette"),
        )
    }

}
