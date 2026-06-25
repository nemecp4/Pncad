package com.openscadviewer.benchmark

import net.jqwik.api.*
import net.jqwik.api.Combinators.combine
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: stl-comparison-tests
 *
 * Property-based tests for StlComparisonAssert using jqwik.
 * Validates: Requirements 3.1, 3.2, 4.1, 4.2, 4.3
 */
class StlComparisonAssertPropertyTest {

    /**
     * Property 1: All within tolerance implies test passes
     *
     * For any list of ComparisonResult entries where every entry satisfies
     * withinTolerance(0.15), calling assertAllWithinTolerance SHALL return
     * normally without throwing an exception.
     *
     * Validates: Requirements 3.1, 4.1
     */
    @Property(tries = 100)
    @Tag("property-1-all-within-tolerance-passes")
    fun allWithinToleranceImpliesTestPasses(
        @ForAll("withinToleranceResults") comparisons: List<StlComparator.ComparisonResult>
    ) {
        // Precondition: all results are within tolerance
        assertTrue(comparisons.all { it.withinTolerance(0.15) },
            "Generator should produce only within-tolerance results")

        // Should NOT throw
        assertDoesNotThrow {
            StlComparisonAssert.assertAllWithinTolerance(comparisons, 0.15)
        }
    }

    /**
     * Property 2: Any exceeding tolerance implies test fails
     *
     * For any list of ComparisonResult entries where at least one entry does
     * not satisfy withinTolerance(0.15), calling assertAllWithinTolerance
     * SHALL throw an AssertionError.
     *
     * Validates: Requirements 3.2, 4.2
     */
    @Property(tries = 100)
    @Tag("property-2-any-exceeding-tolerance-fails")
    fun anyExceedingToleranceImpliesTestFails(
        @ForAll("mixedResultsWithAtLeastOneFailing") comparisons: List<StlComparator.ComparisonResult>
    ) {
        // Precondition: at least one result exceeds tolerance
        assertTrue(comparisons.any { !it.withinTolerance(0.15) },
            "Generator should produce at least one exceeding-tolerance result")

        // Should throw AssertionError
        assertThrows(AssertionError::class.java) {
            StlComparisonAssert.assertAllWithinTolerance(comparisons, 0.15)
        }
    }

    /**
     * Property 7: Assertion error message contains all failing test names
     *
     * For any list of ComparisonResult entries containing multiple entries
     * that exceed tolerance, the AssertionError message SHALL contain the
     * testName of every failing entry.
     *
     * Validates: Requirements 4.3
     */
    @Property(tries = 100)
    @Tag("property-7-error-message-contains-all-failing-names")
    fun assertionErrorMessageContainsAllFailingTestNames(
        @ForAll("multipleFailingResults") comparisons: List<StlComparator.ComparisonResult>
    ) {
        val failingNames = comparisons.filter { !it.withinTolerance(0.15) }.map { it.testName }
        assertTrue(failingNames.size >= 2,
            "Generator should produce at least 2 failing results")

        val error = assertThrows(AssertionError::class.java) {
            StlComparisonAssert.assertAllWithinTolerance(comparisons, 0.15)
        }

        val message = error.message ?: ""
        for (name in failingNames) {
            assertTrue(
                message.contains(name),
                "Error message should contain failing test name '$name' but was:\n$message"
            )
        }
    }

    // --- Providers ---

    @Provide
    fun withinToleranceResults(): Arbitrary<List<StlComparator.ComparisonResult>> {
        return withinToleranceResult().list().ofMinSize(1).ofMaxSize(10)
    }

    @Provide
    fun mixedResultsWithAtLeastOneFailing(): Arbitrary<List<StlComparator.ComparisonResult>> {
        val passing = withinToleranceResult().list().ofMinSize(0).ofMaxSize(5)
        val failing = exceedingToleranceResult().list().ofMinSize(1).ofMaxSize(5)

        return combine(passing, failing).`as` { p, f -> (p + f).shuffled() }
    }

    @Provide
    fun multipleFailingResults(): Arbitrary<List<StlComparator.ComparisonResult>> {
        val failing = exceedingToleranceResultWithUniqueName().list().ofMinSize(2).ofMaxSize(6)
        val passing = withinToleranceResult().list().ofMinSize(0).ofMaxSize(3)

        return combine(failing, passing).`as` { f, p -> (f + p).shuffled() }
    }

    // --- Helper Arbitraries ---

    private fun withinToleranceResult(): Arbitrary<StlComparator.ComparisonResult> {
        // Generate expected values, then pick generated values within ±14% (safely inside 15%)
        return combine(
            testNames(),
            Arbitraries.integers().between(100, 10000),
            Arbitraries.longs().between(1000L, 1000000L),
            Arbitraries.integers().between(-14, 14),
            Arbitraries.integers().between(-14, 14)
        ).`as` { name, expectedTriangles, expectedFileSize, trianglePercentOff, sizePercentOff ->
            val genTriangles = expectedTriangles + (expectedTriangles * trianglePercentOff / 100)
            val genSize = expectedFileSize + (expectedFileSize * sizePercentOff / 100)

            val trianglePercentDiff = kotlin.math.abs(
                (genTriangles - expectedTriangles).toDouble() / expectedTriangles * 100.0
            )
            val sizePercentDiff = kotlin.math.abs(
                (genSize - expectedFileSize).toDouble() / expectedFileSize * 100.0
            )

            StlComparator.ComparisonResult(
                testName = name,
                generatedTriangles = genTriangles,
                expectedTriangles = expectedTriangles,
                generatedFileSize = genSize,
                expectedFileSize = expectedFileSize,
                trianglesMatch = trianglePercentDiff <= 15.0,
                fileSizeMatch = sizePercentDiff <= 15.0
            )
        }
    }

    private fun exceedingToleranceResult(): Arbitrary<StlComparator.ComparisonResult> {
        // Generate expected values, then pick generated triangles >15% away
        return combine(
            testNames(),
            Arbitraries.integers().between(100, 10000),
            Arbitraries.longs().between(1000L, 1000000L)
        ).`as` { name, expectedTriangles, expectedFileSize ->
            // Double the triangle count to ensure >15% deviation
            val genTriangles = expectedTriangles * 2
            val genSize = expectedFileSize // file size within tolerance

            val trianglePercentDiff = kotlin.math.abs(
                (genTriangles - expectedTriangles).toDouble() / expectedTriangles * 100.0
            )
            val sizePercentDiff = 0.0

            StlComparator.ComparisonResult(
                testName = name,
                generatedTriangles = genTriangles,
                expectedTriangles = expectedTriangles,
                generatedFileSize = genSize,
                expectedFileSize = expectedFileSize,
                trianglesMatch = trianglePercentDiff <= 15.0,
                fileSizeMatch = sizePercentDiff <= 15.0
            )
        }
    }

    private fun exceedingToleranceResultWithUniqueName(): Arbitrary<StlComparator.ComparisonResult> {
        return combine(
            uniqueTestNames(),
            Arbitraries.integers().between(100, 10000),
            Arbitraries.longs().between(1000L, 1000000L)
        ).`as` { name, expectedTriangles, expectedFileSize ->
            val genTriangles = expectedTriangles * 2
            val genSize = expectedFileSize

            val trianglePercentDiff = kotlin.math.abs(
                (genTriangles - expectedTriangles).toDouble() / expectedTriangles * 100.0
            )
            val sizePercentDiff = 0.0

            StlComparator.ComparisonResult(
                testName = name,
                generatedTriangles = genTriangles,
                expectedTriangles = expectedTriangles,
                generatedFileSize = genSize,
                expectedFileSize = expectedFileSize,
                trianglesMatch = trianglePercentDiff <= 15.0,
                fileSizeMatch = sizePercentDiff <= 15.0
            )
        }
    }

    private fun testNames(): Arbitrary<String> {
        return Arbitraries.of(
            "cube_test", "sphere_test", "cylinder_test", "cone_test",
            "torus_test", "pyramid_test", "prism_test", "helix_test",
            "custom_sun", "control_rose", "tower_test", "bridge_test"
        )
    }

    private fun uniqueTestNames(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('_')
            .ofMinLength(5)
            .ofMaxLength(20)
    }
}
