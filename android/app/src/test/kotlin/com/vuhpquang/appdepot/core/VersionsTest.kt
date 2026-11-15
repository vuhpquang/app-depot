package com.vuhpquang.appdepot.core

import org.junit.Assert.*
import org.junit.Test

class VersionsTest {
    @Test fun numericNotLexical() = assertTrue(compareVersions("0.15.10", "0.15.2") > 0)
    @Test fun equalWithMissingSegments() = assertEquals(0, compareVersions("1.2", "1.2.0"))
    @Test fun lessThan() = assertTrue(compareVersions("0.15.1", "0.15.2") < 0)
    @Test fun ignoresSuffixAndPrefix() = assertEquals(0, compareVersions("v1.2.3", "1.2.3-debug"))
}
