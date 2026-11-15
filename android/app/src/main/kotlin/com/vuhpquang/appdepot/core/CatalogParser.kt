package com.vuhpquang.appdepot.core

import kotlinx.serialization.json.Json

internal val catalogJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Parses the `/api/depot/catalog` envelope (`data.apps`). */
fun parseCatalog(json: String): Result<List<CatalogApp>> = runCatching {
    val data = catalogJson.decodeFromString<CatalogEnvelope>(json).data
        ?: error("Catalog response has no data")
    data.apps
}
