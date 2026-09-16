package com.openscadviewer.editor

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * [EditorAdapter] backed by sora-editor's [CodeEditor].
 *
 * Bridges the app's character-offset / user-edit-callback view of the editor
 * onto sora's (line, column) cursor model and event pipeline, and configures
 * the widget to match the previous editor's look (monospace, ~13sp, gutter)
 * while enabling the new capabilities the migration delivers (bracket-pair
 * highlighting, pinch-to-zoom, undo/redo — the latter built into [CodeEditor]).
 *
 * Requirements: 3.1, 4.3, 7.1, 7.4, 8.2, 8.3.
 */
class SoraEditorAdapter(
    private val editor: CodeEditor,
) : EditorAdapter {

    /**
     * Guards programmatic content updates so they do not surface as user edits.
     *
     * Set around [setText] (and placeholder rendering); while true the
     * content-change subscription skips the registered listener, mirroring the
     * legacy `isLoadingContent` flag (Requirement 4.3).
     */
    private var loading = false

    /** Registered user-edit listener, invoked with `(text, cursor)`. */
    private var contentChangedListener: ((text: String, cursor: Int) -> Unit)? = null

    /**
     * Hint shown when the editor is empty. sora-editor 0.23.6 has no native
     * placeholder API, so the hint is stored and rendered as non-editable
     * placeholder text while the editor is otherwise empty (see [renderPlaceholder]).
     */
    private var placeholder: String = ""

    /** True while the stored [placeholder] is currently displayed as editor text. */
    private var placeholderShown = false

    init {
        configureWidget()
        editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
            if (loading) return@subscribeEvent
            contentChangedListener?.invoke(getText(), getCursor())
        }
    }

    override fun getText(): String {
        // While the placeholder is displayed the editor holds no real content.
        if (placeholderShown) return ""
        return editor.text.toString()
    }

    override fun setText(text: String) {
        loading = true
        try {
            placeholderShown = false
            editor.setText(text)
            if (text.isEmpty()) {
                renderPlaceholder()
            }
        } finally {
            loading = false
        }
    }

    override fun getCursor(): Int {
        if (placeholderShown) return 0
        return editor.cursor.left().index
    }

    override fun setCursor(offset: Int) {
        if (placeholderShown) return
        val content = editor.text
        val clamped = offset.coerceIn(0, content.length)
        val position = content.indexer.getCharPosition(clamped)
        editor.setSelection(position.line, position.column)
    }

    override fun setEditable(enabled: Boolean) {
        editor.editable = enabled
    }

    override fun setPlaceholder(hint: String) {
        placeholder = hint
        // Refresh the display if the editor is currently empty (or showing an
        // older placeholder), so an updated hint takes effect immediately.
        if (placeholderShown || editor.text.isEmpty()) {
            renderPlaceholder()
        }
    }

    override fun setOnContentChanged(listener: (text: String, cursor: Int) -> Unit) {
        contentChangedListener = listener
    }

    override fun setLanguage(language: Language) {
        editor.setEditorLanguage(language)
    }

    override fun setColorScheme(scheme: EditorColorScheme) {
        editor.colorScheme = scheme
    }

    override val view: View
        get() = editor

    /**
     * Displays the stored [placeholder] as non-editable editor text.
     *
     * Rendered under the [loading] guard so it never fires the user-edit
     * callback. Does nothing when there is no hint to show.
     */
    private fun renderPlaceholder() {
        if (placeholder.isEmpty()) {
            placeholderShown = false
            return
        }
        val alreadyLoading = loading
        loading = true
        try {
            editor.setText(placeholder)
            placeholderShown = true
        } finally {
            loading = alreadyLoading
        }
    }

    private fun configureWidget() {
        // VS Code-style dark color scheme mapping the OpenSCAD span categories
        // to the legacy palette (Requirement 5.3).
        editor.colorScheme = OpenScadColorScheme()

        // Monospace, matching the legacy editor's ~13sp code font.
        editor.setTypefaceText(Typeface.MONOSPACE)
        editor.setTypefaceLineNumber(Typeface.MONOSPACE)
        // CodeEditor.setTextSize interprets its argument in sp units.
        editor.setTextSize(TEXT_SIZE_SP)

        // Line-number gutter on (Requirement 7.1).
        editor.isLineNumberEnabled = true

        // Bracket-pair highlighting (Requirement 8.3).
        editor.isHighlightBracketPair = true

        // Pinch-to-scale / text zoom (Requirement 8.2).
        // setScaleTextSizes expects pixel units, so convert the sp bounds.
        editor.isScalable = true
        editor.setScaleTextSizes(spToPx(MIN_TEXT_SIZE_SP), spToPx(MAX_TEXT_SIZE_SP))

        // Undo/redo (Requirement 7.4) is built into CodeEditor; ensure it is on.
        editor.isEditable = true
    }

    private fun spToPx(sizeSp: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sizeSp,
            editor.context.resources.displayMetrics,
        )

    private companion object {
        /** Matches the legacy editor's 13sp code font. */
        const val TEXT_SIZE_SP = 13f

        /** Pinch-zoom bounds around the default text size. */
        const val MIN_TEXT_SIZE_SP = 8f
        const val MAX_TEXT_SIZE_SP = 32f
    }
}
