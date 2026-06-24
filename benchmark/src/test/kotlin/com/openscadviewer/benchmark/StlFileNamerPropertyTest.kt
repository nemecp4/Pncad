package com.openscadviewer.benchmark

import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: engine-modularization-and-benchmarks
 *
 * Property-based tests for StlFileNamer using jqwik.
 * Validates: Requirements 5.2
 */
class StlFileNamerPropertyTest {

    /**
     * Feature: engine-modularization-and-benchmarks, Property 8: STL filename sanitization
     *
     * For any category string and test case name, the generated STL filename must:
     * - Be lowercase
     * - Contain only [a-z0-9_.] characters
     * - Have no consecutive underscores
     * - End with .stl
     *
     * Validates: Requirements 5.2
     */
    @Property(tries = 100)
    @Tag("property-8-stl-filename-sanitization")
    fun outputIsAlwaysLowercase(
        @ForAll("categories") category: String,
        @ForAll("testNames") testName: String
    ) {
        val filename = StlFileNamer.generateFilename(category, testName)
        assertEquals(filename, filename.lowercase(), "Filename must be lowercase: $filename")
    }

    @Property(tries = 100)
    @Tag("property-8-stl-filename-sanitization")
    fun outputContainsOnlyValidCharacters(
        @ForAll("categories") category: String,
        @ForAll("testNames") testName: String
    ) {
        val filename = StlFileNamer.generateFilename(category, testName)
        assertTrue(
            filename.matches(Regex("[a-z0-9_.]*")),
            "Filename contains invalid characters: '$filename'"
        )
    }

    @Property(tries = 100)
    @Tag("property-8-stl-filename-sanitization")
    fun outputHasNoConsecutiveUnderscores(
        @ForAll("categories") category: String,
        @ForAll("testNames") testName: String
    ) {
        val filename = StlFileNamer.generateFilename(category, testName)
        assertFalse(
            filename.contains("__"),
            "Filename must not contain consecutive underscores: '$filename'"
        )
    }

    @Property(tries = 100)
    @Tag("property-8-stl-filename-sanitization")
    fun outputAlwaysEndsWithStl(
        @ForAll("categories") category: String,
        @ForAll("testNames") testName: String
    ) {
        val filename = StlFileNamer.generateFilename(category, testName)
        assertTrue(
            filename.endsWith(".stl"),
            "Filename must end with .stl: '$filename'"
        )
    }

    // --- Providers ---

    @Provide
    fun categories(): Arbitrary<String> {
        return Arbitraries.oneOf(
            // Realistic category names
            Arbitraries.of(
                "geometry_primitives", "transformations", "linear_extrusion",
                "csg_operations", "combined_operations", "variables_expressions", "edge_cases"
            ),
            // Arbitrary strings with mixed characters to stress test sanitization
            Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_', '.', '!', '@', '#')
                .ofMinLength(1)
                .ofMaxLength(30)
        )
    }

    @Provide
    fun testNames(): Arbitrary<String> {
        return Arbitraries.oneOf(
            // Realistic test names
            Arbitraries.of(
                "prim_cube_centered", "transform_rotate_xyz", "csg_union_three"
            ),
            // Arbitrary strings with mixed characters
            Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_', '.', '!', '@', '#', '(', ')')
                .ofMinLength(1)
                .ofMaxLength(40)
        )
    }
}
