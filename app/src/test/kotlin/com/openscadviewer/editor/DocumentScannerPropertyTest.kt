package com.openscadviewer.editor

import kotlinx.coroutines.test.TestScope
import net.jqwik.api.*
import net.jqwik.api.Combinators
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: editor-code-completion, Property 4: Scanner deduplication
 *
 * Property-based tests for DocumentScanner.
 *
 * Property 4: Document scanner deduplication
 * For any text containing multiple declarations with the same name,
 * scan(text) shall include that name exactly once in its output.
 *
 * Validates: Requirements 4.3
 */
class DocumentScannerPropertyTest {

    private val scanner = DocumentScanner(TestScope())

    // --- Property 4: Scanner deduplication ---

    @Property(tries = 100)
    @Tag("property-4-scanner-deduplication")
    fun scanReturnsEachNameExactlyOnceRegardlessOfRepetitions(
        @ForAll("repeatedDeclarationTexts") textAndNames: Pair<String, Set<String>>
    ) {
        val (text, expectedNames) = textAndNames
        val result = scanner.scan(text)

        // Each name appears exactly once
        assertEquals(
            result.size, result.distinct().size,
            "scan() returned duplicates: $result"
        )

        // All expected names are present
        assertEquals(
            expectedNames.sorted(), result,
            "scan() should contain exactly the expected names (sorted). Got: $result, expected: ${expectedNames.sorted()}"
        )
    }

    @Property(tries = 100)
    @Tag("property-4-scanner-deduplication")
    fun scanNeverReturnsDuplicateNames(
        @ForAll("repeatedDeclarationTexts") textAndNames: Pair<String, Set<String>>
    ) {
        val (text, _) = textAndNames
        val result = scanner.scan(text)

        // Verify no duplicates exist in the output
        val duplicates = result.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue(
            duplicates.isEmpty(),
            "scan() returned duplicate names: $duplicates in result: $result"
        )
    }

    @Property(tries = 100)
    @Tag("property-4-scanner-deduplication")
    fun scanOutputSizeEqualsUniqueNameCount(
        @ForAll("repeatedDeclarationTexts") textAndNames: Pair<String, Set<String>>
    ) {
        val (text, expectedNames) = textAndNames
        val result = scanner.scan(text)

        assertEquals(
            expectedNames.size, result.size,
            "scan() should return ${expectedNames.size} unique names but returned ${result.size}: $result"
        )
    }

    // --- Generators ---

    @Provide
    fun repeatedDeclarationTexts(): Arbitrary<Pair<String, Set<String>>> {
        // Generate 2-5 unique identifier names
        val namesArbitrary = validIdentifiers().set().ofMinSize(2).ofMaxSize(5)

        // Generate repetition count for each name (2-4 times)
        val repetitionsArbitrary = Arbitraries.integers().between(2, 4)

        return namesArbitrary.flatMap { names ->
            repetitionsArbitrary.list().ofSize(names.size).map { repetitions ->
                val nameList = names.toList()
                val declarations = mutableListOf<String>()

                nameList.forEachIndexed { index, name ->
                    val reps = repetitions[index]
                    repeat(reps) {
                        // Alternate between module and function declarations
                        val keyword = if ((declarations.size + it) % 2 == 0) "module" else "function"
                        declarations.add("$keyword $name() { }")
                    }
                }

                // Shuffle the declarations to create a realistic mixed document
                declarations.shuffle()
                val text = declarations.joinToString("\n")
                Pair(text, names)
            }
        }
    }

    private fun validIdentifiers(): Arbitrary<String> {
        val firstChar: Arbitrary<Char> = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .with('_')

        val restChar: Arbitrary<Char> = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .range('0', '9')
            .with('_')

        return Combinators.combine(firstChar, restChar.list().ofMinSize(1).ofMaxSize(8))
            .`as` { first: Char, rest: List<Char> -> first.toString() + rest.joinToString("") }
    }
}
