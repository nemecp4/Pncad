package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class LinearExtrusionBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.linearExtrusion

    @Test
    fun runLinearExtrusion() = runCategory()
}
