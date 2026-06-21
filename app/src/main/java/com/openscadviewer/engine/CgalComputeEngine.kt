package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ComputeEngine implementation that delegates to native CGAL library via JNI.
 * Provides exact CSG Boolean operations using Nef polyhedra.
 *
 * The native library is loaded lazily on first call to isAvailable().
 * If the library cannot be loaded, all compute calls will fail gracefully.
 */
class CgalComputeEngine : ComputeEngine {
    private var nativeHandle: Long = 0
    private var libraryLoaded = false
    private var loadAttempted = false

    override suspend fun compute(scene: SceneNode, progress: ProgressCallback?): Result<MeshResult> {
        if (!isAvailable()) {
            val error = ComputeError(ErrorCategory.COMPUTATION_FAILURE, "CGAL native library not available")
            progress?.onProgress("${error.category}: ${error.message}", "ERROR")
            return Result.failure(ComputeException(error))
        }

        progress?.onProgress("Parsing OpenSCAD source...")

        val json = SceneSerializer.toJson(scene)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val nodeCount = countNodes(scene)

        progress?.onProgress("Parsed $nodeCount scene nodes")
        progress?.onProgress("Computing mesh with CGAL engine...")

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.Default) {
            val result = withTimeoutOrNull(60_000L) {
                nativeCompute(jsonBytes) { message ->
                    progress?.onProgress(message)
                }
            }

            if (result == null) {
                nativeCancel(nativeHandle)
                val error = ComputeError(ErrorCategory.TIMEOUT, "Computation exceeded 60 seconds")
                progress?.onProgress("${error.category}: ${error.message}", "ERROR")
                Result.failure(ComputeException(error))
            } else if (result.errorCategory != null) {
                val error = ComputeError(
                    ErrorCategory.valueOf(result.errorCategory),
                    result.errorMessage ?: "Unknown error"
                )
                progress?.onProgress("${error.category}: ${error.message}", "ERROR")
                Result.failure(ComputeException(error))
            } else {
                val elapsed = System.currentTimeMillis() - startTime
                val vertices = result.vertices ?: FloatArray(0)
                val triangleCount = vertices.size / 9 // 3 vertices per triangle, 3 floats per vertex
                progress?.onProgress("Mesh generated: $triangleCount triangles in ${elapsed}ms")
                Result.success(
                    MeshResult(
                        vertices,
                        result.normals ?: FloatArray(0),
                        result.colors ?: FloatArray(0)
                    )
                )
            }
        }
    }

    override fun cancel() {
        if (libraryLoaded) {
            nativeCancel(nativeHandle)
        }
    }

    override fun isAvailable(): Boolean {
        if (!loadAttempted) {
            loadAttempted = true
            libraryLoaded = try {
                System.loadLibrary("cgal_engine")
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }
        return libraryLoaded
    }

    /**
     * Recursively counts nodes in the scene tree.
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
            else -> 0
        }
    }

    // JNI native methods
    private external fun nativeCompute(sceneJson: ByteArray, progressCallback: (String) -> Unit): NativeResult
    private external fun nativeCancel(handle: Long)
}
