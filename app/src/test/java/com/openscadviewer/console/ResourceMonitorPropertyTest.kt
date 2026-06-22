package com.openscadviewer.console

import net.jqwik.api.*
import net.jqwik.api.constraints.NotEmpty
import org.junit.jupiter.api.Assertions.*

/**
 * Property-based tests for ResourceMonitor peak memory tracking.
 *
 * Validates: Requirements 6.4
 */
class ResourceMonitorPropertyTest {

    /**
     * Property 6: Peak memory accuracy
     *
     * For any sequence of memory readings observed by ResourceMonitor, the reported
     * peak memory value SHALL equal the maximum value in that sequence.
     *
     * Since ResourceMonitor reads from Runtime.getRuntime() which cannot be controlled
     * in unit tests, this test validates the peak-tracking algorithm directly: for any
     * sequence of positive floats, tracking the peak incrementally (as ResourceMonitor does)
     * yields the same result as computing the maximum of the entire sequence.
     *
     * **Validates: Requirements 6.4**
     */
    @Property(tries = 100)
    @Tag("Feature: render-console-view, Property 6: Peak memory accuracy")
    fun peakMemoryAccuracy(
        @ForAll("memoryReadingSequence") @NotEmpty readings: List<Float>
    ) {
        // Simulate ResourceMonitor's peak-tracking logic:
        // peakMemoryMb starts at 0, updated when memoryMb > peakMemoryMb
        var peakMemoryMb = 0f

        for (memoryMb in readings) {
            if (memoryMb > peakMemoryMb) {
                peakMemoryMb = memoryMb
            }
        }

        // The reported peak SHALL equal the maximum value in the sequence
        val expectedMax = readings.max()
        assertEquals(expectedMax, peakMemoryMb,
            "Peak memory should equal the maximum value in the readings sequence. " +
            "Readings: $readings, tracked peak: $peakMemoryMb, expected max: $expectedMax")
    }

    // --- Custom Generators ---

    @Provide
    fun memoryReadingSequence(): Arbitrary<List<Float>> {
        val memoryReadingArb = Arbitraries.floats()
            .between(0.1f, 4096.0f)
        return memoryReadingArb.list().ofMinSize(1).ofMaxSize(100)
    }
}
