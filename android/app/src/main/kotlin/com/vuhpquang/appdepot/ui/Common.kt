package com.vuhpquang.appdepot.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vuhpquang.appdepot.R

/**
 * Back + title. With a [scrollBehavior] it is Expressive's medium flexible bar (big title that
 * collapses as the content scrolls); without one, a plain bar (detail, where the hero carries the name).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, scrollBehavior: TopAppBarScrollBehavior? = null, showTitle: Boolean = true) {
    val back: @Composable () -> Unit = {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    if (scrollBehavior != null) {
        MediumFlexibleTopAppBar(title = { Text(title) }, navigationIcon = back, colors = colors, scrollBehavior = scrollBehavior)
    } else {
        TopAppBar(
            title = {
                AnimatedVisibility(showTitle, enter = fadeIn(), exit = fadeOut()) { Text(title) }
            },
            navigationIcon = back,
            colors = colors,
        )
    }
}

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}

fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

/** "just now" under a minute, else "5 minutes ago" / "yesterday" / a date. */
@Composable
fun relativeTimeText(epochMs: Long): String {
    val now = System.currentTimeMillis()
    return if (now - epochMs < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.just_now)
    else DateUtils.getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS).toString()
}
