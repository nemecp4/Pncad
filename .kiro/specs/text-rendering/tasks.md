# Implementation Plan: Text Rendering

## Overview

This plan implements proper text-to-geometry conversion for the `text()` module in OpenSCAD, replacing the current `TextApprox` placeholder rectangle with real glyph polygon outlines. The implementation spans four modules: `shared-base` (data model, interfaces, layout logic), `kotlin-engine` (mesh generation with platform font APIs), `cgal-engine` (serialization for C++), and the C++ native code (ttf2mesh integration). A bundled Liberation Sans font ensures consistent rendering across devices.

## Tasks

- [x] 1. SceneNode and parser updates (shared-base)
  - [x] 1.1 Replace TextApprox with SceneNode.Text
    - In `shared-base/src/main/kotlin/com/openscadviewer/parser/SceneNode.kt`, replace the `TextApprox` data class with a new `Text` data class containing fields: text (String), size (Double, default 10.0), font (String, default "Liberation Sans"), halign (String, default "left"), valign (String, default "baseline"), spacing (Double, default 1.0), direction (String, default "ltr")
    - Update `SceneNodeExtensions.kt` to handle `SceneNode.Text` — return true for `containsGeometry()` when text is non-empty and size > 0
    - _Requirements: 7.1, 7.4, 7.5_

  - [x] 1.2 Update OpenSCADParser.parseText() to produce SceneNode.Text
    - In `shared-base/src/main/kotlin/com/openscadviewer/parser/OpenSCADParser.kt`, update `parseText()` to extract all parameters (size, font, halign, valign, spacing, direction) and return `SceneNode.Text` instead of `SceneNode.TextApprox`
    - Keep existing logic for resolving text content from string literals and variable references
    - Use defaults: size=10.0, font="Liberation Sans", halign="left", valign="baseline", spacing=1.0, direction="ltr"
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 1.3 Fix all compilation errors from TextApprox removal
    - Update `kotlin-engine/src/main/kotlin/com/openscadviewer/engine/MeshGenerator.kt` — replace `is SceneNode.TextApprox` branches with `is SceneNode.Text` (temporarily delegate to placeholder square based on text width/height like before)
    - Update `kotlin-engine/src/main/kotlin/com/openscadviewer/engine/KotlinComputeEngine.kt` — handle `SceneNode.Text` in node counting
    - Update `cgal-engine/src/main/kotlin/com/openscadviewer/engine/SceneSerializer.kt` — replace `TextApprox` handling with `SceneNode.Text` serialization
    - Update `app/src/main/java/com/openscadviewer/renderer/MeshGenerator.kt` — replace `TextApprox` branch
    - Update `benchmark/src/test/kotlin/` — replace all `TextApprox` references
    - _Requirements: 7.1_

  - [x] 1.4 Write property test for containsGeometry with Text nodes (Property 10)
    - Create `shared-base/src/test/kotlin/com/openscadviewer/parser/TextSceneNodePropertyTest.kt`
    - **Property 10: containsGeometry() correctness for Text nodes**
    - Generate random SceneNode.Text with varying text content (empty/non-empty) and size (positive/zero/negative)
    - Verify: non-empty text with positive size → true; empty text or non-positive size → false
    - **Validates: Requirements 7.4, 7.5**

- [x] 2. GlyphOutline data model and FontProvider interface (shared-base)
  - [x] 2.1 Create GlyphOutline and FontMetrics data classes
    - Create `shared-base/src/main/kotlin/com/openscadviewer/engine/text/GlyphOutline.kt`
    - Define `GlyphOutline(contours: List<List<Pair<Float, Float>>>, advanceWidth: Float, ascent: Float, descent: Float)`
    - Add `scaledTo(size: Float): GlyphOutline` method that scales all points and metrics from reference size
    - Add `estimatedSizeBytes(): Long` for cache sizing
    - Define `FontMetrics(ascent: Float, descent: Float, lineHeight: Float)`
    - _Requirements: 3.1, 3.3, 3.5_

  - [x] 2.2 Create FontProvider interface
    - Create `shared-base/src/main/kotlin/com/openscadviewer/engine/text/FontProvider.kt`
    - Define interface with `getGlyphOutline(char: Char, fontName: String, size: Float): GlyphOutline?`
    - Add `getFontMetrics(fontName: String, size: Float): FontMetrics`
    - Add `isFontAvailable(fontName: String): Boolean`
    - _Requirements: 2.1, 2.4, 2.5_

  - [x] 2.3 Create GlyphCache
    - Create `shared-base/src/main/kotlin/com/openscadviewer/engine/text/GlyphCache.kt`
    - Implement thread-safe LRU cache using `ConcurrentHashMap` with max size 4MB
    - Define `GlyphCacheKey(fontName: String, char: Char)`
    - Implement `get(key)`, `put(key, outline)` with LRU eviction
    - _Requirements: 3.4, 8.1, 8.3_

  - [x] 2.4 Write property test for GlyphCache consistency (Property 7)
    - Create `shared-base/src/test/kotlin/com/openscadviewer/engine/text/GlyphCachePropertyTest.kt`
    - **Property 7: Glyph cache returns identical data on cache hit**
    - Generate random sequences of put/get operations with various keys and GlyphOutline values
    - Verify: get after put with same key returns identical data; eviction only happens when size limit exceeded
    - **Validates: Requirements 3.4, 8.1**

- [x] 3. TextLayoutEngine (shared-base)
  - [x] 3.1 Implement TextLayoutEngine
    - Create `shared-base/src/main/kotlin/com/openscadviewer/engine/text/TextLayoutEngine.kt`
    - Implement `layout(text, size, halign, valign, spacing, direction, fontProvider, fontName): List<PositionedGlyph>`
    - Define `PositionedGlyph(char: Char, x: Float, y: Float, outline: GlyphOutline)`
    - Implement LTR/RTL character positioning using advance widths × spacing
    - Implement halign offsets: left=0, center=-totalWidth/2, right=-totalWidth
    - Implement valign offsets: baseline=0, bottom=-descent, top=-ascent, center=-(ascent+descent)/2
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 3.2 Write property test for monotonic LTR positions (Property 1)
    - Create `shared-base/src/test/kotlin/com/openscadviewer/engine/text/TextLayoutPropertyTest.kt`
    - **Property 1: Text layout positions are monotonically increasing (LTR)**
    - Generate random ASCII strings (1-50 chars) with spacing > 0, direction="ltr"
    - Use a mock FontProvider with random positive advance widths
    - Verify X positions are strictly non-decreasing
    - **Validates: Requirements 4.1, 4.4**

  - [x] 3.3 Write property test for total width calculation (Property 2)
    - Add to `TextLayoutPropertyTest.kt`
    - **Property 2: Total text width equals sum of advance widths times spacing**
    - Generate random text with mock FontProvider (known advance widths)
    - Verify total width matches expected sum
    - **Validates: Requirements 4.1, 4.5**

  - [x] 3.4 Write property test for alignment offsets (Property 3)
    - Add to `TextLayoutPropertyTest.kt`
    - **Property 3: Horizontal alignment offsets are consistent**
    - Generate random text, test each halign value
    - Verify: "left" → first glyph at X≥0; "center" → midpoint at X≈0; "right" → last glyph+advance ends at X≈0
    - **Validates: Requirements 4.2**

  - [x] 3.5 Write property test for RTL reversal (Property 4)
    - Add to `TextLayoutPropertyTest.kt`
    - **Property 4: RTL direction reverses character order**
    - Generate random text, compare LTR vs RTL layouts
    - Verify RTL positions are non-increasing
    - **Validates: Requirements 4.4**

  - [x] 3.6 Write property test for spacing linearity (Property 5)
    - Add to `TextLayoutPropertyTest.kt`
    - **Property 5: Spacing multiplier scales inter-character distances linearly**
    - Generate random text, compute layout with spacing S1 and S2
    - Verify distances scale proportionally: dist(S2)/dist(S1) ≈ S2/S1
    - **Validates: Requirements 4.5**

- [x] 4. Checkpoint — verify shared-base compiles and tests pass
  - Ensure all modules compile after TextApprox→Text migration
  - Run shared-base tests: `./gradlew :shared-base:test`
  - Run all tests to confirm no regressions: `./gradlew test`

- [x] 5. AwtFontProvider for desktop/benchmark (kotlin-engine)
  - [x] 5.1 Bundle Liberation Sans font
    - Download Liberation Sans Regular TTF from Fedora project (SIL Open Font License)
    - Place at `shared-base/src/main/resources/fonts/LiberationSans-Regular.ttf`
    - Also place at `app/src/main/assets/fonts/LiberationSans-Regular.ttf` for Android
    - _Requirements: 2.2_

  - [x] 5.2 Implement AwtFontProvider
    - Create `kotlin-engine/src/main/kotlin/com/openscadviewer/engine/text/AwtFontProvider.kt`
    - Load bundled font from classpath resource using `java.awt.Font.createFont()`
    - Implement `getGlyphOutline()` using `Font.createGlyphVector()` + `getGlyphOutline(0)`
    - Flatten Shape via `PathIterator` with flatness tolerance (0.01 at reference size 100)
    - Extract advance width from `GlyphMetrics.getAdvance()`
    - Cache results in `GlyphCache`
    - Implement font fallback: try system font by name, fall back to bundled Liberation Sans
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.5_

  - [x] 5.3 Write property test for closed contours (Property 6)
    - Create `kotlin-engine/src/test/kotlin/com/openscadviewer/engine/text/GlyphOutlinePropertyTest.kt`
    - **Property 6: All flattened contours are closed polygons**
    - For all printable ASCII chars (32-126), extract glyph outlines via AwtFontProvider
    - Verify: each contour has first point ≈ last point (within tolerance), and has ≥ 3 points
    - **Validates: Requirements 3.1, 3.3**

- [x] 6. EarClip triangulator and Kotlin engine mesh generation
  - [x] 6.1 Implement EarClipTriangulator
    - Create `kotlin-engine/src/main/kotlin/com/openscadviewer/engine/text/EarClipTriangulator.kt`
    - Implement polygon triangulation with hole support via bridge-vertex technique
    - Input: list of contours (first=outer CCW, rest=holes CW)
    - Output: list of triangles as point triples
    - Handle edge cases: degenerate polygons (< 3 points), collinear points
    - _Requirements: 5.3_

  - [x] 6.2 Implement generateText() in kotlin-engine MeshGenerator
    - In `kotlin-engine/src/main/kotlin/com/openscadviewer/engine/MeshGenerator.kt`:
    - Replace the temporary placeholder in the `is SceneNode.Text` branch with real glyph-based rendering
    - Use `TextLayoutEngine.layout()` to get positioned glyphs
    - For each glyph, offset contour points by (glyph.x, glyph.y)
    - Triangulate using `EarClipTriangulator` and add triangles to vertex/normal/color buffers
    - For non-extruded text: generate 2D triangles at Z=0 with normal (0,0,1)
    - _Requirements: 5.1, 5.3, 5.4_

  - [x] 6.3 Implement text extrusion in kotlin-engine
    - In `MeshGenerator`, when `SceneNode.LinearExtrude` contains a `SceneNode.Text` child:
    - Generate top face triangles at Z=height (normal 0,0,1)
    - Generate bottom face triangles at Z=0 (normal 0,0,-1, reversed winding)
    - Generate side faces by connecting top/bottom contour edges (quads as 2 triangles with outward normals)
    - _Requirements: 5.2, 5.4_

  - [x] 6.4 Update app MeshGenerator (Android renderer)
    - In `app/src/main/java/com/openscadviewer/renderer/MeshGenerator.kt`:
    - Update the `is SceneNode.Text` branch to use the same glyph-based rendering approach
    - This module uses an `AndroidFontProvider` (to be created) or delegates to the kotlin-engine MeshGenerator
    - _Requirements: 5.1_

  - [x] 6.5 Write property test for 2D text Z=0 (Property 11)
    - Create `kotlin-engine/src/test/kotlin/com/openscadviewer/engine/text/TextMeshPropertyTest.kt`
    - **Property 11: 2D text mesh has all Z coordinates at zero**
    - Generate random short text strings, render without extrusion
    - Verify all Z vertex coordinates are exactly 0.0f
    - **Validates: Requirements 5.1**

  - [x] 6.6 Write property test for extruded text Z range (Property 12)
    - Add to `TextMeshPropertyTest.kt`
    - **Property 12: Extruded text mesh Z coordinates span [0, height]**
    - Generate random text + random heights (0.1-100.0)
    - Verify Z coordinates range from 0 to height; both extremes present
    - **Validates: Requirements 5.2**

  - [x] 6.7 Write property test for empty text produces no geometry (Property 8)
    - Add to `TextMeshPropertyTest.kt`
    - **Property 8: Empty text produces no geometry**
    - Generate SceneNode.Text with empty text or size <= 0
    - Verify MeshGenerator produces zero vertices
    - **Validates: Requirements 1.3, 1.4**

- [x] 7. CGAL engine serialization and C++ text renderer
  - [x] 7.1 Update SceneSerializer for SceneNode.Text
    - In `cgal-engine/src/main/kotlin/com/openscadviewer/engine/SceneSerializer.kt`:
    - In `nodeToJson()`: serialize SceneNode.Text as `{"type":"text", "text":"...", "size":..., "font":"...", "halign":"...", "valign":"...", "spacing":..., "direction":"..."}`
    - In `preprocessForCgal()`: pass Text nodes through (not skipped like TextApprox was); return empty group only if text is empty or size <= 0
    - _Requirements: 6.2_

  - [x] 7.2 Write property test for serialization round-trip (Property 9)
    - Create `cgal-engine/src/test/kotlin/com/openscadviewer/engine/TextSerializerPropertyTest.kt`
    - **Property 9: Scene serialization round-trip preserves text parameters**
    - Generate random SceneNode.Text instances with various parameter values
    - Serialize to JSON, parse JSON, verify all fields match original
    - **Validates: Requirements 6.2**

  - [x] 7.3 Vendor ttf2mesh library
    - Download ttf2mesh source from https://github.com/fetisov/ttf2mesh (MIT license)
    - Place `ttf2mesh.h` and `ttf2mesh.c` in `app/src/main/cpp/ttf2mesh/`
    - Update `app/src/main/cpp/CMakeLists.txt` to include `ttf2mesh/ttf2mesh.c` in the build
    - Verify compilation with existing build: `./scripts/build-cgal-desktop.sh`
    - _Requirements: 6.1_

  - [x] 7.4 Implement text_renderer.h and text_renderer.cpp
    - Create `app/src/main/cpp/text_renderer.h` with `TextRenderer` class declaration
    - Create `app/src/main/cpp/text_renderer.cpp` implementing:
      - `load_font()`: load TTF file using ttf2mesh API, cache font handle
      - `get_glyph()`: extract glyph polygon contours using ttf2mesh, cache results
      - `build_text()`: lay out characters (positioning, alignment, direction), extrude polygons using `make_nef_from_mesh()` from scene_builder, union all character Nefs
    - Handle font loading from bundled asset path (passed as parameter or compiled-in path)
    - _Requirements: 6.1, 6.3, 6.4, 6.5_

  - [x] 7.5 Integrate text_renderer into scene_builder.cpp
    - In `scene_builder.cpp`:
    - Add `#include "text_renderer.h"` and declare a static `TextRenderer` instance
    - Add `type == "text"` branch in `process_node()` that calls `g_text_renderer.build_text()`
    - Add `child_type == "text"` branch in `build_linear_extrude()` that calls `build_text()` with the extrude height
    - Update build script: add `text_renderer.cpp` to the compile command in `build-cgal-desktop.sh`
    - _Requirements: 6.3, 6.4_

  - [x] 7.6 Bundle font for CGAL native access
    - Ensure the Liberation Sans TTF file is accessible to the C++ code at runtime
    - On Android: extract font from APK assets to app cache dir on first CGAL engine use
    - On desktop (benchmarks): load from filesystem path relative to the project or from a known location
    - Pass font file path to `TextRenderer` during initialization
    - _Requirements: 2.2, 6.3_

- [x] 8. AndroidFontProvider (app module)
  - [x] 8.1 Implement AndroidFontProvider
    - Create `app/src/main/java/com/openscadviewer/engine/text/AndroidFontProvider.kt`
    - Load bundled font from `assets/fonts/LiberationSans-Regular.ttf` using `Typeface.createFromAsset()`
    - Implement `getGlyphOutline()` using `Paint.getTextPath()` to get Path, then flatten
    - Implement path flattening: iterate Path using `PathMeasure` or `Path.approximate()` (API 26+) to extract line segment points
    - Group segments into closed contours (detect MOVE_TO as contour boundary)
    - Extract advance width via `Paint.measureText()`
    - Implement font resolution: try `Typeface.create(fontName, NORMAL)`, fallback to bundled
    - Cache results in `GlyphCache`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3_

- [x] 9. Final integration and testing
  - [x] 9.1 Wire FontProvider into engine initialization
    - In kotlin-engine's `KotlinComputeEngine`: accept a `FontProvider` parameter (constructor injection or companion factory)
    - In the app module: pass `AndroidFontProvider(context)` when constructing the engine
    - For benchmarks/desktop: use `AwtFontProvider()` (auto-detected based on platform or passed explicitly)
    - _Requirements: 2.1_

  - [x] 9.2 Integration test: text parsing end-to-end
    - Write test: parse `text("Hello", size=20, halign="center");` → verify produces SceneNode.Text with correct params
    - Write test: parse `linear_extrude(5) text("ABC");` → verify produces LinearExtrude wrapping Text
    - Write test: parse `text("");` → verify produces Text with empty content
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 9.3 Integration test: mesh generation end-to-end
    - Write test: render `text("A")` with AwtFontProvider → verify non-empty mesh with Z=0
    - Write test: render `linear_extrude(10) text("X")` → verify 3D mesh with Z in [0,10]
    - Write test: render `text("")` → verify empty mesh (zero vertices)
    - _Requirements: 5.1, 5.2_

  - [x] 9.4 Run full test suite and fix any regressions
    - Run `./gradlew test` to verify all existing tests pass
    - Run `./gradlew :benchmark:test` to verify benchmarks still work
    - Verify the desktop CGAL build compiles: `./scripts/build-cgal-desktop.sh`
    - _Requirements: all_

## Notes

- The project uses `net.jqwik:jqwik:1.8.4` for property-based testing
- Kotlin source in `shared-base` goes in `shared-base/src/main/kotlin/com/openscadviewer/`
- Kotlin source in `kotlin-engine` goes in `kotlin-engine/src/main/kotlin/com/openscadviewer/engine/`
- Android-specific source goes in `app/src/main/java/com/openscadviewer/`
- C++ source goes in `app/src/main/cpp/`
- The `cgal-engine` module depends on `shared-base` (via `api(project(":shared-base"))`)
- The `kotlin-engine` module depends on `shared-base` (via `api(project(":shared-base"))`)
- The `app` module depends on both `kotlin-engine` and `cgal-engine`
- Liberation Sans is licensed under SIL Open Font License — compatible with bundling in applications
- ttf2mesh is MIT licensed — compatible with the project

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "1.4"] },
    { "id": 2, "tasks": ["2.1", "2.2", "2.3"] },
    { "id": 3, "tasks": ["2.4", "3.1"] },
    { "id": 4, "tasks": ["3.2", "3.3", "3.4", "3.5", "3.6", "4"] },
    { "id": 5, "tasks": ["5.1", "5.2"] },
    { "id": 6, "tasks": ["5.3", "6.1"] },
    { "id": 7, "tasks": ["6.2", "6.3", "6.4", "6.5", "6.6", "6.7"] },
    { "id": 8, "tasks": ["7.1", "7.2", "7.3"] },
    { "id": 9, "tasks": ["7.4", "7.5", "7.6"] },
    { "id": 10, "tasks": ["8.1"] },
    { "id": 11, "tasks": ["9.1", "9.2", "9.3", "9.4"] }
  ]
}
```
