package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class CustomBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.custom

    @Test
    fun runCustom() = runCategory()
}
