package com.openscadviewer.benchmark

import com.openscadviewer.parser.OpenSCADParser
import com.openscadviewer.engine.SceneSerializer
import org.junit.jupiter.api.Test
import java.io.File

class PrintJsonTest {
    @Test
    fun printControllRoseJson() {
        val parser = OpenSCADParser()
        val code = File("src/test/resources/testcases/custom/controll_rose.scad").readText()
        val scene = parser.parse(code)
        val json = SceneSerializer.toJsonForCgal(scene)
        
        // Find all "segments" values
        val regex = Regex("\"segments\":(\\d+)")
        val matches = regex.findAll(json)
        for (match in matches) {
            println("segments = ${match.groupValues[1]}")
        }
        println("\nJSON length: ${json.length}")
        println("\nFirst 3000 chars:")
        println(json.take(3000))
    }
}
