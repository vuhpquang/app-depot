package com.vuhpquang.appdepot.ui

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.ui.components.AppCard
import com.vuhpquang.appdepot.ui.components.AppGridCard
import com.vuhpquang.appdepot.ui.components.UpdateAllBar
import com.vuhpquang.appdepot.ui.theme.DepotTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppDepotViewModel, onOpenApp: (String) -> Unit, onSettings: () -> Unit, onHistory: () -> Unit) {
    val ui by vm.home.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(ui.query.isNotEmpty()) }
    val uninstall = rememberUninstaller(vm)
    val closeSearch = { searching = false; vm.setQuery("") }
    BackHandler(enabled = searching) { closeSearch() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (searching) IconButton(closeSearch) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_close_search)) }
                },
                title = {
                    if (searching) SearchField(ui.query, vm::setQuery) else Text(stringResource(R.string.app_name))
                },
                actions = {
                    if (!searching) {
                        IconButton({ searching = true }) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search)) }
                        if (ui.layout == HomeLayout.List) {
                            IconButton({ vm.setLayout(HomeLayout.Grid) }) { Icon(Icons.Outlined.GridView, stringResource(R.string.action_grid_view)) }
                        } else {
                            IconButton({ vm.setLayout(HomeLayout.List) }) { Icon(Icons.AutoMirrored.Outlined.ViewList, stringResource(R.string.action_list_view)) }
                        }
                        SortMenu(ui.sort, vm::setSort)
                        IconButton(onHistory) { Icon(Icons.Outlined.History, stringResource(R.string.action_history)) }
                        IconButton(onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.action_settings)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val grid = ui.layout == HomeLayout.Grid
            val message = when {
                !ui.loaded || ui.refreshing -> null
                ui.rows.isEmpty() -> stringResource(if (ui.refreshFailed) R.string.empty_load_failed else R.string.empty_no_apps)
                ui.visible.isEmpty() -> stringResource(R.string.search_no_results, ui.query.trim())
                else -> null
            }
            LazyVerticalGrid(
                columns = if (grid) GridCells.Adaptive(DepotTokens.GridCellMin) else GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(DepotTokens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
                horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
            ) {
                val full: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
                if (ui.refreshFailed && ui.rows.isNotEmpty()) {
                    item(key = "refresh-failed", span = full) {
                        Text(
                            stringResource(R.string.refresh_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                val updatable = ui.updatable.size
                if (updatable > 0 && !searching) {
                    item(key = "update-all", span = full) {
                        UpdateAllBar(updatable, ui.busy, vm::updateAll, size = formatSize(context, updateAllBytes(ui.rows)))
                    }
                }
                items(ui.visible, key = { it.app.slug }) { row ->
                    val onAction = { action: CardAction -> handleAction(vm, context, row.app.slug, action) }
                    val onMenu = { a: MenuAction -> handleMenu(vm, context, row.app, a, uninstall) }
                    val onAllow = { context.startSafely(vm.unknownSourcesIntent()) }
                    if (grid) {
                        AppGridCard(row, { onOpenApp(row.app.slug) }, onAction, onAllow, onMenu)
                    } else {
                        AppCard(row, { onOpenApp(row.app.slug) }, onAction, onAllow, onMenu)
                    }
                }
                if (message != null) {
                    item(key = "empty", span = full) {
                        Box(Modifier.fillMaxWidth().padding(top = DepotTokens.SpaceXl)) {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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

fun handleMenu(vm: AppDepotViewModel, context: Context, app: CatalogApp, action: MenuAction, uninstall: (String) -> Unit) {
    when (action) {
        MenuAction.Open -> vm.open(app.slug) { context.startSafely(it) }
        MenuAction.Uninstall -> uninstall(app.slug)
        MenuAction.Share -> shareApp(context, app)
        MenuAction.ReleasePage -> context.openUrl(releasePageUrl(app))
    }
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
