package com.vuhpquang.appdepot.core

private fun segments(v: String): List<Int> =
    v.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        .split('.').map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

/** Numeric segment comparison; missing segments count as 0. */
fun compareVersions(a: String, b: String): Int {
    val x = segments(a)
    val y = segments(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}
