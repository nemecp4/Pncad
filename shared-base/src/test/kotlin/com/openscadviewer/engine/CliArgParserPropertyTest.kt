package com.openscadviewer.engine

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.StringLength
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: engine-modularization-and-benchmarks
 *
 * Property-based tests for CliArgParser using jqwik.
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.6
 */
class CliArgParserPropertyTest {

    /**
     * Feature: engine-modularization-and-benchmarks, Property 1: CLI argument validation
     *
     * For any sequence of command-line arguments with fewer than 2 positional arguments,
     * parse() returns a failure result.
     *
     * Validates: Requirements 2.1, 2.2, 2.3, 2.4
     */
    @Property(tries = 100)
    @Tag("property-1-cli-argument-validation")
    fun zeroPositionalArgsAlwaysFails(
        @ForAll("zeroPositionalArgs") args: Array<String>
    ) {
        val result = CliArgParser.parse(args)
        assertTrue(result.isFailure, "Expected failure with 0 positional args, but got: $result")
    }

    @Property(tries = 100)
    @Tag("property-1-cli-argument-validation")
    fun onePositionalArgAlwaysFails(
        @ForAll("onePositionalArgs") args: Array<String>
    ) {
        val result = CliArgParser.parse(args)
        assertTrue(result.isFailure, "Expected failure with 1 positional arg, but got: $result")
    }

    @Property(tries = 100)
    @Tag("property-1-cli-argument-validation")
    fun emptyArgsAlwaysFails() {
        val result = CliArgParser.parse(emptyArray())
        assertTrue(result.isFailure, "Expected failure with empty args")
    }

    /**
     * Feature: engine-modularization-and-benchmarks, Property 2: Timeout flag parsing
     *
     * For any integer value between 1 and 3600 (inclusive), --timeout <value> is accepted.
     * For any value outside this range or non-integer string, it is rejected.
     *
     * Validates: Requirements 2.6
     */
    @Property(tries = 100)
    @Tag("property-2-timeout-flag-parsing")
    fun validTimeoutIsAccepted(
        @ForAll @IntRange(min = 1, max = 3600) timeout: Int
    ) {
        val args = arrayOf("input.scad", "output.stl", "--timeout", timeout.toString())
        val result = CliArgParser.parse(args)
        assertTrue(result.isSuccess, "Expected success for timeout=$timeout, but got failure: ${result.exceptionOrNull()?.message}")
        assertEquals(timeout, result.getOrNull()!!.timeoutSeconds)
    }

    @Property(tries = 100)
    @Tag("property-2-timeout-flag-parsing")
    fun timeoutBelowRangeIsRejected(
        @ForAll @IntRange(min = Int.MIN_VALUE, max = 0) timeout: Int
    ) {
        val args = arrayOf("input.scad", "output.stl", "--timeout", timeout.toString())
        val result = CliArgParser.parse(args)
        assertTrue(result.isFailure, "Expected failure for timeout=$timeout (below range)")
    }

    @Property(tries = 100)
    @Tag("property-2-timeout-flag-parsing")
    fun timeoutAboveRangeIsRejected(
        @ForAll @IntRange(min = 3601, max = Int.MAX_VALUE) timeout: Int
    ) {
        val args = arrayOf("input.scad", "output.stl", "--timeout", timeout.toString())
        val result = CliArgParser.parse(args)
        assertTrue(result.isFailure, "Expected failure for timeout=$timeout (above range)")
    }

    @Property(tries = 100)
    @Tag("property-2-timeout-flag-parsing")
    fun nonIntegerTimeoutIsRejected(
        @ForAll("nonIntegerStrings") value: String
    ) {
        val args = arrayOf("input.scad", "output.stl", "--timeout", value)
        val result = CliArgParser.parse(args)
        assertTrue(result.isFailure, "Expected failure for non-integer timeout='$value'")
    }

    // --- Providers ---

    @Provide
    fun zeroPositionalArgs(): Arbitrary<Array<String>> {
        // Generate 0-5 timeout flags but no positional arguments
        val validTimeout = Arbitraries.integers().between(1, 3600)
        return validTimeout.map { t ->
            arrayOf("--timeout", t.toString())
        }.withoutEdgeCases()
            .injectNull(0.3) // sometimes no args at all
            .map { it ?: emptyArray() }
    }

    @Provide
    fun onePositionalArgs(): Arbitrary<Array<String>> {
        // Generate exactly 1 positional arg, optionally with a --timeout flag
        val positionalArg = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
            .map { "$it.scad" }
        val validTimeout = Arbitraries.integers().between(1, 3600)

        return Combinators.combine(positionalArg, validTimeout, Arbitraries.of(true, false))
            .`as` { arg, timeout, includeTimeout ->
                if (includeTimeout) {
                    arrayOf(arg, "--timeout", timeout.toString())
                } else {
                    arrayOf(arg)
                }
            }
    }

    @Provide
    fun nonIntegerStrings(): Arbitrary<String> {
        return Arbitraries.oneOf(
            // Alphabetic strings
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            // Decimal numbers
            Arbitraries.doubles().between(-1000.0, 1000.0).map { it.toString() },
            // Mixed alphanumeric
            Arbitraries.strings().withCharRange('a', 'z').withCharRange('0', '9')
                .ofMinLength(2).ofMaxLength(10)
                .filter { it.toIntOrNull() == null }
        )
    }
}
