# Requirements Document

## Introduction

This feature separates the Kotlin and CGAL compute engines from the monolithic Android `app` module into standalone Gradle modules. Each engine module can be consumed as a dependency by the existing Android application or executed as an independent JVM/CLI process, preparing the architecture for a future "server mode" on powerful hardware. Additionally, a comprehensive benchmark test suite of 50 OpenSCAD test cases is created to measure execution time across both engines, save STL output to disk for correctness verification against reference OpenSCAD output, and identify parsing/generation issues.

## Glossary

- **Compute_Engine**: An implementation of the `ComputeEngine` interface that transforms a parsed OpenSCAD scene graph into triangle mesh data
- **Kotlin_Engine**: The pure-Kotlin compute engine (`KotlinComputeEngine`) that generates meshes using `MeshGenerator`
- **CGAL_Engine**: The compute engine (`CgalComputeEngine`) that delegates to a native CGAL library via JNI for exact CSG Boolean operations
- **Engine_Module**: A standalone Gradle module containing one compute engine, its dependencies, and a CLI entry point
- **Benchmark_Suite**: A JUnit5 test suite containing 50 OpenSCAD test cases that compiles each to STL and measures execution time
- **Test_Case**: A single OpenSCAD code snippet paired with metadata (name, functional category) used as input to both engines
- **STL_Output**: A binary STL file produced by an engine for a given test case, saved to disk for external verification
- **Parser**: The `OpenSCADParser` class that transforms OpenSCAD source code into a `SceneNode` tree
- **Benchmark_Runner**: The component that orchestrates test case execution, timing measurement, and summary reporting
- **Timing_Summary**: A formatted report printed after all test cases complete, listing each test case name, engine, execution time, and pass/fail status

## Requirements

### Requirement 1: Engine Module Extraction

**User Story:** As a developer, I want the Kotlin and CGAL engines extracted into separate Gradle modules, so that each engine can be built and run independently of the Android application.

#### Acceptance Criteria

1. THE Engine_Module SHALL contain the `ComputeEngine` interface, `MeshResult`, `ProgressCallback`, `ComputeError`, `SceneNode`, and `OpenSCADParser` in a shared base module with no transitive dependency on `android.*` or `androidx.*` packages
2. THE Kotlin_Engine module SHALL depend on the shared base module and contain `KotlinComputeEngine`, `MeshGenerator`, and `STLExporter`
3. THE CGAL_Engine module SHALL depend on the shared base module and contain `CgalComputeEngine`, `SceneSerializer`, and the native library loading logic
4. WHEN the Android application is built, THE app module SHALL depend on both engine modules and all existing unit and instrumentation tests SHALL pass with no modifications
5. THE Engine_Module SHALL compile with JVM target 17 and have no dependency on Android SDK classes
6. WHEN `./gradlew :kotlin-engine:build` or `./gradlew :cgal-engine:build` is executed on a machine without the Android SDK, THE respective Engine_Module SHALL compile successfully and produce a JAR artifact

### Requirement 2: Standalone CLI Entry Point

**User Story:** As a developer, I want each engine module to include a CLI entry point, so that I can run the engine as a standalone JVM process without the Android application.

#### Acceptance Criteria

1. THE Kotlin_Engine module SHALL provide a `main` function that accepts two positional command-line arguments — an input OpenSCAD file path and an output STL file path — and writes binary STL output to the specified output path
2. THE CGAL_Engine module SHALL provide a `main` function that accepts two positional command-line arguments — an input OpenSCAD file path and an output STL file path — and writes binary STL output to the specified output path
3. IF the CLI entry point receives fewer than 2 positional arguments, THEN THE Engine_Module SHALL print a usage message to stderr and exit with status code 1
4. IF the input file path does not exist or is not a readable file, THEN THE Engine_Module SHALL print an error message to stderr indicating the path and reason, and exit with status code 1
5. WHEN the CLI entry point receives a valid OpenSCAD file, THE Engine_Module SHALL parse the file, compute the mesh, write binary STL to the output path, and exit with status code 0
6. THE Engine_Module SHALL support a `--timeout` flag that specifies the maximum computation time in seconds, accepting integer values between 1 and 3600, defaulting to 60 when not provided
7. IF computation exceeds the duration specified by the `--timeout` flag, THEN THE Engine_Module SHALL cancel the computation, print an error message to stderr indicating the timeout was exceeded, and exit with status code 2

### Requirement 3: Benchmark Test Case Definitions

**User Story:** As a developer, I want 50 OpenSCAD test cases covering distinct functional categories, so that I can systematically evaluate engine coverage and performance.

#### Acceptance Criteria

1. THE Benchmark_Suite SHALL contain exactly 50 Test_Case definitions
2. THE Benchmark_Suite SHALL cover the following categories with at least the indicated number of test cases: geometry primitives (8), transformations (8), linear extrusion (6), CSG operations (10), combined operations (8), variables and expressions (5), edge cases (5)
3. EACH Test_Case SHALL contain a unique name following the pattern `{category_prefix}_{descriptive_label}` using lowercase alphanumeric characters and underscores with a maximum length of 64 characters, a category identifier matching one of the seven defined categories, and a valid OpenSCAD code snippet that the Parser produces a SceneNode tree containing at least one geometry-producing node from
4. EACH category SHALL contain at least one test case with a single operation and nesting depth of 1, and at least one test case combining 3 or more operations with a nesting depth of at least 3
5. THE Benchmark_Suite SHALL store test case definitions as data accessible to both engine modules without duplication
6. EACH Test_Case code snippet SHALL NOT exceed 2048 characters in length

### Requirement 4: Benchmark Execution and Timing

**User Story:** As a developer, I want to run all 50 test cases against both engines and measure execution time, so that I can compare performance and detect regressions.

#### Acceptance Criteria

1. WHEN the Benchmark_Runner executes a Test_Case, THE Benchmark_Runner SHALL record the wall-clock execution time from parse start to mesh completion in integer milliseconds
2. WHEN a Test_Case exceeds 120 seconds of execution time, THE Benchmark_Runner SHALL cancel the computation and record the result as a timeout with the execution time recorded as 120000 milliseconds
3. THE Benchmark_Runner SHALL execute each Test_Case against both the Kotlin_Engine and the CGAL_Engine sequentially per test case, completing one engine's execution before starting the other, to ensure timing measurements are not affected by resource contention
4. IF a compute engine is unavailable because its module fails to load or its ComputeEngine implementation cannot be instantiated, THEN THE Benchmark_Runner SHALL skip that engine for all Test_Cases and record each result as skipped
5. IF a Test_Case produces a computation error that is neither a timeout nor a parse error, THEN THE Benchmark_Runner SHALL record the result as an error and continue executing the remaining Test_Cases
6. WHEN all Test_Cases have completed, THE Benchmark_Runner SHALL print a Timing_Summary to stdout containing one row per Test_Case per engine with columns: test case name, category, engine name, execution time in milliseconds, and status (success/timeout/error/skipped)
7. THE Timing_Summary SHALL include aggregate statistics per engine: total execution time in milliseconds, number of successes, number of timeouts, number of errors, and number of skipped

### Requirement 5: STL Output Preservation

**User Story:** As a developer, I want the benchmark suite to save STL files to disk for each test case, so that I can visually verify correct geometry generation using external OpenSCAD.

#### Acceptance Criteria

1. WHEN a Test_Case completes successfully, THE Benchmark_Runner SHALL write the resulting mesh as a binary STL file to the configured output directory, overwriting any existing file at the same path without prompting
2. THE STL file name SHALL follow the pattern `{category}_{test_name}_{engine_name}.stl` where each component is converted to lowercase with spaces and non-alphanumeric characters (except underscores) replaced by underscores, and consecutive underscores collapsed to a single underscore
3. IF the output directory does not exist, THEN THE Benchmark_Runner SHALL create the full directory path including any missing parent directories before writing the first STL file
4. WHEN a Test_Case results in a timeout or error, THE Benchmark_Runner SHALL not write an STL file for that test case and engine combination
5. THE STL output directory SHALL default to `build/benchmark-stl/` relative to the project root and be overridable via a system property `benchmark.stl.outputDir`
6. IF a file write operation fails due to I/O error, THEN THE Benchmark_Runner SHALL log a warning message indicating the test case name, engine name, and failure reason, and SHALL continue executing remaining test cases without aborting the suite

### Requirement 6: Benchmark Integration with Build System

**User Story:** As a developer, I want to run the benchmark suite using standard Gradle commands, so that it integrates naturally into my development workflow.

#### Acceptance Criteria

1. THE Benchmark_Suite SHALL be executable via `./gradlew :benchmark:test`
2. THE Benchmark_Suite SHALL use JUnit5 as the test framework with jqwik available as a test dependency for property-based test extensions
3. WHEN the benchmark Gradle task completes, THE Benchmark_Suite SHALL produce both the Timing_Summary on stdout (with Gradle test logging configured to show standard output) and STL files on disk
4. THE benchmark module SHALL declare implementation dependencies on both engine modules and the shared base module
5. THE Benchmark_Suite SHALL be runnable on any JVM 17 or later environment without requiring an Android device, emulator, or Android SDK classes on the classpath
6. THE benchmark module SHALL NOT be included in the default `check` lifecycle task, requiring explicit invocation via `./gradlew :benchmark:test`

### Requirement 7: Parser Correctness Verification Support

**User Story:** As a developer, I want the benchmark to identify parsing failures distinctly from compute failures, so that I can triage OpenSCAD code that is not correctly detected by the parser.

#### Acceptance Criteria

1. WHEN the Parser produces a SceneNode tree containing no geometry-producing nodes from a non-empty Test_Case snippet, THE Benchmark_Runner SHALL record the result with status "parse_error" separate from a "compute_error" or "timeout" status
2. WHEN a parse error occurs, THE Benchmark_Runner SHALL include the failing OpenSCAD snippet (truncated to 256 characters if longer) in the Timing_Summary error detail column for triage
3. THE Timing_Summary status column SHALL use exactly one of the following values for each result row: "success", "parse_error", "compute_error", "timeout", or "skipped"
4. THE Benchmark_Suite SHALL include at least 1 Test_Case per parser edge case category: comments, nested expressions, variable references, multiline code, and named parameters (minimum 5 total dedicated parser edge case tests)
5. IF a Test_Case results in a parse_error, THEN THE Benchmark_Runner SHALL record an execution time of 0 milliseconds and SHALL NOT pass the result to the Compute_Engine
