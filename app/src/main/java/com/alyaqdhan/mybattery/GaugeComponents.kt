package com.alyaqdhan.mybattery

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HealthGaugeCard(
    info: BatteryInfo,
    isLoadingDetail: Boolean = false,
    arcAnimatable: Animatable<Float, AnimationVector1D>,
    amplitude: Float,
    gaugeReplayKey: Int = 0
) {
    val isUnsupported = info.healthUnsupported
    val targetPct = if (isUnsupported) 100f
    else (info.healthPercent ?: 0).coerceIn(0, 100).toFloat()

    LaunchedEffect(isLoadingDetail, targetPct, gaugeReplayKey) {
        if (isLoadingDetail) {
            arcAnimatable.animateTo(
                targetValue   = 1f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        } else {
            if (arcAnimatable.value >= 0.02f) arcAnimatable.snapTo(0f)
            arcAnimatable.animateTo(
                targetValue   = targetPct / 100f,
                animationSpec = tween(1400, easing = FastOutSlowInEasing)
            )
        }
    }

    val animPct = arcAnimatable.value * 100f

    data class GS(val color: Color, @param:StringRes val label: Int, @param:StringRes val pill: Int)
    val gs = when {
        isLoadingDetail && animPct >= 99.9f -> GS(gaugeBlue(),   R.string.status_loading,     R.string.status_scanning_log)
        isLoadingDetail                     -> GS(gaugeGreen(),  R.string.status_loading,     R.string.status_scanning_log)
        isUnsupported                       -> GS(gaugeGray(),   R.string.status_unsupported, R.string.status_device_unsupported)
        animPct >= 99.9f                    -> GS(gaugeBlue(),   R.string.health_new,         R.string.health_new_message)
        animPct >= 80f                      -> GS(gaugeGreen(),  R.string.health_good,        R.string.health_good_message)
        animPct >= 60f                      -> GS(gaugeOrange(), R.string.health_fair,        R.string.health_fair_message)
        else                                -> GS(gaugeRed(),    R.string.health_poor,        R.string.health_poor_message)
    }

    val gaugeColor   by animateColorAsState(gs.color, tween(300), label = "gauge_color")
    val currentLabel = stringResource(gs.label)
    val pillMsg      = stringResource(gs.pill)
    val muted        = textMuted()
    val border       = cardBorderColor()

    ElevatedCard(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = cardColor()),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.gauge_title),
                    color = muted,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp)
                )
                if (!isLoadingDetail && !isUnsupported && info.healthSource == "bsoh") {
                    Text(
                        stringResource(R.string.health_source_bsoh),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                ArcGaugePulsing(
                    progress   = animPct / 100f,
                    amplitude  = amplitude,
                    trackColor = border,
                    fillColor  = gaugeColor,
                    modifier   = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isLoadingDetail || isUnsupported) "—"
                        else stringResource(R.string.gauge_percent, animPct.toInt()).localDigits(),
                        color = gaugeColor,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        currentLabel,
                        color = gaugeColor.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                shape  = RoundedCornerShape(10.dp),
                color  = gaugeColor.copy(alpha = if (isUnsupported) 0.0f else 0.1f),
                border = BorderStroke(1.dp, gaugeColor.copy(alpha = if (isUnsupported) 0.4f else 0.25f))
            ) {
                Text(
                    pillMsg,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color    = gaugeColor,
                    style    = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArcGaugePulsing(
    amplitude: Float,
    trackColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
    progress: Float = 1f
) {
    Box(modifier = modifier.scale(scaleX = -1f, scaleY = 1f), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress    = { progress },
            modifier    = Modifier.fillMaxSize(),
            color       = fillColor,
            trackColor  = trackColor,
            stroke      = WavyProgressIndicatorDefaults.circularIndicatorStroke,
            trackStroke = WavyProgressIndicatorDefaults.circularTrackStroke,
            amplitude   = { amplitude },
            wavelength  = WavyProgressIndicatorDefaults.CircularWavelength,
            waveSpeed   = WavyProgressIndicatorDefaults.CircularWavelength
        )
    }
}

@Composable
fun CycleCountCard(
    info: BatteryInfo,
    isLoadingDetail: Boolean = false,
    isNewBattery: Boolean = false,
    gaugeReplayKey: Int = 0
) {
    val cycles        = info.cycleCount ?: 0
    val isUnsupported = info.cycleCount == null
    val maxBar        = 5000f

    val cyclesAnimatable = remember { Animatable(0f) }

    LaunchedEffect(gaugeReplayKey, isLoadingDetail, cycles) {
        if (isLoadingDetail) {
            cyclesAnimatable.snapTo(0f)
        } else {
            cyclesAnimatable.snapTo(0f)
            delay(200)
            cyclesAnimatable.animateTo(
                targetValue   = cycles.toFloat(),
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            )
        }
    }

    val animatedCycles       = cyclesAnimatable.value
    val animatedBarFraction  = (cyclesAnimatable.value / maxBar).coerceIn(0f, 1f)

    val muted   = textMuted()
    val onSurfV = MaterialTheme.colorScheme.onSurfaceVariant
    val border  = cardBorderColor()

    val (statusColorTarget, statusTextRes) = when {
        isLoadingDetail                  -> Pair(gaugeBlue(),   R.string.status_loading)
        isNewBattery                     -> Pair(gaugeBlue(),   R.string.wear_none)
        isUnsupported                    -> Pair(gaugeGray(),   R.string.wear_unavailable)
        animatedCycles < 2000            -> Pair(gaugeGreen(),  R.string.wear_low)
        animatedCycles < 3000            -> Pair(gaugeOrange(), R.string.wear_moderate)
        else                             -> Pair(gaugeRed(),    R.string.wear_high)
    }
    val statusColor by animateColorAsState(statusColorTarget, tween(300), label = "cycle_color")

    ElevatedCard(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = cardColor()),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = if (isUnsupported && !isLoadingDetail) MaterialTheme.colorScheme.surfaceVariant
                    else statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_sym_refresh), null,
                            tint     = if (isUnsupported && !isLoadingDetail) gaugeGray()
                            else statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(R.string.cycles_title),
                        color = muted,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp)
                    )
                    Text(
                        stringResource(
                            if (isUnsupported && !isLoadingDetail) R.string.cycles_not_reported
                            else R.string.cycles_subtitle
                        ),
                        color = onSurfV,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        when {
                            isUnsupported && !isLoadingDetail -> "—"
                            isLoadingDetail                   -> "—"
                            else                              -> animatedCycles.toInt().toString().localDigits()
                        },
                        color = if (isUnsupported && !isLoadingDetail) gaugeGray()
                        else statusColor,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        pluralStringResource(R.plurals.cycles_label, cycles),
                        color = if (isUnsupported && !isLoadingDetail) gaugeGray()
                        else onSurfV,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Surface(
                    shape  = RoundedCornerShape(12.dp),
                    color  = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(statusTextRes),
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp)
                    .background(border, RoundedCornerShape(3.dp))
            ) {
                if (!isUnsupported) {
                    // The bar grows from the right in RTL, and its leading edge is the solid end.
                    val barColors = listOf(statusColor.copy(alpha = 0.7f), statusColor)
                    val isRtl     = LocalLayoutDirection.current == LayoutDirection.Rtl
                    Box(
                        Modifier.fillMaxWidth(animatedBarFraction).fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(if (isRtl) barColors.reversed() else barColors),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0".localDigits(),     color = muted, style = MaterialTheme.typography.labelSmall)
                Text("2000".localDigits(),  color = if (!isUnsupported && animatedCycles >= 2000) gaugeGreen()  else muted,
                    style = MaterialTheme.typography.labelSmall)
                Text("3000".localDigits(),  color = if (!isUnsupported && animatedCycles >= 3000) gaugeOrange() else muted,
                    style = MaterialTheme.typography.labelSmall)
                Text("5000+".localDigits(), color = if (!isUnsupported && animatedCycles >= 5000) gaugeRed()    else muted,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}