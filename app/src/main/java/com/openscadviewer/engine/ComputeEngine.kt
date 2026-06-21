package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode

/**
 * Common interface for mesh generation engines.
 * The rendering layer uses only this interface.
 */
interface ComputeEngine {
    /**
     * Compute triangle mesh from scene graph.
     * @param scene Root node of the parsed scene graph
     * @param progress Optional callback for progress reporting
     * @return Result containing either mesh data or error
     */
    suspend fun compute(
        scene: SceneNode,
        progress: ProgressCallback? = null
    ): Result<MeshResult>

    /**
     * Cancel any in-progress computation.
     */
    fun cancel()

    /**
     * Check if this engine is available on the current device.
     */
    fun isAvailable(): Boolean
}
