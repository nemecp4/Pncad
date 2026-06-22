package com.openscadviewer.benchmark

import com.openscadviewer.engine.ComputeEngine
import com.openscadviewer.engine.KotlinComputeEngine
import com.openscadviewer.engine.CgalComputeEngine
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class BenchmarkTest {

    @Test
    fun `run all 50 test cases against available engines`() {
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

        // Print timing summary
        TimingSummaryFormatter.format(results)

        // Assert result count: 50 test cases × number of engines
        // Both available and unavailable engines produce results (SKIPPED for unavailable)
        val expectedResultCount = 50 * engines.size
        assertEquals(expectedResultCount, results.size,
            "Expected $expectedResultCount results (50 test cases × ${engines.size} engines), got ${results.size}")
    }
}
