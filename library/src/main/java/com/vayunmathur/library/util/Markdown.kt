package com.vayunmathur.library.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Converts a Markdown string into an AnnotatedString for Jetpack Compose.
 * @param mdtext The raw markdown text.
 * @param showMarkers If false, the formatting symbols (#, *, etc.) are hidden and occupy no space.
 * @param process If true, the text is preprocessed for newline rules and list normalization.
 * @param softWrap If true, single newlines in regular text blocks are merged into a single line.
 */
fun parseMarkdown(
    mdtext: String,
    showMarkers: Boolean = true,
    process: Boolean = true,
    softWrap: Boolean = true
): AnnotatedString {
    // 1. Preprocess text for newline rules and list normalization
    val processedText = if (process) {
        val lines = mdtext.lines()
        buildString {
            var i = 0
            while (i < lines.size) {
                val line = lines[i]

                // Rule: 2+ newlines always becomes 1 newline for anything.
                // Since we append \n after every processed block, skipping blank lines
                // effectively reduces 2+ newlines to the 1 newline from the previous block.
                if (line.isBlank()) {
                    while (i + 1 < lines.size && lines[i + 1].isBlank()) i++
                    i++; continue
                }

                val trimmed = line.trimStart()
                val listMatch = Regex("^(\\s*)([•*+-]|\\d+[.)])(\\s+.*)").matchEntire(line)
                val isCurrentSpecial =
                    trimmed.startsWith("#") || trimmed.startsWith(">") || listMatch != null

                if (isCurrentSpecial) {
                    if (listMatch != null) {
                        val rawIndent = listMatch.groups[1]!!.value
                        val level = rawIndent.length / 2
                        val normalizedIndent = "  ".repeat(level)
                        val marker = listMatch.groups[2]!!.value
                        val rest = listMatch.groups[3]!!.value
                        val newMarker =
                            if (marker.length == 1 && "*+-".contains(marker)) "•" else marker
                        append("$normalizedIndent$newMarker ${rest.trimStart()}\n")
                    } else {
                        append(line.trimEnd() + "\n")
                    }
                } else {
                    // Regular text: Rule: 1 newline becomes 0 newlines only when both lines are not headers or bullets
                    var merged = line.trim()
                    if (softWrap) {
                        while (i + 1 < lines.size && lines[i + 1].isNotBlank()) {
                            val nextLine = lines[i + 1]
                            val nextTrimmed = nextLine.trimStart()
                            val nextListMatch =
                                Regex("^(\\s*)([•*+-]|\\d+[.)])(\\s+.*)").matchEntire(nextLine)
                            val isNextSpecial =
                                nextTrimmed.startsWith("#") || nextTrimmed.startsWith(">") || nextListMatch != null

                            if (isNextSpecial) break // Newline preserved if next line is special

                            merged += " " + nextLine.trim()
                            i++
                        }
                    }
                    append(merged + "\n")
                }
                i++
            }
        }.trim()
    } else {
        mdtext
    }

    return buildAnnotatedString {
        append(processedText)

        // Helper to hide formatting markers
        fun hideRange(start: Int, end: Int) {
            if (!showMarkers && start < end) {
                addStyle(
                    style = SpanStyle(
                        color = Color.Transparent,
                        fontSize = 0.sp,
                        textGeometricTransform = TextGeometricTransform(scaleX = 0f)
                    ),
                    start = start,
                    end = end
                )
            }
        }

        // 1. Headers
        val headerRegex = Regex("(?m)^(#{1,6} )(.*(?:\\R|$))")
        headerRegex.findAll(processedText).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val markers = match.groups[1]!!
            val level = markers.value.trim().length
            val fontSize = (32 - (level * 2)).sp

            hideRange(markers.range.first, markers.range.last + 1)

            addStyle(SpanStyle(fontWeight = FontWeight.Bold).copy(fontSize = if (process) fontSize else TextUnit.Unspecified), start, end)
            if (process) {
                addStyle(
                    ParagraphStyle(
                        lineHeight = fontSize * 1.3f,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
                    ),
                    start,
                    end
                )
            }
        }

        // 2. Lists
        val listRegex = Regex("(?m)^(\\s*)([•*+-]|\\d+[.)])\\s+(?:\\[([ xX])]\\s+)?(.*(?:\\R|$))")
        listRegex.findAll(processedText).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val indentation = match.groups[1]!!.value
            val markerString = match.groups[2]!!.value
            val taskStatus = match.groups[3]?.value
            val contentStart = match.groups[4]!!.range.first

            if (!showMarkers) {
                hideRange(match.groups[1]!!.range.first, match.groups[1]!!.range.last + 1)
                if (taskStatus != null) {
                    val taskMarkerStart = processedText.indexOf('[', start)
                    val taskMarkerEnd = processedText.indexOf(']', taskMarkerStart) + 1
                    hideRange(taskMarkerStart, taskMarkerEnd)
                }
            }

            if (process) {
                val level = indentation.length / 2
                val indentBase = 12.sp
                val indentStep = 24.sp
                val firstLineIndent = (indentBase.value + (level * indentStep.value)).sp
                val markerOffset = if (markerString.any { it.isDigit() }) 32.sp else 16.sp

                addStyle(
                    ParagraphStyle(
                        textIndent = TextIndent(firstLine = firstLineIndent, restLine = (firstLineIndent.value + markerOffset.value).sp)
                    ),
                    start,
                    end
                )
            }

            addStyle(
                SpanStyle(
                    color = if (showMarkers) Color.Gray else Color.Unspecified,
                    fontWeight = FontWeight.Bold
                ).copy(fontSize = if (process) (if (markerString == "•") 18.sp else 16.sp) else TextUnit.Unspecified),
                start,
                contentStart
            )

            if (taskStatus != null && taskStatus.lowercase() == "x") {
                addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray), contentStart, end)
            }
        }

        // 3. Inline Formatting (Post-processing)
        
        // Bold
        Regex("(\\*\\*|__)(.*?)\\1").findAll(processedText).forEach { match ->
            val m = match.groups[1]!!.value
            hideRange(match.range.first, match.range.first + m.length)
            hideRange(match.range.last + 1 - m.length, match.range.last + 1)
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
        }

        // Italic
        Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)|(?<!_)_(?!_)(.*?)(?<!_)_(?!_)").findAll(processedText).forEach { match ->
            hideRange(match.range.first, match.range.first + 1)
            hideRange(match.range.last, match.range.last + 1)
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
        }

        // Code
        Regex("`(.+?)`").findAll(processedText).forEach { match ->
            hideRange(match.range.first, match.range.first + 1)
            hideRange(match.range.last, match.range.last + 1)
            addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color.LightGray.copy(0.2f), color = Color(0xFFD32F2F)),
                match.range.first,
                match.range.last + 1
            )
        }

        // Strikethrough
        Regex("~~(.+?)~~").findAll(processedText).forEach { match ->
            hideRange(match.range.first, match.range.first + 2)
            hideRange(match.range.last - 1, match.range.last + 1)
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), match.range.first, match.range.last + 1)
        }

        // Blockquotes
        Regex("(?m)^>\\s").findAll(processedText).forEach { match ->
            hideRange(match.range.first, match.range.last + 1)
            val lineEnd = processedText.indexOf('\n', match.range.first).let { if (it == -1) processedText.length else it }
            addStyle(SpanStyle(color = Color.Gray, fontStyle = FontStyle.Italic), match.range.first, lineEnd)
        }
    }
}
