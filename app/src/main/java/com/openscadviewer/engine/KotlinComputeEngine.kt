package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode
import com.openscadviewer.renderer.MeshGenerator

/**
 * ComputeEngine implementation that wraps the existing pure-Kotlin MeshGenerator.
 * Always available; provides fast but approximate CSG (renders all children without Boolean ops).
 */
class KotlinComputeEngine : ComputeEngine {
    private val meshGenerator = MeshGenerator()
    @Volatile private var cancelled = false

    override suspend fun compute(scene: SceneNode): Result<MeshResult> {
        cancelled = false
        return try {
            val mesh = meshGenerator.generate(scene)
            if (cancelled) {
                Result.success(MeshResult.EMPTY)
            } else {
                Result.success(MeshResult(mesh.vertices, mesh.normals, mesh.colors))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun cancel() {
        cancelled = true
    }

    override fun isAvailable(): Boolean = true
}
