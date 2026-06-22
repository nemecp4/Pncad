package com.openscadviewer.benchmark

/**
 * Represents a single benchmark test case containing an OpenSCAD code snippet.
 * Validates structural constraints on construction.
 */
data class TestCase(
    val name: String,       // max 64 chars, pattern: {category_prefix}_{label}
    val category: String,   // one of 7 defined categories
    val code: String        // OpenSCAD source, max 2048 chars
) {
    init {
        require(name.length <= 64) { "Name exceeds 64 characters" }
        require(name.matches(Regex("[a-z0-9_]+"))) { "Name must be lowercase alphanumeric + underscores" }
        require(code.length <= 2048) { "Code exceeds 2048 characters" }
        require(category in VALID_CATEGORIES) { "Invalid category: $category" }
    }

    companion object {
        val VALID_CATEGORIES = setOf(
            "geometry_primitives",
            "transformations",
            "linear_extrusion",
            "csg_operations",
            "combined_operations",
            "variables_expressions",
            "edge_cases"
        )
    }
}
