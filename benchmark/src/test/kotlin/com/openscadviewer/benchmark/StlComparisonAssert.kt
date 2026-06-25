package com.openscadviewer.benchmark

import org.junit.jupiter.api.Assertions.fail

/**
 * Provides JUnit assertion methods that fail the test when STL comparison
 * results exceed the configured tolerance threshold.
 *
 * Collects all failures before throwing a single AssertionError with
 * a comprehensive message listing every failing test case and its metrics.
 */
object StlComparisonAssert {

    /**
     * Asserts that all comparison results are within the given tolerance.
     * If any result exceeds the tolerance, throws a single AssertionError
     * listing all failures with test name, actual vs expected values,
     * percent difference, and threshold.
     *
     * @param comparisons list of comparison results to check
     * @param tolerance fractional tolerance (0.15 = 15%)
     */
    fun assertAllWithinTolerance(
        comparisons: List<StlComparator.ComparisonResult>,
        tolerance: Double = 0.15
    ) {
        val failures = mutableListOf<String>()

        for (result in comparisons) {
            if (!result.withinTolerance(tolerance)) {
                failures.add(buildFailureMessage(result, tolerance))
            }
        }

        if (failures.isNotEmpty()) {
            fail<Unit>("STL comparison failed for ${failures.size} test(s):\n${failures.joinToString("\n")}")
        }
    }

    /**
     * Asserts that a single comparison result is within the given tolerance.
     * Throws an AssertionError if the result exceeds the tolerance.
     *
     * @param result the comparison result to check
     * @param tolerance fractional tolerance (0.15 = 15%)
     */
    fun assertWithinTolerance(
        result: StlComparator.ComparisonResult,
        tolerance: Double = 0.15
    ) {
        if (!result.withinTolerance(tolerance)) {
            fail<Unit>("STL comparison failed for 1 test(s):\n${buildFailureMessage(result, tolerance)}")
        }
    }

    private fun buildFailureMessage(result: StlComparator.ComparisonResult, tolerance: Double): String {
        return buildString {
            append("${result.testName}: ")
            if (!result.trianglesMatch) {
                append("triangles ${result.generatedTriangles} vs ${result.expectedTriangles} ")
                append("(${formatPercent(result.trianglePercentDiff)} diff) ")
            }
            if (!result.fileSizeMatch) {
                append("file size ${result.generatedFileSize} vs ${result.expectedFileSize} bytes ")
                append("(${formatPercent(result.fileSizePercentDiff)} diff) ")
            }
            append("exceeds ${(tolerance * 100).toInt()}% tolerance")
        }
    }

    private fun formatPercent(value: Double): String {
        val sign = if (value >= 0) "+" else ""
        return "$sign${"%.1f".format(value)}%"
    }
}
