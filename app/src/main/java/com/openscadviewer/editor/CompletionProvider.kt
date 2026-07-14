package com.openscadviewer.editor

interface CompletionProvider {
    /** Provider category for priority ordering */
    val category: CompletionCategory

    /**
     * Returns candidates matching the given prefix (case-insensitive prefix match).
     * Results are sorted alphabetically.
     */
    fun complete(prefix: String): List<String>
}

enum class CompletionCategory(val priority: Int) {
    USER_DEFINED(0),
    KEYWORD(1),
    BUILTIN(2),
    MATH(3)
}
