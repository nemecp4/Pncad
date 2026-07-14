package com.openscadviewer.editor

object OpenScadTokens {
    val KEYWORDS: List<String> = listOf(
        "module", "function", "if", "else", "for", "let",
        "each", "assert", "echo", "include", "use"
    )

    val BUILTINS: List<String> = listOf(
        "cube", "sphere", "cylinder", "polyhedron",
        "circle", "square", "polygon", "text",
        "translate", "rotate", "scale", "mirror", "multmatrix",
        "color", "offset", "hull", "minkowski",
        "union", "difference", "intersection",
        "linear_extrude", "rotate_extrude",
        "import", "surface", "projection",
        "render", "children"
    )

    val MATH_FUNCTIONS: List<String> = listOf(
        "abs", "sign", "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
        "floor", "ceil", "round", "sqrt", "pow", "exp", "log", "ln",
        "min", "max", "len", "norm", "cross", "concat", "lookup", "str"
    )
}
