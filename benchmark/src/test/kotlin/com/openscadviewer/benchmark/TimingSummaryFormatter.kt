package com.openscadviewer.benchmark

/**
 * Formats and prints benchmark results grouped by engine.
 *
 * Output format:
 * ```
 * engine - {ENGINE_NAME}
 * TEST1 - X.XXX seconds
 *   {test_name}.scad -> ./benchmark/build/benchmark-stl/{output_filename}.stl
 * TEST2 - Y.YYY seconds
 *   {test_name}.scad -> ./benchmark/build/benchmark-stl/{output_filename}.stl
 * ```
 */
object TimingSummaryFormatter {

    data class EngineAggregate(
        val engineName: String,
        val totalTimeMs: Long,
        val successCount: Int,
        val timeoutCount: Int,
        val errorCount: Int,
        val skippedCount: Int
    )

    /**
     * Formats results into the engine-grouped output and prints it to stdout.
     * Returns the formatted string for testability.
     */
    fun format(results: List<BenchmarkResult>, comparisons: List<StlComparator.ComparisonResult> = emptyList()): String {
        val output = buildString {
            append(formatTable(results))
            if (comparisons.isNotEmpty()) {
                appendLine()
                appendLine()
                append(formatComparisons(comparisons))
            }
        }
        print(output)
        return output
    }

    /**
     * Formats STL comparison results into a readable section.
     */
    fun formatComparisons(comparisons: List<StlComparator.ComparisonResult>): String {
        return buildString {
            appendLine("=== Reference STL Comparison ===")
            for (result in comparisons) {
                val status = if (result.match) {
                    "\u2713 MATCH"
                } else {
                    val diff = result.percentDiff
                    val direction = if (diff < 0) "fewer" else "more"
                    "\u2717 ${"%.0f".format(kotlin.math.abs(diff))}% $direction triangles"
                }
                appendLine("${result.testName}: ${result.generatedTriangles} triangles (generated) vs ${result.expectedTriangles} (expected) $status")
            }
        }.trimEnd()
    }

    /**
     * Formats results grouped by engine.
     *
     * Each engine section has a header line `engine - {ENGINE_NAME}` followed by
     * one entry per test result showing timing and file mapping.
     */
    fun formatTable(results: List<BenchmarkResult>): String {
        val grouped = results.groupBy { it.engineName }

        return buildString {
            for ((engineName, engineResults) in grouped) {
                appendLine("engine - $engineName")
                for (result in engineResults) {
                    val timeSeconds = result.timeMs / 1000.0
                    val formattedTime = "%.3f".format(timeSeconds)
                    appendLine("${result.testCase.name} - $formattedTime seconds")

                    // Second line: source .scad -> output .stl (or status for failures)
                    val detail = when (result.status) {
                        ResultStatus.SUCCESS -> {
                            val stlFilename = StlFileNamer.generateFilename(
                                result.testCase.category,
                                result.testCase.name
                            )
                            "${result.testCase.name}.scad -> ./benchmark/build/benchmark-stl/${result.engineName}/$stlFilename"
                        }
                        ResultStatus.TIMEOUT -> "TIMEOUT"
                        ResultStatus.SKIPPED -> "SKIPPED"
                        ResultStatus.PARSE_ERROR -> {
                            val errorInfo = if (result.errorDetail != null) ": ${result.errorDetail}" else ""
                            "PARSE_ERROR$errorInfo"
                        }
                        ResultStatus.COMPUTE_ERROR -> {
                            val errorInfo = if (result.errorDetail != null) ": ${result.errorDetail}" else ""
                            "COMPUTE_ERROR$errorInfo"
                        }
                    }
                    appendLine("  $detail")
                }
            }
        }.trimEnd()
    }

    /**
     * Computes per-engine aggregate statistics from a list of results.
     */
    fun aggregateByEngine(results: List<BenchmarkResult>): List<EngineAggregate> {
        return results.groupBy { it.engineName }.map { (engineName, engineResults) ->
            EngineAggregate(
                engineName = engineName,
                totalTimeMs = engineResults.sumOf { it.timeMs },
                successCount = engineResults.count { it.status == ResultStatus.SUCCESS },
                timeoutCount = engineResults.count { it.status == ResultStatus.TIMEOUT },
                errorCount = engineResults.count { it.status == ResultStatus.COMPUTE_ERROR || it.status == ResultStatus.PARSE_ERROR },
                skippedCount = engineResults.count { it.status == ResultStatus.SKIPPED }
            )
        }
    }
}
