package com.vuhpquang.appdepot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.ui.CardChip
import com.vuhpquang.appdepot.ui.theme.DepotTokens

@Composable
fun StatusChip(chip: CardChip, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (chip) {
        CardChip.UpdateAvailable -> c.primaryContainer to c.onPrimaryContainer
        CardChip.LocalBuild -> c.tertiaryContainer to c.onTertiaryContainer
        CardChip.UpToDate, CardChip.SelfUpdates -> c.secondaryContainer to c.onSecondaryContainer
        CardChip.NoRelease, CardChip.NotInstallable, CardChip.Skipped, CardChip.NotOnThisDevice -> c.surfaceContainerHighest to c.onSurfaceVariant
    }
    val label = when (chip) {
        CardChip.UpToDate -> R.string.chip_up_to_date
        CardChip.UpdateAvailable -> R.string.chip_update_available
        CardChip.Skipped -> R.string.chip_skipped
        CardChip.LocalBuild -> R.string.chip_local_build
        CardChip.NoRelease -> R.string.chip_no_release
        CardChip.NotInstallable -> R.string.chip_not_installable
        CardChip.SelfUpdates -> R.string.chip_self_updates
        CardChip.NotOnThisDevice -> R.string.chip_not_on_device
    }
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier
            .background(bg, CircleShape)
            .padding(horizontal = DepotTokens.SpaceSm, vertical = DepotTokens.SpaceXs / 2),
    )
}
