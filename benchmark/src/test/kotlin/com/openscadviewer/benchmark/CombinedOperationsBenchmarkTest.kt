package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class CombinedOperationsBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.combinedOperations

    @Test
    fun runCombinedOperations() = runCategory()
}
