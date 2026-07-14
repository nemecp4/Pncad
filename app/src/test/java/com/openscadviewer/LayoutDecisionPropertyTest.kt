package com.openscadviewer

import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*

// Feature: tablet-layout, Property 1: Layout fallback decision correctness

/**
 * Property-based test for the layout fallback decision function.
 *
 * For any available screen width (0–2000dp) and divider width (0–10dp),
 * shouldFallbackToSinglePane returns true if and only if the computed pane width
 * (availableWidth - dividerWidth) / 2 is less than 200dp.
 *
 * Validates: Requirements 1.5, 2.5
 */
class LayoutDecisionPropertyTest {

    @Property(tries = 100)
    fun fallbackDecisionIsCorrectForAllInputs(
        @ForAll("availableWidths") availableWidthDp: Float,
        @ForAll("dividerWidths") dividerWidthDp: Float
    ) {
        val result = shouldFallbackToSinglePane(availableWidthDp, dividerWidthDp)
        val expectedPaneWidth = (availableWidthDp - dividerWidthDp) / 2f
        val expected = expectedPaneWidth < 200f

        assertEquals(
            expected,
            result,
            "shouldFallbackToSinglePane($availableWidthDp, $dividerWidthDp) returned $result " +
                "but pane width is $expectedPaneWidth (expected fallback=$expected)"
        )
    }

    @Provide
    fun availableWidths(): Arbitrary<Float> {
        return Arbitraries.floats().between(0.0f, 2000.0f)
    }

    @Provide
    fun dividerWidths(): Arbitrary<Float> {
        return Arbitraries.floats().between(0.0f, 10.0f)
    }
}
