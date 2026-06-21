package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode
import kotlinx.coroutines.runBlocking
import net.jqwik.api.*
import net.jqwik.api.Combinators.combine
import net.jqwik.api.lifecycle.BeforeProperty

/**
 * Property 1: Mesh output structural invariants
 *
 * **Validates: Requirements 1.1, 4.5**
 *
 * For any valid SceneNode tree that produces a non-empty mesh result, the output
 * arrays SHALL satisfy:
 * - vertices.size % 9 == 0 (complete triangles only)
 * - normals.size == vertices.size
 * - colors.size == (vertices.size / 3) * 4
 */
@Tag("Feature: cgal-compute-engine, Property 1: Mesh output structural invariants")
class MeshOutputStructuralInvariantsTest {

    private lateinit var engine: KotlinComputeEngine

    @BeforeProperty
    fun setup() {
        engine = KotlinComputeEngine()
    }

    @Property(tries = 100)
    fun `mesh output has complete triangles - vertices divisible by 9`(
        @ForAll("sceneNodeTrees") scene: SceneNode
    ) {
        val result = runBlocking { engine.compute(scene) }
        val meshResult = result.getOrThrow()
        assert(meshResult.vertices.size % 9 == 0) {
            "vertices.size (${meshResult.vertices.size}) must be divisible by 9 (complete triangles)"
        }
    }

    @Property(tries = 100)
    fun `normals array size equals vertices array size`(
        @ForAll("sceneNodeTrees") scene: SceneNode
    ) {
        val result = runBlocking { engine.compute(scene) }
        val meshResult = result.getOrThrow()
        assert(meshResult.normals.size == meshResult.vertices.size) {
            "normals.size (${meshResult.normals.size}) must equal vertices.size (${meshResult.vertices.size})"
        }
    }

    @Property(tries = 100)
    fun `colors array has 4 components per vertex`(
        @ForAll("sceneNodeTrees") scene: SceneNode
    ) {
        val result = runBlocking { engine.compute(scene) }
        val meshResult = result.getOrThrow()
        val expectedColorSize = (meshResult.vertices.size / 3) * 4
        assert(meshResult.colors.size == expectedColorSize) {
            "colors.size (${meshResult.colors.size}) must equal (vertices.size / 3) * 4 = $expectedColorSize"
        }
    }

    @Provide
    fun sceneNodeTrees(): Arbitrary<SceneNode> {
        return sceneNodeArbitrary(maxDepth = 4)
    }

    companion object {
        /**
         * Generates random SceneNode trees with configurable max depth.
         * Produces primitives at leaves and wraps them in CSG ops and transforms.
         */
        fun sceneNodeArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
            if (maxDepth <= 0) {
                return primitiveArbitrary()
            }
            return Arbitraries.frequencyOf(
                Tuple.of(5, primitiveArbitrary()),
                Tuple.of(2, transformArbitrary(maxDepth - 1)),
                Tuple.of(2, csgArbitrary(maxDepth - 1)),
                Tuple.of(1, colorArbitrary(maxDepth - 1))
            )
        }

        private fun primitiveArbitrary(): Arbitrary<SceneNode> {
            return Arbitraries.frequencyOf(
                Tuple.of(3, cubeArbitrary()),
                Tuple.of(3, sphereArbitrary()),
                Tuple.of(3, cylinderArbitrary())
            )
        }

        private fun cubeArbitrary(): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(0.1, 100.0),
                Arbitraries.doubles().between(0.1, 100.0),
                Arbitraries.doubles().between(0.1, 100.0),
                Arbitraries.of(true, false)
            ).`as` { sx, sy, sz, center ->
                SceneNode.Cube(sx, sy, sz, center) as SceneNode
            }
        }

        private fun sphereArbitrary(): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(0.1, 50.0),
                Arbitraries.integers().between(8, 32)
            ).`as` { radius, segments ->
                SceneNode.Sphere(radius, segments) as SceneNode
            }
        }

        private fun cylinderArbitrary(): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(0.1, 50.0),
                Arbitraries.doubles().between(0.1, 30.0),
                Arbitraries.doubles().between(0.1, 30.0),
                Arbitraries.of(true, false),
                Arbitraries.integers().between(8, 32)
            ).`as` { height, r1, r2, center, segments ->
                SceneNode.Cylinder(height, r1, r2, center, segments) as SceneNode
            }
        }

        private fun transformArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
            return Arbitraries.frequencyOf(
                Tuple.of(1, translateArbitrary(maxDepth)),
                Tuple.of(1, rotateArbitrary(maxDepth)),
                Tuple.of(1, scaleArbitrary(maxDepth))
            )
        }

        private fun translateArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(-100.0, 100.0),
                Arbitraries.doubles().between(-100.0, 100.0),
                Arbitraries.doubles().between(-100.0, 100.0),
                sceneNodeArbitrary(maxDepth)
            ).`as` { x, y, z, child ->
                SceneNode.Translate(x, y, z, child) as SceneNode
            }
        }

        private fun rotateArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(0.0, 360.0),
                Arbitraries.doubles().between(0.0, 360.0),
                Arbitraries.doubles().between(0.0, 360.0),
                sceneNodeArbitrary(maxDepth)
            ).`as` { x, y, z, child ->
                SceneNode.Rotate(x, y, z, child) as SceneNode
            }
        }

        private fun scaleArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(0.1, 10.0),
                Arbitraries.doubles().between(0.1, 10.0),
                Arbitraries.doubles().between(0.1, 10.0),
                sceneNodeArbitrary(maxDepth)
            ).`as` { x, y, z, child ->
                SceneNode.Scale(x, y, z, child) as SceneNode
            }
        }

        private fun csgArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
            val childrenArb = sceneNodeArbitrary(maxDepth).list().ofMinSize(1).ofMaxSize(4)
            return Arbitraries.frequencyOf(
                Tuple.of(1, childrenArb.map { SceneNode.Union(it) as SceneNode }),
                Tuple.of(1, childrenArb.map { SceneNode.Difference(it) as SceneNode }),
                Tuple.of(1, childrenArb.map { SceneNode.Intersection(it) as SceneNode })
            )
        }

        private fun colorArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.floats().between(0f, 1f),
                Arbitraries.floats().between(0f, 1f),
                Arbitraries.floats().between(0f, 1f),
                Arbitraries.floats().between(0f, 1f),
                sceneNodeArbitrary(maxDepth)
            ).`as` { r, g, b, a, child ->
                SceneNode.Color(r, g, b, a, child) as SceneNode
            }
        }
    }
}
