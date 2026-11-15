package com.vuhpquang.appdepot.core

import kotlinx.serialization.Serializable

@Serializable
data class CatalogRelease(
    val version: String,
    val build: String? = null,
    val notes: String = "",
    val assetName: String,
    val downloadUrl: String,
    val sha256: String,
    val size: Long,
    val publishedAt: String,
    val minOs: String? = null,
)

@Serializable
data class CatalogApp(
    val slug: String,
    val name: String,
    val tagline: String,
    val platform: String,
    val appId: String,
    val repo: String,
    val iconUrl: String? = null,
    val projectSlug: String? = null,
    val signer: String? = null,
    val relaunchArgs: List<String> = emptyList(),
    val installMode: String = "managed",
    val latest: CatalogRelease? = null,
)

@Serializable
internal data class CatalogData(val apps: List<CatalogApp> = emptyList())

@Serializable
internal data class CatalogEnvelope(val data: CatalogData? = null)
