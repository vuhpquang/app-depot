package com.vuhpquang.appdepot.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.core.ANDROID
import com.vuhpquang.appdepot.ui.CardAction
import com.vuhpquang.appdepot.ui.CardActivity
import com.vuhpquang.appdepot.ui.CardModel
import com.vuhpquang.appdepot.ui.GroupPosition
import com.vuhpquang.appdepot.ui.MenuAction
import com.vuhpquang.appdepot.ui.ProductRow
import com.vuhpquang.appdepot.ui.productMenuActions
import com.vuhpquang.appdepot.ui.theme.DepotTokens

/** List card, one link of a connected group ([position] picks its corners). */
@Composable
fun AppCard(
    item: ProductRow,
    showPlatforms: Boolean,
    position: GroupPosition,
    onClick: () -> Unit,
    onAction: (CardAction) -> Unit,
    onAllowInstalls: () -> Unit,
    onMenu: (MenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = item.app
    ConnectedSurface(position, modifier, onClick = onClick) {
        Column(
            Modifier.padding(DepotTokens.SpaceMd).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd)) {
                AppIcon(app.name, app.iconUrl)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceXs / 2)) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        app.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.isLocal) {
                    ActionButton(item.model.action, onAction)
                } else {
                    // An icon, not a labelled button: the row is narrow and the name must stay readable.
                    IconButton({ onMenu(MenuAction.OpenOnWeb) }) {
                        Icon(Icons.Outlined.Public, stringResource(R.string.action_open_web), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                AppMenu(productMenuActions(item), onMenu)
            }
            StatusLine(item.model)
            if (showPlatforms) PlatformBadges(item.product.platforms(ANDROID), ANDROID)
            CardFooter(item.model, onAllowInstalls)
        }
    }
}

/** Gallery tile: big icon, name, status chip and one action button. */
@Composable
fun AppGridCard(
    item: ProductRow,
    showPlatforms: Boolean,
    onClick: () -> Unit,
    onAction: (CardAction) -> Unit,
    onAllowInstalls: () -> Unit,
    onMenu: (MenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(DepotTokens.SpaceMd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm),
            ) {
                AppIcon(item.app.name, item.app.iconUrl, size = DepotTokens.IconGrid)
                Text(
                    item.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                val m = item.model
                if (showPlatforms) PlatformBadges(item.product.platforms(ANDROID), ANDROID)
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
                } else if (!item.isLocal) {
                    WebButton(Modifier.fillMaxWidth()) { onMenu(MenuAction.OpenOnWeb) }
                } else if (m.action != null) {
                    ActionButton(m.action, onAction, Modifier.fillMaxWidth(), fill = true)
                } else {
                    Spacer(Modifier.height(DepotTokens.TouchMin))
                }
            }
            AppMenu(productMenuActions(item), onMenu, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The card's "⋮" menu: Open / Uninstall / Share link / Release page, as they apply. */
@Composable
fun AppMenu(actions: List<MenuAction>, onMenu: (MenuAction) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton({ open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            actions.forEach { a ->
                val (label, icon) = when (a) {
                    MenuAction.Open -> R.string.action_open to Icons.Outlined.PlayCircle
                    MenuAction.Uninstall -> R.string.action_uninstall to Icons.Outlined.Delete
                    MenuAction.Share -> R.string.action_share to Icons.Outlined.Share
                    MenuAction.ReleasePage -> R.string.action_release_page to Icons.AutoMirrored.Outlined.OpenInNew
                    MenuAction.OpenOnWeb -> R.string.action_open_web to Icons.Outlined.Public
                    MenuAction.ShareWebLink -> R.string.action_share_web to Icons.Outlined.Share
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

/**
 * Install / Update / Open as an Expressive button: the corners morph while pressed, and a change of
 * action cross-fades. [size] (e.g. "57 MB") is appended to Install / Update: "Update · 57 MB".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionButton(
    action: CardAction?,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
    size: String? = null,
    fill: Boolean = false,
) {
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = action,
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        modifier = modifier,
        label = "action",
    ) { a ->
        val m = (if (fill) Modifier.fillMaxWidth() else Modifier).heightIn(min = DepotTokens.TouchMin)
        @Composable fun label(res: Int) = stringResource(res).let { if (size != null) stringResource(R.string.action_with_size, it, size) else it }
        when (a) {
            CardAction.Update -> Button({ onAction(a) }, modifier = m, shapes = ButtonDefaults.shapes()) { Text(label(R.string.action_update), maxLines = 1) }
            CardAction.Install -> FilledTonalButton({ onAction(a) }, modifier = m, shapes = ButtonDefaults.shapes()) { Text(label(R.string.action_install), maxLines = 1) }
            CardAction.Open -> OutlinedButton({ onAction(a) }, modifier = m, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_open), maxLines = 1) }
            null -> Unit
        }
    }
}

/** For an app with no build for this phone: its page on vuhpquang.com. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = modifier.heightIn(min = DepotTokens.TouchMin), shapes = ButtonDefaults.shapes()) {
        Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(DepotTokens.IconSmall))
        Text(stringResource(R.string.action_web_page), modifier = Modifier.padding(start = DepotTokens.SpaceSm), maxLines = 1)
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

/** What an install is doing, in words. */
@Composable
fun activityLabel(a: CardActivity): String = when (a) {
    CardActivity.Queued -> stringResource(R.string.progress_queued)
    is CardActivity.WaitingForOthers -> pluralStringResource(R.plurals.progress_waiting_for_others, a.count, a.count)
    is CardActivity.Downloading -> stringResource(R.string.progress_downloading, a.percent)
    CardActivity.Verifying -> stringResource(R.string.progress_verifying)
    CardActivity.Installing -> stringResource(R.string.progress_installing)
    CardActivity.WaitingForConfirm -> stringResource(R.string.progress_confirm)
}

/** Progress (wavy), the permission prompt or the last error. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardFooter(model: CardModel, onAllowInstalls: () -> Unit, modifier: Modifier = Modifier) {
    if (model.activity == null && !model.needsPermission && model.error == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
        model.activity?.let { a ->
            Text(activityLabel(a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DepotProgress(a)
        }
        if (model.needsPermission) {
            Text(stringResource(R.string.needs_permission), style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onAllowInstalls, modifier = Modifier.heightIn(min = DepotTokens.TouchMin), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.action_allow_installs))
            }
        }
        model.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
