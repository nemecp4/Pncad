package com.openscadviewer.editor

class BuiltinProvider : CompletionProvider {
    override val category = CompletionCategory.BUILTIN

    private val builtins = OpenScadTokens.BUILTINS.sorted()

    override fun complete(prefix: String): List<String> {
        val lowerPrefix = prefix.lowercase()
        return builtins.filter { it.lowercase().startsWith(lowerPrefix) }
    }
}
