package com.vuhpquang.appdepot.ui

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.vuhpquang.appdepot.ui.components.ConnectedSurface
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
import androidx.compose.material.icons.outlined.Delete
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

/** History (Material 3 Expressive): a collapsing medium bar, one connected group per day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppDepotViewModel, onBack: () -> Unit) {
    val rows by vm.history.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadHistory() }
    // "started" is telemetry for the dashboard, not something to read here.
    val visible = rows.filter { it.type != "depot_started" }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { BackTopBar(stringResource(R.string.history_title), onBack, scroll) },
    ) { padding ->
        if (visible.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                modifier = Modifier.padding(padding).padding(DepotTokens.SpaceMd),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }
        val zone = ZoneId.systemDefault()
        val days = remember(visible) { historyDays(visible, zone) }
        val today = LocalDate.now(zone)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = DepotTokens.SpaceMd, end = DepotTokens.SpaceMd, bottom = DepotTokens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(DepotTokens.GroupGap),
        ) {
            days.forEach { d ->
                item(key = "day-${d.day}") {
                    Text(
                        when (dayLabel(d.day, today)) {
                            DayLabel.Today -> stringResource(R.string.history_today)
                            DayLabel.Yesterday -> stringResource(R.string.history_yesterday)
                            DayLabel.Date -> d.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = DepotTokens.SpaceMd, top = DepotTokens.SpaceMd, bottom = DepotTokens.SpaceSm),
                    )
                }
                itemsIndexed(d.rows, key = { _, r -> r.id }) { i, r ->
                    ConnectedSurface(groupPosition(i, d.rows.size), Modifier.animateItem()) {
                        HistoryItem(r, vm, Modifier.padding(horizontal = DepotTokens.SpaceMd))
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(row: HistoryRow, vm: AppDepotViewModel, modifier: Modifier = Modifier) {
    val failed = row.type == "install_failed" || row.type == "crash"
    val icon = when (row.type) {
        "install" -> Icons.Outlined.Download
        "update", "self_update" -> Icons.Outlined.SystemUpdate
        "install_failed" -> Icons.Outlined.ErrorOutline
        "crash" -> Icons.Outlined.ReportProblem
        "check" -> Icons.Outlined.Refresh
        "open" -> Icons.Outlined.Launch
        "uninstall" -> Icons.Outlined.Delete
        else -> Icons.Outlined.PlayCircle
    }
    val label = stringResource(
        when (row.type) {
            "install" -> R.string.history_install
            "update" -> R.string.history_update
            "install_failed" -> R.string.history_failed
            "check" -> R.string.history_check
            "open" -> R.string.history_open
            "uninstall" -> R.string.history_uninstall
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
        modifier.fillMaxWidth().heightIn(min = DepotTokens.TouchMin).padding(vertical = DepotTokens.SpaceMd - DepotTokens.SpaceXs),
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
