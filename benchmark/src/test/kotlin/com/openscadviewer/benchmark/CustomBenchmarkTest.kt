package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

/**
 * Runs all custom benchmark test cases together.
 * Individual custom tests can also be run separately via their own test classes
 * (e.g., SunBenchmarkTest, TowerBenchmarkTest, etc.)
 */
class CustomBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.custom

    @Test
    fun runCustom() = runCategory()
}
