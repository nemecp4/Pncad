package com.openscadviewer.editor

import net.jqwik.api.*
import net.jqwik.api.Combinators
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: editor-code-completion
 *
 * Property-based tests for CompletionEngine.
 *
 * Property 2: Short prefix produces empty results
 * For any prefix string of length 0 or 1, CompletionEngine.complete(prefix) shall return
 * an empty list regardless of the candidates available in any provider.
 * Validates: Requirements 3.4, 7.2
 *
 * Property 5: Engine priority ordering and deduplication
 * For any prefix where multiple providers return candidates, CompletionEngine.complete(prefix)
 * shall return items ordered by category priority (USER_DEFINED < KEYWORD < BUILTIN < MATH),
 * and if the same name (case-insensitive) appears in multiple providers, it shall appear only
 * once from the highest-priority provider.
 * Validates: Requirements 7.4, 7.5
 */
class CompletionEnginePropertyTest {

    // --- Property 2: Short prefix produces empty results ---

    @Property(tries = 100)
    @Tag("property-2-short-prefix-rejection")
    fun shortPrefixAlwaysReturnsEmptyList(
        @ForAll("shortPrefixes") prefix: String
    ) {
        val engine = CompletionEngine(
            listOf(KeywordProvider(), BuiltinProvider(), MathProvider())
        )
        val result = engine.complete(prefix)

        assertTrue(
            result.isEmpty(),
            "CompletionEngine.complete(\"$prefix\") should return empty list for prefix of length ${prefix.length}, but got: $result"
        )
    }

    @Property(tries = 100)
    @Tag("property-2-short-prefix-rejection")
    fun emptyPrefixAlwaysReturnsEmptyList(
        @ForAll("emptyOrSingleCharPrefixes") prefix: String
    ) {
        // Engine with all provider types, including a fake user-defined provider
        val userProvider = FakeProvider(CompletionCategory.USER_DEFINED, listOf("myModule", "myFunction"))
        val engine = CompletionEngine(
            listOf(userProvider, KeywordProvider(), BuiltinProvider(), MathProvider())
        )
        val result = engine.complete(prefix)

        assertTrue(
            result.isEmpty(),
            "CompletionEngine.complete(\"$prefix\") should return empty for short prefix, but got: $result"
        )
    }

    // --- Property 5: Engine priority ordering and deduplication ---

    @Property(tries = 100)
    @Tag("property-5-engine-priority-ordering-deduplication")
    fun resultsAreOrderedByCategoryPriority(
        @ForAll("overlappingProviderScenarios") scenario: ProviderScenario
    ) {
        val engine = CompletionEngine(scenario.providers)
        val result = engine.complete(scenario.prefix)

        // Verify priority ordering: items from lower priority value come first
        for (i in 0 until result.size - 1) {
            assertTrue(
                result[i].category.priority <= result[i + 1].category.priority,
                "Results not ordered by priority: ${result[i]} (priority=${result[i].category.priority}) " +
                    "appears before ${result[i + 1]} (priority=${result[i + 1].category.priority}) " +
                    "for prefix \"${scenario.prefix}\""
            )
        }
    }

    @Property(tries = 100)
    @Tag("property-5-engine-priority-ordering-deduplication")
    fun duplicateNamesAppearOnlyOnceFromHighestPriorityProvider(
        @ForAll("overlappingProviderScenarios") scenario: ProviderScenario
    ) {
        val engine = CompletionEngine(scenario.providers)
        val result = engine.complete(scenario.prefix)

        // Check that no name appears more than once (case-insensitive)
        val lowerNames = result.map { it.text.lowercase() }
        val uniqueLowerNames = lowerNames.distinct()
        assertEquals(
            uniqueLowerNames.size, lowerNames.size,
            "Duplicate names found in results for prefix \"${scenario.prefix}\": $result"
        )

        // For each name that exists in multiple providers, verify it comes from the highest-priority one
        for (item in result) {
            val lowerName = item.text.lowercase()
            // Find all providers that would return this name
            val providersWithName = scenario.providers.filter { provider ->
                provider.complete(scenario.prefix).any { it.lowercase() == lowerName }
            }
            if (providersWithName.size > 1) {
                val highestPriorityCategory = providersWithName.minByOrNull { it.category.priority }!!.category
                assertEquals(
                    highestPriorityCategory, item.category,
                    "Name \"${item.text}\" should come from highest-priority provider " +
                        "(${highestPriorityCategory}) but came from ${item.category}"
                )
            }
        }
    }

    @Property(tries = 100)
    @Tag("property-5-engine-priority-ordering-deduplication")
    fun allMatchingCandidatesFromAllProvidersAreRepresented(
        @ForAll("overlappingProviderScenarios") scenario: ProviderScenario
    ) {
        val engine = CompletionEngine(scenario.providers)
        val result = engine.complete(scenario.prefix)

        // Collect all unique names (case-insensitive) across all providers
        val allCandidateNames = scenario.providers
            .flatMap { it.complete(scenario.prefix) }
            .map { it.lowercase() }
            .distinct()

        val resultNames = result.map { it.text.lowercase() }.distinct()

        assertEquals(
            allCandidateNames.sorted(), resultNames.sorted(),
            "Engine should include all unique matching candidates. " +
                "Missing: ${allCandidateNames - resultNames.toSet()}, " +
                "Extra: ${resultNames - allCandidateNames.toSet()}"
        )
    }

    // --- Generators ---

    @Provide
    fun shortPrefixes(): Arbitrary<String> {
        // Generate strings of length 0 or 1 from any printable characters
        return Arbitraries.oneOf(
            Arbitraries.just(""),
            Arbitraries.chars()
                .range('a', 'z')
                .range('A', 'Z')
                .range('0', '9')
                .with('_')
                .map { it.toString() }
        )
    }

    @Provide
    fun emptyOrSingleCharPrefixes(): Arbitrary<String> {
        return Arbitraries.oneOf(
            Arbitraries.just(""),
            Arbitraries.strings().ofMinLength(1).ofMaxLength(1)
        )
    }

    @Provide
    fun overlappingProviderScenarios(): Arbitrary<ProviderScenario> {
        // Generate a short prefix (2-3 chars) that will match overlapping names
        val prefixArbitrary = Arbitraries.of(
            "mo", "cu", "si", "co", "mi", "ro", "sq", "le", "ab", "fl",
            "ma", "fo", "tr", "sc", "fu", "as", "st", "po", "un", "ci"
        )

        return prefixArbitrary.flatMap { prefix ->
            // Generate candidate names that all start with this prefix
            validIdentifiersSuffix().list().ofMinSize(1).ofMaxSize(4).map { suffixes ->
                val overlappingNames = suffixes.map { prefix + it }

                // Create providers with overlapping names across different categories
                val providers = mutableListOf<CompletionProvider>()

                // USER_DEFINED provider gets some of the overlapping names
                if (overlappingNames.isNotEmpty()) {
                    providers.add(FakeProvider(
                        CompletionCategory.USER_DEFINED,
                        overlappingNames.take((overlappingNames.size + 1) / 2)
                    ))
                }

                // KEYWORD provider gets some overlapping names (possibly same ones)
                if (overlappingNames.size > 1) {
                    providers.add(FakeProvider(
                        CompletionCategory.KEYWORD,
                        overlappingNames.drop(overlappingNames.size / 3).take(2)
                    ))
                }

                // BUILTIN provider gets some names
                providers.add(FakeProvider(
                    CompletionCategory.BUILTIN,
                    overlappingNames.takeLast((overlappingNames.size + 1) / 2)
                ))

                // MATH provider gets the first name to create guaranteed overlap
                providers.add(FakeProvider(
                    CompletionCategory.MATH,
                    listOf(overlappingNames.first())
                ))

                ProviderScenario(providers, prefix)
            }
        }
    }

    private fun validIdentifiersSuffix(): Arbitrary<String> {
        val restChar: Arbitrary<Char> = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .range('0', '9')
            .with('_')

        return restChar.list().ofMinSize(1).ofMaxSize(6)
            .map { chars -> chars.joinToString("") }
    }

    // --- Test helpers ---

    data class ProviderScenario(
        val providers: List<CompletionProvider>,
        val prefix: String
    )

    /**
     * A simple fake CompletionProvider for testing with configurable candidates.
     */
    private class FakeProvider(
        override val category: CompletionCategory,
        private val candidates: List<String>
    ) : CompletionProvider {
        override fun complete(prefix: String): List<String> {
            val lowerPrefix = prefix.lowercase()
            return candidates
                .filter { it.lowercase().startsWith(lowerPrefix) }
                .sorted()
        }
    }
}
