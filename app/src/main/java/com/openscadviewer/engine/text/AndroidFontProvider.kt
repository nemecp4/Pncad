package com.openscadviewer.engine.text

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * FontProvider implementation using Android's Paint.getTextPath() API.
 * Extracts glyph outlines from system or bundled TrueType fonts.
 *
 * Uses Path.approximate() (API 26+) to flatten Bezier curves to polygon segments.
 * Glyphs are extracted at REFERENCE_SIZE and cached; retrieval scales to the requested size.
 */
class AndroidFontProvider(private val context: Context) : FontProvider {

    private val cache = GlyphCache()

    /** Bundled Liberation Sans loaded from assets. */
    private val bundledTypeface: Typeface = loadBundledTypeface()

    /** Cache of resolved Typeface objects by font name. */
    private val typefaceCache = mutableMapOf<String, Typeface>()

    override fun getGlyphOutline(char: Char, fontName: String, size: Float): GlyphOutline? {
        val cacheKey = GlyphCacheKey(fontName, char)
        cache.get(cacheKey)?.let { return it.scaledTo(size) }

        val typeface = resolveTypeface(fontName)
        val paint = Paint().apply {
            this.typeface = typeface
            textSize = REFERENCE_SIZE
            isAntiAlias = true
        }

        val path = Path()
        paint.getTextPath(char.toString(), 0, 1, 0f, 0f, path)

        val outline = flattenPath(path, paint, char)
        cache.put(cacheKey, outline)
        return outline.scaledTo(size)
    }

    override fun getFontMetrics(fontName: String, size: Float): FontMetrics {
        val typeface = resolveTypeface(fontName)
        val paint = Paint().apply {
            this.typeface = typeface
            textSize = size
            isAntiAlias = true
        }
        val fm = paint.fontMetrics
        return FontMetrics(
            ascent = -fm.ascent,     // Paint.FontMetrics.ascent is negative (above baseline)
            descent = -fm.descent,   // Paint.FontMetrics.descent is positive (below baseline) → negate for our convention
            lineHeight = fm.descent - fm.ascent + fm.leading
        )
    }

    override fun isFontAvailable(fontName: String): Boolean {
        if (fontName.equals("Liberation Sans", ignoreCase = true)) return true
        // Create the typeface and check if Android resolved it to something other than default
        val requested = Typeface.create(fontName, Typeface.NORMAL)
        val defaultTf = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        return requested != defaultTf
    }

    /**
     * Resolve a font name to an Android Typeface instance.
     * Tries system font first; falls back to bundled Liberation Sans.
     */
    private fun resolveTypeface(fontName: String): Typeface {
        typefaceCache[fontName]?.let { return it }

        val resolved = if (fontName.equals("Liberation Sans", ignoreCase = true)) {
            bundledTypeface
        } else {
            val systemTypeface = Typeface.create(fontName, Typeface.NORMAL)
            val defaultTypeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            // Android returns default if the requested font isn't found
            if (systemTypeface == defaultTypeface) {
                bundledTypeface
            } else {
                systemTypeface
            }
        }

        typefaceCache[fontName] = resolved
        return resolved
    }

    /**
     * Flatten a Path (containing Bezier curves) to polygon segments.
     * Uses Path.approximate() (API 26+) which returns a FloatArray of (fraction, x, y) triples.
     *
     * Detects contour boundaries by a reset of fraction to 0.0 after the first triple.
     * Groups points into closed contours.
     */
    private fun flattenPath(path: Path, paint: Paint, char: Char): GlyphOutline {
        val contours = mutableListOf<List<Pair<Float, Float>>>()

        if (!path.isEmpty) {
            val approximation = path.approximate(FLATNESS_TOLERANCE)

            // approximation is a FloatArray with triples: (fraction, x, y)
            // Each contour starts when fraction resets to 0.0 (except the very first triple)
            var currentContour = mutableListOf<Pair<Float, Float>>()
            var isFirst = true

            var i = 0
            while (i < approximation.size) {
                val fraction = approximation[i]
                val x = approximation[i + 1]
                val y = approximation[i + 2]

                if (!isFirst && fraction == 0.0f) {
                    // New contour boundary — close and save the current one
                    if (currentContour.size >= 3) {
                        closeAndAddContour(currentContour, contours)
                    }
                    currentContour = mutableListOf()
                }
                isFirst = false

                currentContour.add(Pair(x, y))
                i += 3
            }

            // Handle final contour
            if (currentContour.size >= 3) {
                closeAndAddContour(currentContour, contours)
            }
        }

        // Get advance width and font metrics at reference size
        val advanceWidth = paint.measureText(char.toString())
        val fm = paint.fontMetrics

        return GlyphOutline(
            contours = contours,
            advanceWidth = advanceWidth,
            ascent = -fm.ascent,   // Convert from negative (above baseline) to positive
            descent = -fm.descent  // Convert from positive (below baseline) to negative
        )
    }

    /**
     * Ensure the contour is closed (first == last) and add it to the contours list.
     */
    private fun closeAndAddContour(
        contour: MutableList<Pair<Float, Float>>,
        contours: MutableList<List<Pair<Float, Float>>>
    ) {
        if (contour.isEmpty()) return
        val first = contour.first()
        val last = contour.last()
        if (first != last) {
            contour.add(first)
        }
        contours.add(contour.toList())
    }

    /**
     * Create a rectangular placeholder glyph for characters that cannot be rendered.
     */
    private fun createPlaceholder(paint: Paint): GlyphOutline {
        val fm = paint.fontMetrics
        val ascent = -fm.ascent
        val width = ascent * 0.6f

        val rect = listOf(
            Pair(0f, 0f),
            Pair(width, 0f),
            Pair(width, -ascent),
            Pair(0f, -ascent),
            Pair(0f, 0f)
        )

        return GlyphOutline(
            contours = listOf(rect),
            advanceWidth = width,
            ascent = ascent,
            descent = -fm.descent
        )
    }

    /** Load bundled Liberation Sans from app assets. */
    private fun loadBundledTypeface(): Typeface {
        return Typeface.createFromAsset(context.assets, "fonts/LiberationSans-Regular.ttf")
    }

    companion object {
        /** Extract glyphs at reference size for caching; scale on retrieval. */
        private const val REFERENCE_SIZE = GlyphOutline.REFERENCE_SIZE

        /**
         * Flatness tolerance for Path.approximate().
         * Controls the maximum deviation of flattened segments from the original curves.
         * Lower = more accurate but more points. 0.5f is good for reference size 100.
         */
        private const val FLATNESS_TOLERANCE = 0.5f
    }
}
