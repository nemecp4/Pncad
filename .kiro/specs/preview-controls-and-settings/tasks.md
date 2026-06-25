# Implementation Plan: Preview Controls and Settings

## Overview

This plan implements view control overlay buttons, display settings (axes toggle, wireframe toggle), and background color presets for the 3D preview screen. The implementation follows the existing architecture: `PreferenceKeys` object → settings UI → `SharedPreferences` → listener in `MainActivity` → `SceneRenderer` extensions with dedicated drawer classes.

## Tasks

- [x] 1. Create PreferenceKeys and settings infrastructure
  - [x] 1.1 Create PreferenceKeys object with constants and defaults
    - Create `com.openscadviewer.settings.PreferenceKeys` object
    - Define keys: `pref_show_axes`, `pref_show_wireframe`, `pref_background_color`
    - Define defaults: `false`, `false`, `"dark_grey"`
    - Add `mapBackgroundColor(key: String): FloatArray` utility function
    - _Requirements: 6.1, 6.3, 5.2, 5.3, 5.4_

  - [x] 1.2 Create preferences XML and string resources
    - Create `res/xml/preferences.xml` with `SwitchPreferenceCompat` for axes and wireframe, `ListPreference` for background color
    - Add `res/values/arrays.xml` with `bg_color_labels` (Dark Grey, White, Yellow) and `bg_color_values` (dark_grey, white, yellow)
    - Add appropriate string resources for preference titles and summaries
    - _Requirements: 3.1, 4.1, 5.1_

  - [x] 1.3 Create SettingsActivity and SettingsPreferenceFragment
    - Create `com.openscadviewer.settings.SettingsActivity` extending `AppCompatActivity`
    - Create `com.openscadviewer.settings.SettingsPreferenceFragment` extending `PreferenceFragmentCompat`
    - Create `res/layout/activity_settings.xml` with a container FrameLayout
    - Register `SettingsActivity` in `AndroidManifest.xml`
    - Add navigation to SettingsActivity from toolbar menu in MainActivity
    - _Requirements: 3.1, 4.1, 5.1, 5.6, 6.1_

- [x] 2. Implement View Control Overlay
  - [x] 2.1 Create overlay layout and drawable resources
    - Create `res/layout/view_controls_overlay.xml` with vertical LinearLayout containing four ImageButtons (Top, Front, Left, Right)
    - Position layout with `layout_gravity="top|end"` and appropriate margins
    - Create `res/drawable/overlay_background.xml` semi-transparent background shape
    - Add icon drawables or vector assets for each view direction
    - Set `contentDescription` on each button for accessibility
    - _Requirements: 1.1, 1.2, 1.4_

  - [x] 2.2 Include overlay in preview layout and wire click listeners
    - Include `view_controls_overlay.xml` inside the existing preview `FrameLayout` in `MainActivity`
    - Implement `setupViewControls()` method in `MainActivity` with click listeners for each button
    - Implement `snapCamera(rotX: Float, rotY: Float)` that sets `cameraRotX`/`cameraRotY` on `SceneRenderer`, preserves `cameraDistance`/`cameraPanX`/`cameraPanY`, and calls `requestRender()`
    - Ensure overlay is visible only when preview tab is active and mesh is loaded
    - Ensure overlay does not intercept drag gestures on the GLSurfaceView
    - _Requirements: 1.1, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 2.3 Write property test for camera snap preserving distance and pan
    - **Property 1: Camera snap preserves distance and pan offsets**
    - Generate random `Float` for cameraDistance (0.1–1000), cameraPanX (-500–500), cameraPanY (-500–500)
    - Pick a random snap direction (Top, Front, Left, Right)
    - Assert cameraDistance, cameraPanX, and cameraPanY remain unchanged after snap
    - **Validates: Requirements 2.6**

  - [x] 2.4 Write unit tests for snapCamera
    - Test each button sets correct rotX/rotY values (Top: 90/0, Front: 0/0, Left: 0/90, Right: 0/-90)
    - Test that requestRender is called after snap
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 3. Implement AxesDrawer
  - [x] 3.1 Create AxesDrawer class with OpenGL line rendering
    - Create `com.openscadviewer.renderer.AxesDrawer` class
    - Implement `initialize()` to compile a simple position+uniform-color shader and create line VBO with 6 vertices (two per axis)
    - Implement `draw(mvpMatrix: FloatArray, axisLength: Float)` that draws X (red), Y (green), Z (blue) lines from origin
    - Compute axis length as `max(boundingBoxDiagonal * 0.5, 1.0)` for proportional scaling
    - Guard draw with `program != 0` check for safety
    - _Requirements: 3.2, 3.3, 3.4_

  - [x] 3.2 Write property test for axis length scaling
    - **Property 2: Axis length scales proportionally with bounding box**
    - Generate random bounding box with min < max in each axis
    - Compute axis length using the formula
    - Verify result is a monotonically increasing function of the bounding box diagonal
    - **Validates: Requirements 3.4**

- [x] 4. Implement WireframeDrawer
  - [x] 4.1 Create WireframeDrawer class with edge rendering
    - Create `com.openscadviewer.renderer.WireframeDrawer` class
    - Implement `initialize()` to compile a flat-color shader program
    - Implement `setMeshData(vertices: FloatBuffer, vertexCount: Int)` to store mesh reference
    - Implement `draw(mvpMatrix: FloatArray)` that draws mesh edges as `GL_LINES` with `glPolygonOffset` to avoid z-fighting
    - Use contrasting color based on current background (dark wireframe on light bg, light wireframe on dark bg)
    - Guard draw with `program != 0` check for safety
    - _Requirements: 4.2, 4.3_

- [x] 5. Extend SceneRenderer and wire preferences
  - [x] 5.1 Add display setting properties to SceneRenderer
    - Add `@Volatile var showAxes: Boolean = false`
    - Add `@Volatile var showWireframe: Boolean = false`
    - Add `@Volatile var backgroundColorRgba: FloatArray = floatArrayOf(0.18f, 0.18f, 0.18f, 1.0f)`
    - Instantiate `AxesDrawer` and `WireframeDrawer`, call `initialize()` in `onSurfaceCreated`
    - Call `WireframeDrawer.setMeshData(...)` when mesh data is loaded
    - _Requirements: 3.2, 3.3, 4.2, 5.2, 5.3, 5.4_

  - [x] 5.2 Extend onDrawFrame to use new settings
    - Apply `backgroundColorRgba` via `glClearColor` before `glClear` each frame
    - After main mesh draw, conditionally call `AxesDrawer.draw(...)` when `showAxes` is true
    - After main mesh draw, conditionally call `WireframeDrawer.draw(...)` when `showWireframe` is true
    - _Requirements: 3.2, 3.3, 3.5, 4.2, 4.3, 4.4, 5.2, 5.3, 5.4, 5.5_

  - [x] 5.3 Register SharedPreferences listener in MainActivity
    - Implement `SharedPreferences.OnSharedPreferenceChangeListener` in `MainActivity`
    - On `KEY_SHOW_AXES` change: update `sceneRenderer.showAxes` and call `requestRender()`
    - On `KEY_SHOW_WIREFRAME` change: update `sceneRenderer.showWireframe` and call `requestRender()`
    - On `KEY_BACKGROUND_COLOR` change: update `sceneRenderer.backgroundColorRgba` via `mapBackgroundColor()` and call `requestRender()`
    - Register listener in `onResume`, unregister in `onPause`
    - Read and apply all stored preferences on startup before first frame
    - _Requirements: 3.5, 4.4, 5.5, 6.1, 6.2, 6.3_

- [x] 6. Checkpoint - Verify integration
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Property and unit tests for settings persistence
  - [x] 7.1 Write property test for preferences round-trip persistence
    - **Property 3: Preferences round-trip persistence**
    - Generate random valid combinations: showAxes ∈ {true, false}, showWireframe ∈ {true, false}, backgroundColor ∈ {"dark_grey", "white", "yellow"}
    - Write all three to SharedPreferences (use Robolectric or in-memory map)
    - Read back and assert equality
    - **Validates: Requirements 6.1, 5.6**

  - [x] 7.2 Write property test for invalid preference fallback
    - **Property 4: Invalid preference values fall back to defaults**
    - Generate arbitrary strings (excluding valid keys "dark_grey", "white", "yellow")
    - Pass to `mapBackgroundColor`
    - Assert default Dark Grey RGBA (0.18, 0.18, 0.18, 1.0) is returned
    - **Validates: Requirements 6.3**

  - [x] 7.3 Write unit tests for mapBackgroundColor
    - Test "dark_grey" → (0.18, 0.18, 0.18, 1.0)
    - Test "white" → (1.0, 1.0, 1.0, 1.0)
    - Test "yellow" → (1.0, 1.0, 0.5, 1.0)
    - Test unknown string → default Dark Grey
    - _Requirements: 5.2, 5.3, 5.4, 6.3_

  - [x] 7.4 Write unit tests for preference listener
    - Test that changing axes pref updates renderer `showAxes` and triggers render
    - Test that changing wireframe pref updates renderer `showWireframe` and triggers render
    - Test that changing background pref updates renderer `backgroundColorRgba` and triggers render
    - Test that missing preferences use defaults on startup
    - _Requirements: 3.5, 4.4, 5.5, 6.2, 6.3_

- [x] 8. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The project uses `net.jqwik:jqwik:1.8.4` for property-based testing
- All Kotlin code follows existing package conventions under `com.openscadviewer`

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "2.1", "3.1", "4.1"] },
    { "id": 2, "tasks": ["2.2", "5.1"] },
    { "id": 3, "tasks": ["2.3", "2.4", "3.2", "5.2"] },
    { "id": 4, "tasks": ["5.3"] },
    { "id": 5, "tasks": ["7.1", "7.2", "7.3", "7.4"] }
  ]
}
```
