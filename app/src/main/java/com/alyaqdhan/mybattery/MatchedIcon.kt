package com.alyaqdhan.mybattery

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * An icon drawn at the weight of the text beside it. Material Symbols are cut at one weight, and
 * the text comes in several, in whatever font the phone is set to, so a plain Icon beside a bold
 * label reads thin. Both are measured: how thick the font's strokes are (the stem of its "I") and
 * how thick the icon's are (twice its area over its outline). The icon is then thinned or
 * thickened by the difference: its outline is traced and either cut away or filled in.
 *
 * Decorative: it says nothing to TalkBack, as its label does. Same as Riyal's MatchedIcon, plus
 * mirroring for icons marked autoMirrored, which a plain Icon does on its own.
 */
@Composable
fun MatchedIcon(
    @DrawableRes res: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val vector   = ImageVector.vectorResource(res)
    val shape    = remember(res) { shapes.getOrPut(res) { IconShape.of(vector) } }
    val resolver = LocalFontFamilyResolver.current
    val density  = LocalDensity.current
    val mirrored = vector.autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    val fontStem = if (style.fontSize.isSpecified) {
        val key = Triple(style.fontFamily, style.fontWeight, style.fontStyle)
        // Measured once per family and weight, so repeated icons keep no measurer each.
        val ratio = remember(key) {
            stems.getOrPut(key) { fontStem(TextMeasurer(resolver, density, LayoutDirection.Ltr, cacheSize = 0), style) }
        }
        ratio * with(density) { style.fontSize.toPx() }
    } else {
        null
    }

    Canvas(
        modifier.graphicsLayer {
            // Thinning cuts the outline away, and needs a layer of its own to cut from.
            compositingStrategy =
                if (shape.change(size, fontStem) < 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
    ) {
        val s      = shape.scaleIn(size)
        val change = shape.change(size, fontStem)
        scale(if (mirrored) -1f else 1f, 1f) {
            translate((size.width - shape.width * s) / 2, (size.height - shape.height * s) / 2) {
                scale(s, s, pivot = Offset.Zero) {
                    for (path in shape.paths) {
                        drawPath(path, tint)
                        if (change > 0f) {
                            drawPath(path, tint, style = Stroke(width = change / s, join = StrokeJoin.Round))
                        } else if (change < 0f) {
                            drawPath(
                                path, Color.Black,
                                style     = Stroke(width = -change / s, join = StrokeJoin.Round),
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Each icon's paths and measured stroke, worked out once. */
private val shapes = HashMap<Int, IconShape>()

/** Each font's stem as a share of its size, worked out once per family and weight. */
private val stems = HashMap<Any, Float>()

private class IconShape(val paths: List<Path>, val width: Float, val height: Float, val stem: Float) {

    /** The scale that fits the icon in [size]. */
    fun scaleIn(size: Size): Float = min(size.width / width, size.height / height)

    /**
     * How much thicker to draw the strokes at [size] to match [fontStem], or below zero how much
     * thinner. Never more than half again or half away, so it can't become a blob or a hairline.
     */
    fun change(size: Size, fontStem: Float?): Float {
        if (fontStem == null) return 0f
        val iconStem = stem * height * scaleIn(size)
        return (fontStem - iconStem).coerceIn(-iconStem * 0.5f, iconStem * 0.5f)
    }

    companion object {
        fun of(vector: ImageVector): IconShape {
            val paths = ArrayList<Path>()
            fun walk(group: VectorGroup, dx: Float, dy: Float) {
                for (node in group) {
                    when (node) {
                        is VectorPath -> paths += PathParser().addPathNodes(node.pathData).toPath().apply {
                            fillType = node.pathFillType
                            translate(Offset(dx, dy))
                        }
                        is VectorGroup -> walk(node, dx + node.translationX, dy + node.translationY)
                    }
                }
            }
            walk(vector.root, 0f, 0f)
            val w = vector.viewportWidth
            val h = vector.viewportHeight
            return IconShape(paths, w, h, stemOf(paths, w, h))
        }

        /**
         * Typical stroke thickness as a share of the height. A shape made of strokes has an area
         * of about thickness × length and an outline of about twice the length, so twice the area
         * over the outline is the thickness, measured on a raster.
         */
        private fun stemOf(paths: List<Path>, w: Float, h: Float): Float {
            val px     = 240
            val factor = px / h
            val bw     = max(1, (w * factor).toInt())
            val bitmap = ImageBitmap(bw, px)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(bw.toFloat(), px.toFloat())) {
                scale(factor, factor, pivot = Offset.Zero) { paths.forEach { drawPath(it, Color.Black) } }
            }
            val pixels = bitmap.toPixelMap()
            fun on(x: Int, y: Int) = x in 0 until bw && y in 0 until px && pixels[x, y].alpha > 0.5f
            var area = 0
            var edge = 0
            for (y in 0 until px) for (x in 0 until bw) {
                if (!on(x, y)) continue
                area++
                if (!on(x - 1, y) || !on(x + 1, y) || !on(x, y - 1) || !on(x, y + 1)) edge++
            }
            return if (edge == 0) 0.1f else 2f * area / edge / px
        }
    }
}

/** The stem of the font's "I", as a share of its size. */
private fun fontStem(measurer: TextMeasurer, style: TextStyle): Float {
    val probe  = style.copy(fontSize = 100.sp, color = Color.Black, letterSpacing = 0.sp)
    val layout = measurer.measure("I", probe, density = Density(1f, 1f))
    val bw     = max(1, layout.size.width)
    val bh     = max(1, layout.size.height)
    val bitmap = ImageBitmap(bw, bh)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(bw.toFloat(), bh.toFloat())) {
        drawText(layout)
    }
    val pixels = bitmap.toPixelMap()
    // Half way up the capital: clear of any serif.
    val y = (layout.firstBaseline - 35f).toInt().coerceIn(0, bh - 1)
    var run     = 0
    var longest = 0
    for (x in 0 until bw) {
        if (pixels[x, y].alpha > 0.5f) run++ else run = 0
        longest = max(longest, run)
    }
    return if (longest == 0) 0.09f else longest / 100f
}
