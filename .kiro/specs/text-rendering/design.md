# Design Document: Text Rendering

## Overview

This design describes how to add proper text-to-geometry conversion to the OpenSCAD viewer, replacing the current `TextApprox` placeholder (a flat rectangle) with actual glyph polygon outlines. The `text()` module produces 2D polygon geometry from TrueType font glyphs, suitable for direct rendering or extrusion via `linear_extrude()`.

The implementation leverages platform-native libraries for font glyph extraction:
- **Android:** `android.graphics.Paint.getTextPath()` returns a `Path` object containing glyph outlines as Bezier curves, which are flattened to polygon segments.
- **Desktop JVM (benchmarks):** `java.awt.Font.createGlyphVector()` with `PathIterator` provides equivalent outline extraction.
- **CGAL C++ engine:** The [ttf2mesh](https://github.com/fetisov/ttf2mesh) library (MIT, single-file C) converts TTF glyphs directly to 2D polygon meshes.

Key design decisions:
- **Platform APIs over custom TTF parsing:** Android's Skia-based `Paint.getTextPath()` handles all font complexities (hinting, composite glyphs, kerning). No need to write a TTF parser in Kotlin.
- **ttf2mesh for CGAL:** The C++ engine operates headless without Android APIs. ttf2mesh is a lightweight single-file C library that integrates easily into the existing CMake build.
- **Font_Provider abstraction:** A `FontProvider` interface isolates platform-specific glyph extraction, with `AndroidFontProvider` and `AwtFontProvider` implementations.
- **SceneNode.Text replaces TextApprox:** The sealed class variant carries all `text()` parameters, enabling both engines to interpret them consistently.
- **Glyph caching at the polygon level:** Flattened polygon outlines are cached per (font, character) to avoid repeated path extraction and flattening.
- **Bundled Liberation Sans:** Ensures consistent fallback rendering across devices.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        shared-base module                         │
│  ┌──────────────┐   ┌──────────────────┐   ┌────────────────┐  │
│  │ SceneNode.Text│   │ OpenSCADParser    │   │ FontProvider   │  │
│  │ (data class)  │   │ parseText()       │   │ (interface)    │  │
│  └──────────────┘   └──────────────────┘   └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
         │                                          │
    ┌────┴─────────────┐                ┌───────────┴───────────┐
    ▼                  ▼                ▼                       ▼
┌────────────────┐ ┌────────────────┐ ┌───────────────┐ ┌──────────────┐
│ kotlin-engine  │ │  cgal-engine   │ │AndroidFont-   │ │ AwtFont-     │
│                │ │                │ │Provider (app)  │ │Provider      │
│ MeshGenerator  │ │SceneSerializer │ │Paint.getText-  │ │(kotlin-engine│
│ generates mesh │ │serializes JSON │ │Path()          │ │/benchmark)   │
│ from glyphs   │ │with text params│ └───────────────┘ └──────────────┘
└────────────────┘ └────────────────┘
                          │
                          ▼
              ┌──────────────────────┐
              │  C++ scene_builder   │
              │  + ttf2mesh library  │
              │  text_renderer.cpp   │
              └──────────────────────┘
```

### Data Flow — Kotlin Engine

1. Parser produces `SceneNode.Text(text, size, font, halign, valign, spacing, direction)`
2. `MeshGenerator.generateText()` is called with the Text node
3. `FontProvider.getGlyphOutlines(char, font)` returns cached or freshly-extracted polygon contours
4. `TextLayoutEngine` computes character positions (spacing, alignment, direction)
5. Positioned glyph polygons are triangulated and added to the mesh vertex buffer

### Data Flow — CGAL Engine

1. `SceneSerializer` encounters `SceneNode.Text` and serializes it as JSON: `{"type":"text", "text":"Hello", "size":10, ...}`
2. C++ `scene_builder.cpp` dispatches to `build_text()` in `text_renderer.cpp`
3. `text_renderer.cpp` uses ttf2mesh to load the font and extract glyph outlines
4. Glyph polygons are positioned per layout rules, then extruded (if inside `linear_extrude`) using `make_nef_from_mesh()`
5. Resulting Nef polyhedra are returned to the CSG pipeline

### Threading Model

- Glyph extraction (Android `Paint.getTextPath()`) runs on the calling thread (fast: <1ms per glyph)
- `GlyphCache` is thread-safe via `ConcurrentHashMap`
- ttf2mesh operates single-threaded within the CGAL compute thread (already on `Dispatchers.Default`)

## Components and Interfaces

### SceneNode.Text (shared-base)

```kotlin
data class Text(
    val text: String,
    val size: Double = 10.0,
    val font: String = "Liberation Sans",
    val halign: String = "left",     // "left", "center", "right"
    val valign: String = "baseline", // "baseline", "bottom", "top", "center"
    val spacing: Double = 1.0,
    val direction: String = "ltr"    // "ltr", "rtl"
) : SceneNode()
```

### GlyphOutline (shared-base)

```kotlin
/**
 * Flattened polygon outline for a single glyph.
 * Contours are closed polygons; outer contours are CCW, inner (holes) are CW.
 */
data class GlyphOutline(
    val contours: List<List<Pair<Float, Float>>>,  // List of closed polygons
    val advanceWidth: Float,                        // Horizontal advance after this glyph
    val ascent: Float,                              // Distance above baseline
    val descent: Float                              // Distance below baseline (negative)
)
```

### FontProvider (shared-base interface)

```kotlin
/**
 * Platform-agnostic interface for extracting glyph polygon outlines from fonts.
 */
interface FontProvider {
    /**
     * Get the flattened polygon outline for a character at the given size.
     * Returns cached data if available, otherwise extracts and caches.
     */
    fun getGlyphOutline(char: Char, fontName: String, size: Float): GlyphOutline?

    /**
     * Get font metrics (ascent, descent) for the given font at the given size.
     */
    fun getFontMetrics(fontName: String, size: Float): FontMetrics

    /**
     * Check if a specific font is available on this platform.
     */
    fun isFontAvailable(fontName: String): Boolean
}

data class FontMetrics(
    val ascent: Float,   // Positive: distance from baseline to top
    val descent: Float,  // Negative: distance from baseline to bottom
    val lineHeight: Float
)
```

### AndroidFontProvider (app module)

```kotlin
/**
 * FontProvider implementation using Android's Paint.getTextPath() API.
 * Extracts glyph outlines from system or bundled TrueType fonts.
 */
class AndroidFontProvider(private val context: Context) : FontProvider {
    private val cache = GlyphCache()
    private val defaultTypeface: Typeface  // Loaded from assets/fonts/LiberationSans-Regular.ttf

    override fun getGlyphOutline(char: Char, fontName: String, size: Float): GlyphOutline? {
        val cacheKey = GlyphCacheKey(fontName, char)
        cache.get(cacheKey)?.let { return it.scaledTo(size) }

        val typeface = resolveTypeface(fontName)
        val paint = Paint().apply {
            this.typeface = typeface
            textSize = REFERENCE_SIZE  // Extract at reference size, scale later
        }

        val path = Path()
        paint.getTextPath(char.toString(), 0, 1, 0f, 0f, path)

        val outline = flattenPath(path, paint, char)
        cache.put(cacheKey, outline)
        return outline.scaledTo(size)
    }

    /**
     * Flatten a Path (containing Bezier curves) to polygon segments.
     * Uses PathMeasure to walk the path and extract points.
     */
    private fun flattenPath(path: Path, paint: Paint, char: Char): GlyphOutline {
        // Use Path.approximate() (API 26+) or manual flattening
        // Returns list of contours as point lists
    }
}
```

### AwtFontProvider (kotlin-engine / benchmark)

```kotlin
/**
 * FontProvider implementation using Java AWT Font/GlyphVector APIs.
 * Used for desktop JVM (benchmarks, CLI tool).
 */
class AwtFontProvider : FontProvider {
    private val cache = GlyphCache()
    private val defaultFont: java.awt.Font  // Loaded from classpath resource

    override fun getGlyphOutline(char: Char, fontName: String, size: Float): GlyphOutline? {
        val cacheKey = GlyphCacheKey(fontName, char)
        cache.get(cacheKey)?.let { return it.scaledTo(size) }

        val font = resolveFont(fontName).deriveFont(REFERENCE_SIZE)
        val frc = FontRenderContext(null, true, true)
        val gv = font.createGlyphVector(frc, charArrayOf(char))
        val shape = gv.getGlyphOutline(0)

        val outline = flattenShape(shape, gv.getGlyphMetrics(0))
        cache.put(cacheKey, outline)
        return outline.scaledTo(size)
    }

    /**
     * Flatten a Shape (via PathIterator) to polygon segments.
     * Uses getPathIterator(null, flatness) for automatic Bezier flattening.
     */
    private fun flattenShape(shape: Shape, metrics: GlyphMetrics): GlyphOutline {
        val contours = mutableListOf<List<Pair<Float, Float>>>()
        val iterator = shape.getPathIterator(null, FLATNESS_TOLERANCE)
        // Walk segments: SEG_MOVETO starts new contour, SEG_LINETO adds point, SEG_CLOSE finishes
    }
}
```

### GlyphCache (shared-base)

```kotlin
/**
 * Thread-safe LRU cache for glyph outlines.
 * Stores outlines at a reference size; consumers scale on retrieval.
 */
class GlyphCache(private val maxSizeBytes: Long = 4 * 1024 * 1024) {
    private val cache = ConcurrentHashMap<GlyphCacheKey, GlyphOutline>()
    private val accessOrder = ConcurrentLinkedDeque<GlyphCacheKey>()
    private val currentSize = AtomicLong(0)

    fun get(key: GlyphCacheKey): GlyphOutline? {
        val outline = cache[key] ?: return null
        accessOrder.remove(key)
        accessOrder.addFirst(key)
        return outline
    }

    fun put(key: GlyphCacheKey, outline: GlyphOutline) {
        val size = outline.estimatedSizeBytes()
        while (currentSize.get() + size > maxSizeBytes && accessOrder.isNotEmpty()) {
            val evictKey = accessOrder.pollLast() ?: break
            cache.remove(evictKey)?.let { currentSize.addAndGet(-it.estimatedSizeBytes()) }
        }
        cache[key] = outline
        accessOrder.addFirst(key)
        currentSize.addAndGet(size)
    }
}

data class GlyphCacheKey(val fontName: String, val char: Char)
```

### TextLayoutEngine (shared-base)

```kotlin
/**
 * Computes character positions for a text string given font metrics and layout parameters.
 * Shared logic between both engines — returns positioned glyph data ready for mesh generation.
 */
object TextLayoutEngine {
    data class PositionedGlyph(
        val char: Char,
        val x: Float,
        val y: Float,
        val outline: GlyphOutline
    )

    /**
     * Layout text characters with proper spacing, alignment, and direction.
     */
    fun layout(
        text: String,
        size: Float,
        halign: String,
        valign: String,
        spacing: Float,
        direction: String,
        fontProvider: FontProvider,
        fontName: String
    ): List<PositionedGlyph> {
        if (text.isEmpty()) return emptyList()

        val glyphs = text.map { ch ->
            ch to (fontProvider.getGlyphOutline(ch, fontName, size)
                ?: fontProvider.getGlyphOutline('\u0000', fontName, size)) // placeholder
        }

        // Compute positions along X axis
        var xCursor = 0f
        val positioned = glyphs.map { (ch, outline) ->
            val glyph = PositionedGlyph(ch, xCursor, 0f, outline!!)
            xCursor += outline.advanceWidth * spacing
            glyph
        }

        val totalWidth = xCursor
        val metrics = fontProvider.getFontMetrics(fontName, size)

        // Apply horizontal alignment offset
        val xOffset = when (halign) {
            "center" -> -totalWidth / 2f
            "right" -> -totalWidth
            else -> 0f  // "left"
        }

        // Apply vertical alignment offset
        val yOffset = when (valign) {
            "bottom" -> -metrics.descent
            "top" -> -metrics.ascent
            "center" -> -(metrics.ascent + metrics.descent) / 2f
            else -> 0f  // "baseline"
        }

        // Apply direction (RTL reverses order and mirrors X positions)
        val directedGlyphs = if (direction == "rtl") {
            positioned.map { it.copy(x = -(it.x + it.outline.advanceWidth)) }
        } else {
            positioned
        }

        return directedGlyphs.map { it.copy(x = it.x + xOffset, y = it.y + yOffset) }
    }
}
```

### Parser Changes (shared-base: OpenSCADParser)

The existing `parseText()` method is updated to produce `SceneNode.Text` instead of `SceneNode.TextApprox`:

```kotlin
private fun parseText(): SceneNode {
    skipWhitespaceAndComments()
    var textContent = ""
    var size = 10.0
    var font = "Liberation Sans"
    var halign = "left"
    var valign = "baseline"
    var spacing = 1.0
    var direction = "ltr"

    if (pos < input.length && input[pos] == '(') {
        pos++; skipWhitespaceAndComments()
        val paramsStr = extractParenContent()
        val parts = splitParams(paramsStr)
        for (part in parts) {
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                val valueStr = trimmed.substring(eqIdx + 1).trim()
                when (name) {
                    "size" -> size = evaluateParamValue(valueStr)
                    "font" -> font = valueStr.removeSurrounding("\"")
                    "halign" -> halign = valueStr.removeSurrounding("\"")
                    "valign" -> valign = valueStr.removeSurrounding("\"")
                    "spacing" -> spacing = evaluateParamValue(valueStr)
                    "direction" -> direction = valueStr.removeSurrounding("\"")
                }
            } else {
                if (textContent.isEmpty()) {
                    textContent = resolveTextParam(trimmed)
                }
            }
        }
    }
    skipSemicolon()
    return SceneNode.Text(textContent, size, font, halign, valign, spacing, direction)
}
```

### MeshGenerator Text Handling (kotlin-engine)

```kotlin
// In MeshGenerator.generateNode() — new branch for SceneNode.Text
is SceneNode.Text -> {
    if (node.text.isNotEmpty() && node.size > 0) {
        generateText(node, vertices, normals, colors, transform)
    }
}

private fun generateText(
    node: SceneNode.Text,
    vertices: MutableList<Float>,
    normals: MutableList<Float>,
    colors: MutableList<Float>,
    transform: Matrix4
) {
    val positioned = TextLayoutEngine.layout(
        node.text, node.size.toFloat(), node.halign, node.valign,
        node.spacing.toFloat(), node.direction, fontProvider, node.font
    )

    for (glyph in positioned) {
        val translated = glyph.outline.contours.map { contour ->
            contour.map { (x, y) -> Pair(x + glyph.x, y + glyph.y) }
        }
        // Triangulate the multi-contour polygon (outer - holes)
        val triangles = EarClipTriangulator.triangulate(translated)
        for (tri in triangles) {
            // Add vertices at Z=0 with normal (0,0,1)
            addTriangle2D(tri, vertices, normals, colors, transform)
        }
    }
}
```

### SceneSerializer Text Handling (cgal-engine)

```kotlin
// In SceneSerializer.nodeToJson() — new branch
is SceneNode.Text -> {
    obj.put("type", "text")
    obj.put("text", node.text)
    obj.put("size", node.size)
    obj.put("font", node.font)
    obj.put("halign", node.halign)
    obj.put("valign", node.valign)
    obj.put("spacing", node.spacing)
    obj.put("direction", node.direction)
}

// In SceneSerializer.preprocessForCgal() — new branch
is SceneNode.Text -> {
    if (node.text.isEmpty() || node.size <= 0) SceneNode.Group(emptyList())
    else node  // Pass through to CGAL for native rendering
}
```

### C++ Text Renderer (text_renderer.h / text_renderer.cpp)

```cpp
// text_renderer.h
#pragma once
#include "scene_builder.h"
#include <string>
#include <vector>
#include <unordered_map>

// Forward declaration — ttf2mesh types
struct ttf_t;

/**
 * Manages font loading and glyph-to-polygon conversion using ttf2mesh.
 * Caches glyph outlines for reuse across characters.
 */
class TextRenderer {
public:
    TextRenderer();
    ~TextRenderer();

    /**
     * Build Nef polyhedra for text content.
     * Returns a list of Nef polyhedra (one per character or merged).
     */
    Nef_polyhedron build_text(
        const std::string& text,
        double size,
        const std::string& font,
        const std::string& halign,
        const std::string& valign,
        double spacing,
        const std::string& direction,
        double extrude_height,  // 0 = 2D only (flat polygon)
        std::atomic<bool>& cancel_flag
    );

private:
    struct GlyphData {
        std::vector<std::vector<std::pair<double, double>>> contours;
        double advance_width;
    };

    ttf_t* load_font(const std::string& font_name);
    GlyphData get_glyph(ttf_t* font, int codepoint);

    std::unordered_map<std::string, ttf_t*> font_cache_;
    std::unordered_map<uint64_t, GlyphData> glyph_cache_;  // key = font_hash << 32 | codepoint
};
```

### Integration into scene_builder.cpp

```cpp
// In process_node() — new branch for "text" type:
if (type == "text") {
    std::string text_content = node.value("text", "");
    double size = node.value("size", 10.0);
    std::string font = node.value("font", "Liberation Sans");
    std::string halign = node.value("halign", "left");
    std::string valign = node.value("valign", "baseline");
    double spacing = node.value("spacing", 1.0);
    std::string direction = node.value("direction", "ltr");

    if (!text_content.empty() && size > 0) {
        // extrude_height = 0 means flat 2D (for direct text node)
        Nef_polyhedron nef = g_text_renderer.build_text(
            text_content, size, font, halign, valign, spacing, direction,
            0.0, cancel_flag);
        if (!nef.is_empty()) {
            result.push_back({std::move(nef), color});
        }
    }
    return;
}

// In build_linear_extrude() — new branch for "text" child type:
if (child_type == "text") {
    // Extract with extrude_height = the linear_extrude height
    Nef_polyhedron nef = g_text_renderer.build_text(
        child_node.value("text", ""), ...params...,
        height, cancel_flag);
    return nef;
}
```

### EarClipTriangulator (kotlin-engine)

```kotlin
/**
 * Triangulates a polygon with holes using ear-clipping algorithm.
 * Outer contours are CCW, inner contours (holes) are CW.
 * Returns list of triangles as (p1, p2, p3) point triples.
 */
object EarClipTriangulator {
    data class Triangle(
        val p1: Pair<Float, Float>,
        val p2: Pair<Float, Float>,
        val p3: Pair<Float, Float>
    )

    /**
     * @param contours First contour is outer boundary (CCW), remaining are holes (CW)
     */
    fun triangulate(contours: List<List<Pair<Float, Float>>>): List<Triangle> {
        if (contours.isEmpty()) return emptyList()
        // 1. Bridge holes into outer contour by finding mutual visibility edges
        // 2. Apply ear-clipping on the merged polygon
        // 3. Return triangle list
    }
}
```

### CMakeLists.txt Update

```cmake
# Add ttf2mesh source (single-file library)
add_library(cgal_engine SHARED
    cgal_engine.cpp
    cgal_compute.cpp
    scene_builder.cpp
    mesh_extractor.cpp
    text_renderer.cpp       # NEW
    ttf2mesh/ttf2mesh.c     # NEW — vendored ttf2mesh library
)
```

### Bundled Font Asset

Location: `app/src/main/assets/fonts/LiberationSans-Regular.ttf`

For CGAL (desktop build / Android native): The font file path is passed to `TextRenderer` at initialization. On Android, it's extracted from APK assets to a cache directory on first use. For desktop builds, it's loaded from a classpath resource or filesystem path.

## Data Models

### SceneNode.Text Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `text` | String | "" | The text content to render |
| `size` | Double | 10.0 | Font size in OpenSCAD units |
| `font` | String | "Liberation Sans" | Font family name |
| `halign` | String | "left" | Horizontal alignment: "left", "center", "right" |
| `valign` | String | "baseline" | Vertical alignment: "baseline", "bottom", "top", "center" |
| `spacing` | Double | 1.0 | Character spacing multiplier |
| `direction` | String | "ltr" | Text direction: "ltr" or "rtl" |

### GlyphOutline Fields

| Field | Type | Description |
|-------|------|-------------|
| `contours` | List<List<Pair<Float, Float>>> | Closed polygon paths; first is outer (CCW), rest are holes (CW) |
| `advanceWidth` | Float | Horizontal distance to next character origin |
| `ascent` | Float | Max height above baseline |
| `descent` | Float | Max depth below baseline (negative value) |

### JSON Schema for CGAL Text Node

```json
{
    "type": "text",
    "text": "Hello World",
    "size": 10.0,
    "font": "Liberation Sans",
    "halign": "left",
    "valign": "baseline",
    "spacing": 1.0,
    "direction": "ltr"
}
```

## Correctness Properties

### Property 1: Text layout positions are monotonically increasing (LTR)

*For any* non-empty text string with spacing > 0 and direction="ltr", the X positions of laid-out glyphs shall be strictly non-decreasing (each character's X >= previous character's X).

**Validates: Requirements 4.1, 4.4**

### Property 2: Total text width equals sum of advance widths times spacing

*For any* text string and spacing value, the total width of the laid-out text (last character X + last advance width × spacing) shall equal the sum of all characters' advance widths multiplied by spacing.

**Validates: Requirements 4.1, 4.5**

### Property 3: Horizontal alignment offsets are consistent

*For any* text with halign="center", the center of the text bounding box (minX + maxX) / 2 shall be within 0.01 units of X=0. For halign="right", the rightmost extent shall be within 0.01 units of X=0. For halign="left", the leftmost extent shall be at X=0.

**Validates: Requirements 4.2**

### Property 4: RTL direction reverses character order

*For any* text laid out with direction="rtl", the X positions of glyphs shall be strictly non-increasing (each successive character's X position is less than or equal to the previous). Additionally, the set of characters rendered shall be identical to the LTR layout.

**Validates: Requirements 4.4**

### Property 5: Spacing multiplier scales inter-character distances linearly

*For any* text string, the distance between character N and character N+1 at spacing=S equals S times the distance at spacing=1.0.

**Validates: Requirements 4.5**

### Property 6: All flattened contours are closed polygons

*For any* character glyph extracted by the Font_Provider, every contour in the GlyphOutline shall have its first point equal to its last point (closed path), and contain at least 3 distinct points.

**Validates: Requirements 3.1, 3.3**

### Property 7: Glyph cache returns identical data on cache hit

*For any* sequence of getGlyphOutline() calls with the same (font, char, size) arguments, all calls after the first shall return polygon data identical to the first call's result.

**Validates: Requirements 3.4, 8.1**

### Property 8: Empty text produces no geometry

*For any* SceneNode.Text with empty text string or size <= 0, both engines shall produce zero vertices (no geometry added to the mesh).

**Validates: Requirements 1.3, 1.4**

### Property 9: Scene serialization round-trip preserves text parameters

*For any* SceneNode.Text node, serializing to JSON and inspecting the result shall yield a JSON object with type="text" and all parameter values matching the original node's field values.

**Validates: Requirements 6.2**

### Property 10: containsGeometry() correctness for Text nodes

*For any* SceneNode.Text with non-empty text and positive size, `containsGeometry()` shall return true. For empty text or non-positive size, it shall return false.

**Validates: Requirements 7.4, 7.5**

### Property 11: 2D text mesh has all Z coordinates at zero

*For any* SceneNode.Text rendered without linear_extrude, all vertex Z coordinates in the generated mesh shall be exactly 0.0f.

**Validates: Requirements 5.1**

### Property 12: Extruded text mesh Z coordinates span [0, height]

*For any* LinearExtrude(height, Text(...)) where height > 0, the generated mesh vertices shall have Z coordinates in the range [0, height], with at least some vertices at Z=0 and some at Z=height.

**Validates: Requirements 5.2**

## Error Handling

### Invalid States

| Condition | Handling |
|-----------|----------|
| Empty text string | Both engines produce no geometry; no error |
| Size <= 0 | Both engines produce no geometry; no error |
| Font not found | Fall back to bundled Liberation Sans |
| ttf2mesh fails to load font | CGAL engine logs warning, skips text node |
| Degenerate glyph (zero-area polygon) | Skip individual glyph, continue with rest |
| ttf2mesh returns empty contours | Skip character, use advance width for spacing |
| Path.getTextPath() returns empty path | Use rectangular placeholder outline |
| Cache exceeds memory limit | LRU eviction removes oldest entries |

### Platform Compatibility

- **Android API < 26:** `Path.approximate()` unavailable; use manual `PathMeasure` walk instead
- **Headless JVM (no AWT):** If `java.awt.Font` is unavailable (unlikely for benchmark JVM), fall back to a minimal hardcoded glyph set
- **Missing bundled font:** Checked at app startup; logs error if asset not found

## Testing Strategy

### Property-Based Tests (jqwik)

| Property | Test Class | Generator Strategy |
|----------|-----------|-------------------|
| Property 1: Monotonic LTR positions | `TextLayoutPropertyTest` | Random ASCII strings (1-50 chars), random spacing (0.1-5.0) |
| Property 2: Total width = sum of advances | `TextLayoutPropertyTest` | Random strings with mock FontProvider returning known advance widths |
| Property 3: Alignment offsets | `TextLayoutPropertyTest` | Random strings, each halign value |
| Property 4: RTL reversal | `TextLayoutPropertyTest` | Random strings, compare LTR vs RTL positions |
| Property 5: Spacing linearity | `TextLayoutPropertyTest` | Random strings, pairs of spacing values |
| Property 6: Closed contours | `GlyphOutlinePropertyTest` | All printable ASCII chars with real FontProvider |
| Property 7: Cache consistency | `GlyphCachePropertyTest` | Random sequences of get/put operations |
| Property 8: Empty text = no geometry | `TextMeshPropertyTest` | Empty strings, zero/negative sizes |
| Property 9: Serialization round-trip | `TextSerializerPropertyTest` | Random Text node parameters |
| Property 10: containsGeometry | `SceneNodePropertyTest` | Random Text nodes with empty/non-empty text |
| Property 11: 2D Z=0 | `TextMeshPropertyTest` | Random text rendered without extrusion |
| Property 12: Extruded Z range | `TextMeshPropertyTest` | Random text + random extrude heights |

### Unit Tests (Example-Based)

- Verify parser produces correct SceneNode.Text with all defaults
- Verify parser handles named parameters in any order
- Verify specific characters produce expected contour counts (e.g., "O" has 2 contours)
- Verify triangulation of a known polygon with one hole produces correct triangle count
- Verify ttf2mesh integration compiles and loads the bundled font (C++ unit test)

### Integration Tests

- End-to-end: Parse `text("Hello"); → generate mesh → verify non-empty vertices`
- End-to-end: Parse `linear_extrude(5) text("A");` → verify 3D mesh has correct Z range
- CGAL integration: Verify `difference() { cube(10); linear_extrude(5) text("X"); }` produces geometry
- Performance: 100-character text renders within 100ms (benchmark test)

### Test File Structure

```
shared-base/src/test/kotlin/com/openscadviewer/parser/
├── TextParserPropertyTest.kt
└── TextParserTest.kt

kotlin-engine/src/test/kotlin/com/openscadviewer/engine/
├── TextLayoutPropertyTest.kt
├── GlyphCachePropertyTest.kt
├── TextMeshPropertyTest.kt
└── EarClipTriangulatorTest.kt

cgal-engine/src/test/kotlin/com/openscadviewer/engine/
└── TextSerializerPropertyTest.kt

app/src/test/kotlin/com/openscadviewer/engine/
└── SceneNodePropertyTest.kt
```
