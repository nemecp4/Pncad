package com.openscadviewer.benchmark

import com.openscadviewer.engine.ComputeEngine
import com.openscadviewer.engine.KotlinComputeEngine
import com.openscadviewer.engine.CgalComputeEngine
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class BenchmarkTest {

    @Test
    fun runAllBenchamrkTestCases() {
        val kotlinEngine = KotlinComputeEngine()
        val cgalEngine = CgalComputeEngine()

        val engines = listOf<Pair<String, ComputeEngine>>(
            "kotlin" to kotlinEngine,
            "cgal" to cgalEngine
        )

        val stlOutputDir = File(
            System.getProperty("benchmark.stl.outputDir", "build/benchmark-stl/")
        )

        val runner = BenchmarkRunner(
            engines = engines,
            testCases = TestCaseRegistry.allCases,
            stlOutputDir = stlOutputDir
        )

        val results = runner.run()

        // Compare with reference STL files
        val comparisons = runner.compareWithReferences(results)

        // Print timing summary with comparisons
        TimingSummaryFormatter.format(results, comparisons)

        // Assert STL comparisons are within 15% tolerance
        if (comparisons.isNotEmpty()) {
            StlComparisonAssert.assertAllWithinTolerance(comparisons, 0.15)
        }

        // Assert result count: testCases × number of engines
        // Both available and unavailable engines produce results (SKIPPED for unavailable)
        val expectedResultCount = TestCaseRegistry.allCases.size * engines.size
        assertEquals(expectedResultCount, results.size,
            "Expected $expectedResultCount results (${TestCaseRegistry.allCases.size} test cases × ${engines.size} engines), got ${results.size}")
    }
}
