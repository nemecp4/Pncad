package com.openscadviewer.engine.text

/**
 * Flattened polygon outline for a single glyph.
 * Contours are closed polygons; outer contours are CCW, inner (holes) are CW.
 */
data class GlyphOutline(
    val contours: List<List<Pair<Float, Float>>>,  // List of closed polygons
    val advanceWidth: Float,                        // Horizontal advance after this glyph
    val ascent: Float,                              // Distance above baseline
    val descent: Float                              // Distance below baseline (negative)
) {
    companion object {
        /** Reference size at which glyphs are extracted and cached. */
        const val REFERENCE_SIZE = 100.0f
    }

    /**
     * Returns a new GlyphOutline with all point coordinates and metrics scaled
     * from the reference size to the given target size.
     */
    fun scaledTo(size: Float): GlyphOutline {
        val scale = size / REFERENCE_SIZE
        return GlyphOutline(
            contours = contours.map { contour ->
                contour.map { (x, y) -> Pair(x * scale, y * scale) }
            },
            advanceWidth = advanceWidth * scale,
            ascent = ascent * scale,
            descent = descent * scale
        )
    }

    /**
     * Estimates the memory footprint of this outline in bytes.
     * Each point is 2 floats (8 bytes), plus overhead for list structures.
     * Used by GlyphCache to track memory usage.
     */
    fun estimatedSizeBytes(): Long {
        // Each Pair<Float, Float> = 2 floats (8 bytes) + object overhead (~16 bytes)
        // Each inner list has ~40 bytes overhead, outer list ~40 bytes overhead
        val pointCount = contours.sumOf { it.size }
        val pointBytes = pointCount.toLong() * 24L  // 8 bytes data + 16 bytes object overhead
        val listOverhead = (contours.size + 1).toLong() * 40L
        val fieldsOverhead = 16L  // 3 floats + object header
        return pointBytes + listOverhead + fieldsOverhead
    }
}

/**
 * Font-level metrics for layout calculations.
 */
data class FontMetrics(
    val ascent: Float,     // Positive: distance from baseline to top
    val descent: Float,    // Negative: distance from baseline to bottom
    val lineHeight: Float  // Total line height (typically ascent - descent + leading)
)
