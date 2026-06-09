package com.openscadviewer.parser

/**
 * Represents a node in the OpenSCAD scene graph.
 * Each node can be a primitive shape, a transformation, or a CSG operation.
 */
sealed class SceneNode {

    data class Cube(
        val sizeX: Double,
        val sizeY: Double,
        val sizeZ: Double,
        val center: Boolean
    ) : SceneNode()

    data class Sphere(
        val radius: Double,
        val segments: Int = 32
    ) : SceneNode()

    data class Cylinder(
        val height: Double,
        val radius1: Double,
        val radius2: Double,
        val center: Boolean,
        val segments: Int = 32
    ) : SceneNode()

    data class Circle(
        val radius: Double,
        val segments: Int = 32
    ) : SceneNode()

    data class Square(
        val sizeX: Double,
        val sizeY: Double,
        val center: Boolean
    ) : SceneNode()

    data class Polygon(
        val points: List<Pair<Double, Double>>
    ) : SceneNode()

    data class LinearExtrude(
        val height: Double,
        val child: SceneNode
    ) : SceneNode()

    data class Translate(
        val x: Double,
        val y: Double,
        val z: Double,
        val child: SceneNode
    ) : SceneNode()

    data class Rotate(
        val x: Double,
        val y: Double,
        val z: Double,
        val child: SceneNode
    ) : SceneNode()

    data class Scale(
        val x: Double,
        val y: Double,
        val z: Double,
        val child: SceneNode
    ) : SceneNode()

    data class Color(
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float,
        val child: SceneNode
    ) : SceneNode()

    data class Union(
        val children: List<SceneNode>
    ) : SceneNode()

    data class Difference(
        val children: List<SceneNode>
    ) : SceneNode()

    data class Intersection(
        val children: List<SceneNode>
    ) : SceneNode()

    data class Group(
        val children: List<SceneNode>
    ) : SceneNode()
}
