package com.openscadviewer.benchmark

import com.openscadviewer.engine.ComputeEngine
import com.openscadviewer.engine.KotlinComputeEngine
import com.openscadviewer.engine.CgalComputeEngine
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Base class for category-specific benchmark tests.
 * Provides shared engine setup, runner configuration, and table-formatted output.
 *
 * Output format:
 * ─────────────────────────────┼────────┼───────────────┼────────────────┼──────────────
 * TEST_NAME                    │ ENGINE │ TRIANGLES(A/E)│ FILE_SIZE(A/E) │ RESULT
 * ─────────────────────────────┼────────┼───────────────┼────────────────┼──────────────
 * sun                          │ cgal   │       672/644 │  32.9KB/31.5KB │ SUCCESS (5ms)
 * sun                          │ cgal   │         0/644 │    84B/31.5KB  │ COMPARISON_FAILED
 * ─────────────────────────────┼────────┼───────────────┼────────────────┼──────────────
 */
abstract class CategoryBenchmarkTest {

    protected val kotlinEngine: ComputeEngine = KotlinComputeEngine()
    protected val cgalEngine: ComputeEngine = CgalComputeEngine()

    protected val engines: List<Pair<String, ComputeEngine>> = listOf(
        "kotlin" to kotlinEngine,
        "cgal" to cgalEngine
    )

    protected val stlOutputDir: File = File(
        System.getProperty("benchmark.stl.outputDir", "build/benchmark-stl/")
    )

    /**
     * Override to provide the test cases for this category.
     */
    abstract fun testCases(): List<TestCase>

    /**
     * Runs all test cases for this category, prints table summary, and asserts results.
     */
    protected fun runCategory() {
        val cases = testCases()
        val runner = BenchmarkRunner(
            engines = engines,
            testCases = cases,
            stlOutputDir = stlOutputDir
        )

        val results = runner.run()

        // Print table summary
        printResultsTable(results)

        // Assert expected result count
        val expectedCount = cases.size * engines.size
        assertEquals(expectedCount, results.size,
            "Expected $expectedCount results (${cases.size} cases × ${engines.size} engines)")

        // Collect failures from results
        val failures = results.filter {
            it.status == ResultStatus.COMPARISON_FAILED ||
                (it.status == ResultStatus.COMPUTE_ERROR && it.testCase.expectedStlPath != null)
        }

        if (failures.isNotEmpty()) {
            val messages = failures.map { r ->
                when (r.status) {
                    ResultStatus.COMPARISON_FAILED ->
                        "  • ${r.testCase.name} (${r.engineName}): ${r.errorDetail}"
                    ResultStatus.COMPUTE_ERROR ->
                        "  • ${r.testCase.name} (${r.engineName}): COMPUTE_ERROR - ${r.errorDetail ?: "unknown"}"
                    else -> "  • ${r.testCase.name} (${r.engineName}): ${r.status}"
                }
            }
            fail<Unit>("${failures.size} failure(s):\n${messages.joinToString("\n")}")
        }
    }

    /**
     * Prints results in a fixed-width table format with actual/expected columns.
     */
    private fun printResultsTable(results: List<BenchmarkResult>) {
        val nameWidth = maxOf(30, results.maxOfOrNull { it.testCase.name.length + 2 } ?: 30)
        val header = "%-${nameWidth}s │ %-6s │ %13s │ %14s │ %s".format(
            "TEST_NAME", "ENGINE", "TRIANGLES(A/E)", "FILE_SIZE(A/E)", "RESULT"
        )
        val separator = "─".repeat(nameWidth) + "─┼─" + "─".repeat(6) + "─┼─" +
            "─".repeat(13) + "─┼─" + "─".repeat(14) + "─┼─" + "─".repeat(30)

        println(separator)
        println(header)
        println(separator)

        for (result in results) {
            val trianglesStr = formatActualExpected(
                if (result.generatedTriangles >= 0) result.generatedTriangles.toString() else "-",
                if (result.expectedTriangles >= 0) result.expectedTriangles.toString() else null
            )
            val fileSizeStr = formatActualExpected(
                if (result.generatedFileSize >= 0) formatFileSize(result.generatedFileSize) else "-",
                if (result.expectedFileSize >= 0) formatFileSize(result.expectedFileSize) else null
            )

            val resultStr = when (result.status) {
                ResultStatus.SUCCESS -> "SUCCESS (${result.timeMs}ms)"
                ResultStatus.SKIPPED -> "SKIPPED"
                ResultStatus.TIMEOUT -> "TIMEOUT"
                ResultStatus.PARSE_ERROR -> "PARSE_ERROR"
                ResultStatus.COMPUTE_ERROR -> "COMPUTE_ERROR: ${result.errorDetail?.take(40) ?: ""}"
                ResultStatus.COMPARISON_FAILED -> "FAILED: ${result.errorDetail?.take(50) ?: ""}"
            }
            println("%-${nameWidth}s │ %-6s │ %13s │ %14s │ %s".format(
                result.testCase.name,
                result.engineName,
                trianglesStr,
                fileSizeStr,
                resultStr
            ))
        }
        println(separator)
    }

    private fun formatActualExpected(actual: String, expected: String?): String {
        return if (expected != null) "$actual/$expected" else "$actual/ -"
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
    }
}
