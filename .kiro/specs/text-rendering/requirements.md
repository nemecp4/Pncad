# Requirements Document

## Introduction

This feature replaces the current placeholder text rendering (TextApprox — a flat rectangle) with proper text-to-geometry conversion that produces 2D polygon outlines from font glyph data. The `text()` module in OpenSCAD produces 2D geometry (outlines of characters), which can then be extruded with `linear_extrude()` to create 3D text.

The implementation leverages existing open-source libraries to simplify font rendering:
- **Android (Kotlin engine):** Uses Android's built-in `Paint.getTextPath()` API to extract vector path outlines from TrueType fonts, then flattens Bezier curves to polygon segments.
- **Desktop/benchmark (Kotlin engine):** Uses Java AWT's `Font.createGlyphVector()` and `PathIterator` to extract glyph outlines on JVM desktop.
- **CGAL engine (C++):** Uses the [ttf2mesh](https://github.com/fetisov/ttf2mesh) library (MIT-licensed, single-file C) to convert TTF glyphs directly to 2D polygon meshes.

A bundled TrueType font (Liberation Sans or similar) is included in the app assets so that text renders consistently even when the user-specified font is unavailable.

## Glossary

- **Text_Module**: The OpenSCAD `text()` built-in module that produces 2D character outlines from a string.
- **Glyph**: The polygon outline data for a single character in a font.
- **Font_Provider**: The platform-specific component that extracts glyph polygon outlines from a TrueType font file.
- **Glyph_Cache**: An in-memory cache holding flattened glyph polygon outlines to avoid repeated path extraction.
- **Kotlin_Engine**: The pure-Kotlin mesh generator (`MeshGenerator`) that produces triangle meshes from SceneNodes.
- **CGAL_Engine**: The C++ engine that receives JSON scene graphs and produces Nef polyhedra.
- **Scene_Serializer**: The component that converts SceneNode trees to JSON for the CGAL engine.
- **Parser**: The OpenSCADParser that converts source text into a SceneNode tree.
- **Text_Node**: The SceneNode variant representing a parsed `text()` call with all its parameters.
- **ttf2mesh**: A standalone MIT-licensed C library for converting TrueType font glyphs to 2D/3D mesh objects without rasterization.
- **Baseline**: The horizontal line on which characters rest; descenders extend below this line.
- **Advance_Width**: The horizontal distance to move after rendering a glyph before rendering the next character.
- **Path_Flattener**: The component that converts Bezier curves from font outlines into straight-line polygon segments with configurable tolerance.

## Requirements

### Requirement 1: Parse text() Module Parameters

**User Story:** As a user writing OpenSCAD code, I want the parser to correctly handle all standard `text()` parameters, so that my text renders with the specified size, spacing, and alignment.

#### Acceptance Criteria

1. WHEN the Parser encounters a `text()` call with a string argument, THE Parser SHALL produce a Text_Node containing the text content, size, font name, horizontal alignment, vertical alignment, spacing, and direction parameters.
2. THE Parser SHALL use the following default values when parameters are not specified: size=10, font="Liberation Sans", halign="left", valign="baseline", spacing=1.0, direction="ltr".
3. WHEN the text parameter is an empty string, THE Parser SHALL produce a Text_Node with empty text content that generates no geometry.
4. WHEN the size parameter is zero or negative, THE Parser SHALL produce a Text_Node that generates no geometry.
5. WHEN the text parameter is a variable reference, THE Parser SHALL attempt to resolve the variable from the current scope and use its string value.

### Requirement 2: Font Loading via Platform Libraries

**User Story:** As a user, I want the application to render text using real TrueType fonts via platform-native libraries, so that text appears with correct glyph shapes matching OpenSCAD behavior.

#### Acceptance Criteria

1. THE Font_Provider SHALL load glyph outline data from TrueType font files using the platform's native font rendering API (Android `Paint.getTextPath()` on Android, Java AWT `Font.createGlyphVector()` on desktop JVM).
2. THE application SHALL bundle a default TrueType font file (Liberation Sans) in the app assets for use when the user-specified font is not available on the system.
3. WHEN the user specifies a font name in the `text()` call, THE Font_Provider SHALL attempt to load that font from the system. IF the specified font is not available, THEN THE Font_Provider SHALL fall back to the bundled default font.
4. THE Font_Provider SHALL supply polygon outline data for all printable ASCII characters (codes 32-126) from the loaded font.
5. WHEN a character is not found in the loaded font, THE Font_Provider SHALL substitute a rectangular placeholder glyph with dimensions based on the font's average character width and ascent height.

### Requirement 3: Glyph Outline Extraction and Flattening

**User Story:** As a developer, I want Bezier curves from font outlines to be flattened to polygon segments, so that the engines can process text as standard polygon geometry.

#### Acceptance Criteria

1. THE Path_Flattener SHALL convert quadratic and cubic Bezier curve segments from font path data into sequences of straight line segments.
2. THE Path_Flattener SHALL use an adaptive subdivision strategy that produces line segments deviating no more than 0.1 units (at size=10) from the original curve.
3. THE Path_Flattener SHALL produce closed polygon contours for each glyph outline, including separate inner contours for characters with holes (e.g., "O", "D", "A", "B").
4. THE Glyph_Cache SHALL store flattened polygon outlines keyed by (font, character, size) so that subsequent rendering of the same character reuses cached polygon data.
5. THE Font_Provider SHALL supply Advance_Width values for each glyph to enable correct inter-character spacing.

### Requirement 4: Text Layout and Positioning

**User Story:** As a user, I want `text("Hello")` to produce correctly spaced and aligned 2D polygon outlines, so that the characters form readable text geometry.

#### Acceptance Criteria

1. WHEN a Text_Node is processed, THE Kotlin_Engine SHALL position each character glyph sequentially along the X axis with horizontal offset computed as the sum of preceding characters' Advance_Width values multiplied by the spacing parameter.
2. WHEN halign is "left", THE Kotlin_Engine SHALL position the text starting at X=0. WHEN halign is "center", THE Kotlin_Engine SHALL offset the text by negative half of the total text width. WHEN halign is "right", THE Kotlin_Engine SHALL offset the text by negative total text width.
3. WHEN valign is "baseline", THE Kotlin_Engine SHALL position glyphs with the Baseline at Y=0. WHEN valign is "bottom", THE Kotlin_Engine SHALL offset the text so the lowest descender is at Y=0. WHEN valign is "top", THE Kotlin_Engine SHALL offset the text so the highest ascender is at Y=0. WHEN valign is "center", THE Kotlin_Engine SHALL offset the text so the vertical midpoint between ascender and descender is at Y=0.
4. WHEN direction is "ltr" (left-to-right), THE Kotlin_Engine SHALL lay out characters from left to right with increasing X positions. WHEN direction is "rtl" (right-to-left), THE Kotlin_Engine SHALL lay out characters from right to left with decreasing X positions.
5. WHEN the spacing parameter is specified, THE Kotlin_Engine SHALL multiply each character's Advance_Width by the spacing value when computing horizontal positions.

### Requirement 5: Kotlin Engine Mesh Generation for Text

**User Story:** As a user previewing my model in the Kotlin engine, I want text to render as visible 2D geometry in the viewport, so that I can see proper character shapes where text appears.

#### Acceptance Criteria

1. WHEN the Kotlin_Engine encounters a Text_Node that is not a child of LinearExtrude, THE Kotlin_Engine SHALL generate a flat 2D triangulated mesh from the glyph polygon outlines at Z=0.
2. WHEN the Kotlin_Engine encounters a LinearExtrude node containing a Text_Node child, THE Kotlin_Engine SHALL extrude the text polygon outlines along the Z axis by the specified height, producing a closed 3D mesh.
3. THE Kotlin_Engine SHALL triangulate multi-contour polygons (characters with holes) using ear-clipping or equivalent triangulation that correctly subtracts inner contours from outer contours.
4. THE Kotlin_Engine SHALL generate correct face normals for all produced triangles: (0, 0, 1) for flat 2D text, and proper outward-facing normals for extruded 3D text faces (top, bottom, and sides).

### Requirement 6: CGAL Engine Text Support via ttf2mesh

**User Story:** As a user rendering with the CGAL engine, I want text to produce correct CSG-compatible geometry, so that I can use Boolean operations on extruded text.

#### Acceptance Criteria

1. THE CGAL_Engine SHALL use the ttf2mesh C library to convert TrueType font glyphs into 2D polygon outlines for text geometry generation.
2. WHEN the Scene_Serializer encounters a Text_Node, THE Scene_Serializer SHALL serialize the text parameters (text content, size, font name, halign, valign, spacing, direction) as a JSON node with type "text" for the CGAL engine to process.
3. THE CGAL_Engine SHALL load the bundled TrueType font file and use ttf2mesh to extract glyph meshes, applying the same layout rules (spacing, alignment) as the Kotlin engine.
4. WHEN the CGAL_Engine processes text inside a linear_extrude operation, THE CGAL_Engine SHALL extrude each glyph polygon to produce a valid Nef polyhedron using the polygon extrusion pipeline already available in scene_builder.cpp.
5. IF ttf2mesh produces degenerate geometry (self-intersecting contours or zero-area polygons) for a glyph, THEN THE CGAL_Engine SHALL skip the degenerate glyph and continue processing remaining characters.

### Requirement 7: SceneNode Integration

**User Story:** As a developer maintaining the engine, I want the text rendering to follow the existing SceneNode pattern, so that transforms, colors, and CSG operations work naturally with text.

#### Acceptance Criteria

1. THE Text_Node SHALL replace the existing TextApprox variant in the SceneNode sealed class, containing fields: text (String), size (Double), font (String), halign (String), valign (String), spacing (Double), direction (String).
2. WHEN a Text_Node is a child of Translate, Rotate, Scale, or Color nodes, THE Kotlin_Engine SHALL apply the parent transformation or color to the generated text geometry identically to how other 2D primitives are handled.
3. WHEN a Text_Node appears inside Union, Difference, or Intersection operations, both engines SHALL process the text geometry as a standard operand in the Boolean operation.
4. THE SceneNodeExtensions.containsGeometry() function SHALL return true for Text_Node when the text content is non-empty.
5. WHEN a Text_Node has empty text content, THE SceneNodeExtensions.containsGeometry() function SHALL return false.

### Requirement 8: Performance and Caching

**User Story:** As a user, I want text rendering to be fast enough for interactive preview, so that editing text parameters provides immediate visual feedback.

#### Acceptance Criteria

1. THE Glyph_Cache SHALL cache flattened polygon outlines per (font, character) pair so that rendering the same character at different sizes requires only a scale operation on cached data.
2. THE Kotlin_Engine SHALL generate mesh geometry for a single text() call with up to 100 characters within 100ms on a mid-range Android device.
3. THE Glyph_Cache SHALL evict least-recently-used entries when cache size exceeds 4MB of polygon data.
4. THE CGAL_Engine SHALL cache ttf2mesh glyph output per (font, character) pair across multiple compute() calls within the same session to avoid repeated font parsing.
