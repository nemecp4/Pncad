# Requirements Document

## Introduction

This feature extends the benchmark test runner to validate CGAL engine output by comparing generated STL files against reference STL files. The comparison checks two metrics — file size (in bytes) and triangle count — and fails the test if either metric deviates from the reference by more than 15%. The feature builds on the existing `StlComparator` and `BenchmarkRunner.compareWithReferences()` infrastructure.

## Glossary

- **StlComparator**: The object responsible for reading binary STL files and producing comparison metrics between a generated file and a reference file.
- **ComparisonResult**: A data class holding the comparison metrics (triangle counts, file sizes, match flags) for a single test case.
- **StlComparisonAssert**: A utility object that provides JUnit assertion methods to fail the test when comparison results exceed tolerance.
- **BenchmarkRunner**: The orchestrator that executes benchmark test cases and compares generated STL output against references.
- **Tolerance**: A fractional threshold (0.15 = 15%) representing the maximum acceptable relative difference between generated and expected values.
- **Triangle_Count**: The number of triangles stored in a binary STL file header at bytes 80–83 (little-endian uint32).
- **File_Size**: The size in bytes of an STL file as reported by the filesystem.
- **Reference_File**: A known-good STL file stored in `expected_results/` used as the baseline for comparison.
- **Generated_File**: An STL file produced by the CGAL engine during a benchmark run.

## Requirements

### Requirement 1: Read Triangle Count from Binary STL

**User Story:** As a benchmark developer, I want to read the triangle count from a binary STL file, so that I can compare generated geometry against reference geometry.

#### Acceptance Criteria

1. WHEN a valid binary STL file (≥ 84 bytes) is provided, THE StlComparator SHALL return the triangle count as a non-negative integer read from bytes 80–83 in little-endian format.
2. IF the file does not exist, THEN THE StlComparator SHALL return null.
3. IF the file is smaller than 84 bytes, THEN THE StlComparator SHALL return null.
4. IF an I/O error occurs while reading the file, THEN THE StlComparator SHALL return null.

### Requirement 2: Compare Generated STL Against Reference

**User Story:** As a benchmark developer, I want to compare a generated STL file against a reference STL file on both triangle count and file size, so that I can detect regressions in the CGAL engine output.

#### Acceptance Criteria

1. WHEN both the generated file and reference file exist and are valid, THE StlComparator SHALL return a ComparisonResult containing triangle counts and file sizes for both files.
2. IF the reference file does not exist, THEN THE StlComparator SHALL return null.
3. IF the generated file does not exist, THEN THE StlComparator SHALL return null.
4. WHEN computing percent difference for triangle count, THE StlComparator SHALL calculate it as (generatedTriangles − expectedTriangles) / expectedTriangles × 100.
5. WHEN computing percent difference for file size, THE StlComparator SHALL calculate it as (generatedFileSize − expectedFileSize) / expectedFileSize × 100.

### Requirement 3: Determine Whether Metrics Are Within Tolerance

**User Story:** As a benchmark developer, I want to determine whether comparison metrics fall within an acceptable tolerance, so that minor variations do not cause false test failures.

#### Acceptance Criteria

1. WHEN both triangle count percent difference and file size percent difference have absolute values ≤ tolerance × 100, THE ComparisonResult SHALL report withinTolerance as true.
2. WHEN either triangle count percent difference or file size percent difference has an absolute value > tolerance × 100, THE ComparisonResult SHALL report withinTolerance as false.
3. WHILE expectedTriangles equals zero, THE ComparisonResult SHALL treat the triangle metric as passing regardless of the generated value.
4. WHILE expectedFileSize equals zero, THE ComparisonResult SHALL treat the file size metric as passing regardless of the generated value.
5. WHEN the absolute percent difference equals exactly tolerance × 100, THE ComparisonResult SHALL report that metric as passing (inclusive boundary).

### Requirement 4: Assert All Comparisons Within Tolerance

**User Story:** As a benchmark developer, I want a single assertion that checks all STL comparison results against the 15% threshold, so that the test fails clearly when any comparison exceeds tolerance.

#### Acceptance Criteria

1. WHEN all ComparisonResult entries satisfy withinTolerance, THE StlComparisonAssert SHALL return normally without throwing an exception.
2. WHEN any ComparisonResult entry does not satisfy withinTolerance, THE StlComparisonAssert SHALL throw an AssertionError.
3. WHEN multiple comparisons exceed tolerance, THE StlComparisonAssert SHALL include all failing test names and their metric details in the AssertionError message.
4. WHEN a comparison fails on the triangle metric, THE StlComparisonAssert SHALL include the generated count, expected count, and percent difference in the error message.
5. WHEN a comparison fails on the file size metric, THE StlComparisonAssert SHALL include the generated size, expected size, and percent difference in the error message.

### Requirement 5: Integrate STL Comparison into Benchmark Test

**User Story:** As a benchmark developer, I want the benchmark test to automatically compare generated STL files against references and fail on excessive deviation, so that regressions are caught during CI.

#### Acceptance Criteria

1. WHEN benchmark execution completes for custom test cases, THE BenchmarkRunner SHALL compare each generated STL file against the corresponding reference file.
2. WHEN comparisons are collected, THE BenchmarkTest SHALL call StlComparisonAssert.assertAllWithinTolerance with a tolerance of 0.15.
3. IF no reference file exists for a custom test case, THEN THE BenchmarkRunner SHALL skip that comparison without failing the test.
4. WHILE the comparison list is empty, THE BenchmarkTest SHALL skip the assertion step without failing.

### Requirement 6: Graceful Handling of Missing and Degenerate Files

**User Story:** As a benchmark developer, I want the comparison to handle missing files and zero-value references gracefully, so that the test suite remains robust in incomplete environments.

#### Acceptance Criteria

1. IF the reference STL file is missing, THEN THE StlComparator SHALL return null and the comparison SHALL be skipped.
2. IF the generated STL file is missing or corrupt (< 84 bytes), THEN THE StlComparator SHALL return null and the comparison SHALL be skipped.
3. WHILE expectedTriangles equals zero, THE ComparisonResult SHALL not trigger a test failure for the triangle metric.
4. WHILE expectedFileSize equals zero, THE ComparisonResult SHALL not trigger a test failure for the file size metric.
