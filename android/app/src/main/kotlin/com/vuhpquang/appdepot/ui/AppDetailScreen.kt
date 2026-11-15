package com.vuhpquang.appdepot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.ui.components.ActionButton
import com.vuhpquang.appdepot.ui.components.AppIcon
import com.vuhpquang.appdepot.ui.components.CardFooter
import com.vuhpquang.appdepot.ui.components.StatusLine
import com.vuhpquang.appdepot.ui.theme.DepotTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AppDetailScreen(vm: AppDepotViewModel, slug: String, onBack: () -> Unit) {
    val ui by vm.home.collectAsStateWithLifecycle()
    val allHistory by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadHistory() }
    val row = ui.rows.firstOrNull { it.app.slug == slug }
    Scaffold(topBar = { BackTopBar(row?.app?.name ?: "", onBack) }) { padding ->
        if (row == null) {
            Text(
                stringResource(R.string.app_not_found),
                modifier = Modifier.padding(padding).padding(DepotTokens.SpaceMd),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(DepotTokens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd)) {
                AppIcon(row.app.name, row.app.iconUrl, size = DepotTokens.IconHeader)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceXs)) {
                    Text(row.app.name, style = MaterialTheme.typography.headlineSmall)
                    Text(row.app.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusLine(row.model)
            Row(horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
                ActionButton(row.model.action, { handleAction(vm, context, slug, it) })
                // Updating or installing: also offer Open when the app is already there.
                if (row.model.action != CardAction.Open && row.model.action != null && row.launchable) {
                    ActionButton(CardAction.Open, { handleAction(vm, context, slug, it) })
                }
            }
            CardFooter(row.model, onAllowInstalls = { context.startSafely(vm.unknownSourcesIntent()) })

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.whats_new), style = MaterialTheme.typography.titleMedium)
            val latest = row.app.latest
            if (latest != null) {
                Text(
                    stringResource(R.string.whats_new_version, latest.version, formatDate(latest.publishedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (latest.notes.isNotBlank()) ReleaseNotes(latest.notes) else NoNotes()
            } else {
                NoNotes()
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium)
            val rows = allHistory.filter { it.appSlug == slug && it.type != "check" }.take(20)
            if (rows.isEmpty()) {
                Text(stringResource(R.string.history_empty_app), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column { rows.forEach { HistoryItem(it, vm) } }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(
                onClick = { context.openUrl("https://github.com/${row.app.repo}") },
                modifier = Modifier.heightIn(min = DepotTokens.TouchMin),
            ) {
                Text(stringResource(R.string.source_code))
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null,
                    modifier = Modifier.padding(start = DepotTokens.SpaceXs),
                )
            }
        }
    }
}

@Composable
private fun NoNotes() {
    Text(stringResource(R.string.no_notes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatDate(iso: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
}.getOrDefault(iso.take(10))
