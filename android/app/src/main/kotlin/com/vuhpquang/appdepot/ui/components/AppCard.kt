package com.vuhpquang.appdepot.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.ui.AppRow
import com.vuhpquang.appdepot.ui.CardAction
import com.vuhpquang.appdepot.ui.CardActivity
import com.vuhpquang.appdepot.ui.CardModel
import com.vuhpquang.appdepot.ui.MenuAction
import com.vuhpquang.appdepot.ui.menuActions
import com.vuhpquang.appdepot.ui.theme.DepotTokens

@Composable
fun AppCard(
    row: AppRow,
    onClick: () -> Unit,
    onAction: (CardAction) -> Unit,
    onAllowInstalls: () -> Unit,
    onMenu: (MenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(DepotTokens.RadiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(DepotTokens.SpaceMd), verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd)) {
                AppIcon(row.app.name, row.app.iconUrl)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceXs / 2)) {
                    Text(row.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        row.app.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ActionButton(row.model.action, onAction)
                AppMenu(row, onMenu)
            }
            StatusLine(row.model)
            CardFooter(row.model, onAllowInstalls)
        }
    }
}

/** Gallery tile: big icon, name, status chip and one action button. */
@Composable
fun AppGridCard(
    row: AppRow,
    onClick: () -> Unit,
    onAction: (CardAction) -> Unit,
    onAllowInstalls: () -> Unit,
    onMenu: (MenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(DepotTokens.RadiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(DepotTokens.SpaceMd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm),
            ) {
                AppIcon(row.app.name, row.app.iconUrl, size = DepotTokens.IconGrid)
                Text(
                    row.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                val m = row.model
                when {
                    m.chip != null -> StatusChip(m.chip)
                    m.latest != null -> Text(
                        stringResource(R.string.latest_only, m.latest),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Spacer(Modifier.height(DepotTokens.SpaceMd))
                }
                if (m.activity != null || m.needsPermission || m.error != null) {
                    CardFooter(m, onAllowInstalls, Modifier.fillMaxWidth())
                } else if (m.action != null) {
                    ActionButton(m.action, onAction, Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(DepotTokens.TouchMin))
                }
            }
            AppMenu(row, onMenu, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The card's "⋮" menu: Open / Uninstall / Share link / Release page, as they apply. */
@Composable
fun AppMenu(row: AppRow, onMenu: (MenuAction) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton({ open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menuActions(row).forEach { a ->
                val (label, icon) = when (a) {
                    MenuAction.Open -> R.string.action_open to Icons.Outlined.PlayCircle
                    MenuAction.Uninstall -> R.string.action_uninstall to Icons.Outlined.Delete
                    MenuAction.Share -> R.string.action_share to Icons.Outlined.Share
                    MenuAction.ReleasePage -> R.string.action_release_page to Icons.AutoMirrored.Outlined.OpenInNew
                }
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = { open = false; onMenu(a) },
                )
            }
        }
    }
}

/** [size] (e.g. "57 MB") is appended to Install / Update: "Update · 57 MB". */
@Composable
fun ActionButton(action: CardAction?, onAction: (CardAction) -> Unit, modifier: Modifier = Modifier, size: String? = null) {
    val m = modifier.heightIn(min = DepotTokens.TouchMin)
    @Composable fun label(res: Int) = stringResource(res).let { if (size != null) stringResource(R.string.action_with_size, it, size) else it }
    when (action) {
        CardAction.Update -> Button({ onAction(action) }, m) { Text(label(R.string.action_update), maxLines = 1) }
        CardAction.Install -> FilledTonalButton({ onAction(action) }, m) { Text(label(R.string.action_install), maxLines = 1) }
        CardAction.Open -> OutlinedButton({ onAction(action) }, m) { Text(stringResource(R.string.action_open), maxLines = 1) }
        null -> Unit
    }
}

/** "Installed X · Latest Y" plus the state chip. */
@Composable
fun StatusLine(model: CardModel, modifier: Modifier = Modifier) {
    val versions = when {
        model.installed != null && model.latest != null && model.installed != model.latest ->
            stringResource(R.string.installed_latest, model.installed, model.latest)
        model.installed != null -> stringResource(R.string.installed_only, model.installed)
        model.latest != null -> stringResource(R.string.latest_only, model.latest)
        else -> null
    }
    if (versions == null && model.chip == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
        model.chip?.let { StatusChip(it) }
        if (versions != null) {
            Text(versions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Progress, the permission prompt or the last error. */
@Composable
fun CardFooter(model: CardModel, onAllowInstalls: () -> Unit, modifier: Modifier = Modifier) {
    if (model.activity == null && !model.needsPermission && model.error == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
        model.activity?.let { a ->
            val label = when (a) {
                CardActivity.Queued -> stringResource(R.string.progress_queued)
                is CardActivity.Downloading -> stringResource(R.string.progress_downloading, a.percent)
                CardActivity.Verifying -> stringResource(R.string.progress_verifying)
                CardActivity.Installing -> stringResource(R.string.progress_installing)
                CardActivity.WaitingForConfirm -> stringResource(R.string.progress_confirm)
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val bar = Modifier.fillMaxWidth().height(DepotTokens.ProgressHeight)
            if (a is CardActivity.Downloading && a.percent > 0) {
                LinearProgressIndicator(progress = { a.percent / 100f }, modifier = bar)
            } else if (a != CardActivity.WaitingForConfirm) {
                LinearProgressIndicator(modifier = bar)
            }
        }
        if (model.needsPermission) {
            Text(stringResource(R.string.needs_permission), style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onAllowInstalls, Modifier.heightIn(min = DepotTokens.TouchMin)) {
                Text(stringResource(R.string.action_allow_installs))
            }
        }
        model.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
