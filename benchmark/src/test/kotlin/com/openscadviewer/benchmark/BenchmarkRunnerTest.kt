package com.openscadviewer.benchmark

import com.openscadviewer.engine.ComputeEngine
import com.openscadviewer.engine.MeshResult
import com.openscadviewer.engine.ProgressCallback
import com.openscadviewer.parser.SceneNode
import kotlinx.coroutines.delay
import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Feature: engine-modularization-and-benchmarks
 *
 * Property-based tests for BenchmarkRunner using jqwik.
 *
 * Property 4: Timing measurement invariant
 * Property 5: Unavailable engine produces all-skipped results
 * Property 6: Error recording and continuation
 * Property 9: No STL output for non-success results
 * Property 10: Parse error detection and handling
 * Property 11: Error snippet truncation
 *
 * Validates: Requirements 4.1, 4.2, 4.4, 4.5, 5.4, 7.1, 7.2, 7.5
 */
class BenchmarkRunnerTest {

    // --- Mock Engines ---

    /** Engine that is always available and returns a simple mesh immediately. */
    class AlwaysAvailableEngine : ComputeEngine {
        var computeCallCount = 0
            private set

        override suspend fun compute(scene: SceneNode, progress: ProgressCallback?): Result<MeshResult> {
            computeCallCount++
            // A simple triangle mesh (1 triangle = 9 vertex floats)
            val vertices = floatArrayOf(
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f
            )
            val normals = floatArrayOf(
                0f, 0f, 1f,
                0f, 0f, 1f,
                0f, 0f, 1f
            )
            val colors = floatArrayOf(
                1f, 0f, 0f, 1f,
                1f, 0f, 0f, 1f,
                1f, 0f, 0f, 1f
            )
            return Result.success(MeshResult(vertices, normals, colors))
        }

        override fun cancel() {}
        override fun isAvailable(): Boolean = true
    }

    /** Engine that always throws an exception on compute. */
    class AlwaysFailingEngine : ComputeEngine {
        override suspend fun compute(scene: SceneNode, progress: ProgressCallback?): Result<MeshResult> {
            throw RuntimeException("Engine computation failed")
        }

        override fun cancel() {}
        override fun isAvailable(): Boolean = true
    }

    /** Engine that reports itself as unavailable. */
    class UnavailableEngine : ComputeEngine {
        var computeCallCount = 0
            private set

        override suspend fun compute(scene: SceneNode, progress: ProgressCallback?): Result<MeshResult> {
            computeCallCount++
            return Result.success(MeshResult.EMPTY)
        }

        override fun cancel() {}
        override fun isAvailable(): Boolean = false
    }

    /** Engine that delays forever to trigger timeout. Use with a very short timeoutMs. */
    class TimeoutEngine : ComputeEngine {
        override suspend fun compute(scene: SceneNode, progress: ProgressCallback?): Result<MeshResult> {
            delay(10_000L) // 10 seconds - will be cancelled by short timeout
            return Result.success(MeshResult.EMPTY)
        }

        override fun cancel() {}
        override fun isAvailable(): Boolean = true
    }

    // --- Property 4: Timing measurement invariant ---

    /**
     * Feature: engine-modularization-and-benchmarks, Property 4: Timing measurement invariant
     *
     * For any test case execution that completes, the recorded time must be non-negative.
     *
     * Validates: Requirements 4.1, 4.2
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 4: Timing measurement invariant")
    fun recordedTimeMustBeNonNegative(
        @ForAll("geometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val engine = AlwaysAvailableEngine()
            val runner = BenchmarkRunner(
                engines = listOf("test_engine" to engine),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            val results = runner.run()

            for (result in results) {
                assertTrue(result.timeMs >= 0,
                    "Recorded time must be non-negative, got ${result.timeMs} for test '${result.testCase.name}'")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 4: Timing measurement invariant
     *
     * For timeout results, time must equal the configured timeoutMs.
     *
     * Validates: Requirements 4.1, 4.2
     */
    @Property(tries = 20)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 4: Timing measurement invariant")
    fun timeoutResultTimeMustEqualTimeoutMs(
        @ForAll("singleGeometryTestCase") testCase: TestCase
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val timeoutMs = 100L
            val runner = BenchmarkRunner(
                engines = listOf("timeout_engine" to TimeoutEngine()),
                testCases = listOf(testCase),
                stlOutputDir = tempDir,
                timeoutMs = timeoutMs
            )

            val results = runner.run()
            val timeoutResults = results.filter { it.status == ResultStatus.TIMEOUT }

            assertTrue(timeoutResults.isNotEmpty(),
                "Expected at least one TIMEOUT result")
            for (result in timeoutResults) {
                assertEquals(timeoutMs, result.timeMs,
                    "Timeout result time must equal configured timeoutMs ($timeoutMs), got ${result.timeMs}")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // --- Property 5: Unavailable engine produces all-skipped results ---

    /**
     * Feature: engine-modularization-and-benchmarks, Property 5: Unavailable engine produces all-skipped results
     *
     * For any engine where isAvailable() returns false, all results for that engine must be SKIPPED.
     *
     * Validates: Requirements 4.4
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 5: Unavailable engine produces all-skipped results")
    fun unavailableEngineProducesAllSkippedResults(
        @ForAll("geometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val unavailableEngine = UnavailableEngine()
            val runner = BenchmarkRunner(
                engines = listOf("unavailable" to unavailableEngine),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            val results = runner.run()

            // All results for the unavailable engine must be SKIPPED
            val unavailableResults = results.filter { it.engineName == "unavailable" }
            for (result in unavailableResults) {
                assertEquals(ResultStatus.SKIPPED, result.status,
                    "Unavailable engine result status must be SKIPPED, got ${result.status} for '${result.testCase.name}'")
            }

            // Engine compute should never be called
            assertEquals(0, unavailableEngine.computeCallCount,
                "Unavailable engine compute() should not be called")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 5: Unavailable engine produces all-skipped results
     *
     * With mixed engines (available + unavailable), the unavailable one gets SKIPPED for all test cases.
     *
     * Validates: Requirements 4.4
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 5: Unavailable engine produces all-skipped results")
    fun mixedEnginesUnavailableAlwaysSkipped(
        @ForAll("geometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val availableEngine = AlwaysAvailableEngine()
            val unavailableEngine = UnavailableEngine()
            val runner = BenchmarkRunner(
                engines = listOf(
                    "available" to availableEngine,
                    "unavailable" to unavailableEngine
                ),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            val results = runner.run()

            val unavailableResults = results.filter { it.engineName == "unavailable" }
            for (result in unavailableResults) {
                assertEquals(ResultStatus.SKIPPED, result.status,
                    "Unavailable engine result must always be SKIPPED")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // --- Property 6: Error recording and continuation ---

    /**
     * Feature: engine-modularization-and-benchmarks, Property 6: Error recording and continuation
     *
     * For any sequence of test cases where one produces an error, the runner still produces results
     * for all test cases. Total result count = testCases × engines.
     *
     * Validates: Requirements 4.5
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 6: Error recording and continuation")
    fun errorDoesNotStopExecution(
        @ForAll("geometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val failingEngine = AlwaysFailingEngine()
            val runner = BenchmarkRunner(
                engines = listOf("failing" to failingEngine),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            val results = runner.run()

            // Must produce one result per test case per engine
            assertEquals(testCases.size, results.size,
                "Runner must produce results for ALL test cases even when errors occur. " +
                    "Expected ${testCases.size}, got ${results.size}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 6: Error recording and continuation
     *
     * With multiple engines (including a failing one), total results = testCases × engines.
     *
     * Validates: Requirements 4.5
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 6: Error recording and continuation")
    fun totalResultCountEqualsTestCasesTimesEngines(
        @ForAll("geometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val engines = listOf(
                "available" to AlwaysAvailableEngine() as ComputeEngine,
                "failing" to AlwaysFailingEngine() as ComputeEngine,
                "unavailable" to UnavailableEngine() as ComputeEngine
            )
            val runner = BenchmarkRunner(
                engines = engines,
                testCases = testCases,
                stlOutputDir = tempDir
            )

            val results = runner.run()

            // Total count should be testCases × total_engines (all engines including unavailable get entries)
            val expectedCount = testCases.size * engines.size
            assertEquals(expectedCount, results.size,
                "Total result count must be testCases (${testCases.size}) × engines (${engines.size}) = $expectedCount, got ${results.size}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // --- Property 9: No STL output for non-success results ---

    /**
     * Feature: engine-modularization-and-benchmarks, Property 9: No STL output for non-success results
     *
     * For any result with status TIMEOUT, COMPUTE_ERROR, PARSE_ERROR, or SKIPPED,
     * no STL file is written.
     *
     * Validates: Requirements 5.4
     */
    @Property(tries = 20)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 9: No STL output for non-success results")
    fun noStlForTimeoutResults(
        @ForAll("singleGeometryTestCase") testCase: TestCase
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val timeoutMs = 100L
            val runner = BenchmarkRunner(
                engines = listOf("timeout_engine" to TimeoutEngine()),
                testCases = listOf(testCase),
                stlOutputDir = tempDir,
                timeoutMs = timeoutMs
            )

            runner.run()

            // No STL files should be created for timeout results
            val stlFiles = tempDir.listFiles()?.filter { it.extension == "stl" } ?: emptyList()
            assertTrue(stlFiles.isEmpty(),
                "No STL files should be written for TIMEOUT results, found: ${stlFiles.map { it.name }}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 9: No STL output for non-success results
     *
     * For COMPUTE_ERROR results, no STL file is written.
     *
     * Validates: Requirements 5.4
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 9: No STL output for non-success results")
    fun noStlForComputeErrorResults(
        @ForAll("geometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val runner = BenchmarkRunner(
                engines = listOf("failing" to AlwaysFailingEngine()),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            runner.run()

            val stlFiles = tempDir.listFiles()?.filter { it.extension == "stl" } ?: emptyList()
            assertTrue(stlFiles.isEmpty(),
                "No STL files should be written for COMPUTE_ERROR results, found: ${stlFiles.map { it.name }}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 9: No STL output for non-success results
     *
     * For SKIPPED results (unavailable engine), no STL file is written.
     *
     * Validates: Requirements 5.4
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 9: No STL output for non-success results")
    fun noStlForSkippedResults(
        @ForAll("geometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val runner = BenchmarkRunner(
                engines = listOf("unavailable" to UnavailableEngine()),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            runner.run()

            val stlFiles = tempDir.listFiles()?.filter { it.extension == "stl" } ?: emptyList()
            assertTrue(stlFiles.isEmpty(),
                "No STL files should be written for SKIPPED results, found: ${stlFiles.map { it.name }}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 9: No STL output for non-success results
     *
     * For PARSE_ERROR results (no geometry), no STL file is written.
     *
     * Validates: Requirements 5.4
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 9: No STL output for non-success results")
    fun noStlForParseErrorResults(
        @ForAll("noGeometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val engine = AlwaysAvailableEngine()
            val runner = BenchmarkRunner(
                engines = listOf("test_engine" to engine),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            runner.run()

            val stlFiles = tempDir.listFiles()?.filter { it.extension == "stl" } ?: emptyList()
            assertTrue(stlFiles.isEmpty(),
                "No STL files should be written for PARSE_ERROR results, found: ${stlFiles.map { it.name }}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // --- Property 10: Parse error detection and handling ---

    /**
     * Feature: engine-modularization-and-benchmarks, Property 10: Parse error detection and handling
     *
     * For any non-empty OpenSCAD snippet that parses to a SceneNode tree with no geometry,
     * status = PARSE_ERROR, time = 0, engine compute not called.
     *
     * Validates: Requirements 7.1, 7.5
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 10: Parse error detection and handling")
    fun noGeometrySnippetProducesParseError(
        @ForAll("noGeometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val engine = AlwaysAvailableEngine()
            val runner = BenchmarkRunner(
                engines = listOf("test_engine" to engine),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            val results = runner.run()

            for (result in results) {
                assertEquals(ResultStatus.PARSE_ERROR, result.status,
                    "Non-geometry snippet must produce PARSE_ERROR, got ${result.status} for '${result.testCase.name}'")
                assertEquals(0L, result.timeMs,
                    "PARSE_ERROR result must have time = 0, got ${result.timeMs}")
            }

            // Engine should never be called for parse errors
            assertEquals(0, engine.computeCallCount,
                "Engine compute() should not be called for no-geometry snippets")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 10: Parse error detection and handling
     *
     * Parse error results should be produced for all engines when a snippet has no geometry.
     *
     * Validates: Requirements 7.1, 7.5
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 10: Parse error detection and handling")
    fun parseErrorRecordedForAllEngines(
        @ForAll("singleNoGeometryTestCase") testCase: TestCase
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val engine1 = AlwaysAvailableEngine()
            val engine2 = AlwaysAvailableEngine()
            val runner = BenchmarkRunner(
                engines = listOf(
                    "engine1" to engine1,
                    "engine2" to engine2
                ),
                testCases = listOf(testCase),
                stlOutputDir = tempDir
            )

            val results = runner.run()

            assertEquals(2, results.size,
                "Should produce one PARSE_ERROR result per engine")
            for (result in results) {
                assertEquals(ResultStatus.PARSE_ERROR, result.status)
                assertEquals(0L, result.timeMs)
            }
            assertEquals(0, engine1.computeCallCount)
            assertEquals(0, engine2.computeCallCount)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // --- Property 11: Error snippet truncation ---

    /**
     * Feature: engine-modularization-and-benchmarks, Property 11: Error snippet truncation
     *
     * For any OpenSCAD snippet that triggers a parse error, the error detail
     * is truncated to at most 256 characters.
     *
     * Validates: Requirements 7.2
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 11: Error snippet truncation")
    fun errorSnippetTruncatedTo256Chars(
        @ForAll("noGeometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val runner = BenchmarkRunner(
                engines = listOf("test_engine" to AlwaysAvailableEngine()),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            val results = runner.run()

            for (result in results) {
                if (result.status == ResultStatus.PARSE_ERROR && result.errorDetail != null) {
                    assertTrue(result.errorDetail!!.length <= 256,
                        "Error detail must be at most 256 characters, got ${result.errorDetail!!.length}")
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 11: Error snippet truncation
     *
     * For long snippets (> 256 chars) that produce parse errors, the error detail is exactly 256 chars.
     *
     * Validates: Requirements 7.2
     */
    @Property(tries = 100)
    @Tag("Feature: engine-modularization-and-benchmarks, Property 11: Error snippet truncation")
    fun longSnippetErrorDetailExactly256Chars(
        @ForAll("longNoGeometryTestCases") testCases: List<TestCase>
    ) {
        val tempDir = createTempDir("benchmark_test_")
        try {
            val runner = BenchmarkRunner(
                engines = listOf("test_engine" to AlwaysAvailableEngine()),
                testCases = testCases,
                stlOutputDir = tempDir
            )

            val results = runner.run()

            for (result in results) {
                if (result.status == ResultStatus.PARSE_ERROR && result.errorDetail != null) {
                    assertEquals(256, result.errorDetail!!.length,
                        "Error detail for long snippet must be exactly 256 characters, got ${result.errorDetail!!.length}")
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // --- Providers ---

    @Provide
    fun geometryTestCases(): Arbitrary<List<TestCase>> {
        return singleGeometryTestCase().list().ofMinSize(1).ofMaxSize(5)
    }

    @Provide
    fun singleGeometryTestCase(): Arbitrary<TestCase> {
        val categories = Arbitraries.of(TestCase.VALID_CATEGORIES.toList())
        val names = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('_')
            .ofMinLength(3)
            .ofMaxLength(20)
            .filter { it.matches(Regex("[a-z0-9_]+")) && it.isNotEmpty() }
        // Use codes that produce geometry nodes
        val codes = Arbitraries.of(
            "cube([1,1,1]);",
            "sphere(r=5);",
            "cylinder(h=10, r=3);",
            "translate([1,0,0]) cube([2,2,2]);",
            "union() { cube([1,1,1]); sphere(r=2); }",
            "difference() { cube([5,5,5]); sphere(r=3); }",
            "linear_extrude(height=5) square([2,2]);",
            "rotate([45,0,0]) cylinder(h=5, r=2);",
            "scale([2,1,1]) cube([1,1,1]);"
        )

        return Combinators.combine(names, categories, codes)
            .`as` { name, category, code -> TestCase(name = name, category = category, code = code) }
    }

    @Provide
    fun noGeometryTestCases(): Arbitrary<List<TestCase>> {
        return singleNoGeometryTestCase().list().ofMinSize(1).ofMaxSize(5)
    }

    @Provide
    fun singleNoGeometryTestCase(): Arbitrary<TestCase> {
        val categories = Arbitraries.of(TestCase.VALID_CATEGORIES.toList())
        val names = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('_')
            .ofMinLength(3)
            .ofMaxLength(20)
            .filter { it.matches(Regex("[a-z0-9_]+")) && it.isNotEmpty() }
        // Codes that parse but produce NO geometry (only variable assignments, comments, empty groups)
        val codes = Arbitraries.of(
            "x = 5;",
            "y = 10; z = x + y;",
            "// just a comment\nx = 1;",
            "a = 42; b = a * 2;",
            "val = sin(45);"
        )

        return Combinators.combine(names, categories, codes)
            .`as` { name, category, code -> TestCase(name = name, category = category, code = code) }
    }

    @Provide
    fun longNoGeometryTestCases(): Arbitrary<List<TestCase>> {
        val categories = Arbitraries.of(TestCase.VALID_CATEGORIES.toList())
        val names = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('_')
            .ofMinLength(3)
            .ofMaxLength(20)
            .filter { it.matches(Regex("[a-z0-9_]+")) && it.isNotEmpty() }
        // Long snippets (> 256 chars) that produce no geometry
        val codes = Arbitraries.integers().between(300, 500).map { length ->
            // Generate a long variable assignment chain that produces no geometry
            val sb = StringBuilder()
            var varCount = 0
            while (sb.length < length && sb.length < 2000) {
                sb.append("var${varCount} = ${varCount};\n")
                varCount++
            }
            sb.toString().take(length.coerceAtMost(2048))
        }

        return Combinators.combine(names, categories, codes)
            .`as` { name, category, code -> TestCase(name = name, category = category, code = code) }
            .list().ofMinSize(1).ofMaxSize(3)
    }

    private fun createTempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "$prefix${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }
}
