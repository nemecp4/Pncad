#include "scene_builder.h"
#include <CGAL/Polyhedron_incremental_builder_3.h>
#include <cmath>
#include <stdexcept>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

using json = nlohmann::json;
using HalfedgeDS = Polyhedron::HalfedgeDS;

// Default color: (0.6, 0.7, 0.85, 1.0)
const SceneColor SceneColor::DEFAULT = {0.6f, 0.7f, 0.85f, 1.0f};

// ---------------------------------------------------------------------------
// Cancellation check helper
// ---------------------------------------------------------------------------

static inline bool is_cancelled(std::atomic<bool>& flag) {
    return flag.load(std::memory_order_relaxed);
}

// ---------------------------------------------------------------------------
// Polyhedron builder for constructing meshes from vertices and triangle faces
// ---------------------------------------------------------------------------

template <class HDS>
class Build_polyhedron : public CGAL::Modifier_base<HDS> {
public:
    using Point = typename HDS::Vertex::Point;

    Build_polyhedron(const std::vector<Point_3>& verts,
                     const std::vector<std::array<int, 3>>& faces)
        : vertices_(verts), faces_(faces) {}

    void operator()(HDS& hds) override {
        CGAL::Polyhedron_incremental_builder_3<HDS> builder(hds, true);
        builder.begin_surface(vertices_.size(), faces_.size());

        for (const auto& v : vertices_) {
            builder.add_vertex(v);
        }

        for (const auto& f : faces_) {
            builder.begin_facet();
            builder.add_vertex_to_facet(f[0]);
            builder.add_vertex_to_facet(f[1]);
            builder.add_vertex_to_facet(f[2]);
            builder.end_facet();
        }

        builder.end_surface();
    }

private:
    const std::vector<Point_3>& vertices_;
    const std::vector<std::array<int, 3>>& faces_;
};

// ---------------------------------------------------------------------------
// Helper: Build a Nef polyhedron from vertices and triangle faces
// ---------------------------------------------------------------------------

static Nef_polyhedron make_nef_from_mesh(const std::vector<Point_3>& vertices,
                                         const std::vector<std::array<int, 3>>& faces) {
    Polyhedron poly;
    Build_polyhedron<HalfedgeDS> builder(vertices, faces);
    poly.delegate(builder);

    if (poly.empty() || !poly.is_closed()) {
        return Nef_polyhedron();  // Return empty on degenerate mesh
    }

    return Nef_polyhedron(poly);
}

// ---------------------------------------------------------------------------
// Primitive construction: Cube
// ---------------------------------------------------------------------------

static Nef_polyhedron build_cube(double sx, double sy, double sz, bool center) {
    if (sx <= 0 || sy <= 0 || sz <= 0) {
        return Nef_polyhedron();  // Degenerate
    }

    double x0, y0, z0, x1, y1, z1;
    if (center) {
        x0 = -sx / 2.0; y0 = -sy / 2.0; z0 = -sz / 2.0;
        x1 =  sx / 2.0; y1 =  sy / 2.0; z1 =  sz / 2.0;
    } else {
        x0 = 0; y0 = 0; z0 = 0;
        x1 = sx; y1 = sy; z1 = sz;
    }

    std::vector<Point_3> verts = {
        Point_3(x0, y0, z0),  // 0
        Point_3(x1, y0, z0),  // 1
        Point_3(x1, y1, z0),  // 2
        Point_3(x0, y1, z0),  // 3
        Point_3(x0, y0, z1),  // 4
        Point_3(x1, y0, z1),  // 5
        Point_3(x1, y1, z1),  // 6
        Point_3(x0, y1, z1),  // 7
    };

    // 12 triangles (2 per face), winding outward
    std::vector<std::array<int, 3>> faces = {
        // Bottom (z=z0) - normal -Z
        {0, 2, 1}, {0, 3, 2},
        // Top (z=z1) - normal +Z
        {4, 5, 6}, {4, 6, 7},
        // Front (y=y0) - normal -Y
        {0, 1, 5}, {0, 5, 4},
        // Back (y=y1) - normal +Y
        {2, 3, 7}, {2, 7, 6},
        // Left (x=x0) - normal -X
        {0, 4, 7}, {0, 7, 3},
        // Right (x=x1) - normal +X
        {1, 2, 6}, {1, 6, 5},
    };

    return make_nef_from_mesh(verts, faces);
}

// ---------------------------------------------------------------------------
// Primitive construction: Sphere (tessellated with longitude/latitude bands)
// ---------------------------------------------------------------------------

static Nef_polyhedron build_sphere(double radius, int segments) {
    if (radius <= 0) {
        return Nef_polyhedron();  // Degenerate
    }

    if (segments < 4) segments = 4;

    int lon_steps = segments;
    int lat_steps = segments / 2;
    if (lat_steps < 2) lat_steps = 2;

    std::vector<Point_3> verts;
    std::vector<std::array<int, 3>> faces;

    // Generate vertices: poles + grid
    // Index 0 = north pole, index 1..((lat_steps-1)*lon_steps) = grid,
    // last index = south pole

    // North pole
    verts.push_back(Point_3(0, 0, radius));
    int north_idx = 0;

    // Latitude bands (from top to bottom, excluding poles)
    for (int lat = 1; lat < lat_steps; ++lat) {
        double phi = M_PI * static_cast<double>(lat) / static_cast<double>(lat_steps);
        double sp = std::sin(phi);
        double cp = std::cos(phi);
        for (int lon = 0; lon < lon_steps; ++lon) {
            double theta = 2.0 * M_PI * static_cast<double>(lon) / static_cast<double>(lon_steps);
            double x = radius * sp * std::cos(theta);
            double y = radius * sp * std::sin(theta);
            double z = radius * cp;
            verts.push_back(Point_3(x, y, z));
        }
    }

    // South pole
    verts.push_back(Point_3(0, 0, -radius));
    int south_idx = static_cast<int>(verts.size()) - 1;

    // Triangles connecting north pole to first latitude ring
    for (int lon = 0; lon < lon_steps; ++lon) {
        int next = (lon + 1) % lon_steps;
        int ring_base = 1;  // first ring starts at index 1
        faces.push_back({north_idx, ring_base + next, ring_base + lon});
    }

    // Triangles between adjacent latitude rings
    for (int lat = 0; lat < lat_steps - 2; ++lat) {
        int ring_curr = 1 + lat * lon_steps;
        int ring_next = 1 + (lat + 1) * lon_steps;
        for (int lon = 0; lon < lon_steps; ++lon) {
            int next_lon = (lon + 1) % lon_steps;
            // Two triangles per quad
            faces.push_back({ring_curr + lon, ring_next + lon, ring_next + next_lon});
            faces.push_back({ring_curr + lon, ring_next + next_lon, ring_curr + next_lon});
        }
    }

    // Triangles connecting last latitude ring to south pole
    int last_ring = 1 + (lat_steps - 2) * lon_steps;
    for (int lon = 0; lon < lon_steps; ++lon) {
        int next = (lon + 1) % lon_steps;
        faces.push_back({last_ring + lon, south_idx, last_ring + next});
    }

    return make_nef_from_mesh(verts, faces);
}

// ---------------------------------------------------------------------------
// Primitive construction: Cylinder (supports radius1 != radius2 for cones)
// ---------------------------------------------------------------------------

static Nef_polyhedron build_cylinder(double height, double r1, double r2,
                                     bool center, int segments) {
    if (height <= 0 || (r1 <= 0 && r2 <= 0)) {
        return Nef_polyhedron();  // Degenerate
    }

    if (segments < 3) segments = 3;

    double z0 = center ? -height / 2.0 : 0.0;
    double z1 = center ?  height / 2.0 : height;

    std::vector<Point_3> verts;
    std::vector<std::array<int, 3>> faces;

    // Bottom ring (at z0 with radius r1)
    int bottom_start = 0;
    for (int i = 0; i < segments; ++i) {
        double theta = 2.0 * M_PI * static_cast<double>(i) / static_cast<double>(segments);
        double x = r1 * std::cos(theta);
        double y = r1 * std::sin(theta);
        verts.push_back(Point_3(x, y, z0));
    }

    // Top ring (at z1 with radius r2)
    int top_start = segments;
    for (int i = 0; i < segments; ++i) {
        double theta = 2.0 * M_PI * static_cast<double>(i) / static_cast<double>(segments);
        double x = r2 * std::cos(theta);
        double y = r2 * std::sin(theta);
        verts.push_back(Point_3(x, y, z1));
    }

    // Bottom center point
    int bottom_center = static_cast<int>(verts.size());
    verts.push_back(Point_3(0, 0, z0));

    // Top center point
    int top_center = static_cast<int>(verts.size());
    verts.push_back(Point_3(0, 0, z1));

    // Side faces (quads as 2 triangles)
    for (int i = 0; i < segments; ++i) {
        int next = (i + 1) % segments;
        int b0 = bottom_start + i;
        int b1 = bottom_start + next;
        int t0 = top_start + i;
        int t1 = top_start + next;

        if (r1 > 0 && r2 > 0) {
            // Normal quad: two triangles
            faces.push_back({b0, b1, t1});
            faces.push_back({b0, t1, t0});
        } else if (r1 > 0 && r2 <= 0) {
            // Cone top: triangle from bottom edge to top center
            faces.push_back({b0, b1, top_center});
        } else if (r1 <= 0 && r2 > 0) {
            // Cone bottom: triangle from top edge to bottom center
            faces.push_back({t1, t0, bottom_center});
        }
    }

    // Bottom cap (fan from center, only if r1 > 0)
    if (r1 > 0) {
        for (int i = 0; i < segments; ++i) {
            int next = (i + 1) % segments;
            faces.push_back({bottom_center, bottom_start + next, bottom_start + i});
        }
    }

    // Top cap (fan from center, only if r2 > 0)
    if (r2 > 0) {
        for (int i = 0; i < segments; ++i) {
            int next = (i + 1) % segments;
            faces.push_back({top_center, top_start + i, top_start + next});
        }
    }

    return make_nef_from_mesh(verts, faces);
}

// ---------------------------------------------------------------------------
// LinearExtrude: Extrude a 2D profile along Z axis
// ---------------------------------------------------------------------------

// Forward declarations for transform helpers (defined later in this file)
static Aff_transformation make_translation(double tx, double ty, double tz);
static Aff_transformation make_scale(double sx, double sy, double sz);
static Aff_transformation make_rotation(double rx, double ry, double rz);

static Nef_polyhedron build_linear_extrude_circle(double height, double radius, int segments) {
    // A circle extruded along Z is essentially a cylinder with equal radii
    if (height <= 0 || radius <= 0) {
        return Nef_polyhedron();
    }
    return build_cylinder(height, radius, radius, false, segments);
}

static Nef_polyhedron build_linear_extrude_square(double height, double sx, double sy, bool center) {
    if (height <= 0 || sx <= 0 || sy <= 0) {
        return Nef_polyhedron();
    }

    // A square extruded along Z is a box
    double x0, y0, x1, y1;
    if (center) {
        x0 = -sx / 2.0; y0 = -sy / 2.0;
        x1 =  sx / 2.0; y1 =  sy / 2.0;
    } else {
        x0 = 0; y0 = 0;
        x1 = sx; y1 = sy;
    }

    double z0 = 0;
    double z1 = height;

    std::vector<Point_3> verts = {
        Point_3(x0, y0, z0), Point_3(x1, y0, z0),
        Point_3(x1, y1, z0), Point_3(x0, y1, z0),
        Point_3(x0, y0, z1), Point_3(x1, y0, z1),
        Point_3(x1, y1, z1), Point_3(x0, y1, z1),
    };

    std::vector<std::array<int, 3>> faces = {
        {0, 2, 1}, {0, 3, 2},
        {4, 5, 6}, {4, 6, 7},
        {0, 1, 5}, {0, 5, 4},
        {2, 3, 7}, {2, 7, 6},
        {0, 4, 7}, {0, 7, 3},
        {1, 2, 6}, {1, 6, 5},
    };

    return make_nef_from_mesh(verts, faces);
}

static Nef_polyhedron build_linear_extrude_polygon(double height,
                                                    const std::vector<std::pair<double, double>>& points) {
    if (height <= 0 || points.size() < 3) {
        return Nef_polyhedron();
    }

    int n = static_cast<int>(points.size());

    std::vector<Point_3> verts;
    std::vector<std::array<int, 3>> faces;

    // Bottom face vertices (z=0)
    for (int i = 0; i < n; ++i) {
        verts.push_back(Point_3(points[i].first, points[i].second, 0));
    }
    // Top face vertices (z=height)
    for (int i = 0; i < n; ++i) {
        verts.push_back(Point_3(points[i].first, points[i].second, height));
    }

    // Bottom face triangulation (fan from vertex 0) - reverse winding for outward normal
    for (int i = 1; i < n - 1; ++i) {
        faces.push_back({0, i + 1, i});
    }

    // Top face triangulation (fan from vertex n)
    for (int i = 1; i < n - 1; ++i) {
        faces.push_back({n, n + i, n + i + 1});
    }

    // Side faces (quads as 2 triangles)
    for (int i = 0; i < n; ++i) {
        int next = (i + 1) % n;
        int b0 = i;
        int b1 = next;
        int t0 = n + i;
        int t1 = n + next;
        faces.push_back({b0, b1, t1});
        faces.push_back({b0, t1, t0});
    }

    return make_nef_from_mesh(verts, faces);
}

static Nef_polyhedron build_linear_extrude(double height, const json& child_node,
                                           std::atomic<bool>& cancel_flag) {
    if (height <= 0) {
        return Nef_polyhedron();
    }

    if (!child_node.contains("type")) {
        return Nef_polyhedron();
    }

    std::string child_type = child_node["type"].get<std::string>();

    if (child_type == "circle") {
        double radius = child_node.value("radius", 0.0);
        int segments = child_node.value("segments", 32);
        return build_linear_extrude_circle(height, radius, segments);
    } else if (child_type == "square") {
        double sx = child_node.value("sizeX", 0.0);
        double sy = child_node.value("sizeY", 0.0);
        bool center = child_node.value("center", false);
        return build_linear_extrude_square(height, sx, sy, center);
    } else if (child_type == "polygon") {
        std::vector<std::pair<double, double>> points;
        if (child_node.contains("points") && child_node["points"].is_array()) {
            for (const auto& pt : child_node["points"]) {
                if (pt.is_array() && pt.size() >= 2) {
                    points.emplace_back(pt[static_cast<size_t>(0)].get<double>(), pt[static_cast<size_t>(1)].get<double>());
                }
            }
        }
        return build_linear_extrude_polygon(height, points);
    } else if (child_type == "union" || child_type == "group") {
        // Extrude each child and union the results
        if (!child_node.contains("children") || !child_node["children"].is_array()) {
            return Nef_polyhedron();
        }
        Nef_polyhedron result;
        bool first = true;
        for (const auto& grandchild : child_node["children"]) {
            if (is_cancelled(cancel_flag)) return Nef_polyhedron();
            Nef_polyhedron extruded = build_linear_extrude(height, grandchild, cancel_flag);
            if (!extruded.is_empty()) {
                if (first) {
                    result = std::move(extruded);
                    first = false;
                } else {
                    result += extruded;
                }
            }
        }
        return result;
    } else if (child_type == "translate") {
        // Extrude the child, then apply translation
        double tx = child_node.value("x", 0.0);
        double ty = child_node.value("y", 0.0);
        double tz = child_node.value("z", 0.0);
        if (child_node.contains("child")) {
            Nef_polyhedron extruded = build_linear_extrude(height, child_node["child"], cancel_flag);
            if (!extruded.is_empty()) {
                Aff_transformation transform = make_translation(tx, ty, tz);
                extruded.transform(transform);
            }
            return extruded;
        }
        return Nef_polyhedron();
    } else if (child_type == "rotate") {
        // Extrude the child, then apply rotation
        double rx = child_node.value("x", 0.0);
        double ry = child_node.value("y", 0.0);
        double rz = child_node.value("z", 0.0);
        if (child_node.contains("child")) {
            Nef_polyhedron extruded = build_linear_extrude(height, child_node["child"], cancel_flag);
            if (!extruded.is_empty()) {
                Aff_transformation transform = make_rotation(rx, ry, rz);
                extruded.transform(transform);
            }
            return extruded;
        }
        return Nef_polyhedron();
    } else if (child_type == "scale") {
        double sx = child_node.value("x", 1.0);
        double sy = child_node.value("y", 1.0);
        double sz = child_node.value("z", 1.0);
        if (child_node.contains("child")) {
            Nef_polyhedron extruded = build_linear_extrude(height, child_node["child"], cancel_flag);
            if (!extruded.is_empty()) {
                Aff_transformation transform = make_scale(sx, sy, sz);
                extruded.transform(transform);
            }
            return extruded;
        }
        return Nef_polyhedron();
    }

    // Unsupported child type for linear extrude
    return Nef_polyhedron();
}

// ---------------------------------------------------------------------------
// Transform construction
// ---------------------------------------------------------------------------

static Aff_transformation make_translation(double tx, double ty, double tz) {
    return Aff_transformation(CGAL::TRANSLATION, Vector_3(tx, ty, tz));
}

static Aff_transformation make_scale(double sx, double sy, double sz) {
    // CGAL Aff_transformation_3 with general matrix (homogeneous coordinates)
    // The constructor takes 12 values + denominator for a 3x4 matrix:
    // | m00 m01 m02 m03 |
    // | m10 m11 m12 m13 |
    // | m20 m21 m22 m23 |
    // with a common denominator hw
    // We use integer-based exact rational representation
    // For non-uniform scale, we use the general constructor
    return Aff_transformation(
        sx, 0, 0, 0,
        0, sy, 0, 0,
        0, 0, sz, 0,
        1  // hw (denominator)
    );
}

static Aff_transformation make_rotation_z(double angle_deg) {
    double rad = angle_deg * M_PI / 180.0;
    double c = std::cos(rad);
    double s = std::sin(rad);
    return Aff_transformation(
        c, -s, 0, 0,
        s,  c, 0, 0,
        0,  0, 1, 0,
        1
    );
}

static Aff_transformation make_rotation_y(double angle_deg) {
    double rad = angle_deg * M_PI / 180.0;
    double c = std::cos(rad);
    double s = std::sin(rad);
    return Aff_transformation(
         c, 0, s, 0,
         0, 1, 0, 0,
        -s, 0, c, 0,
        1
    );
}

static Aff_transformation make_rotation_x(double angle_deg) {
    double rad = angle_deg * M_PI / 180.0;
    double c = std::cos(rad);
    double s = std::sin(rad);
    return Aff_transformation(
        1, 0,  0, 0,
        0, c, -s, 0,
        0, s,  c, 0,
        1
    );
}

/**
 * Build a combined rotation transform in Z-Y-X order.
 * This means: first rotate around X, then Y, then Z.
 * Combined = Rz * Ry * Rx
 */
static Aff_transformation make_rotation(double rx, double ry, double rz) {
    Aff_transformation rot_x = make_rotation_x(rx);
    Aff_transformation rot_y = make_rotation_y(ry);
    Aff_transformation rot_z = make_rotation_z(rz);
    // Z-Y-X order: Rz * Ry * Rx applied to point
    return rot_z * rot_y * rot_x;
}

// ---------------------------------------------------------------------------
// Apply an affine transformation to a Nef polyhedron
// ---------------------------------------------------------------------------

static Nef_polyhedron apply_transform(const Nef_polyhedron& nef,
                                      const Aff_transformation& transform) {
    if (nef.is_empty()) {
        return nef;
    }
    Nef_polyhedron result(nef);
    result.transform(transform);
    return result;
}

// ---------------------------------------------------------------------------
// Recursive scene graph traversal
// ---------------------------------------------------------------------------

/**
 * Internal recursive function that processes a JSON node and produces SceneObjects.
 * @param node       The current JSON node
 * @param color      The inherited color from ancestor Color nodes
 * @param cancel_flag  Atomic cancel flag
 */
static void process_node(const json& node, const SceneColor& color,
                         std::atomic<bool>& cancel_flag,
                         std::vector<SceneObject>& result) {
    if (is_cancelled(cancel_flag)) return;

    if (!node.contains("type") || !node["type"].is_string()) {
        return;  // Skip invalid nodes
    }

    std::string type = node["type"].get<std::string>();

    // --- Primitives ---

    if (type == "cube") {
        double sx = node.value("sizeX", 0.0);
        double sy = node.value("sizeY", 0.0);
        double sz = node.value("sizeZ", 0.0);
        bool center = node.value("center", false);

        Nef_polyhedron nef = build_cube(sx, sy, sz, center);
        if (!nef.is_empty()) {
            result.push_back({std::move(nef), color});
        }
        return;
    }

    if (type == "sphere") {
        double radius = node.value("radius", 0.0);
        int segments = node.value("segments", 32);

        Nef_polyhedron nef = build_sphere(radius, segments);
        if (!nef.is_empty()) {
            result.push_back({std::move(nef), color});
        }
        return;
    }

    if (type == "cylinder") {
        double height = node.value("height", 0.0);
        double r1 = node.value("radius1", 0.0);
        double r2 = node.value("radius2", 0.0);
        bool center = node.value("center", false);
        int segments = node.value("segments", 32);

        Nef_polyhedron nef = build_cylinder(height, r1, r2, center, segments);
        if (!nef.is_empty()) {
            result.push_back({std::move(nef), color});
        }
        return;
    }

    // --- Linear Extrude ---

    if (type == "linear_extrude") {
        double height = node.value("height", 0.0);
        if (node.contains("child")) {
            Nef_polyhedron nef = build_linear_extrude(height, node["child"], cancel_flag);
            if (!nef.is_empty()) {
                result.push_back({std::move(nef), color});
            }
        }
        return;
    }

    // --- Transforms ---

    if (type == "translate") {
        double tx = node.value("x", 0.0);
        double ty = node.value("y", 0.0);
        double tz = node.value("z", 0.0);

        if (node.contains("child")) {
            std::vector<SceneObject> child_objects;
            process_node(node["child"], color, cancel_flag, child_objects);

            Aff_transformation transform = make_translation(tx, ty, tz);
            for (auto& obj : child_objects) {
                if (is_cancelled(cancel_flag)) return;
                obj.nef = apply_transform(obj.nef, transform);
                result.push_back(std::move(obj));
            }
        }
        return;
    }

    if (type == "rotate") {
        double rx = node.value("x", 0.0);
        double ry = node.value("y", 0.0);
        double rz = node.value("z", 0.0);

        if (node.contains("child")) {
            std::vector<SceneObject> child_objects;
            process_node(node["child"], color, cancel_flag, child_objects);

            Aff_transformation transform = make_rotation(rx, ry, rz);
            for (auto& obj : child_objects) {
                if (is_cancelled(cancel_flag)) return;
                obj.nef = apply_transform(obj.nef, transform);
                result.push_back(std::move(obj));
            }
        }
        return;
    }

    if (type == "scale") {
        double sx = node.value("x", 1.0);
        double sy = node.value("y", 1.0);
        double sz = node.value("z", 1.0);

        if (node.contains("child")) {
            std::vector<SceneObject> child_objects;
            process_node(node["child"], color, cancel_flag, child_objects);

            Aff_transformation transform = make_scale(sx, sy, sz);
            for (auto& obj : child_objects) {
                if (is_cancelled(cancel_flag)) return;
                obj.nef = apply_transform(obj.nef, transform);
                result.push_back(std::move(obj));
            }
        }
        return;
    }

    // --- Color node: propagate color to children ---

    if (type == "color") {
        SceneColor new_color;
        new_color.r = node.value("r", static_cast<double>(SceneColor::DEFAULT.r));
        new_color.g = node.value("g", static_cast<double>(SceneColor::DEFAULT.g));
        new_color.b = node.value("b", static_cast<double>(SceneColor::DEFAULT.b));
        new_color.a = node.value("a", static_cast<double>(SceneColor::DEFAULT.a));

        if (node.contains("child")) {
            process_node(node["child"], new_color, cancel_flag, result);
        }
        return;
    }

    // --- Container nodes with children array ---

    if (type == "union" || type == "difference" || type == "intersection" || type == "group") {
        if (node.contains("children") && node["children"].is_array()) {
            for (const auto& child : node["children"]) {
                if (is_cancelled(cancel_flag)) return;
                process_node(child, color, cancel_flag, result);
            }
        }
        return;
    }

    // --- Unsupported node types: skip silently ---
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

std::vector<SceneObject> build_scene(const nlohmann::json& node,
                                     std::atomic<bool>& cancel_flag) {
    std::vector<SceneObject> result;
    process_node(node, SceneColor::DEFAULT, cancel_flag, result);
    return result;
}
