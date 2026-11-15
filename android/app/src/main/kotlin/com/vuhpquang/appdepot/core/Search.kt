package com.vuhpquang.appdepot.core

import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{Mn}+")

/** Lowercase, without diacritics (Vietnamese đ included), so "ghi chu" finds "Ghi chú". */
fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace('đ', 'd').replace('Đ', 'D')
        .lowercase(Locale.ROOT)

/** Blank matches everything; otherwise a case- and accent-insensitive substring of the name or tagline. */
fun matchesQuery(app: CatalogApp, query: String): Boolean {
    val q = foldForSearch(query.trim())
    if (q.isEmpty()) return true
    return foldForSearch(app.name).contains(q) || foldForSearch(app.tagline).contains(q)
}
