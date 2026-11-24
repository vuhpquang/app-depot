package com.vuhpquang.appdepot.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.core.ANDROID
import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogRelease
import com.vuhpquang.appdepot.ui.components.AppIcon
import com.vuhpquang.appdepot.ui.components.CardFooter
import com.vuhpquang.appdepot.ui.components.ConnectedSurface
import com.vuhpquang.appdepot.ui.components.HeroShape
import com.vuhpquang.appdepot.ui.components.PlatformBadges
import com.vuhpquang.appdepot.ui.components.ShapeBackdrop
import com.vuhpquang.appdepot.ui.components.StatusChip
import com.vuhpquang.appdepot.ui.components.platformName
import com.vuhpquang.appdepot.ui.components.rowGroupShape
import com.vuhpquang.appdepot.ui.theme.DepotTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * App detail (Material 3 Expressive): a hero with the icon on a soft-burst backdrop, the main action
 * as a split button (its menu holds Skip / Uninstall / Share / Release page), version facts as a
 * connected row of tiles, What's New on a large-shape card, and the app's history as a connected list.
 */
@Composable
fun AppDetailScreen(vm: AppDepotViewModel, slug: String, onBack: () -> Unit) {
    val ui by vm.home.collectAsStateWithLifecycle()
    val allHistory by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadHistory() }
    val item = ui.product(slug)
    val uninstall = rememberUninstaller(vm)
    val scroll = rememberScrollState()
    // The bar shows the name only once the hero (which already says it) has scrolled away.
    val heroPx = with(LocalDensity.current) { DepotTokens.HeroBackdrop.roundToPx() }
    val showTitle by remember { derivedStateOf { scroll.value > heroPx } }
    Scaffold(topBar = { BackTopBar(item?.app?.name ?: "", onBack, showTitle = showTitle) }) { padding ->
        if (item == null) {
            Text(
                stringResource(R.string.app_not_found),
                modifier = Modifier.padding(padding).padding(DepotTokens.SpaceMd),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }
        val app = item.app
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(scroll)
                .padding(start = DepotTokens.SpaceMd, end = DepotTokens.SpaceMd, bottom = DepotTokens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
        ) {
            Hero(item)
            val row = item.row
            if (row != null) {
                LocalDetail(vm, row, uninstall)
                val rows = allHistory.filter { it.appSlug == row.app.slug && it.type != "check" }.take(20)
                SectionTitle(stringResource(R.string.history_title))
                if (rows.isEmpty()) {
                    Text(stringResource(R.string.history_empty_app), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(DepotTokens.GroupGap)) {
                        rows.forEachIndexed { i, h ->
                            ConnectedSurface(groupPosition(i, rows.size)) {
                                HistoryItem(h, vm, Modifier.padding(horizontal = DepotTokens.SpaceMd))
                            }
                        }
                    }
                }
            } else {
                RemoteDetail(item)
            }

            TextButton(
                onClick = { context.openUrl("https://github.com/${app.repo}") },
                modifier = Modifier.heightIn(min = DepotTokens.TouchMin),
            ) {
                Text(stringResource(R.string.source_code))
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null,
                    modifier = Modifier.padding(start = DepotTokens.SpaceXs).size(DepotTokens.IconSmall),
                )
            }
        }
    }
}

/** Icon on the soft burst, name, tagline, platform badges and "Also on macOS · 1.2". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Hero(item: ProductRow) {
    val app = item.app
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
        ShapeBackdrop(HeroShape, DepotTokens.HeroBackdrop, MaterialTheme.colorScheme.primaryContainer) {
            AppIcon(app.name, app.iconUrl, size = DepotTokens.IconHero)
        }
        Text(app.name, style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
        Text(
            app.tagline, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        PlatformBadges(item.product.platforms(ANDROID), ANDROID, Modifier.padding(top = DepotTokens.SpaceXs))
        if (item.row != null) {
            item.product.others(ANDROID).forEach { other ->
                val name = platformName(other.platform)
                Text(
                    other.latest?.let { stringResource(R.string.also_on, name, it.version) } ?: stringResource(R.string.also_on_no_release, name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** This phone's variant: state chip, split button, facts, progress, What's New. */
@Composable
private fun LocalDetail(vm: AppDepotViewModel, row: AppRow, uninstall: (String) -> Unit) {
    val context = LocalContext.current
    val slug = row.app.slug
    Column(Modifier.fillMaxWidth().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd)) {
        row.model.chip?.let { StatusChip(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm), verticalAlignment = Alignment.CenterVertically) {
            PrimaryAction(vm, row, uninstall)
            // Updating or installing: also offer Open when the app is already there.
            if (row.model.action != CardAction.Open && row.model.action != null && row.launchable) {
                OpenButton { handleAction(vm, context, slug, CardAction.Open) }
            }
        }
        Facts(row)
        CardFooter(row.model, onAllowInstalls = { context.startSafely(vm.unknownSourcesIntent()) }, modifier = Modifier.fillMaxWidth())
        (row.state as? AppState.Skipped)?.let {
            Text(
                stringResource(R.string.skipped_note, it.latest.version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    ReleaseCard(stringResource(R.string.whats_new), row.app.latest)
}

private data class DetailMenuItem(val label: Int, val icon: ImageVector, val run: () -> Unit)

/** Skip / Undo skip, Uninstall, Share, Release page: as many as apply. */
@Composable
private fun detailMenu(vm: AppDepotViewModel, row: AppRow, onUninstall: (String) -> Unit): List<DetailMenuItem> {
    val context = LocalContext.current
    val slug = row.app.slug
    return buildList {
        when (val s = row.state) {
            is AppState.UpdateAvailable -> add(DetailMenuItem(R.string.action_skip_version, Icons.Outlined.DoNotDisturbOn) { vm.skipVersion(slug, s.latest.version) })
            is AppState.Skipped -> add(DetailMenuItem(R.string.action_undo_skip, Icons.AutoMirrored.Outlined.Undo) { vm.undoSkip(slug) })
            else -> Unit
        }
        if (canUninstall(row)) add(DetailMenuItem(R.string.action_uninstall, Icons.Outlined.Delete) { onUninstall(slug) })
        if (row.app.latest != null) add(DetailMenuItem(R.string.action_share, Icons.Outlined.Share) { shareApp(context, row.app) })
        add(DetailMenuItem(R.string.action_release_page, Icons.AutoMirrored.Outlined.OpenInNew) { context.openUrl(releasePageUrl(row.app)) })
    }
}

/**
 * The split button: Update (filled) / Install (tonal) / Open (outlined) on the leading side, the
 * menu on the trailing side (its chevron turns while open). No primary action: just "More actions".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrimaryAction(vm: AppDepotViewModel, row: AppRow, uninstall: (String) -> Unit) {
    val context = LocalContext.current
    val slug = row.app.slug
    val menu = detailMenu(vm, row, uninstall)
    var open by remember { mutableStateOf(false) }
    val action = row.model.action
    // While an install runs, the progress below says what is happening; no stray "More actions".
    if (action == null && row.model.activity != null) return
    val size = formatSize(context, downloadBytes(row))
    val height = ButtonDefaults.MediumContainerHeight
    Box {
        if (action == null) {
            OutlinedButton({ open = true }, modifier = Modifier.heightIn(min = DepotTokens.TouchMin), shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = null, modifier = Modifier.size(DepotTokens.IconSmall))
                Text(stringResource(R.string.action_more_actions), modifier = Modifier.padding(start = DepotTokens.SpaceSm))
            }
        } else {
            val (labelRes, icon) = when (action) {
                CardAction.Update -> R.string.action_update to Icons.Outlined.SystemUpdate
                CardAction.Install -> R.string.action_install to Icons.Outlined.Download
                CardAction.Open -> R.string.action_open to Icons.Outlined.PlayCircle
            }
            val base = stringResource(labelRes)
            val label = if (size != null && action != CardAction.Open) stringResource(R.string.action_with_size, base, size) else base
            val onLead = { handleAction(vm, context, slug, action) }
            val rotation by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chevron")
            val more = stringResource(R.string.action_more)
            val leadContent: @Composable () -> Unit = {
                Icon(icon, contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize))
                Text(label, style = ButtonDefaults.textStyleFor(height), maxLines = 1, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
            val trailContent: @Composable () -> Unit = {
                Icon(
                    Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).rotate(rotation),
                )
            }
            val lead = Modifier.heightIn(min = height)
            val trail = Modifier.heightIn(min = height).semantics { contentDescription = more }
            SplitButtonLayout(
                leadingButton = {
                    when (action) {
                        CardAction.Update -> SplitButtonDefaults.LeadingButton(onLead, lead) { leadContent() }
                        CardAction.Install -> SplitButtonDefaults.TonalLeadingButton(onLead, lead) { leadContent() }
                        CardAction.Open -> SplitButtonDefaults.OutlinedLeadingButton(onLead, lead) { leadContent() }
                    }
                },
                trailingButton = {
                    when (action) {
                        CardAction.Update -> SplitButtonDefaults.TrailingButton(open, { open = it }, trail) { trailContent() }
                        CardAction.Install -> SplitButtonDefaults.TonalTrailingButton(open, { open = it }, trail) { trailContent() }
                        CardAction.Open -> SplitButtonDefaults.OutlinedTrailingButton(open, { open = it }, trail) { trailContent() }
                    }
                },
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            menu.forEach { m ->
                DropdownMenuItem(
                    text = { Text(stringResource(m.label)) },
                    leadingIcon = { Icon(m.icon, contentDescription = null) },
                    onClick = { open = false; m.run() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OpenButton(onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
        Text(stringResource(R.string.action_open), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
    }
}

/** Installed · Latest · Download as a connected row of tiles (only the ones that apply). */
@Composable
private fun Facts(row: AppRow) {
    val context = LocalContext.current
    val facts = buildList {
        row.model.installed?.let { add(R.string.fact_installed to it) }
        row.model.latest?.let { add(R.string.fact_latest to it) }
        formatSize(context, downloadBytes(row))?.let { add(R.string.fact_size to it) }
    }
    if (facts.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DepotTokens.GroupGap)) {
        facts.forEachIndexed { i, (label, value) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = rowGroupShape(groupPosition(i, facts.size)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    Modifier.padding(vertical = DepotTokens.SpaceMd, horizontal = DepotTokens.SpaceSm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** No build for this phone: say so, offer the web page and a link to share, then each platform's latest release. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteDetail(item: ProductRow) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd)) {
        StatusChip(CardChip.NotOnThisDevice)
        Text(
            stringResource(R.string.not_on_device_note, item.app.name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
            Button({ context.openUrl(productWebUrl(item.product)) }, modifier = Modifier.heightIn(min = DepotTokens.TouchMin), shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(DepotTokens.IconSmall))
                Text(stringResource(R.string.action_open_web), modifier = Modifier.padding(start = DepotTokens.SpaceSm))
            }
            OutlinedButton({ shareWebLink(context, item.product) }, modifier = Modifier.heightIn(min = DepotTokens.TouchMin), shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(DepotTokens.IconSmall))
                Text(stringResource(R.string.action_share_web), modifier = Modifier.padding(start = DepotTokens.SpaceSm))
            }
        }
    }
    item.product.others(ANDROID).forEach { other ->
        ReleaseCard(stringResource(R.string.available_for, platformName(other.platform)), other.latest)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = DepotTokens.SpaceSm))
}

/** "What's new" (or "Available for macOS"), "Version X · date" and the notes, on a large-shape card. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReleaseCard(title: String, latest: CatalogRelease?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(DepotTokens.SpaceLg - DepotTokens.SpaceXs), verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
            ReleaseSection(latest)
        }
    }
}

@Composable
private fun ColumnScope.ReleaseSection(latest: CatalogRelease?) {
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
