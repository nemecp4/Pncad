package com.openscadviewer.engine

import com.openscadviewer.engine.csg.Csg
import com.openscadviewer.engine.text.EarClipTriangulator
import com.openscadviewer.engine.text.FontProvider
import com.openscadviewer.engine.text.TextLayoutEngine
import com.openscadviewer.parser.SceneNode
import kotlin.math.*

/**
 * Generates triangle mesh data from the parsed OpenSCAD scene graph.
 * Produces vertices, normals, and colors for rendering.
 */
class MeshGenerator {

    /**
     * Platform font provider for text rendering.
     * On Android, set to AndroidFontProvider via KotlinComputeEngine constructor.
     * On desktop/benchmark, defaults to AwtFontProvider (loaded lazily to avoid
     * java.awt.Font class loading on Android where AWT is unavailable).
     */
    var fontProvider: FontProvider = createDefaultFontProvider()

    private companion object {
        fun createDefaultFontProvider(): FontProvider {
            return try {
                // AwtFontProvider is only available on desktop JVM (not Android)
                val clazz = Class.forName("com.openscadviewer.engine.text.AwtFontProvider")
                clazz.getDeclaredConstructor().newInstance() as FontProvider
            } catch (e: Exception) {
                // Fallback: no-op provider that returns null outlines (text won't render)
                object : FontProvider {
                    override fun getGlyphOutline(char: Char, fontName: String, size: Float) = null
                    override fun getFontMetrics(fontName: String, size: Float) =
                        com.openscadviewer.engine.text.FontMetrics(0f, 0f, 0f)
                    override fun isFontAvailable(fontName: String) = false
                }
            }
        }
    }

    data class Mesh(
        val vertices: FloatArray,   // x,y,z triplets
        val normals: FloatArray,    // nx,ny,nz triplets
        val colors: FloatArray      // r,g,b,a quads
    ) {
        val vertexCount: Int get() = vertices.size / 3
        val triangleCount: Int get() = vertices.size / 9

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Mesh) return false
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

    private var currentColor = floatArrayOf(0.6f, 0.7f, 0.85f, 1.0f) // Default blue-gray

    fun generate(node: SceneNode): Mesh {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val colors = mutableListOf<Float>()

        generateNode(node, vertices, normals, colors, Matrix4.identity())

        return Mesh(
            vertices.toFloatArray(),
            normals.toFloatArray(),
            colors.toFloatArray()
        )
    }

    private fun generateNode(
        node: SceneNode,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        when (node) {
            is SceneNode.Cube -> {
                if (node.sizeX > 0.0 && node.sizeY > 0.0 && node.sizeZ > 0.0) {
                    generateCube(node, vertices, normals, colors, transform)
                }
            }
            is SceneNode.Sphere -> {
                if (node.radius > 0.0) {
                    generateSphere(node, vertices, normals, colors, transform)
                }
            }
            is SceneNode.Cylinder -> {
                if (node.height > 0.0 && (node.radius1 > 0.0 || node.radius2 > 0.0)) {
                    generateCylinder(node, vertices, normals, colors, transform)
                }
            }
            is SceneNode.Circle -> generateCircle2D(node, vertices, normals, colors, transform)
            is SceneNode.Square -> generateSquare2D(node, vertices, normals, colors, transform)
            is SceneNode.Text -> {
                if (node.text.isNotEmpty() && node.size > 0) {
                    generateText(node, vertices, normals, colors, transform)
                }
            }
            is SceneNode.Polygon -> generatePolygon2D(node, vertices, normals, colors, transform)
            is SceneNode.LinearExtrude -> generateLinearExtrude(node, vertices, normals, colors, transform)
            is SceneNode.Translate -> {
                val t = transform.multiply(Matrix4.translation(node.x.toFloat(), node.y.toFloat(), node.z.toFloat()))
                generateNode(node.child, vertices, normals, colors, t)
            }
            is SceneNode.Rotate -> {
                val t = transform
                    .multiply(Matrix4.rotationZ(node.z.toFloat()))
                    .multiply(Matrix4.rotationY(node.y.toFloat()))
                    .multiply(Matrix4.rotationX(node.x.toFloat()))
                generateNode(node.child, vertices, normals, colors, t)
            }
            is SceneNode.Scale -> {
                val t = transform.multiply(Matrix4.scale(node.x.toFloat(), node.y.toFloat(), node.z.toFloat()))
                generateNode(node.child, vertices, normals, colors, t)
            }
            is SceneNode.Color -> {
                val prevColor = currentColor.copyOf()
                currentColor = floatArrayOf(node.r, node.g, node.b, node.a)
                generateNode(node.child, vertices, normals, colors, transform)
                currentColor = prevColor
            }
            is SceneNode.Union -> {
                for (child in node.children) {
                    generateNode(child, vertices, normals, colors, transform)
                }
            }
            is SceneNode.Difference -> {
                if (node.children.size < 2) {
                    for (child in node.children) {
                        generateNode(child, vertices, normals, colors, transform)
                    }
                } else {
                    generateCsgDifference(node.children, vertices, normals, colors, transform)
                }
            }
            is SceneNode.Intersection -> {
                if (node.children.size < 2) {
                    for (child in node.children) {
                        generateNode(child, vertices, normals, colors, transform)
                    }
                } else {
                    generateCsgIntersection(node.children, vertices, normals, colors, transform)
                }
            }
            is SceneNode.Group -> {
                for (child in node.children) {
                    generateNode(child, vertices, normals, colors, transform)
                }
            }
        }
    }

    private fun generateCube(
        cube: SceneNode.Cube,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val sx = cube.sizeX.toFloat()
        val sy = cube.sizeY.toFloat()
        val sz = cube.sizeZ.toFloat()

        val ox = if (cube.center) -sx / 2f else 0f
        val oy = if (cube.center) -sy / 2f else 0f
        val oz = if (cube.center) -sz / 2f else 0f

        // 6 faces, 2 triangles each = 12 triangles
        val v = arrayOf(
            floatArrayOf(ox, oy, oz),           // 0
            floatArrayOf(ox + sx, oy, oz),      // 1
            floatArrayOf(ox + sx, oy + sy, oz), // 2
            floatArrayOf(ox, oy + sy, oz),      // 3
            floatArrayOf(ox, oy, oz + sz),      // 4
            floatArrayOf(ox + sx, oy, oz + sz), // 5
            floatArrayOf(ox + sx, oy + sy, oz + sz), // 6
            floatArrayOf(ox, oy + sy, oz + sz)  // 7
        )

        val faces = arrayOf(
            // front (z+)
            intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
            // back (z-)
            intArrayOf(1, 0, 3), intArrayOf(1, 3, 2),
            // right (x+)
            intArrayOf(5, 1, 2), intArrayOf(5, 2, 6),
            // left (x-)
            intArrayOf(0, 4, 7), intArrayOf(0, 7, 3),
            // top (y+)
            intArrayOf(7, 6, 2), intArrayOf(7, 2, 3),
            // bottom (y-)
            intArrayOf(0, 1, 5), intArrayOf(0, 5, 4)
        )

        val faceNormals = arrayOf(
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 0f, -1f),
            floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 0f, 0f),
            floatArrayOf(-1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, -1f, 0f)
        )

        for (i in faces.indices) {
            val face = faces[i]
            val normal = faceNormals[i]
            for (idx in face) {
                val transformed = transform.transformPoint(v[idx])
                vertices.addAll(transformed.toList())
                val tn = transform.transformNormal(normal)
                normals.addAll(tn.toList())
                colors.addAll(currentColor.toList())
            }
        }
    }

    private fun generateSphere(
        sphere: SceneNode.Sphere,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val r = sphere.radius.toFloat()
        val segments = sphere.segments
        val rings = segments / 2

        for (i in 0 until rings) {
            val theta1 = PI.toFloat() * i / rings
            val theta2 = PI.toFloat() * (i + 1) / rings

            for (j in 0 until segments) {
                val phi1 = 2f * PI.toFloat() * j / segments
                val phi2 = 2f * PI.toFloat() * (j + 1) / segments

                val p1 = spherePoint(r, theta1, phi1)
                val p2 = spherePoint(r, theta1, phi2)
                val p3 = spherePoint(r, theta2, phi2)
                val p4 = spherePoint(r, theta2, phi1)

                val n1 = normalize(p1)
                val n2 = normalize(p2)
                val n3 = normalize(p3)
                val n4 = normalize(p4)

                // Triangle 1
                addTransformedTriangle(vertices, normals, colors, transform, p1, p3, p2, n1, n3, n2)
                // Triangle 2
                addTransformedTriangle(vertices, normals, colors, transform, p1, p4, p3, n1, n4, n3)
            }
        }
    }

    private fun generateCylinder(
        cyl: SceneNode.Cylinder,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val h = cyl.height.toFloat()
        val r1 = cyl.radius1.toFloat()
        val r2 = cyl.radius2.toFloat()
        val segments = cyl.segments
        val zOffset = if (cyl.center) -h / 2f else 0f

        for (i in 0 until segments) {
            val angle1 = 2f * PI.toFloat() * i / segments
            val angle2 = 2f * PI.toFloat() * (i + 1) / segments

            val cos1 = cos(angle1)
            val sin1 = sin(angle1)
            val cos2 = cos(angle2)
            val sin2 = sin(angle2)

            // Bottom circle
            val b1 = floatArrayOf(r1 * cos1, r1 * sin1, zOffset)
            val b2 = floatArrayOf(r1 * cos2, r1 * sin2, zOffset)
            // Top circle
            val t1 = floatArrayOf(r2 * cos1, r2 * sin1, zOffset + h)
            val t2 = floatArrayOf(r2 * cos2, r2 * sin2, zOffset + h)

            // Side normal (approximate)
            val sideNormal1 = normalize(floatArrayOf(cos1, sin1, (r1 - r2) / h))
            val sideNormal2 = normalize(floatArrayOf(cos2, sin2, (r1 - r2) / h))

            // Side triangles — outward-facing winding (CCW when viewed from outside)
            addTransformedTriangle(vertices, normals, colors, transform, b1, b2, t2, sideNormal1, sideNormal2, sideNormal2)
            addTransformedTriangle(vertices, normals, colors, transform, b1, t2, t1, sideNormal1, sideNormal2, sideNormal1)

            // Bottom cap
            val center_b = floatArrayOf(0f, 0f, zOffset)
            val normalDown = floatArrayOf(0f, 0f, -1f)
            addTransformedTriangle(vertices, normals, colors, transform, center_b, b2, b1, normalDown, normalDown, normalDown)

            // Top cap
            val center_t = floatArrayOf(0f, 0f, zOffset + h)
            val normalUp = floatArrayOf(0f, 0f, 1f)
            addTransformedTriangle(vertices, normals, colors, transform, center_t, t1, t2, normalUp, normalUp, normalUp)
        }
    }

    private fun generateCircle2D(
        circle: SceneNode.Circle,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val r = circle.radius.toFloat()
        val segments = circle.segments
        val normalUp = floatArrayOf(0f, 0f, 1f)
        val center = floatArrayOf(0f, 0f, 0f)

        for (i in 0 until segments) {
            val angle1 = 2f * PI.toFloat() * i / segments
            val angle2 = 2f * PI.toFloat() * (i + 1) / segments
            val p1 = floatArrayOf(r * cos(angle1), r * sin(angle1), 0f)
            val p2 = floatArrayOf(r * cos(angle2), r * sin(angle2), 0f)
            addTransformedTriangle(vertices, normals, colors, transform, center, p1, p2, normalUp, normalUp, normalUp)
        }
    }

    private fun generateSquare2D(
        square: SceneNode.Square,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val sx = square.sizeX.toFloat()
        val sy = square.sizeY.toFloat()
        val ox = if (square.center) -sx / 2f else 0f
        val oy = if (square.center) -sy / 2f else 0f
        val normalUp = floatArrayOf(0f, 0f, 1f)

        val p0 = floatArrayOf(ox, oy, 0f)
        val p1 = floatArrayOf(ox + sx, oy, 0f)
        val p2 = floatArrayOf(ox + sx, oy + sy, 0f)
        val p3 = floatArrayOf(ox, oy + sy, 0f)

        addTransformedTriangle(vertices, normals, colors, transform, p0, p1, p2, normalUp, normalUp, normalUp)
        addTransformedTriangle(vertices, normals, colors, transform, p0, p2, p3, normalUp, normalUp, normalUp)
    }

    private fun generatePolygon2D(
        polygon: SceneNode.Polygon,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        if (polygon.points.size < 3) return
        val normalUp = floatArrayOf(0f, 0f, 1f)

        // Simple fan triangulation from first point
        val p0 = floatArrayOf(polygon.points[0].first.toFloat(), polygon.points[0].second.toFloat(), 0f)
        for (i in 1 until polygon.points.size - 1) {
            val p1 = floatArrayOf(polygon.points[i].first.toFloat(), polygon.points[i].second.toFloat(), 0f)
            val p2 = floatArrayOf(polygon.points[i + 1].first.toFloat(), polygon.points[i + 1].second.toFloat(), 0f)
            addTransformedTriangle(vertices, normals, colors, transform, p0, p1, p2, normalUp, normalUp, normalUp)
        }
    }

    /**
     * Generate 2D triangulated mesh from text glyph outlines at Z=0.
     */
    private fun generateText(
        node: SceneNode.Text,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val positioned = TextLayoutEngine.layout(
            node.text, node.size.toFloat(), node.halign, node.valign,
            node.spacing.toFloat(), node.direction, fontProvider, node.font
        )

        for (glyph in positioned) {
            val translated = glyph.outline.contours.map { contour ->
                contour.map { (x, y) -> Pair(x + glyph.x, y + glyph.y) }
            }
            val triangles = EarClipTriangulator.triangulate(translated)
            for (tri in triangles) {
                addTriangle2D(tri, vertices, normals, colors, transform)
            }
        }
    }

    /**
     * Add a 2D triangle (at Z=0 with normal 0,0,1) to the vertex buffers.
     */
    private fun addTriangle2D(
        tri: EarClipTriangulator.Triangle,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val normalUp = floatArrayOf(0f, 0f, 1f)
        val p1 = floatArrayOf(tri.p1.first, tri.p1.second, 0f)
        val p2 = floatArrayOf(tri.p2.first, tri.p2.second, 0f)
        val p3 = floatArrayOf(tri.p3.first, tri.p3.second, 0f)
        addTransformedTriangle(vertices, normals, colors, transform, p1, p2, p3, normalUp, normalUp, normalUp)
    }

    private fun generateLinearExtrude(
        extrude: SceneNode.LinearExtrude,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val h = extrude.height.toFloat()

        // Special handling for Text child: use glyph-based extrusion with proper triangulation
        val textChild = unwrapToText(extrude.child)
        if (textChild != null && textChild.text.isNotEmpty() && textChild.size > 0) {
            generateTextExtrusion(textChild, h, vertices, normals, colors, transform)
            return
        }

        // Extract 2D outline points from the child node(s)
        val outlines = extract2DOutlines(extrude.child)

        for (outline in outlines) {
            if (outline.size < 3) continue

            // Bottom face (z=0) — fan triangulation with reversed winding for outward normal
            val normalDown = floatArrayOf(0f, 0f, -1f)
            val bottomCenter = floatArrayOf(
                outline.map { it[0] }.average().toFloat(),
                outline.map { it[1] }.average().toFloat(),
                0f
            )
            for (i in 0 until outline.size - 1) {
                val p1 = floatArrayOf(outline[i][0], outline[i][1], 0f)
                val p2 = floatArrayOf(outline[i + 1][0], outline[i + 1][1], 0f)
                addTransformedTriangle(vertices, normals, colors, transform,
                    bottomCenter, p2, p1, normalDown, normalDown, normalDown)
            }
            // Close the fan
            val pLast = floatArrayOf(outline.last()[0], outline.last()[1], 0f)
            val pFirst = floatArrayOf(outline.first()[0], outline.first()[1], 0f)
            addTransformedTriangle(vertices, normals, colors, transform,
                bottomCenter, pFirst, pLast, normalDown, normalDown, normalDown)

            // Top face (z=h) — fan triangulation with normal winding
            val normalUp = floatArrayOf(0f, 0f, 1f)
            val topCenter = floatArrayOf(bottomCenter[0], bottomCenter[1], h)
            for (i in 0 until outline.size - 1) {
                val p1 = floatArrayOf(outline[i][0], outline[i][1], h)
                val p2 = floatArrayOf(outline[i + 1][0], outline[i + 1][1], h)
                addTransformedTriangle(vertices, normals, colors, transform,
                    topCenter, p1, p2, normalUp, normalUp, normalUp)
            }
            val tLast = floatArrayOf(outline.last()[0], outline.last()[1], h)
            val tFirst = floatArrayOf(outline.first()[0], outline.first()[1], h)
            addTransformedTriangle(vertices, normals, colors, transform,
                topCenter, tLast, tFirst, normalUp, normalUp, normalUp)

            // Side walls — quads between bottom and top outline edges
            for (i in outline.indices) {
                val next = (i + 1) % outline.size
                val b1 = floatArrayOf(outline[i][0], outline[i][1], 0f)
                val b2 = floatArrayOf(outline[next][0], outline[next][1], 0f)
                val t1 = floatArrayOf(outline[i][0], outline[i][1], h)
                val t2 = floatArrayOf(outline[next][0], outline[next][1], h)

                // Compute outward normal for this wall segment
                val dx = b2[0] - b1[0]
                val dy = b2[1] - b1[1]
                val len = sqrt(dx * dx + dy * dy)
                val wallNormal = if (len > 0.0001f)
                    floatArrayOf(dy / len, -dx / len, 0f)
                else
                    floatArrayOf(1f, 0f, 0f)

                // Two triangles per quad
                addTransformedTriangle(vertices, normals, colors, transform,
                    b1, b2, t2, wallNormal, wallNormal, wallNormal)
                addTransformedTriangle(vertices, normals, colors, transform,
                    b1, t2, t1, wallNormal, wallNormal, wallNormal)
            }
        }
    }

    /**
     * Unwrap a node to find a Text child (skipping Color, Group with single child).
     */
    private fun unwrapToText(node: SceneNode): SceneNode.Text? {
        return when (node) {
            is SceneNode.Text -> node
            is SceneNode.Color -> unwrapToText(node.child)
            is SceneNode.Group -> if (node.children.size == 1) unwrapToText(node.children[0]) else null
            else -> null
        }
    }

    /**
     * Generate extruded 3D mesh from text glyph outlines.
     * Produces top face at Z=height, bottom face at Z=0, and side walls.
     */
    private fun generateTextExtrusion(
        textNode: SceneNode.Text,
        height: Float,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val positioned = TextLayoutEngine.layout(
            textNode.text, textNode.size.toFloat(), textNode.halign, textNode.valign,
            textNode.spacing.toFloat(), textNode.direction, fontProvider, textNode.font
        )

        val normalUp = floatArrayOf(0f, 0f, 1f)
        val normalDown = floatArrayOf(0f, 0f, -1f)

        for (glyph in positioned) {
            val translatedContours = glyph.outline.contours.map { contour ->
                contour.map { (x, y) -> Pair(x + glyph.x, y + glyph.y) }
            }

            // Triangulate the glyph polygon (handles holes via ear-clipping)
            val triangles = EarClipTriangulator.triangulate(translatedContours)

            // Top face at Z=height (normal 0,0,1)
            for (tri in triangles) {
                val p1 = floatArrayOf(tri.p1.first, tri.p1.second, height)
                val p2 = floatArrayOf(tri.p2.first, tri.p2.second, height)
                val p3 = floatArrayOf(tri.p3.first, tri.p3.second, height)
                addTransformedTriangle(vertices, normals, colors, transform,
                    p1, p2, p3, normalUp, normalUp, normalUp)
            }

            // Bottom face at Z=0 (normal 0,0,-1, reversed winding)
            for (tri in triangles) {
                val p1 = floatArrayOf(tri.p1.first, tri.p1.second, 0f)
                val p2 = floatArrayOf(tri.p2.first, tri.p2.second, 0f)
                val p3 = floatArrayOf(tri.p3.first, tri.p3.second, 0f)
                // Reverse winding: p1, p3, p2 instead of p1, p2, p3
                addTransformedTriangle(vertices, normals, colors, transform,
                    p1, p3, p2, normalDown, normalDown, normalDown)
            }

            // Side faces: connect top and bottom contour edges
            for (contour in translatedContours) {
                if (contour.size < 2) continue
                for (i in contour.indices) {
                    val next = (i + 1) % contour.size
                    val x1 = contour[i].first
                    val y1 = contour[i].second
                    val x2 = contour[next].first
                    val y2 = contour[next].second

                    val b1 = floatArrayOf(x1, y1, 0f)
                    val b2 = floatArrayOf(x2, y2, 0f)
                    val t1 = floatArrayOf(x1, y1, height)
                    val t2 = floatArrayOf(x2, y2, height)

                    // Compute outward normal for this wall segment
                    val dx = x2 - x1
                    val dy = y2 - y1
                    val len = sqrt(dx * dx + dy * dy)
                    val wallNormal = if (len > 0.0001f)
                        floatArrayOf(dy / len, -dx / len, 0f)
                    else
                        floatArrayOf(1f, 0f, 0f)

                    // Two triangles per quad
                    addTransformedTriangle(vertices, normals, colors, transform,
                        b1, b2, t2, wallNormal, wallNormal, wallNormal)
                    addTransformedTriangle(vertices, normals, colors, transform,
                        b1, t2, t1, wallNormal, wallNormal, wallNormal)
                }
            }
        }
    }

    /**
     * Extracts 2D outline point lists from a SceneNode (for use by linear_extrude).
     * Returns a list of outlines (each is a list of [x, y] float arrays).
     */
    private fun extract2DOutlines(node: SceneNode): List<List<FloatArray>> {
        return when (node) {
            is SceneNode.Circle -> {
                val r = node.radius.toFloat()
                val segments = node.segments
                val points = (0 until segments).map { i ->
                    val angle = 2f * PI.toFloat() * i / segments
                    floatArrayOf(r * cos(angle), r * sin(angle))
                }
                listOf(points)
            }
            is SceneNode.Square -> {
                val sx = node.sizeX.toFloat()
                val sy = node.sizeY.toFloat()
                val ox = if (node.center) -sx / 2f else 0f
                val oy = if (node.center) -sy / 2f else 0f
                val points = listOf(
                    floatArrayOf(ox, oy),
                    floatArrayOf(ox + sx, oy),
                    floatArrayOf(ox + sx, oy + sy),
                    floatArrayOf(ox, oy + sy)
                )
                listOf(points)
            }
            is SceneNode.Text -> {
                if (node.text.isNotEmpty() && node.size > 0) {
                    // Use real glyph contours from font provider
                    val positioned = TextLayoutEngine.layout(
                        node.text, node.size.toFloat(), node.halign, node.valign,
                        node.spacing.toFloat(), node.direction, fontProvider, node.font
                    )
                    positioned.flatMap { glyph ->
                        glyph.outline.contours.map { contour ->
                            contour.map { (x, y) ->
                                floatArrayOf(x + glyph.x, y + glyph.y)
                            }
                        }
                    }
                } else {
                    emptyList()
                }
            }
            is SceneNode.Polygon -> {
                val points = node.points.map { (x, y) ->
                    floatArrayOf(x.toFloat(), y.toFloat())
                }
                if (points.size >= 3) listOf(points) else emptyList()
            }
            is SceneNode.Union -> {
                node.children.flatMap { extract2DOutlines(it) }
            }
            is SceneNode.Group -> {
                node.children.flatMap { extract2DOutlines(it) }
            }
            is SceneNode.Translate -> {
                val childOutlines = extract2DOutlines(node.child)
                childOutlines.map { outline ->
                    outline.map { p ->
                        floatArrayOf(p[0] + node.x.toFloat(), p[1] + node.y.toFloat())
                    }
                }
            }
            is SceneNode.Rotate -> {
                // Apply 2D rotation (z-axis only for 2D context)
                val angle = node.z.toFloat() * PI.toFloat() / 180f
                val cosA = cos(angle)
                val sinA = sin(angle)
                val childOutlines = extract2DOutlines(node.child)
                childOutlines.map { outline ->
                    outline.map { p ->
                        floatArrayOf(
                            p[0] * cosA - p[1] * sinA,
                            p[0] * sinA + p[1] * cosA
                        )
                    }
                }
            }
            is SceneNode.Scale -> {
                val childOutlines = extract2DOutlines(node.child)
                childOutlines.map { outline ->
                    outline.map { p ->
                        floatArrayOf(p[0] * node.x.toFloat(), p[1] * node.y.toFloat())
                    }
                }
            }
            is SceneNode.Color -> extract2DOutlines(node.child)
            is SceneNode.Difference -> {
                // Simplified: just use the first child's outline
                if (node.children.isNotEmpty()) extract2DOutlines(node.children[0])
                else emptyList()
            }
            is SceneNode.Intersection -> {
                if (node.children.isNotEmpty()) extract2DOutlines(node.children[0])
                else emptyList()
            }
            else -> emptyList()
        }
    }

    // --- CSG methods ---

    private fun generateCsgDifference(
        children: List<SceneNode>,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        // Generate meshes for each child separately
        val meshes = children.map { child ->
            val childVerts = mutableListOf<Float>()
            val childNorms = mutableListOf<Float>()
            val childColors = mutableListOf<Float>()
            generateNode(child, childVerts, childNorms, childColors, Matrix4.identity())
            Triple(childVerts.toFloatArray(), childNorms.toFloatArray(), childColors.toFloatArray())
        }

        // First child is the base
        var result = Csg.fromTriangles(meshes[0].first, meshes[0].second)

        // Subtract subsequent children.
        // For Group/Union children with multiple sub-meshes, union them first
        // before subtracting. This handles for-loop expansions correctly where
        // multiple overlapping shapes should form one solid subtraction volume.
        for (i in 1 until meshes.size) {
            val childNode = children[i]
            val other: Csg

            if (isCompoundNode(childNode)) {
                // Union the individual sub-meshes of this compound child
                other = buildUnionedCsg(childNode)
            } else {
                other = Csg.fromTriangles(meshes[i].first, meshes[i].second)
            }

            if (other.polygons.isNotEmpty()) {
                result = result.subtract(other)
            }
        }

        // Convert result back to triangle arrays and apply transform
        val (resultVerts, resultNorms) = result.toTriangles()
        for (i in 0 until resultVerts.size / 3) {
            val base = i * 3
            val p = transform.transformPoint(floatArrayOf(resultVerts[base], resultVerts[base+1], resultVerts[base+2]))
            val n = transform.transformNormal(floatArrayOf(resultNorms[base], resultNorms[base+1], resultNorms[base+2]))
            vertices.addAll(p.toList())
            normals.addAll(n.toList())
            colors.addAll(currentColor.toList())
        }
    }

    /**
     * Returns true if the node is a compound that contains multiple geometry children
     * (e.g., Group, Union wrapping a for-loop expansion).
     */
    private fun isCompoundNode(node: SceneNode): Boolean {
        return when (node) {
            is SceneNode.Group -> node.children.size > 1
            is SceneNode.Union -> node.children.size > 1
            is SceneNode.Translate -> isCompoundNode(node.child)
            is SceneNode.Rotate -> isCompoundNode(node.child)
            is SceneNode.Scale -> isCompoundNode(node.child)
            is SceneNode.Color -> isCompoundNode(node.child)
            else -> false
        }
    }

    /**
     * Builds a CSG solid by unioning all sub-geometries of a compound node.
     * Used for subtraction operands that contain multiple overlapping shapes
     * (e.g., for-loop generated cubes that should form one cutting volume).
     */
    private fun buildUnionedCsg(node: SceneNode): Csg {
        val leaves = collectLeafGeometries(node, Matrix4.identity())
        if (leaves.isEmpty()) return Csg.fromTriangles(FloatArray(0), FloatArray(0))

        var result = leaves[0]
        for (i in 1 until leaves.size) {
            if (leaves[i].polygons.isNotEmpty()) {
                result = result.union(leaves[i])
            }
        }
        return result
    }

    /**
     * Recursively collects individual geometry CSG solids from a compound node,
     * applying transforms as it descends.
     */
    private fun collectLeafGeometries(node: SceneNode, transform: Matrix4): List<Csg> {
        return when (node) {
            is SceneNode.Group -> node.children.flatMap { collectLeafGeometries(it, transform) }
            is SceneNode.Union -> node.children.flatMap { collectLeafGeometries(it, transform) }
            is SceneNode.Translate -> {
                val t = transform.multiply(Matrix4.translation(node.x.toFloat(), node.y.toFloat(), node.z.toFloat()))
                collectLeafGeometries(node.child, t)
            }
            is SceneNode.Rotate -> {
                val t = transform
                    .multiply(Matrix4.rotationZ(node.z.toFloat()))
                    .multiply(Matrix4.rotationY(node.y.toFloat()))
                    .multiply(Matrix4.rotationX(node.x.toFloat()))
                collectLeafGeometries(node.child, t)
            }
            is SceneNode.Scale -> {
                val t = transform.multiply(Matrix4.scale(node.x.toFloat(), node.y.toFloat(), node.z.toFloat()))
                collectLeafGeometries(node.child, t)
            }
            is SceneNode.Color -> collectLeafGeometries(node.child, transform)
            else -> {
                // Leaf geometry — generate mesh and create CSG
                val verts = mutableListOf<Float>()
                val norms = mutableListOf<Float>()
                val cols = mutableListOf<Float>()
                generateNode(node, verts, norms, cols, transform)
                if (verts.isNotEmpty()) {
                    listOf(Csg.fromTriangles(verts.toFloatArray(), norms.toFloatArray()))
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun generateCsgIntersection(
        children: List<SceneNode>,
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4
    ) {
        val meshes = children.map { child ->
            val childVerts = mutableListOf<Float>()
            val childNorms = mutableListOf<Float>()
            val childColors = mutableListOf<Float>()
            generateNode(child, childVerts, childNorms, childColors, Matrix4.identity())
            Triple(childVerts.toFloatArray(), childNorms.toFloatArray(), childColors.toFloatArray())
        }

        var result = Csg.fromTriangles(meshes[0].first, meshes[0].second)

        for (i in 1 until meshes.size) {
            val other = Csg.fromTriangles(meshes[i].first, meshes[i].second)
            if (other.polygons.isNotEmpty()) {
                result = result.intersect(other)
            }
        }

        val (resultVerts, resultNorms) = result.toTriangles()
        for (i in 0 until resultVerts.size / 3) {
            val base = i * 3
            val p = transform.transformPoint(floatArrayOf(resultVerts[base], resultVerts[base+1], resultVerts[base+2]))
            val n = transform.transformNormal(floatArrayOf(resultNorms[base], resultNorms[base+1], resultNorms[base+2]))
            vertices.addAll(p.toList())
            normals.addAll(n.toList())
            colors.addAll(currentColor.toList())
        }
    }

    // --- Utility methods ---

    private fun addTransformedTriangle(
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        colors: MutableList<Float>,
        transform: Matrix4,
        p1: FloatArray, p2: FloatArray, p3: FloatArray,
        n1: FloatArray, n2: FloatArray, n3: FloatArray
    ) {
        val tp1 = transform.transformPoint(p1)
        val tp2 = transform.transformPoint(p2)
        val tp3 = transform.transformPoint(p3)
        val tn1 = transform.transformNormal(n1)
        val tn2 = transform.transformNormal(n2)
        val tn3 = transform.transformNormal(n3)

        vertices.addAll(tp1.toList())
        vertices.addAll(tp2.toList())
        vertices.addAll(tp3.toList())

        normals.addAll(tn1.toList())
        normals.addAll(tn2.toList())
        normals.addAll(tn3.toList())

        colors.addAll(currentColor.toList())
        colors.addAll(currentColor.toList())
        colors.addAll(currentColor.toList())
    }

    private fun spherePoint(r: Float, theta: Float, phi: Float): FloatArray {
        return floatArrayOf(
            r * sin(theta) * cos(phi),
            r * sin(theta) * sin(phi),
            r * cos(theta)
        )
    }

    private fun normalize(v: FloatArray): FloatArray {
        val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (len > 0.0001f) floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
        else floatArrayOf(0f, 0f, 1f)
    }
}
