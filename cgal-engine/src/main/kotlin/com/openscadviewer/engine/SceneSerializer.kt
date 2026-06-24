package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes a SceneNode tree to JSON for transport across the JNI boundary.
 * Each node is represented as a JSONObject with a "type" field and type-specific parameters.
 */
object SceneSerializer {
    fun toJson(node: SceneNode): String {
        return nodeToJson(node).toString()
    }

    /**
     * Serialize with CGAL-specific preprocessing (expands linear_extrude compound children).
     * Used by CgalComputeEngine before sending to native code.
     */
    fun toJsonForCgal(node: SceneNode): String {
        val preprocessed = preprocessForCgal(node)
        return nodeToJson(preprocessed).toString()
    }

    /**
     * Pre-process the scene tree to handle cases CGAL can't process directly:
     * - LinearExtrude(Union/Group/Difference([children])) → Union([LinearExtrude(child1), ...])
     * - Transforms inside LinearExtrude → moved outside: Rotate(LinearExtrude(primitive))
     */
    private fun preprocessForCgal(node: SceneNode): SceneNode {
        val MAX_SEGMENTS = 48
        return when (node) {
            is SceneNode.Sphere -> SceneNode.Sphere(node.radius, node.segments.coerceAtMost(MAX_SEGMENTS))
            is SceneNode.Cylinder -> SceneNode.Cylinder(node.height, node.radius1, node.radius2, node.center, node.segments.coerceAtMost(MAX_SEGMENTS))
            is SceneNode.Circle -> SceneNode.Circle(node.radius, node.segments.coerceAtMost(MAX_SEGMENTS))
            is SceneNode.LinearExtrude -> {
                val processedChild = preprocessForCgal(node.child)
                expandLinearExtrude(node.height, processedChild)
            }
            is SceneNode.Union -> SceneNode.Union(node.children.map { preprocessForCgal(it) })
            is SceneNode.Difference -> SceneNode.Difference(node.children.map { preprocessForCgal(it) })
            is SceneNode.Intersection -> SceneNode.Intersection(node.children.map { preprocessForCgal(it) })
            is SceneNode.Group -> SceneNode.Group(node.children.map { preprocessForCgal(it) })
            is SceneNode.Translate -> SceneNode.Translate(node.x, node.y, node.z, preprocessForCgal(node.child))
            is SceneNode.Rotate -> SceneNode.Rotate(node.x, node.y, node.z, preprocessForCgal(node.child))
            is SceneNode.Scale -> SceneNode.Scale(node.x, node.y, node.z, preprocessForCgal(node.child))
            is SceneNode.Color -> SceneNode.Color(node.r, node.g, node.b, node.a, preprocessForCgal(node.child))
            else -> node
        }
    }

    /**
     * Expand a linear_extrude with a compound child into individual extrusions.
     * LinearExtrude(Union([A, B, C])) → Union([LinearExtrude(A), LinearExtrude(B), LinearExtrude(C)])
     * LinearExtrude(Rotate(z, Polygon)) → Rotate(z, LinearExtrude(Polygon))
     */
    private fun expandLinearExtrude(height: Double, child: SceneNode): SceneNode {
        return when (child) {
            is SceneNode.Circle, is SceneNode.Square, is SceneNode.Polygon ->
                SceneNode.LinearExtrude(height, child)
            is SceneNode.Union ->
                SceneNode.Union(child.children.map { expandLinearExtrude(height, it) })
            is SceneNode.Group ->
                SceneNode.Union(child.children.map { expandLinearExtrude(height, it) })
            is SceneNode.Difference ->
                SceneNode.Difference(child.children.map { expandLinearExtrude(height, it) })
            is SceneNode.Intersection ->
                SceneNode.Intersection(child.children.map { expandLinearExtrude(height, it) })
            is SceneNode.Translate ->
                SceneNode.Translate(child.x, child.y, child.z, expandLinearExtrude(height, child.child))
            is SceneNode.Rotate ->
                SceneNode.Rotate(child.x, child.y, child.z, expandLinearExtrude(height, child.child))
            is SceneNode.Scale ->
                SceneNode.Scale(child.x, child.y, child.z, expandLinearExtrude(height, child.child))
            is SceneNode.Color ->
                SceneNode.Color(child.r, child.g, child.b, child.a, expandLinearExtrude(height, child.child))
            else -> SceneNode.LinearExtrude(height, child)
        }
    }

    private fun nodeToJson(node: SceneNode): JSONObject {
        val obj = JSONObject()
        when (node) {
            is SceneNode.Cube -> {
                obj.put("type", "cube")
                obj.put("sizeX", node.sizeX)
                obj.put("sizeY", node.sizeY)
                obj.put("sizeZ", node.sizeZ)
                obj.put("center", node.center)
            }
            is SceneNode.Sphere -> {
                obj.put("type", "sphere")
                obj.put("radius", node.radius)
                obj.put("segments", node.segments)
            }
            is SceneNode.Cylinder -> {
                obj.put("type", "cylinder")
                obj.put("height", node.height)
                obj.put("radius1", node.radius1)
                obj.put("radius2", node.radius2)
                obj.put("center", node.center)
                obj.put("segments", node.segments)
            }
            is SceneNode.Circle -> {
                obj.put("type", "circle")
                obj.put("radius", node.radius)
                obj.put("segments", node.segments)
            }
            is SceneNode.Square -> {
                obj.put("type", "square")
                obj.put("sizeX", node.sizeX)
                obj.put("sizeY", node.sizeY)
                obj.put("center", node.center)
            }
            is SceneNode.Polygon -> {
                obj.put("type", "polygon")
                val pts = JSONArray()
                for (p in node.points) {
                    val pt = JSONArray()
                    pt.put(p.first)
                    pt.put(p.second)
                    pts.put(pt)
                }
                obj.put("points", pts)
            }
            is SceneNode.LinearExtrude -> {
                obj.put("type", "linear_extrude")
                obj.put("height", node.height)
                obj.put("child", nodeToJson(node.child))
            }
            is SceneNode.Translate -> {
                obj.put("type", "translate")
                obj.put("x", node.x)
                obj.put("y", node.y)
                obj.put("z", node.z)
                obj.put("child", nodeToJson(node.child))
            }
            is SceneNode.Rotate -> {
                obj.put("type", "rotate")
                obj.put("x", node.x)
                obj.put("y", node.y)
                obj.put("z", node.z)
                obj.put("child", nodeToJson(node.child))
            }
            is SceneNode.Scale -> {
                obj.put("type", "scale")
                obj.put("x", node.x)
                obj.put("y", node.y)
                obj.put("z", node.z)
                obj.put("child", nodeToJson(node.child))
            }
            is SceneNode.Color -> {
                obj.put("type", "color")
                obj.put("r", node.r.toDouble())
                obj.put("g", node.g.toDouble())
                obj.put("b", node.b.toDouble())
                obj.put("a", node.a.toDouble())
                obj.put("child", nodeToJson(node.child))
            }
            is SceneNode.Union -> {
                obj.put("type", "union")
                obj.put("children", childrenToJson(node.children))
            }
            is SceneNode.Difference -> {
                obj.put("type", "difference")
                obj.put("children", childrenToJson(node.children))
            }
            is SceneNode.Intersection -> {
                obj.put("type", "intersection")
                obj.put("children", childrenToJson(node.children))
            }
            is SceneNode.Group -> {
                obj.put("type", "group")
                obj.put("children", childrenToJson(node.children))
            }
        }
        return obj
    }

    private fun childrenToJson(children: List<SceneNode>): JSONArray {
        val arr = JSONArray()
        for (child in children) {
            arr.put(nodeToJson(child))
        }
        return arr
    }

    /**
     * Flattens a 2D scene tree for linear_extrude serialization.
     * Returns a list of (transform_chain, primitive) pairs where each primitive
     * is a Circle, Square, or Polygon, and the transform chain wraps the extrusion.
     *
     * For CGAL: produces Transform(LinearExtrude(Primitive)) instead of
     * LinearExtrude(Transform(Primitive)) since CGAL only supports primitives
     * as direct children of linear_extrude.
     */
    private fun flattenForExtrude(node: SceneNode): List<SceneNode> {
        return when (node) {
            is SceneNode.Circle, is SceneNode.Square, is SceneNode.Polygon -> listOf(node)
            is SceneNode.Union -> node.children.flatMap { flattenForExtrude(it) }
            is SceneNode.Group -> node.children.flatMap { flattenForExtrude(it) }
            is SceneNode.Difference -> node.children.flatMap { flattenForExtrude(it) }
            is SceneNode.Intersection -> node.children.flatMap { flattenForExtrude(it) }
            is SceneNode.Translate -> {
                flattenForExtrude(node.child).map { child ->
                    SceneNode.Translate(node.x, node.y, node.z, child)
                }
            }
            is SceneNode.Rotate -> {
                flattenForExtrude(node.child).map { child ->
                    SceneNode.Rotate(node.x, node.y, node.z, child)
                }
            }
            is SceneNode.Scale -> {
                flattenForExtrude(node.child).map { child ->
                    SceneNode.Scale(node.x, node.y, node.z, child)
                }
            }
            is SceneNode.Color -> flattenForExtrude(node.child)
            else -> listOf(node)
        }
    }

    /**
     * Wraps a linear_extrude primitive with its parent transforms for CGAL.
     * Transforms are moved outside the extrusion:
     * Rotate(Polygon) -> becomes a node that serializes as Rotate(LinearExtrude(Polygon))
     */
    private fun serializeExtrudedPrimitive(node: SceneNode, height: Double): JSONObject {
        return when (node) {
            is SceneNode.Translate -> {
                val obj = JSONObject()
                obj.put("type", "translate")
                obj.put("x", node.x)
                obj.put("y", node.y)
                obj.put("z", node.z)
                obj.put("child", serializeExtrudedPrimitive(node.child, height))
                obj
            }
            is SceneNode.Rotate -> {
                val obj = JSONObject()
                obj.put("type", "rotate")
                obj.put("x", node.x)
                obj.put("y", node.y)
                obj.put("z", node.z)
                obj.put("child", serializeExtrudedPrimitive(node.child, height))
                obj
            }
            is SceneNode.Scale -> {
                val obj = JSONObject()
                obj.put("type", "scale")
                obj.put("x", node.x)
                obj.put("y", node.y)
                obj.put("z", node.z)
                obj.put("child", serializeExtrudedPrimitive(node.child, height))
                obj
            }
            else -> {
                // Leaf primitive — wrap in linear_extrude
                val obj = JSONObject()
                obj.put("type", "linear_extrude")
                obj.put("height", height)
                obj.put("child", nodeToJson(node))
                obj
            }
        }
    }
}
