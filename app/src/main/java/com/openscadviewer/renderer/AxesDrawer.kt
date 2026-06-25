package com.openscadviewer.renderer

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Draws three colored coordinate axis lines at the scene origin.
 * X-axis: red (1,0,0), Y-axis: green (0,1,0), Z-axis: blue (0,0,1).
 *
 * Uses a simple position-only vertex shader with a uniform color
 * and GL_LINES to render each axis as a pair of vertices.
 */
class AxesDrawer {

    private var program = 0
    private var vertexBuffer: FloatBuffer? = null

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
     * Compiles the shader program and allocates the vertex buffer.
     * Must be called on the GL thread (e.g., in onSurfaceCreated).
     */
    fun initialize() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        if (program == 0) {
            Log.e(TAG, "Failed to create axes shader program")
        }

        // Allocate buffer for 6 vertices (2 per axis) * 3 floats each
        vertexBuffer = ByteBuffer.allocateDirect(6 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    /**
     * Draws three axis lines from the origin with the given length.
     * X-axis in red, Y-axis in green, Z-axis in blue.
     *
     * @param mvpMatrix The model-view-projection matrix (16 floats, column-major)
     * @param axisLength The length of each axis line from the origin
     */
    fun draw(mvpMatrix: FloatArray, axisLength: Float) {
        if (program == 0) return

        GLES20.glUseProgram(program)

        // Build 6 vertices: origin→(len,0,0), origin→(0,len,0), origin→(0,0,len)
        val vertices = floatArrayOf(
            // X-axis
            0f, 0f, 0f,
            axisLength, 0f, 0f,
            // Y-axis
            0f, 0f, 0f,
            0f, axisLength, 0f,
            // Z-axis
            0f, 0f, 0f,
            0f, 0f, axisLength
        )

        vertexBuffer?.let { buffer ->
            buffer.clear()
            buffer.put(vertices)
            buffer.position(0)

            val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)

            val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

            val colorHandle = GLES20.glGetUniformLocation(program, "uColor")

            // Disable depth test so axes are always visible
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glLineWidth(2.0f)

            // X-axis: red
            GLES20.glUniform4f(colorHandle, 1.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)

            // Y-axis: green
            GLES20.glUniform4f(colorHandle, 0.0f, 1.0f, 0.0f, 1.0f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 2, 2)

            // Z-axis: blue
            GLES20.glUniform4f(colorHandle, 0.0f, 0.0f, 1.0f, 1.0f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 4, 2)

            // Re-enable depth test
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)

            GLES20.glDisableVertexAttribArray(posHandle)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    companion object {
        private const val TAG = "AxesDrawer"

        /**
         * Computes the axis display length proportional to the model's bounding box.
         * Returns max(boundingBoxDiagonal * 0.5, 1.0) for proportional scaling.
         *
         * @param boundingMin The minimum corner of the bounding box [x, y, z]
         * @param boundingMax The maximum corner of the bounding box [x, y, z]
         * @return The axis length to use for drawing
         */
        fun computeAxisLength(boundingMin: FloatArray, boundingMax: FloatArray): Float {
            val dx = boundingMax[0] - boundingMin[0]
            val dy = boundingMax[1] - boundingMin[1]
            val dz = boundingMax[2] - boundingMin[2]
            val diagonal = sqrt(dx * dx + dy * dy + dz * dz)
            return max(diagonal * 0.5f, 1.0f)
        }
    }
}
