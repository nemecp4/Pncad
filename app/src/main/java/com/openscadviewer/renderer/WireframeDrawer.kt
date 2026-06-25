package com.openscadviewer.renderer

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Draws mesh edges as wireframe overlay using GL_LINES.
 * Uses glPolygonOffset to avoid z-fighting with the solid mesh.
 * Automatically selects a contrasting wireframe color based on the current background.
 */
class WireframeDrawer {

    private var program = 0
    private var vertices: FloatBuffer? = null
    private var vertexCount = 0
    private var edgeIndexBuffer: ShortBuffer? = null
    private var edgeCount = 0

    /** Current background color RGBA — used to determine contrasting wireframe color. */
    @Volatile
    var backgroundColorRgba: FloatArray = floatArrayOf(0.18f, 0.18f, 0.18f, 1.0f)

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 uColor;
        void main() {
            gl_FragColor = uColor;
        }
    """.trimIndent()

    /**
     * Compiles the flat-color shader program for wireframe rendering.
     * Must be called on the GL thread (e.g., in onSurfaceCreated).
     */
    fun initialize() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Failed to compile wireframe shaders")
            return
        }

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Failed to link wireframe program: ${GLES20.glGetProgramInfoLog(it)}")
                GLES20.glDeleteProgram(it)
                program = 0
            }
        }
    }

    /**
     * Stores a reference to the mesh vertex data and generates an edge index buffer.
     * The vertices are expected as interleaved x,y,z triples (3 floats per vertex).
     * Triangles are assumed to be sequential (every 3 vertices form one triangle).
     */
    fun setMeshData(vertices: FloatBuffer, vertexCount: Int) {
        this.vertices = vertices
        this.vertexCount = vertexCount
        generateEdgeIndices(vertexCount)
    }

    /**
     * Draws the wireframe edges using GL_LINES.
     * Uses polygon offset to push the wireframe slightly in front of the solid geometry,
     * preventing z-fighting artifacts.
     *
     * @param mvpMatrix The combined Model-View-Projection matrix (16 floats, column-major)
     */
    fun draw(mvpMatrix: FloatArray) {
        if (program == 0) return
        val verts = vertices ?: return
        if (edgeCount == 0) return

        GLES20.glUseProgram(program)

        // Enable polygon offset to push lines slightly forward (avoid z-fighting)
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-1.0f, -1.0f)

        // Set MVP matrix uniform
        val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        // Set wireframe color (contrasting with background)
        val colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        val wireColor = computeContrastingColor()
        GLES20.glUniform4fv(colorHandle, 1, wireColor, 0)

        // Bind vertex data
        verts.position(0)
        val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, verts)

        // Draw edges as GL_LINES using the edge index buffer
        edgeIndexBuffer?.position(0)
        GLES20.glDrawElements(
            GLES20.GL_LINES,
            edgeCount * 2,
            GLES20.GL_UNSIGNED_SHORT,
            edgeIndexBuffer
        )

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
    }

    /**
     * Generates an edge index buffer from sequential triangles.
     * For each triangle (v0, v1, v2), creates 3 edges: (v0,v1), (v1,v2), (v2,v0).
     */
    private fun generateEdgeIndices(vertexCount: Int) {
        val triangleCount = vertexCount / 3
        // Each triangle has 3 edges, each edge is 2 indices
        edgeCount = triangleCount * 3
        val indices = ShortArray(edgeCount * 2)

        var idx = 0
        for (tri in 0 until triangleCount) {
            val v0 = (tri * 3).toShort()
            val v1 = (tri * 3 + 1).toShort()
            val v2 = (tri * 3 + 2).toShort()

            // Edge 0: v0 -> v1
            indices[idx++] = v0
            indices[idx++] = v1
            // Edge 1: v1 -> v2
            indices[idx++] = v1
            indices[idx++] = v2
            // Edge 2: v2 -> v0
            indices[idx++] = v2
            indices[idx++] = v0
        }

        edgeIndexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices)
            .apply { position(0) }
    }

    /**
     * Computes a contrasting wireframe color based on the current background.
     * Dark wireframe on light backgrounds, light wireframe on dark backgrounds.
     */
    private fun computeContrastingColor(): FloatArray {
        val bg = backgroundColorRgba
        // Compute perceived luminance using standard coefficients
        val luminance = 0.299f * bg[0] + 0.587f * bg[1] + 0.114f * bg[2]
        return if (luminance > 0.5f) {
            // Light background → dark wireframe
            floatArrayOf(0.1f, 0.1f, 0.1f, 1.0f)
        } else {
            // Dark background → light wireframe
            floatArrayOf(0.8f, 0.8f, 0.8f, 1.0f)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
                return 0
            }
        }
    }

    companion object {
        private const val TAG = "WireframeDrawer"
    }
}
