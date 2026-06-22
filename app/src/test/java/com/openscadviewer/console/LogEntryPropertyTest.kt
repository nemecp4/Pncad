package com.openscadviewer.console

import net.jqwik.api.*
import net.jqwik.api.constraints.LongRange

/**
 * Property 2: Timestamp format validity
 *
 * For any LogEntry created with any valid System.currentTimeMillis() value,
 * calling formattedTimestamp() SHALL produce a string matching the pattern
 * \d{2}:\d{2}:\d{2}\.\d{3} (HH:mm:ss.SSS format).
 *
 * **Validates: Requirements 3.1**
 */
@Tag("Feature: render-console-view, Property 2: Timestamp format validity")
class LogEntryPropertyTest {

    private val timestampPattern = Regex("""\d{2}:\d{2}:\d{2}\.\d{3}""")

    @Property(tries = 100)
    fun formattedTimestampMatchesHHmmssSSS(
        @ForAll("plausibleTimestamps") timestamp: Long,
        @ForAll("severities") severity: LogSeverity,
        @ForAll("messages") message: String
    ) {
        val entry = LogEntry(
            timestamp = timestamp,
            severity = severity,
            message = message
        )

        val formatted = entry.formattedTimestamp()

        assert(timestampPattern.matches(formatted)) {
            "Expected formattedTimestamp() to match HH:mm:ss.SSS pattern, " +
                "but got '$formatted' for timestamp=$timestamp"
        }
    }

    @Provide
    fun plausibleTimestamps(): Arbitrary<Long> {
        // Range covers from epoch (0) to a far-future date (~year 2100)
        // This exercises all possible hour/minute/second/millisecond combinations
        return Arbitraries.longs().between(0L, 4_102_444_800_000L)
    }

    @Provide
    fun severities(): Arbitrary<LogSeverity> {
        return Arbitraries.of(*LogSeverity.values())
    }

    @Provide
    fun messages(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
    }
}
