package com.openscadviewer.engine.text

import kotlin.math.abs

/**
 * Triangulates a polygon with holes using ear-clipping algorithm.
 * Outer contours are CCW, inner contours (holes) are CW.
 * Returns list of triangles as (p1, p2, p3) point triples.
 *
 * Implementation approach:
 * 1. Bridge holes into the outer contour using the mutual visibility technique
 * 2. Apply ear-clipping on the merged single polygon
 * 3. Return triangle list, skipping zero-area (degenerate) triangles
 */
object EarClipTriangulator {

    data class Triangle(
        val p1: Pair<Float, Float>,
        val p2: Pair<Float, Float>,
        val p3: Pair<Float, Float>
    )

    /**
     * @param contours First contour is outer boundary (CCW), remaining are holes (CW).
     *                 Each contour is a list of points. If a contour's last point equals
     *                 its first point (closed), the duplicate is removed internally.
     */
    fun triangulate(contours: List<List<Pair<Float, Float>>>): List<Triangle> {
        if (contours.isEmpty()) return emptyList()

        // Clean contours: remove closing duplicate point, skip degenerate ones
        val cleaned = contours.mapNotNull { contour ->
            val c = removeClosingDuplicate(contour)
            if (c.size < 3) null else c
        }
        if (cleaned.isEmpty()) return emptyList()

        val outer = cleaned[0]
        val holes = if (cleaned.size > 1) cleaned.subList(1, cleaned.size) else emptyList()

        // Merge holes into outer contour using bridge edges
        val merged = if (holes.isEmpty()) {
            outer.toMutableList()
        } else {
            bridgeHoles(outer, holes)
        }

        if (merged.size < 3) return emptyList()

        // Apply ear-clipping on the merged polygon
        return earClip(merged)
    }

    /**
     * Remove the closing duplicate point if first == last.
     */
    private fun removeClosingDuplicate(contour: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (contour.size <= 1) return contour
        val first = contour.first()
        val last = contour.last()
        return if (first.first == last.first && first.second == last.second) {
            contour.dropLast(1)
        } else {
            contour
        }
    }

    /**
     * Bridge holes into the outer contour by finding mutual visibility edges.
     *
     * For each hole:
     * 1. Find the rightmost point in the hole
     * 2. Cast a ray to the right from that point and find the closest intersection
     *    with the outer (merged) polygon edge
     * 3. From the intersection point, find the best visible vertex on the outer polygon
     * 4. Insert the hole's vertices into the outer polygon at the bridge point
     *    (duplicating the bridge vertices to maintain a simple polygon)
     */
    private fun bridgeHoles(
        outer: List<Pair<Float, Float>>,
        holes: List<List<Pair<Float, Float>>>
    ): MutableList<Pair<Float, Float>> {
        var merged = outer.toMutableList()

        // Sort holes by their rightmost X coordinate (descending) for better bridge construction
        val sortedHoles = holes.sortedByDescending { hole ->
            hole.maxOf { it.first }
        }

        for (hole in sortedHoles) {
            merged = bridgeSingleHole(merged, hole)
        }

        return merged
    }

    /**
     * Bridge a single hole into the merged polygon.
     */
    private fun bridgeSingleHole(
        polygon: MutableList<Pair<Float, Float>>,
        hole: List<Pair<Float, Float>>
    ): MutableList<Pair<Float, Float>> {
        if (hole.size < 3 || polygon.size < 3) return polygon

        // Find the rightmost point in the hole
        val holeRightIdx = hole.indices.maxByOrNull { hole[it].first } ?: return polygon
        val holePoint = hole[holeRightIdx]

        // Find the best bridge point on the outer polygon
        val outerIdx = findBridgeVertex(polygon, holePoint)
        if (outerIdx < 0) return polygon

        // Insert hole into polygon at the bridge point
        // The merged polygon becomes:
        // [...outer up to outerIdx, outerPoint, hole from holeRightIdx around back to holeRightIdx, holePoint, outerPoint, ...rest of outer]
        val result = mutableListOf<Pair<Float, Float>>()

        // Copy outer polygon vertices up to and including the bridge vertex
        for (i in 0..outerIdx) {
            result.add(polygon[i])
        }

        // Insert hole vertices starting from the rightmost point, going all the way around
        for (i in hole.indices) {
            val idx = (holeRightIdx + i) % hole.size
            result.add(hole[idx])
        }

        // Add the hole's bridge point again (to close the hole loop in the merged polygon)
        result.add(hole[holeRightIdx])

        // Add the outer bridge vertex again (to close the bridge)
        result.add(polygon[outerIdx])

        // Copy remaining outer polygon vertices after the bridge vertex
        for (i in (outerIdx + 1) until polygon.size) {
            result.add(polygon[i])
        }

        return result
    }

    /**
     * Find the best vertex on the polygon to bridge to from [holePoint].
     *
     * Algorithm:
     * 1. Cast a horizontal ray to the right from holePoint
     * 2. Find the closest edge intersection
     * 3. The candidate is the vertex of that edge with the maximum X <= ray intersection
     * 4. Check if any polygon vertices are inside the triangle (holePoint, intersection, candidate)
     *    - if so, pick the one with the smallest angle to the ray as the bridge vertex
     * 5. Return the index of the chosen bridge vertex
     */
    private fun findBridgeVertex(
        polygon: List<Pair<Float, Float>>,
        holePoint: Pair<Float, Float>
    ): Int {
        val hx = holePoint.first
        val hy = holePoint.second
        var bestDist = Float.MAX_VALUE
        var bestEdgeIdx = -1
        var intersectionX = Float.MAX_VALUE

        // Step 1: Find the closest edge that intersects the rightward ray from holePoint
        for (i in polygon.indices) {
            val j = (i + 1) % polygon.size
            val p1 = polygon[i]
            val p2 = polygon[j]

            // Check if edge crosses the horizontal ray at y = hy going right
            val y1 = p1.second
            val y2 = p2.second

            if ((y1 <= hy && y2 > hy) || (y2 <= hy && y1 > hy)) {
                // Compute X intersection of edge with horizontal line y = hy
                val t = (hy - y1) / (y2 - y1)
                val ix = p1.first + t * (p2.first - p1.first)

                if (ix >= hx && ix < bestDist) {
                    bestDist = ix
                    bestEdgeIdx = i
                    intersectionX = ix
                }
            }
        }

        if (bestEdgeIdx < 0) return -1

        // Step 2: Determine the candidate vertex — the endpoint of the intersected edge
        // that is closest to the intersection and visible from holePoint
        val edgeStart = polygon[bestEdgeIdx]
        val edgeEnd = polygon[(bestEdgeIdx + 1) % polygon.size]

        // Pick the vertex with greater X that is still reachable
        val candidateIdx = if (edgeStart.first > edgeEnd.first) bestEdgeIdx
        else (bestEdgeIdx + 1) % polygon.size

        val candidate = polygon[candidateIdx]

        // If the intersection point IS a vertex, we can directly use it
        if (abs(candidate.first - intersectionX) < EPSILON &&
            abs(candidate.second - hy) < EPSILON
        ) {
            return candidateIdx
        }

        // Step 3: Check if any polygon vertex lies inside the triangle
        // (holePoint, intersection point, candidate). If so, pick the one
        // that minimizes the angle to the horizontal ray.
        val intersectionPoint = Pair(intersectionX, hy)
        var bestReflex = candidateIdx
        var bestAngle = Float.MAX_VALUE

        for (i in polygon.indices) {
            if (i == candidateIdx) continue
            val p = polygon[i]

            if (pointInTriangle(p, holePoint, intersectionPoint, candidate)) {
                // Compute angle between ray direction (1,0) and vector from holePoint to p
                val dx = p.first - hx
                val dy = p.second - hy
                val angle = abs(dy) / (dx + EPSILON) // Approximation of angle tangent
                if (angle < bestAngle) {
                    bestAngle = angle
                    bestReflex = i
                }
            }
        }

        return bestReflex
    }

    /**
     * Ear-clipping triangulation on a simple polygon (no holes — holes already bridged in).
     */
    private fun earClip(polygon: MutableList<Pair<Float, Float>>): List<Triangle> {
        val triangles = mutableListOf<Triangle>()
        val n = polygon.size

        if (n < 3) return triangles
        if (n == 3) {
            val tri = Triangle(polygon[0], polygon[1], polygon[2])
            if (!isDegenerate(tri)) triangles.add(tri)
            return triangles
        }

        // Build a doubly-linked list of vertex indices for efficient removal
        val prev = IntArray(n) { (it - 1 + n) % n }
        val next = IntArray(n) { (it + 1) % n }
        var remaining = n

        // Determine winding of the merged polygon. If it's CW, we need to flip the ear test.
        val area = signedArea(polygon)
        val ccw = area > 0

        // Track which vertices are ears
        val isEar = BooleanArray(n)
        for (i in 0 until n) {
            isEar[i] = checkEar(polygon, i, prev, next, ccw)
        }

        var current = 0
        var attempts = 0
        val maxAttempts = remaining * 2 // Safety limit to avoid infinite loops

        while (remaining > 3 && attempts < maxAttempts) {
            if (isEar[current]) {
                val p = prev[current]
                val nx = next[current]

                val tri = Triangle(polygon[p], polygon[current], polygon[nx])
                if (!isDegenerate(tri)) {
                    triangles.add(tri)
                }

                // Remove current vertex from the polygon
                next[p] = nx
                prev[nx] = p
                remaining--
                attempts = 0

                // Re-check neighbors
                isEar[p] = checkEar(polygon, p, prev, next, ccw)
                isEar[nx] = checkEar(polygon, nx, prev, next, ccw)

                current = nx
            } else {
                current = next[current]
                attempts++
            }
        }

        // Add the final triangle
        if (remaining == 3) {
            val p = prev[current]
            val nx = next[current]
            val tri = Triangle(polygon[p], polygon[current], polygon[nx])
            if (!isDegenerate(tri)) {
                triangles.add(tri)
            }
        } else if (remaining > 3) {
            // Fallback: if we couldn't clip all ears (due to degenerate geometry),
            // try forcing ear clips by relaxing the convexity check
            forceClipRemaining(polygon, prev, next, current, remaining, ccw, triangles)
        }

        return triangles
    }

    /**
     * Fallback: force-clip remaining vertices when standard ear-clipping gets stuck.
     * This handles near-degenerate polygons that can occur in font glyphs.
     */
    private fun forceClipRemaining(
        polygon: MutableList<Pair<Float, Float>>,
        prev: IntArray,
        next: IntArray,
        startIdx: Int,
        remaining: Int,
        ccw: Boolean,
        triangles: MutableList<Triangle>
    ) {
        var current = startIdx
        var left = remaining

        while (left > 3) {
            val p = prev[current]
            val nx = next[current]
            val tri = Triangle(polygon[p], polygon[current], polygon[nx])

            if (!isDegenerate(tri)) {
                triangles.add(tri)
            }

            // Remove current vertex
            next[p] = nx
            prev[nx] = p
            left--
            current = nx
        }

        // Final triangle
        if (left == 3) {
            val p = prev[current]
            val nx = next[current]
            val tri = Triangle(polygon[p], polygon[current], polygon[nx])
            if (!isDegenerate(tri)) {
                triangles.add(tri)
            }
        }
    }

    /**
     * Check if vertex at [idx] forms an ear:
     * - The triangle (prev, idx, next) must be convex (positive cross product for CCW polygon)
     * - No other polygon vertex may lie inside this triangle
     */
    private fun checkEar(
        polygon: List<Pair<Float, Float>>,
        idx: Int,
        prev: IntArray,
        next: IntArray,
        ccw: Boolean
    ): Boolean {
        val p = prev[idx]
        val n = next[idx]
        val a = polygon[p]
        val b = polygon[idx]
        val c = polygon[n]

        // Check convexity: cross product of (b-a) × (c-b) should be positive for CCW
        val cross = cross(a, b, c)
        if (ccw) {
            if (cross <= 0f) return false // reflex vertex
        } else {
            if (cross >= 0f) return false // reflex vertex in CW polygon
        }

        // Check that no other vertex lies inside the triangle (a, b, c)
        var test = next[n]
        while (test != p) {
            if (pointInTriangle(polygon[test], a, b, c)) {
                return false
            }
            test = next[test]
        }

        return true
    }

    /**
     * Compute the signed area of a polygon.
     * Positive = CCW, Negative = CW.
     */
    private fun signedArea(polygon: List<Pair<Float, Float>>): Float {
        var area = 0f
        val n = polygon.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += polygon[i].first * polygon[j].second
            area -= polygon[j].first * polygon[i].second
        }
        return area * 0.5f
    }

    /**
     * Cross product of vectors (b-a) and (c-b).
     * Positive means c is to the left of the line a→b (CCW turn).
     */
    private fun cross(a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>): Float {
        return (b.first - a.first) * (c.second - a.second) -
                (b.second - a.second) * (c.first - a.first)
    }

    /**
     * Check if point [p] is inside triangle [a, b, c] using barycentric coordinates.
     * Returns true if p is strictly inside (not on the boundary).
     */
    private fun pointInTriangle(
        p: Pair<Float, Float>,
        a: Pair<Float, Float>,
        b: Pair<Float, Float>,
        c: Pair<Float, Float>
    ): Boolean {
        val d1 = sign(p, a, b)
        val d2 = sign(p, b, c)
        val d3 = sign(p, c, a)

        val hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0)
        val hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0)

        return !(hasNeg && hasPos)
    }

    /**
     * Sign of cross product for point-in-triangle test.
     */
    private fun sign(
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>
    ): Float {
        return (p1.first - p3.first) * (p2.second - p3.second) -
                (p2.first - p3.first) * (p1.second - p3.second)
    }

    /**
     * Check if a triangle is degenerate (zero or near-zero area).
     */
    private fun isDegenerate(tri: Triangle): Boolean {
        val area = abs(
            (tri.p2.first - tri.p1.first) * (tri.p3.second - tri.p1.second) -
                    (tri.p3.first - tri.p1.first) * (tri.p2.second - tri.p1.second)
        )
        return area < EPSILON
    }

    private const val EPSILON = 1e-7f
}
