package com.alyaqdhan.mybattery

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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

    data class GS(val color: Color, val label: String, val pill: String)
    val gs = when {
        isLoadingDetail && animPct >= 99.9f -> GS(gaugeBlue(),   "Loading",     "Scanning log…")
        isLoadingDetail                     -> GS(gaugeGreen(),  "Loading",     "Scanning log…")
        isUnsupported                       -> GS(gaugeGray(),   "Unsupported", "Device unsupported")
        animPct >= 99.9f                    -> GS(gaugeBlue(),   "New",         "The phone is as fresh as it gets")
        animPct >= 80f                      -> GS(gaugeGreen(),  "Good",        "Battery is in great condition")
        animPct >= 60f                      -> GS(gaugeOrange(), "Fair",        "Battery capacity is declining")
        else                                -> GS(gaugeRed(),    "Poor",        "Battery replacement recommended")
    }

    val gaugeColor   by animateColorAsState(gs.color, tween(300), label = "gauge_color")
    val currentLabel = gs.label
    val pillMsg      = gs.pill
    val muted        = textMuted()
    val border       = cardBorderColor()

    ElevatedCard(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "BATTERY HEALTH",
                    color = muted,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp)
                )
                if (!isLoadingDetail && !isUnsupported && info.healthSource == "bsoh") {
                    Text(
                        "BSOH",
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
                        if (isLoadingDetail || isUnsupported) "—" else "${animPct.toInt()}%",
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
    progress: Float = 1f,
    amplitude: Float,
    trackColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
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
    val maxBar        = 500f

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

    val (statusColor, statusText) = when {
        isLoadingDetail              -> Pair(gaugeBlue(),   "Loading")
        isNewBattery                 -> Pair(gaugeBlue(),   "No wear")
        isUnsupported                -> Pair(MaterialTheme.colorScheme.outlineVariant, "Unavailable")
        cycles < 200                 -> Pair(gaugeGreen(),  "Low wear")
        cycles < 300                 -> Pair(gaugeOrange(), "Moderate wear")
        else                         -> Pair(gaugeRed(),    "High wear")
    }

    ElevatedCard(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            painterResource(R.drawable.refresh), null,
                            tint     = if (isUnsupported && !isLoadingDetail)
                                MaterialTheme.colorScheme.outlineVariant
                            else statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "CHARGE CYCLES",
                        color = muted,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp)
                    )
                    Text(
                        if (isUnsupported && !isLoadingDetail) "Not reported by device"
                        else "Full charge equivalents",
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
                            else                              -> animatedCycles.toInt().toString()
                        },
                        color = if (isUnsupported && !isLoadingDetail)
                            MaterialTheme.colorScheme.outlineVariant
                        else statusColor,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "cycles",
                        color = if (isUnsupported && !isLoadingDetail)
                            MaterialTheme.colorScheme.outlineVariant
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
                            statusText,
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
                    Box(
                        Modifier.fillMaxWidth(animatedBarFraction).fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.7f), statusColor)),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0",    color = muted, style = MaterialTheme.typography.labelSmall)
                Text("200",  color = if (!isUnsupported && cycles >= 200) gaugeGreen()  else muted,
                    style = MaterialTheme.typography.labelSmall)
                Text("300",  color = if (!isUnsupported && cycles >= 300) gaugeOrange() else muted,
                    style = MaterialTheme.typography.labelSmall)
                Text("500+", color = if (!isUnsupported && cycles >= 500) gaugeRed()    else muted,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}