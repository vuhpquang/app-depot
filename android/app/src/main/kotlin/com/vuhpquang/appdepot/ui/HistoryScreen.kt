package com.vuhpquang.appdepot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.android.HistoryRow
import com.vuhpquang.appdepot.ui.theme.DepotTokens

@Composable
fun HistoryScreen(vm: AppDepotViewModel, onBack: () -> Unit) {
    val rows by vm.history.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadHistory() }
    // "started" is telemetry for the dashboard, not something to read here.
    val visible = rows.filter { it.type != "depot_started" }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.history_title), onBack) }) { padding ->
        if (visible.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                modifier = Modifier.padding(padding).padding(DepotTokens.SpaceMd),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = DepotTokens.SpaceMd, vertical = DepotTokens.SpaceSm),
        ) {
            items(visible, key = { it.id }) { HistoryItem(it, vm) }
        }
    }
}

@Composable
fun HistoryItem(row: HistoryRow, vm: AppDepotViewModel) {
    val failed = row.type == "install_failed" || row.type == "crash"
    val icon = when (row.type) {
        "install" -> Icons.Outlined.Download
        "update", "self_update" -> Icons.Outlined.SystemUpdate
        "install_failed" -> Icons.Outlined.ErrorOutline
        "crash" -> Icons.Outlined.ReportProblem
        "check" -> Icons.Outlined.Refresh
        "open" -> Icons.Outlined.Launch
        else -> Icons.Outlined.PlayCircle
    }
    val label = stringResource(
        when (row.type) {
            "install" -> R.string.history_install
            "update" -> R.string.history_update
            "install_failed" -> R.string.history_failed
            "check" -> R.string.history_check
            "open" -> R.string.history_open
            "crash" -> R.string.history_crash
            "self_update" -> R.string.history_self_update
            else -> R.string.history_started
        },
    )
    val name = vm.appName(row.appSlug)
    val versions = when {
        row.fromVersion != null && row.toVersion != null -> "${row.fromVersion} → ${row.toVersion}"
        else -> row.toVersion ?: row.fromVersion
    }
    // Title: "ZenTask 0.15.1 → 0.15.2"; app-less events (check, started) use their label.
    val title = if (name != null) listOfNotNull(name, versions).joinToString(" ") else label
    val time = relativeTimeText(row.at.toEpochMilli())
    val meta = buildList {
        if (name != null) add(label)
        if (row.type == "check" && row.message != null) add(row.message)
        add(time)
    }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = DepotTokens.TouchMin).padding(vertical = DepotTokens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(DepotTokens.IconRow),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceXs / 2)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (failed && row.message != null) {
                Text(row.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
