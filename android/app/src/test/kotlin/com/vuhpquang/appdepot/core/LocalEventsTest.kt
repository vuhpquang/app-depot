package com.vuhpquang.appdepot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEventsTest {
    @Test fun uninstallStaysOnTheDevice() {
        assertFalse(isUploaded("uninstall"))
        assertTrue(isUploaded("install"))
        assertTrue(isUploaded("update"))
        assertTrue(isUploaded("open"))
    }
}
