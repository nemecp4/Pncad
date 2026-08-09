package com.openscadviewer.engine.text

import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: text-rendering
 *
 * Property-based tests for TextLayoutEngine.
 *
 * Tests Properties 1-5 covering text layout positioning, width calculation,
 * alignment offsets, RTL direction, and spacing linearity.
 */
class TextLayoutPropertyTest {

    /**
     * Mock FontProvider that returns GlyphOutlines with configurable advance widths.
     * Each character maps to a deterministic advance width based on the provided map.
     */
    private class MockFontProvider(
        private val advanceWidths: Map<Char, Float>,
        private val defaultAdvanceWidth: Float = 10f,
        private val ascent: Float = 80f,
        private val descent: Float = -20f
    ) : FontProvider {

        override fun getGlyphOutline(char: Char, fontName: String, size: Float): GlyphOutline {
            val advance = advanceWidths.getOrDefault(char, defaultAdvanceWidth)
            // Simple rectangular contour for testing
            val contour = listOf(
                Pair(0f, 0f),
                Pair(advance * 0.8f, 0f),
                Pair(advance * 0.8f, ascent),
                Pair(0f, ascent),
                Pair(0f, 0f)
            )
            return GlyphOutline(
                contours = listOf(contour),
                advanceWidth = advance,
                ascent = ascent,
                descent = descent
            )
        }

        override fun getFontMetrics(fontName: String, size: Float): FontMetrics {
            return FontMetrics(
                ascent = ascent,
                descent = descent,
                lineHeight = ascent - descent
            )
        }

        override fun isFontAvailable(fontName: String): Boolean = true
    }

    // =========================================================================
    // Property 1: Text layout positions are monotonically increasing (LTR)
    // =========================================================================

    /**
     * Feature: text-rendering, Property 1: Text layout positions are monotonically increasing (LTR)
     *
     * For any non-empty text string with spacing > 0 and direction="ltr",
     * the X positions of laid-out glyphs shall be strictly non-decreasing.
     *
     * Validates: Requirements 4.1, 4.4
     */
    @Property(tries = 200)
    @Tag("property-1-monotonic-ltr-positions")
    fun ltrPositionsAreMonotonicallyNonDecreasing(
        @ForAll("asciiStrings") text: String,
        @ForAll("positiveSpacings") spacing: Float,
        @ForAll("positiveAdvanceWidthMaps") advanceWidths: Map<Char, Float>
    ) {
        val fontProvider = MockFontProvider(advanceWidths)

        val result = TextLayoutEngine.layout(
            text = text,
            size = 10f,
            halign = "left",
            valign = "baseline",
            spacing = spacing,
            direction = "ltr",
            fontProvider = fontProvider,
            fontName = "test"
        )

        assertTrue(result.isNotEmpty(), "Layout should produce glyphs for non-empty text")

        for (i in 1 until result.size) {
            assertTrue(
                result[i].x >= result[i - 1].x,
                "LTR positions should be non-decreasing: position[$i]=${result[i].x} < position[${i - 1}]=${result[i - 1].x} " +
                    "for text='$text', spacing=$spacing"
            )
        }
    }

    // =========================================================================
    // Property 2: Total text width equals sum of advance widths times spacing
    // =========================================================================

    /**
     * Feature: text-rendering, Property 2: Total text width equals sum of advance widths times spacing
     *
     * For any text string and spacing value, the total width of the laid-out text
     * (last character X + last advanceWidth * spacing) shall equal the sum of all
     * characters' advance widths multiplied by spacing.
     *
     * Validates: Requirements 4.1, 4.5
     */
    @Property(tries = 200)
    @Tag("property-2-total-width-equals-sum")
    fun totalWidthEqualsSumOfAdvanceWidthsTimesSpacing(
        @ForAll("asciiStrings") text: String,
        @ForAll("positiveSpacings") spacing: Float,
        @ForAll("positiveAdvanceWidthMaps") advanceWidths: Map<Char, Float>
    ) {
        val fontProvider = MockFontProvider(advanceWidths)

        val result = TextLayoutEngine.layout(
            text = text,
            size = 10f,
            halign = "left",
            valign = "baseline",
            spacing = spacing,
            direction = "ltr",
            fontProvider = fontProvider,
            fontName = "test"
        )

        assertTrue(result.isNotEmpty(), "Layout should produce glyphs for non-empty text")

        // Expected total width = sum of all advanceWidth * spacing
        val expectedTotalWidth = text.sumOf { ch ->
            (advanceWidths.getOrDefault(ch, 10f) * spacing).toDouble()
        }.toFloat()

        // Actual total width = last glyph X + last advanceWidth * spacing
        val lastGlyph = result.last()
        val lastAdvance = lastGlyph.outline.advanceWidth
        val actualTotalWidth = lastGlyph.x + lastAdvance * spacing

        assertEquals(
            expectedTotalWidth.toDouble(),
            actualTotalWidth.toDouble(),
            0.01,
            "Total width should equal sum of advance widths * spacing. " +
                "Expected=$expectedTotalWidth, actual=$actualTotalWidth, text='$text', spacing=$spacing"
        )
    }

    // =========================================================================
    // Property 3: Horizontal alignment offsets are consistent
    // =========================================================================

    /**
     * Feature: text-rendering, Property 3: Horizontal alignment offsets are consistent
     *
     * For halign="left", the first glyph starts at X>=0.
     * For halign="center", the midpoint of the text is at X≈0.
     * For halign="right", the rightmost extent (last glyph + advance) ends at X≈0.
     *
     * Validates: Requirements 4.2
     */
    @Property(tries = 200)
    @Tag("property-3-alignment-offsets")
    fun leftAlignmentStartsAtZeroOrPositive(
        @ForAll("asciiStrings") text: String,
        @ForAll("positiveSpacings") spacing: Float,
        @ForAll("positiveAdvanceWidthMaps") advanceWidths: Map<Char, Float>
    ) {
        val fontProvider = MockFontProvider(advanceWidths)

        val result = TextLayoutEngine.layout(
            text = text,
            size = 10f,
            halign = "left",
            valign = "baseline",
            spacing = spacing,
            direction = "ltr",
            fontProvider = fontProvider,
            fontName = "test"
        )

        assertTrue(result.isNotEmpty(), "Layout should produce glyphs")

        // For left alignment, first glyph X should be 0
        assertEquals(
            0f, result.first().x, 0.01f,
            "Left-aligned text should start at X=0, got X=${result.first().x}"
        )
    }

    @Property(tries = 200)
    @Tag("property-3-alignment-offsets")
    fun centerAlignmentMidpointAtZero(
        @ForAll("asciiStrings") text: String,
        @ForAll("positiveSpacings") spacing: Float,
        @ForAll("positiveAdvanceWidthMaps") advanceWidths: Map<Char, Float>
    ) {
        val fontProvider = MockFontProvider(advanceWidths)

        val result = TextLayoutEngine.layout(
            text = text,
            size = 10f,
            halign = "center",
            valign = "baseline",
            spacing = spacing,
            direction = "ltr",
            fontProvider = fontProvider,
            fontName = "test"
        )

        assertTrue(result.isNotEmpty(), "Layout should produce glyphs")

        // For center alignment, midpoint of text should be at X≈0
        val minX = result.first().x
        val lastGlyph = result.last()
        val maxX = lastGlyph.x + lastGlyph.outline.advanceWidth * spacing
        val midpoint = (minX + maxX) / 2f

        assertEquals(
            0.0, midpoint.toDouble(), 0.01,
            "Center-aligned text midpoint should be at X≈0, got midpoint=$midpoint " +
                "(minX=$minX, maxX=$maxX)"
        )
    }

    @Property(tries = 200)
    @Tag("property-3-alignment-offsets")
    fun rightAlignmentEndsAtZero(
        @ForAll("asciiStrings") text: String,
        @ForAll("positiveSpacings") spacing: Float,
        @ForAll("positiveAdvanceWidthMaps") advanceWidths: Map<Char, Float>
    ) {
        val fontProvider = MockFontProvider(advanceWidths)

        val result = TextLayoutEngine.layout(
            text = text,
            size = 10f,
            halign = "right",
            valign = "baseline",
            spacing = spacing,
            direction = "ltr",
            fontProvider = fontProvider,
            fontName = "test"
        )

        assertTrue(result.isNotEmpty(), "Layout should produce glyphs")

        // For right alignment, rightmost extent should end at X≈0
        val lastGlyph = result.last()
        val rightExtent = lastGlyph.x + lastGlyph.outline.advanceWidth * spacing

        assertEquals(
            0.0, rightExtent.toDouble(), 0.01,
            "Right-aligned text should end at X≈0, got rightExtent=$rightExtent"
        )
    }

    // =========================================================================
    // Property 4: RTL direction reverses character order
    // =========================================================================

    /**
     * Feature: text-rendering, Property 4: RTL direction reverses character order
     *
     * For any text laid out with direction="rtl" and spacing >= 1.0, the X positions
     * of glyphs shall be strictly non-increasing (each successive character's X position
     * is less than or equal to the previous).
     *
     * Note: With spacing >= 1, the right-edge of each character in LTR is non-decreasing,
     * so RTL mirroring (-(x + advanceWidth)) produces non-increasing positions.
     *
     * Validates: Requirements 4.4
     */
    @Property(tries = 200)
    @Tag("property-4-rtl-reversal")
    fun rtlPositionsAreNonIncreasing(
        @ForAll("asciiStrings") text: String,
        @ForAll("spacingsAtLeastOne") spacing: Float,
        @ForAll("positiveAdvanceWidthMaps") advanceWidths: Map<Char, Float>
    ) {
        val fontProvider = MockFontProvider(advanceWidths)

        val result = TextLayoutEngine.layout(
            text = text,
            size = 10f,
            halign = "left",
            valign = "baseline",
            spacing = spacing,
            direction = "rtl",
            fontProvider = fontProvider,
            fontName = "test"
        )

        assertTrue(result.isNotEmpty(), "Layout should produce glyphs for non-empty text")

        for (i in 1 until result.size) {
            assertTrue(
                result[i].x <= result[i - 1].x,
                "RTL positions should be non-increasing: position[$i]=${result[i].x} > position[${i - 1}]=${result[i - 1].x} " +
                    "for text='$text', spacing=$spacing"
            )
        }
    }

    @Property(tries = 200)
    @Tag("property-4-rtl-reversal")
    fun rtlAndLtrContainSameCharacters(
        @ForAll("asciiStrings") text: String,
        @ForAll("positiveSpacings") spacing: Float,
        @ForAll("positiveAdvanceWidthMaps") advanceWidths: Map<Char, Float>
    ) {
        val fontProvider = MockFontProvider(advanceWidths)

        val ltrResult = TextLayoutEngine.layout(
            text = text, size = 10f, halign = "left", valign = "baseline",
            spacing = spacing, direction = "ltr", fontProvider = fontProvider, fontName = "test"
        )

        val rtlResult = TextLayoutEngine.layout(
            text = text, size = 10f, halign = "left", valign = "baseline",
            spacing = spacing, direction = "rtl", fontProvider = fontProvider, fontName = "test"
        )

        assertEquals(ltrResult.size, rtlResult.size, "LTR and RTL should produce same number of glyphs")

        val ltrChars = ltrResult.map { it.char }
        val rtlChars = rtlResult.map { it.char }
        assertEquals(ltrChars, rtlChars, "LTR and RTL should contain same characters in same order")
    }

    // =========================================================================
    // Property 5: Spacing multiplier scales inter-character distances linearly
    // =========================================================================

    /**
     * Feature: text-rendering, Property 5: Spacing multiplier scales inter-character distances linearly
     *
     * For any text string, the distance between character N and character N+1 at spacing=S
     * equals S times the distance at spacing=1.0.
     *
     * Validates: Requirements 4.5
     */
    @Property(tries = 200)
    @Tag("property-5-spacing-linearity")
    fun spacingScalesDistancesLinearly(
        @ForAll("multiCharStrings") text: String,
        @ForAll("positiveSpacings") spacing1: Float,
        @ForAll("positiveSpacings") spacing2: Float,
        @ForAll("positiveAdvanceWidthMaps") advanceWidths: Map<Char, Float>
    ) {
        val fontProvider = MockFontProvider(advanceWidths)

        val result1 = TextLayoutEngine.layout(
            text = text, size = 10f, halign = "left", valign = "baseline",
            spacing = spacing1, direction = "ltr", fontProvider = fontProvider, fontName = "test"
        )

        val result2 = TextLayoutEngine.layout(
            text = text, size = 10f, halign = "left", valign = "baseline",
            spacing = spacing2, direction = "ltr", fontProvider = fontProvider, fontName = "test"
        )

        assertTrue(result1.size >= 2, "Need at least 2 glyphs for distance comparison")
        assertEquals(result1.size, result2.size, "Both layouts should have same glyph count")

        // For each pair of adjacent glyphs, distance should scale proportionally
        for (i in 0 until result1.size - 1) {
            val dist1 = result1[i + 1].x - result1[i].x
            val dist2 = result2[i + 1].x - result2[i].x

            // dist2 / dist1 should equal spacing2 / spacing1
            // Equivalent: dist2 * spacing1 ≈ dist1 * spacing2
            val expectedRatio = spacing2 / spacing1
            val actualRatio = if (dist1 != 0f) dist2 / dist1 else expectedRatio

            assertEquals(
                expectedRatio.toDouble(),
                actualRatio.toDouble(),
                0.01,
                "Distance ratio between spacing $spacing2 and $spacing1 should be ${expectedRatio}, " +
                    "got $actualRatio at position $i for text='$text'"
            )
        }
    }

    // =========================================================================
    // Generators
    // =========================================================================

    @Provide
    fun asciiStrings(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('!', '~')  // printable ASCII excluding space
            .ofMinLength(1)
            .ofMaxLength(50)
    }

    @Provide
    fun multiCharStrings(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('!', '~')
            .ofMinLength(2)
            .ofMaxLength(50)
    }

    @Provide
    fun positiveSpacings(): Arbitrary<Float> {
        return Arbitraries.floats().between(0.1f, 5.0f)
    }

    @Provide
    fun spacingsAtLeastOne(): Arbitrary<Float> {
        return Arbitraries.floats().between(1.0f, 5.0f)
    }

    @Provide
    fun positiveAdvanceWidthMaps(): Arbitrary<Map<Char, Float>> {
        // Generate a map of character -> advance width for printable ASCII
        val widthArb = Arbitraries.floats().between(1f, 50f)

        return widthArb.list().ofSize(94).map { widths ->
            val map = mutableMapOf<Char, Float>()
            for (i in widths.indices) {
                map['!' + i] = widths[i]
            }
            map.toMap()
        }
    }
}
