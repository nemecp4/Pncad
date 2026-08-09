package com.openscadviewer.engine.text

import com.openscadviewer.engine.MeshGenerator
import com.openscadviewer.parser.SceneNode
import net.jqwik.api.*
import net.jqwik.api.constraints.DoubleRange
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Assertions.*

/**
 * Property-based tests for text mesh generation in the Kotlin engine MeshGenerator.
 */
class TextMeshPropertyTest {

    private lateinit var generator: MeshGenerator

    @BeforeProperty
    fun setUp() {
        generator = MeshGenerator()
    }

    // ─── Property 11: 2D text mesh has all Z coordinates at zero ─────────────────

    /**
     * Property 11: 2D text mesh has all Z coordinates at zero.
     *
     * For any SceneNode.Text rendered without linear_extrude, all vertex Z coordinates
     * in the generated mesh shall be exactly 0.0f.
     *
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 50)
    fun allZCoordinatesAreZeroFor2DText(
        @ForAll("shortTextStrings") text: String,
        @ForAll @DoubleRange(min = 1.0, max = 50.0) size: Double
    ) {
        val node = SceneNode.Text(text = text, size = size)
        val mesh = generator.generate(node)

        // If no vertices produced, property trivially holds
        if (mesh.vertices.isEmpty()) return

        // Vertices are stored as x,y,z triplets — every 3rd float starting at index 2 is Z
        for (i in 2 until mesh.vertices.size step 3) {
            assertEquals(0.0f, mesh.vertices[i],
                "Vertex at index $i (Z coordinate of vertex ${i / 3}) should be 0.0f for 2D text, " +
                    "but was ${mesh.vertices[i]} (text=\"$text\", size=$size)")
        }
    }

    // ─── Property 12: Extruded text mesh Z coordinates span [0, height] ──────────

    /**
     * Property 12: Extruded text mesh Z coordinates span [0, height].
     *
     * For any LinearExtrude(height, Text(...)) where height > 0, the generated mesh
     * vertices shall have Z coordinates in the range [0, height], with at least some
     * vertices at Z=0 and some at Z=height.
     *
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 50)
    fun extrudedTextZCoordinatesSpanFullHeight(
        @ForAll("shortTextStrings") text: String,
        @ForAll @DoubleRange(min = 0.1, max = 100.0) height: Double
    ) {
        val textNode = SceneNode.Text(text = text, size = 10.0)
        val node = SceneNode.LinearExtrude(height = height, child = textNode)
        val mesh = generator.generate(node)

        // Must produce geometry for non-empty text
        assertTrue(mesh.vertices.isNotEmpty(),
            "Extruded text should produce vertices (text=\"$text\", height=$height)")

        val heightF = height.toFloat()
        val tolerance = 0.001f

        var hasZAtZero = false
        var hasZAtHeight = false

        for (i in 2 until mesh.vertices.size step 3) {
            val z = mesh.vertices[i]

            // All Z values must be in [0, height] (within tolerance)
            assertTrue(z >= -tolerance && z <= heightF + tolerance,
                "Z coordinate $z at vertex ${i / 3} is outside [0, $heightF] range " +
                    "(text=\"$text\", height=$height)")

            if (z <= tolerance) hasZAtZero = true
            if (z >= heightF - tolerance) hasZAtHeight = true
        }

        assertTrue(hasZAtZero,
            "Extruded text should have vertices at Z=0 (text=\"$text\", height=$height)")
        assertTrue(hasZAtHeight,
            "Extruded text should have vertices at Z=$heightF (text=\"$text\", height=$height)")
    }

    // ─── Property 8: Empty text produces no geometry ─────────────────────────────

    /**
     * Property 8: Empty text produces no geometry.
     *
     * For any SceneNode.Text with empty text string or size <= 0, the MeshGenerator
     * shall produce zero vertices (no geometry added to the mesh).
     *
     * **Validates: Requirements 1.3, 1.4**
     */
    @Property(tries = 50)
    fun emptyTextProducesNoGeometry(
        @ForAll("emptyTextNodes") node: SceneNode.Text
    ) {
        val mesh = generator.generate(node)

        assertEquals(0, mesh.vertices.size,
            "Empty text or non-positive size should produce no vertices, " +
                "but got ${mesh.vertices.size / 3} vertices " +
                "(text=\"${node.text}\", size=${node.size})")
        assertEquals(0, mesh.normals.size,
            "Empty text or non-positive size should produce no normals")
        assertEquals(0, mesh.colors.size,
            "Empty text or non-positive size should produce no colors")
    }

    // ─── Providers ───────────────────────────────────────────────────────────────

    @Provide
    fun shortTextStrings(): Arbitrary<String> {
        // Generate short non-empty text strings with printable ASCII characters
        return Arbitraries.strings()
            .withCharRange('A', 'Z')
            .ofMinLength(1)
            .ofMaxLength(5)
    }

    @Provide
    fun emptyTextNodes(): Arbitrary<SceneNode.Text> {
        val emptyText = Arbitraries.just(SceneNode.Text(text = "", size = 10.0))
        val zeroSize = Arbitraries.just(SceneNode.Text(text = "Hello", size = 0.0))
        val negativeSize = Arbitraries.doubles().between(-100.0, -0.01).map { size ->
            SceneNode.Text(text = "Test", size = size)
        }

        return Arbitraries.oneOf(emptyText, zeroSize, negativeSize)
    }
}
