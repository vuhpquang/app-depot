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
import androidx.compose.material3.Button
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
import com.vuhpquang.appdepot.ui.theme.DepotTokens

@Composable
fun AppCard(
    row: AppRow,
    onClick: () -> Unit,
    onAction: (CardAction) -> Unit,
    onAllowInstalls: () -> Unit,
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
            }
            StatusLine(row.model)
            CardFooter(row.model, onAllowInstalls)
        }
    }
}

@Composable
fun ActionButton(action: CardAction?, onAction: (CardAction) -> Unit, modifier: Modifier = Modifier) {
    val m = modifier.heightIn(min = DepotTokens.TouchMin)
    when (action) {
        CardAction.Update -> Button({ onAction(action) }, m) { Text(stringResource(R.string.action_update)) }
        CardAction.Install -> FilledTonalButton({ onAction(action) }, m) { Text(stringResource(R.string.action_install)) }
        CardAction.Open -> OutlinedButton({ onAction(action) }, m) { Text(stringResource(R.string.action_open)) }
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
