package com.openscadviewer.benchmark

data class BenchmarkResult(
    val testCase: TestCase,
    val engineName: String,
    val timeMs: Long,
    val status: ResultStatus,
    val errorDetail: String? = null
)

enum class ResultStatus {
    SUCCESS,
    PARSE_ERROR,
    COMPUTE_ERROR,
    TIMEOUT,
    SKIPPED
}
