package com.openscadviewer.editor

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Light counterpart to [OpenScadColorScheme].
 *
 * Maps the same OpenSCAD span categories emitted by [OpenScadLanguage] onto a
 * light background using a VS Code "Light+"-style palette, so syntax
 * highlighting stays readable when the user prefers a light editor theme.
 *
 * Colors are chosen for sufficient contrast against the light background; they
 * are deliberately darker/more saturated than the dark scheme's palette.
 */
class OpenScadLightColorScheme : EditorColorScheme() {

    override fun applyDefault() {
        super.applyDefault()

        // --- Standard editor keys (light palette) ---------------------------
        setColor(WHOLE_BACKGROUND, CODE_BACKGROUND)
        setColor(LINE_NUMBER_BACKGROUND, CODE_BACKGROUND)
        setColor(TEXT_NORMAL, CODE_TEXT)
        setColor(LINE_NUMBER, LINE_NUMBER_COLOR)
        setColor(CURRENT_LINE, CURRENT_LINE_COLOR)
        setColor(SELECTION_INSERT, CODE_TEXT)
        setColor(SELECTED_TEXT_BACKGROUND, SELECTION_BACKGROUND)

        // --- Custom OpenSCAD span categories --------------------------------
        setColor(OpenScadLanguage.TYPE_NORMAL, CODE_TEXT)
        setColor(OpenScadLanguage.TYPE_KEYWORD, KEYWORD)
        setColor(OpenScadLanguage.TYPE_BUILTIN, BUILTIN)
        setColor(OpenScadLanguage.TYPE_MATH, MATH)
        setColor(OpenScadLanguage.TYPE_NUMBER, NUMBER)
        setColor(OpenScadLanguage.TYPE_BOOLEAN, BOOLEAN)
        setColor(OpenScadLanguage.TYPE_VARIABLE, VARIABLE)
        setColor(OpenScadLanguage.TYPE_STRING, STRING)
        setColor(OpenScadLanguage.TYPE_COMMENT, COMMENT)
    }

    private companion object {
        // Light background / near-black text.
        const val CODE_BACKGROUND = 0xFFFFFFFF.toInt()
        const val CODE_TEXT = 0xFF1E1E1E.toInt()

        // Gutter and current-line accents consistent with the light palette.
        const val LINE_NUMBER_COLOR = 0xFF6E7781.toInt()
        const val CURRENT_LINE_COLOR = 0xFFF0F0F0.toInt()
        const val SELECTION_BACKGROUND = 0xFFADD6FF.toInt()

        // Syntax palette (VS Code "Light+" style).
        const val KEYWORD = 0xFF0000FF.toInt()
        const val BUILTIN = 0xFF267F99.toInt()
        const val MATH = 0xFF795E26.toInt()
        const val NUMBER = 0xFF098658.toInt()
        const val BOOLEAN = 0xFF0000FF.toInt()
        const val VARIABLE = 0xFF795E26.toInt()
        const val STRING = 0xFFA31515.toInt()
        const val COMMENT = 0xFF008000.toInt()
    }
}
