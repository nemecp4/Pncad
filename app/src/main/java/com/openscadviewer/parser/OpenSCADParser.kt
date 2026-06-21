package com.openscadviewer.parser

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Parser for a subset of OpenSCAD language.
 * Supports: cube, sphere, cylinder, translate, rotate, scale, union, difference, intersection,
 * color, linear_extrude, circle, square, polygon, module calls, and variables.
 */
class OpenSCADParser {

    private var pos = 0
    private var input = ""
    private val variables = mutableMapOf<String, Double>()

    fun parse(code: String): SceneNode {
        input = code
        pos = 0
        variables.clear()
        val children = mutableListOf<SceneNode>()

        while (pos < input.length) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break

            val node = parseStatement()
            if (node != null) {
                children.add(node)
            }
        }

        return SceneNode.Group(children)
    }

    private fun parseStatement(): SceneNode? {
        skipWhitespaceAndComments()
        if (pos >= input.length) return null

        // Check for variable assignment
        val varMatch = Regex("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*=").find(input.substring(pos))
        if (varMatch != null) {
            val varName = varMatch.groupValues[1]
            pos += varMatch.value.length
            skipWhitespaceAndComments()
            val value = parseExpression()
            variables[varName] = value
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ';') pos++
            return null
        }

        val identifier = parseIdentifier() ?: return null
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
            else -> {
                // Unknown identifier - try to skip it
                skipToNextStatement()
                null
            }
        }
    }

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
                sizeX = vec[0]
                sizeY = vec[1]
                sizeZ = vec[2]
            }
        } else {
            // Check for named parameters or single number
            val params = parseNamedParams()
            if (params.containsKey("size")) {
                // Would need vector parsing for named size
                sizeX = params["size"] ?: 1.0
                sizeY = sizeX
                sizeZ = sizeX
            } else {
                val size = parseExpression()
                sizeX = size
                sizeY = size
                sizeZ = size
            }
        }

        skipWhitespaceAndComments()
        // Check for center parameter
        if (pos < input.length && input[pos] == ',') {
            pos++
            skipWhitespaceAndComments()
            val paramCheck = input.substring(pos)
            if (paramCheck.startsWith("center")) {
                pos += "center".length
                skipWhitespaceAndComments()
                if (pos < input.length && input[pos] == '=') {
                    pos++
                    skipWhitespaceAndComments()
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
            return SceneNode.Sphere(1.0)
        }
        pos++

        var radius = 1.0
        skipWhitespaceAndComments()

        if (pos < input.length && input[pos] != ')') {
            val paramStr = input.substring(pos)
            if (paramStr.startsWith("r") && !paramStr.startsWith("r=").not()) {
                // Named parameter r=
                val match = Regex("^r\\s*=\\s*").find(paramStr)
                if (match != null) {
                    pos += match.value.length
                    radius = parseExpression()
                } else {
                    radius = parseExpression()
                }
            } else if (paramStr.startsWith("d")) {
                val match = Regex("^d\\s*=\\s*").find(paramStr)
                if (match != null) {
                    pos += match.value.length
                    radius = parseExpression() / 2.0
                }
            } else {
                radius = parseExpression()
            }
        }

        skipToCloseParen()
        skipSemicolon()

        return SceneNode.Sphere(radius)
    }

    private fun parseCylinder(): SceneNode {
        skipWhitespaceAndComments()
        if (pos >= input.length || input[pos] != '(') {
            return SceneNode.Cylinder(1.0, 1.0, 1.0, false)
        }
        pos++

        var h = 1.0
        var r1 = 1.0
        var r2 = 1.0
        var center = false

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

        skipSemicolon()

        return SceneNode.Cylinder(h, r1, r2, center)
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
            // Try parsing as a named param or direct value
            val paramStr = input.substring(pos)
            val namedMatch = Regex("^v\\s*=\\s*").find(paramStr)
            if (namedMatch != null) {
                pos += namedMatch.value.length
                if (pos < input.length && input[pos] == '[') {
                    val parsedVec = parseVector()
                    for (i in parsedVec.indices.take(3)) {
                        vec[i] = parsedVec[i]
                    }
                }
            } else {
                // Single value for rotate (around z) or uniform scale
                val value = parseExpression()
                if (type == "scale") {
                    vec[0] = value; vec[1] = value; vec[2] = value
                } else {
                    vec[2] = value // rotate around z by default
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
            pos++
            skipToCloseParen()
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
            // Named color
            pos++
            val colorName = StringBuilder()
            while (pos < input.length && input[pos] != '"') {
                colorName.append(input[pos])
                pos++
            }
            if (pos < input.length) pos++ // skip closing "

            val color = getNamedColor(colorName.toString())
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
            pos++
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] != ')') {
                val paramStr = input.substring(pos)
                val rMatch = Regex("^r\\s*=\\s*").find(paramStr)
                val dMatch = Regex("^d\\s*=\\s*").find(paramStr)
                if (rMatch != null) {
                    pos += rMatch.value.length
                    radius = parseExpression()
                } else if (dMatch != null) {
                    pos += dMatch.value.length
                    radius = parseExpression() / 2.0
                } else {
                    radius = parseExpression()
                }
            }
            skipToCloseParen()
        }
        skipSemicolon()
        return SceneNode.Circle(radius)
    }

    private fun parseSquare(): SceneNode {
        skipWhitespaceAndComments()
        var sizeX = 1.0
        var sizeY = 1.0
        var center = false
        if (pos < input.length && input[pos] == '(') {
            pos++
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == '[') {
                val vec = parseVector()
                if (vec.size >= 2) { sizeX = vec[0]; sizeY = vec[1] }
            } else if (pos < input.length && input[pos] != ')') {
                sizeX = parseExpression()
                sizeY = sizeX
            }
            skipWhitespaceAndComments()
            if (pos < input.length && input[pos] == ',') {
                pos++
                skipWhitespaceAndComments()
                val rest = input.substring(pos)
                if (rest.startsWith("center")) {
                    pos += "center".length
                    skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == '=') {
                        pos++
                        skipWhitespaceAndComments()
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
            pos++
            skipWhitespaceAndComments()
            // Look for points=[[...]] or [[...]]
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
                    } else {
                        pos++
                    }
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

    // --- Helper methods ---

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

    private fun parseExpression(): Double {
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

        // Variable or function
        val id = parseIdentifier()
        if (id != null) {
            // Check for built-in constants
            when (id) {
                "true" -> return 1.0
                "false" -> return 0.0
                "PI" -> return PI
                "sin" -> return sin(parseFunctionArg() * PI / 180.0)
                "cos" -> return cos(parseFunctionArg() * PI / 180.0)
                "abs" -> return kotlin.math.abs(parseFunctionArg())
                "sqrt" -> return kotlin.math.sqrt(parseFunctionArg())
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
                "max", "min" -> {
                    skipWhitespaceAndComments()
                    if (pos < input.length && input[pos] == '(') {
                        pos++
                        val a = parseExpression()
                        skipWhitespaceAndComments()
                        if (pos < input.length && input[pos] == ',') pos++
                        val b = parseExpression()
                        skipWhitespaceAndComments()
                        if (pos < input.length && input[pos] == ')') pos++
                        return if (id == "max") maxOf(a, b) else minOf(a, b)
                    }
                    return 0.0
                }
            }
            // Variable lookup
            return variables[id] ?: 0.0
        }

        return 0.0
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

    private fun parseNumber(): Double {
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        // Handle scientific notation
        if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
            pos++
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
            while (pos < input.length && input[pos].isDigit()) pos++
        }
        return input.substring(start, pos).toDoubleOrNull() ?: 0.0
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
        // Simple implementation - skips complex named params for now
        return params
    }

    private fun extractParenContent(): String {
        // Already past the opening paren, find matching close
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
        var idx = 0
        var positionalIndex = 0
        val s = str.trim()

        // Simple comma-split for parameters
        val parts = splitParams(s)
        for (part in parts) {
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                val valueStr = trimmed.substring(eqIdx + 1).trim()
                val value = valueStr.toDoubleOrNull()
                    ?: if (valueStr == "true") 1.0
                    else if (valueStr == "false") 0.0
                    else 0.0
                params[name] = value
            } else {
                val value = trimmed.toDoubleOrNull() ?: 0.0
                params["_$positionalIndex"] = value
                positionalIndex++
            }
        }

        return params
    }

    private fun splitParams(s: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        for (ch in s) {
            when {
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
                    // Line comment
                    while (pos < input.length && input[pos] != '\n') pos++
                }
                pos + 1 < input.length && input[pos] == '/' && input[pos + 1] == '*' -> {
                    // Block comment
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
        // Skip until we find a semicolon or closing brace at depth 0
        var depth = 0
        while (pos < input.length) {
            when (input[pos]) {
                '(' , '{', '[' -> depth++
                ')', '}', ']' -> {
                    if (depth > 0) depth--
                    else { pos++; return }
                }
                ';' -> {
                    if (depth == 0) { pos++; return }
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
