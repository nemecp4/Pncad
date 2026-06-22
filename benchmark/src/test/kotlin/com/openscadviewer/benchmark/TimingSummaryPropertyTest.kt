package com.openscadviewer.benchmark

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: engine-modularization-and-benchmarks
 *
 * Property-based tests for TimingSummaryFormatter using jqwik.
 *
 * Property 7: Timing summary correctness
 * Property 12: Result status enum constraint
 *
 * Validates: Requirements 4.6, 4.7, 7.3
 */
class TimingSummaryPropertyTest {

    // --- Property 7: Timing summary correctness ---

    /**
     * Feature: engine-modularization-and-benchmarks, Property 7: Timing summary correctness
     *
     * For any list of BenchmarkResults, the formatted table must contain one row per result.
     *
     * Validates: Requirements 4.6, 4.7
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 7: Timing summary correctness")
    fun formattedTableContainsOneRowPerResult(
        @ForAll("benchmarkResults") results: List<BenchmarkResult>
    ) {
        val table = TimingSummaryFormatter.formatTable(results)
        val lines = table.lines().filter { it.isNotBlank() }

        // First line is header, second is separator, remaining are data rows
        val dataRowCount = if (lines.size >= 2) lines.size - 2 else 0
        assertEquals(results.size, dataRowCount,
            "Expected ${results.size} data rows, got $dataRowCount.\nTable:\n$table")
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 7: Timing summary correctness
     *
     * For any list of BenchmarkResults, the formatted table must contain all required columns:
     * test name, category, engine, time, status.
     *
     * Validates: Requirements 4.6, 4.7
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 7: Timing summary correctness")
    fun formattedTableContainsAllRequiredColumns(
        @ForAll("benchmarkResults") results: List<BenchmarkResult>
    ) {
        val table = TimingSummaryFormatter.formatTable(results)
        val headerLine = table.lines().firstOrNull() ?: ""

        // Check all required columns are present in the header
        assertTrue(headerLine.contains("Test Name"), "Header missing 'Test Name' column")
        assertTrue(headerLine.contains("Category"), "Header missing 'Category' column")
        assertTrue(headerLine.contains("Engine"), "Header missing 'Engine' column")
        assertTrue(headerLine.contains("Time (ms)"), "Header missing 'Time (ms)' column")
        assertTrue(headerLine.contains("Status"), "Header missing 'Status' column")
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 7: Timing summary correctness
     *
     * For any list of BenchmarkResults, the per-engine aggregate totals must equal the sum
     * of individual results for that engine.
     *
     * Validates: Requirements 4.6, 4.7
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 7: Timing summary correctness")
    fun aggregateTotalsEqualSumOfIndividualResults(
        @ForAll("benchmarkResults") results: List<BenchmarkResult>
    ) {
        val aggregates = TimingSummaryFormatter.aggregateByEngine(results)

        for (aggregate in aggregates) {
            val engineResults = results.filter { it.engineName == aggregate.engineName }

            // Total time must equal sum of individual times
            val expectedTotalTime = engineResults.sumOf { it.timeMs }
            assertEquals(expectedTotalTime, aggregate.totalTimeMs,
                "Engine '${aggregate.engineName}': totalTimeMs mismatch. Expected $expectedTotalTime, got ${aggregate.totalTimeMs}")

            // Success count
            val expectedSuccessCount = engineResults.count { it.status == ResultStatus.SUCCESS }
            assertEquals(expectedSuccessCount, aggregate.successCount,
                "Engine '${aggregate.engineName}': successCount mismatch")

            // Timeout count
            val expectedTimeoutCount = engineResults.count { it.status == ResultStatus.TIMEOUT }
            assertEquals(expectedTimeoutCount, aggregate.timeoutCount,
                "Engine '${aggregate.engineName}': timeoutCount mismatch")

            // Error count (COMPUTE_ERROR + PARSE_ERROR)
            val expectedErrorCount = engineResults.count {
                it.status == ResultStatus.COMPUTE_ERROR || it.status == ResultStatus.PARSE_ERROR
            }
            assertEquals(expectedErrorCount, aggregate.errorCount,
                "Engine '${aggregate.engineName}': errorCount mismatch")

            // Skipped count
            val expectedSkippedCount = engineResults.count { it.status == ResultStatus.SKIPPED }
            assertEquals(expectedSkippedCount, aggregate.skippedCount,
                "Engine '${aggregate.engineName}': skippedCount mismatch")
        }
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 7: Timing summary correctness
     *
     * For any list of BenchmarkResults, each result's data must appear in the formatted table rows.
     *
     * Validates: Requirements 4.6, 4.7
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 7: Timing summary correctness")
    fun eachResultDataAppearsInTableRow(
        @ForAll("benchmarkResults") results: List<BenchmarkResult>
    ) {
        val table = TimingSummaryFormatter.formatTable(results)

        for (result in results) {
            assertTrue(table.contains(result.testCase.name),
                "Table does not contain test name '${result.testCase.name}'")
            assertTrue(table.contains(result.testCase.category),
                "Table does not contain category '${result.testCase.category}'")
            assertTrue(table.contains(result.engineName),
                "Table does not contain engine name '${result.engineName}'")
            assertTrue(table.contains(result.timeMs.toString()),
                "Table does not contain time '${result.timeMs}'")
            assertTrue(table.contains(result.status.name),
                "Table does not contain status '${result.status.name}'")
        }
    }

    // --- Property 12: Result status enum constraint ---

    /**
     * Feature: engine-modularization-and-benchmarks, Property 12: Result status enum constraint
     *
     * For any benchmark result row, the status value must be exactly one of:
     * SUCCESS, PARSE_ERROR, COMPUTE_ERROR, TIMEOUT, SKIPPED.
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 12: Result status enum constraint")
    fun resultStatusIsConstrainedToValidValues(
        @ForAll("singleBenchmarkResult") result: BenchmarkResult
    ) {
        val validStatuses = setOf(
            ResultStatus.SUCCESS,
            ResultStatus.PARSE_ERROR,
            ResultStatus.COMPUTE_ERROR,
            ResultStatus.TIMEOUT,
            ResultStatus.SKIPPED
        )
        assertTrue(result.status in validStatuses,
            "Status '${result.status}' is not in the valid set: $validStatuses")
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 12: Result status enum constraint
     *
     * The ResultStatus enum contains exactly 5 values.
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 12: Result status enum constraint")
    fun resultStatusEnumHasExactlyFiveValues(
        @ForAll("singleBenchmarkResult") result: BenchmarkResult
    ) {
        val allValues = ResultStatus.entries
        assertEquals(5, allValues.size,
            "ResultStatus enum should have exactly 5 values, but has ${allValues.size}: $allValues")

        val expectedNames = setOf("SUCCESS", "PARSE_ERROR", "COMPUTE_ERROR", "TIMEOUT", "SKIPPED")
        val actualNames = allValues.map { it.name }.toSet()
        assertEquals(expectedNames, actualNames,
            "ResultStatus enum values mismatch. Expected: $expectedNames, Actual: $actualNames")
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 12: Result status enum constraint
     *
     * For any benchmark result, the status displayed in the formatted table matches
     * exactly one of the valid enum names.
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 12: Result status enum constraint")
    fun statusInFormattedTableMatchesEnumName(
        @ForAll("singleBenchmarkResult") result: BenchmarkResult
    ) {
        val table = TimingSummaryFormatter.formatTable(listOf(result))
        val validStatusNames = setOf("SUCCESS", "PARSE_ERROR", "COMPUTE_ERROR", "TIMEOUT", "SKIPPED")

        // The table should contain exactly the status name from the result
        assertTrue(table.contains(result.status.name),
            "Formatted table does not contain status '${result.status.name}'")

        // Verify the status in the table is one of the valid values
        assertTrue(result.status.name in validStatusNames,
            "Status '${result.status.name}' in formatted output is not a valid ResultStatus value")
    }

    // --- Providers ---

    @Provide
    fun benchmarkResults(): Arbitrary<List<BenchmarkResult>> {
        return singleBenchmarkResult().list().ofMinSize(0).ofMaxSize(15)
    }

    @Provide
    fun singleBenchmarkResult(): Arbitrary<BenchmarkResult> {
        val testCases = testCaseArbitrary()
        val engineNames = Arbitraries.of("kotlin_engine", "cgal_engine", "test_engine")
        val timesMs = Arbitraries.longs().between(0, 120000)
        val statuses = Arbitraries.of(*ResultStatus.entries.toTypedArray())
        val errorDetails = Arbitraries.oneOf(
            Arbitraries.just(null as String?),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100)
        )

        return Combinators.combine(testCases, engineNames, timesMs, statuses, errorDetails)
            .`as` { testCase, engine, time, status, error ->
                BenchmarkResult(
                    testCase = testCase,
                    engineName = engine,
                    timeMs = time,
                    status = status,
                    errorDetail = error
                )
            }
    }

    private fun testCaseArbitrary(): Arbitrary<TestCase> {
        val categories = Arbitraries.of(TestCase.VALID_CATEGORIES.toList())
        val names = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('_')
            .ofMinLength(3)
            .ofMaxLength(20)
            .filter { it.matches(Regex("[a-z0-9_]+")) && it.isNotEmpty() }
        val codes = Arbitraries.of(
            "cube([1,1,1]);",
            "sphere(r=5);",
            "cylinder(h=10, r=3);",
            "translate([1,0,0]) cube([2,2,2]);",
            "union() { cube([1,1,1]); sphere(r=2); }",
            "difference() { cube([5,5,5]); sphere(r=3); }",
            "linear_extrude(height=5) square([2,2]);",
            "rotate([45,0,0]) cylinder(h=5, r=2);",
            "intersection() { cube([3,3,3]); sphere(r=2); }",
            "scale([2,1,1]) cube([1,1,1]);"
        )

        return Combinators.combine(names, categories, codes)
            .`as` { name, category, code -> TestCase(name = name, category = category, code = code) }
    }
}
