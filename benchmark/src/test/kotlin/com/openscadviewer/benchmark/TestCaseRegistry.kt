package com.openscadviewer.benchmark

/**
 * Registry holding all 50 benchmark test cases organized by category.
 * Each test case contains valid OpenSCAD code parseable by [com.openscadviewer.parser.OpenSCADParser].
 */
object TestCaseRegistry {

    // --- geometry_primitives (8 cases) ---

    val geometryPrimitives: List<TestCase> = listOf(
        // Single-operation, depth 1
        TestCase(
            name = "prim_cube_unit",
            category = "geometry_primitives",
            code = "cube([1, 1, 1]);"
        ),
        TestCase(
            name = "prim_sphere_basic",
            category = "geometry_primitives",
            code = "sphere(r = 5);"
        ),
        TestCase(
            name = "prim_cylinder_basic",
            category = "geometry_primitives",
            code = "cylinder(h = 10, r = 3);"
        ),
        TestCase(
            name = "prim_circle_basic",
            category = "geometry_primitives",
            code = "circle(r = 8);"
        ),
        TestCase(
            name = "prim_square_basic",
            category = "geometry_primitives",
            code = "square([4, 6]);"
        ),
        TestCase(
            name = "prim_polygon_triangle",
            category = "geometry_primitives",
            code = "polygon(points = [[0, 0], [10, 0], [5, 8]]);"
        ),
        TestCase(
            name = "prim_cube_centered",
            category = "geometry_primitives",
            code = "cube([20, 20, 20], center = true);"
        ),
        // Combined test: 3+ operations, depth >= 3
        TestCase(
            name = "prim_large_sphere_in_union",
            category = "geometry_primitives",
            code = """
                union() {
                    translate([0, 0, 0]) sphere(r = 50);
                    translate([100, 0, 0]) cube([30, 30, 30]);
                    translate([0, 100, 0]) cylinder(h = 40, r = 10);
                }
            """.trimIndent()
        )
    )

    // --- transformations (8 cases) ---

    val transformations: List<TestCase> = listOf(
        // Single-operation, depth 1 (transform wrapping a primitive = depth 2, single transform op)
        TestCase(
            name = "trans_translate_cube",
            category = "transformations",
            code = "translate([10, 20, 30]) cube([5, 5, 5]);"
        ),
        TestCase(
            name = "trans_rotate_cube",
            category = "transformations",
            code = "rotate([45, 0, 0]) cube([10, 10, 10]);"
        ),
        TestCase(
            name = "trans_scale_sphere",
            category = "transformations",
            code = "scale([2, 1, 0.5]) sphere(r = 5);"
        ),
        TestCase(
            name = "trans_translate_cylinder",
            category = "transformations",
            code = "translate([0, 0, 5]) cylinder(h = 20, r = 4);"
        ),
        TestCase(
            name = "trans_rotate_y_cube",
            category = "transformations",
            code = "rotate([0, 90, 0]) cube([8, 8, 8]);"
        ),
        TestCase(
            name = "trans_scale_uniform",
            category = "transformations",
            code = "scale([3, 3, 3]) cube([2, 2, 2]);"
        ),
        // Combined test: 3+ operations, depth >= 3
        TestCase(
            name = "trans_nested_translate_rotate_scale",
            category = "transformations",
            code = """
                translate([10, 0, 0]) rotate([0, 0, 45]) scale([2, 2, 2]) cube([5, 5, 5]);
            """.trimIndent()
        ),
        TestCase(
            name = "trans_multiple_nested",
            category = "transformations",
            code = """
                translate([20, 0, 0]) rotate([90, 0, 0]) translate([0, 5, 0]) sphere(r = 3);
            """.trimIndent()
        )
    )

    // --- linear_extrusion (6 cases) ---

    val linearExtrusion: List<TestCase> = listOf(
        // Single-operation, depth 1 (linear_extrude wrapping a 2D primitive)
        TestCase(
            name = "extrude_circle_basic",
            category = "linear_extrusion",
            code = "linear_extrude(height = 10) circle(r = 5);"
        ),
        TestCase(
            name = "extrude_square_basic",
            category = "linear_extrusion",
            code = "linear_extrude(height = 20) square([8, 8]);"
        ),
        TestCase(
            name = "extrude_polygon_basic",
            category = "linear_extrusion",
            code = "linear_extrude(height = 5) polygon(points = [[0, 0], [10, 0], [10, 10], [0, 10]]);"
        ),
        TestCase(
            name = "extrude_tall_circle",
            category = "linear_extrusion",
            code = "linear_extrude(height = 100) circle(r = 2);"
        ),
        TestCase(
            name = "extrude_small_square",
            category = "linear_extrusion",
            code = "linear_extrude(height = 3) square([1, 1]);"
        ),
        // Combined test: 3+ operations, depth >= 3
        TestCase(
            name = "extrude_translated_rotated",
            category = "linear_extrusion",
            code = """
                translate([10, 0, 0]) rotate([90, 0, 0]) linear_extrude(height = 15) circle(r = 4);
            """.trimIndent()
        )
    )

    // --- csg_operations (10 cases) ---

    val csgOperations: List<TestCase> = listOf(
        // Single-operation, depth 1 (CSG wrapping primitives)
        TestCase(
            name = "csg_union_two_cubes",
            category = "csg_operations",
            code = """
                union() {
                    cube([10, 10, 10]);
                    translate([5, 5, 5]) cube([10, 10, 10]);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "csg_difference_cube_sphere",
            category = "csg_operations",
            code = """
                difference() {
                    cube([20, 20, 20], center = true);
                    sphere(r = 13);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "csg_intersection_cube_sphere",
            category = "csg_operations",
            code = """
                intersection() {
                    cube([15, 15, 15], center = true);
                    sphere(r = 10);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "csg_union_three_spheres",
            category = "csg_operations",
            code = """
                union() {
                    sphere(r = 5);
                    translate([10, 0, 0]) sphere(r = 5);
                    translate([5, 8, 0]) sphere(r = 5);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "csg_difference_cylinder_hole",
            category = "csg_operations",
            code = """
                difference() {
                    cylinder(h = 20, r = 10);
                    cylinder(h = 20, r = 5);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "csg_intersection_two_cylinders",
            category = "csg_operations",
            code = """
                intersection() {
                    cylinder(h = 20, r = 8);
                    rotate([90, 0, 0]) cylinder(h = 20, r = 8);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "csg_union_mixed_primitives",
            category = "csg_operations",
            code = """
                union() {
                    cube([10, 10, 10]);
                    sphere(r = 7);
                    cylinder(h = 15, r = 3);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "csg_difference_multiple",
            category = "csg_operations",
            code = """
                difference() {
                    cube([30, 30, 30], center = true);
                    translate([0, 0, 0]) cylinder(h = 30, r = 8);
                    translate([10, 10, 0]) cylinder(h = 30, r = 5);
                }
            """.trimIndent()
        ),
        // Combined test: 3+ operations, depth >= 3 (nested CSG)
        TestCase(
            name = "csg_nested_union_difference",
            category = "csg_operations",
            code = """
                difference() {
                    union() {
                        cube([20, 20, 20], center = true);
                        translate([10, 0, 0]) sphere(r = 8);
                    }
                    translate([0, 0, 5]) cylinder(h = 25, r = 4);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "csg_deep_nested_operations",
            category = "csg_operations",
            code = """
                union() {
                    difference() {
                        intersection() {
                            cube([20, 20, 20], center = true);
                            sphere(r = 14);
                        }
                        cylinder(h = 25, r = 5);
                    }
                    translate([15, 0, 0]) sphere(r = 4);
                }
            """.trimIndent()
        )
    )

    // --- combined_operations (8 cases) ---

    val combinedOperations: List<TestCase> = listOf(
        // Single-operation (transform + primitive, simple combination)
        TestCase(
            name = "comb_color_cube",
            category = "combined_operations",
            code = """color("red") cube([10, 10, 10]);"""
        ),
        TestCase(
            name = "comb_color_sphere",
            category = "combined_operations",
            code = """color("blue") sphere(r = 8);"""
        ),
        // Combined test: 3+ operations, depth >= 3
        TestCase(
            name = "comb_transform_csg_scene",
            category = "combined_operations",
            code = """
                translate([0, 0, 5]) difference() {
                    cube([20, 20, 20], center = true);
                    sphere(r = 12);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "comb_color_translate_union",
            category = "combined_operations",
            code = """
                color("green") translate([5, 5, 0]) union() {
                    cube([10, 10, 10]);
                    sphere(r = 7);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "comb_rotate_difference_cylinder",
            category = "combined_operations",
            code = """
                rotate([45, 0, 0]) difference() {
                    cylinder(h = 30, r = 10);
                    translate([0, 0, 5]) cylinder(h = 30, r = 6);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "comb_scale_union_multi",
            category = "combined_operations",
            code = """
                scale([1.5, 1.5, 1.5]) union() {
                    translate([0, 0, 0]) cube([8, 8, 8]);
                    translate([10, 0, 0]) sphere(r = 5);
                    translate([0, 10, 0]) cylinder(h = 12, r = 3);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "comb_complex_scene",
            category = "combined_operations",
            code = """
                union() {
                    color("red") translate([0, 0, 0]) cube([10, 10, 10]);
                    color("blue") translate([15, 0, 0]) sphere(r = 6);
                    color("green") translate([0, 15, 0]) cylinder(h = 10, r = 4);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "comb_nested_transforms_csg",
            category = "combined_operations",
            code = """
                translate([5, 5, 5]) rotate([0, 0, 45]) difference() {
                    cube([20, 20, 20], center = true);
                    translate([0, 0, 0]) sphere(r = 11);
                }
            """.trimIndent()
        )
    )

    // --- variables_expressions (5 cases) ---

    val variablesExpressions: List<TestCase> = listOf(
        // Single-operation with variable usage
        TestCase(
            name = "var_simple_cube_size",
            category = "variables_expressions",
            code = """
                size = 10;
                cube([size, size, size]);
            """.trimIndent()
        ),
        TestCase(
            name = "var_math_expression",
            category = "variables_expressions",
            code = """
                r = 3 + 4 * 2;
                sphere(r = r);
            """.trimIndent()
        ),
        TestCase(
            name = "var_trig_functions",
            category = "variables_expressions",
            code = """
                x = cos(45) * 10;
                y = sin(45) * 10;
                translate([x, y, 0]) cube([5, 5, 5]);
            """.trimIndent()
        ),
        // Combined test: 3+ operations, depth >= 3
        TestCase(
            name = "var_multiple_variables_scene",
            category = "variables_expressions",
            code = """
                base = 20;
                h = base / 2;
                r = sqrt(base);
                translate([0, 0, h]) difference() {
                    cube([base, base, h], center = true);
                    sphere(r = r);
                }
            """.trimIndent()
        ),
        TestCase(
            name = "var_computed_positions",
            category = "variables_expressions",
            code = """
                offset = 15;
                rad = 5;
                union() {
                    translate([0, 0, 0]) sphere(r = rad);
                    translate([offset, 0, 0]) sphere(r = rad);
                    translate([offset / 2, offset, 0]) sphere(r = rad);
                }
            """.trimIndent()
        )
    )

    // --- edge_cases (5 cases) ---

    val edgeCases: List<TestCase> = listOf(
        // Comments edge case
        TestCase(
            name = "edge_comments_in_code",
            category = "edge_cases",
            code = """
                // This is a single-line comment
                cube([10, 10, 10]); // inline comment
                /* Multi-line
                   comment block */
                sphere(r = 5);
            """.trimIndent()
        ),
        // Nested expressions edge case
        TestCase(
            name = "edge_nested_expressions",
            category = "edge_cases",
            code = """
                cube([(2 + 3) * 2, (4 - 1) * 3, (1 + 1) * 5]);
            """.trimIndent()
        ),
        // Variable references edge case
        TestCase(
            name = "edge_variable_references",
            category = "edge_cases",
            code = """
                a = 5;
                b = a * 2;
                c = b + a;
                cube([a, b, c]);
            """.trimIndent()
        ),
        // Multiline code edge case
        TestCase(
            name = "edge_multiline_code",
            category = "edge_cases",
            code = """
                translate(
                    [10, 20, 30]
                )
                rotate(
                    [45, 0, 0]
                )
                cube(
                    [5, 5, 5]
                );
            """.trimIndent()
        ),
        // Named parameters edge case
        TestCase(
            name = "edge_named_parameters",
            category = "edge_cases",
            code = """
                cylinder(h = 20, r1 = 10, r2 = 5, center = true);
            """.trimIndent()
        )
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
    }

    /** Returns all test cases for a given category. */
    fun byCategory(category: String): List<TestCase> =
        allCases.filter { it.category == category }

    /** Returns the total count of test cases. */
    val size: Int get() = allCases.size
}
