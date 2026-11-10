package com.qingluan.ledger

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DesktopEntryTest {
    @Test
    fun knownActionsOpenTheCorrectDraftAndUnknownActionsAreIgnored() {
        assertNull(DesktopEntry.parse(null))
        assertNull(DesktopEntry.parse(Intent(Intent.ACTION_MAIN)))
        assertNull(DesktopEntry.parse(Intent("other.action")))
        assertEquals("EXPENSE", DesktopEntry.parse(Intent(DesktopEntry.expenseAction)))
        assertEquals("INCOME", DesktopEntry.parse(Intent(DesktopEntry.incomeAction)))
    }
}
