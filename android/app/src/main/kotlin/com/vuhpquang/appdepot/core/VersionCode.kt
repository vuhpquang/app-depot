package com.vuhpquang.appdepot.core

private val PLAIN_VERSION = Regex("""\d+\.\d+\.\d+""")

/** "0.1.0" -> 100, "1.2.3" -> 10203. Same formula as `versionCode` in app/build.gradle.kts. */
fun versionCodeFor(version: String): Int {
    val v = version.trim()
    require(PLAIN_VERSION.matches(v)) { "Version must be X.Y.Z, was '$version'" }
    val (major, minor, patch) = v.split(".").map { it.toInt() }
    return major * 10_000 + minor * 100 + patch
}
