package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class SunBenchmarkTest : CategoryBenchmarkTest() {

    val testCase = TestCase(
        name = "sun",
        category = "custom",
        code = loadResource("testcases/custom/sun.scad"),
        expectedStlPath = "expected_results/sun.stl"
    )

    override fun testCases() = listOf(testCase)

    @Test
    fun runSun() = runCategory()
}
