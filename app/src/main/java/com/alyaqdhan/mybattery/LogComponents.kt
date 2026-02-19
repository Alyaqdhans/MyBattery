package com.alyaqdhan.mybattery

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

@Composable
fun buildColorizedLogText(
    text: String,
    keyColor: Color,
    valueColor: Color,
    sectionColor: Color
): AnnotatedString = buildAnnotatedString {
    val sectionHeaderRegex = Regex("""^━━━ .+ ━━━$""")
    val kvRegex            = Regex("""^(\s*)(.+?)(\s*[:=]\s*)(.+)$""")

    text.lines().forEach { line ->
        when {
            sectionHeaderRegex.matches(line.trim()) -> {
                withStyle(SpanStyle(color = sectionColor, fontWeight = FontWeight.SemiBold)) {
                    append(line)
                }
            }
            line.trim().isEmpty() -> append(line)
            else -> {
                val match = kvRegex.matchEntire(line)
                if (match != null) {
                    withStyle(SpanStyle(color = keyColor)) { append(match.groupValues[1]) }
                    withStyle(SpanStyle(color = keyColor)) { append(match.groupValues[2]) }
                    withStyle(SpanStyle(color = keyColor.copy(alpha = 0.6f))) { append(match.groupValues[3]) }
                    withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Medium)) {
                        append(match.groupValues[4])
                    }
                } else {
                    withStyle(SpanStyle(color = keyColor)) { append(line) }
                }
            }
        }
        append('\n')
    }
}

fun buildSearchHighlightedText(
    base: AnnotatedString,
    query: String,
    highlightBg: Color,
    highlightFg: Color
): AnnotatedString {
    if (query.isBlank()) return base
    val rawText  = base.text
    val lowerRaw = rawText.lowercase()
    val lowerQ   = query.lowercase()
    if (!lowerRaw.contains(lowerQ)) return base

    return buildAnnotatedString {
        append(base)
        var start = 0
        while (true) {
            val idx = lowerRaw.indexOf(lowerQ, start)
            if (idx < 0) break
            addStyle(
                SpanStyle(background = highlightBg, color = highlightFg),
                start = idx,
                end   = idx + query.length
            )
            start = idx + query.length
        }
    }
}
