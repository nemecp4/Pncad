package com.openscadviewer.benchmark

/**
 * Formats and prints benchmark results as a table with per-engine aggregate statistics.
 */
object TimingSummaryFormatter {

    private const val MAX_ERROR_DETAIL_LENGTH = 256

    data class EngineAggregate(
        val engineName: String,
        val totalTimeMs: Long,
        val successCount: Int,
        val timeoutCount: Int,
        val errorCount: Int,
        val skippedCount: Int
    )

    /**
     * Formats results into a table string and prints it to stdout.
     * Returns the formatted string for testability.
     */
    fun format(results: List<BenchmarkResult>): String {
        val output = buildString {
            appendLine(formatTable(results))
            appendLine()
            appendLine(formatAggregates(aggregateByEngine(results)))
        }
        print(output)
        return output
    }

    /**
     * Formats results into a table with columns:
     * test name | category | engine | time (ms) | status | error detail
     */
    fun formatTable(results: List<BenchmarkResult>): String {
        val header = listOf("Test Name", "Category", "Engine", "Time (ms)", "Status", "Error Detail")

        val rows = results.map { result ->
            listOf(
                result.testCase.name,
                result.testCase.category,
                result.engineName,
                result.timeMs.toString(),
                result.status.name,
                truncateErrorDetail(result.errorDetail)
            )
        }

        return formatColumns(header, rows)
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

    private fun formatAggregates(aggregates: List<EngineAggregate>): String {
        val header = listOf("Engine", "Total Time (ms)", "Success", "Timeout", "Error", "Skipped")

        val rows = aggregates.map { agg ->
            listOf(
                agg.engineName,
                agg.totalTimeMs.toString(),
                agg.successCount.toString(),
                agg.timeoutCount.toString(),
                agg.errorCount.toString(),
                agg.skippedCount.toString()
            )
        }

        return buildString {
            appendLine("=== Per-Engine Aggregates ===")
            append(formatColumns(header, rows))
        }
    }

    private fun formatColumns(header: List<String>, rows: List<List<String>>): String {
        val allRows = listOf(header) + rows
        val columnWidths = header.indices.map { col ->
            allRows.maxOf { row -> row[col].length }
        }

        return buildString {
            // Header row
            appendLine(formatRow(header, columnWidths))
            // Separator
            appendLine(columnWidths.joinToString(" | ") { "-".repeat(it) })
            // Data rows
            rows.forEach { row ->
                appendLine(formatRow(row, columnWidths))
            }
        }.trimEnd()
    }

    private fun formatRow(values: List<String>, widths: List<Int>): String {
        return values.mapIndexed { index, value ->
            value.padEnd(widths[index])
        }.joinToString(" | ")
    }

    private fun truncateErrorDetail(detail: String?): String {
        if (detail == null) return ""
        return if (detail.length > MAX_ERROR_DETAIL_LENGTH) {
            detail.take(MAX_ERROR_DETAIL_LENGTH) + "..."
        } else {
            detail
        }
    }
}
