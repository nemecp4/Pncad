package com.openscadviewer.settings

import net.jqwik.api.*
import net.jqwik.api.Arbitrary
import org.junit.jupiter.api.Assertions.*

// Feature: preview-controls-and-settings, Property 4: Invalid preference values fall back to defaults

/**
 * Property-based tests verifying that invalid preference values fall back to defaults.
 *
 * **Validates: Requirements 6.3**
 */
class BackgroundColorFallbackPropertyTest {

    private val validKeys = setOf("dark_grey", "white", "yellow")
    private val defaultColor = floatArrayOf(0.18f, 0.18f, 0.18f, 1.0f)

    /**
     * Property 4: Invalid preference values fall back to defaults
     *
     * For any string that is not in the valid set {"dark_grey", "white", "yellow"},
     * the mapBackgroundColor function shall return the default Dark Grey color
     * (0.18, 0.18, 0.18, 1.0).
     *
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: preview-controls-and-settings, Property 4: Invalid preference values fall back to defaults")
    fun invalidPreferenceValuesFallBackToDefaults(
        @ForAll("invalidColorKeys") key: String
    ) {
        val result = mapBackgroundColor(key)

        assertArrayEquals(defaultColor, result, 0.001f,
            "mapBackgroundColor(\"$key\") should return default Dark Grey RGBA but got ${result.toList()}")
    }

    // --- Custom Generators ---

    @Provide
    fun invalidColorKeys(): Arbitrary<String> {
        return Arbitraries.strings()
            .ofMinLength(0)
            .ofMaxLength(50)
            .filter { it !in validKeys }
    }
}
