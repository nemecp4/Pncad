package com.openscadviewer.renderer

import kotlin.math.*

/**
 * Simple 4x4 matrix class for 3D transformations.
 * Column-major order compatible with OpenGL.
 */
class Matrix4(val m: FloatArray = FloatArray(16)) {

    companion object {
        fun identity(): Matrix4 {
            val m = Matrix4()
            m.m[0] = 1f; m.m[5] = 1f; m.m[10] = 1f; m.m[15] = 1f
            return m
        }

        fun translation(x: Float, y: Float, z: Float): Matrix4 {
            val m = identity()
            m.m[12] = x; m.m[13] = y; m.m[14] = z
            return m
        }

        fun scale(x: Float, y: Float, z: Float): Matrix4 {
            val m = Matrix4()
            m.m[0] = x; m.m[5] = y; m.m[10] = z; m.m[15] = 1f
            return m
        }

        fun rotationX(degrees: Float): Matrix4 {
            val rad = degrees * PI.toFloat() / 180f
            val c = cos(rad)
            val s = sin(rad)
            val m = identity()
            m.m[5] = c; m.m[6] = s
            m.m[9] = -s; m.m[10] = c
            return m
        }

        fun rotationY(degrees: Float): Matrix4 {
            val rad = degrees * PI.toFloat() / 180f
            val c = cos(rad)
            val s = sin(rad)
            val m = identity()
            m.m[0] = c; m.m[2] = -s
            m.m[8] = s; m.m[10] = c
            return m
        }

        fun rotationZ(degrees: Float): Matrix4 {
            val rad = degrees * PI.toFloat() / 180f
            val c = cos(rad)
            val s = sin(rad)
            val m = identity()
            m.m[0] = c; m.m[1] = s
            m.m[4] = -s; m.m[5] = c
            return m
        }

        fun perspective(fovDegrees: Float, aspect: Float, near: Float, far: Float): Matrix4 {
            val fov = fovDegrees * PI.toFloat() / 180f
            val f = 1f / tan(fov / 2f)
            val m = Matrix4()
            m.m[0] = f / aspect
            m.m[5] = f
            m.m[10] = (far + near) / (near - far)
            m.m[11] = -1f
            m.m[14] = 2f * far * near / (near - far)
            return m
        }

        fun lookAt(eyeX: Float, eyeY: Float, eyeZ: Float,
                   centerX: Float, centerY: Float, centerZ: Float,
                   upX: Float, upY: Float, upZ: Float): Matrix4 {
            var fx = centerX - eyeX
            var fy = centerY - eyeY
            var fz = centerZ - eyeZ
            val fLen = sqrt(fx * fx + fy * fy + fz * fz)
            fx /= fLen; fy /= fLen; fz /= fLen

            // s = f x up
            var sx = fy * upZ - fz * upY
            var sy = fz * upX - fx * upZ
            var sz = fx * upY - fy * upX
            val sLen = sqrt(sx * sx + sy * sy + sz * sz)
            sx /= sLen; sy /= sLen; sz /= sLen

            // u = s x f
            val ux = sy * fz - sz * fy
            val uy = sz * fx - sx * fz
            val uz = sx * fy - sy * fx

            val m = identity()
            m.m[0] = sx; m.m[4] = sy; m.m[8] = sz
            m.m[1] = ux; m.m[5] = uy; m.m[9] = uz
            m.m[2] = -fx; m.m[6] = -fy; m.m[10] = -fz
            m.m[12] = -(sx * eyeX + sy * eyeY + sz * eyeZ)
            m.m[13] = -(ux * eyeX + uy * eyeY + uz * eyeZ)
            m.m[14] = (fx * eyeX + fy * eyeY + fz * eyeZ)
            return m
        }
    }

    fun multiply(other: Matrix4): Matrix4 {
        val result = Matrix4()
        for (i in 0..3) {
            for (j in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += m[i + k * 4] * other.m[k + j * 4]
                }
                result.m[i + j * 4] = sum
            }
        }
        return result
    }

    fun transformPoint(p: FloatArray): FloatArray {
        val x = p[0]; val y = p[1]; val z = p[2]
        return floatArrayOf(
            m[0] * x + m[4] * y + m[8] * z + m[12],
            m[1] * x + m[5] * y + m[9] * z + m[13],
            m[2] * x + m[6] * y + m[10] * z + m[14]
        )
    }

    fun transformNormal(n: FloatArray): FloatArray {
        val x = n[0]; val y = n[1]; val z = n[2]
        val rx = m[0] * x + m[4] * y + m[8] * z
        val ry = m[1] * x + m[5] * y + m[9] * z
        val rz = m[2] * x + m[6] * y + m[10] * z
        val len = sqrt(rx * rx + ry * ry + rz * rz)
        return if (len > 0.0001f) floatArrayOf(rx / len, ry / len, rz / len)
        else floatArrayOf(0f, 0f, 1f)
    }
}
