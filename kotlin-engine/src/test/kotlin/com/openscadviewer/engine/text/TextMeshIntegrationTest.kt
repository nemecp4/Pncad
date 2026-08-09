package com.openscadviewer.engine.text

import com.openscadviewer.engine.MeshGenerator
import com.openscadviewer.parser.SceneNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration tests for end-to-end text mesh generation.
 * Verifies that the MeshGenerator with AwtFontProvider produces correct geometry
 * for text rendering scenarios.
 *
 * Validates: Requirements 5.1, 5.2
 */
class TextMeshIntegrationTest {

    private lateinit var generator: MeshGenerator

    @BeforeEach
    fun setUp() {
        generator = MeshGenerator()
        // MeshGenerator defaults to AwtFontProvider
    }

    /**
     * Render text("A") with AwtFontProvider → verify non-empty mesh with Z=0.
     * Validates: Requirement 5.1
     */
    @Test
    fun `text A produces non-empty 2D mesh with all Z coordinates at zero`() {
        val node = SceneNode.Text(text = "A", size = 10.0)
        val mesh = generator.generate(node)

        // Mesh should be non-empty (the letter "A" has glyph outlines)
        assertTrue(mesh.vertices.isNotEmpty(),
            "text(\"A\") should produce a non-empty mesh")
        assertTrue(mesh.vertices.size % 3 == 0,
            "Vertices array size should be a multiple of 3 (x,y,z triplets)")

        // All Z coordinates (every 3rd float starting at index 2) should be 0.0f
        for (i in 2 until mesh.vertices.size step 3) {
            assertEquals(0.0f, mesh.vertices[i],
                "Vertex ${i / 3}: Z coordinate should be 0.0f for 2D text, but was ${mesh.vertices[i]}")
        }

        // Normals should also be present and matching vertex count
        assertEquals(mesh.vertices.size, mesh.normals.size,
            "Normals array size should match vertices array size")
    }

    /**
     * Render linear_extrude(10) text("X") → verify 3D mesh with Z in [0, 10].
     * Validates: Requirement 5.2
     */
    @Test
    fun `linear_extrude text X produces 3D mesh with Z in 0 to 10`() {
        val textNode = SceneNode.Text(text = "X", size = 10.0)
        val node = SceneNode.LinearExtrude(height = 10.0, child = textNode)
        val mesh = generator.generate(node)

        // Mesh should be non-empty
        assertTrue(mesh.vertices.isNotEmpty(),
            "linear_extrude(10) text(\"X\") should produce a non-empty mesh")
        assertTrue(mesh.vertices.size % 3 == 0,
            "Vertices array size should be a multiple of 3 (x,y,z triplets)")

        val tolerance = 0.001f
        var hasZAtZero = false
        var hasZAtTen = false

        for (i in 2 until mesh.vertices.size step 3) {
            val z = mesh.vertices[i]

            // All Z values must be in [0, 10] (within tolerance)
            assertTrue(z >= -tolerance && z <= 10.0f + tolerance,
                "Vertex ${i / 3}: Z coordinate $z is outside [0, 10] range")

            if (z <= tolerance) hasZAtZero = true
            if (z >= 10.0f - tolerance) hasZAtTen = true
        }

        assertTrue(hasZAtZero,
            "Extruded text should have vertices at Z=0")
        assertTrue(hasZAtTen,
            "Extruded text should have vertices at Z=10")
    }

    /**
     * Render text("") → verify empty mesh (zero vertices).
     * Validates: Requirements 5.1 (empty text produces no geometry)
     */
    @Test
    fun `empty text produces empty mesh with zero vertices`() {
        val node = SceneNode.Text(text = "", size = 10.0)
        val mesh = generator.generate(node)

        assertEquals(0, mesh.vertices.size,
            "text(\"\") should produce zero vertices")
        assertEquals(0, mesh.normals.size,
            "text(\"\") should produce zero normals")
        assertEquals(0, mesh.colors.size,
            "text(\"\") should produce zero colors")
    }
}
