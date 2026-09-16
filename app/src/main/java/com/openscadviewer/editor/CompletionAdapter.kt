package com.openscadviewer.editor

import android.os.Bundle
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Bridges the existing [CompletionEngine] (and its providers) into
 * sora-editor's completion pipeline (Requirement 6).
 *
 * This is a thin adapter: it does not reimplement any completion logic. It
 * holds the same [CompletionEngine] the legacy `CompletionTextWatcher` used —
 * built from [DocumentScanner], [KeywordProvider], [BuiltinProvider] and
 * [MathProvider] — and reuses the prefix-extraction rule from
 * `CompletionTextWatcher.extractPrefix` (Requirement 6.1, 6.2).
 *
 * Wiring: [attachTo] assigns this adapter as an [OpenScadLanguage]'s
 * `completionDelegate`, so the language forwards `requireAutoComplete` here
 * without any signature change (Requirement 6.3).
 *
 * On each completion request the adapter:
 *  1. drives [DocumentScanner.onTextChanged] with the current document text so
 *     user-defined `module`/`function` declarations stay current
 *     (Requirement 6.4 — debounced 500 ms inside the scanner);
 *  2. extracts the identifier prefix ending at the cursor;
 *  3. calls [CompletionEngine.complete] (which enforces
 *     [CompletionEngine.MIN_PREFIX_LENGTH] and provider priority ordering); and
 *  4. publishes each result as a [SimpleCompletionItem] whose commit replaces
 *     the current prefix with the item text.
 */
class CompletionAdapter private constructor(
    private val engine: CompletionEngine,
    private val documentScanner: DocumentScanner,
) : OpenScadLanguage.CompletionDelegate {

    companion object {
        /** Same identifier-prefix rule as `CompletionTextWatcher.extractPrefix`. */
        private val WORD_CHAR_PATTERN = Regex("[a-zA-Z_][a-zA-Z0-9_]*$")

        /**
         * Builds a [CompletionAdapter] with the standard provider set, matching
         * the engine the legacy watcher used. The [DocumentScanner] runs its
         * debounced scans on [scope] (defaults to a [Dispatchers.Default]-backed
         * supervisor scope owned by the adapter).
         */
        fun create(
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ): CompletionAdapter {
            val documentScanner = DocumentScanner(scope)
            val engine = CompletionEngine(
                listOf(
                    documentScanner,
                    KeywordProvider(),
                    BuiltinProvider(),
                    MathProvider(),
                )
            )
            return CompletionAdapter(engine, documentScanner)
        }

        /**
         * Builds a [CompletionAdapter] over an already-constructed engine and
         * scanner. Primarily for tests that want to inject their own instances.
         */
        fun of(engine: CompletionEngine, documentScanner: DocumentScanner): CompletionAdapter =
            CompletionAdapter(engine, documentScanner)
    }

    /** Registers this adapter as [language]'s completion delegate. */
    fun attachTo(language: OpenScadLanguage) {
        language.completionDelegate = this
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        val text = content.reference.toString()

        // Keep user-defined declarations current (debounced inside the scanner).
        documentScanner.onTextChanged(text)

        val cursorPos = position.index
        if (cursorPos <= 0) return

        val prefix = extractPrefix(text, cursorPos)
        if (prefix.length < CompletionEngine.MIN_PREFIX_LENGTH) return

        val suggestions = engine.complete(prefix)
        if (suggestions.isEmpty()) return

        val prefixLength = prefix.length
        for (item in suggestions) {
            // SimpleCompletionItem(label, prefixLength, commitText): sora removes
            // `prefixLength` chars before the cursor and inserts commitText,
            // i.e. it replaces the current prefix with the item text.
            publisher.addItem(SimpleCompletionItem(item.text, prefixLength, item.text))
        }
    }

    /**
     * Extracts the identifier prefix ending at [cursorPos].
     * Mirrors `CompletionTextWatcher.extractPrefix`.
     */
    internal fun extractPrefix(text: String, cursorPos: Int): String {
        val beforeCursor = text.substring(0, cursorPos)
        val match = WORD_CHAR_PATTERN.find(beforeCursor)
        return match?.value ?: ""
    }
}
