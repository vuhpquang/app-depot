package com.vuhpquang.appdepot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VerifyTest {
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private fun file(text: String) = File.createTempFile("verify", ".apk").apply { writeText(text); deleteOnExit() }

    @Test fun sha256OfAbc() = assertEquals(abc, sha256Hex("abc".byteInputStream()))

    @Test fun okWhenSizeAndHashMatch() = assertEquals(VerifyResult.Ok, verifyDownload(file("abc"), 3, abc))

    @Test fun hashIsCaseInsensitive() = assertEquals(VerifyResult.Ok, verifyDownload(file("abc"), 3, abc.uppercase()))

    @Test fun wrongSize() = assertEquals(VerifyResult.WrongSize, verifyDownload(file("abc"), 4, abc))

    @Test fun wrongHash() = assertEquals(VerifyResult.WrongHash, verifyDownload(file("abd"), 3, abc))

    @Test fun signerMatching() {
        assertTrue(signerMatches("AB12", "ab12"))
        assertTrue(signerMatches(null, null))
        assertTrue(signerMatches("ab12", null))
        assertFalse(signerMatches("ab12", "cd34"))
        assertFalse(signerMatches(null, "ab12"))
    }
}
