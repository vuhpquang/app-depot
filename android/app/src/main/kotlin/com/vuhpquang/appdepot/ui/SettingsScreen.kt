package com.vuhpquang.appdepot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.SELF_SLUG
import com.vuhpquang.appdepot.ui.components.AppIcon
import com.vuhpquang.appdepot.ui.theme.DepotTokens

private const val PAIR_CODE_LENGTH = 8
private const val REPO_URL = "https://github.com/vuhpquang/app-depot"

// Author identity: verbatim from app-shell's AppShellCore/Author.swift.
private const val AUTHOR_WEBSITE = "https://vuhpquang.com"
private const val AUTHOR_GITHUB = "https://github.com/vuhpquang"
private const val AUTHOR_LINKEDIN = "https://www.linkedin.com/in/vuhpquang/"

@Composable
fun SettingsScreen(vm: AppDepotViewModel, onBack: () -> Unit) {
    val s by vm.settingsUi.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = DepotTokens.SpaceMd, vertical = DepotTokens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceLg),
        ) {
            Section(stringResource(R.string.section_updates)) {
                SwitchRow(stringResource(R.string.auto_check), stringResource(R.string.auto_check_summary), s.autoCheck, true, vm::setAutoCheck)
                SwitchRow(
                    stringResource(R.string.auto_install), stringResource(R.string.auto_install_summary),
                    s.autoInstall && s.autoCheck, s.autoCheck, vm::setAutoInstall,
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = DepotTokens.SpaceMd, end = DepotTokens.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (s.lastChecked > 0) stringResource(R.string.last_checked, relativeTimeText(s.lastChecked))
                        else stringResource(R.string.last_checked_never),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(vm::refresh, enabled = !home.refreshing, modifier = Modifier.heightIn(min = DepotTokens.TouchMin)) {
                        Text(stringResource(R.string.check_now))
                    }
                }
            }
            Section(stringResource(R.string.section_connection)) { ConnectionBlock(s, vm) }
            Section(stringResource(R.string.section_about)) { AboutBlock(vm, home) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = DepotTokens.SpaceMd),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DepotTokens.RadiusLg),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(vertical = DepotTokens.SpaceSm), content = content)
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val alpha = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = DepotTokens.TouchMin)
            .padding(horizontal = DepotTokens.SpaceMd, vertical = DepotTokens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceMd),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = alpha)
            Text(
                summary, style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else alpha,
            )
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun ConnectionBlock(s: SettingsUi, vm: AppDepotViewModel) {
    val pad = Modifier.padding(horizontal = DepotTokens.SpaceMd, vertical = DepotTokens.SpaceSm)
    if (s.paired) {
        Row(pad.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.paired_as, s.deviceName), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            OutlinedButton(vm::disconnect, Modifier.heightIn(min = DepotTokens.TouchMin)) { Text(stringResource(R.string.action_disconnect)) }
        }
        return
    }
    var entering by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    Column(pad, verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
        Text(stringResource(R.string.connect_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!entering) {
            FilledTonalButton({ entering = true }, Modifier.heightIn(min = DepotTokens.TouchMin)) { Text(stringResource(R.string.connect)) }
        } else {
            val ready = code.length == PAIR_CODE_LENGTH && !s.pairing
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.filter { it.isLetterOrDigit() }.uppercase().take(PAIR_CODE_LENGTH) },
                label = { Text(stringResource(R.string.pair_code_label)) },
                supportingText = { Text(stringResource(R.string.pair_code_hint)) },
                singleLine = true,
                isError = s.pairError != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ready) vm.pair(code) }),
                modifier = Modifier.fillMaxWidth(),
            )
            s.pairError?.let {
                Text(
                    stringResource(if (it == PairError.BadCode) R.string.pair_bad_code else R.string.pair_failed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
                TextButton({ entering = false; code = "" }, Modifier.heightIn(min = DepotTokens.TouchMin)) { Text(stringResource(R.string.action_cancel)) }
                Button({ vm.pair(code) }, enabled = ready, modifier = Modifier.heightIn(min = DepotTokens.TouchMin)) {
                    Text(stringResource(R.string.action_connect))
                }
            }
        }
    }
}

@Composable
private fun AboutBlock(vm: AppDepotViewModel, home: HomeUi) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(false) }
    var noNotes by remember { mutableStateOf(false) }
    val self = home.rows.firstOrNull { it.app.slug == SELF_SLUG }
    Column(
        Modifier.fillMaxWidth().padding(DepotTokens.SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm),
    ) {
        AppIcon(stringResource(R.string.app_name), null, size = DepotTokens.IconHeader)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(stringResource(R.string.version_line, vm.versionName, vm.versionCode.toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (checked && !home.refreshing) {
            val status = when (val st = self?.state) {
                null -> stringResource(R.string.self_not_listed)
                is AppState.UpdateAvailable -> stringResource(R.string.self_update_available, st.latest.version)
                else -> stringResource(R.string.self_up_to_date)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
        if (self?.state is AppState.UpdateAvailable) {
            Button({ vm.install(SELF_SLUG) }, Modifier.heightIn(min = DepotTokens.TouchMin), enabled = self.model.activity == null) {
                Text(stringResource(R.string.action_update))
            }
        } else {
            OutlinedButton({ checked = true; vm.refresh() }, Modifier.heightIn(min = DepotTokens.TouchMin), enabled = !home.refreshing) {
                Text(stringResource(R.string.check_for_updates))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            TextButton({ noNotes = !vm.showWhatsNew() }, Modifier.heightIn(min = DepotTokens.TouchMin)) { Text(stringResource(R.string.whats_new_button)) }
            TextButton({ context.openUrl("$REPO_URL/issues/new") }, Modifier.heightIn(min = DepotTokens.TouchMin)) { Text(stringResource(R.string.report_issue)) }
        }
        if (noNotes) {
            Text(stringResource(R.string.no_whats_new), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(vertical = DepotTokens.SpaceSm), color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.made_by), style = MaterialTheme.typography.bodyMedium)
        // Author links on one line (each carries the dot before it, so a wrap never leaves a dangling dot);
        // the project's source on its own line.
        val links = listOf(
            stringResource(R.string.link_website) to AUTHOR_WEBSITE,
            stringResource(R.string.link_github) to AUTHOR_GITHUB,
            stringResource(R.string.link_linkedin) to AUTHOR_LINKEDIN,
        )
        FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            links.forEachIndexed { i, (label, url) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i > 0) Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinkText(label) { context.openUrl(url) }
                }
            }
        }
        LinkText(stringResource(R.string.link_source)) { context.openUrl(REPO_URL) }
        Text(stringResource(R.string.copyright), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinkText(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = DepotTokens.TouchMin).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DepotTokens.SpaceSm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
