package com.openscadviewer.benchmark

import com.openscadviewer.engine.ComputeEngine
import com.openscadviewer.engine.MeshResult
import com.openscadviewer.parser.OpenSCADParser
import com.openscadviewer.parser.containsGeometry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Orchestrates benchmark execution across multiple engines and test cases.
 *
 * Execution flow per test case:
 * 1. Parse the OpenSCAD snippet
 * 2. Check if the scene contains geometry-producing nodes
 *    - If not → PARSE_ERROR, time = 0, skip engine calls
 * 3. For each engine (sequentially):
 *    a. Check isAvailable() — if false → SKIPPED
 *    b. Call engine.compute() with coroutine timeout
 *    c. On success → write STL via StlOutputWriter
 *    d. On timeout → TIMEOUT with time = 120000
 *    e. On error → COMPUTE_ERROR
 * 4. Continue to next test case regardless of errors
 */
class BenchmarkRunner(
    private val engines: List<Pair<String, ComputeEngine>>,
    private val testCases: List<TestCase>,
    private val stlOutputDir: File,
    private val timeoutMs: Long = 120_000L
) {
    private val parser = OpenSCADParser()
    private val stlWriter = StlOutputWriter(stlOutputDir)

    /**
     * Returns the directory containing reference STL files.
     * Looks for expected_results/ as a classpath resource first, then falls back
     * to the source tree location (src/test/expected_results/).
     */
    private fun getReferenceDir(): File? {
        // Try classpath first (works if expected_results is in src/test/resources/)
        val url = javaClass.classLoader.getResource("expected_results")
        if (url != null) return File(url.toURI())

        // Fallback: look relative to project root (src/test/expected_results/)
        val fallback = File("benchmark/src/test/expected_results")
        if (fallback.exists()) return fallback

        // Also try without module prefix (when CWD is already the benchmark module)
        val fallback2 = File("src/test/expected_results")
        if (fallback2.exists()) return fallback2

        return null
    }

    fun run(): List<BenchmarkResult> {
        val results = mutableListOf<BenchmarkResult>()

        // Pre-check engine availability
        val engineAvailability = engines.map { (name, engine) ->
            Triple(name, engine, engine.isAvailable())
        }

        for (testCase in testCases) {
            // Step 1: Parse the OpenSCAD snippet
            val sceneNode = try {
                parser.parse(testCase.code)
            } catch (e: Exception) {
                // Parser threw an exception — record PARSE_ERROR for all engines
                val errorSnippet = truncateSnippet(testCase.code)
                for ((engineName, _, _) in engineAvailability) {
                    results.add(
                        BenchmarkResult(
                            testCase = testCase,
                            engineName = engineName,
                            timeMs = 0L,
                            status = ResultStatus.PARSE_ERROR,
                            errorDetail = errorSnippet
                        )
                    )
                }
                continue
            }

            // Step 2: Check if scene contains geometry-producing nodes
            if (!sceneNode.containsGeometry()) {
                val errorSnippet = truncateSnippet(testCase.code)
                for ((engineName, _, _) in engineAvailability) {
                    results.add(
                        BenchmarkResult(
                            testCase = testCase,
                            engineName = engineName,
                            timeMs = 0L,
                            status = ResultStatus.PARSE_ERROR,
                            errorDetail = errorSnippet
                        )
                    )
                }
                continue
            }

            // Step 3: Run each engine sequentially
            for ((engineName, engine, available) in engineAvailability) {
                if (!available) {
                    // Unavailable engine → SKIPPED
                    results.add(
                        BenchmarkResult(
                            testCase = testCase,
                            engineName = engineName,
                            timeMs = 0L,
                            status = ResultStatus.SKIPPED
                        )
                    )
                    continue
                }

                // Execute with timeout (STL writing is handled inside on success)
                val result = executeWithTimeout(testCase, engineName, engine)
                results.add(result)
            }
        }

        return results
    }

    private fun executeWithTimeout(
        testCase: TestCase,
        engineName: String,
        engine: ComputeEngine
    ): BenchmarkResult {
        val sceneNode = parser.parse(testCase.code)
        val startTime = System.currentTimeMillis()

        return try {
            val meshResult = runBlocking {
                withTimeoutOrNull(timeoutMs) {
                    engine.compute(sceneNode)
                }
            }

            val elapsed = System.currentTimeMillis() - startTime

            if (meshResult == null) {
                // Timeout occurred
                BenchmarkResult(
                    testCase = testCase,
                    engineName = engineName,
                    timeMs = timeoutMs,
                    status = ResultStatus.TIMEOUT
                )
            } else {
                // Got a result — check if it was successful
                meshResult.fold(
                    onSuccess = { mesh ->
                        val result = BenchmarkResult(
                            testCase = testCase,
                            engineName = engineName,
                            timeMs = elapsed,
                            status = ResultStatus.SUCCESS
                        )
                        // Write STL on success
                        stlWriter.write(result, mesh)
                        result
                    },
                    onFailure = { error ->
                        BenchmarkResult(
                            testCase = testCase,
                            engineName = engineName,
                            timeMs = elapsed,
                            status = ResultStatus.COMPUTE_ERROR,
                            errorDetail = error.message
                        )
                    }
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            BenchmarkResult(
                testCase = testCase,
                engineName = engineName,
                timeMs = elapsed,
                status = ResultStatus.COMPUTE_ERROR,
                errorDetail = e.message
            )
        }
    }

    private fun truncateSnippet(code: String): String {
        return if (code.length > 256) {
            code.substring(0, 256)
        } else {
            code
        }
    }

    /**
     * Compares generated STL files against reference STL files in expected_results/.
     * Only compares test cases in the "custom" category (which always have a reference).
     *
     * Generated files use the full filename (category_name.stl) but reference files
     * use just the test name (name.stl).
     *
     * @throws AssertionError if the reference directory is missing or a reference file
     *         cannot be found for a successful custom test case.
     */
    fun compareWithReferences(results: List<BenchmarkResult>): List<StlComparator.ComparisonResult> {
        // Only compare CGAL engine output against references since the Kotlin engine
        // does not perform CSG boolean operations (difference/intersection) and will
        // produce more geometry than the reference STLs generated by OpenSCAD.
        val customSuccesses = results.filter {
            it.status == ResultStatus.SUCCESS && it.testCase.category == "custom" && it.engineName == "cgal"
        }
        if (customSuccesses.isEmpty()) return emptyList()

        val referenceDir = getReferenceDir()
            ?: error("Reference directory 'expected_results' not found. Cannot compare custom test cases.")

        val comparisons = mutableListOf<StlComparator.ComparisonResult>()
        val missing = mutableListOf<String>()

        for (result in customSuccesses) {
            val stlFilename = StlFileNamer.generateFilename(result.testCase.category, result.testCase.name)
            val generatedFile = File(File(stlOutputDir, result.engineName), stlFilename)
            // Reference files use just the test name without category prefix
            val referenceFilename = "${result.testCase.name}.stl"
            val referenceFile = File(referenceDir, referenceFilename)

            if (!referenceFile.exists()) {
                missing.add("${result.engineName}/${result.testCase.name}: reference not found at ${referenceFile.path}")
                continue
            }

            val comparison = StlComparator.compare(generatedFile, referenceFile, "${result.testCase.name} (${result.engineName})")
            if (comparison != null) {
                comparisons.add(comparison)
            } else {
                missing.add("${result.engineName}/${result.testCase.name}: generated file missing or unreadable at ${generatedFile.path}")
            }
        }

        if (missing.isNotEmpty()) {
            error("STL comparison could not run for ${missing.size} case(s):\n${missing.joinToString("\n")}")
        }

        return comparisons
    }
}
