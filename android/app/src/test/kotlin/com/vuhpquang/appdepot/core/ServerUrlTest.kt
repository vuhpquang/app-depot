package com.vuhpquang.appdepot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test fun keepsHttpAndHttpsAndDropsTrailingSlash() {
        assertEquals("https://vuhpquang.com", normalizeServerUrl(" https://vuhpquang.com/ "))
        assertEquals("http://10.0.2.2:3000", normalizeServerUrl("http://10.0.2.2:3000"))
    }

    @Test fun rejectsEmptyAndNonHttp() {
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("vuhpquang.com"))
        assertNull(normalizeServerUrl("ftp://x"))
        assertNull(normalizeServerUrl("https://"))
        assertNull(normalizeServerUrl("https://a b"))
    }
}
