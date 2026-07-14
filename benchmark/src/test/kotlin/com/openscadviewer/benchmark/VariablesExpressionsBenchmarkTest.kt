package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class VariablesExpressionsBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.variablesExpressions

    @Test
    fun runVariablesExpressions() = runCategory()
}
