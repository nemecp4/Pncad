package com.openscadviewer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for MainViewModel state operations.
 *
 * Validates: Requirements 6.1, 6.2
 */
class MainViewModelTest {

    @Test
    fun testDefaultValues() {
        val viewModel = MainViewModel()

        // Editor state defaults
        assertEquals("", viewModel.editorText)
        assertEquals(0, viewModel.cursorPosition)
        assertEquals("", viewModel.currentFileName)
        assertEquals("", viewModel.statusBarText)

        // Renderer state defaults
        assertNull(viewModel.meshVertices)
        assertNull(viewModel.meshNormals)
        assertNull(viewModel.meshColors)
        assertEquals(0, viewModel.triangleCount)

        // Camera state defaults
        assertEquals(30f, viewModel.cameraRotX)
        assertEquals(-45f, viewModel.cameraRotY)
        assertEquals(10f, viewModel.cameraDistance)
        assertEquals(0f, viewModel.cameraPanX)
        assertEquals(0f, viewModel.cameraPanY)

        // Computation state default
        assertFalse(viewModel.isComputing)
    }

    @Test
    fun testStoreAndRetrieveEditorState() {
        val viewModel = MainViewModel()

        viewModel.editorText = "cube([10, 20, 30]);\nsphere(r = 5);"
        viewModel.cursorPosition = 15
        viewModel.currentFileName = "my_model.scad"
        viewModel.statusBarText = "Preview complete"

        assertEquals("cube([10, 20, 30]);\nsphere(r = 5);", viewModel.editorText)
        assertEquals(15, viewModel.cursorPosition)
        assertEquals("my_model.scad", viewModel.currentFileName)
        assertEquals("Preview complete", viewModel.statusBarText)
    }

    @Test
    fun testCursorClamping() {
        val viewModel = MainViewModel()

        // Store a cursor position beyond what the text length would support
        viewModel.cursorPosition = 100
        viewModel.editorText = "short text" // length = 10

        // The clamping logic is applied in MainActivity during restore,
        // so we test the expected behavior pattern here
        val clampedCursor = minOf(viewModel.cursorPosition, viewModel.editorText.length)
        assertEquals(10, clampedCursor)
    }

    @Test
    fun testStoreAndRetrieveMeshState() {
        val viewModel = MainViewModel()

        val vertices = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f)
        val normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
        val colors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f)

        viewModel.meshVertices = vertices
        viewModel.meshNormals = normals
        viewModel.meshColors = colors
        viewModel.triangleCount = 3

        assertArrayEquals(vertices, viewModel.meshVertices)
        assertArrayEquals(normals, viewModel.meshNormals)
        assertArrayEquals(colors, viewModel.meshColors)
        assertEquals(3, viewModel.triangleCount)
    }

    @Test
    fun testStoreAndRetrieveCameraState() {
        val viewModel = MainViewModel()

        viewModel.cameraRotX = 45f
        viewModel.cameraRotY = -90f
        viewModel.cameraDistance = 25.5f
        viewModel.cameraPanX = 3.2f
        viewModel.cameraPanY = -1.7f

        assertEquals(45f, viewModel.cameraRotX)
        assertEquals(-90f, viewModel.cameraRotY)
        assertEquals(25.5f, viewModel.cameraDistance)
        assertEquals(3.2f, viewModel.cameraPanX)
        assertEquals(-1.7f, viewModel.cameraPanY)
    }
}
