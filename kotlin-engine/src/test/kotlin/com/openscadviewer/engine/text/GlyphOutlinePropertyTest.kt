package com.openscadviewer.engine.text

import net.jqwik.api.*
import net.jqwik.api.lifecycle.BeforeProperty
import kotlin.math.abs

/**
 * Property 6: All flattened contours are closed polygons
 *
 * For any visible printable ASCII character (codes 33-126), extracting glyph outlines
 * via AwtFontProvider SHALL produce contours where each contour has its first point
 * approximately equal to its last point (closed path) and contains at least 3 points.
 *
 * **Validates: Requirements 3.1, 3.3**
 */
class GlyphOutlinePropertyTest {

    private lateinit var fontProvider: AwtFontProvider

    @BeforeProperty
    fun setUp() {
        fontProvider = AwtFontProvider()
    }

    /**
     * Property 6: All flattened contours are closed polygons.
     *
     * For each visible printable ASCII character, every contour in the glyph outline
     * must be a closed polygon with at least 3 points, where the first and last
     * points are within a tolerance of 0.01.
     */
    @Property(tries = 94) // 94 visible printable ASCII chars (33-126)
    fun allContoursAreClosedPolygons(
        @ForAll("visibleAsciiChars") char: Char
    ) {
        val outline = fontProvider.getGlyphOutline(char, "Liberation Sans", 100f)

        assert(outline != null) {
            "AwtFontProvider should return a non-null outline for char '$char' (code ${char.code})"
        }

        // Skip characters that produce no contours (e.g., space-like chars)
        if (outline!!.contours.isEmpty()) return

        for ((index, contour) in outline.contours.withIndex()) {
            // Each contour must have at least 3 points
            assert(contour.size >= 3) {
                "Contour $index for char '$char' (code ${char.code}) has only ${contour.size} points, expected >= 3"
            }

            // First point must approximately equal last point (closed polygon)
            val first = contour.first()
            val last = contour.last()
            val dx = abs(first.first - last.first)
            val dy = abs(first.second - last.second)

            assert(dx <= 0.01f && dy <= 0.01f) {
                "Contour $index for char '$char' (code ${char.code}) is not closed: " +
                    "first=(${first.first}, ${first.second}), last=(${last.first}, ${last.second}), " +
                    "dx=$dx, dy=$dy (tolerance=0.01)"
            }
        }
    }

    // --- Providers ---

    @Provide
    fun visibleAsciiChars(): Arbitrary<Char> {
        return Arbitraries.chars().range(33.toChar(), 126.toChar())
    }
}
