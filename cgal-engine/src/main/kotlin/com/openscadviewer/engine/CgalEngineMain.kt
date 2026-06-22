package com.openscadviewer.engine

import com.openscadviewer.parser.OpenSCADParser
import com.openscadviewer.parser.containsGeometry
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlin.system.exitProcess

/**
 * CLI entry point for the CGAL compute engine.
 *
 * Usage: cgal-engine <input.scad> <output.stl> [--timeout <seconds>]
 * Exit codes: 0 = success, 1 = argument/file/parse error, 2 = timeout
 */
fun main(args: Array<String>) {
    val cliArgs = CliArgParser.parse(args).getOrElse { error ->
        System.err.println("Error: ${error.message}")
        exitProcess(1)
    }

    val inputFile = File(cliArgs.inputPath)
    if (!inputFile.exists() || !inputFile.isFile || !inputFile.canRead()) {
        System.err.println("Error: Input file '${cliArgs.inputPath}' does not exist or is not readable")
        exitProcess(1)
    }

    val source = inputFile.readText()
    val parser = OpenSCADParser()
    val scene = parser.parse(source)

    if (!scene.containsGeometry()) {
        System.err.println("Error: Input file contains no geometry-producing nodes")
        exitProcess(1)
    }

    val engine = CgalComputeEngine()
    if (!engine.isAvailable()) {
        System.err.println("Error: CGAL native library is not available")
        exitProcess(1)
    }

    val meshResult = try {
        runBlocking {
            withTimeout(cliArgs.timeoutSeconds * 1000L) {
                engine.compute(scene)
            }
        }
    } catch (e: TimeoutCancellationException) {
        System.err.println("Error: Computation timed out after ${cliArgs.timeoutSeconds} seconds")
        exitProcess(2)
    }

    meshResult.getOrElse { error ->
        System.err.println("Error: Computation failed: ${error.message}")
        exitProcess(1)
    }.let { mesh ->
        StlWriter.write(mesh, File(cliArgs.outputPath))
    }

    exitProcess(0)
}

/**
 * Minimal binary STL writer for the CGAL engine module.
 * Avoids dependency on kotlin-engine's STLExporter.
 */
internal object StlWriter {

    fun write(meshResult: MeshResult, file: File) {
        FileOutputStream(file).use { fos ->
            val triangleCount = meshResult.triangleCount
            val vertices = meshResult.vertices

            // 80-byte header
            val header = ByteArray(80)
            val headerText = "CGAL Engine - Binary STL"
            headerText.toByteArray().copyInto(header, 0, 0, minOf(headerText.length, 80))
            fos.write(header)

            // Triangle count (4 bytes, little-endian uint32)
            val countBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            countBuffer.putInt(triangleCount)
            fos.write(countBuffer.array())

            // Per triangle: normal (3 floats) + 3 vertices (9 floats) + attribute (2 bytes) = 50 bytes
            val triBuffer = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until triangleCount) {
                triBuffer.clear()

                val baseIdx = i * 9

                val v0x = vertices[baseIdx];     val v0y = vertices[baseIdx + 1]; val v0z = vertices[baseIdx + 2]
                val v1x = vertices[baseIdx + 3]; val v1y = vertices[baseIdx + 4]; val v1z = vertices[baseIdx + 5]
                val v2x = vertices[baseIdx + 6]; val v2y = vertices[baseIdx + 7]; val v2z = vertices[baseIdx + 8]

                // Face normal via cross product
                val e1x = v1x - v0x; val e1y = v1y - v0y; val e1z = v1z - v0z
                val e2x = v2x - v0x; val e2y = v2y - v0y; val e2z = v2z - v0z

                var nx = e1y * e2z - e1z * e2y
                var ny = e1z * e2x - e1x * e2z
                var nz = e1x * e2y - e1y * e2x
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                if (len > 0.0001f) { nx /= len; ny /= len; nz /= len }

                triBuffer.putFloat(nx)
                triBuffer.putFloat(ny)
                triBuffer.putFloat(nz)

                triBuffer.putFloat(v0x); triBuffer.putFloat(v0y); triBuffer.putFloat(v0z)
                triBuffer.putFloat(v1x); triBuffer.putFloat(v1y); triBuffer.putFloat(v1z)
                triBuffer.putFloat(v2x); triBuffer.putFloat(v2y); triBuffer.putFloat(v2z)

                // Attribute byte count (unused)
                triBuffer.putShort(0)

                fos.write(triBuffer.array())
            }
        }
    }
}
