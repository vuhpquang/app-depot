package com.vuhpquang.appdepot.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon
import com.vuhpquang.appdepot.ui.CardActivity
import com.vuhpquang.appdepot.ui.GroupPosition
import com.vuhpquang.appdepot.ui.theme.DepotTokens

/** A card's shape in a vertical connected group: large outer corners, small inner ones. */
fun groupShape(position: GroupPosition): Shape {
    val o = DepotTokens.RadiusGroupOuter
    val i = DepotTokens.RadiusGroupInner
    return when (position) {
        GroupPosition.Single -> RoundedCornerShape(o)
        GroupPosition.First -> RoundedCornerShape(topStart = o, topEnd = o, bottomEnd = i, bottomStart = i)
        GroupPosition.Middle -> RoundedCornerShape(i)
        GroupPosition.Last -> RoundedCornerShape(topStart = i, topEnd = i, bottomEnd = o, bottomStart = o)
    }
}

/** The same for a horizontal row of tiles (detail facts). */
fun rowGroupShape(position: GroupPosition): Shape {
    val o = DepotTokens.RadiusGroupOuter
    val i = DepotTokens.RadiusGroupInner
    return when (position) {
        GroupPosition.Single -> RoundedCornerShape(o)
        GroupPosition.First -> RoundedCornerShape(topStart = o, topEnd = i, bottomEnd = i, bottomStart = o)
        GroupPosition.Middle -> RoundedCornerShape(i)
        GroupPosition.Last -> RoundedCornerShape(topStart = i, topEnd = o, bottomEnd = o, bottomStart = i)
    }
}

/** One card of a connected group; clickable when [onClick] is given. */
@Composable
fun ConnectedSurface(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    val shape = groupShape(position)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, content = content)
    } else {
        Surface(modifier.fillMaxWidth(), shape = shape, color = color, content = content)
    }
}

/** Content centred on an Expressive shape (detail hero, About). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeBackdrop(
    polygon: RoundedPolygon,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = polygon.toShape()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().clip(shape).background(color))
        content()
    }
}

/** The detail hero's backdrop: a soft burst, never a plain circle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val HeroShape: RoundedPolygon get() = MaterialShapes.SoftBurst

/** About's backdrop. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val AboutShape: RoundedPolygon get() = MaterialShapes.Cookie9Sided

/** Wavy progress for an install: determinate while downloading, indeterminate otherwise, none while waiting for the user. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DepotProgress(activity: CardActivity, modifier: Modifier = Modifier) {
    val bar = modifier.fillMaxWidth()
    if (activity is CardActivity.Downloading && activity.percent > 0) {
        val p by animateFloatAsState(activity.percent / 100f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "download")
        LinearWavyProgressIndicator(progress = { p }, modifier = bar)
    } else if (activity != CardActivity.WaitingForConfirm) {
        LinearWavyProgressIndicator(modifier = bar)
    }
}
