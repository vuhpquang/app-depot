package com.vuhpquang.appdepot.ui

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.Product
import com.vuhpquang.appdepot.ui.components.AppCard
import com.vuhpquang.appdepot.ui.components.AppGridCard
import com.vuhpquang.appdepot.ui.components.UpdateStage
import com.vuhpquang.appdepot.ui.theme.DepotTokens

/**
 * Home, "Update Stage" (Material 3 Expressive): a large flexible bar that says the state in words,
 * the platform filter as connected toggle buttons, a hero card with every pending update and Update
 * all, then the other apps as connected groups (list) or tiles (grid).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(vm: AppDepotViewModel, onOpenApp: (String) -> Unit, onSettings: () -> Unit, onHistory: () -> Unit) {
    val ui by vm.home.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(ui.query.isNotEmpty()) }
    val uninstall = rememberUninstaller(vm)
    val closeSearch = { searching = false; vm.setQuery("") }
    BackHandler(enabled = searching) { closeSearch() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    val updates = ui.updatable.size
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (searching) {
                TopAppBar(
                    navigationIcon = { IconButton(closeSearch) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_close_search)) } },
                    title = { SearchField(ui.query, vm::setQuery) },
                    colors = barColors,
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(if (updates > 0) pluralStringResource(R.plurals.stage_title, updates, updates) else stringResource(R.string.app_name))
                    },
                    subtitle = {
                        when {
                            updates > 0 -> Text(stringResource(if (ui.busy) R.string.update_all_running else R.string.stage_subtitle_ready))
                            ui.loaded && ui.counts.device > 0 -> Text(stringResource(R.string.home_subtitle_current))
                        }
                    },
                    actions = {
                        IconButton({ searching = true }) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search)) }
                        SortMenu(ui.sort, vm::setSort)
                        MoreMenu(ui.layout, vm::setLayout, onHistory, onSettings)
                    },
                    colors = barColors,
                    scrollBehavior = scroll,
                )
            }
        },
    ) { padding ->
        val refresh = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = vm::refresh,
            state = refresh,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(state = refresh, isRefreshing = ui.refreshing, modifier = Modifier.align(Alignment.TopCenter))
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val grid = ui.layout == HomeLayout.Grid
            val staged = stageSplit(ui.sections, showStage = !searching && ui.query.isBlank())
            val sections = staged.sections
            val showPlatforms = ui.filter == PlatformFilter.All
            val deviceEmpty = ui.loaded && !ui.refreshing && ui.products.isNotEmpty() && ui.counts.device == 0 &&
                ui.filter == PlatformFilter.Device && ui.query.isBlank()
            val message = when {
                !ui.loaded || ui.refreshing -> null
                ui.products.isEmpty() -> stringResource(if (ui.refreshFailed) R.string.empty_load_failed else R.string.empty_no_apps)
                deviceEmpty -> stringResource(R.string.empty_device)
                sections.isEmpty && staged.stage.isEmpty() -> stringResource(R.string.search_no_results, ui.query.trim())
                else -> null
            }
            LazyVerticalGrid(
                columns = if (grid) GridCells.Adaptive(DepotTokens.GridCellMin) else GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = DepotTokens.SpaceMd, end = DepotTokens.SpaceMd, top = DepotTokens.SpaceSm, bottom = DepotTokens.SpaceXl),
                // List cards are links of a connected group (a 2 dp gap); headers and the stage add their own space.
                verticalArrangement = Arrangement.spacedBy(if (grid) DepotTokens.SpaceMd else DepotTokens.GroupGap),
                horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
            ) {
                val full: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
                val gap = Modifier.padding(bottom = if (grid) 0.dp else DepotTokens.SpaceMd - DepotTokens.GroupGap)
                if (!ui.loaded) {
                    item(key = "loading", span = full) {
                        Box(Modifier.fillMaxWidth().padding(top = DepotTokens.SpaceXl), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                }
                if (ui.products.isNotEmpty()) {
                    item(key = "filter", span = full) { PlatformFilterRow(ui.filter, ui.counts, vm::setFilter, gap) }
                }
                if (ui.refreshFailed && ui.products.isNotEmpty()) {
                    item(key = "refresh-failed", span = full) {
                        Text(
                            stringResource(R.string.refresh_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = gap,
                        )
                    }
                }
                if (staged.stage.isNotEmpty()) {
                    item(key = "stage", span = full) {
                        UpdateStage(
                            rows = staged.stage,
                            running = ui.busy,
                            size = formatSize(context, updateAllBytes(ui.rows)),
                            sizeOf = { p -> p.row?.let { formatSize(context, downloadBytes(it)) } },
                            onOpen = onOpenApp,
                            onUpdateAll = vm::updateAll,
                            onAllowInstalls = { context.startSafely(vm.unknownSourcesIntent()) },
                            modifier = gap.animateItem(),
                        )
                    }
                }
                val card: @Composable (ProductRow, GroupPosition, Modifier) -> Unit = { item, position, mod ->
                    val slug = item.app.slug
                    val onAction = { action: CardAction -> handleAction(vm, context, slug, action) }
                    val onMenu = { a: MenuAction -> handleMenu(vm, context, item, a, uninstall) }
                    val onAllow = { context.startSafely(vm.unknownSourcesIntent()) }
                    if (grid) {
                        AppGridCard(item, showPlatforms, { onOpenApp(slug) }, onAction, onAllow, onMenu, mod)
                    } else {
                        AppCard(item, showPlatforms, position, { onOpenApp(slug) }, onAction, onAllow, onMenu, mod)
                    }
                }
                if (staged.stage.isNotEmpty() && sections.local.isNotEmpty()) {
                    item(key = "local-header", span = full) { SectionHeader(stringResource(R.string.section_this_phone)) }
                }
                itemsIndexed(sections.local, key = { _, it -> it.product.key }) { i, it ->
                    card(it, groupPosition(i, sections.local.size), Modifier.animateItem())
                }
                if (sections.other.isNotEmpty()) {
                    item(key = "other-header", span = full) {
                        SectionHeader(
                            stringResource(R.string.section_other_platforms),
                            Modifier.padding(top = if (sections.local.isEmpty()) 0.dp else DepotTokens.SpaceMd),
                        )
                    }
                    itemsIndexed(sections.other, key = { _, it -> it.product.key }) { i, it ->
                        card(it, groupPosition(i, sections.other.size), Modifier.animateItem())
                    }
                }
                if (message != null) {
                    item(key = "empty", span = full) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = DepotTokens.SpaceXl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
                        ) {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (deviceEmpty) {
                                FilledTonalButton(
                                    { vm.setFilter(PlatformFilter.All) },
                                    modifier = Modifier.heightIn(min = DepotTokens.TouchMin),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.action_show_all))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = DepotTokens.SpaceMd, bottom = DepotTokens.SpaceSm),
    )
}

/** "This phone · 3" | "All · 9" as connected toggle buttons: which products Home lists. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlatformFilterRow(filter: PlatformFilter, counts: FilterCounts, onFilter: (PlatformFilter) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.filter_label)
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val n = PlatformFilter.entries.size
        PlatformFilter.entries.forEachIndexed { i, f ->
            val checked = filter == f
            ToggleButton(
                checked = checked,
                onCheckedChange = { onFilter(f) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    n - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).heightIn(min = DepotTokens.TouchMin).semantics { role = Role.RadioButton },
            ) {
                Icon(
                    if (checked) Icons.Outlined.Check else if (f == PlatformFilter.Device) Icons.Outlined.PhoneAndroid else Icons.Outlined.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                )
                Text(
                    when (f) {
                        PlatformFilter.Device -> stringResource(R.string.filter_device, counts.device)
                        PlatformFilter.All -> stringResource(R.string.filter_all, counts.all)
                    },
                    maxLines = 1,
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
    }
}

/** ⋮: List/Grid view, History, Settings. */
@Composable
private fun MoreMenu(layout: HomeLayout, onLayout: (HomeLayout) -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            val grid = layout == HomeLayout.Grid
            DropdownMenuItem(
                text = { Text(stringResource(if (grid) R.string.action_list_view else R.string.action_grid_view)) },
                leadingIcon = { Icon(if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, contentDescription = null) },
                onClick = { open = false; onLayout(if (grid) HomeLayout.List else HomeLayout.Grid) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_history)) },
                leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null) },
                onClick = { open = false; onHistory() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_settings)) },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = { open = false; onSettings() },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Local text state: a value that round-trips through the view model's flow drops fast keystrokes.
    var text by rememberSaveable { mutableStateOf(query) }
    val set = { v: String -> text = v; onChange(v) }
    TextField(
        value = text,
        onValueChange = set,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton({ set("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear_search)) }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@Composable
private fun SortMenu(current: SortOrder, onSort: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.action_sort)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            SortOrder.entries.forEach { order ->
                val label = when (order) {
                    SortOrder.UpdatesFirst -> R.string.sort_updates_first
                    SortOrder.Name -> R.string.sort_name
                    SortOrder.RecentlyReleased -> R.string.sort_recent
                }
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    // A check on the current order; the others keep the same indent.
                    leadingIcon = {
                        if (order == current) Icon(Icons.Outlined.Check, contentDescription = null)
                        else Box(Modifier.size(DepotTokens.IconRow))
                    },
                    onClick = { open = false; onSort(order) },
                )
            }
        }
    }
}

/** "57 MB" (system units and locale). */
fun formatSize(context: Context, bytes: Long?): String? =
    bytes?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }

/** Starts the system uninstall dialog and tells the view model when the user comes back. */
@Composable
fun rememberUninstaller(vm: AppDepotViewModel): (String) -> Unit {
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pending?.let(vm::afterUninstall)
        pending = null
    }
    return { slug ->
        vm.uninstallIntent(slug)?.let { intent ->
            pending = slug
            runCatching { launcher.launch(intent) }.onFailure { pending = null }
        }
    }
}

fun handleAction(vm: AppDepotViewModel, context: Context, slug: String, action: CardAction) {
    when (action) {
        CardAction.Install, CardAction.Update -> vm.install(slug)
        CardAction.Open -> vm.open(slug) { context.startSafely(it) }
    }
}

fun handleMenu(vm: AppDepotViewModel, context: Context, item: ProductRow, action: MenuAction, uninstall: (String) -> Unit) {
    val app = item.app
    when (action) {
        MenuAction.Open -> vm.open(app.slug) { context.startSafely(it) }
        MenuAction.Uninstall -> uninstall(app.slug)
        MenuAction.Share -> shareApp(context, app)
        MenuAction.ReleasePage -> context.openUrl(releasePageUrl(app))
        MenuAction.OpenOnWeb -> context.openUrl(productWebUrl(item.product))
        MenuAction.ShareWebLink -> shareWebLink(context, item.product)
    }
}

/** Share the product's vuhpquang.com page: for apps that have no build for this phone. */
fun shareWebLink(context: Context, product: Product) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject, product.display.name))
        putExtra(Intent.EXTRA_TEXT, webShareText(product))
    }
    context.startSafely(Intent.createChooser(send, null))
}

fun shareApp(context: Context, app: CatalogApp) {
    val text = shareText(app) ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject, app.name))
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startSafely(Intent.createChooser(send, null))
}
