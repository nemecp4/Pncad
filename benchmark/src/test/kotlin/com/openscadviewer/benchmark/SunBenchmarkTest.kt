package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class SunBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.custom.filter { it.name == "sun" }

    @Test
    fun runSun() = runCategory()
}
