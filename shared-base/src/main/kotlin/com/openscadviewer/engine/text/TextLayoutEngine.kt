package com.openscadviewer.engine.text

/**
 * Computes character positions for a text string given font metrics and layout parameters.
 * Shared logic between both engines — returns positioned glyph data ready for mesh generation.
 */
object TextLayoutEngine {
    data class PositionedGlyph(
        val char: Char,
        val x: Float,
        val y: Float,
        val outline: GlyphOutline
    )

    /**
     * Layout text characters with proper spacing, alignment, and direction.
     */
    fun layout(
        text: String,
        size: Float,
        halign: String,
        valign: String,
        spacing: Float,
        direction: String,
        fontProvider: FontProvider,
        fontName: String
    ): List<PositionedGlyph> {
        if (text.isEmpty()) return emptyList()

        val glyphs = text.map { ch ->
            ch to (fontProvider.getGlyphOutline(ch, fontName, size)
                ?: fontProvider.getGlyphOutline('\u0000', fontName, size))
        }

        // Compute positions along X axis
        var xCursor = 0f
        val positioned = glyphs.map { (ch, outline) ->
            val glyph = PositionedGlyph(ch, xCursor, 0f, outline!!)
            xCursor += outline.advanceWidth * spacing
            glyph
        }

        val totalWidth = xCursor
        val metrics = fontProvider.getFontMetrics(fontName, size)

        // Apply horizontal alignment offset
        val xOffset = when (halign) {
            "center" -> -totalWidth / 2f
            "right" -> -totalWidth
            else -> 0f  // "left"
        }

        // Apply vertical alignment offset
        val yOffset = when (valign) {
            "bottom" -> -metrics.descent
            "top" -> -metrics.ascent
            "center" -> -(metrics.ascent + metrics.descent) / 2f
            else -> 0f  // "baseline"
        }

        // Apply direction (RTL reverses order and mirrors X positions)
        val directedGlyphs = if (direction == "rtl") {
            positioned.map { it.copy(x = -(it.x + it.outline.advanceWidth)) }
        } else {
            positioned
        }

        return directedGlyphs.map { it.copy(x = it.x + xOffset, y = it.y + yOffset) }
    }
}
