package com.vuhpquang.appdepot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.graphics.vector.ImageVector
import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.ANDROID
import com.vuhpquang.appdepot.core.CatalogRelease
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import com.vuhpquang.appdepot.ui.components.PlatformBadges
import com.vuhpquang.appdepot.ui.components.StatusChip
import com.vuhpquang.appdepot.ui.components.platformName
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
    val item = ui.product(slug)
    val uninstall = rememberUninstaller(vm)
    Scaffold(topBar = { BackTopBar(item?.app?.name ?: "", onBack) }) { padding ->
        if (item == null) {
            Text(
                stringResource(R.string.app_not_found),
                modifier = Modifier.padding(padding).padding(DepotTokens.SpaceMd),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }
        val app = item.app
        val others = item.product.others(ANDROID)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(DepotTokens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd)) {
                AppIcon(app.name, app.iconUrl, size = DepotTokens.IconHeader)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceXs)) {
                    Text(app.name, style = MaterialTheme.typography.headlineSmall)
                    Text(app.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PlatformBadges(item.product.platforms(ANDROID), ANDROID, Modifier.padding(top = DepotTokens.SpaceXs))
                }
            }
            val row = item.row
            if (row != null) {
                others.forEach { other ->
                    val name = platformName(other.platform)
                    Text(
                        other.latest?.let { stringResource(R.string.also_on, name, it.version) }
                            ?: stringResource(R.string.also_on_no_release, name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LocalDetail(vm, row, uninstall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium)
                val rows = allHistory.filter { it.appSlug == row.app.slug && it.type != "check" }.take(20)
                if (rows.isEmpty()) {
                    Text(stringResource(R.string.history_empty_app), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column { rows.forEach { HistoryItem(it, vm) } }
                }
            } else {
                RemoteDetail(item)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(
                onClick = { context.openUrl("https://github.com/${app.repo}") },
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

/** This phone's variant: state, install/update/open, skip/uninstall/share, its release notes. */
@Composable
private fun LocalDetail(vm: AppDepotViewModel, row: AppRow, uninstall: (String) -> Unit) {
    val context = LocalContext.current
    val slug = row.app.slug
    StatusLine(row.model)
    Row(horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
        ActionButton(row.model.action, { handleAction(vm, context, slug, it) }, size = formatSize(context, downloadBytes(row)))
        // Updating or installing: also offer Open when the app is already there.
        if (row.model.action != CardAction.Open && row.model.action != null && row.launchable) {
            ActionButton(CardAction.Open, { handleAction(vm, context, slug, it) })
        }
    }
    CardFooter(row.model, onAllowInstalls = { context.startSafely(vm.unknownSourcesIntent()) })
    (row.state as? AppState.Skipped)?.let {
        Text(
            stringResource(R.string.skipped_note, it.latest.version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    DetailActions(vm, row, onUninstall = uninstall)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(stringResource(R.string.whats_new), style = MaterialTheme.typography.titleMedium)
    ReleaseSection(row.app.latest)
}

/** No build for this phone: say so, offer the web page and a link to share, then each platform's latest release. */
@Composable
private fun RemoteDetail(item: ProductRow) {
    val context = LocalContext.current
    StatusChip(CardChip.NotOnThisDevice)
    Text(
        stringResource(R.string.not_on_device_note, item.app.name),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
        FilledTonalButton({ context.openUrl(productWebUrl(item.product)) }, Modifier.heightIn(min = DepotTokens.TouchMin)) {
            Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(DepotTokens.IconSmall))
            Text(stringResource(R.string.action_open_web), modifier = Modifier.padding(start = DepotTokens.SpaceSm))
        }
        OutlinedButton({ shareWebLink(context, item.product) }, Modifier.heightIn(min = DepotTokens.TouchMin)) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(DepotTokens.IconSmall))
            Text(stringResource(R.string.action_share_web), modifier = Modifier.padding(start = DepotTokens.SpaceSm))
        }
    }
    item.product.others(ANDROID).forEach { other ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.available_for, platformName(other.platform)), style = MaterialTheme.typography.titleMedium)
        ReleaseSection(other.latest)
    }
}

/** "Version X · date" and the notes, or "No notes". */
@Composable
private fun ReleaseSection(latest: CatalogRelease?) {
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
}

@Composable
private fun NoNotes() {
    Text(stringResource(R.string.no_notes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatDate(iso: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
}.getOrDefault(iso.take(10))

/** Skip / Undo skip, Uninstall, Share link, Release page: as many as apply, wrapping on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActions(vm: AppDepotViewModel, row: AppRow, onUninstall: (String) -> Unit) {
    val context = LocalContext.current
    val slug = row.app.slug
    FlowRow(horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceXs)) {
        when (val s = row.state) {
            is AppState.UpdateAvailable -> DetailAction(R.string.action_skip_version, Icons.Outlined.DoNotDisturbOn) {
                vm.skipVersion(slug, s.latest.version)
            }
            is AppState.Skipped -> DetailAction(R.string.action_undo_skip, Icons.AutoMirrored.Outlined.Undo) { vm.undoSkip(slug) }
            else -> Unit
        }
        if (canUninstall(row)) DetailAction(R.string.action_uninstall, Icons.Outlined.Delete) { onUninstall(slug) }
        if (row.app.latest != null) DetailAction(R.string.action_share, Icons.Outlined.Share) { shareApp(context, row.app) }
        DetailAction(R.string.action_release_page, Icons.AutoMirrored.Outlined.OpenInNew) { context.openUrl(releasePageUrl(row.app)) }
    }
}

@Composable
private fun DetailAction(label: Int, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick, Modifier.heightIn(min = DepotTokens.TouchMin)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(DepotTokens.IconSmall))
        Text(stringResource(label), modifier = Modifier.padding(start = DepotTokens.SpaceSm))
    }
}
