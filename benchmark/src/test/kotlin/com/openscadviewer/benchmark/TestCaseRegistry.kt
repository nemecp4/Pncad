package com.openscadviewer.benchmark

/**
 * Registry holding all 50 benchmark test cases organized by category.
 * Each test case contains valid OpenSCAD code parseable by [com.openscadviewer.parser.OpenSCADParser].
 *
 * Code snippets are loaded from .scad resource files under testcases/{category}/{name}.scad
 */
object TestCaseRegistry {

    private fun loadCode(category: String, name: String): String {
        val path = "testcases/$category/$name.scad"
        val stream = TestCaseRegistry::class.java.classLoader.getResourceAsStream(path)
            ?: error("Resource not found: $path")
        return stream.bufferedReader().use { it.readText() }.trim()
    }

    // --- geometry_primitives (8 cases) ---

    val geometryPrimitives: List<TestCase> = listOf(
        TestCase(name = "prim_cube_unit", category = "geometry_primitives", code = loadCode("geometry_primitives", "prim_cube_unit")),
        TestCase(name = "prim_sphere_basic", category = "geometry_primitives", code = loadCode("geometry_primitives", "prim_sphere_basic")),
        TestCase(name = "prim_cylinder_basic", category = "geometry_primitives", code = loadCode("geometry_primitives", "prim_cylinder_basic")),
        TestCase(name = "prim_circle_basic", category = "geometry_primitives", code = loadCode("geometry_primitives", "prim_circle_basic")),
        TestCase(name = "prim_square_basic", category = "geometry_primitives", code = loadCode("geometry_primitives", "prim_square_basic")),
        TestCase(name = "prim_polygon_triangle", category = "geometry_primitives", code = loadCode("geometry_primitives", "prim_polygon_triangle")),
        TestCase(name = "prim_cube_centered", category = "geometry_primitives", code = loadCode("geometry_primitives", "prim_cube_centered")),
        TestCase(name = "prim_large_sphere_in_union", category = "geometry_primitives", code = loadCode("geometry_primitives", "prim_large_sphere_in_union"))
    )

    // --- transformations (8 cases) ---

    val transformations: List<TestCase> = listOf(
        TestCase(name = "trans_translate_cube", category = "transformations", code = loadCode("transformations", "trans_translate_cube")),
        TestCase(name = "trans_rotate_cube", category = "transformations", code = loadCode("transformations", "trans_rotate_cube")),
        TestCase(name = "trans_scale_sphere", category = "transformations", code = loadCode("transformations", "trans_scale_sphere")),
        TestCase(name = "trans_translate_cylinder", category = "transformations", code = loadCode("transformations", "trans_translate_cylinder")),
        TestCase(name = "trans_rotate_y_cube", category = "transformations", code = loadCode("transformations", "trans_rotate_y_cube")),
        TestCase(name = "trans_scale_uniform", category = "transformations", code = loadCode("transformations", "trans_scale_uniform")),
        TestCase(name = "trans_nested_translate_rotate_scale", category = "transformations", code = loadCode("transformations", "trans_nested_translate_rotate_scale")),
        TestCase(name = "trans_multiple_nested", category = "transformations", code = loadCode("transformations", "trans_multiple_nested"))
    )

    // --- linear_extrusion (6 cases) ---

    val linearExtrusion: List<TestCase> = listOf(
        TestCase(name = "extrude_circle_basic", category = "linear_extrusion", code = loadCode("linear_extrusion", "extrude_circle_basic")),
        TestCase(name = "extrude_square_basic", category = "linear_extrusion", code = loadCode("linear_extrusion", "extrude_square_basic")),
        TestCase(name = "extrude_polygon_basic", category = "linear_extrusion", code = loadCode("linear_extrusion", "extrude_polygon_basic")),
        TestCase(name = "extrude_tall_circle", category = "linear_extrusion", code = loadCode("linear_extrusion", "extrude_tall_circle")),
        TestCase(name = "extrude_small_square", category = "linear_extrusion", code = loadCode("linear_extrusion", "extrude_small_square")),
        TestCase(name = "extrude_translated_rotated", category = "linear_extrusion", code = loadCode("linear_extrusion", "extrude_translated_rotated"))
    )

    // --- csg_operations (10 cases) ---

    val csgOperations: List<TestCase> = listOf(
        TestCase(name = "csg_union_two_cubes", category = "csg_operations", code = loadCode("csg_operations", "csg_union_two_cubes")),
        TestCase(name = "csg_difference_cube_sphere", category = "csg_operations", code = loadCode("csg_operations", "csg_difference_cube_sphere")),
        TestCase(name = "csg_intersection_cube_sphere", category = "csg_operations", code = loadCode("csg_operations", "csg_intersection_cube_sphere")),
        TestCase(name = "csg_union_three_spheres", category = "csg_operations", code = loadCode("csg_operations", "csg_union_three_spheres")),
        TestCase(name = "csg_difference_cylinder_hole", category = "csg_operations", code = loadCode("csg_operations", "csg_difference_cylinder_hole")),
        TestCase(name = "csg_intersection_two_cylinders", category = "csg_operations", code = loadCode("csg_operations", "csg_intersection_two_cylinders")),
        TestCase(name = "csg_union_mixed_primitives", category = "csg_operations", code = loadCode("csg_operations", "csg_union_mixed_primitives")),
        TestCase(name = "csg_difference_multiple", category = "csg_operations", code = loadCode("csg_operations", "csg_difference_multiple")),
        TestCase(name = "csg_nested_union_difference", category = "csg_operations", code = loadCode("csg_operations", "csg_nested_union_difference")),
        TestCase(name = "csg_deep_nested_operations", category = "csg_operations", code = loadCode("csg_operations", "csg_deep_nested_operations"))
    )

    // --- combined_operations (8 cases) ---

    val combinedOperations: List<TestCase> = listOf(
        TestCase(name = "comb_color_cube", category = "combined_operations", code = loadCode("combined_operations", "comb_color_cube")),
        TestCase(name = "comb_color_sphere", category = "combined_operations", code = loadCode("combined_operations", "comb_color_sphere")),
        TestCase(name = "comb_transform_csg_scene", category = "combined_operations", code = loadCode("combined_operations", "comb_transform_csg_scene")),
        TestCase(name = "comb_color_translate_union", category = "combined_operations", code = loadCode("combined_operations", "comb_color_translate_union")),
        TestCase(name = "comb_rotate_difference_cylinder", category = "combined_operations", code = loadCode("combined_operations", "comb_rotate_difference_cylinder")),
        TestCase(name = "comb_scale_union_multi", category = "combined_operations", code = loadCode("combined_operations", "comb_scale_union_multi")),
        TestCase(name = "comb_complex_scene", category = "combined_operations", code = loadCode("combined_operations", "comb_complex_scene")),
        TestCase(name = "comb_nested_transforms_csg", category = "combined_operations", code = loadCode("combined_operations", "comb_nested_transforms_csg"))
    )

    // --- variables_expressions (5 cases) ---

    val variablesExpressions: List<TestCase> = listOf(
        TestCase(name = "var_simple_cube_size", category = "variables_expressions", code = loadCode("variables_expressions", "var_simple_cube_size")),
        TestCase(name = "var_math_expression", category = "variables_expressions", code = loadCode("variables_expressions", "var_math_expression")),
        TestCase(name = "var_trig_functions", category = "variables_expressions", code = loadCode("variables_expressions", "var_trig_functions")),
        TestCase(name = "var_multiple_variables_scene", category = "variables_expressions", code = loadCode("variables_expressions", "var_multiple_variables_scene")),
        TestCase(name = "var_computed_positions", category = "variables_expressions", code = loadCode("variables_expressions", "var_computed_positions"))
    )

    // 

    val edgeCases: List<TestCase> = listOf(
        TestCase(name = "edge_comments_in_code", category = "edge_cases", code = loadCode("edge_cases", "edge_comments_in_code")),
        TestCase(name = "edge_nested_expressions", category = "edge_cases", code = loadCode("edge_cases", "edge_nested_expressions")),
        TestCase(name = "edge_variable_references", category = "edge_cases", code = loadCode("edge_cases", "edge_variable_references")),
        TestCase(name = "edge_multiline_code", category = "edge_cases", code = loadCode("edge_cases", "edge_multiline_code")),
        TestCase(name = "edge_named_parameters", category = "edge_cases", code = loadCode("edge_cases", "edge_named_parameters"))
    )

    // --- custom (user-provided test cases with expected_results) ---
    // Each custom test is a separate class that can be run independently.

    val custom: List<TestCase> = listOf(
        SunBenchmarkTest().testCase,
        ControllRoseBenchmarkTest().testCase,
        TowerBenchmarkTest().testCase,
        RotateText1BenchmarkTest().testCase,
        RotateText2BenchmarkTest().testCase
    )

    // --- Aggregate list of all 50 test cases ---

    val allCases: List<TestCase> = buildList {
        addAll(geometryPrimitives)
        addAll(transformations)
        addAll(linearExtrusion)
        addAll(csgOperations)
        addAll(combinedOperations)
        addAll(variablesExpressions)
        addAll(edgeCases)
        addAll(custom)
    }

    /** Returns all test cases for a given category. */
    fun byCategory(category: String): List<TestCase> =
        allCases.filter { it.category == category }

    /** Returns the total count of test cases. */
    val size: Int get() = allCases.size
}
