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
 *    c. On success → write STL, then compare against reference if available
 *    d. On timeout → TIMEOUT with time = 120000
 *    e. On error → COMPUTE_ERROR
 * 4. Continue to next test case regardless of errors
 */
class BenchmarkRunner(
    private val engines: List<Pair<String, ComputeEngine>>,
    private val testCases: List<TestCase>,
    private val stlOutputDir: File,
    private val timeoutMs: Long = 120_000L,
    private val tolerance: Double = 0.15
) {
    private val parser = OpenSCADParser()
    private val stlWriter = StlOutputWriter(stlOutputDir)

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
                BenchmarkResult(
                    testCase = testCase,
                    engineName = engineName,
                    timeMs = timeoutMs,
                    status = ResultStatus.TIMEOUT
                )
            } else {
                meshResult.fold(
                    onSuccess = { mesh ->
                        // Write STL
                        val prelimResult = BenchmarkResult(
                            testCase = testCase,
                            engineName = engineName,
                            timeMs = elapsed,
                            status = ResultStatus.SUCCESS
                        )
                        stlWriter.write(prelimResult, mesh)

                        // Compare against reference STL if available (only for CGAL engine)
                        if (engineName == "cgal") {
                            validateAgainstReference(testCase, engineName, elapsed)
                        } else {
                            // Non-CGAL engines: return SUCCESS with generated stats
                            // and expected stats for display purposes (no assertion)
                            val stlFilename = StlFileNamer.generateFilename(testCase.category, testCase.name)
                            val generatedFile = File(File(stlOutputDir, engineName), stlFilename)
                            val genTriangles = if (generatedFile.exists()) StlComparator.readTriangleCount(generatedFile) ?: 0 else 0
                            val genFileSize = if (generatedFile.exists()) generatedFile.length() else 0L

                            // Read expected stats if reference exists (for display only)
                            val refFile = testCase.expectedStlPath?.let { resolveExpectedStl(it) }
                            val expTriangles = refFile?.let { StlComparator.readTriangleCount(it) } ?: -1
                            val expFileSize = refFile?.length() ?: -1L

                            BenchmarkResult(
                                testCase = testCase,
                                engineName = engineName,
                                timeMs = elapsed,
                                status = ResultStatus.SUCCESS,
                                generatedTriangles = genTriangles,
                                generatedFileSize = genFileSize,
                                expectedTriangles = expTriangles,
                                expectedFileSize = expFileSize
                            )
                        }
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

    /**
     * After writing the STL, compare it against the reference.
     * Sets COMPARISON_FAILED if triangle count or file size exceeds tolerance.
     * Returns SUCCESS with stats populated if no reference or comparison passes.
     */
    private fun validateAgainstReference(
        testCase: TestCase,
        engineName: String,
        elapsed: Long
    ): BenchmarkResult {
        val stlFilename = StlFileNamer.generateFilename(testCase.category, testCase.name)
        val generatedFile = File(File(stlOutputDir, engineName), stlFilename)

        val genTriangles = if (generatedFile.exists()) StlComparator.readTriangleCount(generatedFile) ?: 0 else 0
        val genFileSize = if (generatedFile.exists()) generatedFile.length() else 0L

        // No reference → SUCCESS with generated stats only
        val expectedPath = testCase.expectedStlPath
            ?: return BenchmarkResult(
                testCase = testCase,
                engineName = engineName,
                timeMs = elapsed,
                status = ResultStatus.SUCCESS,
                generatedTriangles = genTriangles,
                generatedFileSize = genFileSize
            )

        // Resolve reference file
        val referenceFile = resolveExpectedStl(expectedPath)
        if (referenceFile == null || !referenceFile.exists()) {
            return BenchmarkResult(
                testCase = testCase,
                engineName = engineName,
                timeMs = elapsed,
                status = ResultStatus.SUCCESS,
                errorDetail = "reference not found: $expectedPath",
                generatedTriangles = genTriangles,
                generatedFileSize = genFileSize
            )
        }

        val expTriangles = StlComparator.readTriangleCount(referenceFile) ?: 0
        val expFileSize = referenceFile.length()

        // Compare triangle count
        val triangleDiffPercent = if (expTriangles > 0)
            kotlin.math.abs((genTriangles - expTriangles).toDouble() / expTriangles * 100.0) else 0.0
        val sizeDiffPercent = if (expFileSize > 0)
            kotlin.math.abs((genFileSize - expFileSize).toDouble() / expFileSize * 100.0) else 0.0

        val triangleOk = expTriangles == 0 || triangleDiffPercent <= tolerance * 100.0
        val sizeOk = expFileSize == 0L || sizeDiffPercent <= tolerance * 100.0

        return if (triangleOk && sizeOk) {
            BenchmarkResult(
                testCase = testCase,
                engineName = engineName,
                timeMs = elapsed,
                status = ResultStatus.SUCCESS,
                generatedTriangles = genTriangles,
                generatedFileSize = genFileSize,
                expectedTriangles = expTriangles,
                expectedFileSize = expFileSize
            )
        } else {
            val details = mutableListOf<String>()
            if (!triangleOk) details.add("triangles $genTriangles vs $expTriangles (${"%.1f".format(triangleDiffPercent)}%)")
            if (!sizeOk) details.add("size $genFileSize vs $expFileSize (${"%.1f".format(sizeDiffPercent)}%)")
            BenchmarkResult(
                testCase = testCase,
                engineName = engineName,
                timeMs = elapsed,
                status = ResultStatus.COMPARISON_FAILED,
                errorDetail = details.joinToString("; "),
                generatedTriangles = genTriangles,
                generatedFileSize = genFileSize,
                expectedTriangles = expTriangles,
                expectedFileSize = expFileSize
            )
        }
    }

    private fun truncateSnippet(code: String): String {
        return if (code.length > 256) code.substring(0, 256) else code
    }

    /**
     * Resolves an expectedStlPath to a File.
     * Tries classpath first, then filesystem relative paths.
     */
    private fun resolveExpectedStl(path: String): File? {
        val url = javaClass.classLoader.getResource(path)
        if (url != null) return File(url.toURI())
        val f1 = File("benchmark/src/test/resources/$path")
        if (f1.exists()) return f1
        val f2 = File("src/test/resources/$path")
        if (f2.exists()) return f2
        val f3 = File(path)
        if (f3.exists()) return f3
        return null
    }
}
