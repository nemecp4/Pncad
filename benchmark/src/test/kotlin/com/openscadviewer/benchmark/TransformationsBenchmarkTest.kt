package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class TransformationsBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.transformations

    @Test
    fun runTransformations() = runCategory()
}
