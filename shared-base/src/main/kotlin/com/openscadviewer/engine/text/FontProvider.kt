package com.openscadviewer.engine.text

/**
 * Platform-agnostic interface for extracting glyph polygon outlines from fonts.
 */
interface FontProvider {
    /**
     * Get the flattened polygon outline for a character at the given size.
     * Returns cached data if available, otherwise extracts and caches.
     */
    fun getGlyphOutline(char: Char, fontName: String, size: Float): GlyphOutline?

    /**
     * Get font metrics (ascent, descent) for the given font at the given size.
     */
    fun getFontMetrics(fontName: String, size: Float): FontMetrics

    /**
     * Check if a specific font is available on this platform.
     */
    fun isFontAvailable(fontName: String): Boolean
}
