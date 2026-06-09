# OpenSCAD Viewer for Android

An Android application that reads OpenSCAD (.scad) files, provides syntax-highlighted code editing, 3D preview rendering, and STL file export.

## Features

- **Code Editor** with OpenSCAD syntax highlighting (keywords, functions, numbers, strings, comments)
- **3D Preview** using OpenGL ES 2.0 with Phong shading and orbit/pan/zoom controls
- **STL Export** in binary format for 3D printing
- **File Picker** to open .scad files from device storage
- **Touch Controls**: single-finger orbit, two-finger pan, pinch to zoom

## Supported OpenSCAD Primitives

- `cube`, `sphere`, `cylinder`
- `circle`, `square`, `polygon`
- `translate`, `rotate`, `scale`
- `union`, `difference`, `intersection`
- `color`, `linear_extrude`
- `hull`, `minkowski`
- Variables and basic math expressions

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

- CSG operations (difference, intersection) display all geometry without boolean subtraction (proper CSG requires BSP tree implementation)
- Module definitions and `include`/`use` are not yet supported
- Only a subset of OpenSCAD is implemented

## License

MIT
