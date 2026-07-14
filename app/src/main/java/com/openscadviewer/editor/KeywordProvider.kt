package com.openscadviewer.editor

class KeywordProvider : CompletionProvider {
    override val category = CompletionCategory.KEYWORD

    private val keywords = OpenScadTokens.KEYWORDS.sorted()

    override fun complete(prefix: String): List<String> {
        val lowerPrefix = prefix.lowercase()
        return keywords.filter { it.lowercase().startsWith(lowerPrefix) }
    }
}
