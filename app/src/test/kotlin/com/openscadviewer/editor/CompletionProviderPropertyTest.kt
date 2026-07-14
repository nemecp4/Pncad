package com.openscadviewer.editor

import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: editor-code-completion, Property 1: Provider filtering
 *
 * Property-based test for static completion providers (KeywordProvider, BuiltinProvider, MathProvider).
 *
 * For any CompletionProvider with a known candidate list and for any prefix string of length >= 2,
 * complete(prefix) shall return exactly those candidates whose lowercase form starts with the
 * lowercase prefix, in alphabetical order, with no duplicates.
 *
 * Validates: Requirements 1.1, 1.3, 1.4, 2.1, 2.3, 2.4, 3.1, 3.3, 7.1, 7.3
 */
class CompletionProviderPropertyTest {

    private val keywordProvider = KeywordProvider()
    private val builtinProvider = BuiltinProvider()
    private val mathProvider = MathProvider()

    // --- Property tests for KeywordProvider ---

    @Property(tries = 100)
    @Tag("property-1-provider-filtering")
    fun keywordProviderReturnsExactlyMatchingCandidatesInSortedOrder(
        @ForAll("validPrefixes") prefix: String
    ) {
        val result = keywordProvider.complete(prefix)
        val expected = referenceFilter(OpenScadTokens.KEYWORDS, prefix)

        assertEquals(
            expected, result,
            "KeywordProvider.complete(\"$prefix\") returned $result but expected $expected"
        )
    }

    // --- Property tests for BuiltinProvider ---

    @Property(tries = 100)
    @Tag("property-1-provider-filtering")
    fun builtinProviderReturnsExactlyMatchingCandidatesInSortedOrder(
        @ForAll("validPrefixes") prefix: String
    ) {
        val result = builtinProvider.complete(prefix)
        val expected = referenceFilter(OpenScadTokens.BUILTINS, prefix)

        assertEquals(
            expected, result,
            "BuiltinProvider.complete(\"$prefix\") returned $result but expected $expected"
        )
    }

    // --- Property tests for MathProvider ---

    @Property(tries = 100)
    @Tag("property-1-provider-filtering")
    fun mathProviderReturnsExactlyMatchingCandidatesInSortedOrder(
        @ForAll("validPrefixes") prefix: String
    ) {
        val result = mathProvider.complete(prefix)
        val expected = referenceFilter(OpenScadTokens.MATH_FUNCTIONS, prefix)

        assertEquals(
            expected, result,
            "MathProvider.complete(\"$prefix\") returned $result but expected $expected"
        )
    }

    // --- Property: results are sorted ---

    @Property(tries = 100)
    @Tag("property-1-provider-filtering")
    fun allProvidersReturnResultsInSortedOrder(
        @ForAll("validPrefixes") prefix: String
    ) {
        listOf(keywordProvider, builtinProvider, mathProvider).forEach { provider ->
            val result = provider.complete(prefix)
            val sorted = result.sortedBy { it.lowercase() }
            assertEquals(
                sorted, result,
                "${provider::class.simpleName}.complete(\"$prefix\") is not sorted: $result"
            )
        }
    }

    // --- Property: no duplicates ---

    @Property(tries = 100)
    @Tag("property-1-provider-filtering")
    fun allProvidersReturnNoDuplicates(
        @ForAll("validPrefixes") prefix: String
    ) {
        listOf(keywordProvider, builtinProvider, mathProvider).forEach { provider ->
            val result = provider.complete(prefix)
            val unique = result.distinct()
            assertEquals(
                unique, result,
                "${provider::class.simpleName}.complete(\"$prefix\") contains duplicates: $result"
            )
        }
    }

    // --- Generators ---

    @Provide
    fun validPrefixes(): Arbitrary<String> {
        // Generate valid identifier prefixes: first char is [a-zA-Z_], rest are [a-zA-Z0-9_]
        val firstChar: Arbitrary<Char> = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .with('_')

        val restChar: Arbitrary<Char> = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .range('0', '9')
            .with('_')

        return Combinators.combine(firstChar, restChar.list().ofMinSize(1).ofMaxSize(9))
            .`as` { first: Char, rest: List<Char> -> first.toString() + rest.joinToString("") }
    }

    // --- Reference implementation ---

    /**
     * Reference filter: case-insensitive prefix match, sorted alphabetically, no duplicates.
     */
    private fun referenceFilter(candidates: List<String>, prefix: String): List<String> {
        val lowerPrefix = prefix.lowercase()
        return candidates
            .filter { it.lowercase().startsWith(lowerPrefix) }
            .distinct()
            .sortedBy { it.lowercase() }
    }
}
