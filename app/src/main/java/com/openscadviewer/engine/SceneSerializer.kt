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
}
