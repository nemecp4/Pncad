package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class CsgOperationsBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.csgOperations

    @Test
    fun runCsgOperations() = runCategory()
}
