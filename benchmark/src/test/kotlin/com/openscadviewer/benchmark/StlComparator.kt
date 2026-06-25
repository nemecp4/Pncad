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
        val generatedFileSize: Long,
        val expectedFileSize: Long,
        val trianglesMatch: Boolean,
        val fileSizeMatch: Boolean
    ) {
        val triangleRatio: Double
            get() = if (expectedTriangles > 0) generatedTriangles.toDouble() / expectedTriangles else 0.0

        val trianglePercentDiff: Double
            get() = if (expectedTriangles > 0)
                ((generatedTriangles - expectedTriangles).toDouble() / expectedTriangles * 100.0)
            else 0.0

        val fileSizeRatio: Double
            get() = if (expectedFileSize > 0) generatedFileSize.toDouble() / expectedFileSize else 0.0

        val fileSizePercentDiff: Double
            get() = if (expectedFileSize > 0)
                ((generatedFileSize - expectedFileSize).toDouble() / expectedFileSize * 100.0)
            else 0.0

        fun withinTolerance(tolerance: Double = 0.15): Boolean {
            val triangleOk = expectedTriangles == 0 ||
                kotlin.math.abs(trianglePercentDiff) <= tolerance * 100.0
            val sizeOk = expectedFileSize == 0L ||
                kotlin.math.abs(fileSizePercentDiff) <= tolerance * 100.0
            return triangleOk && sizeOk
        }
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
     * Returns null if either file doesn't exist or is unreadable.
     */
    fun compare(generatedFile: File, referenceFile: File, testName: String, tolerance: Double = 0.15): ComparisonResult? {
        if (!referenceFile.exists()) return null
        if (!generatedFile.exists()) return null

        val genTriangles = readTriangleCount(generatedFile) ?: return null
        val expTriangles = readTriangleCount(referenceFile) ?: return null
        val genSize = generatedFile.length()
        val expSize = referenceFile.length()

        val trianglePercentDiff = if (expTriangles > 0)
            kotlin.math.abs((genTriangles - expTriangles).toDouble() / expTriangles * 100.0) else 0.0
        val sizePercentDiff = if (expSize > 0)
            kotlin.math.abs((genSize - expSize).toDouble() / expSize * 100.0) else 0.0

        return ComparisonResult(
            testName = testName,
            generatedTriangles = genTriangles,
            expectedTriangles = expTriangles,
            generatedFileSize = genSize,
            expectedFileSize = expSize,
            trianglesMatch = trianglePercentDiff <= tolerance * 100.0,
            fileSizeMatch = sizePercentDiff <= tolerance * 100.0
        )
    }
}
