package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class TowerBenchmarkTest : CategoryBenchmarkTest() {

    val testCase = TestCase(
        name = "tower",
        category = "custom",
        code = loadResource("testcases/custom/tower.scad"),
        expectedStlPath = "expected_results/tower.stl"
    )

    override fun testCases() = listOf(testCase)

    @Test
    fun runTower() = runCategory()
}
