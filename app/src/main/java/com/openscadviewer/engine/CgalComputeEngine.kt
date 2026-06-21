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

    override suspend fun compute(scene: SceneNode): Result<MeshResult> {
        if (!isAvailable()) {
            return Result.failure(
                ComputeException(
                    ComputeError(ErrorCategory.COMPUTATION_FAILURE, "CGAL native library not available")
                )
            )
        }

        val json = SceneSerializer.toJson(scene)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)

        return withContext(Dispatchers.Default) {
            val result = withTimeoutOrNull(60_000L) {
                nativeCompute(jsonBytes)
            }

            if (result == null) {
                nativeCancel(nativeHandle)
                Result.failure(
                    ComputeException(
                        ComputeError(ErrorCategory.TIMEOUT, "Computation exceeded 60 seconds")
                    )
                )
            } else if (result.errorCategory != null) {
                Result.failure(
                    ComputeException(
                        ComputeError(
                            ErrorCategory.valueOf(result.errorCategory),
                            result.errorMessage ?: "Unknown error"
                        )
                    )
                )
            } else {
                Result.success(
                    MeshResult(
                        result.vertices ?: FloatArray(0),
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

    // JNI native methods
    private external fun nativeCompute(sceneJson: ByteArray): NativeResult
    private external fun nativeCancel(handle: Long)
}
