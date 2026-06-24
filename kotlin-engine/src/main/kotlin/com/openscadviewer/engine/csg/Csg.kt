package com.openscadviewer.engine.csg

/**
 * Pure-Kotlin CSG implementation using BSP trees.
 * Based on the csg.js algorithm by Evan Wallace.
 *
 * Supports: union, difference, intersection.
 */
class Csg private constructor(val polygons: List<CsgPolygon>) {
    
    companion object {
        fun fromPolygons(polygons: List<CsgPolygon>) = Csg(polygons)
        
        /**
         * Create a CSG from triangle mesh data (vertices as float array, 9 floats per triangle).
         */
        fun fromTriangles(vertices: FloatArray, normals: FloatArray): Csg {
            val polygons = mutableListOf<CsgPolygon>()
            val triCount = vertices.size / 9
            for (i in 0 until triCount) {
                val base = i * 9
                val nBase = i * 9
                val v0 = CsgVertex(
                    Vector3(vertices[base].toDouble(), vertices[base+1].toDouble(), vertices[base+2].toDouble()),
                    Vector3(normals[nBase].toDouble(), normals[nBase+1].toDouble(), normals[nBase+2].toDouble())
                )
                val v1 = CsgVertex(
                    Vector3(vertices[base+3].toDouble(), vertices[base+4].toDouble(), vertices[base+5].toDouble()),
                    Vector3(normals[nBase+3].toDouble(), normals[nBase+4].toDouble(), normals[nBase+5].toDouble())
                )
                val v2 = CsgVertex(
                    Vector3(vertices[base+6].toDouble(), vertices[base+7].toDouble(), vertices[base+8].toDouble()),
                    Vector3(normals[nBase+6].toDouble(), normals[nBase+7].toDouble(), normals[nBase+8].toDouble())
                )
                // Skip degenerate triangles
                val edge1 = v1.pos - v0.pos
                val edge2 = v2.pos - v0.pos
                if (edge1.cross(edge2).length() > 1e-10) {
                    polygons.add(CsgPolygon(listOf(v0, v1, v2)))
                }
            }
            return Csg(polygons)
        }
    }
    
    /**
     * Return a new CSG solid representing the union of this and another.
     */
    fun union(other: Csg): Csg {
        val a = BspNode(this.polygons)
        val b = BspNode(other.polygons)
        a.clipTo(b)
        b.clipTo(a)
        b.invert()
        b.clipTo(a)
        b.invert()
        a.build(b.allPolygons())
        return Csg(a.allPolygons())
    }
    
    /**
     * Return a new CSG solid representing this minus another.
     * A.subtract(B) = complement of (complement(A) union B)
     */
    fun subtract(other: Csg): Csg {
        if (this.polygons.isEmpty()) return this
        if (other.polygons.isEmpty()) return this
        
        val a = BspNode(this.polygons)
        val b = BspNode(other.polygons)
        a.invert()
        a.clipTo(b)
        b.clipTo(a)
        b.invert()
        b.clipTo(a)
        b.invert()
        a.build(b.allPolygons())
        a.invert()
        return Csg(a.allPolygons())
    }
    
    /**
     * Return a new CSG solid representing the intersection of this and another.
     */
    fun intersect(other: Csg): Csg {
        val a = BspNode(this.polygons)
        val b = BspNode(other.polygons)
        a.invert()
        b.clipTo(a)
        b.invert()
        a.clipTo(b)
        b.clipTo(a)
        a.build(b.allPolygons())
        a.invert()
        return Csg(a.allPolygons())
    }
    
    /**
     * Convert to triangle mesh arrays (vertices and normals as FloatArrays).
     * Polygons with more than 3 vertices are fan-triangulated.
     * Face normals are recomputed from winding order for correct orientation.
     */
    fun toTriangles(): Pair<FloatArray, FloatArray> {
        val verts = mutableListOf<Float>()
        val norms = mutableListOf<Float>()
        
        for (polygon in polygons) {
            if (polygon.vertices.size < 3) continue
            // Fan triangulation from first vertex
            for (i in 1 until polygon.vertices.size - 1) {
                val v0 = polygon.vertices[0]
                val v1 = polygon.vertices[i]
                val v2 = polygon.vertices[i + 1]
                
                verts.add(v0.pos.x.toFloat()); verts.add(v0.pos.y.toFloat()); verts.add(v0.pos.z.toFloat())
                verts.add(v1.pos.x.toFloat()); verts.add(v1.pos.y.toFloat()); verts.add(v1.pos.z.toFloat())
                verts.add(v2.pos.x.toFloat()); verts.add(v2.pos.y.toFloat()); verts.add(v2.pos.z.toFloat())
                
                // Use the polygon's plane normal for consistent face orientation
                val fn = polygon.plane.normal
                norms.add(fn.x.toFloat()); norms.add(fn.y.toFloat()); norms.add(fn.z.toFloat())
                norms.add(fn.x.toFloat()); norms.add(fn.y.toFloat()); norms.add(fn.z.toFloat())
                norms.add(fn.x.toFloat()); norms.add(fn.y.toFloat()); norms.add(fn.z.toFloat())
            }
        }
        
        return Pair(verts.toFloatArray(), norms.toFloatArray())
    }
}
