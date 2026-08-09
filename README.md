# OpenSCAD Viewer for Android

An Android application that reads OpenSCAD (.scad) files, provides syntax-highlighted code editing, 3D preview rendering, and STL file export.

## Features

- **Code Editor** with OpenSCAD syntax highlighting (keywords, functions, numbers, strings, comments)
- **3D Preview** using OpenGL ES 2.0 with Phong shading and orbit/pan/zoom controls
- **STL Export** in binary format for 3D printing
- **File Picker** to open .scad files from device storage
- **Touch Controls**: single-finger orbit, two-finger pan, pinch to zoom

## Supported OpenSCAD Constructs

**3D Primitives:** `cube`, `sphere`, `cylinder`

**2D Primitives:** `circle`, `square`, `polygon`, `text`

**Transformations:** `translate`, `rotate`, `scale`, `color`

**CSG Operations:** `union`, `difference`, `intersection`, `hull`, `minkowski`

**Extrusion:** `linear_extrude`

**Language Features:**
- Variables and basic math expressions
- User-defined modules (with parameters and defaults)
- User-defined functions
- `for` loops
- Ternary operator, string/array indexing

## Unsupported OpenSCAD Constructs

The following OpenSCAD features are not yet implemented:

**Primitives & Modules:**
- `polyhedron`
- `surface`, `import` (STL/DXF/SVG import)

**Transformations:**
- `mirror`
- `multmatrix`
- `resize`
- `offset`

**Extrusion & Projection:**
- `rotate_extrude`
- `projection`

**Language Features:**
- `include` / `use` (external file imports)
- `if` / `else` conditional statements
- `let`, `each`, `assert`, `echo`
- List comprehensions
- `children()`, `$children`
- Recursive modules
- Special variables beyond `$fn`, `$fa`, `$fs`

**Text Parameters:**
- `font` resolves to bundled Liberation Sans if the specified font is unavailable
- `$fn` / `$fa` / `$fs` are not applied to text curve flattening

**Other:**
- `render`
- Named colors (only `[r, g, b]` / `[r, g, b, a]` vector notation and common color name strings)
- Animation (`$t`)
- Customizer syntax

## Building

1. Open in Android Studio
2. Sync Gradle
3. Build and run on device/emulator (API 26+)

## Architecture

```
com.openscadviewer/
├── MainActivity.kt          - Main UI controller
├── editor/
│   └── SyntaxHighlighter.kt - OpenSCAD syntax coloring
├── parser/
│   ├── OpenSCADParser.kt    - Recursive descent parser
│   └── SceneNode.kt         - Scene graph data model
└── renderer/
    ├── Matrix4.kt           - 4x4 matrix math
    ├── MeshGenerator.kt     - Triangle mesh generation
    ├── SceneRenderer.kt     - OpenGL ES 2.0 renderer
    ├── STLExporter.kt       - Binary/ASCII STL export
    └── TouchHandler.kt      - Touch gesture handling
```

## Requirements

- Android 8.0 (API 26) or higher
- OpenGL ES 2.0 support

## Limitations

- `hull` and `minkowski` are parsed but treated as `union` (no convex hull or Minkowski sum computation)
- CSG operations in the Kotlin engine render all children without true Boolean subtraction; the CGAL engine provides exact CSG
- Only a subset of OpenSCAD is implemented (see Unsupported Constructs above)

## License

MIT
