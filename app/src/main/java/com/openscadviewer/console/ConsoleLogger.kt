package com.openscadviewer.console

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Collects log messages from computation pipeline and emits them for display.
 * Thread-safe: can be called from any coroutine context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleLogger {
    private val _entries = MutableSharedFlow<LogEntry>(
        replay = 100,
        extraBufferCapacity = 50
    )
    val entries: SharedFlow<LogEntry> = _entries.asSharedFlow()

    private val _allEntries = mutableListOf<LogEntry>()
    val allEntries: List<LogEntry> get() = _allEntries.toList()

    private var sessionActive = false

    fun startSession() {
        _allEntries.clear()
        _entries.resetReplayCache()
        sessionActive = true
        emit(LogSeverity.INFO, "Session started")
    }

    fun endSession(success: Boolean) {
        sessionActive = false
        if (success) {
            emit(LogSeverity.INFO, "Computation completed successfully")
        }
    }

    fun emit(severity: LogSeverity, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            severity = severity,
            message = message
        )
        _allEntries.add(entry)
        _entries.tryEmit(entry)
    }

    fun isSessionActive(): Boolean = sessionActive

    fun clear() {
        _allEntries.clear()
        _entries.resetReplayCache()
    }
}
