package com.openscadviewer.benchmark

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compares generated STL files against reference STL files.
 * Reads the triangle count from binary STL headers and reports differences.
 */
object StlComparator {

    data class ComparisonResult(
        val testName: String,
        val generatedTriangles: Int,
        val expectedTriangles: Int,
        val match: Boolean
    ) {
        val ratio: Double get() = if (expectedTriangles > 0) generatedTriangles.toDouble() / expectedTriangles else 0.0
        val percentDiff: Double get() = if (expectedTriangles > 0) ((generatedTriangles - expectedTriangles).toDouble() / expectedTriangles * 100.0) else 0.0
    }

    /**
     * Reads the triangle count from a binary STL file.
     * Binary STL: 80-byte header + 4-byte uint32 triangle count.
     */
    fun readTriangleCount(file: File): Int? {
        if (!file.exists() || file.length() < 84) return null
        return try {
            FileInputStream(file).use { fis ->
                fis.skip(80) // skip header
                val buf = ByteArray(4)
                fis.read(buf)
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getInt()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compares a generated STL against a reference STL file.
     * Returns null if no reference file exists for this test case.
     */
    fun compare(generatedFile: File, referenceFile: File, testName: String): ComparisonResult? {
        if (!referenceFile.exists()) return null
        val generated = readTriangleCount(generatedFile) ?: return null
        val expected = readTriangleCount(referenceFile) ?: return null
        return ComparisonResult(
            testName = testName,
            generatedTriangles = generated,
            expectedTriangles = expected,
            match = generated == expected
        )
    }
}
