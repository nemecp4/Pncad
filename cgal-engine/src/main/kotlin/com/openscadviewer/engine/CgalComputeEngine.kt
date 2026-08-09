package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

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
    private var fontPathInitialized = false

    override suspend fun compute(scene: SceneNode, progress: ProgressCallback?): Result<MeshResult> {
        if (!isAvailable()) {
            val error = ComputeError(ErrorCategory.COMPUTATION_FAILURE, "CGAL native library not available")
            progress?.onProgress("${error.category}: ${error.message}", "ERROR")
            return Result.failure(ComputeException(error))
        }

        // Ensure font path is initialized for text rendering
        initFontPathIfNeeded()

        progress?.onProgress("Parsing OpenSCAD source...")

        val json = SceneSerializer.toJsonForCgal(scene)
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

    /**
     * Initialize the font path for desktop/benchmark use.
     * On desktop (non-Android), the font is loaded from the classpath resource
     * (bundled in shared-base) or from a known relative path.
     * On Android, the font path is set externally via setFontPath() from EngineManager.
     */
    private fun initFontPathIfNeeded() {
        if (fontPathInitialized) return
        fontPathInitialized = true

        // Try to find the font from classpath resource (desktop/benchmark JVM)
        val resourceUrl = javaClass.classLoader?.getResource("fonts/LiberationSans-Regular.ttf")
        if (resourceUrl != null) {
            val resourcePath = resourceUrl.path
            // If it's a file URL (not inside a JAR), use it directly
            if (resourceUrl.protocol == "file") {
                nativeSetFontPath(resourcePath)
                return
            }
            // If inside a JAR, extract to temp file
            try {
                val tempDir = File(System.getProperty("java.io.tmpdir"), "pncad-fonts")
                tempDir.mkdirs()
                val tempFont = File(tempDir, "LiberationSans-Regular.ttf")
                if (!tempFont.exists()) {
                    javaClass.classLoader?.getResourceAsStream("fonts/LiberationSans-Regular.ttf")?.use { input ->
                        tempFont.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (tempFont.exists()) {
                    nativeSetFontPath(tempFont.absolutePath)
                    return
                }
            } catch (_: Exception) {
                // Fall through to relative path search
            }
        }

        // Fallback: try known relative paths from project root (for desktop builds)
        val relativePaths = listOf(
            "shared-base/src/main/resources/fonts/LiberationSans-Regular.ttf",
            "../shared-base/src/main/resources/fonts/LiberationSans-Regular.ttf",
            "../../shared-base/src/main/resources/fonts/LiberationSans-Regular.ttf"
        )
        for (relPath in relativePaths) {
            val file = File(relPath)
            if (file.exists()) {
                nativeSetFontPath(file.absolutePath)
                return
            }
        }
    }

    // JNI native methods
    private external fun nativeCompute(sceneJson: ByteArray, progressCallback: (String) -> Unit): NativeResult
    private external fun nativeCancel(handle: Long)
    private external fun nativeSetFontPath(fontPath: String)

    /**
     * Set the font file path for the native text renderer.
     * Must be called before compute() to enable text rendering in CGAL.
     * On Android, this should be the path to the font extracted from APK assets.
     * On desktop, this should be a filesystem path to the bundled font file.
     */
    fun setFontPath(path: String) {
        if (isAvailable()) {
            nativeSetFontPath(path)
            fontPathInitialized = true
        }
    }
}
