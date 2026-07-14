package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class EdgeCasesBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.edgeCases

    @Test
    fun runEdgeCases() = runCategory()
}
