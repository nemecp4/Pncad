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
     * For any list of BenchmarkResults, the formatted output must contain one timing line per result.
     *
     * Validates: Requirements 4.6, 4.7
     */
    @Property(tries = 100)
    @Tag("property-7-timing-summary-correctness")
    fun formattedOutputContainsOneTimingLinePerResult(
        @ForAll("benchmarkResults") results: List<BenchmarkResult>
    ) {
        val output = TimingSummaryFormatter.formatTable(results)
        val timingLines = output.lines().filter { it.contains(" seconds") && !it.startsWith("  ") }

        assertEquals(results.size, timingLines.size,
            "Expected ${results.size} timing lines, got ${timingLines.size}.\nOutput:\n$output")
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 7: Timing summary correctness
     *
     * For any list of BenchmarkResults, the formatted output must contain an engine header
     * for each distinct engine.
     *
     * Validates: Requirements 4.6, 4.7
     */
    @Property(tries = 100)
    @Tag("property-7-timing-summary-correctness")
    fun formattedOutputContainsEngineHeaders(
        @ForAll("benchmarkResults") results: List<BenchmarkResult>
    ) {
        val output = TimingSummaryFormatter.formatTable(results)
        val distinctEngines = results.map { it.engineName }.distinct()

        for (engine in distinctEngines) {
            assertTrue(output.contains("engine - $engine"),
                "Output missing engine header 'engine - $engine'")
        }
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
    @Tag("property-7-timing-summary-correctness")
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
     * For any list of BenchmarkResults, each result's test name must appear in the formatted output.
     *
     * Validates: Requirements 4.6, 4.7
     */
    @Property(tries = 100)
    @Tag("property-7-timing-summary-correctness")
    fun eachResultTestNameAppearsInOutput(
        @ForAll("benchmarkResults") results: List<BenchmarkResult>
    ) {
        val output = TimingSummaryFormatter.formatTable(results)

        for (result in results) {
            assertTrue(output.contains(result.testCase.name),
                "Output does not contain test name '${result.testCase.name}'")
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
    @Tag("property-12-result-status-enum-constraint")
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
    @Tag("property-12-result-status-enum-constraint")
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
     * For any benchmark result, the detail line in the formatted output reflects the status correctly:
     * - SUCCESS results show the .stl path
     * - TIMEOUT results show "TIMEOUT"
     * - SKIPPED results show "SKIPPED"
     * - PARSE_ERROR results show "PARSE_ERROR"
     * - COMPUTE_ERROR results show "COMPUTE_ERROR"
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Tag("property-12-result-status-enum-constraint")
    fun statusInFormattedOutputMatchesResult(
        @ForAll("singleBenchmarkResult") result: BenchmarkResult
    ) {
        val output = TimingSummaryFormatter.formatTable(listOf(result))

        when (result.status) {
            ResultStatus.SUCCESS -> {
                assertTrue(output.contains(".stl"),
                    "SUCCESS result should contain .stl path in output")
            }
            ResultStatus.TIMEOUT -> {
                assertTrue(output.contains("TIMEOUT"),
                    "TIMEOUT result should contain 'TIMEOUT' in output")
            }
            ResultStatus.SKIPPED -> {
                assertTrue(output.contains("SKIPPED"),
                    "SKIPPED result should contain 'SKIPPED' in output")
            }
            ResultStatus.PARSE_ERROR -> {
                assertTrue(output.contains("PARSE_ERROR"),
                    "PARSE_ERROR result should contain 'PARSE_ERROR' in output")
            }
            ResultStatus.COMPUTE_ERROR -> {
                assertTrue(output.contains("COMPUTE_ERROR"),
                    "COMPUTE_ERROR result should contain 'COMPUTE_ERROR' in output")
            }
        }
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
