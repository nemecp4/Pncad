package com.openscadviewer.engine

/**
 * Error descriptor returned when a compute engine fails.
 */
data class ComputeError(
    val category: ErrorCategory,
    val message: String
)

/**
 * Categories of errors that can occur during mesh computation.
 */
enum class ErrorCategory {
    INVALID_INPUT,
    COMPUTATION_FAILURE,
    OUT_OF_MEMORY,
    TIMEOUT,
    CANCELLED
}

/**
 * Exception wrapping a ComputeError for use with Result.failure().
 */
class ComputeException(val error: ComputeError) : Exception(error.message)
