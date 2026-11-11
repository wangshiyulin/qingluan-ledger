package com.qingluan.ledger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagSearchInsightsTest {
    private val entry = Entry(amount = 1200, categoryId = 1, tags = "午餐、工作、午餐")

    @Test fun tagSearchSupportsAnyAllAndExactNames() {
        assertTrue(BillSearch(tagNames = listOf("午餐", "晚餐")).matches(entry, null))
        assertFalse(BillSearch(tagNames = listOf("午餐", "晚餐"), tagMode = "all").matches(entry, null))
        assertTrue(BillSearch(tagNames = listOf("午餐", "工作"), tagMode = "all").matches(entry, null))
        assertFalse(BillSearch(tagNames = listOf("餐")).matches(entry, null))
        assertTrue(BillSearch().matches(entry, null))
        assertTrue(BillSearch().matches(entry.copy(type = "INCOME"), null))
        assertFalse(BillSearch(categoryId = 2, tagNames = listOf("午餐")).matches(entry, null))
    }

    @Test fun oldPresetsAndExperienceKeepDefaultsAndRoundTripTags() {
        val old = BillSearch().json().apply { remove("tagNames"); remove("tagMode") }
        assertEquals(emptyList<String>(), BillSearch.decode(old).tagNames)
        assertEquals("any", BillSearch.decode(old).tagMode)
        val saved = BillSearch(tagNames = listOf("历史标签"), tagMode = "all")
        assertEquals(saved, BillSearch.decode(saved.json()))
        assertTrue(Experience.decode(JSONObject()).showTags)
        assertFalse(Experience.decode(Experience(showTags = false).json()).showTags)
    }

    @Test fun tagTotalsCountRepeatedNamesOnceAndKeepWholeBillAmounts() {
        val totals = tagTotals(listOf(entry, entry.copy(id = 2, amount = 300, tags = "午餐")))
        assertEquals(TagTotal("午餐", 2, 1500), totals.find { it.name == "午餐" })
        assertEquals(TagTotal("工作", 1, 1200), totals.find { it.name == "工作" })
        assertThrows(IllegalArgumentException::class.java) {
            tagTotals(listOf(entry, entry.copy(currencyCode = "USD")))
        }
    }

    @Test fun tagFilterRejectsUnboundedAndMalformedConditions() {
        assertThrows(IllegalArgumentException::class.java) { BillSearch(tagMode = "unknown").validate() }
        assertThrows(IllegalArgumentException::class.java) { BillSearch(tagNames = listOf("午餐、晚餐")).validate() }
        assertThrows(IllegalArgumentException::class.java) { BillSearch(tagNames = List(31) { "tag$it" }).validate() }
    }
}
