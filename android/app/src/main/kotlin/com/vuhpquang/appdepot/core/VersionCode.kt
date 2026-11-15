package com.vuhpquang.appdepot.core

/** "0.1.0" -> 100, "1.2.3" -> 10203. Same formula as `versionCode` in app/build.gradle.kts. */
fun versionCodeFor(version: String): Int {
    val (major, minor, patch) = version.trim().split(".").map { it.toInt() }
    return major * 10_000 + minor * 100 + patch
}
