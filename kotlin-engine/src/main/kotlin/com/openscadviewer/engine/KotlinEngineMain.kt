package com.openscadviewer.engine

import com.openscadviewer.parser.OpenSCADParser
import com.openscadviewer.parser.containsGeometry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // 1. Parse CLI arguments
    val cliArgs = CliArgParser.parse(args).getOrElse { error ->
        System.err.println("Error: ${error.message}")
        exitProcess(1)
    }

    // 2. Validate input file exists and is readable
    val inputFile = File(cliArgs.inputPath)
    if (!inputFile.exists() || !inputFile.canRead()) {
        System.err.println("Error: Input file '${cliArgs.inputPath}' does not exist or is not readable")
        exitProcess(1)
    }

    // 3. Parse with OpenSCADParser
    val source = inputFile.readText()
    val parser = OpenSCADParser()
    val sceneNode = try {
        parser.parse(source)
    } catch (e: Exception) {
        System.err.println("Error: Failed to parse input file: ${e.message}")
        exitProcess(1)
    }

    if (!sceneNode.containsGeometry()) {
        System.err.println("Error: Input file contains no geometry")
        exitProcess(1)
    }

    // 4. Compute mesh with timeout
    val engine = KotlinComputeEngine()
    val meshResult = runBlocking {
        try {
            withTimeout(cliArgs.timeoutSeconds * 1000L) {
                engine.compute(sceneNode)
            }.getOrElse { error ->
                System.err.println("Error: Computation failed: ${error.message}")
                exitProcess(1)
            }
        } catch (e: TimeoutCancellationException) {
            System.err.println("Error: Computation timed out after ${cliArgs.timeoutSeconds} seconds")
            exitProcess(2)
        }
    }

    // 5. Write binary STL
    val outputFile = File(cliArgs.outputPath)
    outputFile.parentFile?.mkdirs()
    val exporter = STLExporter()
    exporter.export(meshResult, outputFile)

    exitProcess(0)
}
