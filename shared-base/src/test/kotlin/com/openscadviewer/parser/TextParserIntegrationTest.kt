package com.openscadviewer.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Integration tests for text() parsing end-to-end.
 * Validates: Requirements 1.1, 1.2, 1.3
 */
class TextParserIntegrationTest {

    private val parser = OpenSCADParser()

    /**
     * Parse text("Hello", size=20, halign="center") and verify it produces
     * SceneNode.Text with correct parameters.
     * Validates: Requirement 1.1
     */
    @Test
    fun `parse text with explicit parameters produces correct SceneNode Text`() {
        val result = parser.parse("""text("Hello", size=20, halign="center");""")

        // parse() returns a Group wrapping top-level statements
        assertInstanceOf(SceneNode.Group::class.java, result)
        val group = result as SceneNode.Group
        assertEquals(1, group.children.size, "Expected exactly one child node")

        val textNode = group.children[0]
        assertInstanceOf(SceneNode.Text::class.java, textNode)
        val text = textNode as SceneNode.Text

        assertEquals("Hello", text.text)
        assertEquals(20.0, text.size)
        assertEquals("center", text.halign)
        // Verify defaults for unspecified parameters
        assertEquals("Liberation Sans", text.font)
        assertEquals("baseline", text.valign)
        assertEquals(1.0, text.spacing)
        assertEquals("ltr", text.direction)
    }

    /**
     * Parse linear_extrude(5) text("ABC") and verify it produces
     * SceneNode.LinearExtrude wrapping SceneNode.Text.
     * Validates: Requirement 1.1, 1.2
     */
    @Test
    fun `parse linear_extrude wrapping text produces correct tree`() {
        val result = parser.parse("""linear_extrude(5) text("ABC");""")

        assertInstanceOf(SceneNode.Group::class.java, result)
        val group = result as SceneNode.Group
        assertEquals(1, group.children.size, "Expected exactly one child node")

        val extrudeNode = group.children[0]
        assertInstanceOf(SceneNode.LinearExtrude::class.java, extrudeNode)
        val extrude = extrudeNode as SceneNode.LinearExtrude

        assertEquals(5.0, extrude.height)

        val child = extrude.child
        assertInstanceOf(SceneNode.Text::class.java, child)
        val text = child as SceneNode.Text

        assertEquals("ABC", text.text)
        // All other params should be defaults
        assertEquals(10.0, text.size)
        assertEquals("Liberation Sans", text.font)
        assertEquals("left", text.halign)
        assertEquals("baseline", text.valign)
        assertEquals(1.0, text.spacing)
        assertEquals("ltr", text.direction)
    }

    /**
     * Parse text("") and verify it produces SceneNode.Text with empty content.
     * Validates: Requirement 1.3
     */
    @Test
    fun `parse text with empty string produces Text with empty content`() {
        val result = parser.parse("""text("");""")

        assertInstanceOf(SceneNode.Group::class.java, result)
        val group = result as SceneNode.Group
        assertEquals(1, group.children.size, "Expected exactly one child node")

        val textNode = group.children[0]
        assertInstanceOf(SceneNode.Text::class.java, textNode)
        val text = textNode as SceneNode.Text

        assertEquals("", text.text)
        // Defaults
        assertEquals(10.0, text.size)
        assertEquals("Liberation Sans", text.font)
        assertEquals("left", text.halign)
        assertEquals("baseline", text.valign)
        assertEquals(1.0, text.spacing)
        assertEquals("ltr", text.direction)
    }
}
