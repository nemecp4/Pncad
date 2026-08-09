package com.openscadviewer.parser

import net.jqwik.api.*
import net.jqwik.api.constraints.DoubleRange
import net.jqwik.api.constraints.StringLength
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: text-rendering
 *
 * Property-based tests for containsGeometry() correctness with SceneNode.Text nodes.
 * Validates: Requirements 7.4, 7.5
 */
class TextSceneNodePropertyTest {

    /**
     * Feature: text-rendering, Property 10: containsGeometry() correctness for Text nodes
     *
     * For any SceneNode.Text with non-empty text and positive size,
     * containsGeometry() shall return true.
     *
     * Validates: Requirements 7.4, 7.5
     */
    @Property(tries = 200)
    @Tag("property-10-containsGeometry-text")
    fun nonEmptyTextWithPositiveSizeContainsGeometry(
        @ForAll("nonEmptyText") text: String,
        @ForAll("positiveSize") size: Double
    ) {
        val node = SceneNode.Text(text = text, size = size)
        assertTrue(
            node.containsGeometry(),
            "Expected containsGeometry()=true for text='$text', size=$size"
        )
    }

    /**
     * Feature: text-rendering, Property 10: containsGeometry() correctness for Text nodes
     *
     * For any SceneNode.Text with empty text, containsGeometry() shall return false
     * regardless of size.
     *
     * Validates: Requirements 7.4, 7.5
     */
    @Property(tries = 200)
    @Tag("property-10-containsGeometry-text")
    fun emptyTextDoesNotContainGeometry(
        @ForAll("anySize") size: Double
    ) {
        val node = SceneNode.Text(text = "", size = size)
        assertFalse(
            node.containsGeometry(),
            "Expected containsGeometry()=false for empty text, size=$size"
        )
    }

    /**
     * Feature: text-rendering, Property 10: containsGeometry() correctness for Text nodes
     *
     * For any SceneNode.Text with non-positive size, containsGeometry() shall return false
     * regardless of text content.
     *
     * Validates: Requirements 7.4, 7.5
     */
    @Property(tries = 200)
    @Tag("property-10-containsGeometry-text")
    fun nonPositiveSizeDoesNotContainGeometry(
        @ForAll("anyText") text: String,
        @ForAll("nonPositiveSize") size: Double
    ) {
        val node = SceneNode.Text(text = text, size = size)
        assertFalse(
            node.containsGeometry(),
            "Expected containsGeometry()=false for text='$text', size=$size"
        )
    }

    // --- Providers ---

    @Provide
    fun nonEmptyText(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange(' ', '~') // printable ASCII
            .ofMinLength(1)
            .ofMaxLength(50)
    }

    @Provide
    fun positiveSize(): Arbitrary<Double> {
        return Arbitraries.doubles().between(0.01, 1000.0)
    }

    @Provide
    fun nonPositiveSize(): Arbitrary<Double> {
        return Arbitraries.doubles().between(-1000.0, 0.0)
    }

    @Provide
    fun anySize(): Arbitrary<Double> {
        return Arbitraries.doubles().between(-1000.0, 1000.0)
    }

    @Provide
    fun anyText(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange(' ', '~')
            .ofMinLength(0)
            .ofMaxLength(50)
    }
}
