package com.vuhpquang.appdepot

import com.vuhpquang.appdepot.core.versionCodeFor
import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun versionCodeMatchesFormula() {
        assertEquals(100, versionCodeFor("0.1.0"))
        assertEquals(10203, versionCodeFor("1.2.3"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun versionCodeRejectsNonXYZ() {
        versionCodeFor("1.2")
    }
}
