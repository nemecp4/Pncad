package com.openscadviewer.settings

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for the mapBackgroundColor utility function.
 *
 * Requirements: 5.2, 5.3, 5.4, 6.3
 */
class MapBackgroundColorTest {

    private val tolerance = 0.001f

    // --- Requirement 5.2: Dark Grey preset ---

    @Test
    fun `mapBackgroundColor dark_grey returns RGBA 0_18 0_18 0_18 1_0`() {
        val result = mapBackgroundColor("dark_grey")

        assertArrayEquals(
            floatArrayOf(0.18f, 0.18f, 0.18f, 1.0f),
            result,
            tolerance,
            "dark_grey should map to RGBA (0.18, 0.18, 0.18, 1.0)"
        )
    }

    // --- Requirement 5.3: White preset ---

    @Test
    fun `mapBackgroundColor white returns RGBA 1_0 1_0 1_0 1_0`() {
        val result = mapBackgroundColor("white")

        assertArrayEquals(
            floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f),
            result,
            tolerance,
            "white should map to RGBA (1.0, 1.0, 1.0, 1.0)"
        )
    }

    // --- Requirement 5.4: Yellow preset ---

    @Test
    fun `mapBackgroundColor yellow returns RGBA 1_0 1_0 0_5 1_0`() {
        val result = mapBackgroundColor("yellow")

        assertArrayEquals(
            floatArrayOf(1.0f, 1.0f, 0.5f, 1.0f),
            result,
            tolerance,
            "yellow should map to RGBA (1.0, 1.0, 0.5, 1.0)"
        )
    }

    // --- Requirement 6.3: Unknown string falls back to default Dark Grey ---

    @Test
    fun `mapBackgroundColor unknown string returns default Dark Grey`() {
        val result = mapBackgroundColor("unknown_color")

        assertArrayEquals(
            floatArrayOf(0.18f, 0.18f, 0.18f, 1.0f),
            result,
            tolerance,
            "Unknown key should fall back to default Dark Grey RGBA (0.18, 0.18, 0.18, 1.0)"
        )
    }
}
