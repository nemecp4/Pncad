package com.openscadviewer.console

data class LogEntry(
    val timestamp: Long,
    val severity: LogSeverity,
    val message: String
) {
    fun formattedTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
        return sdf.format(java.util.Date(timestamp))
    }
}
