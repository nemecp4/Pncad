package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class ControllRoseBenchmarkTest : CategoryBenchmarkTest() {

    val testCase = TestCase(
        name = "controll_rose",
        category = "custom",
        code = loadResource("testcases/custom/controll_rose.scad"),
        expectedStlPath = "expected_results/controll_rose.stl"
    )

    override fun testCases() = listOf(testCase)

    @Test
    fun runControllRose() = runCategory()
}
