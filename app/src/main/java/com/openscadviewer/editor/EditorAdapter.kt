package com.openscadviewer.editor

import android.view.View
import io.github.rosemoe.sora.lang.Language

/**
 * Abstraction over the concrete code-editor widget.
 *
 * Isolates [com.openscadviewer.MainActivity] from the underlying editor
 * implementation so the migration's blast radius is contained and future
 * editor changes stay localized (Requirement 3).
 *
 * The current call sites map one-to-one onto these operations:
 * `getText`, `setText`, `getCursor`/`selectionStart`, `setSelection`,
 * `isEnabled`, `hint`, and a single debounced content-change listener.
 */
interface EditorAdapter {

    /** Returns the current editor contents. */
    fun getText(): String

    /**
     * Sets the editor contents programmatically.
     *
     * Programmatic updates must NOT fire the user-edit callback registered
     * via [setOnContentChanged] (Requirement 4.3).
     */
    fun setText(text: String)

    /** Returns the caret position as a character offset. */
    fun getCursor(): Int

    /**
     * Moves the caret to the given character [offset].
     *
     * Implementations clamp the offset to the valid range `[0, textLength]`.
     */
    fun setCursor(offset: Int)

    /** Enables or disables editing. */
    fun setEditable(enabled: Boolean)

    /** Sets the hint/placeholder text shown when the editor is empty. */
    fun setPlaceholder(hint: String)

    /**
     * Registers the single content-change listener.
     *
     * The listener is invoked with `(text, cursor)` on user edits only.
     */
    fun setOnContentChanged(listener: (text: String, cursor: Int) -> Unit)

    /**
     * Sets the editor's syntax-highlighting / completion [Language].
     *
     * Keeps [com.openscadviewer.MainActivity] off the concrete widget type when
     * wiring [OpenScadLanguage] + the completion bridge.
     */
    fun setLanguage(language: Language)

    /** Underlying [View] for adding to the layout / findViewById-style access. */
    val view: View
}
