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

        // --- OpenSCAD token colors ------------------------------------------
        // Same standard-id mapping as the dark scheme (see OpenScadColorScheme);
        // each shared id is set exactly once with the light palette.
        setColor(KEYWORD, KEYWORD_COLOR)              // keywords + booleans
        setColor(FUNCTION_NAME, BUILTIN_COLOR)        // built-in modules/functions
        setColor(IDENTIFIER_VAR, VARIABLE_COLOR)      // math functions + $-variables
        setColor(LITERAL, NUMBER_COLOR)               // numeric literals
        setColor(IDENTIFIER_NAME, STRING_COLOR)       // string literals
        setColor(COMMENT, COMMENT_COLOR)              // comments
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
        const val KEYWORD_COLOR = 0xFF0000FF.toInt()
        const val BUILTIN_COLOR = 0xFF267F99.toInt()
        const val VARIABLE_COLOR = 0xFF795E26.toInt()
        const val NUMBER_COLOR = 0xFF098658.toInt()
        const val STRING_COLOR = 0xFFA31515.toInt()
        const val COMMENT_COLOR = 0xFF008000.toInt()
    }
}
