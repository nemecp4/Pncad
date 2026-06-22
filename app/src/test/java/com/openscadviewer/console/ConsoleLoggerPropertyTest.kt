package com.openscadviewer.console

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.NotEmpty
import net.jqwik.api.constraints.StringLength
import org.junit.jupiter.api.Assertions.*

/**
 * Property-based tests for ConsoleLogger.
 *
 * Validates: Requirements 1.3, 5.1, 5.2
 */
class ConsoleLoggerPropertyTest {

    /**
     * Property 1: Log entry ordering preservation
     *
     * For any sequence of log messages emitted to ConsoleLogger, the entries in allEntries
     * SHALL appear in the same order they were emitted, and the total count SHALL equal
     * the number of emissions (no entries lost or reordered).
     *
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 100)
    @Tag("Feature: render-console-view, Property 1: Log entry ordering preservation")
    fun logEntryOrderingPreservation(
        @ForAll("logMessageSequence") messages: List<Pair<LogSeverity, String>>
    ) {
        val logger = ConsoleLogger()

        // Emit all messages in order
        messages.forEach { (severity, message) ->
            logger.emit(severity, message)
        }

        val entries = logger.allEntries

        // Total count SHALL equal the number of emissions
        assertEquals(messages.size, entries.size,
            "Number of entries should equal number of emissions")

        // Entries SHALL appear in the same order they were emitted
        messages.forEachIndexed { index, (severity, message) ->
            assertEquals(severity, entries[index].severity,
                "Entry at index $index should have severity $severity")
            assertEquals(message, entries[index].message,
                "Entry at index $index should have message '$message'")
        }

        // Additionally verify timestamps are non-decreasing (ordering in time)
        for (i in 1 until entries.size) {
            assertTrue(entries[i].timestamp >= entries[i - 1].timestamp,
                "Timestamps should be non-decreasing")
        }
    }

    /**
     * Property 5: Session lifecycle clears previous and retains current
     *
     * For any non-empty list of log entries from a previous session, calling startSession()
     * SHALL result in allEntries containing only the session-start message (previous entries
     * are cleared). Furthermore, for any sequence of entries emitted after startSession(),
     * all SHALL be present in allEntries until the next startSession() call.
     *
     * **Validates: Requirements 5.1, 5.2**
     */
    @Property(tries = 100)
    @Tag("Feature: render-console-view, Property 5: Session lifecycle clears previous and retains current")
    fun sessionLifecycleClearsPreviousAndRetainsCurrent(
        @ForAll("logMessageSequence") @NotEmpty previousMessages: List<Pair<LogSeverity, String>>,
        @ForAll("logMessageSequence") currentMessages: List<Pair<LogSeverity, String>>
    ) {
        val logger = ConsoleLogger()

        // Emit previous session entries
        previousMessages.forEach { (severity, message) ->
            logger.emit(severity, message)
        }
        assertEquals(previousMessages.size, logger.allEntries.size)

        // Start a new session - previous entries SHALL be cleared
        logger.startSession()

        // allEntries SHALL contain only the session-start message
        assertEquals(1, logger.allEntries.size,
            "After startSession(), allEntries should contain only the session-start message")
        assertEquals("Session started", logger.allEntries[0].message,
            "The sole entry after startSession() should be 'Session started'")
        assertEquals(LogSeverity.INFO, logger.allEntries[0].severity,
            "Session started message should have INFO severity")

        // Emit current session entries
        currentMessages.forEach { (severity, message) ->
            logger.emit(severity, message)
        }

        // All current entries SHALL be present in allEntries (plus the session-start message)
        val expectedCount = 1 + currentMessages.size  // session-start + current messages
        assertEquals(expectedCount, logger.allEntries.size,
            "allEntries should contain session-start plus all current messages")

        // Verify current messages are retained in order after session-start
        currentMessages.forEachIndexed { index, (severity, message) ->
            val entryIndex = index + 1  // offset by session-start message
            assertEquals(severity, logger.allEntries[entryIndex].severity,
                "Current entry at index $index should have correct severity")
            assertEquals(message, logger.allEntries[entryIndex].message,
                "Current entry at index $index should have correct message")
        }

        // Verify entries persist until next startSession() call
        val snapshotBeforeNextSession = logger.allEntries.toList()
        assertEquals(expectedCount, snapshotBeforeNextSession.size,
            "Entries should persist until next startSession()")

        // Call startSession() again - current entries SHALL be cleared
        logger.startSession()
        assertEquals(1, logger.allEntries.size,
            "After second startSession(), only session-start message should remain")
        assertEquals("Session started", logger.allEntries[0].message)
    }

    // --- Custom Generators ---

    @Provide
    fun logMessageSequence(): Arbitrary<List<Pair<LogSeverity, String>>> {
        val severityArb = Arbitraries.of(LogSeverity.INFO, LogSeverity.WARN, LogSeverity.ERROR)
        val messageArb = Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(100)
        val pairArb = Combinators.combine(severityArb, messageArb).`as` { s, m -> Pair(s, m) }
        return pairArb.list().ofMinSize(0).ofMaxSize(50)
    }
}
