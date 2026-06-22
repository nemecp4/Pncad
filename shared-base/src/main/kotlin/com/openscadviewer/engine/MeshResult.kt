package com.openscadviewer.engine

/**
 * Result of mesh generation containing OpenGL-ready float arrays.
 */
data class MeshResult(
    val vertices: FloatArray,   // x,y,z triplets
    val normals: FloatArray,    // nx,ny,nz triplets
    val colors: FloatArray      // r,g,b,a quads
) {
    val vertexCount: Int get() = vertices.size / 3
    val triangleCount: Int get() = vertices.size / 9

    companion object {
        val EMPTY = MeshResult(FloatArray(0), FloatArray(0), FloatArray(0))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshResult) return false
        return vertices.contentEquals(other.vertices) &&
            normals.contentEquals(other.normals) &&
            colors.contentEquals(other.colors)
    }

    override fun hashCode(): Int {
        var result = vertices.contentHashCode()
        result = 31 * result + normals.contentHashCode()
        result = 31 * result + colors.contentHashCode()
        return result
    }
}
