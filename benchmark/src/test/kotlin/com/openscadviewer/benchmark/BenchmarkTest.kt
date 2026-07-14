package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

/**
 * Runs ALL benchmark test cases across all categories.
 * For running a single category, use the category-specific test classes:
 *   - GeometryPrimitivesBenchmarkTest
 *   - TransformationsBenchmarkTest
 *   - LinearExtrusionBenchmarkTest
 *   - CsgOperationsBenchmarkTest
 *   - CombinedOperationsBenchmarkTest
 *   - VariablesExpressionsBenchmarkTest
 *   - EdgeCasesBenchmarkTest
 *   - CustomBenchmarkTest
 */
class BenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.allCases

    @Test
    fun runAllBenchmarkTestCases() = runCategory()
}
