package com.openscadviewer.editor

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * sora-editor [EditorColorScheme] for OpenSCAD, mapping the legacy VS Code-style
 * palette (from `SyntaxHighlighter` + `colors.xml`) onto both the standard sora
 * color keys and the custom span-category ids emitted by [OpenScadLanguage].
 *
 * [OpenScadLanguage] tags each span via `TextStyle.makeStyle(typeId)` using the
 * `TYPE_*` ids in [OpenScadLanguage.Companion]; this scheme registers a color
 * for each of those ids so highlighting renders in the dark VS Code palette.
 *
 * Requirement: 5.3.
 */
class OpenScadColorScheme : EditorColorScheme() {

    override fun applyDefault() {
        super.applyDefault()

        // --- Standard editor keys (dark VS Code palette) --------------------
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
        // Background/text from res/values/colors.xml (code_background/code_text).
        const val CODE_BACKGROUND = 0xFF2D2D2D.toInt()
        const val CODE_TEXT = 0xFFD4D4D4.toInt()

        // Gutter and current-line accents consistent with the dark palette.
        const val LINE_NUMBER_COLOR = 0xFF858585.toInt()
        const val CURRENT_LINE_COLOR = 0xFF3A3A3A.toInt()
        const val SELECTION_BACKGROUND = 0xFF264F78.toInt()

        // Syntax palette (from legacy SyntaxHighlighter).
        const val KEYWORD = 0xFF569CD6.toInt()
        const val BUILTIN = 0xFF4EC9B0.toInt()
        const val MATH = 0xFFDCDCAA.toInt()
        const val NUMBER = 0xFFB5CEA8.toInt()
        const val BOOLEAN = 0xFF569CD6.toInt()
        const val VARIABLE = 0xFFDCDCAA.toInt()
        const val STRING = 0xFFCE9178.toInt()
        const val COMMENT = 0xFF6A9955.toInt()
    }
}
