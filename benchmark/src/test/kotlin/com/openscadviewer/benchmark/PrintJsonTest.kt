package com.openscadviewer.benchmark

import com.openscadviewer.parser.OpenSCADParser
import com.openscadviewer.parser.SceneNode
import com.openscadviewer.engine.SceneSerializer
import org.junit.jupiter.api.Test
import java.io.File

class PrintJsonTest {
    @Test
    fun printTowerStructure() {
        val parser = OpenSCADParser()
        val code = File("src/test/resources/testcases/custom/tower.scad").readText()
        val scene = parser.parse(code)
        
        // Count cubes in the tree
        val cubeCount = countNodes(scene, "Cube")
        val rotateCount = countNodes(scene, "Rotate")
        println("Total Cubes in parsed tree: $cubeCount")
        println("Total Rotates in parsed tree: $rotateCount")
        
        // Print the crenellation section — find the translate(0,0,40) node
        printCrenellations(scene, 0)
        
        // Also print JSON snippet for the difference children
        val json = SceneSerializer.toJsonForCgal(scene)
        println("\nJSON length: ${json.length}")
        // Find cubes in JSON
        val cubeMatches = Regex("\"type\":\"cube\"").findAll(json)
        println("Cubes in CGAL JSON: ${cubeMatches.count()}")
    }
    
    private fun countNodes(node: SceneNode, typeName: String): Int {
        val self = if (node::class.simpleName == typeName) 1 else 0
        return self + when (node) {
            is SceneNode.Translate -> countNodes(node.child, typeName)
            is SceneNode.Rotate -> countNodes(node.child, typeName)
            is SceneNode.Scale -> countNodes(node.child, typeName)
            is SceneNode.Color -> countNodes(node.child, typeName)
            is SceneNode.LinearExtrude -> countNodes(node.child, typeName)
            is SceneNode.Union -> node.children.sumOf { countNodes(it, typeName) }
            is SceneNode.Difference -> node.children.sumOf { countNodes(it, typeName) }
            is SceneNode.Intersection -> node.children.sumOf { countNodes(it, typeName) }
            is SceneNode.Group -> node.children.sumOf { countNodes(it, typeName) }
            else -> 0
        }
    }
    
    private fun printCrenellations(node: SceneNode, depth: Int) {
        val indent = "  ".repeat(depth)
        when (node) {
            is SceneNode.Cube -> println("${indent}Cube(${node.sizeX}, ${node.sizeY}, ${node.sizeZ}, center=${node.center})")
            is SceneNode.Cylinder -> {} // skip
            is SceneNode.Translate -> {
                if (node.z == 40.0) {
                    println("${indent}Translate(${node.x}, ${node.y}, ${node.z}):")
                    printCrenellations(node.child, depth + 1)
                } else {
                    printCrenellations(node.child, depth)
                }
            }
            is SceneNode.Rotate -> {
                println("${indent}Rotate(${node.x}, ${node.y}, ${node.z}):")
                printCrenellations(node.child, depth + 1)
            }
            is SceneNode.Union -> node.children.forEach { printCrenellations(it, depth) }
            is SceneNode.Difference -> {
                println("${indent}Difference(${node.children.size} children):")
                node.children.forEach { printCrenellations(it, depth + 1) }
            }
            is SceneNode.Group -> node.children.forEach { printCrenellations(it, depth) }
            else -> {}
        }
    }
}
