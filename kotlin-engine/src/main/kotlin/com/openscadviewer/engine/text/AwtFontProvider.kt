package com.openscadviewer.engine.text

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.GlyphMetrics
import java.awt.font.LineMetrics
import java.awt.geom.PathIterator
import java.awt.Shape

/**
 * FontProvider implementation using Java AWT Font/GlyphVector APIs.
 * Used for desktop JVM (benchmarks, CLI tool).
 *
 * Extracts glyph outlines at REFERENCE_SIZE and caches them.
 * On retrieval, cached outlines are scaled to the requested size.
 */
class AwtFontProvider : FontProvider {

    private val cache = GlyphCache()

    /** Bundled Liberation Sans loaded from classpath resource. */
    private val bundledFont: Font = loadBundledFont()

    /** Cache of resolved AWT Font objects by font name. */
    private val fontCache = mutableMapOf<String, Font>()

    override fun getGlyphOutline(char: Char, fontName: String, size: Float): GlyphOutline? {
        val cacheKey = GlyphCacheKey(fontName, char)
        cache.get(cacheKey)?.let { return it.scaledTo(size) }

        val font = resolveFont(fontName).deriveFont(REFERENCE_SIZE)
        val frc = FontRenderContext(null, true, true)
        val gv = font.createGlyphVector(frc, charArrayOf(char))

        // If the font cannot display this character, return a placeholder
        if (gv.numGlyphs == 0) {
            return createPlaceholder(font, frc).also {
                cache.put(cacheKey, it)
            }.scaledTo(size)
        }

        val shape = gv.getGlyphOutline(0)
        val metrics = gv.getGlyphMetrics(0)

        val outline = flattenShape(shape, metrics, font, frc)
        cache.put(cacheKey, outline)
        return outline.scaledTo(size)
    }

    override fun getFontMetrics(fontName: String, size: Float): FontMetrics {
        val font = resolveFont(fontName).deriveFont(size)
        val frc = FontRenderContext(null, true, true)
        val lineMetrics: LineMetrics = font.getLineMetrics("Ag", frc)
        return FontMetrics(
            ascent = lineMetrics.ascent,
            descent = -lineMetrics.descent, // FontMetrics uses negative descent
            lineHeight = lineMetrics.height
        )
    }

    override fun isFontAvailable(fontName: String): Boolean {
        val font = Font(fontName, Font.PLAIN, 12)
        // AWT returns a default font if the requested one isn't found.
        // Check if the resolved family matches what was requested.
        return font.family.equals(fontName, ignoreCase = true) ||
                font.name.equals(fontName, ignoreCase = true)
    }

    /**
     * Resolve a font name to an AWT Font instance.
     * Tries system font first; falls back to bundled Liberation Sans.
     */
    private fun resolveFont(fontName: String): Font {
        fontCache[fontName]?.let { return it }

        val resolved = if (fontName.equals("Liberation Sans", ignoreCase = true)) {
            bundledFont
        } else {
            val systemFont = Font(fontName, Font.PLAIN, 12)
            // Check if AWT actually resolved to the requested font
            if (systemFont.family.equals(fontName, ignoreCase = true) ||
                systemFont.name.equals(fontName, ignoreCase = true)) {
                // Verify the font can display typical characters
                if (systemFont.canDisplay('A')) {
                    systemFont
                } else {
                    bundledFont
                }
            } else {
                bundledFont
            }
        }

        fontCache[fontName] = resolved
        return resolved
    }

    /**
     * Flatten a Shape (glyph outline) to polygon segments using PathIterator
     * with automatic Bezier flattening at the specified flatness tolerance.
     */
    private fun flattenShape(
        shape: Shape,
        metrics: GlyphMetrics,
        font: Font,
        frc: FontRenderContext
    ): GlyphOutline {
        val contours = mutableListOf<List<Pair<Float, Float>>>()
        var currentContour = mutableListOf<Pair<Float, Float>>()
        val coords = FloatArray(6)

        val iterator = shape.getPathIterator(null, FLATNESS_TOLERANCE.toDouble())

        while (!iterator.isDone) {
            when (iterator.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> {
                    // Start a new contour; save previous if non-empty
                    if (currentContour.size >= 3) {
                        contours.add(currentContour.toList())
                    }
                    currentContour = mutableListOf()
                    currentContour.add(Pair(coords[0], coords[1]))
                }
                PathIterator.SEG_LINETO -> {
                    currentContour.add(Pair(coords[0], coords[1]))
                }
                PathIterator.SEG_CLOSE -> {
                    // Close the contour — ensure first point equals last
                    if (currentContour.isNotEmpty()) {
                        val first = currentContour.first()
                        val last = currentContour.last()
                        if (first != last) {
                            currentContour.add(first)
                        }
                        if (currentContour.size >= 3) {
                            contours.add(currentContour.toList())
                        }
                    }
                    currentContour = mutableListOf()
                }
            }
            iterator.next()
        }

        // Handle any remaining unclosed contour
        if (currentContour.size >= 3) {
            val first = currentContour.first()
            val last = currentContour.last()
            if (first != last) {
                currentContour.add(first)
            }
            contours.add(currentContour.toList())
        }

        // Get font ascent/descent at reference size
        val lineMetrics: LineMetrics = font.getLineMetrics("Ag", frc)

        return GlyphOutline(
            contours = contours,
            advanceWidth = metrics.advance,
            ascent = lineMetrics.ascent,
            descent = -lineMetrics.descent
        )
    }

    /**
     * Create a rectangular placeholder glyph for characters that cannot be displayed.
     */
    private fun createPlaceholder(font: Font, frc: FontRenderContext): GlyphOutline {
        val lineMetrics: LineMetrics = font.getLineMetrics("A", frc)
        val ascent = lineMetrics.ascent
        val width = ascent * 0.6f // approximate average width

        val rect = listOf(
            Pair(0f, 0f),
            Pair(width, 0f),
            Pair(width, -ascent),
            Pair(0f, -ascent),
            Pair(0f, 0f) // close
        )

        return GlyphOutline(
            contours = listOf(rect),
            advanceWidth = width,
            ascent = ascent,
            descent = -lineMetrics.descent
        )
    }

    companion object {
        /** Extract glyphs at reference size for caching; scale on retrieval. */
        private const val REFERENCE_SIZE = GlyphOutline.REFERENCE_SIZE

        /** Flatness tolerance for PathIterator Bezier flattening at reference size 100. */
        private const val FLATNESS_TOLERANCE = 0.01f

        /** Load Liberation Sans from classpath resource (bundled in shared-base). */
        private fun loadBundledFont(): Font {
            val stream = AwtFontProvider::class.java.classLoader
                ?.getResourceAsStream("fonts/LiberationSans-Regular.ttf")
                ?: throw IllegalStateException(
                    "Bundled font 'fonts/LiberationSans-Regular.ttf' not found on classpath"
                )
            return stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
        }
    }
}
