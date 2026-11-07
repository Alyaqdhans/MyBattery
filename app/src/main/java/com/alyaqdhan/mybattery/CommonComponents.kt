package com.alyaqdhan.mybattery

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Where every screen's content starts, measured from the top of the window. It's a fixed height
 * and not the status bar's, which differs between phones, so that headings stay level from one
 * screen to the next.
 */
internal val ScreenContentTop = 52.dp

/**
 * How far along its track a scrollbar thumb sits, from 0 to 1.
 *
 * [maxValue] is 0 when the content fits without scrolling. The range is only known once the
 * content is measured, after the scrollbar was composed, so a check there doesn't cover this.
 */
internal fun scrollbarFraction(value: Int, maxValue: Int): Float =
    if (maxValue > 0) (value.toFloat() / maxValue).coerceIn(0f, 1f) else 0f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescanPullToRefreshBox(
    isRefreshing: Boolean,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val indicatorColor = refreshIndicatorColor()
    val state          = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh    = onRescan,
        state        = state,
        modifier     = modifier,
        indicator    = {
            PullToRefreshDefaults.Indicator(
                state        = state,
                isRefreshing = false,
                modifier     = Modifier.align(Alignment.TopCenter),
                color        = indicatorColor
            )
        },
        content = content
    )
}
