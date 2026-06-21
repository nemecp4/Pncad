package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode
import kotlinx.coroutines.test.runTest
import net.jqwik.api.*
import net.jqwik.api.Combinators.combine
import net.jqwik.api.lifecycle.BeforeProperty

/**
 * Property 2: Empty scene graph produces empty result
 *
 * For any SceneNode tree that contains no geometry-producing leaf nodes
 * (only Group, Translate, Rotate, Scale nodes wrapping no primitives),
 * the Kotlin engine SHALL return a MeshResult with zero vertices.
 *
 * **Validates: Requirements 1.5**
 */
@Tag("cgal-compute-engine")
@Tag("Property-2")
class EmptySceneGraphPropertyTest {

    private lateinit var engine: KotlinComputeEngine

    @BeforeProperty
    fun setUp() {
        engine = KotlinComputeEngine()
    }

    @Property(tries = 100)
    fun emptySceneGraphProducesEmptyResult(
        @ForAll("structuralOnlyTrees") tree: SceneNode
    ) = runTest {
        val result = engine.compute(tree)

        assert(result.isSuccess) { "Computation should succeed for structural-only trees" }
        val mesh = result.getOrThrow()
        assert(mesh.vertices.isEmpty()) {
            "Expected zero vertices for structural-only tree, got ${mesh.vertices.size / 3} vertices. Tree: $tree"
        }
        assert(mesh.normals.isEmpty()) {
            "Expected zero normals for structural-only tree, got ${mesh.normals.size / 3} normals"
        }
        assert(mesh.colors.isEmpty()) {
            "Expected zero colors for structural-only tree, got ${mesh.colors.size / 4} colors"
        }
    }

    @Provide
    fun structuralOnlyTrees(): Arbitrary<SceneNode> {
        return structuralNode(3)
    }

    /**
     * Generates a random structural-only SceneNode tree.
     * Only produces Group, Translate, Rotate, Scale nodes.
     * Children never include primitives.
     */
    private fun structuralNode(maxDepth: Int): Arbitrary<SceneNode> {
        if (maxDepth <= 0) {
            // Base case: empty group
            return Arbitraries.just(SceneNode.Group(emptyList()) as SceneNode)
        }

        val childArbitrary = structuralNode(maxDepth - 1)
        val childrenArbitrary = childArbitrary.list().ofMinSize(0).ofMaxSize(4)

        val groupArbitrary: Arbitrary<SceneNode> = childrenArbitrary.map { children ->
            SceneNode.Group(children)
        }

        val translateArbitrary: Arbitrary<SceneNode> = combine(
            Arbitraries.doubles().between(-100.0, 100.0),
            Arbitraries.doubles().between(-100.0, 100.0),
            Arbitraries.doubles().between(-100.0, 100.0),
            childArbitrary
        ).`as` { x, y, z, child -> SceneNode.Translate(x, y, z, child) }

        val rotateArbitrary: Arbitrary<SceneNode> = combine(
            Arbitraries.doubles().between(0.0, 360.0),
            Arbitraries.doubles().between(0.0, 360.0),
            Arbitraries.doubles().between(0.0, 360.0),
            childArbitrary
        ).`as` { x, y, z, child -> SceneNode.Rotate(x, y, z, child) }

        val scaleArbitrary: Arbitrary<SceneNode> = combine(
            Arbitraries.doubles().between(0.1, 10.0),
            Arbitraries.doubles().between(0.1, 10.0),
            Arbitraries.doubles().between(0.1, 10.0),
            childArbitrary
        ).`as` { x, y, z, child -> SceneNode.Scale(x, y, z, child) }

        return Arbitraries.oneOf(
            groupArbitrary,
            translateArbitrary,
            rotateArbitrary,
            scaleArbitrary
        )
    }
}
