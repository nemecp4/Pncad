package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class RotateText1BenchmarkTest : CategoryBenchmarkTest() {

    val testCase = TestCase(
        name = "rotate_text1",
        category = "custom",
        code = loadResource("testcases/custom/rotate_text1.scad"),
        expectedStlPath = "expected_results/rotate_text1.stl"
    )

    override fun testCases() = listOf(testCase)

    @Test
    fun runRotateText() = runCategory()
}
