package com.vuhpquang.appdepot.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

enum class VerifyResult { Ok, WrongSize, WrongHash }

fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        digest.update(buf, 0, n)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun verifyDownload(file: File, expectedSize: Long, expectedSha256: String): VerifyResult {
    if (file.length() != expectedSize) return VerifyResult.WrongSize
    val actual = file.inputStream().use { sha256Hex(it) }
    return if (actual.equals(expectedSha256, ignoreCase = true)) VerifyResult.Ok else VerifyResult.WrongHash
}

/** True when the catalog names no signer, otherwise the APK's cert digest must equal it (hex, any case). */
fun signerMatches(apkCertSha256: String?, catalogSigner: String?): Boolean {
    if (catalogSigner == null) return true
    return apkCertSha256 != null && apkCertSha256.equals(catalogSigner, ignoreCase = true)
}
