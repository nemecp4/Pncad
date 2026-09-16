package com.openscadviewer.editor

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager.LineTokenizeResult
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch

/**
 * A sora-editor [Language] for OpenSCAD source.
 *
 * Highlighting is driven by an [AsyncIncrementalAnalyzeManager] that tokenizes
 * one line at a time and emits [Span]s for the categories the legacy
 * `SyntaxHighlighter` covered: keywords, built-in modules/functions, math
 * functions, numbers, booleans (`true` / `false` / `undef`), `$`-prefixed
 * special variables, strings, line comments, and block comments.
 *
 * Keyword / builtin / math classification is sourced from [OpenScadTokens] so
 * the highlighted token set stays in lock-step with the completion engine
 * rather than duplicating the lists here (Requirement 5.2).
 *
 * Block-comment continuation is tracked across lines through the incremental
 * analyzer's per-line [State] mechanism, so a block comment opened on one line
 * keeps the following lines highlighted as comment until it is closed
 * (Requirement 5.4). Per-line tokenization is wrapped in try/catch: a malformed
 * line degrades to a single plain-text span instead of crashing the analyzer.
 *
 * The span "category" constants below ([TYPE_KEYWORD], [TYPE_BUILTIN], ...) are
 * the color-scheme IDs each span is tagged with. A later task defines the
 * `EditorColorScheme` that maps these IDs to the VS Code-style palette.
 *
 * Requirements: 5.1, 5.2, 5.4.
 */
class OpenScadLanguage : Language {

    /**
     * Optional delegate that supplies auto-completion.
     *
     * This is the extension point for a later task (the completion bridge):
     * when non-null, [requireAutoComplete] forwards to it; when null,
     * completion is a no-op. Wiring completion in later only means setting this
     * field — the [Language] contract and [requireAutoComplete] signature stay
     * unchanged.
     */
    var completionDelegate: CompletionDelegate? = null

    /**
     * Extension point for auto-completion. Implemented by the completion bridge
     * in a later task; kept minimal here so this class has no completion
     * dependency yet.
     */
    fun interface CompletionDelegate {
        fun requireAutoComplete(
            content: ContentReference,
            position: CharPosition,
            publisher: CompletionPublisher,
            extraArguments: Bundle,
        )
    }

    private val analyzer = OpenScadAnalyzeManager()

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_STRONG

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        // Minimal stub: delegate when a completion bridge has been wired in,
        // otherwise publish nothing. See [completionDelegate].
        completionDelegate?.requireAutoComplete(content, position, publisher, extraArguments)
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int = 0

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = EmptyLanguage.EmptyFormatter.INSTANCE

    override fun getSymbolPairs(): SymbolPairMatch = EmptyLanguage.EMPTY_SYMBOL_PAIRS

    override fun getNewlineHandlers(): Array<NewlineHandler> = EMPTY_NEWLINE_HANDLERS

    override fun destroy() {
        completionDelegate = null
    }

    /**
     * Per-line analyzer state.
     *
     * The only cross-line context OpenSCAD highlighting needs is whether the
     * line begins inside an open block comment.
     */
    private class State(val inBlockComment: Boolean)

    /**
     * A tokenized region of a single line: the range starting at [column] with
     * the given [length], tagged with a color-scheme [type] id.
     */
    private class TokenSpan(val type: Int, val column: Int, val length: Int)

    private class OpenScadAnalyzeManager :
        AsyncIncrementalAnalyzeManager<State, TokenSpan>() {

        override fun getInitialState(): State = State(inBlockComment = false)

        override fun stateEquals(state: State?, another: State?): Boolean {
            if (state == null || another == null) return state === another
            return state.inBlockComment == another.inBlockComment
        }

        override fun tokenizeLine(
            line: CharSequence,
            state: State,
            lineIndex: Int,
        ): LineTokenizeResult<State, TokenSpan> {
            val tokens = ArrayList<TokenSpan>()
            val endState = try {
                lexLine(line, state.inBlockComment, tokens)
            } catch (_: Throwable) {
                // A malformed line must never crash the analyzer: fall back to
                // a single plain-text span covering the whole line and carry
                // the incoming comment state forward unchanged.
                tokens.clear()
                tokens.add(TokenSpan(TYPE_NORMAL, 0, line.length))
                state.inBlockComment
            }
            return LineTokenizeResult(State(endState), tokens)
        }

        override fun generateSpansForLine(
            tokens: LineTokenizeResult<State, TokenSpan>,
        ): List<Span> {
            val list = tokens.tokens
            val spans = ArrayList<Span>(list.size.coerceAtLeast(1))
            if (list.isEmpty()) {
                spans.add(Span.obtain(0, TextStyle.makeStyle(TYPE_NORMAL)))
                return spans
            }
            for (token in list) {
                spans.add(Span.obtain(token.column, TextStyle.makeStyle(token.type)))
            }
            // A span must start at column 0 for sora to render the line.
            if (spans.first().column != 0) {
                spans.add(0, Span.obtain(0, TextStyle.makeStyle(TYPE_NORMAL)))
            }
            return spans
        }

        override fun computeBlocks(
            text: Content,
            delegate: CodeBlockAnalyzeDelegate,
        ): List<CodeBlock> = emptyList()

        /**
         * Lexes [line], appending a [TokenSpan] for every highlighted region,
         * and returns the block-comment state at the end of the line.
         *
         * @param inBlockComment whether the line starts inside an open block comment
         */
        private fun lexLine(
            line: CharSequence,
            inBlockComment: Boolean,
            out: ArrayList<TokenSpan>,
        ): Boolean {
            val length = line.length
            var i = 0
            var blockComment = inBlockComment

            while (i < length) {
                if (blockComment) {
                    val start = i
                    // Consume until the comment closes or the line ends.
                    var closed = false
                    while (i < length) {
                        if (isBlockCommentClose(line, i, length)) {
                            i += 2
                            closed = true
                            break
                        }
                        i++
                    }
                    out.add(TokenSpan(TYPE_COMMENT, start, i - start))
                    if (closed) blockComment = false
                    continue
                }

                val c = line[i]

                // Whitespace: no span needed, but advance.
                if (c.isWhitespace()) {
                    i++
                    continue
                }

                // Comments.
                if (c == '/' && i + 1 < length) {
                    val next = line[i + 1]
                    if (next == '/') {
                        // Line comment: highlight the rest of the line.
                        out.add(TokenSpan(TYPE_COMMENT, i, length - i))
                        i = length
                        continue
                    }
                    if (next == '*') {
                        val start = i
                        i += 2
                        var closed = false
                        while (i < length) {
                            if (isBlockCommentClose(line, i, length)) {
                                i += 2
                                closed = true
                                break
                            }
                            i++
                        }
                        out.add(TokenSpan(TYPE_COMMENT, start, i - start))
                        blockComment = !closed
                        continue
                    }
                }

                // Strings (double-quoted, with backslash escapes).
                if (c == '"') {
                    val start = i
                    i++
                    while (i < length) {
                        val ch = line[i]
                        if (ch == '\\' && i + 1 < length) {
                            i += 2
                            continue
                        }
                        i++
                        if (ch == '"') break
                    }
                    out.add(TokenSpan(TYPE_STRING, start, i - start))
                    continue
                }

                // `$`-prefixed special variables ($fn, $fa, $t, ...).
                if (c == '$') {
                    val start = i
                    i++
                    while (i < length && isIdentifierPart(line[i])) i++
                    out.add(TokenSpan(TYPE_VARIABLE, start, i - start))
                    continue
                }

                // Numbers (integer, decimal, exponent, leading-dot).
                if (c.isDigit() || (c == '.' && i + 1 < length && line[i + 1].isDigit())) {
                    val start = i
                    i = consumeNumber(line, i)
                    out.add(TokenSpan(TYPE_NUMBER, start, i - start))
                    continue
                }

                // Identifiers / keywords / builtins / math / booleans.
                if (isIdentifierStart(c)) {
                    val start = i
                    i++
                    while (i < length && isIdentifierPart(line[i])) i++
                    val word = line.subSequence(start, i).toString()
                    out.add(TokenSpan(classifyWord(word), start, i - start))
                    continue
                }

                // Anything else (operators, punctuation): plain text.
                i++
            }

            return blockComment
        }

        /** True when a block-comment terminator begins at [index] in [line]. */
        private fun isBlockCommentClose(line: CharSequence, index: Int, length: Int): Boolean =
            line[index] == '*' && index + 1 < length && line[index + 1] == '/'

        private fun consumeNumber(line: CharSequence, from: Int): Int {
            val length = line.length
            var i = from
            var seenDot = false
            var seenExp = false
            while (i < length) {
                val ch = line[i]
                when {
                    ch.isDigit() -> i++
                    ch == '.' && !seenDot && !seenExp -> {
                        seenDot = true
                        i++
                    }
                    (ch == 'e' || ch == 'E') && !seenExp -> {
                        seenExp = true
                        i++
                        if (i < length && (line[i] == '+' || line[i] == '-')) i++
                    }
                    else -> return i
                }
            }
            return i
        }

        private fun classifyWord(word: String): Int = when {
            word == "true" || word == "false" || word == "undef" -> TYPE_BOOLEAN
            KEYWORD_SET.contains(word) -> TYPE_KEYWORD
            BUILTIN_SET.contains(word) -> TYPE_BUILTIN
            MATH_SET.contains(word) -> TYPE_MATH
            else -> TYPE_NORMAL
        }

        private fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '_'

        private fun isIdentifierPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
    }

    companion object {
        // --- Public span category (color-scheme) IDs -------------------------
        // The later EditorColorScheme task maps each of these ids to a color.
        // Ids start above the reserved range used by sora's built-in scheme.

        /** Default / unclassified text. */
        const val TYPE_NORMAL: Int = 0

        /** OpenSCAD language keywords (module, function, if, for, ...). */
        const val TYPE_KEYWORD: Int = 40

        /** Built-in modules/functions (cube, translate, union, ...). */
        const val TYPE_BUILTIN: Int = 41

        /** Math functions (sin, cos, sqrt, ...). */
        const val TYPE_MATH: Int = 42

        /** Numeric literals. */
        const val TYPE_NUMBER: Int = 43

        /** Boolean/undef literals (true, false, undef). */
        const val TYPE_BOOLEAN: Int = 44

        /** `$`-prefixed special variables ($fn, $fa, $t, ...). */
        const val TYPE_VARIABLE: Int = 45

        /** String literals. */
        const val TYPE_STRING: Int = 46

        /** Line and block comments. */
        const val TYPE_COMMENT: Int = 47

        private val EMPTY_NEWLINE_HANDLERS = emptyArray<NewlineHandler>()

        private val KEYWORD_SET: Set<String> = OpenScadTokens.KEYWORDS.toHashSet()
        private val BUILTIN_SET: Set<String> = OpenScadTokens.BUILTINS.toHashSet()
        private val MATH_SET: Set<String> = OpenScadTokens.MATH_FUNCTIONS.toHashSet()
    }
}
