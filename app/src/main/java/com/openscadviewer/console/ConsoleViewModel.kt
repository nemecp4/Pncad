package com.openscadviewer.console

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConsoleViewModel : ViewModel() {
    val logger = ConsoleLogger()
    val resourceMonitor = ResourceMonitor(logger)

    private val _logEntries = MutableLiveData<List<LogEntry>>(emptyList())
    val logEntries: LiveData<List<LogEntry>> = _logEntries

    private val _isVisible = MutableLiveData(false)
    val isVisible: LiveData<Boolean> = _isVisible

    private val _autoScroll = MutableLiveData(true)
    val autoScroll: LiveData<Boolean> = _autoScroll

    init {
        viewModelScope.launch {
            logger.entries.collectLatest {
                _logEntries.postValue(logger.allEntries)
            }
        }
    }

    fun startSession() {
        logger.startSession()
        resourceMonitor.start(viewModelScope)
        _isVisible.value = true
        _autoScroll.value = true
    }

    fun endSession(success: Boolean) {
        resourceMonitor.stop()
        logger.endSession(success)
    }

    fun hide() {
        _isVisible.value = false
    }

    /**
     * Makes the console visible without starting a new compute session, so the
     * user can review the log (e.g. via the Console button).
     */
    fun show() {
        _isVisible.value = true
    }

    /**
     * Toggles console visibility for the manual Console button.
     */
    fun toggleVisibility() {
        _isVisible.value = _isVisible.value != true
    }

    fun setAutoScroll(enabled: Boolean) {
        _autoScroll.value = enabled
    }

    fun createProgressCallback(): com.openscadviewer.engine.ProgressCallback {
        return com.openscadviewer.engine.ProgressCallback { message, severity ->
            val logSeverity = when (severity.uppercase()) {
                "WARN" -> LogSeverity.WARN
                "ERROR" -> LogSeverity.ERROR
                else -> LogSeverity.INFO
            }
            logger.emit(logSeverity, message)
        }
    }
}
