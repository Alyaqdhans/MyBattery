package com.alyaqdhan.mybattery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * The app's mark: a battery with a heartbeat through it, on the same wavy edge as the health
 * gauge. It's drawn rather than loaded so it takes the theme's colors and the pulse can trace
 * itself in. Direction-neutral, so it stays the same way round in Arabic.
 *
 * The depth comes from the glow, not from Modifier.shadow: on Android 10 a shadow around this
 * concave edge makes the render thread hang.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    val primary   = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val badge     = MaterialShapes.Cookie12Sided.toShape()

    val pop   = remember { Animatable(0.7f) }
    val trace = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        trace.animateTo(1f, tween(1000, delayMillis = 250, easing = FastOutSlowInEasing))
    }

    Canvas(
        modifier
            .size(72.dp)
            .drawBehind {
                // Reaches past the badge without taking up layout space around it
                val radius = size.minDimension * 0.69f
                drawCircle(
                    Brush.radialGradient(listOf(primary.copy(alpha = 0.24f), Color.Transparent), center, radius),
                    radius
                )
            }
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .background(Brush.linearGradient(listOf(primary, lerp(primary, onPrimary, 0.24f))), badge)
    ) {
        val unit = size.minDimension / 100f

        // Battery body and terminal
        drawRoundRect(
            color        = onPrimary,
            topLeft      = Offset(22f * unit, 34f * unit),
            size         = Size(50f * unit, 32f * unit),
            cornerRadius = CornerRadius(9f * unit),
            style        = Stroke(width = 4.6f * unit)
        )
        drawRoundRect(
            color        = onPrimary,
            topLeft      = Offset(75.5f * unit, 44f * unit),
            size         = Size(4f * unit, 12f * unit),
            cornerRadius = CornerRadius(2f * unit)
        )

        // Heartbeat
        val pulse = Path().apply {
            moveTo(29f * unit, 50f * unit)
            lineTo(39f * unit, 50f * unit)
            lineTo(44f * unit, 41f * unit)
            lineTo(51f * unit, 59f * unit)
            lineTo(56f * unit, 50f * unit)
            lineTo(65f * unit, 50f * unit)
        }
        val measure = PathMeasure().apply { setPath(pulse, false) }
        val traced  = Path()
        measure.getSegment(0f, measure.length * trace.value, traced, true)
        drawPath(
            path  = traced,
            color = onPrimary,
            style = Stroke(width = 3.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** The card every Setup section sits in, matching the Dashboard's cards. */
@Composable
fun SetupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = cardColor()),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        content   = content
    )
}

data class SetupStep(
    @param:DrawableRes val icon: Int,
    val title: String,
    val desc: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onEditAction: (() -> Unit)? = null,
    val content: (@Composable () -> Unit)? = null
)

/** One stop on the steps timeline. A finished step swaps its number for a tick. */
@Composable
fun SetupStepRow(number: Int, step: SetupStep, isLast: Boolean, isDone: Boolean = false) {
    val green      = accentGreenEffective()
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(if (isDone) green else green.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    MatchedIcon(
                        R.drawable.ic_sym_check,
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        Modifier.size(18.dp),
                        MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "$number".localDigits(),
                        color = green,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .padding(vertical = 5.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(cardBorderColor(), RoundedCornerShape(1.dp))
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f).padding(top = 5.dp, bottom = if (isLast) 0.dp else 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MatchedIcon(step.icon, titleStyle, Modifier.size(18.dp), green)
                Spacer(Modifier.width(8.dp))
                Text(step.title, color = textPrimary(), style = titleStyle)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                step.desc,
                color = textSecondary(),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
            )
            if (step.actionLabel != null && step.onAction != null && step.onEditAction != null) {
                Spacer(Modifier.height(10.dp))
                DialCodeButton(step.actionLabel, step.onAction, step.onEditAction)
            }
            step.content?.let {
                Spacer(Modifier.height(12.dp))
                it()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialCodeButton(label: String, onDial: () -> Unit, onEdit: () -> Unit) {
    val green     = accentGreenEffective()
    val codeStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight    = FontWeight.Bold,
        textDirection = TextDirection.Ltr
    )
    val editLabel = stringResource(R.string.dial_edit_title)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape  = RoundedCornerShape(12.dp),
            color  = green.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, green.copy(alpha = 0.35f))
        ) {
            Row(
                Modifier
                    .combinedClickable(onClick = onDial, onLongClick = onEdit)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MatchedIcon(R.drawable.ic_sym_call, codeStyle, Modifier.size(16.dp), green)
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color    = green,
                    style    = codeStyle,
                    softWrap = false
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onEdit) {
            MatchedIcon(
                R.drawable.ic_sym_edit,
                codeStyle,
                Modifier.size(18.dp).semantics { contentDescription = editLabel },
                textSecondary()
            )
        }
    }
}

/** Step 4 once the folder is granted: how things stand, and how to change them. */
@Composable
fun FolderAccess(
    isScanning: Boolean,
    onOpenSettings: () -> Unit,
    onChangeFolder: () -> Unit,
    onRemoveAccess: () -> Unit
) {
    val green         = accentGreenEffective()
    val actionStyle   = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val settingsLabel = stringResource(R.string.setup_open_app_settings)
    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    Column {
        Surface(
            onClick = onOpenSettings,
            enabled = !isScanning,
            shape   = RoundedCornerShape(12.dp),
            color   = green.copy(alpha = 0.12f),
            border  = BorderStroke(1.dp, green.copy(alpha = 0.35f))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanning) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = green, strokeWidth = 2.dp)
                } else {
                    MatchedIcon(R.drawable.ic_sym_check_circle, actionStyle, Modifier.size(16.dp), green)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (isScanning) R.string.setup_scanning else R.string.setup_permission_granted),
                    modifier = Modifier.weight(1f),
                    color    = green,
                    style    = actionStyle
                )
                if (!isScanning) {
                    MatchedIcon(
                        R.drawable.ic_sym_chevron_right,
                        actionStyle,
                        Modifier.size(18.dp).semantics { contentDescription = settingsLabel },
                        green
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ButtonPair(spacing = 10.dp) {
            Button(
                onClick        = onChangeFolder,
                enabled        = !isScanning,
                modifier       = Modifier.heightIn(min = 44.dp),
                shape          = RoundedCornerShape(12.dp),
                contentPadding = buttonPadding,
                colors         = ButtonDefaults.buttonColors(
                    containerColor = green,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.setup_change_folder), style = actionStyle, textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick        = onRemoveAccess,
                enabled        = !isScanning,
                modifier       = Modifier.heightIn(min = 44.dp),
                shape          = RoundedCornerShape(12.dp),
                contentPadding = buttonPadding,
                border         = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (isScanning) 0.3f else 0.6f)),
                colors         = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.setup_remove_access), style = actionStyle, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Two buttons side by side and equally wide when both labels fit on one line, or stacked at full
 * width when they don't, so large text sizes and longer translations never wrap a label.
 */
@Composable
private fun ButtonPair(spacing: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gap   = spacing.roundToPx()
        val width = constraints.maxWidth
        val half  = (width - gap) / 2
        val sideBySide = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= half }

        val placeables = measurables.map {
            it.measure(Constraints.fixedWidth(if (sideBySide) half else width))
        }
        val height = if (sideBySide) {
            placeables.maxOf { it.height }
        } else {
            placeables.sumOf { it.height } + gap * (placeables.size - 1)
        }
        layout(width, height) {
            var x = 0
            var y = 0
            placeables.forEach {
                it.placeRelative(x, y)
                if (sideBySide) x += it.width + gap else y += it.height + gap
            }
        }
    }
}
