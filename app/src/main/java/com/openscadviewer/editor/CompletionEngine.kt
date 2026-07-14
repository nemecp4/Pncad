package com.openscadviewer.editor

class CompletionEngine(
    private val providers: List<CompletionProvider>
) {
    companion object {
        const val MIN_PREFIX_LENGTH = 2
    }

    /**
     * Returns filtered, deduplicated, priority-ordered suggestions.
     * Returns empty list if prefix is shorter than MIN_PREFIX_LENGTH.
     */
    fun complete(prefix: String): List<CompletionItem> {
        if (prefix.length < MIN_PREFIX_LENGTH) return emptyList()

        val seen = mutableSetOf<String>()
        val results = mutableListOf<CompletionItem>()

        // Providers are pre-sorted by category priority
        for (provider in providers.sortedBy { it.category.priority }) {
            val candidates = provider.complete(prefix)
            for (candidate in candidates) {
                val lower = candidate.lowercase()
                if (lower !in seen) {
                    seen.add(lower)
                    results.add(CompletionItem(candidate, provider.category))
                }
            }
        }
        return results
    }
}

data class CompletionItem(
    val text: String,
    val category: CompletionCategory
)
