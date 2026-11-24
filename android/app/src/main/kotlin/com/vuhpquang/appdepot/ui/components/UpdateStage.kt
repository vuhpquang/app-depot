package com.vuhpquang.appdepot.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.ui.ProductRow
import com.vuhpquang.appdepot.ui.theme.DepotTokens

/**
 * Home's hero: every app with an update on one large primary-container card, each with its own
 * progress, and a big shape-morphing Update all. [size] is the total download ("57 MB").
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateStage(
    rows: List<ProductRow>,
    running: Boolean,
    size: String?,
    sizeOf: (ProductRow) -> String?,
    onOpen: (String) -> Unit,
    onUpdateAll: () -> Unit,
    onAllowInstalls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DepotTokens.RadiusStage),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            Modifier.padding(DepotTokens.SpaceLg - DepotTokens.SpaceXs).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm),
        ) {
            rows.forEach { StageRow(it, sizeOf(it), { onOpen(it.app.slug) }, onAllowInstalls) }
            val height = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = onUpdateAll,
                enabled = !running,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
                modifier = Modifier.fillMaxWidth().padding(top = DepotTokens.SpaceSm).heightIn(min = height),
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
                Text(
                    when {
                        running -> stringResource(R.string.update_all_running)
                        // One app on the stage: "Update · 57 MB", not "Update all (1)".
                        rows.size == 1 && size != null -> stringResource(R.string.action_with_size, stringResource(R.string.action_update), size)
                        rows.size == 1 -> stringResource(R.string.action_update)
                        size != null -> stringResource(R.string.update_all_size, rows.size, size)
                        else -> stringResource(R.string.update_all, rows.size)
                    },
                    style = ButtonDefaults.textStyleFor(height),
                    modifier = Modifier.padding(start = ButtonDefaults.iconSpacingFor(height)),
                )
            }
        }
    }
}

/** Icon, name, "0.9.2 → 0.9.3 · 12 MB", and below it what the install is doing. Tapping opens the detail. */
@Composable
private fun StageRow(item: ProductRow, size: String?, onOpen: () -> Unit, onAllowInstalls: () -> Unit) {
    val m = item.model
    val from = m.installed
    val to = (item.row?.state as? AppState.UpdateAvailable)?.latest?.version
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = DepotTokens.SpaceXs), verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd)) {
                AppIcon(item.app.name, item.app.iconUrl)
                Column(Modifier.weight(1f)) {
                    Text(item.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val versions = when {
                        from != null && to != null && size != null -> stringResource(R.string.stage_version_size, from, to, size)
                        from != null && to != null -> stringResource(R.string.stage_version, from, to)
                        else -> to
                    }
                    if (versions != null) Text(versions, style = MaterialTheme.typography.bodyMedium)
                }
            }
            CardFooter(m, onAllowInstalls, Modifier.fillMaxWidth())
        }
    }
}
