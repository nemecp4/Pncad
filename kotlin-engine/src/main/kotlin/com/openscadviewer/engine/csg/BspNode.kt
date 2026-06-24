package com.openscadviewer.engine.csg

/**
 * BSP tree node. Holds a splitting plane and front/back child nodes.
 */
class BspNode(polygons: List<CsgPolygon> = emptyList()) {
    var plane: Plane? = null
    var front: BspNode? = null
    var back: BspNode? = null
    var polygons: MutableList<CsgPolygon> = mutableListOf()
    
    init {
        if (polygons.isNotEmpty()) {
            build(polygons)
        }
    }
    
    fun clone(): BspNode {
        val node = BspNode()
        node.plane = plane
        node.front = front?.clone()
        node.back = back?.clone()
        node.polygons = polygons.toMutableList()
        return node
    }
    
    /**
     * Convert solid space to empty space and empty space to solid space.
     */
    fun invert() {
        polygons = polygons.map { it.flip() }.toMutableList()
        plane = plane?.flip()
        front?.invert()
        back?.invert()
        val temp = front
        front = back
        back = temp
    }
    
    /**
     * Recursively remove all polygons in `polygons` that are inside this BSP tree.
     */
    fun clipPolygons(polygons: List<CsgPolygon>): List<CsgPolygon> {
        val currentPlane = plane ?: return polygons.toList()
        
        val front = mutableListOf<CsgPolygon>()
        val back = mutableListOf<CsgPolygon>()
        
        for (polygon in polygons) {
            currentPlane.splitPolygon(polygon, front, back, front, back)
        }
        
        val frontResult = this.front?.clipPolygons(front) ?: front
        val backResult = if (this.back != null) this.back!!.clipPolygons(back) else emptyList()
        
        return frontResult + backResult
    }
    
    /**
     * Remove all polygons in this BSP tree that are inside the other BSP tree.
     */
    fun clipTo(bsp: BspNode) {
        polygons = bsp.clipPolygons(polygons).toMutableList()
        front?.clipTo(bsp)
        back?.clipTo(bsp)
    }
    
    /**
     * Return all polygons in this BSP tree.
     */
    fun allPolygons(): List<CsgPolygon> {
        val result = polygons.toMutableList()
        front?.let { result.addAll(it.allPolygons()) }
        back?.let { result.addAll(it.allPolygons()) }
        return result
    }
    
    /**
     * Build a BSP tree from a list of polygons.
     */
    fun build(polygons: List<CsgPolygon>) {
        if (polygons.isEmpty()) return
        
        if (plane == null) {
            plane = polygons[0].plane
        }
        
        val currentPlane = plane!!
        val front = mutableListOf<CsgPolygon>()
        val back = mutableListOf<CsgPolygon>()
        
        for (polygon in polygons) {
            currentPlane.splitPolygon(polygon, this.polygons, this.polygons, front, back)
        }
        
        if (front.isNotEmpty()) {
            if (this.front == null) this.front = BspNode()
            this.front!!.build(front)
        }
        if (back.isNotEmpty()) {
            if (this.back == null) this.back = BspNode()
            this.back!!.build(back)
        }
    }
}
