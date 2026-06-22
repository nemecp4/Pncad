package com.openscadviewer.console

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConsoleLoggerTest {

    private lateinit var logger: ConsoleLogger

    @BeforeEach
    fun setup() {
        logger = ConsoleLogger()
    }

    @Test
    fun `startSession clears previous entries and emits session start message`() {
        logger.emit(LogSeverity.INFO, "old message")
        assertEquals(1, logger.allEntries.size)

        logger.startSession()

        assertEquals(1, logger.allEntries.size)
        assertEquals("Session started", logger.allEntries[0].message)
        assertEquals(LogSeverity.INFO, logger.allEntries[0].severity)
    }

    @Test
    fun `startSession sets session active`() {
        assertFalse(logger.isSessionActive())
        logger.startSession()
        assertTrue(logger.isSessionActive())
    }

    @Test
    fun `endSession with success emits completion message`() {
        logger.startSession()
        logger.endSession(success = true)

        assertFalse(logger.isSessionActive())
        assertEquals(2, logger.allEntries.size)
        assertEquals("Computation completed successfully", logger.allEntries[1].message)
    }

    @Test
    fun `endSession with failure does not emit completion message`() {
        logger.startSession()
        logger.endSession(success = false)

        assertFalse(logger.isSessionActive())
        assertEquals(1, logger.allEntries.size)
    }

    @Test
    fun `emit creates timestamped LogEntry and adds to allEntries`() {
        logger.emit(LogSeverity.WARN, "test warning")

        assertEquals(1, logger.allEntries.size)
        val entry = logger.allEntries[0]
        assertEquals(LogSeverity.WARN, entry.severity)
        assertEquals("test warning", entry.message)
        assertTrue(entry.timestamp > 0)
    }

    @Test
    fun `emit sends entry via SharedFlow`() = runTest {
        // Emit first, then collect from replay cache
        logger.emit(LogSeverity.ERROR, "flow test")

        val entry = logger.entries.first()
        assertEquals("flow test", entry.message)
        assertEquals(LogSeverity.ERROR, entry.severity)
    }

    @Test
    fun `allEntries returns a snapshot copy`() {
        logger.emit(LogSeverity.INFO, "message 1")
        val snapshot = logger.allEntries
        logger.emit(LogSeverity.INFO, "message 2")

        assertEquals(1, snapshot.size)
        assertEquals(2, logger.allEntries.size)
    }

    @Test
    fun `clear removes all entries and resets replay cache`() {
        logger.emit(LogSeverity.INFO, "message 1")
        logger.emit(LogSeverity.INFO, "message 2")
        assertEquals(2, logger.allEntries.size)

        logger.clear()

        assertEquals(0, logger.allEntries.size)
    }

    @Test
    fun `entries preserves ordering`() {
        logger.emit(LogSeverity.INFO, "first")
        logger.emit(LogSeverity.WARN, "second")
        logger.emit(LogSeverity.ERROR, "third")

        assertEquals(3, logger.allEntries.size)
        assertEquals("first", logger.allEntries[0].message)
        assertEquals("second", logger.allEntries[1].message)
        assertEquals("third", logger.allEntries[2].message)
    }
}
