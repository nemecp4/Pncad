package com.openscadviewer.engine

import com.openscadviewer.engine.text.AwtFontProvider
import com.openscadviewer.engine.text.FontProvider
import com.openscadviewer.parser.SceneNode

/**
 * ComputeEngine implementation that wraps the existing pure-Kotlin MeshGenerator.
 * Always available; provides fast but approximate CSG (renders all children without Boolean ops).
 *
 * @param fontProvider Platform-specific font provider for text rendering.
 *                     Defaults to [AwtFontProvider] for desktop/benchmark use.
 *                     On Android, pass [AndroidFontProvider(context)] from the app module.
 */
class KotlinComputeEngine(fontProvider: FontProvider = AwtFontProvider()) : ComputeEngine {
    private val meshGenerator = MeshGenerator().apply {
        this.fontProvider = fontProvider
    }
    @Volatile private var cancelled = false

    override suspend fun compute(scene: SceneNode, progress: ProgressCallback?): Result<MeshResult> {
        cancelled = false
        return try {
            progress?.onProgress("Parsing OpenSCAD source...")

            val nodeCount = countNodes(scene)
            progress?.onProgress("Parsed $nodeCount scene nodes")

            progress?.onProgress("Computing mesh with Kotlin engine...")
            val startTime = System.currentTimeMillis()

            val mesh = meshGenerator.generate(scene)

            if (cancelled) {
                Result.success(MeshResult.EMPTY)
            } else {
                val elapsed = System.currentTimeMillis() - startTime
                val triangleCount = mesh.triangleCount
                progress?.onProgress("Mesh generated: $triangleCount triangles in ${elapsed}ms")
                Result.success(MeshResult(mesh.vertices, mesh.normals, mesh.colors))
            }
        } catch (e: Exception) {
            progress?.onProgress(e.message ?: "Unknown error", "ERROR")
            Result.failure(e)
        }
    }

    override fun cancel() {
        cancelled = true
    }

    override fun isAvailable(): Boolean = true

    /**
     * Recursively counts all nodes in the scene graph.
     */
    private fun countNodes(node: SceneNode): Int {
        return 1 + when (node) {
            is SceneNode.Translate -> countNodes(node.child)
            is SceneNode.Rotate -> countNodes(node.child)
            is SceneNode.Scale -> countNodes(node.child)
            is SceneNode.Color -> countNodes(node.child)
            is SceneNode.LinearExtrude -> countNodes(node.child)
            is SceneNode.Union -> node.children.sumOf { countNodes(it) }
            is SceneNode.Difference -> node.children.sumOf { countNodes(it) }
            is SceneNode.Intersection -> node.children.sumOf { countNodes(it) }
            is SceneNode.Group -> node.children.sumOf { countNodes(it) }
            is SceneNode.Cube -> 0
            is SceneNode.Sphere -> 0
            is SceneNode.Cylinder -> 0
            is SceneNode.Circle -> 0
            is SceneNode.Square -> 0
            is SceneNode.Polygon -> 0
            is SceneNode.Text -> 0
        }
    }
}
