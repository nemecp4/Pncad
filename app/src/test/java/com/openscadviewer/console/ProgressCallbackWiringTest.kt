package com.openscadviewer.console

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressCallbackWiringTest {

    private lateinit var viewModel: ConsoleViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        viewModel = ConsoleViewModel()
    }

    @AfterEach
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
    }

    @Test
    fun `createProgressCallback maps INFO severity to LogSeverity INFO`() {
        val callback = viewModel.createProgressCallback()

        callback.onProgress("info message", "INFO")

        val entries = viewModel.logger.allEntries
        assertEquals(1, entries.size)
        assertEquals(LogSeverity.INFO, entries[0].severity)
        assertEquals("info message", entries[0].message)
    }

    @Test
    fun `createProgressCallback maps WARN severity to LogSeverity WARN`() {
        val callback = viewModel.createProgressCallback()

        callback.onProgress("warning message", "WARN")

        val entries = viewModel.logger.allEntries
        assertEquals(1, entries.size)
        assertEquals(LogSeverity.WARN, entries[0].severity)
        assertEquals("warning message", entries[0].message)
    }

    @Test
    fun `createProgressCallback maps ERROR severity to LogSeverity ERROR`() {
        val callback = viewModel.createProgressCallback()

        callback.onProgress("error message", "ERROR")

        val entries = viewModel.logger.allEntries
        assertEquals(1, entries.size)
        assertEquals(LogSeverity.ERROR, entries[0].severity)
        assertEquals("error message", entries[0].message)
    }

    @Test
    fun `createProgressCallback maps lowercase severity to correct LogSeverity`() {
        val callback = viewModel.createProgressCallback()

        callback.onProgress("warn lower", "warn")
        callback.onProgress("error lower", "error")
        callback.onProgress("info lower", "info")

        val entries = viewModel.logger.allEntries
        assertEquals(3, entries.size)
        assertEquals(LogSeverity.WARN, entries[0].severity)
        assertEquals(LogSeverity.ERROR, entries[1].severity)
        assertEquals(LogSeverity.INFO, entries[2].severity)
    }

    @Test
    fun `createProgressCallback maps unknown severity to LogSeverity INFO`() {
        val callback = viewModel.createProgressCallback()

        callback.onProgress("debug message", "DEBUG")
        callback.onProgress("trace message", "TRACE")
        callback.onProgress("unknown message", "SOMETHING")

        val entries = viewModel.logger.allEntries
        assertEquals(3, entries.size)
        entries.forEach { entry ->
            assertEquals(LogSeverity.INFO, entry.severity)
        }
    }

    @Test
    fun `progress messages flow from callback to logger in order`() {
        val callback = viewModel.createProgressCallback()

        callback.onProgress("Parsing OpenSCAD source...", "INFO")
        callback.onProgress("Parsed 5 scene nodes", "INFO")
        callback.onProgress("Computing mesh with Kotlin engine...", "INFO")
        callback.onProgress("Mesh generated: 120 triangles in 45ms", "INFO")

        val entries = viewModel.logger.allEntries
        assertEquals(4, entries.size)
        assertEquals("Parsing OpenSCAD source...", entries[0].message)
        assertEquals("Parsed 5 scene nodes", entries[1].message)
        assertEquals("Computing mesh with Kotlin engine...", entries[2].message)
        assertEquals("Mesh generated: 120 triangles in 45ms", entries[3].message)
    }

    @Test
    fun `progress messages with mixed severities flow correctly`() {
        val callback = viewModel.createProgressCallback()

        callback.onProgress("Starting computation", "INFO")
        callback.onProgress("Low memory detected", "WARN")
        callback.onProgress("Computation failed", "ERROR")

        val entries = viewModel.logger.allEntries
        assertEquals(3, entries.size)
        assertEquals(LogSeverity.INFO, entries[0].severity)
        assertEquals("Starting computation", entries[0].message)
        assertEquals(LogSeverity.WARN, entries[1].severity)
        assertEquals("Low memory detected", entries[1].message)
        assertEquals(LogSeverity.ERROR, entries[2].severity)
        assertEquals("Computation failed", entries[2].message)
    }
}
