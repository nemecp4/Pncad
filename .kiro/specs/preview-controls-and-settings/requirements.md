# Requirements Document

## Introduction

This feature adds view control icons, display settings, and background color presets to the 3D preview screen of the OpenSCAD Viewer Android app. The view controls provide quick orthographic camera snapping. The display settings allow toggling coordinate axes and wireframe overlay. Background color presets let users choose from predefined colors for the preview area.

## Glossary

- **Preview_Screen**: The 3D preview tab within the ViewFlipper that displays the rendered OpenGL scene via GLSurfaceView.
- **View_Control_Overlay**: A set of small icon buttons overlaid on the Preview_Screen for camera manipulation.
- **SceneRenderer**: The OpenGL ES 2.0 renderer class responsible for drawing the 3D mesh and scene elements.
- **Camera**: The virtual viewpoint defined by rotation angles (cameraRotX, cameraRotY) and distance (cameraDistance) in SceneRenderer.
- **Orthographic_Position**: A predefined camera rotation that shows the model from one of four standard directions (top, left, right, front).
- **Coordinate_Axes**: Three colored lines drawn at the scene origin indicating X (red), Y (green), and Z (blue) directions.
- **Wireframe_Overlay**: An additional rendering pass that draws the mesh edges as lines on top of the shaded triangles.
- **Background_Color_Preset**: A predefined RGBA color value used as the GLSurfaceView clear color.
- **Settings_Screen**: The Android preferences/settings activity or fragment accessible from the toolbar menu.

## Requirements

### Requirement 1: View Control Overlay Display

**User Story:** As a user, I want to see camera control icons on the 3D preview, so that I can quickly snap to standard viewing angles without manual rotation.

#### Acceptance Criteria

1. WHILE the Preview_Screen is visible and a mesh is loaded, THE View_Control_Overlay SHALL display four icon buttons labeled Top, Front, Left, and Right.
2. THE View_Control_Overlay SHALL position the icon buttons in a non-obstructing area of the Preview_Screen (e.g., top-left or top-right corner).
3. THE View_Control_Overlay SHALL remain visible during touch-based camera rotation and zooming without intercepting drag gestures on the preview area.
4. THE View_Control_Overlay SHALL use Material Design IconButton styling with a semi-transparent background for visibility against varying scene content.

### Requirement 2: Camera Snap to Orthographic Positions

**User Story:** As a user, I want to tap a view control icon and have the camera instantly move to a standard orthographic angle, so that I can inspect specific sides of the model.

#### Acceptance Criteria

1. WHEN the user taps the Top icon, THE Camera SHALL set cameraRotX to 90 degrees and cameraRotY to 0 degrees to show the model from directly above.
2. WHEN the user taps the Front icon, THE Camera SHALL set cameraRotX to 0 degrees and cameraRotY to 0 degrees to show the model from the front.
3. WHEN the user taps the Left icon, THE Camera SHALL set cameraRotX to 0 degrees and cameraRotY to 90 degrees to show the model from the left side.
4. WHEN the user taps the Right icon, THE Camera SHALL set cameraRotX to 0 degrees and cameraRotY to -90 degrees to show the model from the right side.
5. WHEN the user taps any view control icon, THE SceneRenderer SHALL request a frame redraw to reflect the new camera position immediately.
6. WHEN the user taps any view control icon, THE Camera SHALL preserve the current cameraDistance and pan offsets (cameraPanX, cameraPanY).

### Requirement 3: Coordinate Axes Toggle

**User Story:** As a user, I want to toggle coordinate axes display in settings, so that I can see or hide orientation reference lines in the 3D preview.

#### Acceptance Criteria

1. THE Settings_Screen SHALL provide a toggle switch labeled "Show Coordinate Axes" with a default value of disabled (axes hidden).
2. WHEN the "Show Coordinate Axes" setting is enabled, THE SceneRenderer SHALL draw three axis lines at the scene origin: X-axis in red (1.0, 0.0, 0.0), Y-axis in green (0.0, 1.0, 0.0), and Z-axis in blue (0.0, 0.0, 1.0).
3. WHEN the "Show Coordinate Axes" setting is disabled, THE SceneRenderer SHALL not draw any axis lines.
4. THE Coordinate_Axes SHALL extend a fixed length proportional to the current model bounding box size so they remain visible but do not dominate the scene.
5. WHEN the user changes the "Show Coordinate Axes" setting, THE SceneRenderer SHALL apply the change on the next frame without requiring a re-render of the mesh.

### Requirement 4: Wireframe Overlay Toggle

**User Story:** As a user, I want to toggle wireframe display in settings, so that I can inspect the mesh topology overlaid on the shaded model.

#### Acceptance Criteria

1. THE Settings_Screen SHALL provide a toggle switch labeled "Show Wireframe" with a default value of disabled (wireframe hidden).
2. WHEN the "Show Wireframe" setting is enabled, THE SceneRenderer SHALL draw the mesh edges as lines on top of the shaded triangles using a contrasting color.
3. WHEN the "Show Wireframe" setting is disabled, THE SceneRenderer SHALL render only the shaded triangles without edge lines.
4. WHEN the user changes the "Show Wireframe" setting, THE SceneRenderer SHALL apply the change on the next frame without requiring a re-render of the mesh.

### Requirement 5: Background Color Presets

**User Story:** As a user, I want to choose a background color for the 3D preview from presets, so that I can select the visual context that works best for my model.

#### Acceptance Criteria

1. THE Settings_Screen SHALL provide a "Background Color" selection with three preset options: Dark Grey (default), White, and Yellow.
2. WHEN the user selects "Dark Grey", THE SceneRenderer SHALL set the clear color to RGBA (0.18, 0.18, 0.18, 1.0).
3. WHEN the user selects "White", THE SceneRenderer SHALL set the clear color to RGBA (1.0, 1.0, 1.0, 1.0).
4. WHEN the user selects "Yellow", THE SceneRenderer SHALL set the clear color to RGBA (1.0, 1.0, 0.5, 1.0).
5. WHEN the user changes the background color setting, THE SceneRenderer SHALL apply the new clear color on the next frame without requiring a re-render of the mesh.
6. THE Settings_Screen SHALL persist the selected background color preference across app restarts using SharedPreferences.

### Requirement 6: Settings Persistence

**User Story:** As a user, I want my display preferences to be saved, so that they are restored when I reopen the app.

#### Acceptance Criteria

1. THE Settings_Screen SHALL persist the "Show Coordinate Axes", "Show Wireframe", and "Background Color" preferences using SharedPreferences.
2. WHEN the app starts, THE SceneRenderer SHALL read stored preferences and apply saved values for axes visibility, wireframe visibility, and background color before the first frame is drawn.
3. IF a stored preference value is missing or corrupted, THEN THE Settings_Screen SHALL use the default values: axes disabled, wireframe disabled, background Dark Grey.
