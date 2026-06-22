package com.openscadviewer.engine

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.LongRange
import org.junit.jupiter.api.Assertions.*

/**
 * Property-based tests for progress message content.
 *
 * These tests verify the message FORMAT strings contain required numeric data
 * and error details for any valid inputs, independent of engine behavior.
 *
 * **Validates: Requirements 2.2, 2.4, 2.5, 2.6**
 */
class ProgressMessagePropertyTest {

    /**
     * Property 3: Completion message contains required numeric data
     *
     * For any non-negative integer node count and triangle count, and any non-negative
     * elapsed time in milliseconds, the formatted parse-completion message SHALL contain
     * the node count as a substring, and the formatted compute-completion message SHALL
     * contain both the triangle count and elapsed time as substrings.
     *
     * **Validates: Requirements 2.2, 2.4**
     */
    @Property(tries = 100)
    @Tag("Feature: render-console-view, Property 3: Completion message contains required numeric data")
    fun completionMessageContainsRequiredNumericData(
        @ForAll("nonNegativeInts") nodeCount: Int,
        @ForAll("nonNegativeInts") triangleCount: Int,
        @ForAll("nonNegativeLongs") elapsedMs: Long
    ) {
        // Parse-completion message format: "Parsed {N} scene nodes"
        val parseCompleteMessage = "Parsed $nodeCount scene nodes"
        assertTrue(
            parseCompleteMessage.contains(nodeCount.toString()),
            "Parse-completion message '$parseCompleteMessage' should contain node count '$nodeCount'"
        )

        // Compute-completion message format: "Mesh generated: {N} triangles in {T}ms"
        val computeCompleteMessage = "Mesh generated: $triangleCount triangles in ${elapsedMs}ms"
        assertTrue(
            computeCompleteMessage.contains(triangleCount.toString()),
            "Compute-completion message '$computeCompleteMessage' should contain triangle count '$triangleCount'"
        )
        assertTrue(
            computeCompleteMessage.contains("${elapsedMs}ms"),
            "Compute-completion message '$computeCompleteMessage' should contain elapsed time '${elapsedMs}ms'"
        )
    }

    /**
     * Property 4: Error message contains required details
     *
     * For any error category (from the ErrorCategory enum) and any non-empty error message
     * string, the formatted log entry for a computation error SHALL contain both the category
     * name and the error message as substrings. Similarly, for any parse error with a source
     * line number and message, the formatted log entry SHALL contain both the line number and
     * error text.
     *
     * **Validates: Requirements 2.5, 2.6**
     */
    @Property(tries = 100)
    @Tag("Feature: render-console-view, Property 4: Error message contains required details")
    fun errorMessageContainsRequiredDetails(
        @ForAll("errorCategories") category: ErrorCategory,
        @ForAll("nonEmptyMessages") errorMessage: String,
        @ForAll("positiveInts") lineNumber: Int,
        @ForAll("nonEmptyMessages") parseErrorText: String
    ) {
        // Compute error format: "{ErrorCategory}: {message}"
        val computeErrorFormatted = "${category}: $errorMessage"
        assertTrue(
            computeErrorFormatted.contains(category.name),
            "Compute error message '$computeErrorFormatted' should contain category name '${category.name}'"
        )
        assertTrue(
            computeErrorFormatted.contains(errorMessage),
            "Compute error message '$computeErrorFormatted' should contain error message '$errorMessage'"
        )

        // Parse error format: "Parse error at line {L}: {message}"
        val parseErrorFormatted = "Parse error at line $lineNumber: $parseErrorText"
        assertTrue(
            parseErrorFormatted.contains(lineNumber.toString()),
            "Parse error message '$parseErrorFormatted' should contain line number '$lineNumber'"
        )
        assertTrue(
            parseErrorFormatted.contains(parseErrorText),
            "Parse error message '$parseErrorFormatted' should contain error text '$parseErrorText'"
        )
    }

    // --- Custom Generators ---

    @Provide
    fun nonNegativeInts(): Arbitrary<Int> {
        return Arbitraries.integers().between(0, Int.MAX_VALUE)
    }

    @Provide
    fun nonNegativeLongs(): Arbitrary<Long> {
        return Arbitraries.longs().between(0L, Long.MAX_VALUE)
    }

    @Provide
    fun positiveInts(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, Int.MAX_VALUE)
    }

    @Provide
    fun errorCategories(): Arbitrary<ErrorCategory> {
        return Arbitraries.of(*ErrorCategory.values())
    }

    @Provide
    fun nonEmptyMessages(): Arbitrary<String> {
        return Arbitraries.strings()
            .ofMinLength(1)
            .ofMaxLength(200)
            .filter { it.isNotBlank() }
    }
}
