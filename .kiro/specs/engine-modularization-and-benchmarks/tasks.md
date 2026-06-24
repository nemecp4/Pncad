# Implementation Plan: Engine Modularization and Benchmarks

## Overview

Extract compute engines and shared types from the monolithic `app` module into four standalone Gradle modules (`shared-base`, `kotlin-engine`, `cgal-engine`, `benchmark`), add CLI entry points to each engine, and build a 50-case benchmark suite with timing and STL output. All new modules target JVM 17 with no Android SDK dependency.

## Tasks

- [x] 1. Set up Gradle multi-module structure and shared-base module
  - [x] 1.1 Update settings.gradle.kts to include new modules
    - Add `:shared-base`, `:kotlin-engine`, `:cgal-engine`, and `:benchmark` to the `include()` call in `settings.gradle.kts`
    - _Requirements: 1.1, 1.5, 6.1_

  - [x] 1.2 Create shared-base module with build configuration
    - Create `shared-base/build.gradle.kts` with `kotlin("jvm")` plugin, JVM 17 target, and `kotlinx-coroutines-core` dependency (JVM variant, not `-android`)
    - Create directory structure `shared-base/src/main/kotlin/com/openscadviewer/engine/` and `shared-base/src/main/kotlin/com/openscadviewer/parser/`
    - _Requirements: 1.1, 1.5_

  - [x] 1.3 Move ComputeEngine interface and data types to shared-base
    - Move/create `ComputeEngine` interface, `MeshResult` data class, `ProgressCallback` functional interface, `ComputeError` data class, `ComputeException`, and `ErrorCategory` enum into `shared-base/src/main/kotlin/com/openscadviewer/engine/`
    - Ensure no transitive dependency on `android.*` or `androidx.*` packages
    - _Requirements: 1.1, 1.5_

  - [x] 1.4 Move SceneNode and OpenSCADParser to shared-base
    - Move/create `SceneNode` sealed class and `OpenSCADParser` into `shared-base/src/main/kotlin/com/openscadviewer/parser/`
    - Add the `containsGeometry()` extension function on `SceneNode`
    - _Requirements: 1.1, 1.5_

  - [x] 1.5 Create CliArgParser utility in shared-base
    - Implement `CliArgParser` object in `shared-base/src/main/kotlin/com/openscadviewer/engine/CliArgParser.kt`
    - Parse two positional arguments (input path, output path) and optional `--timeout <seconds>` flag (default 60, range 1–3600)
    - Return `Result<CliArgs>` with error messages for invalid input
    - _Requirements: 2.1, 2.2, 2.3, 2.6_

  - [x] 1.6 Write property tests for CliArgParser
    - **Property 1: CLI argument validation**
    - **Property 2: Timeout flag parsing**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.6**
    - Create `shared-base/src/test/kotlin/com/openscadviewer/engine/CliArgParserPropertyTest.kt` using jqwik
    - Test: fewer than 2 positional args → failure; non-existent path detection; timeout values in 1–3600 accepted; values outside range rejected

- [x] 2. Checkpoint - Verify shared-base module compiles
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Create kotlin-engine module
  - [x] 3.1 Create kotlin-engine build configuration
    - Create `kotlin-engine/build.gradle.kts` with `kotlin("jvm")` plugin, JVM 17 target, dependency on `:shared-base`
    - Configure `application` plugin with main class `com.openscadviewer.engine.KotlinEngineMainKt`
    - _Requirements: 1.2, 1.5, 1.6_

  - [x] 3.2 Move KotlinComputeEngine and MeshGenerator to kotlin-engine
    - Move/create `KotlinComputeEngine` and `MeshGenerator` into `kotlin-engine/src/main/kotlin/com/openscadviewer/engine/`
    - `KotlinComputeEngine` implements `ComputeEngine` interface from shared-base
    - _Requirements: 1.2_

  - [x] 3.3 Move STLExporter to kotlin-engine
    - Move/create `STLExporter` into `kotlin-engine/src/main/kotlin/com/openscadviewer/engine/`
    - Writes `MeshResult` to binary STL format
    - _Requirements: 1.2, 2.1, 2.5_

  - [x] 3.4 Implement KotlinEngineMain CLI entry point
    - Create `kotlin-engine/src/main/kotlin/com/openscadviewer/engine/KotlinEngineMain.kt`
    - Use `CliArgParser.parse(args)` for argument handling
    - Validate input file exists and is readable (exit 1 if not)
    - Parse with `OpenSCADParser`, compute with `KotlinComputeEngine` using coroutine timeout, write STL
    - Exit codes: 0 = success, 1 = argument/file/parse error, 2 = timeout
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 2.6, 2.7_

- [x] 4. Create cgal-engine module
  - [x] 4.1 Create cgal-engine build configuration
    - Create `cgal-engine/build.gradle.kts` with `kotlin("jvm")` plugin, JVM 17 target, dependency on `:shared-base`
    - Configure `application` plugin with main class `com.openscadviewer.engine.CgalEngineMainKt`
    - _Requirements: 1.3, 1.5, 1.6_

  - [x] 4.2 Move CgalComputeEngine, SceneSerializer, and NativeResult to cgal-engine
    - Move/create `CgalComputeEngine`, `SceneSerializer`, and `NativeResult` into `cgal-engine/src/main/kotlin/com/openscadviewer/engine/`
    - `CgalComputeEngine` implements `ComputeEngine`, includes native library loading logic and `isAvailable()` check
    - _Requirements: 1.3_

  - [x] 4.3 Implement CgalEngineMain CLI entry point
    - Create `cgal-engine/src/main/kotlin/com/openscadviewer/engine/CgalEngineMain.kt`
    - Same CLI contract as KotlinEngineMain: use `CliArgParser`, validate file, parse, compute with timeout, write STL
    - Exit codes: 0 = success, 1 = argument/file/parse error, 2 = timeout
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

- [x] 5. Update app module dependencies
  - [x] 5.1 Update app/build.gradle.kts to depend on engine modules
    - Add `implementation(project(":kotlin-engine"))` and `implementation(project(":cgal-engine"))` to app dependencies
    - Remove classes that were moved to shared-base, kotlin-engine, and cgal-engine from the app module source
    - Update import statements in remaining app code to reference the new module packages
    - Verify all existing unit and instrumentation tests pass without modification
    - _Requirements: 1.4_

- [x] 6. Checkpoint - Verify multi-module build compiles
  - Ensure `./gradlew :shared-base:build`, `./gradlew :kotlin-engine:build`, `./gradlew :cgal-engine:build`, and `./gradlew :app:build` all succeed. Ensure all tests pass, ask the user if questions arise.

- [x] 7. Create benchmark module structure and test case registry
  - [x] 7.1 Create benchmark module build configuration
    - Create `benchmark/build.gradle.kts` with `kotlin("jvm")` plugin, JVM 17 target
    - Add dependencies on `:shared-base`, `:kotlin-engine`, `:cgal-engine`
    - Add test dependencies: JUnit5, jqwik 1.8.4, kotlinx-coroutines-test
    - Configure `useJUnitPlatform()` and `showStandardStreams = true` in test logging
    - Exclude benchmark from default `check` lifecycle task
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 7.2 Create TestCase data class and TestCaseRegistry with 50 test cases
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/TestCase.kt` with validation in `init` block (name pattern, length, category, code length)
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/TestCaseRegistry.kt` as an `object` holding all 50 test cases
    - Categories and minimum counts: geometry_primitives (8), transformations (8), linear_extrusion (6), csg_operations (10), combined_operations (8), variables_expressions (5), edge_cases (5)
    - Each category must have at least one single-operation test (depth 1) and one combined test (3+ operations, depth ≥ 3)
    - Include at least 5 parser edge case tests: comments, nested expressions, variable references, multiline code, named parameters
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 7.4_

  - [x] 7.3 Write property test for test case structure validity
    - **Property 3: Test case structure validity**
    - **Validates: Requirements 3.3, 3.6**
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/TestCaseRegistryTest.kt`
    - Verify all 50 cases match naming pattern, have valid categories, code ≤ 2048 chars, and parse to SceneNode trees with geometry

- [x] 8. Implement BenchmarkResult and TimingSummaryFormatter
  - [x] 8.1 Create BenchmarkResult and ResultStatus
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/BenchmarkResult.kt` with `BenchmarkResult` data class and `ResultStatus` enum (SUCCESS, PARSE_ERROR, COMPUTE_ERROR, TIMEOUT, SKIPPED)
    - _Requirements: 4.1, 7.3_

  - [x] 8.2 Implement TimingSummaryFormatter
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/TimingSummaryFormatter.kt`
    - Format a table with columns: test case name, category, engine name, execution time (ms), status
    - Include error detail column (truncated snippet for parse errors)
    - Compute per-engine aggregates: total time, success count, timeout count, error count, skipped count
    - Print to stdout
    - _Requirements: 4.6, 4.7, 7.2_

  - [x] 8.3 Write property tests for TimingSummaryFormatter
    - **Property 7: Timing summary correctness**
    - **Property 12: Result status enum constraint**
    - **Validates: Requirements 4.6, 4.7, 7.3**
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/TimingSummaryPropertyTest.kt`
    - Verify: one row per result, all columns present, aggregate totals equal sum of individual results

- [x] 9. Implement StlOutputWriter
  - [x] 9.1 Create StlOutputWriter with filename sanitization
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/StlOutputWriter.kt`
    - Implement `StlFileNamer.generateFilename()`: lowercase, replace non-alphanumeric (except underscore/dot) with underscore, collapse consecutive underscores
    - Write binary STL files to configurable output directory (default: `build/benchmark-stl/`, overridable via system property `benchmark.stl.outputDir`)
    - Create output directory (including parents) if it does not exist
    - On I/O error: log warning, continue without aborting
    - _Requirements: 5.1, 5.2, 5.3, 5.5, 5.6_

  - [x] 9.2 Write property tests for STL filename sanitization
    - **Property 8: STL filename sanitization**
    - **Validates: Requirements 5.2**
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/StlFileNamerPropertyTest.kt`
    - Verify: output is lowercase, only `[a-z0-9_.]` chars, no consecutive underscores, ends with `.stl`

- [x] 10. Implement BenchmarkRunner
  - [x] 10.1 Implement BenchmarkRunner core logic
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/BenchmarkRunner.kt`
    - Accept list of engines (with availability check), test cases, STL output dir, timeout (default 120000ms)
    - Execution flow per test case: parse → check geometry → run engines sequentially → record results
    - Parse error detection: if SceneNode tree has no geometry-producing nodes → status PARSE_ERROR, time = 0, skip engine calls
    - Timeout handling: cancel computation at 120s, record TIMEOUT with time = 120000
    - Error handling: record COMPUTE_ERROR, continue to next test case
    - Unavailable engine: record SKIPPED for all test cases
    - On success: delegate to StlOutputWriter; on non-success: do not write STL
    - Truncate error snippets to 256 characters for parse errors
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.4, 7.1, 7.2, 7.5_

  - [x] 10.2 Write property tests for BenchmarkRunner
    - **Property 4: Timing measurement invariant**
    - **Property 5: Unavailable engine produces all-skipped results**
    - **Property 6: Error recording and continuation**
    - **Property 9: No STL output for non-success results**
    - **Property 10: Parse error detection and handling**
    - **Property 11: Error snippet truncation**
    - **Validates: Requirements 4.1, 4.2, 4.4, 4.5, 5.4, 7.1, 7.2, 7.5**
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/BenchmarkRunnerTest.kt`

- [x] 11. Implement BenchmarkTest JUnit5 entry point
  - [x] 11.1 Create BenchmarkTest class
    - Create `benchmark/src/test/kotlin/com/openscadviewer/benchmark/BenchmarkTest.kt`
    - JUnit5 test class that instantiates `BenchmarkRunner` with both engines and all 50 test cases from `TestCaseRegistry`
    - Invokes `runner.run()`, passes results to `TimingSummaryFormatter` for printing
    - Asserts that runner produces exactly `50 × number_of_available_engines` results
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 12. Checkpoint - Verify benchmark module builds and runs
  - Ensure `./gradlew :benchmark:test` executes successfully, prints the timing summary to stdout, and writes STL files to `build/benchmark-stl/`. Ensure all tests pass, ask the user if questions arise.

- [x] 13. Final integration verification
  - [x] 13.1 Verify full project builds and all modules are independent
    - Run `./gradlew build` to confirm the entire project compiles
    - Verify `:shared-base:build` and `:kotlin-engine:build` and `:cgal-engine:build` succeed without Android SDK on classpath
    - Verify `:benchmark:test` is NOT part of the default `check` task
    - Verify existing app unit tests still pass via `./gradlew :app:test`
    - _Requirements: 1.4, 1.5, 1.6, 6.5, 6.6_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (12 properties total)
- Unit tests validate specific examples and edge cases
- The design uses Kotlin as the implementation language throughout all modules
- All new modules use `kotlin("jvm")` plugin with JVM 17 target — no Android SDK dependency

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "1.4"] },
    { "id": 3, "tasks": ["1.5"] },
    { "id": 4, "tasks": ["1.6", "3.1", "4.1"] },
    { "id": 5, "tasks": ["3.2", "3.3", "4.2"] },
    { "id": 6, "tasks": ["3.4", "4.3", "5.1"] },
    { "id": 7, "tasks": ["7.1"] },
    { "id": 8, "tasks": ["7.2", "8.1"] },
    { "id": 9, "tasks": ["7.3", "8.2", "9.1"] },
    { "id": 10, "tasks": ["8.3", "9.2", "10.1"] },
    { "id": 11, "tasks": ["10.2", "11.1"] },
    { "id": 12, "tasks": ["13.1"] }
  ]
}
```
