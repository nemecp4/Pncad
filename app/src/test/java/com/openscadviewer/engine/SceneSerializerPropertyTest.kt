package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode
import net.jqwik.api.*
import net.jqwik.api.arbitraries.DoubleArbitrary
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Property-based test for SceneSerializer round-trip.
 *
 * Feature: cgal-compute-engine, Property 4: Scene graph serialization round-trip
 *
 * Validates: Requirements 4.1
 */
class SceneSerializerPropertyTest {

    companion object {
        private const val FLOAT_TOLERANCE = 1e-9
    }

    @Property(tries = 100)
    @Tag("Feature_cgal-compute-engine")
    @Tag("Property_4_Scene_graph_serialization_round-trip")
    fun `serialization round-trip preserves structure`(@ForAll("sceneNodes") node: SceneNode) {
        val json = SceneSerializer.toJson(node)
        val deserialized = fromJson(json)
        assertStructurallyEquivalent(node, deserialized)
    }

    @Provide
    fun sceneNodes(): Arbitrary<SceneNode> {
        return sceneNodeArbitrary(maxDepth = 4)
    }

    // --- Generators ---

    private fun sceneNodeArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
        if (maxDepth <= 0) {
            return leafNodeArbitrary()
        }
        return Arbitraries.oneOf(
            leafNodeArbitrary(),
            transformNodeArbitrary(maxDepth - 1),
            csgNodeArbitrary(maxDepth - 1)
        )
    }

    private fun leafNodeArbitrary(): Arbitrary<SceneNode> {
        return Arbitraries.oneOf(
            cubeArbitrary(),
            sphereArbitrary(),
            cylinderArbitrary(),
            circleArbitrary(),
            squareArbitrary(),
            polygonArbitrary()
        )
    }

    private fun transformNodeArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return Arbitraries.oneOf(
            translateArbitrary(childDepth),
            rotateArbitrary(childDepth),
            scaleArbitrary(childDepth),
            colorArbitrary(childDepth),
            linearExtrudeArbitrary(childDepth)
        )
    }

    private fun csgNodeArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return Arbitraries.oneOf(
            unionArbitrary(childDepth),
            differenceArbitrary(childDepth),
            intersectionArbitrary(childDepth),
            groupArbitrary(childDepth)
        )
    }

    private fun cubeArbitrary(): Arbitrary<SceneNode> {
        return Combinators.combine(
            positiveDouble(),
            positiveDouble(),
            positiveDouble(),
            Arbitraries.of(true, false)
        ).`as` { sx, sy, sz, center -> SceneNode.Cube(sx, sy, sz, center) }
    }

    private fun sphereArbitrary(): Arbitrary<SceneNode> {
        return Combinators.combine(
            positiveDouble(),
            segmentsArbitrary()
        ).`as` { radius, segments -> SceneNode.Sphere(radius, segments) }
    }

    private fun cylinderArbitrary(): Arbitrary<SceneNode> {
        return Combinators.combine(
            positiveDouble(),
            positiveDouble(),
            positiveDouble(),
            Arbitraries.of(true, false),
            segmentsArbitrary()
        ).`as` { h, r1, r2, center, seg -> SceneNode.Cylinder(h, r1, r2, center, seg) }
    }

    private fun circleArbitrary(): Arbitrary<SceneNode> {
        return Combinators.combine(
            positiveDouble(),
            segmentsArbitrary()
        ).`as` { radius, segments -> SceneNode.Circle(radius, segments) }
    }

    private fun squareArbitrary(): Arbitrary<SceneNode> {
        return Combinators.combine(
            positiveDouble(),
            positiveDouble(),
            Arbitraries.of(true, false)
        ).`as` { sx, sy, center -> SceneNode.Square(sx, sy, center) }
    }

    private fun polygonArbitrary(): Arbitrary<SceneNode> {
        return Arbitraries.integers().between(3, 8).flatMap { numPoints ->
            val pointArbitrary = Combinators.combine(
                finiteDouble(),
                finiteDouble()
            ).`as` { x, y -> Pair(x, y) }
            pointArbitrary.list().ofSize(numPoints).map { points ->
                SceneNode.Polygon(points)
            }
        }
    }

    private fun translateArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return Combinators.combine(
            finiteDouble(),
            finiteDouble(),
            finiteDouble(),
            sceneNodeArbitrary(childDepth)
        ).`as` { x, y, z, child -> SceneNode.Translate(x, y, z, child) }
    }

    private fun rotateArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return Combinators.combine(
            Arbitraries.doubles().between(-360.0, 360.0),
            Arbitraries.doubles().between(-360.0, 360.0),
            Arbitraries.doubles().between(-360.0, 360.0),
            sceneNodeArbitrary(childDepth)
        ).`as` { x, y, z, child -> SceneNode.Rotate(x, y, z, child) }
    }

    private fun scaleArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return Combinators.combine(
            positiveDouble(),
            positiveDouble(),
            positiveDouble(),
            sceneNodeArbitrary(childDepth)
        ).`as` { x, y, z, child -> SceneNode.Scale(x, y, z, child) }
    }

    private fun colorArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return Combinators.combine(
            Arbitraries.floats().between(0f, 1f),
            Arbitraries.floats().between(0f, 1f),
            Arbitraries.floats().between(0f, 1f),
            Arbitraries.floats().between(0f, 1f),
            sceneNodeArbitrary(childDepth)
        ).`as` { r, g, b, a, child -> SceneNode.Color(r, g, b, a, child) }
    }

    private fun linearExtrudeArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return Combinators.combine(
            positiveDouble(),
            sceneNodeArbitrary(childDepth)
        ).`as` { height, child -> SceneNode.LinearExtrude(height, child) }
    }

    private fun unionArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return childrenListArbitrary(childDepth).map { children -> SceneNode.Union(children) }
    }

    private fun differenceArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return childrenListArbitrary(childDepth).map { children -> SceneNode.Difference(children) }
    }

    private fun intersectionArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return childrenListArbitrary(childDepth).map { children -> SceneNode.Intersection(children) }
    }

    private fun groupArbitrary(childDepth: Int): Arbitrary<SceneNode> {
        return childrenListArbitrary(childDepth).map { children -> SceneNode.Group(children) }
    }

    private fun childrenListArbitrary(childDepth: Int): Arbitrary<List<SceneNode>> {
        return sceneNodeArbitrary(childDepth).list().ofMinSize(1).ofMaxSize(4)
    }

    private fun positiveDouble(): DoubleArbitrary {
        return Arbitraries.doubles().between(0.1, 100.0)
    }

    private fun finiteDouble(): DoubleArbitrary {
        return Arbitraries.doubles().between(-100.0, 100.0)
    }

    private fun segmentsArbitrary(): Arbitrary<Int> {
        return Arbitraries.integers().between(3, 64)
    }

    // --- Deserialization (test-only) ---

    private fun fromJson(jsonString: String): SceneNode {
        val obj = JSONObject(jsonString)
        return nodeFromJson(obj)
    }

    private fun nodeFromJson(obj: JSONObject): SceneNode {
        return when (val type = obj.getString("type")) {
            "cube" -> SceneNode.Cube(
                sizeX = obj.getDouble("sizeX"),
                sizeY = obj.getDouble("sizeY"),
                sizeZ = obj.getDouble("sizeZ"),
                center = obj.getBoolean("center")
            )
            "sphere" -> SceneNode.Sphere(
                radius = obj.getDouble("radius"),
                segments = obj.getInt("segments")
            )
            "cylinder" -> SceneNode.Cylinder(
                height = obj.getDouble("height"),
                radius1 = obj.getDouble("radius1"),
                radius2 = obj.getDouble("radius2"),
                center = obj.getBoolean("center"),
                segments = obj.getInt("segments")
            )
            "circle" -> SceneNode.Circle(
                radius = obj.getDouble("radius"),
                segments = obj.getInt("segments")
            )
            "square" -> SceneNode.Square(
                sizeX = obj.getDouble("sizeX"),
                sizeY = obj.getDouble("sizeY"),
                center = obj.getBoolean("center")
            )
            "polygon" -> {
                val pts = obj.getJSONArray("points")
                val points = (0 until pts.length()).map { i ->
                    val pt = pts.getJSONArray(i)
                    Pair(pt.getDouble(0), pt.getDouble(1))
                }
                SceneNode.Polygon(points)
            }
            "linear_extrude" -> SceneNode.LinearExtrude(
                height = obj.getDouble("height"),
                child = nodeFromJson(obj.getJSONObject("child"))
            )
            "translate" -> SceneNode.Translate(
                x = obj.getDouble("x"),
                y = obj.getDouble("y"),
                z = obj.getDouble("z"),
                child = nodeFromJson(obj.getJSONObject("child"))
            )
            "rotate" -> SceneNode.Rotate(
                x = obj.getDouble("x"),
                y = obj.getDouble("y"),
                z = obj.getDouble("z"),
                child = nodeFromJson(obj.getJSONObject("child"))
            )
            "scale" -> SceneNode.Scale(
                x = obj.getDouble("x"),
                y = obj.getDouble("y"),
                z = obj.getDouble("z"),
                child = nodeFromJson(obj.getJSONObject("child"))
            )
            "color" -> SceneNode.Color(
                r = obj.getDouble("r").toFloat(),
                g = obj.getDouble("g").toFloat(),
                b = obj.getDouble("b").toFloat(),
                a = obj.getDouble("a").toFloat(),
                child = nodeFromJson(obj.getJSONObject("child"))
            )
            "union" -> SceneNode.Union(childrenFromJson(obj.getJSONArray("children")))
            "difference" -> SceneNode.Difference(childrenFromJson(obj.getJSONArray("children")))
            "intersection" -> SceneNode.Intersection(childrenFromJson(obj.getJSONArray("children")))
            "group" -> SceneNode.Group(childrenFromJson(obj.getJSONArray("children")))
            else -> throw IllegalArgumentException("Unknown node type: $type")
        }
    }

    private fun childrenFromJson(arr: JSONArray): List<SceneNode> {
        return (0 until arr.length()).map { i -> nodeFromJson(arr.getJSONObject(i)) }
    }

    // --- Structural equivalence assertion ---

    private fun assertStructurallyEquivalent(expected: SceneNode, actual: SceneNode) {
        when {
            expected is SceneNode.Cube && actual is SceneNode.Cube -> {
                assertDoubleEquals(expected.sizeX, actual.sizeX, "Cube.sizeX")
                assertDoubleEquals(expected.sizeY, actual.sizeY, "Cube.sizeY")
                assertDoubleEquals(expected.sizeZ, actual.sizeZ, "Cube.sizeZ")
                assert(expected.center == actual.center) {
                    "Cube.center: expected=${expected.center}, actual=${actual.center}"
                }
            }
            expected is SceneNode.Sphere && actual is SceneNode.Sphere -> {
                assertDoubleEquals(expected.radius, actual.radius, "Sphere.radius")
                assert(expected.segments == actual.segments) {
                    "Sphere.segments: expected=${expected.segments}, actual=${actual.segments}"
                }
            }
            expected is SceneNode.Cylinder && actual is SceneNode.Cylinder -> {
                assertDoubleEquals(expected.height, actual.height, "Cylinder.height")
                assertDoubleEquals(expected.radius1, actual.radius1, "Cylinder.radius1")
                assertDoubleEquals(expected.radius2, actual.radius2, "Cylinder.radius2")
                assert(expected.center == actual.center) {
                    "Cylinder.center: expected=${expected.center}, actual=${actual.center}"
                }
                assert(expected.segments == actual.segments) {
                    "Cylinder.segments: expected=${expected.segments}, actual=${actual.segments}"
                }
            }
            expected is SceneNode.Circle && actual is SceneNode.Circle -> {
                assertDoubleEquals(expected.radius, actual.radius, "Circle.radius")
                assert(expected.segments == actual.segments) {
                    "Circle.segments: expected=${expected.segments}, actual=${actual.segments}"
                }
            }
            expected is SceneNode.Square && actual is SceneNode.Square -> {
                assertDoubleEquals(expected.sizeX, actual.sizeX, "Square.sizeX")
                assertDoubleEquals(expected.sizeY, actual.sizeY, "Square.sizeY")
                assert(expected.center == actual.center) {
                    "Square.center: expected=${expected.center}, actual=${actual.center}"
                }
            }
            expected is SceneNode.Polygon && actual is SceneNode.Polygon -> {
                assert(expected.points.size == actual.points.size) {
                    "Polygon.points.size: expected=${expected.points.size}, actual=${actual.points.size}"
                }
                expected.points.zip(actual.points).forEachIndexed { i, (exp, act) ->
                    assertDoubleEquals(exp.first, act.first, "Polygon.points[$i].x")
                    assertDoubleEquals(exp.second, act.second, "Polygon.points[$i].y")
                }
            }
            expected is SceneNode.LinearExtrude && actual is SceneNode.LinearExtrude -> {
                assertDoubleEquals(expected.height, actual.height, "LinearExtrude.height")
                assertStructurallyEquivalent(expected.child, actual.child)
            }
            expected is SceneNode.Translate && actual is SceneNode.Translate -> {
                assertDoubleEquals(expected.x, actual.x, "Translate.x")
                assertDoubleEquals(expected.y, actual.y, "Translate.y")
                assertDoubleEquals(expected.z, actual.z, "Translate.z")
                assertStructurallyEquivalent(expected.child, actual.child)
            }
            expected is SceneNode.Rotate && actual is SceneNode.Rotate -> {
                assertDoubleEquals(expected.x, actual.x, "Rotate.x")
                assertDoubleEquals(expected.y, actual.y, "Rotate.y")
                assertDoubleEquals(expected.z, actual.z, "Rotate.z")
                assertStructurallyEquivalent(expected.child, actual.child)
            }
            expected is SceneNode.Scale && actual is SceneNode.Scale -> {
                assertDoubleEquals(expected.x, actual.x, "Scale.x")
                assertDoubleEquals(expected.y, actual.y, "Scale.y")
                assertDoubleEquals(expected.z, actual.z, "Scale.z")
                assertStructurallyEquivalent(expected.child, actual.child)
            }
            expected is SceneNode.Color && actual is SceneNode.Color -> {
                assertFloatEquals(expected.r, actual.r, "Color.r")
                assertFloatEquals(expected.g, actual.g, "Color.g")
                assertFloatEquals(expected.b, actual.b, "Color.b")
                assertFloatEquals(expected.a, actual.a, "Color.a")
                assertStructurallyEquivalent(expected.child, actual.child)
            }
            expected is SceneNode.Union && actual is SceneNode.Union -> {
                assertChildrenEquivalent(expected.children, actual.children, "Union")
            }
            expected is SceneNode.Difference && actual is SceneNode.Difference -> {
                assertChildrenEquivalent(expected.children, actual.children, "Difference")
            }
            expected is SceneNode.Intersection && actual is SceneNode.Intersection -> {
                assertChildrenEquivalent(expected.children, actual.children, "Intersection")
            }
            expected is SceneNode.Group && actual is SceneNode.Group -> {
                assertChildrenEquivalent(expected.children, actual.children, "Group")
            }
            else -> {
                throw AssertionError(
                    "Node type mismatch: expected=${expected::class.simpleName}, actual=${actual::class.simpleName}"
                )
            }
        }
    }

    private fun assertChildrenEquivalent(
        expected: List<SceneNode>,
        actual: List<SceneNode>,
        nodeName: String
    ) {
        assert(expected.size == actual.size) {
            "$nodeName.children.size: expected=${expected.size}, actual=${actual.size}"
        }
        expected.zip(actual).forEachIndexed { i, (exp, act) ->
            try {
                assertStructurallyEquivalent(exp, act)
            } catch (e: AssertionError) {
                throw AssertionError("$nodeName.children[$i]: ${e.message}", e)
            }
        }
    }

    private fun assertDoubleEquals(expected: Double, actual: Double, field: String) {
        assert(abs(expected - actual) < FLOAT_TOLERANCE) {
            "$field: expected=$expected, actual=$actual, diff=${abs(expected - actual)}"
        }
    }

    private fun assertFloatEquals(expected: Float, actual: Float, field: String) {
        // Float values go through toDouble() during serialization and back through toFloat()
        // during deserialization, so we use a tolerance appropriate for float precision
        assert(abs(expected - actual) < 1e-6f) {
            "$field: expected=$expected, actual=$actual, diff=${abs(expected - actual)}"
        }
    }
}
