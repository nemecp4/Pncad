# Implementation Plan: CGAL Compute Engine

## Overview

This plan implements a CGAL-based native compute engine as an alternative to the existing pure-Kotlin MeshGenerator. The implementation proceeds incrementally: first establishing the Kotlin abstraction layer, then the JNI bridge and native build system, followed by CGAL CSG logic, engine selection UI, error handling, and finally integration with the existing rendering pipeline.

## Tasks

- [x] 1. Set up ComputeEngine abstraction and Kotlin engine wrapper
  - [x] 1.1 Create ComputeEngine interface, MeshResult, ComputeError, and ErrorCategory
    - Create `app/src/main/java/com/openscadviewer/engine/ComputeEngine.kt` with the `ComputeEngine` interface defining `suspend fun compute(scene: SceneNode): Result<MeshResult>`, `fun cancel()`, and `fun isAvailable(): Boolean`
    - Create `app/src/main/java/com/openscadviewer/engine/MeshResult.kt` with the `MeshResult` data class (vertices, normals, colors FloatArrays) and `EMPTY` companion
    - Create `app/src/main/java/com/openscadviewer/engine/ComputeError.kt` with `ComputeError` data class, `ErrorCategory` enum (INVALID_INPUT, COMPUTATION_FAILURE, OUT_OF_MEMORY, TIMEOUT, CANCELLED), and `ComputeException` class
    - _Requirements: 1.1, 1.5, 1.6_

  - [x] 1.2 Create KotlinComputeEngine wrapping existing MeshGenerator
    - Create `app/src/main/java/com/openscadviewer/engine/KotlinComputeEngine.kt` that implements `ComputeEngine`
    - Delegate to existing `MeshGenerator.generate()` and convert `MeshGenerator.Mesh` to `MeshResult`
    - Handle cancellation via `@Volatile` boolean flag
    - `isAvailable()` always returns true
    - _Requirements: 1.2, 1.5_

  - [x] 1.3 Write property test for mesh output structural invariants
    - **Property 1: Mesh output structural invariants**
    - **Validates: Requirements 1.1, 4.5**
    - Use jqwik to generate random SceneNode trees and verify: `vertices.size % 9 == 0`, `normals.size == vertices.size`, `colors.size == (vertices.size / 3) * 4`

  - [x] 1.4 Write property test for empty scene graph
    - **Property 2: Empty scene graph produces empty result**
    - **Validates: Requirements 1.5**
    - Generate SceneNode trees with only structural nodes (Group, Translate, Rotate, Scale) wrapping no primitives and verify result has zero vertices

- [x] 2. Implement SceneSerializer and EngineManager
  - [x] 2.1 Create SceneSerializer for JSON encoding of SceneNode trees
    - Create `app/src/main/java/com/openscadviewer/engine/SceneSerializer.kt`
    - Implement `toJson(node: SceneNode): String` using `org.json.JSONObject`/`JSONArray`
    - Handle all SceneNode types: Cube, Sphere, Cylinder, Circle, Square, Polygon, LinearExtrude, Translate, Rotate, Scale, Color, Union, Difference, Intersection, Group
    - _Requirements: 4.1_

  - [x] 2.2 Write property test for serialization round-trip
    - **Property 4: Scene graph serialization round-trip**
    - **Validates: Requirements 4.1**
    - Generate random SceneNode trees, serialize to JSON, deserialize back, verify structural equivalence

  - [x] 2.3 Create EngineManager for engine selection and persistence
    - Create `app/src/main/java/com/openscadviewer/engine/EngineManager.kt`
    - Define `EngineType` enum (KOTLIN, CGAL)
    - Use SharedPreferences with key `"engine_type"`, default `"KOTLIN"`
    - Expose `selectedType` property (get/set with persistence), `currentEngine` property, `isCgalAvailable()` method
    - If CGAL selected but unavailable, fall back to Kotlin engine
    - _Requirements: 2.2, 2.3, 6.2_

  - [x] 2.4 Write property test for engine preference persistence round-trip
    - **Property 3: Engine preference persistence round-trip**
    - **Validates: Requirements 2.2**
    - For any EngineType value, store via EngineManager and read back, verify same value returned

- [x] 3. Checkpoint - Verify Kotlin abstraction layer
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Set up native build system and CMake configuration
  - [x] 4.1 Create CMakeLists.txt for the native library
    - Create `app/src/main/cpp/CMakeLists.txt` with C++17 standard
    - Define `cgal_engine` shared library target
    - Configure include paths for CGAL, Boost, GMP, MPFR headers from vendor directory
    - Link against GMP and MPFR static libraries per ABI using `${ANDROID_ABI}`
    - Add `CGAL_HEADER_ONLY` definition
    - Link `log` library for Android logging
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 7.2_

  - [x] 4.2 Update app/build.gradle.kts with externalNativeBuild configuration
    - Add `externalNativeBuild` block in `android {}` pointing to `src/main/cpp/CMakeLists.txt`
    - Add `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }` in `defaultConfig`
    - _Requirements: 7.1, 7.3, 7.5_

  - [x] 4.3 Create vendor directory structure with placeholder README files
    - Create directory layout: `app/vendor/cgal/include/`, `app/vendor/boost/include/`, `app/vendor/gmp/include/`, `app/vendor/gmp/lib/{arm64-v8a,armeabi-v7a,x86_64}/`, `app/vendor/mpfr/include/`, `app/vendor/mpfr/lib/{arm64-v8a,armeabi-v7a,x86_64}/`
    - Add README.md in `app/vendor/` explaining how to obtain and place GMP/MPFR static libraries and CGAL/Boost headers
    - _Requirements: 3.2, 3.3, 7.4_

- [x] 5. Implement JNI bridge and native entry point
  - [x] 5.1 Create CgalComputeEngine Kotlin class with JNI declarations
    - Create `app/src/main/java/com/openscadviewer/engine/CgalComputeEngine.kt`
    - Implement `ComputeEngine` interface with `suspend fun compute()` using `withContext(Dispatchers.Default)` and `withTimeoutOrNull(60_000L)`
    - Declare `external fun nativeCompute(sceneJson: ByteArray): NativeResult` and `external fun nativeCancel(handle: Long)`
    - Lazy-load native library via `System.loadLibrary("cgal_engine")` in `isAvailable()`
    - Handle timeout by calling `nativeCancel` and returning TIMEOUT error
    - _Requirements: 1.3, 3.5, 4.1, 6.3_

  - [x] 5.2 Create NativeResult data class
    - Create `app/src/main/java/com/openscadviewer/engine/NativeResult.kt`
    - Define fields: `vertices: FloatArray?`, `normals: FloatArray?`, `colors: FloatArray?`, `errorCategory: String?`, `errorMessage: String?`
    - _Requirements: 4.5, 4.6_

  - [x] 5.3 Create native JNI entry point (cgal_engine.cpp)
    - Create `app/src/main/cpp/cgal_engine.cpp`
    - Implement `Java_com_openscadviewer_engine_CgalComputeEngine_nativeCompute` JNI function
    - Extract JSON byte array, parse with nlohmann/json, call cgal_compute, build NativeResult jobject
    - Implement `Java_com_openscadviewer_engine_CgalComputeEngine_nativeCancel` setting atomic cancel flag
    - Implement helper `build_native_result` and `build_error_result` functions for constructing JNI return objects
    - _Requirements: 4.1, 4.6_

  - [x] 5.4 Add nlohmann/json single-header library
    - Place `json.hpp` at `app/src/main/cpp/include/nlohmann/json.hpp`
    - _Requirements: 4.1_

- [x] 6. Implement CGAL scene graph builder and primitive construction
  - [x] 6.1 Create scene_builder.h/.cpp for JSON-to-CGAL primitive construction
    - Create `app/src/main/cpp/scene_builder.h` and `app/src/main/cpp/scene_builder.cpp`
    - Parse JSON nodes and construct CGAL `Nef_polyhedron_3` for primitives: cube (with center flag), sphere (tessellated), cylinder (with radius1/radius2 and center flag)
    - Handle LinearExtrude by extruding 2D profiles (circle, square, polygon) to Nef polyhedra
    - Apply transforms (translate, rotate in Z-Y-X order, scale) via CGAL `Aff_transformation_3`
    - Propagate color from nearest ancestor Color node, default (0.6, 0.7, 0.85, 1.0)
    - Skip unsupported node types and degenerate primitives (zero/negative dimensions)
    - Check cancel flag between operations
    - _Requirements: 4.2, 4.3, 4.4, 4.7, 4.8_

  - [x] 6.2 Write property test for degenerate primitives exclusion
    - **Property 5: Degenerate primitives excluded from computation**
    - **Validates: Requirements 4.8, 5.7**
    - Generate scene graphs mixing valid and degenerate primitives, verify degenerate ones don't appear in output and don't cause failures

- [x] 7. Implement CGAL CSG Boolean operations
  - [x] 7.1 Create cgal_compute.h/.cpp with CSG logic
    - Create `app/src/main/cpp/cgal_compute.h` and `app/src/main/cpp/cgal_compute.cpp`
    - Implement `cgal_compute(json, cancel_flag)` function that recursively processes the scene graph
    - Implement union: Boolean union of all children's Nef polyhedra
    - Implement difference: first child minus subsequent children in list order
    - Implement intersection: Boolean intersection of all children
    - Handle single-child CSG (return child unchanged) and empty children (return empty result)
    - Exclude degenerate operands (zero volume/non-manifold) from CSG operations
    - Check cancel flag before each CSG operation
    - _Requirements: 5.1, 5.2, 5.3, 5.5, 5.6, 5.7_

  - [x] 7.2 Create mesh_extractor.h/.cpp for Nef polyhedron to triangle mesh conversion
    - Create `app/src/main/cpp/mesh_extractor.h` and `app/src/main/cpp/mesh_extractor.cpp`
    - Convert final `Nef_polyhedron_3` to `Polyhedron_3` via `convert_nef_to_polyhedron()`
    - Triangulate faces using `CGAL::Polygon_mesh_processing::triangulate_faces()`
    - Extract vertex positions, compute per-face normals, assign per-vertex colors
    - Return flat float arrays in ComputeResult struct
    - _Requirements: 4.5, 5.4_

- [x] 8. Checkpoint - Verify native build compiles
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement engine selection UI
  - [x] 9.1 Add engine selection menu to toolbar overflow
    - Add menu XML resource or programmatic menu items in `MainActivity`
    - Add two mutually exclusive options: "Simple (Kotlin)" and "Advanced (CGAL)"
    - Show checkmark/radio indicator on the currently selected engine
    - Disable "Advanced (CGAL)" option with "(unavailable)" label if `isCgalAvailable()` returns false
    - Wire selection to `EngineManager.selectedType`
    - _Requirements: 2.1, 2.4, 2.5, 2.6_

  - [x] 9.2 Integrate EngineManager into MainActivity preview/render flow
    - Replace direct `MeshGenerator` usage in `generatePreview()` and `renderAndExportSTL()` with `EngineManager.currentEngine.compute()`
    - Convert `MeshResult` to renderer-compatible format for `SceneRenderer.setMeshData()`
    - Ensure the ComputeEngine interface is the sole entry point for mesh generation in the rendering layer
    - Add progress indicator and cancel button during CGAL computation
    - Retain last valid geometry on error
    - _Requirements: 1.4, 1.6, 6.1, 6.4, 6.5_

- [x] 10. Implement error handling and timeout/cancellation
  - [x] 10.1 Add computation error handling and user feedback
    - Display error messages via Snackbar when compute fails
    - Retain most recently rendered geometry on failure
    - On library load failure: disable CGAL option, switch to Kotlin, persist change, notify user
    - Handle timeout (60s): cancel native computation, release resources, show timeout message
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 10.2 Add progress indicator and cancel control for CGAL computation
    - Show indeterminate progress bar while CGAL engine is computing
    - Add cancel button that calls `CgalComputeEngine.cancel()` → `nativeCancel()`
    - On cancellation: terminate native computation, restore viewport to pre-computation state
    - _Requirements: 6.4, 6.5_

- [x] 11. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The vendor directory requires manually obtaining pre-compiled GMP/MPFR static libraries and CGAL/Boost headers before the native build will succeed
- Properties 6–9 (volume bounds, watertight mesh) require native CGAL on-device and are deferred to integration testing

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1"] },
    { "id": 2, "tasks": ["1.3", "1.4", "2.2", "2.3"] },
    { "id": 3, "tasks": ["2.4", "4.1", "4.3"] },
    { "id": 4, "tasks": ["4.2", "5.2", "5.4"] },
    { "id": 5, "tasks": ["5.1", "5.3"] },
    { "id": 6, "tasks": ["6.1"] },
    { "id": 7, "tasks": ["6.2", "7.1"] },
    { "id": 8, "tasks": ["7.2"] },
    { "id": 9, "tasks": ["9.1", "9.2"] },
    { "id": 10, "tasks": ["10.1", "10.2"] }
  ]
}
```
