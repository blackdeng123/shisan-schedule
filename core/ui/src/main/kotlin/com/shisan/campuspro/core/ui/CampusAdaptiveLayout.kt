package com.shisan.campuspro.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

enum class CampusWindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

data class CampusAdaptiveInfo(
    val widthClass: CampusWindowWidthClass,
    val isHeightCompact: Boolean,
    val separatingHingeBounds: IntRect? = null,
)

val CampusAdaptiveInfo.usesNavigationRail: Boolean
    get() = widthClass != CampusWindowWidthClass.Compact

val CampusAdaptiveInfo.usesTwoPane: Boolean
    get() = widthClass == CampusWindowWidthClass.Expanded

fun classifyCampusAdaptiveInfo(
    widthDp: Int,
    heightDp: Int,
): CampusAdaptiveInfo = CampusAdaptiveInfo(
    widthClass = when {
        widthDp >= 840 -> CampusWindowWidthClass.Expanded
        widthDp >= 600 -> CampusWindowWidthClass.Medium
        else -> CampusWindowWidthClass.Compact
    },
    isHeightCompact = heightDp < 480,
)

val LocalCampusAdaptiveInfo = staticCompositionLocalOf {
    classifyCampusAdaptiveInfo(widthDp = 360, heightDp = 800)
}

fun Modifier.adaptiveContentWidth(maxWidth: Dp = 960.dp): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)
        .fillMaxWidth()

@Composable
fun CampusAdaptiveProvider(
    modifier: Modifier = Modifier,
    adaptiveInfo: CampusAdaptiveInfo? = null,
    content: @Composable () -> Unit,
) {
    if (adaptiveInfo != null) {
        CompositionLocalProvider(LocalCampusAdaptiveInfo provides adaptiveInfo, content = content)
        return
    }

    val hingeBounds = rememberSeparatingHingeBounds()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val info = classifyCampusAdaptiveInfo(
            widthDp = maxWidth.value.toInt(),
            heightDp = maxHeight.value.toInt(),
        ).copy(separatingHingeBounds = hingeBounds)
        CompositionLocalProvider(LocalCampusAdaptiveInfo provides info, content = content)
    }
}

@Composable
fun AdaptiveContentColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 840.dp,
    horizontalPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .padding(horizontal = horizontalPadding),
        ) {
            content()
        }
    }
}

@Composable
fun AdaptiveTwoPane(
    modifier: Modifier = Modifier,
    paneSpacing: Dp = 16.dp,
    mainPane: @Composable () -> Unit,
    supportingPane: @Composable () -> Unit,
) {
    val adaptiveInfo = LocalCampusAdaptiveInfo.current
    if (adaptiveInfo.usesTwoPane) {
        var originX by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { originX = it.positionInWindow().x.toInt() },
        ) {
            val hinge = adaptiveInfo.separatingHingeBounds
            val hingeLeftDp = hinge?.let { with(density) { (it.left - originX).toDp() } }
            val hingeRightDp = hinge?.let { with(density) { (it.right - originX).toDp() } }
            val availableWidth = maxWidth
            val usesHinge = hingeLeftDp != null && hingeRightDp != null &&
                hingeLeftDp > 0.dp && hingeRightDp < availableWidth
            if (usesHinge) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.width(hingeLeftDp!!).fillMaxHeight()) { mainPane() }
                    Box(modifier = Modifier.width(hingeRightDp!! - hingeLeftDp))
                    Box(
                        modifier = Modifier
                            .width(availableWidth - hingeRightDp)
                            .fillMaxHeight()
                            .padding(start = paneSpacing),
                    ) { supportingPane() }
                }
            } else {
                val supportingWidth = (availableWidth / 3).coerceIn(320.dp, 400.dp)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(paneSpacing),
                ) {
                    Box(modifier = Modifier.weight(2f)) { mainPane() }
                    Box(modifier = Modifier.width(supportingWidth)) { supportingPane() }
                }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) { mainPane() }
    }
}

@Composable
private fun rememberSeparatingHingeBounds(): IntRect? {
    val context = LocalContext.current
    val activity = context.findActivity()
    val layoutInfo by produceState<androidx.window.layout.WindowLayoutInfo?>(
        initialValue = null,
        key1 = activity,
    ) {
        if (activity == null) return@produceState
        WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity).collect { value = it }
    }
    val hinge = layoutInfo?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
    return hinge?.bounds?.let { IntRect(it.left, it.top, it.right, it.bottom) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
