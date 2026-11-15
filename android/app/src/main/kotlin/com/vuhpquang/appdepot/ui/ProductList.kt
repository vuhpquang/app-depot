package com.vuhpquang.appdepot.ui

import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.Product
import com.vuhpquang.appdepot.core.groupProducts
import com.vuhpquang.appdepot.core.matchesQuery

/** Home platform filter, persisted as `appdepot.ui.platformFilter`. */
enum class PlatformFilter(val key: String) {
    Device("device"), All("all");

    companion object {
        fun fromKey(key: String?): PlatformFilter = entries.firstOrNull { it.key == key } ?: Device
    }
}

/** A Home card: one product, plus the planned row of its variant for this device (null = not on this phone). */
data class ProductRow(val product: Product, val row: AppRow?) {
    /** The entry the card shows: this device's variant, else the product's display entry. */
    val app: CatalogApp get() = row?.app ?: product.display
    val isLocal: Boolean get() = row != null
    val model: CardModel get() = row?.model ?: remoteCardModel(product)
}

/** Groups the full catalog into products; [rowFor] is called for this device's entries only. */
fun productRows(apps: List<CatalogApp>, local: String, rowFor: (CatalogApp) -> AppRow): List<ProductRow> =
    groupProducts(apps, local).map { p -> ProductRow(p, p.variants[local]?.let(rowFor)) }

data class FilterCounts(val device: Int, val all: Int)

fun filterCounts(products: List<ProductRow>) = FilterCounts(products.count { it.isLocal }, products.size)

/** What Home lists: this device's products, then (filter All only) the ones for other platforms. */
data class HomeSections(val local: List<ProductRow>, val other: List<ProductRow>) {
    val isEmpty: Boolean get() = local.isEmpty() && other.isEmpty()
}

fun homeSections(products: List<ProductRow>, filter: PlatformFilter, sort: SortOrder, query: String): HomeSections {
    val found = if (query.isBlank()) products else products.filter { matchesQuery(it.product, query) }
    val (local, other) = found.partition { it.isLocal }
    return HomeSections(
        sortProducts(local, sort),
        if (filter == PlatformFilter.All) sortProducts(other, sort) else emptyList(),
    )
}

fun sortProducts(rows: List<ProductRow>, order: SortOrder): List<ProductRow> =
    rows.sortedWith(rowComparator(order, { it.app }, { it.row?.state }))

/** A product with no build for this device: shown, never installable. */
fun remoteCardModel(product: Product) = CardModel(CardChip.NotOnThisDevice, null, product.display.latest?.version, null)

fun productMenuActions(p: ProductRow): List<MenuAction> =
    p.row?.let(::menuActions) ?: listOf(MenuAction.OpenOnWeb, MenuAction.ShareWebLink)

/** The product's page on vuhpquang.com (every catalog project has one). */
fun productWebUrl(product: Product): String = "https://vuhpquang.com/projects/${product.key}"

fun webShareText(product: Product): String = "${product.display.name}: ${productWebUrl(product)}"

