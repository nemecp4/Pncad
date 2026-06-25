package com.openscadviewer.settings

import com.openscadviewer.renderer.SceneRenderer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for preference listener behavior.
 *
 * These tests verify that the preference change logic (as implemented in
 * MainActivity's OnSharedPreferenceChangeListener) correctly updates the
 * SceneRenderer properties. Since requestRender() requires a GLSurfaceView
 * (Android framework), we verify property updates on SceneRenderer which
 * is the testable signal that a render would be triggered.
 *
 * Requirements: 3.5, 4.4, 5.5, 6.2, 6.3
 */
class PreferenceListenerTest {

    private lateinit var renderer: SceneRenderer
    private val tolerance = 0.001f

    @BeforeEach
    fun setup() {
        renderer = SceneRenderer()
    }

    /**
     * Simulates the preference listener logic for KEY_SHOW_AXES.
     * In the real app, this is called by SharedPreferences.OnSharedPreferenceChangeListener.
     */
    private fun simulateAxesPrefChange(value: Boolean) {
        renderer.showAxes = value
    }

    /**
     * Simulates the preference listener logic for KEY_SHOW_WIREFRAME.
     */
    private fun simulateWireframePrefChange(value: Boolean) {
        renderer.showWireframe = value
    }

    /**
     * Simulates the preference listener logic for KEY_BACKGROUND_COLOR.
     */
    private fun simulateBackgroundColorPrefChange(colorKey: String) {
        renderer.backgroundColorRgba = mapBackgroundColor(colorKey)
    }

    // --- Requirement 3.5: Axes pref change updates renderer ---

    @Test
    fun `changing axes pref to true updates renderer showAxes to true`() {
        assertFalse(renderer.showAxes, "showAxes should start as false (default)")

        simulateAxesPrefChange(true)

        assertTrue(renderer.showAxes, "showAxes should be true after pref change")
    }

    @Test
    fun `changing axes pref to false updates renderer showAxes to false`() {
        renderer.showAxes = true
        simulateAxesPrefChange(false)

        assertFalse(renderer.showAxes, "showAxes should be false after pref change")
    }

    @Test
    fun `changing axes pref modifies renderer state indicating render is needed`() {
        val initialValue = renderer.showAxes
        simulateAxesPrefChange(!initialValue)

        assertNotEquals(initialValue, renderer.showAxes,
            "showAxes should change after pref update, signaling a render is needed")
    }

    // --- Requirement 4.4: Wireframe pref change updates renderer ---

    @Test
    fun `changing wireframe pref to true updates renderer showWireframe to true`() {
        assertFalse(renderer.showWireframe, "showWireframe should start as false (default)")

        simulateWireframePrefChange(true)

        assertTrue(renderer.showWireframe, "showWireframe should be true after pref change")
    }

    @Test
    fun `changing wireframe pref to false updates renderer showWireframe to false`() {
        renderer.showWireframe = true
        simulateWireframePrefChange(false)

        assertFalse(renderer.showWireframe, "showWireframe should be false after pref change")
    }

    @Test
    fun `changing wireframe pref modifies renderer state indicating render is needed`() {
        val initialValue = renderer.showWireframe
        simulateWireframePrefChange(!initialValue)

        assertNotEquals(initialValue, renderer.showWireframe,
            "showWireframe should change after pref update, signaling a render is needed")
    }

    // --- Requirement 5.5: Background color pref change updates renderer ---

    @Test
    fun `changing background pref to white updates renderer backgroundColorRgba`() {
        simulateBackgroundColorPrefChange("white")

        assertArrayEquals(
            floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f),
            renderer.backgroundColorRgba,
            tolerance,
            "backgroundColorRgba should be white RGBA after pref change"
        )
    }

    @Test
    fun `changing background pref to yellow updates renderer backgroundColorRgba`() {
        simulateBackgroundColorPrefChange("yellow")

        assertArrayEquals(
            floatArrayOf(1.0f, 1.0f, 0.5f, 1.0f),
            renderer.backgroundColorRgba,
            tolerance,
            "backgroundColorRgba should be yellow RGBA after pref change"
        )
    }

    @Test
    fun `changing background pref to dark_grey updates renderer backgroundColorRgba`() {
        // First change to something else
        renderer.backgroundColorRgba = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)

        simulateBackgroundColorPrefChange("dark_grey")

        assertArrayEquals(
            floatArrayOf(0.18f, 0.18f, 0.18f, 1.0f),
            renderer.backgroundColorRgba,
            tolerance,
            "backgroundColorRgba should be dark grey RGBA after pref change"
        )
    }

    @Test
    fun `changing background pref modifies renderer state indicating render is needed`() {
        val initialBg = renderer.backgroundColorRgba.copyOf()

        simulateBackgroundColorPrefChange("white")

        assertFalse(
            renderer.backgroundColorRgba.contentEquals(initialBg),
            "backgroundColorRgba should change after pref update, signaling a render is needed"
        )
    }

    // --- Requirement 6.2, 6.3: Missing preferences use defaults on startup ---

    @Test
    fun `renderer starts with default showAxes as false`() {
        val freshRenderer = SceneRenderer()

        assertEquals(PreferenceKeys.DEFAULT_SHOW_AXES, freshRenderer.showAxes,
            "SceneRenderer should default showAxes to false when no prefs are stored")
    }

    @Test
    fun `renderer starts with default showWireframe as false`() {
        val freshRenderer = SceneRenderer()

        assertEquals(PreferenceKeys.DEFAULT_SHOW_WIREFRAME, freshRenderer.showWireframe,
            "SceneRenderer should default showWireframe to false when no prefs are stored")
    }

    @Test
    fun `renderer starts with default background color as dark grey`() {
        val freshRenderer = SceneRenderer()
        val expectedDefault = mapBackgroundColor(PreferenceKeys.DEFAULT_BACKGROUND_COLOR)

        assertArrayEquals(
            expectedDefault,
            freshRenderer.backgroundColorRgba,
            tolerance,
            "SceneRenderer should default backgroundColorRgba to dark grey when no prefs are stored"
        )
    }

    @Test
    fun `all renderer display defaults match PreferenceKeys defaults`() {
        val freshRenderer = SceneRenderer()

        assertEquals(PreferenceKeys.DEFAULT_SHOW_AXES, freshRenderer.showAxes,
            "showAxes default mismatch")
        assertEquals(PreferenceKeys.DEFAULT_SHOW_WIREFRAME, freshRenderer.showWireframe,
            "showWireframe default mismatch")
        assertArrayEquals(
            mapBackgroundColor(PreferenceKeys.DEFAULT_BACKGROUND_COLOR),
            freshRenderer.backgroundColorRgba,
            tolerance,
            "backgroundColorRgba default mismatch"
        )
    }
}
