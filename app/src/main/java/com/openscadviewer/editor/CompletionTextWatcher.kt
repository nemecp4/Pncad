package com.openscadviewer.editor

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class CompletionTextWatcher(
    private val editText: EditText,
    private val engine: CompletionEngine,
    private val popup: CompletionPopup,
    private val documentScanner: DocumentScanner
) : TextWatcher {

    companion object {
        private val WORD_CHAR_PATTERN = Regex("[a-zA-Z_][a-zA-Z0-9_]*$")
    }

    private var isInserting = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (isInserting) return
        val text = s?.toString() ?: return

        // Trigger document scanner (debounced)
        documentScanner.onTextChanged(text)

        // Extract prefix
        val cursorPos = editText.selectionStart
        if (cursorPos <= 0) {
            popup.dismiss()
            return
        }

        val prefix = extractPrefix(text, cursorPos)
        if (prefix.length < CompletionEngine.MIN_PREFIX_LENGTH) {
            popup.dismiss()
            return
        }

        // Get suggestions
        val suggestions = engine.complete(prefix)
        if (suggestions.isEmpty()) {
            popup.dismiss()
            return
        }

        // Show popup
        val layout = editText.layout ?: return
        val line = layout.getLineForOffset(cursorPos)
        val col = cursorPos - layout.getLineStart(line)
        popup.update(suggestions, line, col - prefix.length)
    }

    /**
     * Extracts the identifier prefix ending at the cursor position.
     */
    internal fun extractPrefix(text: String, cursorPos: Int): String {
        val beforeCursor = text.substring(0, cursorPos)
        val match = WORD_CHAR_PATTERN.find(beforeCursor)
        return match?.value ?: ""
    }

    /**
     * Inserts a completion, replacing the current prefix atomically.
     */
    fun insertCompletion(item: CompletionItem) {
        isInserting = true
        try {
            val cursorPos = editText.selectionStart
            val text = editText.text?.toString() ?: return
            val prefix = extractPrefix(text, cursorPos)
            val start = cursorPos - prefix.length

            editText.text?.replace(start, cursorPos, item.text)
            editText.setSelection(start + item.text.length)
        } finally {
            isInserting = false
            popup.dismiss()
        }
    }
}
