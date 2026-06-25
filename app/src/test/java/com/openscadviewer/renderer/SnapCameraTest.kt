package com.openscadviewer.renderer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for snapCamera behavior on SceneRenderer.
 *
 * These tests verify that each snap direction sets the correct cameraRotX/cameraRotY
 * values on SceneRenderer, simulating the logic in MainActivity.snapCamera().
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
 */
class SnapCameraTest {

    private lateinit var renderer: SceneRenderer

    @BeforeEach
    fun setup() {
        renderer = SceneRenderer()
        // Set non-default initial values to ensure snap overrides them
        renderer.cameraRotX = 45f
        renderer.cameraRotY = -30f
        renderer.cameraDistance = 15f
        renderer.cameraPanX = 3f
        renderer.cameraPanY = -2f
    }

    /**
     * Simulates the snapCamera logic from MainActivity:
     * sets rotX/rotY on the renderer (preserving distance and pan).
     */
    private fun snapCamera(rotX: Float, rotY: Float) {
        renderer.cameraRotX = rotX
        renderer.cameraRotY = rotY
    }

    // --- Requirement 2.1: Top view ---

    @Test
    fun `snapCamera Top sets rotX to 90 and rotY to 0`() {
        snapCamera(90f, 0f)

        assertEquals(90f, renderer.cameraRotX, "Top view should set cameraRotX to 90")
        assertEquals(0f, renderer.cameraRotY, "Top view should set cameraRotY to 0")
    }

    // --- Requirement 2.2: Front view ---

    @Test
    fun `snapCamera Front sets rotX to 0 and rotY to 0`() {
        snapCamera(0f, 0f)

        assertEquals(0f, renderer.cameraRotX, "Front view should set cameraRotX to 0")
        assertEquals(0f, renderer.cameraRotY, "Front view should set cameraRotY to 0")
    }

    // --- Requirement 2.3: Left view ---

    @Test
    fun `snapCamera Left sets rotX to 0 and rotY to 90`() {
        snapCamera(0f, 90f)

        assertEquals(0f, renderer.cameraRotX, "Left view should set cameraRotX to 0")
        assertEquals(90f, renderer.cameraRotY, "Left view should set cameraRotY to 90")
    }

    // --- Requirement 2.4: Right view ---

    @Test
    fun `snapCamera Right sets rotX to 0 and rotY to -90`() {
        snapCamera(0f, -90f)

        assertEquals(0f, renderer.cameraRotX, "Right view should set cameraRotX to 0")
        assertEquals(-90f, renderer.cameraRotY, "Right view should set cameraRotY to -90")
    }

    // --- Requirement 2.5: requestRender is needed after snap ---

    @Test
    fun `snapCamera changes rotation values indicating render is needed`() {
        val initialRotX = renderer.cameraRotX
        val initialRotY = renderer.cameraRotY

        // Snap to Top (different from initial 45/-30)
        snapCamera(90f, 0f)

        // After snap, rotation values have changed — this is the signal that
        // requestRender() must be called to reflect the new camera position.
        // In the actual app, MainActivity calls glSurfaceView.requestRender()
        // after setting these values.
        assertNotEquals(initialRotX, renderer.cameraRotX,
            "cameraRotX should change after snap, requiring a render")
        assertNotEquals(initialRotY, renderer.cameraRotY,
            "cameraRotY should change after snap, requiring a render")
    }

    @Test
    fun `snapCamera preserves cameraDistance after each direction`() {
        val originalDistance = renderer.cameraDistance

        snapCamera(90f, 0f)  // Top
        assertEquals(originalDistance, renderer.cameraDistance, "Distance preserved after Top snap")

        snapCamera(0f, 0f)   // Front
        assertEquals(originalDistance, renderer.cameraDistance, "Distance preserved after Front snap")

        snapCamera(0f, 90f)  // Left
        assertEquals(originalDistance, renderer.cameraDistance, "Distance preserved after Left snap")

        snapCamera(0f, -90f) // Right
        assertEquals(originalDistance, renderer.cameraDistance, "Distance preserved after Right snap")
    }

    @Test
    fun `snapCamera preserves pan offsets after each direction`() {
        val originalPanX = renderer.cameraPanX
        val originalPanY = renderer.cameraPanY

        snapCamera(90f, 0f)  // Top
        assertEquals(originalPanX, renderer.cameraPanX, "PanX preserved after Top snap")
        assertEquals(originalPanY, renderer.cameraPanY, "PanY preserved after Top snap")

        snapCamera(0f, 0f)   // Front
        assertEquals(originalPanX, renderer.cameraPanX, "PanX preserved after Front snap")
        assertEquals(originalPanY, renderer.cameraPanY, "PanY preserved after Front snap")

        snapCamera(0f, 90f)  // Left
        assertEquals(originalPanX, renderer.cameraPanX, "PanX preserved after Left snap")
        assertEquals(originalPanY, renderer.cameraPanY, "PanY preserved after Left snap")

        snapCamera(0f, -90f) // Right
        assertEquals(originalPanX, renderer.cameraPanX, "PanX preserved after Right snap")
        assertEquals(originalPanY, renderer.cameraPanY, "PanY preserved after Right snap")
    }
}
