package com.vuhpquang.appdepot.core

private val serverUrlRe = Regex("""^https?://[^\s/]+(/\S*)?$""")

/** A usable http(s) base URL without the trailing slash, or null. Used by the debug-only Server field. */
fun normalizeServerUrl(input: String): String? {
    val s = input.trim().trimEnd('/')
    return if (serverUrlRe.matches(s)) s else null
}
