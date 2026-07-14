package com.openscadviewer.benchmark

import org.junit.jupiter.api.Test

class GeometryPrimitivesBenchmarkTest : CategoryBenchmarkTest() {
    override fun testCases() = TestCaseRegistry.geometryPrimitives

    @Test
    fun runGeometryPrimitives() = runCategory()
}
