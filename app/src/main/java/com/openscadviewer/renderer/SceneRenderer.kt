package com.openscadviewer.renderer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * OpenGL ES 2.0 renderer for the 3D preview.
 * Supports orbit/zoom camera controls and Phong shading.
 */
class SceneRenderer : GLSurfaceView.Renderer {

    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var vertexCount = 0

    private var program = 0
    private var width = 1
    private var height = 1

    // Camera parameters
    var cameraDistance = 10f
    var cameraRotX = 30f
    var cameraRotY = -45f
    var cameraPanX = 0f
    var cameraPanY = 0f

    // Bounding box for auto-fitting
    private var boundingMin = floatArrayOf(-1f, -1f, -1f)
    private var boundingMax = floatArrayOf(1f, 1f, 1f)

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uMVMatrix;
        uniform mat3 uNormalMatrix;
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        attribute vec4 aColor;
        varying vec3 vNormal;
        varying vec3 vPosition;
        varying vec4 vColor;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vPosition = (uMVMatrix * aPosition).xyz;
            vNormal = uNormalMatrix * aNormal;
            vColor = aColor;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec3 vNormal;
        varying vec3 vPosition;
        varying vec4 vColor;
        uniform vec3 uLightPos;
        uniform vec3 uLightPos2;
        void main() {
            vec3 normal = normalize(vNormal);
            
            // Two-sided lighting
            if (!gl_FrontFacing) {
                normal = -normal;
            }
            
            // Light 1 (main)
            vec3 lightDir = normalize(uLightPos - vPosition);
            float diff = max(dot(normal, lightDir), 0.0);
            
            // Light 2 (fill)
            vec3 lightDir2 = normalize(uLightPos2 - vPosition);
            float diff2 = max(dot(normal, lightDir2), 0.0) * 0.4;
            
            // Specular
            vec3 viewDir = normalize(-vPosition);
            vec3 reflectDir = reflect(-lightDir, normal);
            float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0) * 0.3;
            
            float ambient = 0.2;
            float lighting = ambient + diff * 0.6 + diff2 + spec;
            
            gl_FragColor = vec4(vColor.rgb * lighting, vColor.a);
        }
    """.trimIndent()

    fun setMeshData(vertices: FloatArray, normals: FloatArray, colors: FloatArray) {
        vertexCount = vertices.size / 3

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
            .apply { position(0) }

        normalBuffer = ByteBuffer.allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(normals)
            .apply { position(0) }

        colorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(colors)
            .apply { position(0) }

        // Calculate bounding box
        if (vertices.isNotEmpty()) {
            boundingMin = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            boundingMax = floatArrayOf(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
            for (i in vertices.indices step 3) {
                boundingMin[0] = min(boundingMin[0], vertices[i])
                boundingMin[1] = min(boundingMin[1], vertices[i + 1])
                boundingMin[2] = min(boundingMin[2], vertices[i + 2])
                boundingMax[0] = max(boundingMax[0], vertices[i])
                boundingMax[1] = max(boundingMax[1], vertices[i + 1])
                boundingMax[2] = max(boundingMax[2], vertices[i + 2])
            }
            // Auto-fit camera
            val dx = boundingMax[0] - boundingMin[0]
            val dy = boundingMax[1] - boundingMin[1]
            val dz = boundingMax[2] - boundingMin[2]
            val maxDim = maxOf(dx, dy, dz)
            cameraDistance = maxDim * 2.5f
            cameraPanX = -(boundingMin[0] + boundingMax[0]) / 2f
            cameraPanY = -(boundingMin[1] + boundingMax[1]) / 2f
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.18f, 0.18f, 0.18f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (vertexCount == 0 || vertexBuffer == null) return

        GLES20.glUseProgram(program)

        val aspect = width.toFloat() / height.toFloat()
        val projection = Matrix4.perspective(45f, aspect, 0.1f, cameraDistance * 10f)

        // Camera orbit
        val eyeX = cameraDistance * cos(cameraRotX * PI.toFloat() / 180f) * sin(cameraRotY * PI.toFloat() / 180f)
        val eyeY = cameraDistance * sin(cameraRotX * PI.toFloat() / 180f)
        val eyeZ = cameraDistance * cos(cameraRotX * PI.toFloat() / 180f) * cos(cameraRotY * PI.toFloat() / 180f)

        val view = Matrix4.lookAt(
            eyeX - cameraPanX, eyeY - cameraPanY, eyeZ,
            -cameraPanX, -cameraPanY, 0f,
            0f, 1f, 0f
        )

        val mvMatrix = view
        val mvpMatrix = projection.multiply(mvMatrix)

        // Normal matrix (upper 3x3 of modelView)
        val normalMatrix = floatArrayOf(
            mvMatrix.m[0], mvMatrix.m[1], mvMatrix.m[2],
            mvMatrix.m[4], mvMatrix.m[5], mvMatrix.m[6],
            mvMatrix.m[8], mvMatrix.m[9], mvMatrix.m[10]
        )

        // Set uniforms
        val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix.m, 0)

        val mvHandle = GLES20.glGetUniformLocation(program, "uMVMatrix")
        GLES20.glUniformMatrix4fv(mvHandle, 1, false, mvMatrix.m, 0)

        val normalHandle = GLES20.glGetUniformLocation(program, "uNormalMatrix")
        GLES20.glUniformMatrix3fv(normalHandle, 1, false, normalMatrix, 0)

        val lightHandle = GLES20.glGetUniformLocation(program, "uLightPos")
        GLES20.glUniform3f(lightHandle, cameraDistance, cameraDistance * 0.8f, cameraDistance)

        val light2Handle = GLES20.glGetUniformLocation(program, "uLightPos2")
        GLES20.glUniform3f(light2Handle, -cameraDistance * 0.5f, -cameraDistance * 0.3f, cameraDistance * 0.5f)

        // Set vertex attributes
        val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val normHandle = GLES20.glGetAttribLocation(program, "aNormal")
        GLES20.glEnableVertexAttribArray(normHandle)
        GLES20.glVertexAttribPointer(normHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)

        val colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)

        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(normHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
