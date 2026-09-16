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

                // Anything else (operators, punctuation): emit an explicit
                // normal-text span. Without a span here these characters would
                // inherit the color of the preceding token (or none at all),
                // which is why plain/just-typed text could appear invisible.
                val start = i
                i++
                while (i < length) {
                    val ch = line[i]
                    if (ch.isWhitespace()) break
                    if (isIdentifierStart(ch)) break
                    if (ch.isDigit()) break
                    if (ch == '$' || ch == '"') break
                    if (ch == '.' && i + 1 < length && line[i + 1].isDigit()) break
                    if (ch == '/' && i + 1 < length &&
                        (line[i + 1] == '/' || line[i + 1] == '*')
                    ) break
                    i++
                }
                out.add(TokenSpan(TYPE_NORMAL, start, i - start))
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
        // These are sora's STANDARD token color ids (see EditorColorScheme). We
        // deliberately reuse the standard ids rather than inventing custom ones
        // so that BOTH the custom OpenSCAD themes AND sora's bundled themes color
        // OpenSCAD tokens correctly:
        //
        //   * Every bundled scheme (Darcula, VS2019, Eclipse, GitHub, Notepad++)
        //     already defines colors for these standard ids, so OpenSCAD code is
        //     readable on those themes with no per-theme work.
        //   * The custom OpenScadColorScheme / OpenScadLightColorScheme override
        //     these same ids with the VS Code palette for the exact intended look.
        //
        // A previous version used private ids in the 40-47 range, which collided
        // with sora's reserved ids (that range is used internally for snippet /
        // completion / delimiter *backgrounds*). That made tokens render with a
        // background block on the custom themes and rendered them invisible on the
        // bundled themes (which map those ids to backgrounds / leave them unset).
        //
        // Several OpenSCAD categories intentionally share a standard id where the
        // VS Code palette already gives them the same color (keyword == boolean,
        // math == special variable), so no visual distinction is lost.

        /**
         * Default / unclassified text (e.g. plain identifiers, operators, and
         * text being typed before it is classified). Maps to sora's TEXT_NORMAL
         * (id 5), the primary text color every scheme defines.
         *
         * Must NOT be id 0: sora's getColor() returns transparent for unmapped
         * ids, so id 0 would render normal text invisible against the background.
         */
        const val TYPE_NORMAL: Int = 5

        /** Keywords (module, function, if, for, ...). sora KEYWORD. */
        const val TYPE_KEYWORD: Int = 21

        /** Built-in modules/functions (cube, translate, union, ...). sora FUNCTION_NAME. */
        const val TYPE_BUILTIN: Int = 27

        /** Math functions (sin, cos, sqrt, ...). sora IDENTIFIER_VAR. */
        const val TYPE_MATH: Int = 25

        /** Numeric literals. sora LITERAL. */
        const val TYPE_NUMBER: Int = 24

        /** Boolean/undef literals (true, false, undef). sora KEYWORD (same as keywords). */
        const val TYPE_BOOLEAN: Int = 21

        /** `$`-prefixed special variables ($fn, $fa, $t, ...). sora IDENTIFIER_VAR (same as math). */
        const val TYPE_VARIABLE: Int = 25

        /** String literals. sora IDENTIFIER_NAME. */
        const val TYPE_STRING: Int = 26

        /** Line and block comments. sora COMMENT. */
        const val TYPE_COMMENT: Int = 22

        private val EMPTY_NEWLINE_HANDLERS = emptyArray<NewlineHandler>()

        private val KEYWORD_SET: Set<String> = OpenScadTokens.KEYWORDS.toHashSet()
        private val BUILTIN_SET: Set<String> = OpenScadTokens.BUILTINS.toHashSet()
        private val MATH_SET: Set<String> = OpenScadTokens.MATH_FUNCTIONS.toHashSet()
    }
}
