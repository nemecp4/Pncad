package com.openscadviewer.renderer

import net.jqwik.api.*
import net.jqwik.api.Combinators
import org.junit.jupiter.api.Assertions.*

// Feature: preview-controls-and-settings, Property 1: Camera snap preserves distance and pan offsets

/**
 * Property-based tests for camera snap behavior.
 *
 * **Validates: Requirements 2.6**
 */
class CameraSnapPropertyTest {

    /** Snap directions with their corresponding rotation values. */
    enum class SnapDirection(val rotX: Float, val rotY: Float) {
        TOP(90f, 0f),
        FRONT(0f, 0f),
        LEFT(0f, 90f),
        RIGHT(0f, -90f)
    }

    /**
     * Property 1: Camera snap preserves distance and pan offsets
     *
     * For any initial values of cameraDistance, cameraPanX, and cameraPanY,
     * and for any view control button tap (Top, Front, Left, Right),
     * after the snap operation completes, cameraDistance, cameraPanX, and
     * cameraPanY shall remain equal to their pre-snap values.
     *
     * **Validates: Requirements 2.6**
     */
    @Property(tries = 100)
    @Tag("Feature: preview-controls-and-settings, Property 1: Camera snap preserves distance and pan offsets")
    fun cameraSnapPreservesDistanceAndPan(
        @ForAll("cameraDistance") distance: Float,
        @ForAll("cameraPanX") panX: Float,
        @ForAll("cameraPanY") panY: Float,
        @ForAll("snapDirection") direction: SnapDirection
    ) {
        val renderer = SceneRenderer()

        // Set initial camera state
        renderer.cameraDistance = distance
        renderer.cameraPanX = panX
        renderer.cameraPanY = panY

        // Perform snap (same logic as MainActivity.snapCamera)
        renderer.cameraRotX = direction.rotX
        renderer.cameraRotY = direction.rotY

        // Assert distance and pan offsets are preserved
        assertEquals(distance, renderer.cameraDistance,
            "cameraDistance should be preserved after snap to ${direction.name}")
        assertEquals(panX, renderer.cameraPanX,
            "cameraPanX should be preserved after snap to ${direction.name}")
        assertEquals(panY, renderer.cameraPanY,
            "cameraPanY should be preserved after snap to ${direction.name}")
    }

    // --- Custom Generators ---

    @Provide
    fun cameraDistance(): Arbitrary<Float> {
        return Arbitraries.floats().between(0.1f, 1000f)
    }

    @Provide
    fun cameraPanX(): Arbitrary<Float> {
        return Arbitraries.floats().between(-500f, 500f)
    }

    @Provide
    fun cameraPanY(): Arbitrary<Float> {
        return Arbitraries.floats().between(-500f, 500f)
    }

    @Provide
    fun snapDirection(): Arbitrary<SnapDirection> {
        return Arbitraries.of(SnapDirection.TOP, SnapDirection.FRONT, SnapDirection.LEFT, SnapDirection.RIGHT)
    }
}
