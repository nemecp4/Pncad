package com.openscadviewer.benchmark

import net.jqwik.api.*
import net.jqwik.api.constraints.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import kotlin.math.abs

/**
 * Feature: stl-comparison-tests
 *
 * Property-based tests for StlComparator.ComparisonResult tolerance logic using jqwik.
 * Validates: Requirements 2.4, 2.5, 3.3, 3.4, 3.5, 6.3, 6.4
 */
class StlComparatorPropertyTest {

    /**
     * Property 4: Percent difference formula correctness
     *
     * For any ComparisonResult with positive expectedTriangles and positive expectedFileSize:
     * - trianglePercentDiff == (generatedTriangles - expectedTriangles) / expectedTriangles * 100
     * - fileSizePercentDiff == (generatedFileSize - expectedFileSize) / expectedFileSize * 100
     *
     * Validates: Requirements 2.4, 2.5
     */
    @Property(tries = 100)
    @Tag("property-4-percent-difference-formula")
    fun percentDifferenceFormulaIsCorrectForTriangles(
        @ForAll("positiveInts") expectedTriangles: Int,
        @ForAll("nonNegativeInts") generatedTriangles: Int
    ) {
        val result = StlComparator.ComparisonResult(
            testName = "test",
            generatedTriangles = generatedTriangles,
            expectedTriangles = expectedTriangles,
            generatedFileSize = 1000L,
            expectedFileSize = 1000L,
            trianglesMatch = true,
            fileSizeMatch = true
        )

        val expected = (generatedTriangles - expectedTriangles).toDouble() / expectedTriangles * 100.0
        assertEquals(expected, result.trianglePercentDiff, 1e-9,
            "trianglePercentDiff should equal (gen - exp) / exp * 100")
    }

    @Property(tries = 100)
    @Tag("property-4-percent-difference-formula")
    fun percentDifferenceFormulaIsCorrectForFileSize(
        @ForAll("positiveLongs") expectedFileSize: Long,
        @ForAll("nonNegativeLongs") generatedFileSize: Long
    ) {
        val result = StlComparator.ComparisonResult(
            testName = "test",
            generatedTriangles = 100,
            expectedTriangles = 100,
            generatedFileSize = generatedFileSize,
            expectedFileSize = expectedFileSize,
            trianglesMatch = true,
            fileSizeMatch = true
        )

        val expected = (generatedFileSize - expectedFileSize).toDouble() / expectedFileSize * 100.0
        assertEquals(expected, result.fileSizePercentDiff, 1e-9,
            "fileSizePercentDiff should equal (gen - exp) / exp * 100")
    }

    /**
     * Property 5: Zero-expected values never cause false failure
     *
     * For any ComparisonResult where expectedTriangles == 0, withinTolerance() should pass
     * for the triangle metric regardless of generatedTriangles.
     * For any ComparisonResult where expectedFileSize == 0L, withinTolerance() should pass
     * for the file size metric regardless of generatedFileSize.
     *
     * Validates: Requirements 3.3, 3.4, 6.3, 6.4
     */
    @Property(tries = 100)
    @Tag("property-5-zero-expected-no-false-failure")
    fun zeroExpectedTrianglesNeverCausesFailure(
        @ForAll("nonNegativeInts") generatedTriangles: Int,
        @ForAll("tolerances") tolerance: Double
    ) {
        val result = StlComparator.ComparisonResult(
            testName = "test",
            generatedTriangles = generatedTriangles,
            expectedTriangles = 0,
            generatedFileSize = 100L,
            expectedFileSize = 100L,
            trianglesMatch = true,
            fileSizeMatch = true
        )

        // With expectedTriangles == 0 and file size matching (same values),
        // withinTolerance should always pass
        assertTrue(result.withinTolerance(tolerance),
            "withinTolerance should pass when expectedTriangles == 0, " +
            "generatedTriangles=$generatedTriangles, tolerance=$tolerance")
    }

    @Property(tries = 100)
    @Tag("property-5-zero-expected-no-false-failure")
    fun zeroExpectedFileSizeNeverCausesFailure(
        @ForAll("nonNegativeLongs") generatedFileSize: Long,
        @ForAll("tolerances") tolerance: Double
    ) {
        val result = StlComparator.ComparisonResult(
            testName = "test",
            generatedTriangles = 100,
            expectedTriangles = 100,
            generatedFileSize = generatedFileSize,
            expectedFileSize = 0L,
            trianglesMatch = true,
            fileSizeMatch = true
        )

        // With expectedFileSize == 0 and triangles matching (same values),
        // withinTolerance should always pass
        assertTrue(result.withinTolerance(tolerance),
            "withinTolerance should pass when expectedFileSize == 0, " +
            "generatedFileSize=$generatedFileSize, tolerance=$tolerance")
    }

    /**
     * Property 6: Tolerance boundary is inclusive
     *
     * For any positive expected value, if the generated value produces an absolute percent
     * difference of exactly tolerance * 100, withinTolerance() should return true.
     *
     * Validates: Requirements 3.5
     */
    @Property(tries = 100)
    @Tag("property-6-tolerance-boundary-inclusive")
    fun toleranceBoundaryIsInclusiveForTrianglesAbove(
        @ForAll("positiveInts") expectedTriangles: Int,
        @ForAll("tolerances") tolerance: Double
    ) {
        // generated = expected * (1 + tolerance) should be exactly at boundary
        val generatedTriangles = (expectedTriangles * (1.0 + tolerance)).toInt()

        // Recompute the actual percent diff that will be calculated
        val actualPercentDiff = abs((generatedTriangles - expectedTriangles).toDouble() / expectedTriangles * 100.0)

        // Only test if rounding didn't push us over the boundary
        if (actualPercentDiff <= tolerance * 100.0) {
            val result = StlComparator.ComparisonResult(
                testName = "test",
                generatedTriangles = generatedTriangles,
                expectedTriangles = expectedTriangles,
                generatedFileSize = 100L,
                expectedFileSize = 100L,
                trianglesMatch = true,
                fileSizeMatch = true
            )

            assertTrue(result.withinTolerance(tolerance),
                "withinTolerance should be true at boundary: " +
                "expected=$expectedTriangles, generated=$generatedTriangles, " +
                "percentDiff=$actualPercentDiff, tolerance=${tolerance * 100}%")
        }
    }

    @Property(tries = 100)
    @Tag("property-6-tolerance-boundary-inclusive")
    fun toleranceBoundaryIsInclusiveForTrianglesBelow(
        @ForAll("positiveInts") expectedTriangles: Int,
        @ForAll("tolerances") tolerance: Double
    ) {
        // generated = expected * (1 - tolerance) should be exactly at boundary
        val generatedTriangles = (expectedTriangles * (1.0 - tolerance)).toInt()

        // Recompute the actual percent diff that will be calculated
        val actualPercentDiff = abs((generatedTriangles - expectedTriangles).toDouble() / expectedTriangles * 100.0)

        // Only test if rounding didn't push us over the boundary
        if (actualPercentDiff <= tolerance * 100.0) {
            val result = StlComparator.ComparisonResult(
                testName = "test",
                generatedTriangles = generatedTriangles,
                expectedTriangles = expectedTriangles,
                generatedFileSize = 100L,
                expectedFileSize = 100L,
                trianglesMatch = true,
                fileSizeMatch = true
            )

            assertTrue(result.withinTolerance(tolerance),
                "withinTolerance should be true at lower boundary: " +
                "expected=$expectedTriangles, generated=$generatedTriangles, " +
                "percentDiff=$actualPercentDiff, tolerance=${tolerance * 100}%")
        }
    }

    @Property(tries = 100)
    @Tag("property-6-tolerance-boundary-inclusive")
    fun toleranceBoundaryIsInclusiveForFileSizeAbove(
        @ForAll("positiveLongs") expectedFileSize: Long,
        @ForAll("tolerances") tolerance: Double
    ) {
        // generated = expected * (1 + tolerance) should be exactly at boundary
        val generatedFileSize = (expectedFileSize * (1.0 + tolerance)).toLong()

        // Recompute the actual percent diff that will be calculated
        val actualPercentDiff = abs((generatedFileSize - expectedFileSize).toDouble() / expectedFileSize * 100.0)

        // Only test if rounding didn't push us over the boundary
        if (actualPercentDiff <= tolerance * 100.0) {
            val result = StlComparator.ComparisonResult(
                testName = "test",
                generatedTriangles = 100,
                expectedTriangles = 100,
                generatedFileSize = generatedFileSize,
                expectedFileSize = expectedFileSize,
                trianglesMatch = true,
                fileSizeMatch = true
            )

            assertTrue(result.withinTolerance(tolerance),
                "withinTolerance should be true at file size upper boundary: " +
                "expected=$expectedFileSize, generated=$generatedFileSize, " +
                "percentDiff=$actualPercentDiff, tolerance=${tolerance * 100}%")
        }
    }

    @Property(tries = 100)
    @Tag("property-6-tolerance-boundary-inclusive")
    fun toleranceBoundaryIsInclusiveForFileSizeBelow(
        @ForAll("positiveLongs") expectedFileSize: Long,
        @ForAll("tolerances") tolerance: Double
    ) {
        // generated = expected * (1 - tolerance) should be exactly at boundary
        val generatedFileSize = (expectedFileSize * (1.0 - tolerance)).toLong()

        // Recompute the actual percent diff that will be calculated
        val actualPercentDiff = abs((generatedFileSize - expectedFileSize).toDouble() / expectedFileSize * 100.0)

        // Only test if rounding didn't push us over the boundary
        if (actualPercentDiff <= tolerance * 100.0) {
            val result = StlComparator.ComparisonResult(
                testName = "test",
                generatedTriangles = 100,
                expectedTriangles = 100,
                generatedFileSize = generatedFileSize,
                expectedFileSize = expectedFileSize,
                trianglesMatch = true,
                fileSizeMatch = true
            )

            assertTrue(result.withinTolerance(tolerance),
                "withinTolerance should be true at file size lower boundary: " +
                "expected=$expectedFileSize, generated=$generatedFileSize, " +
                "percentDiff=$actualPercentDiff, tolerance=${tolerance * 100}%")
        }
    }

    /**
     * Property 3: Triangle count reading correctness
     *
     * For any non-negative integer N (in range 0..1_000_000):
     * - Construct a byte array of exactly 84 bytes: 80 bytes of zeros (header) + 4 bytes of N in little-endian format
     * - Write to a temporary file
     * - Call StlComparator.readTriangleCount(tempFile)
     * - Assert the return value equals N
     *
     * Validates: Requirements 1.1
     */
    @Property(tries = 100)
    @Tag("property-3-triangle-count-reading")
    fun triangleCountReadCorrectly(@ForAll("triangleCounts") count: Int) {
        val bytes = ByteArray(84)
        // Write count as little-endian at offset 80
        bytes[80] = (count and 0xFF).toByte()
        bytes[81] = ((count shr 8) and 0xFF).toByte()
        bytes[82] = ((count shr 16) and 0xFF).toByte()
        bytes[83] = ((count shr 24) and 0xFF).toByte()

        val tempFile = File.createTempFile("stl_test_", ".stl")
        try {
            tempFile.writeBytes(bytes)
            val result = StlComparator.readTriangleCount(tempFile)
            assertEquals(count, result, "Triangle count mismatch for input $count")
        } finally {
            tempFile.delete()
        }
    }

    // --- Providers ---

    @Provide
    fun triangleCounts(): Arbitrary<Int> = Arbitraries.integers().between(0, 1_000_000)

    @Provide
    fun positiveInts(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 100_000)
    }

    @Provide
    fun nonNegativeInts(): Arbitrary<Int> {
        return Arbitraries.integers().between(0, 200_000)
    }

    @Provide
    fun positiveLongs(): Arbitrary<Long> {
        return Arbitraries.longs().between(1L, 10_000_000L)
    }

    @Provide
    fun nonNegativeLongs(): Arbitrary<Long> {
        return Arbitraries.longs().between(0L, 20_000_000L)
    }

    @Provide
    fun tolerances(): Arbitrary<Double> {
        return Arbitraries.doubles().between(0.01, 0.99)
    }
}
