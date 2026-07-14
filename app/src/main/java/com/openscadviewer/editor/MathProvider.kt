package com.openscadviewer.editor

class MathProvider : CompletionProvider {
    override val category = CompletionCategory.MATH

    private val mathFunctions = OpenScadTokens.MATH_FUNCTIONS.sorted()

    override fun complete(prefix: String): List<String> {
        val lowerPrefix = prefix.lowercase()
        return mathFunctions.filter { it.lowercase().startsWith(lowerPrefix) }
    }
}
