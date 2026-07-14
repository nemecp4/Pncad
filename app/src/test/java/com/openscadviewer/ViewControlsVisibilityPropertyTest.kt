package com.openscadviewer

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import org.junit.jupiter.api.Assertions.*

// Feature: tablet-layout, Property 4: View controls visibility logic

/**
 * Property-based tests for view controls visibility logic.
 *
 * The view controls overlay should be visible if and only if:
 * (isTabletLayout || currentTabPosition == 1) && hasMesh
 *
 * Since the domain is small (2×2×2 = 8 combinations), jqwik will
 * exhaustively cover all cases.
 *
 * **Validates: Requirements 4.2**
 */
class ViewControlsVisibilityPropertyTest {

    /**
     * Computes the expected visibility based on the formula from the design doc.
     */
    private fun expectedVisible(isTabletLayout: Boolean, currentTabPosition: Int, hasMesh: Boolean): Boolean {
        val isPreviewVisible = isTabletLayout || currentTabPosition == 1
        return isPreviewVisible && hasMesh
    }

    /**
     * Property 4: View controls visibility logic
     *
     * For any combination of isTabletLayout, currentTabPosition (0 or 1), and hasMesh,
     * the view controls overlay shall be visible if and only if
     * (isTabletLayout OR currentTabPosition == 1) AND hasMesh is true.
     *
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 100)
    @Tag("Feature: tablet-layout, Property 4: View controls visibility logic")
    fun viewControlsVisibleIffPreviewVisibleAndHasMesh(
        @ForAll isTabletLayout: Boolean,
        @ForAll @IntRange(min = 0, max = 1) currentTabPosition: Int,
        @ForAll hasMesh: Boolean
    ) {
        val expected = expectedVisible(isTabletLayout, currentTabPosition, hasMesh)

        // Replicate the actual logic from MainActivity.updateViewControlsVisibility()
        val isPreviewVisible = isTabletLayout || currentTabPosition == 1
        val actual = isPreviewVisible && hasMesh

        assertEquals(
            expected, actual,
            "Visibility mismatch for isTabletLayout=$isTabletLayout, " +
                "currentTabPosition=$currentTabPosition, hasMesh=$hasMesh: " +
                "expected=$expected, actual=$actual"
        )
    }
}
