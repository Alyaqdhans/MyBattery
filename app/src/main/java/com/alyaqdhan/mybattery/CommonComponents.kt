package com.alyaqdhan.mybattery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescanPullToRefreshBox(
    isRefreshing: Boolean,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val blueColor = if (isSystemInDarkTheme()) AccentBlue else AccentBlueLight
    val state     = rememberPullToRefreshState()
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
                color        = blueColor
            )
        },
        content = content
    )
}

data class SetupStep(val icon: Painter, val title: String, val desc: String)

@Composable
fun SetupStepRow(number: Int, step: SetupStep) {
    val green  = accentGreenEffective()
    val card   = MaterialTheme.colorScheme.surfaceContainerLow
    val border = cardBorderColor()
    val tp     = textPrimary()
    val ts     = textSecondary()

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).background(card, CircleShape).border(1.5.dp, border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                color = green,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    step.icon, null,
                    tint     = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    step.title,
                    color = tp,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(step.desc, color = ts, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp))
        }
    }
}
