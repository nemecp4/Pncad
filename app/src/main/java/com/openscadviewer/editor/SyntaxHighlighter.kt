package com.openscadviewer.editor

import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import java.util.regex.Pattern

/**
 * Applies OpenSCAD syntax highlighting to an EditText.
 * Highlights keywords, functions, numbers, strings, and comments.
 */
class SyntaxHighlighter(private val editText: EditText) {

    companion object {
        // Colors for syntax highlighting
        private const val COLOR_KEYWORD = 0xFF569CD6.toInt()    // Blue
        private const val COLOR_FUNCTION = 0xFFDCDCAA.toInt()   // Yellow
        private const val COLOR_NUMBER = 0xFFB5CEA8.toInt()     // Green
        private const val COLOR_STRING = 0xFFCE9178.toInt()     // Orange
        private const val COLOR_COMMENT = 0xFF6A9955.toInt()    // Green
        private const val COLOR_BUILTIN = 0xFF4EC9B0.toInt()    // Teal
        private const val COLOR_BOOLEAN = 0xFF569CD6.toInt()    // Blue
        private const val COLOR_OPERATOR = 0xFFD4D4D4.toInt()   // Light gray

        private val KEYWORDS = listOf(
            "module", "function", "if", "else", "for", "let",
            "each", "assert", "echo", "include", "use"
        )

        private val BUILTINS = listOf(
            "cube", "sphere", "cylinder", "polyhedron",
            "circle", "square", "polygon", "text",
            "translate", "rotate", "scale", "mirror", "multmatrix",
            "color", "offset", "hull", "minkowski",
            "union", "difference", "intersection",
            "linear_extrude", "rotate_extrude",
            "import", "surface", "projection",
            "render", "children"
        )

        private val MATH_FUNCTIONS = listOf(
            "abs", "sign", "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            "floor", "ceil", "round", "sqrt", "pow", "exp", "log", "ln",
            "min", "max", "len", "norm", "cross", "concat", "lookup", "str"
        )

        // Regex patterns
        private val PATTERN_COMMENT_LINE = Pattern.compile("//[^\\n]*")
        private val PATTERN_COMMENT_BLOCK = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL)
        private val PATTERN_STRING = Pattern.compile("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"")
        private val PATTERN_NUMBER = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b")
        private val PATTERN_BOOLEAN = Pattern.compile("\\b(true|false|undef)\\b")
        private val PATTERN_KEYWORD = Pattern.compile(
            "\\b(${KEYWORDS.joinToString("|")})\\b"
        )
        private val PATTERN_BUILTIN = Pattern.compile(
            "\\b(${BUILTINS.joinToString("|")})\\b"
        )
        private val PATTERN_MATH = Pattern.compile(
            "\\b(${MATH_FUNCTIONS.joinToString("|")})\\b"
        )
        private val PATTERN_VARIABLE = Pattern.compile("\\$[a-zA-Z_][a-zA-Z0-9_]*")
    }

    private var isHighlighting = false

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (!isHighlighting) {
                highlightSyntax(s)
            }
        }
    }

    fun attach() {
        editText.addTextChangedListener(textWatcher)
    }

    fun detach() {
        editText.removeTextChangedListener(textWatcher)
    }

    fun highlightSyntax(editable: Editable? = null) {
        val text = editable ?: editText.text ?: return
        if (text.isEmpty()) return

        isHighlighting = true
        try {
            // Remove existing spans
            val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            for (span in spans) {
                text.removeSpan(span)
            }

            val str = text.toString()

            // Apply highlights in order (later ones override earlier)
            applyPattern(text, str, PATTERN_NUMBER, COLOR_NUMBER)
            applyPattern(text, str, PATTERN_BOOLEAN, COLOR_BOOLEAN)
            applyPattern(text, str, PATTERN_KEYWORD, COLOR_KEYWORD)
            applyPattern(text, str, PATTERN_BUILTIN, COLOR_BUILTIN)
            applyPattern(text, str, PATTERN_MATH, COLOR_FUNCTION)
            applyPattern(text, str, PATTERN_VARIABLE, COLOR_FUNCTION)
            applyPattern(text, str, PATTERN_STRING, COLOR_STRING)
            applyPattern(text, str, PATTERN_COMMENT_LINE, COLOR_COMMENT)
            applyPattern(text, str, PATTERN_COMMENT_BLOCK, COLOR_COMMENT)
        } finally {
            isHighlighting = false
        }
    }

    private fun applyPattern(text: Editable, str: String, pattern: Pattern, color: Int) {
        val matcher = pattern.matcher(str)
        while (matcher.find()) {
            text.setSpan(
                ForegroundColorSpan(color),
                matcher.start(),
                matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
