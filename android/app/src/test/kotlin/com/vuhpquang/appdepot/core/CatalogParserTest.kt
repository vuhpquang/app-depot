package com.vuhpquang.appdepot.core

import org.junit.Assert.*
import org.junit.Test

class CatalogParserTest {
    private fun sample() = javaClass.classLoader!!.getResource("catalog-android.json")!!.readText()

    @Test fun parsesLiveSample() {
        val apps = parseCatalog(sample()).getOrThrow()
        assertEquals(2, apps.size)
        val z = apps.first { it.slug == "zentask-android" }
        assertEquals("0.15.2", z.latest?.version)
        assertEquals("com.vuhpquang.zentask", z.appId)
        assertEquals("managed", z.installMode)
        assertEquals(57316023L, z.latest?.size)
    }

    @Test fun malformedFails() {
        assertTrue(parseCatalog("{not json").isFailure)
        assertTrue(parseCatalog("""{"data":null}""").isFailure)
    }

    @Test fun unknownKeysIgnored() {
        val j = """{"data":{"apps":[{"slug":"a","name":"A","tagline":"t","platform":"android","appId":"x.y","repo":"o/r","extra":1,"latest":null}]},"zzz":true}"""
        val apps = parseCatalog(j).getOrThrow()
        assertEquals(1, apps.size)
        assertNull(apps[0].latest)
        assertEquals(emptyList<String>(), apps[0].relaunchArgs)
    }
}
