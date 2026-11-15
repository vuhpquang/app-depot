package com.vuhpquang.appdepot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.ui.theme.DepotTokens

@Composable
fun UpdateAllBar(count: Int, running: Boolean, onUpdateAll: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DepotTokens.RadiusLg),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(start = DepotTokens.SpaceMd, end = DepotTokens.SpaceSm, top = DepotTokens.SpaceSm, bottom = DepotTokens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm),
        ) {
            Text(
                pluralStringResource(R.plurals.updates_available, count, count),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Button(onUpdateAll, enabled = !running, modifier = Modifier.heightIn(min = DepotTokens.TouchMin)) {
                Text(if (running) stringResource(R.string.update_all_running) else stringResource(R.string.update_all, count))
            }
        }
    }
}
