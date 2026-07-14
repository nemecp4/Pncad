package com.openscadviewer.editor

import kotlinx.coroutines.*

class DocumentScanner(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : CompletionProvider {
    override val category = CompletionCategory.USER_DEFINED

    @Volatile
    private var cachedDeclarations: List<String> = emptyList()

    private var scanJob: Job? = null

    companion object {
        private val DECLARATION_PATTERN = Regex(
            """(?:module|function)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\("""
        )
        private val LINE_COMMENT_PATTERN = Regex("""//[^\n]*""")
        private val BLOCK_COMMENT_PATTERN = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    }

    /**
     * Triggers a debounced re-scan of the document text.
     * Cancels any in-progress scan and starts a new one after 500ms.
     */
    fun onTextChanged(text: String) {
        scanJob?.cancel()
        scanJob = scope.launch(dispatcher) {
            delay(500)
            val declarations = scan(text)
            cachedDeclarations = declarations
        }
    }

    override fun complete(prefix: String): List<String> {
        val lowerPrefix = prefix.lowercase()
        return cachedDeclarations.filter { it.lowercase().startsWith(lowerPrefix) }
    }

    /**
     * Extracts user-defined module/function names from text,
     * excluding those inside comments.
     */
    internal fun scan(text: String): List<String> {
        // Remove comments first (replace with spaces to preserve positions)
        val stripped = BLOCK_COMMENT_PATTERN.replace(text) { " ".repeat(it.value.length) }
            .let { LINE_COMMENT_PATTERN.replace(it) { match -> " ".repeat(match.value.length) } }

        // Extract declarations
        val names = mutableSetOf<String>()
        DECLARATION_PATTERN.findAll(stripped).forEach { match ->
            names.add(match.groupValues[1])
        }
        return names.sorted().toList()
    }
}
