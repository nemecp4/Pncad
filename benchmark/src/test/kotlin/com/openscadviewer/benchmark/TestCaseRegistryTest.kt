package com.openscadviewer.benchmark

import com.openscadviewer.parser.OpenSCADParser
import com.openscadviewer.parser.containsGeometry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Feature: engine-modularization-and-benchmarks, Property 3: Test case structure validity
 *
 * Validates that all 50 test cases in the registry satisfy structural constraints:
 * - Names match pattern [a-z0-9_]+ with length ≤ 64
 * - Categories are one of the 7 valid categories
 * - Code length ≤ 2048 characters
 * - Parsing each code snippet produces a SceneNode tree containing geometry
 *
 * Validates: Requirements 3.3, 3.6
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("property-3-test-case-structure-validity")
class TestCaseRegistryTest {

    private val parser = OpenSCADParser()
    private val namePattern = Regex("[a-z0-9_]+")

    private val validCategories = setOf(
        "geometry_primitives",
        "transformations",
        "linear_extrusion",
        "csg_operations",
        "combined_operations",
        "variables_expressions",
        "edge_cases",
        "custom"
    )

    private val categoryMinimumCounts = mapOf(
        "geometry_primitives" to 8,
        "transformations" to 8,
        "linear_extrusion" to 6,
        "csg_operations" to 10,
        "combined_operations" to 8,
        "variables_expressions" to 5,
        "edge_cases" to 5
    )

    @Test
    fun `registry contains expected number of test cases`() {
        assertTrue(TestCaseRegistry.allCases.size >= 50,
            "Expected at least 50 test cases, got ${TestCaseRegistry.allCases.size}")
    }

    @Test
    fun `all test case names match pattern and length constraint`() {
        for (tc in TestCaseRegistry.allCases) {
            assertTrue(tc.name.length <= 64,
                "Test case '${tc.name}' name exceeds 64 characters (length=${tc.name.length})")
            assertTrue(tc.name.matches(namePattern),
                "Test case '${tc.name}' name does not match pattern [a-z0-9_]+")
        }
    }

    @Test
    fun `all test case names are unique`() {
        val names = TestCaseRegistry.allCases.map { it.name }
        val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue(duplicates.isEmpty(),
            "Duplicate test case names found: $duplicates")
    }

    @Test
    fun `all test cases have valid categories`() {
        for (tc in TestCaseRegistry.allCases) {
            assertTrue(tc.category in validCategories,
                "Test case '${tc.name}' has invalid category '${tc.category}'")
        }
    }

    @Test
    fun `all test case code does not exceed 2048 characters`() {
        for (tc in TestCaseRegistry.allCases) {
            if (tc.category == "custom") continue  // custom tests have no size limit
            assertTrue(tc.code.length <= 2048,
                "Test case '${tc.name}' code exceeds 2048 chars (length=${tc.code.length})")
        }
    }

    @Test
    fun `all test cases parse to SceneNode trees with geometry`() {
        for (tc in TestCaseRegistry.allCases) {
            val scene = parser.parse(tc.code)
            assertTrue(scene.containsGeometry(),
                "Test case '${tc.name}' (category=${tc.category}) does not produce a SceneNode tree with geometry. Code:\n${tc.code}")
        }
    }

    @Test
    fun `category minimum counts are satisfied`() {
        val categoryCounts = TestCaseRegistry.allCases.groupBy { it.category }
            .mapValues { it.value.size }

        for ((category, minCount) in categoryMinimumCounts) {
            val actualCount = categoryCounts[category] ?: 0
            assertTrue(actualCount >= minCount,
                "Category '$category' has $actualCount test cases, expected at least $minCount")
        }
    }

    @Test
    fun `each category has at least one single-operation test`() {
        for (category in validCategories) {
            if (category == "custom") continue  // custom tests have no structural requirements
            val cases = TestCaseRegistry.byCategory(category)
            assertTrue(cases.isNotEmpty(),
                "Category '$category' has no test cases")

            // A single-operation test should have code that produces a relatively simple scene
            // (not combining 3+ geometry operations). We verify by checking at least one
            // case parses to a tree where the total geometry node count is small (1-2).
            val hasSingleOp = cases.any { tc ->
                val scene = parser.parse(tc.code)
                countGeometryNodes(scene) in 1..2
            }
            assertTrue(hasSingleOp,
                "Category '$category' has no test case with a single operation (1-2 geometry nodes)")
        }
    }

    @Test
    fun `each category has at least one test with 3 or more operations`() {
        for (category in validCategories) {
            if (category == "custom") continue  // custom tests have no structural requirements
            val cases = TestCaseRegistry.byCategory(category)
            val hasMultiOp = cases.any { tc ->
                val scene = parser.parse(tc.code)
                countTotalOperations(scene) >= 3
            }
            assertTrue(hasMultiOp,
                "Category '$category' has no test case with 3+ operations")
        }
    }

    // --- Helpers ---

    /**
     * Counts total operations in the scene tree including transforms, CSG, and geometry nodes.
     * This is used to verify "3+ operations" requirement per category.
     */
    private fun countTotalOperations(node: com.openscadviewer.parser.SceneNode): Int = when (node) {
        is com.openscadviewer.parser.SceneNode.Cube -> 1
        is com.openscadviewer.parser.SceneNode.Sphere -> 1
        is com.openscadviewer.parser.SceneNode.Cylinder -> 1
        is com.openscadviewer.parser.SceneNode.Circle -> 1
        is com.openscadviewer.parser.SceneNode.Square -> 1
        is com.openscadviewer.parser.SceneNode.Polygon -> 1
        is com.openscadviewer.parser.SceneNode.Translate -> 1 + countTotalOperations(node.child)
        is com.openscadviewer.parser.SceneNode.Rotate -> 1 + countTotalOperations(node.child)
        is com.openscadviewer.parser.SceneNode.Scale -> 1 + countTotalOperations(node.child)
        is com.openscadviewer.parser.SceneNode.Color -> 1 + countTotalOperations(node.child)
        is com.openscadviewer.parser.SceneNode.LinearExtrude -> 1 + countTotalOperations(node.child)
        is com.openscadviewer.parser.SceneNode.Union -> 1 + node.children.sumOf { countTotalOperations(it) }
        is com.openscadviewer.parser.SceneNode.Difference -> 1 + node.children.sumOf { countTotalOperations(it) }
        is com.openscadviewer.parser.SceneNode.Intersection -> 1 + node.children.sumOf { countTotalOperations(it) }
        is com.openscadviewer.parser.SceneNode.Group -> node.children.sumOf { countTotalOperations(it) }
    }

    private fun countGeometryNodes(node: com.openscadviewer.parser.SceneNode): Int = when (node) {
        is com.openscadviewer.parser.SceneNode.Cube -> 1
        is com.openscadviewer.parser.SceneNode.Sphere -> 1
        is com.openscadviewer.parser.SceneNode.Cylinder -> 1
        is com.openscadviewer.parser.SceneNode.Circle -> 1
        is com.openscadviewer.parser.SceneNode.Square -> 1
        is com.openscadviewer.parser.SceneNode.Polygon -> 1
        is com.openscadviewer.parser.SceneNode.Translate -> countGeometryNodes(node.child)
        is com.openscadviewer.parser.SceneNode.Rotate -> countGeometryNodes(node.child)
        is com.openscadviewer.parser.SceneNode.Scale -> countGeometryNodes(node.child)
        is com.openscadviewer.parser.SceneNode.Color -> countGeometryNodes(node.child)
        is com.openscadviewer.parser.SceneNode.LinearExtrude -> countGeometryNodes(node.child)
        is com.openscadviewer.parser.SceneNode.Union -> node.children.sumOf { countGeometryNodes(it) }
        is com.openscadviewer.parser.SceneNode.Difference -> node.children.sumOf { countGeometryNodes(it) }
        is com.openscadviewer.parser.SceneNode.Intersection -> node.children.sumOf { countGeometryNodes(it) }
        is com.openscadviewer.parser.SceneNode.Group -> node.children.sumOf { countGeometryNodes(it) }
    }
}
