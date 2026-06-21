# Requirements Document

## Introduction

This feature adds a CGAL-based compute engine to the OpenSCAD Viewer Android app as an alternative to the existing pure-Kotlin MeshGenerator. The current engine handles simple primitives but fails on complex CSG operations (difference, intersection) because it lacks proper Boolean mesh operations — it simply renders all children without computing actual geometry. CGAL (Computational Geometry Algorithms Library) provides robust, exact-arithmetic CSG operations that can handle arbitrary model complexity. The user will be able to switch between the lightweight Kotlin engine (fast for simple models) and the CGAL engine (correct for complex models) via a settings UI.

## Glossary

- **Kotlin_Engine**: The existing pure-Kotlin MeshGenerator that tessellates primitives and applies transforms, but approximates CSG operations by rendering all children without Boolean computation
- **CGAL_Engine**: The new native C++ compute engine built on the CGAL library that performs exact CSG Boolean operations (union, difference, intersection) via Nef polyhedra
- **Compute_Engine**: An abstraction representing either the Kotlin_Engine or the CGAL_Engine, selectable by the user
- **JNI_Bridge**: The Java Native Interface layer that marshals scene graph data from Kotlin to native C++ code and returns mesh results
- **Scene_Graph**: The parsed tree of OpenSCAD nodes (primitives, transforms, CSG operations) produced by the OpenSCADParser
- **Mesh_Result**: The output of a compute engine consisting of vertex positions, normals, and colors as float arrays
- **NDK_Toolchain**: The Android Native Development Kit build toolchain used to cross-compile C++ code for Android ABIs (arm64-v8a, armeabi-v7a, x86_64)
- **Engine_Selector**: The settings UI component that allows the user to choose which Compute_Engine to use
- **CSG_Operation**: A Constructive Solid Geometry operation (union, difference, intersection) that combines multiple solid shapes into a new shape

## Requirements

### Requirement 1: Compute Engine Abstraction

**User Story:** As a developer, I want a common interface for mesh generation engines, so that the app can switch between engines without modifying rendering or parsing code.

#### Acceptance Criteria

1. THE Compute_Engine SHALL define a common interface that accepts a Scene_Graph and returns a Mesh_Result containing vertex positions as x,y,z float triplets, normals as nx,ny,nz float triplets, and colors as r,g,b,a float quads
2. THE Kotlin_Engine SHALL implement the Compute_Engine interface using the existing MeshGenerator logic
3. THE CGAL_Engine SHALL implement the Compute_Engine interface using native CGAL operations via the JNI_Bridge
4. WHEN the user selects a Compute_Engine in the Engine_Selector, THE app SHALL use the selected engine for all mesh generation operations initiated after the selection is confirmed, without affecting any operation already in progress
5. IF the Compute_Engine receives an empty Scene_Graph or a Scene_Graph containing no geometry nodes, THEN THE Compute_Engine SHALL return an empty Mesh_Result with zero vertices
6. THE Compute_Engine interface SHALL be the sole entry point for mesh generation used by the rendering layer, so that no rendering code references Kotlin_Engine or CGAL_Engine directly

### Requirement 2: Engine Selection Settings

**User Story:** As a user, I want to choose between the simple Kotlin engine and the CGAL engine, so that I can use the fast engine for simple models and the accurate engine for complex ones.

#### Acceptance Criteria

1. THE Engine_Selector SHALL be accessible from the app toolbar overflow menu and SHALL present two mutually exclusive options: "Simple (Kotlin)" and "Advanced (CGAL)"
2. THE Engine_Selector SHALL persist the user's choice across app restarts using SharedPreferences
3. THE Engine_Selector SHALL default to "Simple (Kotlin)" on first launch
4. THE Engine_Selector SHALL visually indicate which engine is currently selected (e.g., radio button or checkmark on the active option)
5. WHEN the user changes the engine selection, THE app SHALL apply the new engine on the next preview or render operation without requiring an app restart
6. IF the CGAL native library is not available on the device, THEN THE Engine_Selector SHALL display the "Advanced (CGAL)" option as disabled with a label indicating it is unavailable

### Requirement 3: CGAL Native Library Integration

**User Story:** As a developer, I want CGAL and its dependencies compiled for Android, so that the app can perform exact CSG computations on device.

#### Acceptance Criteria

1. THE build system SHALL compile CGAL as a native C++ library using CMake and the NDK_Toolchain with C++17 as the minimum required standard
2. THE build system SHALL cross-compile GMP and MPFR as static libraries for each supported Android ABI
3. THE build system SHALL produce a single native shared library (.so) per ABI (arm64-v8a, armeabi-v7a, x86_64) that statically links CGAL, GMP, and MPFR
4. THE build system SHALL include CGAL and Boost headers as header-only dependencies in the CMake build configuration
5. WHEN the CGAL_Engine is selected by the user, THE app SHALL load the native shared library via System.loadLibrary at the point of first use
6. WHEN a Gradle assemble task completes, THE build system SHALL have produced the native shared library for each configured ABI and packaged it into the APK

### Requirement 4: JNI Bridge

**User Story:** As a developer, I want a JNI layer that translates scene graph data to CGAL operations and returns triangle meshes, so that the Kotlin layer can communicate with the native CGAL code.

#### Acceptance Criteria

1. THE JNI_Bridge SHALL accept the Scene_Graph from Kotlin as a JSON-encoded byte array where each node is represented by its type and parameters matching the SceneNode sealed class hierarchy
2. THE JNI_Bridge SHALL construct CGAL Nef polyhedra for each supported primitive node (cube, sphere, cylinder) and for LinearExtrude nodes that contain 2D profiles (circle, square, polygon)
3. THE JNI_Bridge SHALL apply CSG_Operations (union, difference, intersection) using CGAL exact Boolean operations on Nef polyhedra
4. THE JNI_Bridge SHALL apply geometric transforms (translate, rotate, scale) using CGAL affine transformations, applying rotation in Z-Y-X order consistent with the Kotlin_Engine
5. THE JNI_Bridge SHALL convert the final CGAL polyhedron to a triangle mesh and return vertex positions as a float array of x,y,z triplets, normals as a float array of nx,ny,nz triplets, and per-face colors as a float array of r,g,b,a values propagated from the nearest ancestor Color node or a default color of (0.6, 0.7, 0.85, 1.0) if no Color node is present
6. IF the JNI_Bridge encounters a CGAL computation error, THEN THE JNI_Bridge SHALL return an error descriptor to Kotlin containing a machine-readable error category (invalid_input, computation_failure, out_of_memory) and a human-readable message of at most 256 characters describing the failure
7. IF the JNI_Bridge receives a Scene_Graph containing a node type it does not support, THEN THE JNI_Bridge SHALL skip that subtree and continue processing the remaining nodes
8. IF the JNI_Bridge receives a primitive with degenerate dimensions (any size, radius, or height equal to zero or negative), THEN THE JNI_Bridge SHALL exclude that primitive from the computation and continue processing sibling nodes

### Requirement 5: CGAL CSG Operations

**User Story:** As a user, I want difference and intersection operations to produce correct geometry, so that complex OpenSCAD models render accurately.

#### Acceptance Criteria

1. WHEN a difference node is encountered, THE CGAL_Engine SHALL compute the exact Boolean difference of the first child minus all subsequent children in list order
2. WHEN a union node is encountered, THE CGAL_Engine SHALL compute the exact Boolean union of all children
3. WHEN an intersection node is encountered, THE CGAL_Engine SHALL compute the exact Boolean intersection of all children
4. THE CGAL_Engine SHALL produce watertight (manifold) triangle meshes for all CSG operations where the resulting geometry has non-zero volume
5. IF a CSG node contains fewer than 2 children, THEN THE CGAL_Engine SHALL return the single child mesh unchanged, or an empty Mesh_Result if the children list is empty
6. IF a CSG operation produces an empty geometry (zero volume), THEN THE CGAL_Engine SHALL return an empty Mesh_Result containing zero vertices and zero faces
7. IF any operand of a CSG operation is a degenerate solid (zero volume or non-manifold input), THEN THE CGAL_Engine SHALL exclude that operand from the computation and proceed with the remaining valid operands

### Requirement 6: Error Handling and Fallback

**User Story:** As a user, I want the app to handle CGAL failures gracefully, so that a computation error does not crash the application.

#### Acceptance Criteria

1. IF the CGAL_Engine fails to compute a mesh, THEN THE app SHALL display an error message indicating the nature of the failure and retain the most recently rendered geometry in the viewport
2. IF the CGAL native library fails to load, THEN THE app SHALL disable the CGAL_Engine option in the Engine_Selector, switch the active engine to the Kotlin_Engine, persist this change, and display a non-blocking notification to the user indicating that the CGAL_Engine is unavailable
3. IF a CGAL computation exceeds 60 seconds, THEN THE app SHALL cancel the computation, release native resources associated with that computation, and display a timeout message while retaining the most recently rendered geometry in the viewport
4. WHILE the CGAL_Engine is computing, THE app SHALL display an indeterminate progress indicator and provide a cancel control to the user
5. WHEN the user cancels a CGAL computation via the cancel control, THE app SHALL terminate the native computation, release associated native resources, and restore the viewport to the state it was in before the computation was initiated

### Requirement 7: Build System Configuration

**User Story:** As a developer, I want the Gradle build to integrate native compilation seamlessly, so that building the APK produces all necessary native libraries.

#### Acceptance Criteria

1. THE app build.gradle.kts SHALL configure an externalNativeBuild block specifying the CMake build file path relative to the app module directory and SHALL configure ndk abiFilters within defaultConfig to define which ABIs are built
2. THE CMakeLists.txt SHALL define a shared native library target that links against CGAL, GMP, and MPFR static libraries and includes Boost and CGAL headers as header-only dependencies
3. THE build system SHALL default to building for arm64-v8a, armeabi-v7a, and x86_64 ABIs, and SHALL allow restricting to a subset of these ABIs via the ndk abiFilters configuration in build.gradle.kts
4. THE build system SHALL place pre-compiled GMP and MPFR static libraries in a vendor directory within the project source tree, organized as one subdirectory per ABI (arm64-v8a, armeabi-v7a, x86_64), each containing the corresponding .a files
5. WHEN a developer runs a Gradle assemble task, THE build system SHALL produce a native shared library (.so) for each configured ABI and package it into the APK
