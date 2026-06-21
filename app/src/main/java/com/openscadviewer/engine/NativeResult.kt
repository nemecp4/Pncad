package com.openscadviewer.engine

/**
 * JNI result wrapper returned from native CGAL computation code.
 * Either contains mesh data (vertices/normals/colors) on success,
 * or error information (errorCategory/errorMessage) on failure.
 */
data class NativeResult(
    val vertices: FloatArray?,
    val normals: FloatArray?,
    val colors: FloatArray?,
    val errorCategory: String?,
    val errorMessage: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NativeResult) return false
        return vertices?.contentEquals(other.vertices) ?: (other.vertices == null) &&
            normals?.contentEquals(other.normals) ?: (other.normals == null) &&
            colors?.contentEquals(other.colors) ?: (other.colors == null) &&
            errorCategory == other.errorCategory &&
            errorMessage == other.errorMessage
    }

    override fun hashCode(): Int {
        var result = vertices?.contentHashCode() ?: 0
        result = 31 * result + (normals?.contentHashCode() ?: 0)
        result = 31 * result + (colors?.contentHashCode() ?: 0)
        result = 31 * result + (errorCategory?.hashCode() ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}
