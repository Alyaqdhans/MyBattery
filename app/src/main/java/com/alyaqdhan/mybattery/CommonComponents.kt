package com.alyaqdhan.mybattery

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

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
