package com.openscadviewer.benchmark

data class BenchmarkResult(
    val testCase: TestCase,
    val engineName: String,
    val timeMs: Long,
    val status: ResultStatus,
    val errorDetail: String? = null,
    val generatedTriangles: Int = -1,
    val generatedFileSize: Long = -1,
    val expectedTriangles: Int = -1,
    val expectedFileSize: Long = -1
)

enum class ResultStatus {
    SUCCESS,
    PARSE_ERROR,
    COMPUTE_ERROR,
    COMPARISON_FAILED,
    TIMEOUT,
    SKIPPED
}
