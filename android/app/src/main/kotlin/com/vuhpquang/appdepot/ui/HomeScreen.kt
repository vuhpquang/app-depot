package com.vuhpquang.appdepot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.ui.components.AppCard
import com.vuhpquang.appdepot.ui.components.UpdateAllBar
import com.vuhpquang.appdepot.ui.theme.DepotTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppDepotViewModel, onOpenApp: (String) -> Unit, onSettings: () -> Unit, onHistory: () -> Unit) {
    val ui by vm.home.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onHistory) { Icon(Icons.Outlined.History, stringResource(R.string.action_history)) }
                    IconButton(onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.action_settings)) }
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
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(DepotTokens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
            ) {
                if (ui.refreshFailed && ui.rows.isNotEmpty()) {
                    item(key = "refresh-failed") {
                        Text(
                            stringResource(R.string.refresh_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                val updatable = ui.updatable.size
                if (updatable > 0) {
                    item(key = "update-all") { UpdateAllBar(updatable, ui.busy, vm::updateAll) }
                }
                items(ui.rows, key = { it.app.slug }) { row ->
                    AppCard(
                        row = row,
                        onClick = { onOpenApp(row.app.slug) },
                        onAction = { action -> handleAction(vm, context, row.app.slug, action) },
                        onAllowInstalls = { context.startSafely(vm.unknownSourcesIntent()) },
                    )
                }
                if (ui.loaded && ui.rows.isEmpty() && !ui.refreshing) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(top = DepotTokens.SpaceXl)) {
                            Text(
                                stringResource(if (ui.refreshFailed) R.string.empty_load_failed else R.string.empty_no_apps),
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

fun handleAction(vm: AppDepotViewModel, context: android.content.Context, slug: String, action: CardAction) {
    when (action) {
        CardAction.Install, CardAction.Update -> vm.install(slug)
        CardAction.Open -> vm.open(slug) { context.startSafely(it) }
    }
}
