package com.openscadviewer.engine

/**
 * Callback for engines to report computation progress.
 * Invoked on background threads; implementations must handle thread-safety.
 */
fun interface ProgressCallback {
    fun onProgress(message: String, severity: String)
}

/**
 * Convenience extension to call onProgress with default INFO severity.
 */
fun ProgressCallback.onProgress(message: String) {
    onProgress(message, "INFO")
}
