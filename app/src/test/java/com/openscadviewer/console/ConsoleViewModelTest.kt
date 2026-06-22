package com.openscadviewer.console

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModelTest {

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
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
    }

    @Test
    fun `startSession sets isVisible to true`() {
        assertEquals(false, viewModel.isVisible.value)

        viewModel.startSession()

        assertEquals(true, viewModel.isVisible.value)
    }

    @Test
    fun `startSession clears previous entries via logger`() {
        viewModel.logger.emit(LogSeverity.INFO, "old entry")
        assertEquals(1, viewModel.logger.allEntries.size)

        viewModel.startSession()

        // After startSession, allEntries should only contain the "Session started" message
        assertEquals(1, viewModel.logger.allEntries.size)
        assertEquals("Session started", viewModel.logger.allEntries[0].message)
    }

    @Test
    fun `startSession resets autoScroll to true`() {
        viewModel.setAutoScroll(false)
        assertEquals(false, viewModel.autoScroll.value)

        viewModel.startSession()

        assertEquals(true, viewModel.autoScroll.value)
    }

    @Test
    fun `endSession with success keeps console visible`() {
        viewModel.startSession()
        assertEquals(true, viewModel.isVisible.value)

        viewModel.endSession(success = true)

        // Console stays visible (delayed hiding is handled by the UI layer)
        assertEquals(true, viewModel.isVisible.value)
    }

    @Test
    fun `endSession with failure keeps console visible permanently`() {
        viewModel.startSession()
        assertEquals(true, viewModel.isVisible.value)

        viewModel.endSession(success = false)

        // Console stays visible permanently on failure
        assertEquals(true, viewModel.isVisible.value)
    }

    @Test
    fun `hide sets isVisible to false`() {
        viewModel.startSession()
        assertEquals(true, viewModel.isVisible.value)

        viewModel.hide()

        assertEquals(false, viewModel.isVisible.value)
    }

    @Test
    fun `setAutoScroll sets value to true`() {
        viewModel.setAutoScroll(false)
        assertEquals(false, viewModel.autoScroll.value)

        viewModel.setAutoScroll(true)

        assertEquals(true, viewModel.autoScroll.value)
    }

    @Test
    fun `setAutoScroll sets value to false`() {
        assertEquals(true, viewModel.autoScroll.value)

        viewModel.setAutoScroll(false)

        assertEquals(false, viewModel.autoScroll.value)
    }

    @Test
    fun `setAutoScroll toggles correctly between states`() {
        assertEquals(true, viewModel.autoScroll.value)

        viewModel.setAutoScroll(false)
        assertEquals(false, viewModel.autoScroll.value)

        viewModel.setAutoScroll(true)
        assertEquals(true, viewModel.autoScroll.value)

        viewModel.setAutoScroll(false)
        assertEquals(false, viewModel.autoScroll.value)
    }
}
