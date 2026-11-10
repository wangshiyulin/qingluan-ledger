package com.qingluan.ledger

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLogTest {
    @Test
    fun boundedAnonymousLogAndUnsetLinks() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        AppLog.clear(context)
        try {
            repeat(103) {
                AppLog.record(context, "用户名-银行卡-秘密备注", IllegalArgumentException("秘密密码123"))
            }
            val events = AppLog.read(context)
            assertEquals(100, events.size)
            val text = AppLog.text(events)
            assertFalse(text.contains("秘密"))
            assertFalse(text.contains("银行卡"))
            assertFalse(text.contains("密码123"))
            assertTrue(text.contains("IllegalArgumentException"))
            assertEquals("https://ledger.qingluanx.com", AboutLinks.website)
            assertEquals("https://qingluanx.com", AboutLinks.blog)
            assertEquals("https://github.com/wangshiyulin/qingluan-ledger", AboutLinks.github)
            assertEquals(AboutLinks.github + "/releases", AboutLinks.updates)
            assertEquals(AboutLinks.github + "/issues", AboutLinks.feedback)
            assertFalse(openAboutLink(context, "javascript:alert(1)"))
            AppLog.clear(context)
            assertTrue(AppLog.read(context).isEmpty())
        } finally {
            AppLog.clear(context)
        }
    }
}
