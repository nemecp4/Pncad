package com.openscadviewer.parser

/**
 * Returns true if this SceneNode tree contains at least one geometry-producing node.
 * Geometry-producing nodes are primitive shapes (Cube, Sphere, Cylinder, Circle, Square, Polygon).
 * Transformations and CSG operations recursively check their children.
 */
fun SceneNode.containsGeometry(): Boolean = when (this) {
    is SceneNode.Cube, is SceneNode.Sphere, is SceneNode.Cylinder,
    is SceneNode.Circle, is SceneNode.Square, is SceneNode.Polygon -> true
    is SceneNode.Text -> text.isNotEmpty() && size > 0
    is SceneNode.Translate -> child.containsGeometry()
    is SceneNode.Rotate -> child.containsGeometry()
    is SceneNode.Scale -> child.containsGeometry()
    is SceneNode.Color -> child.containsGeometry()
    is SceneNode.LinearExtrude -> child.containsGeometry()
    is SceneNode.Union -> children.any { it.containsGeometry() }
    is SceneNode.Difference -> children.any { it.containsGeometry() }
    is SceneNode.Intersection -> children.any { it.containsGeometry() }
    is SceneNode.Group -> children.any { it.containsGeometry() }
}
