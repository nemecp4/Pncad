package com.openscadviewer.engine.csg

/**
 * Represents a 3D vector.
 */
data class Vector3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Double) = Vector3(x * scalar, y * scalar, z * scalar)
    fun dot(other: Vector3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vector3) = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
    fun length() = kotlin.math.sqrt(x * x + y * y + z * z)
    fun normalized(): Vector3 {
        val len = length()
        return if (len > 1e-10) Vector3(x / len, y / len, z / len) else this
    }
    fun negated() = Vector3(-x, -y, -z)
    fun lerp(other: Vector3, t: Double) = this + (other - this) * t
}

/**
 * A vertex with position and normal.
 */
data class CsgVertex(val pos: Vector3, val normal: Vector3) {
    fun interpolate(other: CsgVertex, t: Double) = CsgVertex(
        pos.lerp(other.pos, t),
        normal.lerp(other.normal, t).normalized()
    )
    fun flip() = CsgVertex(pos, normal.negated())
}

/**
 * A convex polygon with vertices and a shared plane.
 */
data class CsgPolygon(val vertices: List<CsgVertex>) {
    val plane: Plane = Plane.fromPoints(
        vertices[0].pos, vertices[1].pos, vertices[2].pos
    )
    
    fun flip() = CsgPolygon(vertices.reversed().map { it.flip() })
}

/**
 * A plane defined by normal and distance from origin (w).
 */
data class Plane(val normal: Vector3, val w: Double) {
    companion object {
        private const val EPSILON = 1e-5
        
        fun fromPoints(a: Vector3, b: Vector3, c: Vector3): Plane {
            val n = (b - a).cross(c - a).normalized()
            return Plane(n, n.dot(a))
        }
    }
    
    fun flip() = Plane(normal.negated(), -w)
    
    /**
     * Split polygon by this plane. Coplanar, front, back polygons are added to respective lists.
     */
    fun splitPolygon(
        polygon: CsgPolygon,
        coplanarFront: MutableList<CsgPolygon>,
        coplanarBack: MutableList<CsgPolygon>,
        front: MutableList<CsgPolygon>,
        back: MutableList<CsgPolygon>
    ) {
        val COPLANAR = 0; val FRONT = 1; val BACK = 2; val SPANNING = 3
        
        var polygonType = 0
        val types = polygon.vertices.map { vertex ->
            val t = normal.dot(vertex.pos) - w
            val type = when {
                t < -EPSILON -> BACK
                t > EPSILON -> FRONT
                else -> COPLANAR
            }
            polygonType = polygonType or type
            type
        }
        
        when (polygonType) {
            COPLANAR -> {
                if (normal.dot(polygon.plane.normal) > 0)
                    coplanarFront.add(polygon)
                else
                    coplanarBack.add(polygon)
            }
            FRONT -> front.add(polygon)
            BACK -> back.add(polygon)
            SPANNING -> {
                val f = mutableListOf<CsgVertex>()
                val b = mutableListOf<CsgVertex>()
                for (i in polygon.vertices.indices) {
                    val j = (i + 1) % polygon.vertices.size
                    val ti = types[i]
                    val tj = types[j]
                    val vi = polygon.vertices[i]
                    val vj = polygon.vertices[j]
                    
                    if (ti != BACK) f.add(vi)
                    if (ti != FRONT) b.add(vi)
                    
                    if ((ti or tj) == SPANNING) {
                        val t = (w - normal.dot(vi.pos)) / normal.dot(vj.pos - vi.pos)
                        val v = vi.interpolate(vj, t.coerceIn(0.0, 1.0))
                        f.add(v)
                        b.add(v)
                    }
                }
                if (f.size >= 3) front.add(CsgPolygon(f.toList()))
                if (b.size >= 3) back.add(CsgPolygon(b.toList()))
            }
        }
    }
}
