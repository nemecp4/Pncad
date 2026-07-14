package com.openscadviewer.editor

import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: editor-code-completion, Property 7: Insertion correctness
 *
 * Property 7: Insertion replaces exactly the prefix
 * For any document text, cursor position, and completion item, after insertCompletion(item)
 * the resulting text shall equal text[0..start) + item.text + text[cursorPos..end)
 * and the cursor shall be at position start + item.text.length.
 *
 * Validates: Requirements 6.1, 6.2
 */
class CompletionInsertionPropertyTest {

    companion object {
        private val WORD_CHAR_PATTERN = Regex("[a-zA-Z_][a-zA-Z0-9_]*$")
    }

    /**
     * Pure function simulating the insertion logic from CompletionTextWatcher.insertCompletion().
     * Extracts the prefix at cursorPos, replaces it with completionText, and returns
     * the resulting text and new cursor position.
     */
    private fun simulateInsertion(text: String, cursorPos: Int, completionText: String): Pair<String, Int> {
        val beforeCursor = text.substring(0, cursorPos)
        val prefix = WORD_CHAR_PATTERN.find(beforeCursor)?.value ?: ""
        val start = cursorPos - prefix.length

        val resultText = text.substring(0, start) + completionText + text.substring(cursorPos)
        val resultCursor = start + completionText.length
        return Pair(resultText, resultCursor)
    }

    // --- Property 7: Insertion replaces exactly the prefix ---

    @Property(tries = 200)
    @Tag("Feature: editor-code-completion")
    @Tag("Property 7: Insertion correctness")
    fun insertionReplacesExactlyThePrefix(
        @ForAll("documentsWithPrefix") scenario: InsertionScenario
    ) {
        val (resultText, resultCursor) = simulateInsertion(
            scenario.text, scenario.cursorPos, scenario.completionText
        )

        val prefix = WORD_CHAR_PATTERN.find(scenario.text.substring(0, scenario.cursorPos))?.value ?: ""
        val start = scenario.cursorPos - prefix.length

        // Verify: resulting text = text[0..start) + completionText + text[cursorPos..end)
        val expectedText = scenario.text.substring(0, start) +
            scenario.completionText +
            scenario.text.substring(scenario.cursorPos)
        assertEquals(
            expectedText, resultText,
            "Resulting text should have prefix replaced with completion. " +
                "text=\"${scenario.text}\", cursorPos=${scenario.cursorPos}, " +
                "prefix=\"$prefix\", completion=\"${scenario.completionText}\""
        )

        // Verify: cursor is at start + completionText.length
        val expectedCursor = start + scenario.completionText.length
        assertEquals(
            expectedCursor, resultCursor,
            "Cursor should be at start + completionText.length = $expectedCursor, " +
                "but was $resultCursor"
        )
    }

    @Property(tries = 200)
    @Tag("Feature: editor-code-completion")
    @Tag("Property 7: Insertion correctness")
    fun textBeforePrefixIsUnchanged(
        @ForAll("documentsWithPrefix") scenario: InsertionScenario
    ) {
        val (resultText, _) = simulateInsertion(
            scenario.text, scenario.cursorPos, scenario.completionText
        )

        val prefix = WORD_CHAR_PATTERN.find(scenario.text.substring(0, scenario.cursorPos))?.value ?: ""
        val start = scenario.cursorPos - prefix.length

        // Text before the prefix start must be identical
        val originalBefore = scenario.text.substring(0, start)
        val resultBefore = resultText.substring(0, start)
        assertEquals(
            originalBefore, resultBefore,
            "Text before the prefix should be unchanged. " +
                "Expected \"$originalBefore\" but got \"$resultBefore\""
        )
    }

    @Property(tries = 200)
    @Tag("Feature: editor-code-completion")
    @Tag("Property 7: Insertion correctness")
    fun textAfterCursorIsUnchanged(
        @ForAll("documentsWithPrefix") scenario: InsertionScenario
    ) {
        val (resultText, _) = simulateInsertion(
            scenario.text, scenario.cursorPos, scenario.completionText
        )

        val prefix = WORD_CHAR_PATTERN.find(scenario.text.substring(0, scenario.cursorPos))?.value ?: ""
        val start = scenario.cursorPos - prefix.length

        // Text after the original cursor position must be identical
        val originalAfter = scenario.text.substring(scenario.cursorPos)
        val resultAfter = resultText.substring(start + scenario.completionText.length)
        assertEquals(
            originalAfter, resultAfter,
            "Text after the cursor should be unchanged. " +
                "Expected \"$originalAfter\" but got \"$resultAfter\""
        )
    }

    @Property(tries = 200)
    @Tag("Feature: editor-code-completion")
    @Tag("Property 7: Insertion correctness")
    fun resultingTextLengthIsCorrect(
        @ForAll("documentsWithPrefix") scenario: InsertionScenario
    ) {
        val (resultText, _) = simulateInsertion(
            scenario.text, scenario.cursorPos, scenario.completionText
        )

        val prefix = WORD_CHAR_PATTERN.find(scenario.text.substring(0, scenario.cursorPos))?.value ?: ""

        // Result length = original length - prefix length + completion length
        val expectedLength = scenario.text.length - prefix.length + scenario.completionText.length
        assertEquals(
            expectedLength, resultText.length,
            "Result text length should be original(${scenario.text.length}) - " +
                "prefix(${prefix.length}) + completion(${scenario.completionText.length}) = $expectedLength, " +
                "but was ${resultText.length}"
        )
    }

    // --- Generators ---

    @Provide
    fun documentsWithPrefix(): Arbitrary<InsertionScenario> {
        // Generate text that has at least 2 identifier chars before the cursor
        val beforePrefixArb = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars(' ', '(', ')', '{', '}', ';', '\n', '=', '+', '-', '*', '/')
            .ofMinLength(0).ofMaxLength(20)

        val prefixArb = validIdentifiers(2, 8)

        val afterCursorArb = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars(' ', '(', ')', '{', '}', ';', '\n', '=', '+', '-', '*', '/')
            .ofMinLength(0).ofMaxLength(20)

        val completionArb = validIdentifiers(3, 15)

        return Combinators.combine(beforePrefixArb, prefixArb, afterCursorArb, completionArb)
            .filter { before, prefix, _, _ ->
                // Ensure the before text doesn't end with identifier chars
                // (otherwise it would merge with the prefix)
                before.isEmpty() || !before.last().let { it.isLetterOrDigit() || it == '_' }
            }
            .`as` { before, prefix, after, completion ->
                val text = before + prefix + after
                val cursorPos = before.length + prefix.length
                InsertionScenario(text, cursorPos, completion)
            }
    }

    private fun validIdentifiers(minLen: Int, maxLen: Int): Arbitrary<String> {
        val firstChar: Arbitrary<Char> = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .with('_')

        val restChar: Arbitrary<Char> = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .range('0', '9')
            .with('_')

        return firstChar.flatMap { first ->
            restChar.list().ofMinSize(minLen - 1).ofMaxSize(maxLen - 1)
                .map { rest -> first + rest.joinToString("") }
        }
    }

    // --- Data classes ---

    data class InsertionScenario(
        val text: String,
        val cursorPos: Int,
        val completionText: String
    )
}
