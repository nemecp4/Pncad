package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode
import kotlinx.coroutines.test.runTest
import net.jqwik.api.*
import net.jqwik.api.Combinators.combine
import net.jqwik.api.lifecycle.BeforeProperty

/**
 * Property 5: Degenerate primitives excluded from computation
 *
 * For any scene graph containing a mix of valid primitives (positive dimensions)
 * and degenerate primitives (zero or negative size/radius/height), the compute result
 * SHALL contain geometry only from the valid primitives, and the degenerate primitives
 * SHALL not cause a computation failure.
 *
 * **Validates: Requirements 4.8, 5.7**
 */
@Tag("Feature: cgal-compute-engine, Property 5: Degenerate primitives excluded from computation")
class DegeneratePrimitivesPropertyTest {

    private lateinit var engine: KotlinComputeEngine

    @BeforeProperty
    fun setUp() {
        engine = KotlinComputeEngine()
    }

    /**
     * Degenerate-only scene graphs should produce zero vertices.
     * Verifies that primitives with zero or negative dimensions are excluded.
     */
    @Property(tries = 100)
    fun degenerateOnlySceneProducesEmptyResult(
        @ForAll("degenerateOnlyTrees") tree: SceneNode
    ) = runTest {
        val result = engine.compute(tree)

        assert(result.isSuccess) {
            "Computation should not fail for degenerate primitives, but got: ${result.exceptionOrNull()}"
        }
        val mesh = result.getOrThrow()
        assert(mesh.vertices.isEmpty()) {
            "Expected zero vertices for degenerate-only tree, got ${mesh.vertices.size / 3} vertices. Tree: $tree"
        }
    }

    /**
     * Mixed scene graphs (valid + degenerate) should produce non-zero vertices.
     * The valid primitives contribute geometry; the degenerate ones do not cause failure.
     */
    @Property(tries = 100)
    fun mixedSceneSucceedsWithNonZeroVertices(
        @ForAll("mixedTrees") tree: SceneNode
    ) = runTest {
        val result = engine.compute(tree)

        assert(result.isSuccess) {
            "Computation should not fail for mixed trees, but got: ${result.exceptionOrNull()}"
        }
        val mesh = result.getOrThrow()
        assert(mesh.vertices.isNotEmpty()) {
            "Expected non-zero vertices for mixed tree containing valid primitives, got 0 vertices. Tree: $tree"
        }
    }

    /**
     * Any scene containing degenerate primitives should never throw an exception.
     * This validates the "SHALL not cause a computation failure" requirement.
     */
    @Property(tries = 100)
    fun degeneratePrimitivesNeverCauseFailure(
        @ForAll("anyTreeWithDegenerates") tree: SceneNode
    ) = runTest {
        val result = engine.compute(tree)

        assert(result.isSuccess) {
            "Computation must not fail due to degenerate primitives, but got: ${result.exceptionOrNull()}"
        }
    }

    // --- Providers ---

    @Provide
    fun degenerateOnlyTrees(): Arbitrary<SceneNode> {
        return degenerateTreeArbitrary(maxDepth = 3)
    }

    @Provide
    fun mixedTrees(): Arbitrary<SceneNode> {
        return mixedTreeArbitrary(maxDepth = 3)
    }

    @Provide
    fun anyTreeWithDegenerates(): Arbitrary<SceneNode> {
        return Arbitraries.oneOf(
            degenerateTreeArbitrary(maxDepth = 3),
            mixedTreeArbitrary(maxDepth = 3)
        )
    }

    companion object {

        // --- Degenerate primitive generators ---

        /**
         * Generates cubes with at least one zero or negative dimension.
         */
        private fun degenerateCubeArbitrary(): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(-10.0, 0.0),
                Arbitraries.doubles().between(0.1, 50.0),
                Arbitraries.doubles().between(0.1, 50.0),
                Arbitraries.of(true, false)
            ).`as` { badDim, good1, good2, center ->
                // Randomly place the degenerate dimension
                SceneNode.Cube(badDim, good1, good2, center) as SceneNode
            }
        }

        /**
         * Generates spheres with zero or negative radius.
         */
        private fun degenerateSphereArbitrary(): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(-10.0, 0.0),
                Arbitraries.integers().between(8, 16)
            ).`as` { radius, segments ->
                SceneNode.Sphere(radius, segments) as SceneNode
            }
        }

        /**
         * Generates cylinders with zero or negative height or radii.
         */
        private fun degenerateCylinderArbitrary(): Arbitrary<SceneNode> {
            return Arbitraries.oneOf(
                // Degenerate height
                combine(
                    Arbitraries.doubles().between(-10.0, 0.0),
                    Arbitraries.doubles().between(0.1, 20.0),
                    Arbitraries.doubles().between(0.1, 20.0),
                    Arbitraries.of(true, false),
                    Arbitraries.integers().between(8, 16)
                ).`as` { h, r1, r2, center, seg ->
                    SceneNode.Cylinder(h, r1, r2, center, seg) as SceneNode
                },
                // Degenerate radius1
                combine(
                    Arbitraries.doubles().between(0.1, 20.0),
                    Arbitraries.doubles().between(-10.0, 0.0),
                    Arbitraries.doubles().between(-10.0, 0.0),
                    Arbitraries.of(true, false),
                    Arbitraries.integers().between(8, 16)
                ).`as` { h, r1, r2, center, seg ->
                    SceneNode.Cylinder(h, r1, r2, center, seg) as SceneNode
                }
            )
        }

        /**
         * Generates a single degenerate primitive.
         */
        private fun degeneratePrimitiveArbitrary(): Arbitrary<SceneNode> {
            return Arbitraries.oneOf(
                degenerateCubeArbitrary(),
                degenerateSphereArbitrary(),
                degenerateCylinderArbitrary()
            )
        }

        // --- Valid primitive generators ---

        private fun validCubeArbitrary(): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(1.0, 50.0),
                Arbitraries.doubles().between(1.0, 50.0),
                Arbitraries.doubles().between(1.0, 50.0),
                Arbitraries.of(true, false)
            ).`as` { sx, sy, sz, center ->
                SceneNode.Cube(sx, sy, sz, center) as SceneNode
            }
        }

        private fun validSphereArbitrary(): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(1.0, 30.0),
                Arbitraries.integers().between(8, 16)
            ).`as` { radius, segments ->
                SceneNode.Sphere(radius, segments) as SceneNode
            }
        }

        private fun validCylinderArbitrary(): Arbitrary<SceneNode> {
            return combine(
                Arbitraries.doubles().between(1.0, 30.0),
                Arbitraries.doubles().between(1.0, 20.0),
                Arbitraries.doubles().between(1.0, 20.0),
                Arbitraries.of(true, false),
                Arbitraries.integers().between(8, 16)
            ).`as` { h, r1, r2, center, seg ->
                SceneNode.Cylinder(h, r1, r2, center, seg) as SceneNode
            }
        }

        private fun validPrimitiveArbitrary(): Arbitrary<SceneNode> {
            return Arbitraries.oneOf(
                validCubeArbitrary(),
                validSphereArbitrary(),
                validCylinderArbitrary()
            )
        }

        // --- Tree generators ---

        /**
         * Generates a tree containing ONLY degenerate primitives wrapped in
         * structural/CSG nodes.
         */
        private fun degenerateTreeArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
            if (maxDepth <= 0) {
                return degeneratePrimitiveArbitrary()
            }
            return Arbitraries.frequencyOf(
                Tuple.of(4, degeneratePrimitiveArbitrary()),
                Tuple.of(2, wrapInTransform(degenerateTreeArbitrary(maxDepth - 1))),
                Tuple.of(2, wrapInCsg(degenerateTreeArbitrary(maxDepth - 1)))
            )
        }

        /**
         * Generates a tree containing at least one valid primitive AND at least one
         * degenerate primitive, mixed together in CSG/Group nodes.
         */
        private fun mixedTreeArbitrary(maxDepth: Int): Arbitrary<SceneNode> {
            val validChild = validPrimitiveArbitrary()
            val degenerateChild = degeneratePrimitiveArbitrary()

            // Create a Group with at least one valid and one degenerate primitive
            return combine(
                validChild.list().ofMinSize(1).ofMaxSize(3),
                degenerateChild.list().ofMinSize(1).ofMaxSize(3)
            ).`as` { validNodes, degenerateNodes ->
                val allChildren = (validNodes + degenerateNodes).shuffled()
                SceneNode.Group(allChildren) as SceneNode
            }
        }

        private fun wrapInTransform(childArb: Arbitrary<SceneNode>): Arbitrary<SceneNode> {
            return Arbitraries.oneOf(
                combine(
                    Arbitraries.doubles().between(-50.0, 50.0),
                    Arbitraries.doubles().between(-50.0, 50.0),
                    Arbitraries.doubles().between(-50.0, 50.0),
                    childArb
                ).`as` { x, y, z, child ->
                    SceneNode.Translate(x, y, z, child) as SceneNode
                },
                combine(
                    Arbitraries.doubles().between(0.0, 360.0),
                    Arbitraries.doubles().between(0.0, 360.0),
                    Arbitraries.doubles().between(0.0, 360.0),
                    childArb
                ).`as` { x, y, z, child ->
                    SceneNode.Rotate(x, y, z, child) as SceneNode
                }
            )
        }

        private fun wrapInCsg(childArb: Arbitrary<SceneNode>): Arbitrary<SceneNode> {
            val childrenArb = childArb.list().ofMinSize(1).ofMaxSize(3)
            return Arbitraries.oneOf(
                childrenArb.map { SceneNode.Union(it) as SceneNode },
                childrenArb.map { SceneNode.Group(it) as SceneNode }
            )
        }
    }
}
