package com.openscadviewer.engine

import com.openscadviewer.parser.OpenSCADParser
import com.openscadviewer.parser.SceneNode
import org.junit.jupiter.api.Test

class SunParseDebugTest {
    @Test
    fun `parse sun scad and print tree`() {
        val code = """
            sun_radius   = 40;
            flare_count  = 6;
            ${'$'}fn = 120;
            module flare() {
                cylinder(d=10, h=10);
            }
            union() {
                sphere(r = sun_radius);
                for (i = [0 : flare_count - 1]) {
                    rotate(i * 360 / flare_count)
                        flare();
                }
            }
        """.trimIndent()

        val parser = OpenSCADParser()
        val scene = parser.parse(code)
        printNode(scene, "")
    }

    private fun printNode(node: SceneNode, indent: String) {
        when (node) {
            is SceneNode.Group -> { println("${indent}Group(${node.children.size})"); node.children.forEach { printNode(it, "$indent  ") } }
            is SceneNode.Union -> { println("${indent}Union(${node.children.size})"); node.children.forEach { printNode(it, "$indent  ") } }
            is SceneNode.Sphere -> println("${indent}Sphere(r=${node.radius}, seg=${node.segments})")
            is SceneNode.Cylinder -> println("${indent}Cylinder(h=${node.height}, r1=${node.radius1}, r2=${node.radius2}, seg=${node.segments})")
            is SceneNode.Circle -> println("${indent}Circle(r=${node.radius}, seg=${node.segments})")
            is SceneNode.Rotate -> { println("${indent}Rotate(${node.x}, ${node.y}, ${node.z})"); printNode(node.child, "$indent  ") }
            is SceneNode.Translate -> { println("${indent}Translate(${node.x}, ${node.y}, ${node.z})"); printNode(node.child, "$indent  ") }
            is SceneNode.Cube -> println("${indent}Cube(${node.sizeX}, ${node.sizeY}, ${node.sizeZ})")
            else -> println("${indent}${node::class.simpleName}")
        }
    }
}
