package com.vuhpquang.appdepot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private fun app(name: String, tagline: String = "") =
        CatalogApp(slug = name.lowercase(), name = name, tagline = tagline, platform = "android", appId = "x", repo = "v/x")

    @Test fun foldStripsVietnameseDiacriticsAndCase() {
        assertEquals("ghi chu ca nhan", foldForSearch("Ghi chú cá nhân"))
        assertEquals("duong di", foldForSearch("Đường đi"))
        assertEquals("tieng viet", foldForSearch("TIẾNG VIỆT"))
    }

    @Test fun blankQueryMatchesEverything() {
        assertTrue(matchesQuery(app("ZenTask"), ""))
        assertTrue(matchesQuery(app("ZenTask"), "   "))
    }

    @Test fun matchesNameOrTaglineCaseInsensitive() {
        assertTrue(matchesQuery(app("ZenTask", "Personal task manager"), "zen"))
        assertTrue(matchesQuery(app("ZenTask", "Personal task manager"), "TASK MAN"))
        assertFalse(matchesQuery(app("ZenTask", "Personal task manager"), "wiki"))
    }

    @Test fun matchesWithoutTypingAccents() {
        assertTrue(matchesQuery(app("Sổ tay", "Ghi chú cá nhân"), "ghi chu"))
        assertTrue(matchesQuery(app("Đường"), "duong"))
        assertTrue(matchesQuery(app("Wiki", "ghi chu"), "ghi chú"))
    }
}
