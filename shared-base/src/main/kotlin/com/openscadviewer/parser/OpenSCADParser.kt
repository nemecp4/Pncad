package com.openscadviewer.parser

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Multi-type value system for OpenSCAD variables.
 */
sealed class ScadValue {
    data class Num(val value: Double) : ScadValue()
    data class Str(val value: String) : ScadValue()
    data class Vec(val value: List<ScadValue>) : ScadValue()
    data class Bool(val value: Boolean) : ScadValue()
    object Undef : ScadValue()

    fun toDouble(): Double = when (this) {
        is Num -> value
        is Bool -> if (value) 1.0 else 0.0
        else -> 0.0
    }
}

/**
 * A syntax problem found while parsing, with the 1-based source line it occurred on.
 */
data class ParseError(
    val line: Int,
    val message: String
)

/**
 * Parser for a subset of OpenSCAD language.
 * Supports: cube, sphere, cylinder, translate, rotate, scale, union, difference, intersection,
 * color, linear_extrude, circle, square, polygon, text, module calls, user-defined functions,
 * multi-type variables, ternary operator, string indexing, and array indexing.
 */
class OpenSCADParser {

    private var pos = 0
    private var input = ""
    private val vars = mutableMapOf<String, ScadValue>()
    private val modules = mutableMapOf<String, ModuleDefinition>()
    private val functions = mutableMapOf<String, FunctionDefinition>()
    private var parseStartTime = System.currentTimeMillis()
    private val PARSE_TIMEOUT_MS = 10_000L

    private val _errors = mutableListOf<ParseError>()

    /**
     * Syntax problems collected during the most recent [parse] call, in source order.
     * Empty when the code parsed cleanly.
     */
    val parseErrors: List<ParseError> get() = _errors.toList()

    /**
     * Records a syntax problem at the character [offset] (defaults to the current position),
     * translating the offset into a 1-based line number.
     */
    private fun recordError(message: String, offset: Int = pos) {
        _errors.add(ParseError(line = lineAt(offset), message = message))
    }

    /**
     * Converts a character [offset] into its 1-based line number in [input].
     */
    private fun lineAt(offset: Int): Int {
        val clamped = offset.coerceIn(0, input.length)
        var line = 1
        for (i in 0 until clamped) {
            if (input[i] == '\n') line++
        }
        return line
    }

    companion object {
        private val VAR_ASSIGN_REGEX = Regex("^(\\$?[a-zA-Z_][a-zA-Z0-9_]*)\\s*=")
    }

    /**
     * Backward-compatible variables map. Reading delegates to vars (Num values).
     * Writing stores as Num in vars.
     */
    val variables: MutableMap<String, Double> = object : AbstractMutableMap<String, Double>() {
        override val entries: MutableSet<MutableMap.MutableEntry<String, Double>>
            get() = vars.entries.mapNotNull { (k, v) ->
                object : MutableMap.MutableEntry<String, Double> {
                    override val key = k
                    override val value = v.toDouble()
                    override fun setValue(newValue: Double): Double {
                        val old = v.toDouble()
                        vars[k] = ScadValue.Num(newValue)
                        return old
                    }
                }
            }.toMutableSet()

        override fun put(key: String, value: Double): Double? {
            val old = vars[key]?.toDouble()
            vars[key] = ScadValue.Num(value)
            return old
        }

        override fun get(key: String): Double? {
            return vars[key]?.toDouble()
        }

        override fun containsKey(key: String): Boolean = vars.containsKey(key)

        override fun remove(key: String): Double? {
            val old = vars[key]?.toDouble()
            vars.remove(key)
            return old
        }

        override val size: Int get() = vars.size

        override fun clear() { vars.clear() }
    }

    private data class ModuleDefinition(
        val params: List<String>,
        val defaults: List<String?>,
        val body: String
    )

    private data class FunctionDefinition(
        val params: List<String>,
        val defaults: List<String?>,
        val body: String
    )

    fun parse(code: String): SceneNode {
        input = code
        pos = 0
        vars.clear()
        modules.clear()
        functions.clear()
        _errors.clear()
        parseStartTime = System.currentTimeMillis()

        // Two-pass parsing: first pass scans for module and function definitions
        scanDefinitions(code)

        // Second pass: parse the actual code
        return parseInternal()
    }

    /**
     * Internal parse that preserves pre-set variables, modules, and functions.
     * Used by for-loops and module calls to expand code with context.
     */
    private fun parseWithContext(code: String): SceneNode {
        input = code
        pos = 0
        return parseInternal()
    }

    /**
     * First pass: scan for module and function definitions without fully parsing.
     */
    private fun scanDefinitions(code: String) {
        val savedPos = pos
        val savedInput = input
        input = code
        pos = 0

        while (pos < input.length) {
            checkTimeout()
            skipWhitespaceAndComments()
            if (pos >= input.length) break

            val start = pos
            val id = parseIdentifier()
            if (id == "module") {
                scanModuleDefinition()
            } else if (id == "function") {
                scanFunctionDefinition()
            } else {
                // Skip to next statement
                if (id != null || pos == start) {
                    skipToNextStatement()
                } else {
                    pos++
                }
            }
        }

        input = savedInput
        pos = savedPos
    }

    private fun scanModuleDefinition() {
        skipWhitespaceAndComments()
        val name = parseIdentifier() ?: run { skipToNextStatement(); return }
        skipWhitespaceAndComments()

        val (params, defaults) = parseParamList()
        skipWhitespaceAndComments()

        if (pos < input.length && input[pos] == '{') {
            pos++
            val bodyStart = pos
            var depth = 1
            while (pos < input.length && depth > 0) {
                when (input[pos]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                if (depth > 0) pos++
            }
            val bodyEnd = pos
            if (pos < input.length) pos++
            modules[name] = ModuleDefinition(params, defaults, input.substring(bodyStart, bodyEnd))
        } else {
            val bodyStart = pos
            skipToNextStatement()
            modules[name] = ModuleDefinition(params, defaults, input.substring(bodyStart, pos))
        }
    }

    private fun scanFunctionDefinition() {
        skipWhitespaceAndComments()
        val name = parseIdentifier() ?: run { skipToNextStatement(); return }
        skipWhitespaceAndComments()

        val (params, defaults) = parseParamList()
        skipWhitespaceAndComments()

        // function name(params) = expr;
        if (pos < input.length && input[pos] == '=') {
            pos++
            skipWhitespaceAndComments()
            val bodyStart = pos
            // Read until semicolon at depth 0
            var depth = 0
            while (pos < input.length) {
                when (input[pos]) {
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    ';' -> if (depth <= 0) break
                }
                pos++
            }
            val body = input.substring(bodyStart, pos).trim()
            if (pos < input.length && input[pos] == ';') pos++
            functions[name] = FunctionDefinition(params, defaults, body)
        } else {
            skipToNextStatement()
        }
    }

    private fun parseParamList(): Pair<List<String>, List<String?>> {
        val params = mutableListOf<String>()
        val defaults = mutableListOf<String?>()
        if (pos < input.length && input[pos] == '(') {
            pos++
            skipWhitespaceAndComments()
            while (pos < input.length && input[pos] != ')') {
                val param = parseIdentifier()
                if (param != null) params.add(param)
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == '=') {
                    pos++
                    skipWhitespaceAndComments()
                    val defStart = pos
                    var depth = 0
                    while (pos < input.length) {
                        when (input[pos]) {
                            '(', '[' -> depth++
                            ')', ']' -> { if (depth == 0) break; depth-- }
                            ',' -> { if (depth == 0) break }
                        }
                        pos++
                    }
                    defaults.add(input.substring(defStart, pos).trim())
                } else {
                    defaults.add(null)
                }
                if (pos < input.length && input[pos] == ',') pos++
                skipWhitespaceAndComments()
            }
            if (pos < input.length && input[pos] == ')') pos++
        }
        return Pair(params, defaults)
    }

    private fun parseInternal(): SceneNode {
        val children = mutableListOf<SceneNode>()

        while (pos < input.length) {
            checkTimeout()
            skipWhitespaceAndComments()
            if (pos >= input.length) break

            val node = parseStatement()
            if (node != null) {
                children.add(node)
            }
        }

        return SceneNode.Group(children)
    }

    private fun checkTimeout() {
        if (System.currentTimeMillis() - parseStartTime > PARSE_TIMEOUT_MS) {
            throw RuntimeException("Parser timeout exceeded ${PARSE_TIMEOUT_MS}ms")
        }
    }

    private fun parseStatement(): SceneNode? {
        skipWhitespaceAndComments()
        if (pos >= input.length) return null

        // Skip # debug modifier
        if (input[pos] == '#') {
            pos++
            skipWhitespaceAndComments()
        }

        // Check for variable assignment (including $fn, $fa, etc.)
        val lookAhead = input.substring(pos, minOf(pos + 80, input.length))
        val varMatch = VAR_ASSIGN_REGEX.find(lookAhead)
        if (varMatch != null) {
            val varName = varMatch.groupValues[1]
            pos += varMatch.value.length
            skipWhitespaceAndComments()
            val value = parseExpressionValue()
            vars[varName] = value
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ';') pos++
            return null
        }

        val identifierStart = pos
        val identifier = parseIdentifier() ?: run {
            // A statement that begins with something that isn't an identifier,
            // assignment, or modifier is a syntax error. Skip the stray character(s).
            recordError("Unexpected token '${input[pos]}'", pos)
            skipToNextStatement()
            return null
        }
        skipWhitespaceAndComments()

        return when (identifier) {
            "cube" -> parseCube()
            "sphere" -> parseSphere()
            "cylinder" -> parseCylinder()
            "translate" -> parseTransform(identifier)
            "rotate" -> parseTransform(identifier)
            "scale" -> parseTransform(identifier)
            "union" -> parseCSGOperation(identifier)
            "difference" -> parseCSGOperation(identifier)
            "intersection" -> parseCSGOperation(identifier)
            "color" -> parseColor()
            "linear_extrude" -> parseLinearExtrude()
            "circle" -> parseCircle()
            "square" -> parseSquare()
            "polygon" -> parsePolygon()
            "hull" -> parseCSGOperation(identifier)
            "minkowski" -> parseCSGOperation(identifier)
            "text" -> parseText()
            "module" -> { skipModuleDefinition(); null }
            "function" -> { skipFunctionDefinition(); null }
            "for" -> parseForLoop()
            else -> {
                if (modules.containsKey(identifier)) {
                    parseModuleCall(identifier)
                } else {
                    // Unknown identifier — flag it as a syntax error, then skip past it
                    // so parsing can continue and report further problems.
                    recordError("Unknown command or identifier '$identifier'", identifierStart)
                    skipToNextStatement()
                    null
                }
            }
        }
    }

    // Skip module definition in second pass (already scanned in first pass)
    private fun skipModuleDefinition() {
        skipWhitespaceAndComments()
        parseIdentifier() // name
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '(') {
            pos++; skipToCloseParen()
        }
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '{') {
            pos++
            var depth = 1
            while (pos < input.length && depth > 0) {
                when (input[pos]) { '{' -> depth++; '}' -> depth-- }
                if (depth > 0) pos++
            }
            if (pos < input.length) pos++
        } else {
            skipToNextStatement()
        }
    }

    // Skip function definition in second pass (already scanned in first pass)
    private fun skipFunctionDefinition() {
        skipWhitespaceAndComments()
        parseIdentifier() // name
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '(') {
            pos++; skipToCloseParen()
        }
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '=') {
            pos++
            var depth = 0
            while (pos < input.length) {
                when (input[pos]) {
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    ';' -> if (depth <= 0) { pos++; return }
                }
                pos++
            }
        } else {
            skipToNextStatement()
        }
    }

    // --- Expression evaluation returning ScadValue ---

    private fun parseExpressionValue(): ScadValue {
        skipWhitespaceAndComments()
        if (pos >= input.length) return ScadValue.Num(0.0)

        // Array literal
        if (input[pos] == '[') {
            return parseArrayValue()
        }

        // String literal
        if (input[pos] == '"') {
            return ScadValue.Str(parseStringLiteral())
        }

        // Try to resolve identifier directly for string/vec values
        val savedPos = pos
        val id = parseIdentifier()
        if (id != null) {
            skipWhitespaceAndComments()
            // Check for indexing
            if (pos < input.length && input[pos] == '[') {
                val varVal = vars[id]
                pos++ // skip [
                val index = parseExpression().toInt()
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == ']') pos++
                return when (varVal) {
                    is ScadValue.Vec -> {
                        if (index in varVal.value.indices) varVal.value[index]
                        else ScadValue.Undef
                    }
                    is ScadValue.Str -> {
                        if (index in varVal.value.indices)
                            ScadValue.Str(varVal.value[index].toString())
                        else ScadValue.Undef
                    }
                    else -> ScadValue.Undef
                }
            }
            // Variable that's already a string or vec
            val varVal = vars[id]
            if (varVal is ScadValue.Str || varVal is ScadValue.Vec) {
                return varVal
            }
            // Restore and parse as expression
            pos = savedPos
        } else {
            pos = savedPos
        }

        // Otherwise parse as expression (which may return via ternary etc.)
        val result = parseExpression()
        return ScadValue.Num(result)
    }

    private fun parseArrayValue(): ScadValue {
        if (pos >= input.length || input[pos] != '[') return ScadValue.Vec(emptyList())
        pos++ // skip [
        skipWhitespaceAndComments()

        val elements = mutableListOf<ScadValue>()
        while (pos < input.length && input[pos] != ']') {
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ']') break
            elements.add(parseExpressionValue())
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ',') pos++
            skipWhitespaceAndComments()
        }
        if (pos < input.length) pos++ // skip ]
        return ScadValue.Vec(elements)
    }

    private fun parseStringLiteral(): String {
        if (pos >= input.length || input[pos] != '"') return ""
        pos++ // skip opening "
        val sb = StringBuilder()
        while (pos < input.length && input[pos] != '"') {
            if (input[pos] == '\\' && pos + 1 < input.length) {
                pos++
                when (input[pos]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    else -> { sb.append('\\'); sb.append(input[pos]) }
                }
            } else {
                sb.append(input[pos])
            }
            pos++
        }
        if (pos < input.length) pos++ // skip closing "
        return sb.toString()
    }

    // --- Numeric expression evaluation ---

    fun parseExpression(): Double {
        return parseExpressionScad().toDouble()
    }

    /**
     * Full ScadValue-aware expression parser that handles string comparisons,
     * ternary operators, and all numeric operations correctly.
     */
    private fun parseExpressionScad(): ScadValue {
        skipWhitespaceAndComments()
        val result = parseComparisonScad()

        // Ternary operator — handle iteratively for nested ternaries
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '?') {
            pos++
            skipWhitespaceAndComments()
            val condTrue = when (result) {
                is ScadValue.Num -> result.value != 0.0
                is ScadValue.Bool -> result.value
                is ScadValue.Str -> result.value.isNotEmpty()
                is ScadValue.Vec -> result.value.isNotEmpty()
                is ScadValue.Undef -> false
            }
            if (condTrue) {
                // Condition is true: evaluate true branch, skip false branch
                val trueVal = parseExpressionScad()
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == ':') pos++
                skipWhitespaceAndComments()
                skipExpressionValue() // skip false branch without deep evaluation
                return trueVal
            } else {
                // Condition is false: skip true branch, evaluate false branch
                skipExpressionValue() // skip true branch
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == ':') pos++
                skipWhitespaceAndComments()
                return parseExpressionScad() // evaluate false branch (tail call for chain)
            }
        }

        return result
    }

    /**
     * Skip over an expression value without evaluating it deeply.
     * Handles numbers, identifiers, parenthesized exprs, nested ternaries.
     */
    private fun skipExpressionValue() {
        skipWhitespaceAndComments()
        if (pos >= input.length) return

        // We need to skip a complete expression (which may include nested ternaries)
        // Strategy: count balanced parens/brackets and stop at ':' or ')' or ']' at depth 0
        var depth = 0
        var ternaryDepth = 0
        while (pos < input.length) {
            when (input[pos]) {
                '(' , '[' -> { depth++; pos++ }
                ')' , ']' -> {
                    if (depth == 0) return
                    depth--; pos++
                }
                '?' -> { ternaryDepth++; pos++ }
                ':' -> {
                    if (depth == 0 && ternaryDepth == 0) return
                    if (ternaryDepth > 0) ternaryDepth--
                    pos++
                }
                ';', ',' -> { if (depth == 0) return; pos++ }
                '}' -> { if (depth == 0) return; pos++ }
                '"' -> { pos++; while (pos < input.length && input[pos] != '"') { if (input[pos] == '\\') pos++; pos++ }; if (pos < input.length) pos++ }
                else -> pos++
            }
        }
    }

    /**
     * ScadValue-aware comparison that correctly handles string == string.
     */
    private fun parseComparisonScad(): ScadValue {
        val left = parseFactorScad()

        skipWhitespaceAndComments()
        if (pos >= input.length) return left

        // Check for comparison operators
        when {
            pos + 1 < input.length && input[pos] == '=' && input[pos + 1] == '=' -> {
                pos += 2
                val right = parseFactorScad()
                return ScadValue.Bool(scadValuesEqual(left, right))
            }
            pos + 1 < input.length && input[pos] == '!' && input[pos + 1] == '=' -> {
                pos += 2
                val right = parseFactorScad()
                return ScadValue.Bool(!scadValuesEqual(left, right))
            }
            pos + 1 < input.length && input[pos] == '<' && input[pos + 1] == '=' -> {
                pos += 2
                val right = parseFactorScad()
                return ScadValue.Bool(left.toDouble() <= right.toDouble())
            }
            pos + 1 < input.length && input[pos] == '>' && input[pos + 1] == '=' -> {
                pos += 2
                val right = parseFactorScad()
                return ScadValue.Bool(left.toDouble() >= right.toDouble())
            }
            input[pos] == '<' && !(pos + 1 < input.length && input[pos + 1] == '=') -> {
                pos++
                val right = parseFactorScad()
                return ScadValue.Bool(left.toDouble() < right.toDouble())
            }
            input[pos] == '>' && !(pos + 1 < input.length && input[pos + 1] == '=') -> {
                pos++
                val right = parseFactorScad()
                return ScadValue.Bool(left.toDouble() > right.toDouble())
            }
        }

        // No comparison operator found, check for arithmetic continuing from left
        // We need to handle the case where left is numeric and we have +, -, *, /
        return applyArithmeticScad(left)
    }

    /**
     * Apply arithmetic operations (+, -, *, /) to a ScadValue, continuing the parse.
     * This handles the case where the comparison layer sees no comparator but arithmetic follows.
     */
    private fun applyArithmeticScad(initial: ScadValue): ScadValue {
        var result = initial.toDouble()

        // Handle multiplication/division that may follow
        while (pos < input.length) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break
            when (input[pos]) {
                '*' -> { pos++; result *= parseFactorDouble() }
                '/' -> { pos++; val d = parseFactorDouble(); if (d != 0.0) result /= d }
                '%' -> { pos++; val d = parseFactorDouble(); if (d != 0.0) result %= d }
                else -> break
            }
        }

        // Handle addition/subtraction
        while (pos < input.length) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break
            when (input[pos]) {
                '+' -> { pos++; result += parseTerm() }
                '-' -> { pos++; result -= parseTerm() }
                else -> break
            }
        }

        // After arithmetic, check for comparison operators
        skipWhitespaceAndComments()
        if (pos < input.length) {
            when {
                pos + 1 < input.length && input[pos] == '=' && input[pos + 1] == '=' -> {
                    pos += 2
                    val right = parseFactorScad()
                    val rightVal = applyArithmeticScad(right).toDouble()
                    return ScadValue.Bool(result == rightVal)
                }
                pos + 1 < input.length && input[pos] == '!' && input[pos + 1] == '=' -> {
                    pos += 2
                    val right = parseFactorScad()
                    val rightVal = applyArithmeticScad(right).toDouble()
                    return ScadValue.Bool(result != rightVal)
                }
                pos + 1 < input.length && input[pos] == '<' && input[pos + 1] == '=' -> {
                    pos += 2
                    val rightVal = parseAddSub()
                    return ScadValue.Bool(result <= rightVal)
                }
                pos + 1 < input.length && input[pos] == '>' && input[pos + 1] == '=' -> {
                    pos += 2
                    val rightVal = parseAddSub()
                    return ScadValue.Bool(result >= rightVal)
                }
                input[pos] == '<' && !(pos + 1 < input.length && input[pos + 1] == '=') -> {
                    pos++
                    val rightVal = parseAddSub()
                    return ScadValue.Bool(result < rightVal)
                }
                input[pos] == '>' && !(pos + 1 < input.length && input[pos + 1] == '=') -> {
                    pos++
                    val rightVal = parseAddSub()
                    return ScadValue.Bool(result > rightVal)
                }
            }
        }

        if (initial is ScadValue.Str) return initial
        return ScadValue.Num(result)
    }

    private fun scadValuesEqual(a: ScadValue, b: ScadValue): Boolean {
        return when {
            a is ScadValue.Str && b is ScadValue.Str -> a.value == b.value
            a is ScadValue.Num && b is ScadValue.Num -> a.value == b.value
            a is ScadValue.Bool && b is ScadValue.Bool -> a.value == b.value
            a is ScadValue.Undef && b is ScadValue.Undef -> true
            // Cross-type: compare as doubles for numeric types
            a is ScadValue.Num || a is ScadValue.Bool -> {
                if (b is ScadValue.Num || b is ScadValue.Bool) a.toDouble() == b.toDouble()
                else false
            }
            else -> false
        }
    }

    /**
     * Parse a single factor as ScadValue — preserves strings and handles identifiers
     * with their ScadValue type intact (important for string comparisons).
     */
    private fun parseFactorScad(): ScadValue {
        skipWhitespaceAndComments()
        if (pos >= input.length) return ScadValue.Num(0.0)

        // Unary minus
        if (input[pos] == '-') {
            pos++
            return ScadValue.Num(-parseFactorDouble())
        }

        // Unary not
        if (input[pos] == '!') {
            pos++
            val v = parseFactorDouble()
            return ScadValue.Bool(v == 0.0)
        }

        // Parenthesized expression
        if (input[pos] == '(') {
            pos++
            val result = parseExpressionScad()
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ')') pos++
            return result
        }

        // Number
        if (input[pos].isDigit() || input[pos] == '.') {
            return ScadValue.Num(parseNumber())
        }

        // String literal
        if (input[pos] == '"') {
            return ScadValue.Str(parseStringLiteral())
        }

        // Variable or function — resolve to ScadValue
        val id = parseIdentifier()
        if (id != null) {
            return resolveIdentifierScad(id)
        }

        return ScadValue.Num(0.0)
    }

    /**
     * Parse factor returning Double — used by arithmetic operations.
     */
    private fun parseFactorDouble(): Double {
        return parseFactorScad().toDouble()
    }

    /**
     * Resolve an identifier to its ScadValue — preserves string type for comparisons.
     */
    private fun resolveIdentifierScad(id: String): ScadValue {
        when (id) {
            "true" -> return ScadValue.Bool(true)
            "false" -> return ScadValue.Bool(false)
            "PI" -> return ScadValue.Num(PI)
            "sin" -> return ScadValue.Num(sin(parseFunctionArg() * PI / 180.0))
            "cos" -> return ScadValue.Num(cos(parseFunctionArg() * PI / 180.0))
            "abs" -> return ScadValue.Num(kotlin.math.abs(parseFunctionArg()))
            "sqrt" -> return ScadValue.Num(kotlin.math.sqrt(parseFunctionArg()))
            "floor" -> return ScadValue.Num(kotlin.math.floor(parseFunctionArg()))
            "ceil" -> return ScadValue.Num(kotlin.math.ceil(parseFunctionArg()))
            "round" -> return ScadValue.Num(kotlin.math.round(parseFunctionArg()).toDouble())
            "pow" -> {
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == '(') {
                    pos++
                    val base = parseExpression()
                    skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == ',') pos++
                    val exp = parseExpression()
                    skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == ')') pos++
                    return ScadValue.Num(Math.pow(base, exp))
                }
                return ScadValue.Num(0.0)
            }
            "max" -> return ScadValue.Num(parseVarArgFunc { a, b -> maxOf(a, b) })
            "min" -> return ScadValue.Num(parseVarArgFunc { a, b -> minOf(a, b) })
            "len" -> {
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == '(') {
                    pos++
                    skipWhitespaceAndComments()
                    val argVal = parseExpressionValueInner()
                    skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == ')') pos++
                    return when (argVal) {
                        is ScadValue.Vec -> ScadValue.Num(argVal.value.size.toDouble())
                        is ScadValue.Str -> ScadValue.Num(argVal.value.length.toDouble())
                        else -> ScadValue.Num(0.0)
                    }
                }
                return ScadValue.Num(0.0)
            }
            "str" -> {
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == '(') {
                    pos++
                    skipWhitespaceAndComments()
                    val sb = StringBuilder()
                    while (pos < input.length && input[pos] != ')') {
                        val argVal = parseExpressionValueInner()
                        when (argVal) {
                            is ScadValue.Str -> sb.append(argVal.value)
                            is ScadValue.Num -> sb.append(argVal.value)
                            else -> sb.append("")
                        }
                        skipWhitespaceAndComments()
                        if (pos < input.length && input[pos] == ',') pos++
                        skipWhitespaceAndComments()
                    }
                    if (pos < input.length && input[pos] == ')') pos++
                    return ScadValue.Str(sb.toString())
                }
                return ScadValue.Str("")
            }
        }

        // Check user-defined functions — return ScadValue
        if (functions.containsKey(id)) {
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == '(') {
                return callFunctionScad(id)
            }
        }

        // Check if this is an unknown function call — consume args
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '(') {
            // Not a known function - check if it's really a function call
            val varVal = vars[id]
            if (varVal == null || varVal is ScadValue.Undef) {
                pos++
                skipToCloseParen()
                return ScadValue.Num(0.0)
            }
        }

        // Array/string indexing
        if (pos < input.length && input[pos] == '[') {
            val varVal = vars[id]
            pos++ // skip [
            val index = parseExpression().toInt()
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ']') pos++
            return when (varVal) {
                is ScadValue.Vec -> {
                    if (index in varVal.value.indices) varVal.value[index]
                    else ScadValue.Undef
                }
                is ScadValue.Str -> {
                    if (index in varVal.value.indices) ScadValue.Str(varVal.value[index].toString())
                    else ScadValue.Undef
                }
                else -> ScadValue.Undef
            }
        }

        // Variable lookup — return full ScadValue
        return vars[id] ?: ScadValue.Num(0.0)
    }

    private fun parseComparison(): Double {
        var result = parseAddSub()

        while (pos < input.length) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break
            when {
                pos + 1 < input.length && input[pos] == '<' && input[pos + 1] == '=' -> {
                    pos += 2; result = if (result <= parseAddSub()) 1.0 else 0.0
                }
                pos + 1 < input.length && input[pos] == '>' && input[pos + 1] == '=' -> {
                    pos += 2; result = if (result >= parseAddSub()) 1.0 else 0.0
                }
                pos + 1 < input.length && input[pos] == '=' && input[pos + 1] == '=' -> {
                    pos += 2; result = if (result == parseAddSub()) 1.0 else 0.0
                }
                pos + 1 < input.length && input[pos] == '!' && input[pos + 1] == '=' -> {
                    pos += 2; result = if (result != parseAddSub()) 1.0 else 0.0
                }
                input[pos] == '<' && !(pos + 1 < input.length && input[pos + 1] == '=') -> {
                    pos++; result = if (result < parseAddSub()) 1.0 else 0.0
                }
                input[pos] == '>' && !(pos + 1 < input.length && input[pos + 1] == '=') -> {
                    pos++; result = if (result > parseAddSub()) 1.0 else 0.0
                }
                else -> break
            }
        }
        return result
    }

    private fun parseAddSub(): Double {
        skipWhitespaceAndComments()
        var result = parseTerm()

        while (pos < input.length) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break
            when (input[pos]) {
                '+' -> { pos++; result += parseTerm() }
                '-' -> { pos++; result -= parseTerm() }
                else -> break
            }
        }
        return result
    }

    private fun parseTerm(): Double {
        skipWhitespaceAndComments()
        var result = parseFactor()

        while (pos < input.length) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break
            when (input[pos]) {
                '*' -> { pos++; result *= parseFactor() }
                '/' -> { pos++; val d = parseFactor(); if (d != 0.0) result /= d }
                '%' -> { pos++; val d = parseFactor(); if (d != 0.0) result %= d }
                else -> break
            }
        }
        return result
    }

    private fun parseFactor(): Double {
        skipWhitespaceAndComments()
        if (pos >= input.length) return 0.0

        // Unary minus
        if (input[pos] == '-') {
            pos++
            return -parseFactor()
        }

        // Unary not
        if (input[pos] == '!') {
            pos++
            val v = parseFactor()
            return if (v == 0.0) 1.0 else 0.0
        }

        // Parenthesized expression
        if (input[pos] == '(') {
            pos++
            val result = parseExpression()
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ')') pos++
            return result
        }

        // Number
        if (input[pos].isDigit() || input[pos] == '.') {
            return parseNumber()
        }

        // String literal in expression context — compare etc.
        if (input[pos] == '"') {
            val str = parseStringLiteral()
            // String comparison will be handled at ScadValue level
            // For numeric context, return 0
            return 0.0
        }

        // Variable or function
        val id = parseIdentifier()
        if (id != null) {
            return resolveIdentifier(id)
        }

        return 0.0
    }

    private fun resolveIdentifier(id: String): Double {
        when (id) {
            "true" -> return 1.0
            "false" -> return 0.0
            "PI" -> return PI
            "sin" -> return sin(parseFunctionArg() * PI / 180.0)
            "cos" -> return cos(parseFunctionArg() * PI / 180.0)
            "abs" -> return kotlin.math.abs(parseFunctionArg())
            "sqrt" -> return kotlin.math.sqrt(parseFunctionArg())
            "floor" -> return kotlin.math.floor(parseFunctionArg())
            "ceil" -> return kotlin.math.ceil(parseFunctionArg())
            "round" -> return kotlin.math.round(parseFunctionArg()).toDouble()
            "pow" -> {
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == '(') {
                    pos++
                    val base = parseExpression()
                    skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == ',') pos++
                    val exp = parseExpression()
                    skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == ')') pos++
                    return Math.pow(base, exp)
                }
                return 0.0
            }
            "max" -> return parseVarArgFunc { a, b -> maxOf(a, b) }
            "min" -> return parseVarArgFunc { a, b -> minOf(a, b) }
            "len" -> {
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == '(') {
                    pos++
                    skipWhitespaceAndComments()
                    val argVal = parseExpressionValueInner()
                    skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == ')') pos++
                    return when (argVal) {
                        is ScadValue.Vec -> argVal.value.size.toDouble()
                        is ScadValue.Str -> argVal.value.length.toDouble()
                        else -> 0.0
                    }
                }
                return 0.0
            }
            "str" -> {
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == '(') {
                    pos++
                    skipWhitespaceAndComments()
                    // Just consume all args and return 0 (string result in numeric context)
                    while (pos < input.length && input[pos] != ')') {
                        parseExpression()
                        skipWhitespaceAndComments()
                        if (pos < input.length && input[pos] == ',') pos++
                        skipWhitespaceAndComments()
                    }
                    if (pos < input.length && input[pos] == ')') pos++
                }
                return 0.0
            }
        }

        // Check user-defined functions
        if (functions.containsKey(id)) {
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == '(') {
                return callFunction(id)
            }
        }

        // Check if this is an unknown function call — consume args
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '(') {
            pos++
            skipToCloseParen()
            return 0.0
        }

        // Array/string indexing
        if (pos < input.length && input[pos] == '[') {
            val varVal = vars[id]
            pos++ // skip [
            val index = parseExpression().toInt()
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ']') pos++
            return when (varVal) {
                is ScadValue.Vec -> {
                    if (index in varVal.value.indices) varVal.value[index].toDouble()
                    else 0.0
                }
                is ScadValue.Str -> 0.0 // string char - no numeric meaning
                else -> 0.0
            }
        }

        // Variable lookup
        return vars[id]?.toDouble() ?: 0.0
    }

    /**
     * Parse expression that returns ScadValue (used for len() args, array indexing, etc.)
     */
    private fun parseExpressionValueInner(): ScadValue {
        skipWhitespaceAndComments()
        if (pos >= input.length) return ScadValue.Num(0.0)

        // String literal
        if (input[pos] == '"') {
            return ScadValue.Str(parseStringLiteral())
        }

        // Array literal
        if (input[pos] == '[') {
            return parseArrayValue()
        }

        // Try to resolve as identifier directly to get its ScadValue
        val savedPos = pos
        val id = parseIdentifier()
        if (id != null) {
            skipWhitespaceAndComments()
            // Check for indexing
            if (pos < input.length && input[pos] == '[') {
                val varVal = vars[id]
                pos++ // skip [
                val index = parseExpression().toInt()
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == ']') pos++
                return when (varVal) {
                    is ScadValue.Vec -> {
                        if (index in varVal.value.indices) varVal.value[index]
                        else ScadValue.Undef
                    }
                    is ScadValue.Str -> {
                        if (index in varVal.value.indices)
                            ScadValue.Str(varVal.value[index].toString())
                        else ScadValue.Undef
                    }
                    else -> ScadValue.Undef
                }
            }
            // Check if it's a function call
            if (pos < input.length && input[pos] == '(') {
                // Restore and parse as expression
                pos = savedPos
                return ScadValue.Num(parseExpression())
            }
            // Variable lookup returning full value
            val v = vars[id]
            if (v != null) return v
            // It might be a constant
            return when (id) {
                "true" -> ScadValue.Bool(true)
                "false" -> ScadValue.Bool(false)
                else -> ScadValue.Num(0.0)
            }
        }

        // Fallback to numeric
        pos = savedPos
        return ScadValue.Num(parseExpression())
    }

    /**
     * Call a user-defined function.
     */
    private fun callFunction(name: String): Double {
        return callFunctionScad(name).toDouble()
    }

    /**
     * Call a user-defined function returning ScadValue.
     */
    private fun callFunctionScad(name: String): ScadValue {
        val funcDef = functions[name] ?: return ScadValue.Num(0.0)
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') return ScadValue.Num(0.0)
        pos++ // skip (

        // Parse arguments
        val args = mutableListOf<ScadValue>()
        skipWhitespaceAndComments()
        while (pos < input.length && input[pos] != ')') {
            args.add(parseExpressionValueForFuncArg())
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ',') pos++
            skipWhitespaceAndComments()
        }
        if (pos < input.length && input[pos] == ')') pos++

        // Evaluate the function body with params bound
        return evalFunctionBodyScad(funcDef, args)
    }

    private fun parseExpressionValueForFuncArg(): ScadValue {
        skipWhitespaceAndComments()
        if (pos >= input.length) return ScadValue.Num(0.0)
        if (input[pos] == '"') return ScadValue.Str(parseStringLiteral())
        if (input[pos] == '[') return parseArrayValue()

        // Check for identifier that might resolve to a ScadValue (string, vec, etc.)
        val savedPos = pos
        val id = parseIdentifier()
        if (id != null) {
            skipWhitespaceAndComments()
            // Check for indexing - returns the element's ScadValue
            if (pos < input.length && input[pos] == '[') {
                val varVal = vars[id]
                pos++ // skip [
                val index = parseExpression().toInt()
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == ']') pos++
                return when (varVal) {
                    is ScadValue.Vec -> {
                        if (index in varVal.value.indices) varVal.value[index]
                        else ScadValue.Undef
                    }
                    is ScadValue.Str -> {
                        if (index in varVal.value.indices)
                            ScadValue.Str(varVal.value[index].toString())
                        else ScadValue.Undef
                    }
                    else -> ScadValue.Undef
                }
            }
            // Variable that's a string or vec - return as-is
            val varVal = vars[id]
            if (varVal is ScadValue.Str || varVal is ScadValue.Vec) {
                return varVal
            }
            // Otherwise restore and parse as numeric expression
            pos = savedPos
        } else {
            pos = savedPos
        }
        return ScadValue.Num(parseExpression())
    }

    private fun evalFunctionBody(funcDef: FunctionDefinition, args: List<ScadValue>): Double {
        return evalFunctionBodyScad(funcDef, args).toDouble()
    }

    private fun evalFunctionBodyScad(funcDef: FunctionDefinition, args: List<ScadValue>): ScadValue {
        checkTimeout()
        val subParser = OpenSCADParser()
        subParser.parseStartTime = this.parseStartTime
        subParser.vars.putAll(vars)
        subParser.modules.putAll(modules)
        subParser.functions.putAll(functions)

        // Bind parameters
        for (i in funcDef.params.indices) {
            val paramName = funcDef.params[i]
            val value = if (i < args.size) args[i]
            else {
                // Try default value
                val def = funcDef.defaults.getOrNull(i)
                if (def != null) {
                    val defParser = OpenSCADParser()
                    defParser.parseStartTime = parseStartTime
                    defParser.vars.putAll(subParser.vars)
                    defParser.functions.putAll(functions)
                    defParser.input = def
                    defParser.pos = 0
                    ScadValue.Num(defParser.parseExpression())
                } else ScadValue.Num(0.0)
            }
            subParser.vars[paramName] = value
        }

        subParser.input = funcDef.body
        subParser.pos = 0
        return subParser.parseExpressionScad()
    }

    /**
     * Evaluate a function call returning ScadValue (for string indexing in functions).
     */
    private fun callFunctionValue(name: String): ScadValue {
        val funcDef = functions[name] ?: return ScadValue.Num(0.0)
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') return ScadValue.Num(0.0)
        pos++ // skip (

        val args = mutableListOf<ScadValue>()
        skipWhitespaceAndComments()
        while (pos < input.length && input[pos] != ')') {
            args.add(parseExpressionValueForFuncArg())
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ',') pos++
            skipWhitespaceAndComments()
        }
        if (pos < input.length && input[pos] == ')') pos++

        return evalFunctionBodyScad(funcDef, args)
    }

    private fun parseVarArgFunc(op: (Double, Double) -> Double): Double {
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') return 0.0
        pos++
        val values = mutableListOf<Double>()
        skipWhitespaceAndComments()
        while (pos < input.length && input[pos] != ')') {
            values.add(parseExpression())
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ',') pos++
            skipWhitespaceAndComments()
        }
        if (pos < input.length && input[pos] == ')') pos++
        return if (values.isEmpty()) 0.0
        else values.reduce(op)
    }

    private fun parseFunctionArg(): Double {
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '(') {
            pos++
            val result = parseExpression()
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ')') pos++
            return result
        }
        return 0.0
    }

    // --- Geometry parsing ---

    private fun parseCube(): SceneNode {
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') {
            return SceneNode.Cube(1.0, 1.0, 1.0, false)
        }
        pos++ // skip (

        var sizeX = 1.0
        var sizeY = 1.0
        var sizeZ = 1.0
        var center = false

        skipWhitespaceAndComments()

        if (pos < input.length && input[pos] == '[') {
            val vec = parseVector()
            if (vec.size >= 3) {
                sizeX = vec[0]; sizeY = vec[1]; sizeZ = vec[2]
            }
        } else {
            val params = parseNamedParams()
            if (params.containsKey("size")) {
                sizeX = params["size"] ?: 1.0; sizeY = sizeX; sizeZ = sizeX
            } else if (pos < input.length && input[pos] != ')') {
                val size = parseExpression()
                sizeX = size; sizeY = size; sizeZ = size
            }
        }

        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == ',') {
            pos++; skipWhitespaceAndComments()
            val paramCheck = input.substring(pos)
            if (paramCheck.startsWith("center")) {
                pos += "center".length; skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == '=') {
                    pos++; skipWhitespaceAndComments()
                    center = parseBooleanValue()
                }
            } else {
                center = parseBooleanValue()
            }
        }

        skipToCloseParen()
        skipSemicolon()
        return SceneNode.Cube(sizeX, sizeY, sizeZ, center)
    }

    private fun parseSphere(): SceneNode {
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') {
            return SceneNode.Sphere(1.0, getSegments())
        }
        pos++

        var radius = 1.0
        skipWhitespaceAndComments()

        if (pos < input.length && input[pos] != ')') {
            val paramStr = input.substring(pos)
            val rMatch = Regex("^r\\s*=\\s*").find(paramStr)
            val dMatch = Regex("^d\\s*=\\s*").find(paramStr)
            if (rMatch != null) {
                pos += rMatch.value.length; radius = parseExpression()
            } else if (dMatch != null) {
                pos += dMatch.value.length; radius = parseExpression() / 2.0
            } else {
                radius = parseExpression()
            }
        }

        skipToCloseParen()
        skipSemicolon()
        return SceneNode.Sphere(radius, getSegments())
    }

    private fun parseCylinder(): SceneNode {
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') {
            return SceneNode.Cylinder(1.0, 1.0, 1.0, false, getSegments())
        }
        pos++

        var h = 1.0; var r1 = 1.0; var r2 = 1.0; var center = false

        skipWhitespaceAndComments()
        val paramsStr = extractParenContent()
        val params = parseParamString(paramsStr)

        h = params["h"] ?: params["height"] ?: params["_0"] ?: 1.0
        val r = params["r"]
        val d = params["d"]
        r1 = params["r1"] ?: r ?: (d?.div(2.0)) ?: (params["d1"]?.div(2.0)) ?: 1.0
        r2 = params["r2"] ?: r ?: (d?.div(2.0)) ?: (params["d2"]?.div(2.0)) ?: r1

        if (params.containsKey("center")) {
            center = (params["center"] ?: 0.0) != 0.0
        }

        // Use $fn parameter if specified, otherwise fall back to global $fn
        val segments = params["\$fn"]?.toInt()?.coerceIn(3, 360) ?: getSegments()

        skipSemicolon()
        return SceneNode.Cylinder(h, r1, r2, center, segments)
    }

    private fun parseTransform(type: String): SceneNode? {
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') return null
        pos++

        val vec = mutableListOf(0.0, 0.0, 0.0)
        skipWhitespaceAndComments()

        if (pos < input.length && input[pos] == '[') {
            val parsedVec = parseVector()
            for (i in parsedVec.indices.take(3)) {
                vec[i] = parsedVec[i]
            }
        } else {
            val paramStr = input.substring(pos)
            val namedMatch = Regex("^v\\s*=\\s*").find(paramStr)
            if (namedMatch != null) {
                pos += namedMatch.value.length
                if (pos < input.length && input[pos] == '[') {
                    val parsedVec = parseVector()
                    for (i in parsedVec.indices.take(3)) { vec[i] = parsedVec[i] }
                }
            } else {
                val value = parseExpression()
                if (type == "scale") {
                    vec[0] = value; vec[1] = value; vec[2] = value
                } else {
                    vec[2] = value
                }
            }
        }

        skipToCloseParen()
        skipWhitespaceAndComments()

        val child = parseBlockOrStatement() ?: return null

        return when (type) {
            "translate" -> SceneNode.Translate(vec[0], vec[1], vec[2], child)
            "rotate" -> SceneNode.Rotate(vec[0], vec[1], vec[2], child)
            "scale" -> SceneNode.Scale(vec[0], vec[1], vec[2], child)
            else -> child
        }
    }

    private fun parseCSGOperation(type: String): SceneNode? {
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '(') {
            pos++; skipToCloseParen()
        }
        skipWhitespaceAndComments()

        val children = parseBlock()

        return when (type) {
            "union", "hull", "minkowski" -> SceneNode.Union(children)
            "difference" -> SceneNode.Difference(children)
            "intersection" -> SceneNode.Intersection(children)
            else -> SceneNode.Group(children)
        }
    }

    private fun parseColor(): SceneNode? {
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') return null
        pos++

        var r = 1.0f; var g = 1.0f; var b = 1.0f; var a = 1.0f
        skipWhitespaceAndComments()

        if (pos < input.length && input[pos] == '"') {
            val colorName = parseStringLiteral()
            val color = getNamedColor(colorName)
            r = color[0]; g = color[1]; b = color[2]
        } else if (pos < input.length && input[pos] == '[') {
            val vec = parseVector()
            if (vec.size >= 3) {
                r = vec[0].toFloat(); g = vec[1].toFloat(); b = vec[2].toFloat()
                if (vec.size >= 4) a = vec[3].toFloat()
            }
        }

        skipToCloseParen()
        skipWhitespaceAndComments()

        val child = parseBlockOrStatement() ?: return null
        return SceneNode.Color(r, g, b, a, child)
    }

    private fun parseLinearExtrude(): SceneNode? {
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') return null
        pos++

        var height = 1.0
        skipWhitespaceAndComments()

        val paramsStr = extractParenContent()
        val params = parseParamString(paramsStr)
        height = params["height"] ?: params["_0"] ?: 1.0

        skipWhitespaceAndComments()
        val child = parseBlockOrStatement() ?: return null

        return SceneNode.LinearExtrude(height, child)
    }

    private fun parseCircle(): SceneNode {
        skipWhitespaceAndComments()
        var radius = 1.0
        if (pos < input.length && input[pos] == '(') {
            pos++; skipWhitespaceAndComments()
            if (pos < input.length && input[pos] != ')') {
                val paramStr = input.substring(pos)
                val rMatch = Regex("^r\\s*=\\s*").find(paramStr)
                val dMatch = Regex("^d\\s*=\\s*").find(paramStr)
                if (rMatch != null) {
                    pos += rMatch.value.length; radius = parseExpression()
                } else if (dMatch != null) {
                    pos += dMatch.value.length; radius = parseExpression() / 2.0
                } else {
                    radius = parseExpression()
                }
            }
            skipToCloseParen()
        }
        skipSemicolon()
        return SceneNode.Circle(radius, getSegments())
    }

    private fun parseSquare(): SceneNode {
        skipWhitespaceAndComments()
        var sizeX = 1.0; var sizeY = 1.0; var center = false
        if (pos < input.length && input[pos] == '(') {
            pos++; skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == '[') {
                val vec = parseVector()
                if (vec.size >= 2) { sizeX = vec[0]; sizeY = vec[1] }
            } else if (pos < input.length && input[pos] != ')') {
                sizeX = parseExpression(); sizeY = sizeX
            }
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ',') {
                pos++; skipWhitespaceAndComments()
                val rest = input.substring(pos)
                if (rest.startsWith("center")) {
                    pos += "center".length; skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == '=') {
                        pos++; skipWhitespaceAndComments()
                        center = parseBooleanValue()
                    }
                }
            }
            skipToCloseParen()
        }
        skipSemicolon()
        return SceneNode.Square(sizeX, sizeY, center)
    }

    private fun parsePolygon(): SceneNode {
        skipWhitespaceAndComments()
        val points = mutableListOf<Pair<Double, Double>>()
        if (pos < input.length && input[pos] == '(') {
            pos++; skipWhitespaceAndComments()
            val paramStr = input.substring(pos)
            val pointsMatch = Regex("^points\\s*=\\s*").find(paramStr)
            if (pointsMatch != null) {
                pos += pointsMatch.value.length
            }
            if (pos < input.length && input[pos] == '[') {
                pos++ // outer [
                skipWhitespaceAndComments()
                while (pos < input.length && input[pos] != ']') {
                    if (input[pos] == '[') {
                        val vec = parseVector()
                        if (vec.size >= 2) points.add(Pair(vec[0], vec[1]))
                    } else { pos++ }
                    skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == ',') pos++
                    skipWhitespaceAndComments()
                }
                if (pos < input.length) pos++ // close outer ]
            }
            skipToCloseParen()
        }
        skipSemicolon()
        return SceneNode.Polygon(points)
    }

    /**
     * Parse text() call — extracts all parameters and returns SceneNode.Text with glyph data.
     */
    private fun parseText(): SceneNode {
        skipWhitespaceAndComments()
        var textContent = ""
        var size = 10.0
        var font = "Liberation Sans"
        var halign = "left"
        var valign = "baseline"
        var spacing = 1.0
        var direction = "ltr"

        if (pos < input.length && input[pos] == '(') {
            pos++; skipWhitespaceAndComments()
            val paramsStr = extractParenContent()
            val parts = splitParams(paramsStr)
            for (part in parts) {
                val trimmed = part.trim()
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx > 0) {
                    val name = trimmed.substring(0, eqIdx).trim()
                    val valueStr = trimmed.substring(eqIdx + 1).trim()
                    when (name) {
                        "text" -> {
                            textContent = if (valueStr.startsWith("\"")) {
                                valueStr.removeSurrounding("\"")
                            } else {
                                // Variable reference for text content
                                val subParser = OpenSCADParser()
                                subParser.parseStartTime = parseStartTime
                                subParser.vars.putAll(vars)
                                subParser.functions.putAll(functions)
                                subParser.input = valueStr
                                subParser.pos = 0
                                val resolved = subParser.parseExpressionValueInner()
                                if (resolved is ScadValue.Str) resolved.value else ""
                            }
                        }
                        "size" -> size = evaluateParamValue(valueStr)
                        "font" -> font = valueStr.removeSurrounding("\"")
                        "halign" -> halign = valueStr.removeSurrounding("\"")
                        "valign" -> valign = valueStr.removeSurrounding("\"")
                        "spacing" -> spacing = evaluateParamValue(valueStr)
                        "direction" -> direction = valueStr.removeSurrounding("\"")
                    }
                } else {
                    // First positional parameter is text content
                    if (textContent.isEmpty()) {
                        if (trimmed.startsWith("\"")) {
                            textContent = trimmed.removeSurrounding("\"")
                        } else {
                            // It's a variable reference — try to resolve it
                            val subParser = OpenSCADParser()
                            subParser.parseStartTime = parseStartTime
                            subParser.vars.putAll(vars)
                            subParser.functions.putAll(functions)
                            subParser.input = trimmed
                            subParser.pos = 0
                            val resolved = subParser.parseExpressionValueInner()
                            if (resolved is ScadValue.Str) {
                                textContent = resolved.value
                            }
                        }
                    }
                }
            }
        }
        skipSemicolon()
        return SceneNode.Text(textContent, size, font, halign, valign, spacing, direction)
    }

    // --- Module and For Loop support ---

    private fun parseModuleCall(name: String): SceneNode? {
        val moduleDef = modules[name] ?: return null

        // Parse arguments
        val args = mutableListOf<ScadValue>()
        if (pos < input.length && input[pos] == '(') {
            pos++; skipWhitespaceAndComments()
            while (pos < input.length && input[pos] != ')') {
                args.add(parseExpressionValueForFuncArg())
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == ',') pos++
                skipWhitespaceAndComments()
            }
            if (pos < input.length && input[pos] == ')') pos++
        }
        skipSemicolon()

        // Parse the module body using a sub-parser with context
        val subParser = OpenSCADParser()
        subParser.parseStartTime = this.parseStartTime
        subParser.vars.putAll(vars)
        subParser.modules.putAll(modules)
        subParser.functions.putAll(functions)

        // Bind parameters
        for (i in moduleDef.params.indices) {
            val paramName = moduleDef.params[i]
            val value = if (i < args.size) args[i]
            else {
                val def = moduleDef.defaults.getOrNull(i)
                if (def != null) {
                    val defParser = OpenSCADParser()
                    defParser.parseStartTime = parseStartTime
                    defParser.vars.putAll(subParser.vars)
                    defParser.functions.putAll(functions)
                    defParser.input = def
                    defParser.pos = 0
                    defParser.parseExpressionValue()
                } else ScadValue.Num(0.0)
            }
            subParser.vars[paramName] = value
        }

        val result = subParser.parseWithContext(moduleDef.body)

        return if (result is SceneNode.Group && result.children.size == 1) {
            result.children[0]
        } else {
            result
        }
    }

    private fun parseForLoop(): SceneNode? {
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') return null
        pos++ // skip (

        skipWhitespaceAndComments()
        val loopVar = parseIdentifier() ?: run { skipToCloseParen(); return null }
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == '=') pos++
        skipWhitespaceAndComments()

        var rangeStart = 0.0; var rangeStep = 1.0; var rangeEnd = 0.0

        if (pos < input.length && input[pos] == '[') {
            pos++ // skip [
            skipWhitespaceAndComments()
            rangeStart = parseExpression()
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ':') {
                pos++; skipWhitespaceAndComments()
                val second = parseExpression()
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == ':') {
                    pos++; skipWhitespaceAndComments()
                    rangeStep = second
                    rangeEnd = parseExpression()
                } else {
                    rangeEnd = second
                }
            }
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ']') pos++
        }

        skipToCloseParen()
        skipWhitespaceAndComments()

        // Capture the loop body source
        val bodySource: String
        if (pos < input.length && input[pos] == '{') {
            pos++
            val start = pos
            var depth = 1
            while (pos < input.length && depth > 0) {
                when (input[pos]) { '{' -> depth++; '}' -> depth-- }
                if (depth > 0) pos++
            }
            bodySource = input.substring(start, pos)
            if (pos < input.length) pos++
        } else {
            val start = pos
            var depth = 0
            while (pos < input.length) {
                when (input[pos]) {
                    '(', '{', '[' -> depth++
                    ')', '}', ']' -> depth--
                    ';' -> { if (depth <= 0) { pos++; break } }
                }
                pos++
            }
            bodySource = input.substring(start, pos)
        }

        // Unroll the loop
        val children = mutableListOf<SceneNode>()
        if (rangeStep > 0 && rangeStart <= rangeEnd || rangeStep < 0 && rangeStart >= rangeEnd) {
            var i = rangeStart
            val maxIterations = 1000
            var count = 0
            while ((rangeStep > 0 && i <= rangeEnd) || (rangeStep < 0 && i >= rangeEnd)) {
                if (count++ > maxIterations) break
                checkTimeout()

                val subParser = OpenSCADParser()
                subParser.parseStartTime = this.parseStartTime
                subParser.vars.putAll(vars)
                subParser.vars[loopVar] = ScadValue.Num(i)
                subParser.modules.putAll(modules)
                subParser.functions.putAll(functions)
                val result = subParser.parseWithContext(bodySource)

                if (result is SceneNode.Group) {
                    children.addAll(result.children)
                } else {
                    children.add(result)
                }

                i += rangeStep
            }
        }

        return if (children.isEmpty()) null
        else if (children.size == 1) children[0]
        else SceneNode.Group(children)
    }

    // --- Helper methods ---

    private fun getSegments(): Int {
        return vars["\$fn"]?.toDouble()?.toInt()?.coerceIn(3, 360) ?: 32
    }

    private fun parseBlock(): List<SceneNode> {
        skipWhitespaceAndComments()
        val children = mutableListOf<SceneNode>()
        if (pos >= input.length || input[pos] != '{') return children
        pos++ // skip {

        while (pos < input.length && input[pos] != '}') {
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == '}') break
            val node = parseStatement()
            if (node != null) children.add(node)
        }

        if (pos < input.length) pos++ // skip }
        return children
    }

    private fun parseBlockOrStatement(): SceneNode? {
        skipWhitespaceAndComments()
        if (pos >= input.length) return null

        return if (input[pos] == '{') {
            val children = parseBlock()
            if (children.size == 1) children[0] else SceneNode.Group(children)
        } else {
            parseStatement()
        }
    }

    private fun parseVector(): List<Double> {
        val values = mutableListOf<Double>()
        if (pos >= input.length || input[pos] != '[') return values
        pos++ // skip [
        skipWhitespaceAndComments()

        while (pos < input.length && input[pos] != ']') {
            values.add(parseExpression())
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ',') pos++
            skipWhitespaceAndComments()
        }

        if (pos < input.length) pos++ // skip ]
        return values
    }

    private fun parseNumber(): Double {
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
            pos++
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
            while (pos < input.length && input[pos].isDigit()) pos++
        }
        val numStr = input.substring(start, pos)
        // Avoid regex-based toDoubleOrNull by using try/catch with parseDouble
        return try { java.lang.Double.parseDouble(numStr) } catch (_: NumberFormatException) { 0.0 }
    }

    private fun parseIdentifier(): String? {
        skipWhitespaceAndComments()
        if (pos >= input.length || (!input[pos].isLetter() && input[pos] != '_' && input[pos] != '$'))
            return null
        val start = pos
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_' || input[pos] == '$'))
            pos++
        return input.substring(start, pos)
    }

    private fun parseNamedParams(): Map<String, Double> {
        val params = mutableMapOf<String, Double>()
        return params
    }

    private fun extractParenContent(): String {
        val start = pos
        var depth = 1
        while (pos < input.length && depth > 0) {
            when (input[pos]) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth > 0) pos++
        }
        val content = input.substring(start, pos)
        if (pos < input.length) pos++ // skip closing )
        return content
    }

    private fun parseParamString(str: String): Map<String, Double> {
        val params = mutableMapOf<String, Double>()
        var positionalIndex = 0
        val s = str.trim()

        val parts = splitParams(s)
        for (part in parts) {
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                val valueStr = trimmed.substring(eqIdx + 1).trim()
                val value = evaluateParamValue(valueStr)
                params[name] = value
            } else {
                val value = evaluateParamValue(trimmed)
                params["_$positionalIndex"] = value
                positionalIndex++
            }
        }

        return params
    }

    private fun evaluateParamValue(valueStr: String): Double {
        if (valueStr == "true") return 1.0
        if (valueStr == "false") return 0.0
        if (valueStr.startsWith("\"")) return 0.0 // string param
        valueStr.toDoubleOrNull()?.let { return it }
        val subParser = OpenSCADParser()
        subParser.parseStartTime = this.parseStartTime
        subParser.vars.putAll(vars)
        subParser.functions.putAll(functions)
        subParser.input = valueStr
        subParser.pos = 0
        return try {
            subParser.parseExpression()
        } catch (e: Exception) {
            0.0
        }
    }

    private fun splitParams(s: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        var inString = false
        for (i in s.indices) {
            val ch = s[i]
            when {
                ch == '"' && (i == 0 || s[i-1] != '\\') -> { inString = !inString; current.append(ch) }
                inString -> current.append(ch)
                ch == '[' || ch == '(' -> { depth++; current.append(ch) }
                ch == ']' || ch == ')' -> { depth--; current.append(ch) }
                ch == ',' && depth == 0 -> { parts.add(current.toString()); current = StringBuilder() }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    private fun parseBooleanValue(): Boolean {
        skipWhitespaceAndComments()
        val sub = input.substring(pos)
        return when {
            sub.startsWith("true") -> { pos += 4; true }
            sub.startsWith("false") -> { pos += 5; false }
            else -> false
        }
    }

    private fun skipWhitespaceAndComments() {
        while (pos < input.length) {
            when {
                input[pos].isWhitespace() -> pos++
                pos + 1 < input.length && input[pos] == '/' && input[pos + 1] == '/' -> {
                    while (pos < input.length && input[pos] != '\n') pos++
                }
                pos + 1 < input.length && input[pos] == '/' && input[pos + 1] == '*' -> {
                    pos += 2
                    while (pos + 1 < input.length && !(input[pos] == '*' && input[pos + 1] == '/')) pos++
                    if (pos + 1 < input.length) pos += 2
                }
                else -> break
            }
        }
    }

    private fun skipToCloseParen() {
        var depth = 1
        while (pos < input.length && depth > 0) {
            when (input[pos]) {
                '(' -> depth++
                ')' -> depth--
            }
            pos++
        }
    }

    private fun skipSemicolon() {
        skipWhitespaceAndComments()
        if (pos < input.length && input[pos] == ';') pos++
    }

    private fun skipToNextStatement() {
        var depth = 0
        while (pos < input.length) {
            when (input[pos]) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> {
                    if (depth > 0) {
                        depth--
                        // If a '}' brings us back to depth 0, the block-statement is done
                        if (depth == 0 && input[pos] == '}') {
                            pos++
                            return
                        }
                    }
                    else { pos++; return }
                }
                ';' -> {
                    if (depth == 0) { pos++; return }
                }
                '"' -> {
                    // Skip string literals to avoid counting brackets inside strings
                    pos++
                    while (pos < input.length && input[pos] != '"') {
                        if (input[pos] == '\\') pos++
                        pos++
                    }
                    // pos now at closing " or end, fall through to pos++ below
                }
            }
            pos++
        }
    }

    private fun getNamedColor(name: String): FloatArray {
        return when (name.lowercase()) {
            "red" -> floatArrayOf(1f, 0f, 0f)
            "green" -> floatArrayOf(0f, 1f, 0f)
            "blue" -> floatArrayOf(0f, 0f, 1f)
            "yellow" -> floatArrayOf(1f, 1f, 0f)
            "cyan" -> floatArrayOf(0f, 1f, 1f)
            "magenta" -> floatArrayOf(1f, 0f, 1f)
            "white" -> floatArrayOf(1f, 1f, 1f)
            "black" -> floatArrayOf(0f, 0f, 0f)
            "orange" -> floatArrayOf(1f, 0.647f, 0f)
            "purple" -> floatArrayOf(0.5f, 0f, 0.5f)
            "pink" -> floatArrayOf(1f, 0.753f, 0.796f)
            "gray", "grey" -> floatArrayOf(0.5f, 0.5f, 0.5f)
            else -> floatArrayOf(0.8f, 0.8f, 0.8f)
        }
    }
}
