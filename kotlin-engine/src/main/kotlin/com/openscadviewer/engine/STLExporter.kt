package com.openscadviewer.engine

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Exports [MeshResult] data to binary STL format.
 *
 * Binary STL structure:
 * - 80-byte header
 * - 4-byte triangle count (little-endian uint32)
 * - Per triangle (50 bytes each):
 *   - 12 bytes: face normal (3 × float32)
 *   - 36 bytes: vertices (3 × 3 × float32)
 *   - 2 bytes: attribute byte count (unused, set to 0)
 */
class STLExporter {

    /**
     * Export [meshResult] as binary STL to the given [file].
     */
    fun export(meshResult: MeshResult, file: File) {
        FileOutputStream(file).use { fos ->
            export(meshResult, fos)
        }
    }

    /**
     * Export [meshResult] as binary STL to the given file [outputPath].
     */
    fun export(meshResult: MeshResult, outputPath: String) {
        export(meshResult, File(outputPath))
    }

    /**
     * Export [meshResult] as binary STL to the given [outputStream].
     */
    fun export(meshResult: MeshResult, outputStream: OutputStream) {
        val triangleCount = meshResult.triangleCount
        val vertices = meshResult.vertices

        // Header (80 bytes)
        val header = ByteArray(80)
        val headerText = "OpenSCAD Viewer - Binary STL Export"
        headerText.toByteArray().copyInto(header, 0, 0, minOf(headerText.length, 80))
        outputStream.write(header)

        // Number of triangles (4 bytes, little-endian)
        val countBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        countBuffer.putInt(triangleCount)
        outputStream.write(countBuffer.array())

        // Each triangle: normal (3 floats) + 3 vertices (9 floats) + attribute (2 bytes) = 50 bytes
        val triBuffer = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until triangleCount) {
            triBuffer.clear()

            val baseIdx = i * 9 // 3 vertices × 3 components

            // Read vertex positions
            val v0x = vertices[baseIdx];     val v0y = vertices[baseIdx + 1]; val v0z = vertices[baseIdx + 2]
            val v1x = vertices[baseIdx + 3]; val v1y = vertices[baseIdx + 4]; val v1z = vertices[baseIdx + 5]
            val v2x = vertices[baseIdx + 6]; val v2y = vertices[baseIdx + 7]; val v2z = vertices[baseIdx + 8]

            // Compute face normal via cross product of edge vectors
            val e1x = v1x - v0x; val e1y = v1y - v0y; val e1z = v1z - v0z
            val e2x = v2x - v0x; val e2y = v2y - v0y; val e2z = v2z - v0z

            var nx = e1y * e2z - e1z * e2y
            var ny = e1z * e2x - e1x * e2z
            var nz = e1x * e2y - e1y * e2x
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 0.0001f) { nx /= len; ny /= len; nz /= len }

            // Write normal
            triBuffer.putFloat(nx)
            triBuffer.putFloat(ny)
            triBuffer.putFloat(nz)

            // Write 3 vertices
            triBuffer.putFloat(v0x); triBuffer.putFloat(v0y); triBuffer.putFloat(v0z)
            triBuffer.putFloat(v1x); triBuffer.putFloat(v1y); triBuffer.putFloat(v1z)
            triBuffer.putFloat(v2x); triBuffer.putFloat(v2y); triBuffer.putFloat(v2z)

            // Attribute byte count (unused, set to 0)
            triBuffer.putShort(0)

            outputStream.write(triBuffer.array())
        }
    }
}
