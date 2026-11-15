package com.vuhpquang.appdepot.core

/** This device's platform in the catalog. Install, update, background and history only ever touch these entries. */
const val ANDROID = "android"

/**
 * The catalog entries this device can install. Entries of other platforms may share an app id
 * (App Depot macOS and Android are both `com.vuhpquang.appdepot`), so never look one up by app id alone.
 */
fun localApps(apps: List<CatalogApp>, platform: String = ANDROID): List<CatalogApp> = apps.filter { it.platform == platform }

/**
 * One app across platforms: catalog entries grouped by `projectSlug` (else `slug`).
 * [display] (name, tagline, icon) is this platform's entry when there is one, else the first entry.
 */
data class Product(val key: String, val display: CatalogApp, val variants: Map<String, CatalogApp>) {
    /** Platforms this app ships on, [local] first, the rest alphabetically. */
    fun platforms(local: String): List<String> = variants.keys.sortedWith(compareBy({ it != local }, { it }))

    /** The entries for other platforms, in [platforms] order. */
    fun others(local: String): List<CatalogApp> = platforms(local).filter { it != local }.map { variants.getValue(it) }
}

/** Products in the order their first entry appears in the catalog. */
fun groupProducts(apps: List<CatalogApp>, local: String = ANDROID): List<Product> =
    apps.groupBy { it.projectSlug?.takeIf(String::isNotBlank) ?: it.slug }.map { (key, entries) ->
        // A second entry for the same platform would be a catalog mistake: the first one wins.
        val variants = LinkedHashMap<String, CatalogApp>()
        entries.forEach { variants.putIfAbsent(it.platform, it) }
        Product(key, variants[local] ?: entries.first(), variants)
    }

/** Search over a product: any of its entries' name or tagline. */
fun matchesQuery(product: Product, query: String): Boolean =
    matchesQuery(product.display, query) || product.variants.values.any { matchesQuery(it, query) }
