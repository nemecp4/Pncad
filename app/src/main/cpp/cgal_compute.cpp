#include "cgal_compute.h"
#include "scene_builder.h"
#include "mesh_extractor.h"
#include <string>
#include <cmath>

using json = nlohmann::json;

// ---------------------------------------------------------------------------
// Cancellation check helper
// ---------------------------------------------------------------------------

static inline bool is_cancelled(std::atomic<bool>& flag) {
    return flag.load(std::memory_order_relaxed);
}

// ---------------------------------------------------------------------------
// Helper: Check if a Nef polyhedron is degenerate (empty or zero-volume)
// ---------------------------------------------------------------------------

static bool is_degenerate(const Nef_polyhedron& nef) {
    if (nef.is_empty()) {
        return true;
    }
    // A Nef polyhedron with no volume (e.g., a single point or edge)
    // is considered degenerate for CSG purposes.
    // number_of_volumes() counts bounded volumes; 
    // a valid solid has at least 1 bounded volume (plus the unbounded one).
    if (nef.number_of_volumes() < 2) {
        return true;
    }
    return false;
}

// ---------------------------------------------------------------------------
// Forward declaration: recursive CSG processing
// ---------------------------------------------------------------------------

/**
 * Recursively process a JSON scene node and produce a Nef polyhedron result.
 * For CSG nodes: perform Boolean operations on children's Nef polyhedra.
 * For non-CSG nodes: use build_scene() to get primitives and union them.
 *
 * @param node        The JSON node to process
 * @param cancel_flag Atomic cancel flag
 * @param color_out   Output: the color to use for the resulting geometry
 * @param valid_out   Output: true if a valid (non-empty) Nef was produced
 * @return The resulting Nef polyhedron
 */
static Nef_polyhedron process_csg_node(const json& node,
                                       std::atomic<bool>& cancel_flag,
                                       SceneColor& color_out,
                                       bool& valid_out);

// ---------------------------------------------------------------------------
// CSG Operations
// ---------------------------------------------------------------------------

/**
 * Compute the Boolean union of all children's Nef polyhedra.
 * Degenerate operands are excluded.
 */
static Nef_polyhedron compute_union(const json& children,
                                    std::atomic<bool>& cancel_flag,
                                    SceneColor& color_out,
                                    bool& valid_out) {
    valid_out = false;
    color_out = SceneColor::DEFAULT;
    Nef_polyhedron result;
    bool first = true;

    for (const auto& child : children) {
        if (is_cancelled(cancel_flag)) {
            valid_out = false;
            return Nef_polyhedron();
        }

        SceneColor child_color;
        bool child_valid = false;
        Nef_polyhedron child_nef = process_csg_node(child, cancel_flag,
                                                     child_color, child_valid);

        if (!child_valid || is_degenerate(child_nef)) {
            continue;  // Exclude degenerate operands
        }

        if (first) {
            result = std::move(child_nef);
            color_out = child_color;
            first = false;
        } else {
            if (is_cancelled(cancel_flag)) {
                valid_out = false;
                return Nef_polyhedron();
            }
            result = result + child_nef;
        }
    }

    valid_out = !first;  // true if at least one valid child was processed
    return result;
}

/**
 * Compute the Boolean difference: first child minus subsequent children.
 * Degenerate operands are excluded.
 */
static Nef_polyhedron compute_difference(const json& children,
                                         std::atomic<bool>& cancel_flag,
                                         SceneColor& color_out,
                                         bool& valid_out) {
    valid_out = false;
    color_out = SceneColor::DEFAULT;

    if (children.empty()) {
        return Nef_polyhedron();
    }

    // Collect all children's Nef polyhedra
    std::vector<Nef_polyhedron> child_nefs;
    bool first_color_set = false;

    for (const auto& child : children) {
        if (is_cancelled(cancel_flag)) {
            return Nef_polyhedron();
        }

        SceneColor child_color;
        bool child_valid = false;
        Nef_polyhedron child_nef = process_csg_node(child, cancel_flag,
                                                     child_color, child_valid);

        if (!child_valid || is_degenerate(child_nef)) {
            continue;
        }

        if (!first_color_set) {
            color_out = child_color;
            first_color_set = true;
        }
        child_nefs.push_back(std::move(child_nef));
    }

    if (child_nefs.empty()) {
        return Nef_polyhedron();
    }

    // Single operand: just return it
    if (child_nefs.size() == 1) {
        valid_out = true;
        return std::move(child_nefs[0]);
    }

    // Optimization: union all subtraction operands first, then subtract once.
    // This avoids accumulation of non-manifold artifacts from sequential subtractions.
    // Result = first - (second ∪ third ∪ ... ∪ last)
    Nef_polyhedron base = std::move(child_nefs[0]);

    if (child_nefs.size() == 2) {
        // Simple case: single subtraction
        if (is_cancelled(cancel_flag)) return Nef_polyhedron();
        base = base - child_nefs[1];
    } else {
        // Union all subtraction operands
        Nef_polyhedron subtraction_union = std::move(child_nefs[1]);
        for (size_t i = 2; i < child_nefs.size(); ++i) {
            if (is_cancelled(cancel_flag)) return Nef_polyhedron();
            subtraction_union = subtraction_union + child_nefs[i];
        }
        // Single subtraction
        if (is_cancelled(cancel_flag)) return Nef_polyhedron();
        base = base - subtraction_union;
    }

    valid_out = true;
    return base;
}

/**
 * Compute the Boolean intersection of all children's Nef polyhedra.
 * Degenerate operands are excluded.
 */
static Nef_polyhedron compute_intersection(const json& children,
                                           std::atomic<bool>& cancel_flag,
                                           SceneColor& color_out,
                                           bool& valid_out) {
    valid_out = false;
    color_out = SceneColor::DEFAULT;
    Nef_polyhedron result;
    bool first = true;

    for (const auto& child : children) {
        if (is_cancelled(cancel_flag)) {
            valid_out = false;
            return Nef_polyhedron();
        }

        SceneColor child_color;
        bool child_valid = false;
        Nef_polyhedron child_nef = process_csg_node(child, cancel_flag,
                                                     child_color, child_valid);

        if (!child_valid || is_degenerate(child_nef)) {
            continue;  // Exclude degenerate operands
        }

        if (first) {
            result = std::move(child_nef);
            color_out = child_color;
            first = false;
        } else {
            if (is_cancelled(cancel_flag)) {
                valid_out = false;
                return Nef_polyhedron();
            }
            result = result * child_nef;
        }
    }

    valid_out = !first;
    return result;
}

// ---------------------------------------------------------------------------
// Recursive CSG node processing
// ---------------------------------------------------------------------------

static Nef_polyhedron process_csg_node(const json& node,
                                       std::atomic<bool>& cancel_flag,
                                       SceneColor& color_out,
                                       bool& valid_out) {
    valid_out = false;
    color_out = SceneColor::DEFAULT;

    if (is_cancelled(cancel_flag)) {
        return Nef_polyhedron();
    }

    if (!node.contains("type") || !node["type"].is_string()) {
        return Nef_polyhedron();
    }

    std::string type = node["type"].get<std::string>();

    // --- CSG Nodes: perform Boolean operations recursively ---

    if (type == "union") {
        if (!node.contains("children") || !node["children"].is_array()) {
            return Nef_polyhedron();
        }
        const auto& children = node["children"];
        if (children.empty()) {
            return Nef_polyhedron();  // Empty children → empty result
        }
        return compute_union(children, cancel_flag, color_out, valid_out);
    }

    if (type == "difference") {
        if (!node.contains("children") || !node["children"].is_array()) {
            return Nef_polyhedron();
        }
        const auto& children = node["children"];
        if (children.empty()) {
            return Nef_polyhedron();
        }
        return compute_difference(children, cancel_flag, color_out, valid_out);
    }

    if (type == "intersection") {
        if (!node.contains("children") || !node["children"].is_array()) {
            return Nef_polyhedron();
        }
        const auto& children = node["children"];
        if (children.empty()) {
            return Nef_polyhedron();
        }
        return compute_intersection(children, cancel_flag, color_out, valid_out);
    }

    // --- Group node: recursively process children through CSG pipeline ---

    if (type == "group") {
        if (!node.contains("children") || !node["children"].is_array()) {
            return Nef_polyhedron();
        }
        // Group behaves like an implicit union of its children
        return compute_union(node["children"], cancel_flag, color_out, valid_out);
    }

    // --- Transform nodes wrapping potential CSG children ---

    if (type == "translate" || type == "rotate" || type == "scale") {
        if (!node.contains("child")) {
            return Nef_polyhedron();
        }

        const auto& child_node = node["child"];
        std::string child_type;
        if (child_node.contains("type") && child_node["type"].is_string()) {
            child_type = child_node["type"].get<std::string>();
        }

        // If the child is a CSG node, process it recursively and apply transform
        bool child_is_csg = (child_type == "union" || child_type == "difference" ||
                             child_type == "intersection" || child_type == "group");

        if (child_is_csg) {
            SceneColor child_color;
            bool child_valid = false;
            Nef_polyhedron child_nef = process_csg_node(child_node, cancel_flag,
                                                         child_color, child_valid);
            if (!child_valid || is_degenerate(child_nef)) {
                return Nef_polyhedron();
            }

            // Apply the transform to the resulting Nef polyhedron
            // We need to construct the affine transformation
            // Use build_scene on just this transform node for a single primitive
            // to avoid duplicating the transform logic, but that won't work here.
            // Instead we rely on Nef_polyhedron::transform().

            // For now, construct the transform and apply it:
            if (type == "translate") {
                double tx = node.value("x", 0.0);
                double ty = node.value("y", 0.0);
                double tz = node.value("z", 0.0);
                Aff_transformation t(CGAL::TRANSLATION, Vector_3(tx, ty, tz));
                child_nef.transform(t);
            } else if (type == "scale") {
                double sx = node.value("x", 1.0);
                double sy = node.value("y", 1.0);
                double sz = node.value("z", 1.0);
                Aff_transformation t(
                    sx, 0, 0, 0,
                    0, sy, 0, 0,
                    0, 0, sz, 0,
                    1
                );
                child_nef.transform(t);
            } else if (type == "rotate") {
                double rx = node.value("x", 0.0);
                double ry = node.value("y", 0.0);
                double rz = node.value("z", 0.0);
                // Z-Y-X rotation order (same as scene_builder.cpp)
                double rad_x = rx * 3.14159265358979323846 / 180.0;
                double rad_y = ry * 3.14159265358979323846 / 180.0;
                double rad_z = rz * 3.14159265358979323846 / 180.0;

                Aff_transformation rot_x(
                    1, 0, 0, 0,
                    0, std::cos(rad_x), -std::sin(rad_x), 0,
                    0, std::sin(rad_x), std::cos(rad_x), 0,
                    1);
                Aff_transformation rot_y(
                    std::cos(rad_y), 0, std::sin(rad_y), 0,
                    0, 1, 0, 0,
                    -std::sin(rad_y), 0, std::cos(rad_y), 0,
                    1);
                Aff_transformation rot_z(
                    std::cos(rad_z), -std::sin(rad_z), 0, 0,
                    std::sin(rad_z), std::cos(rad_z), 0, 0,
                    0, 0, 1, 0,
                    1);
                // Combined: Rz * Ry * Rx
                Aff_transformation combined = rot_z * rot_y * rot_x;
                child_nef.transform(combined);
            }

            color_out = child_color;
            valid_out = true;
            return child_nef;
        }

        // Non-CSG child: fall through to build_scene
    }

    // --- Color node wrapping potential CSG child ---

    if (type == "color") {
        if (!node.contains("child")) {
            return Nef_polyhedron();
        }

        const auto& child_node = node["child"];
        std::string child_type;
        if (child_node.contains("type") && child_node["type"].is_string()) {
            child_type = child_node["type"].get<std::string>();
        }

        bool child_is_csg = (child_type == "union" || child_type == "difference" ||
                             child_type == "intersection" || child_type == "group");

        if (child_is_csg) {
            SceneColor child_color;
            bool child_valid = false;
            Nef_polyhedron child_nef = process_csg_node(child_node, cancel_flag,
                                                         child_color, child_valid);
            // Override color with this node's color
            color_out.r = static_cast<float>(node.value("r", static_cast<double>(SceneColor::DEFAULT.r)));
            color_out.g = static_cast<float>(node.value("g", static_cast<double>(SceneColor::DEFAULT.g)));
            color_out.b = static_cast<float>(node.value("b", static_cast<double>(SceneColor::DEFAULT.b)));
            color_out.a = static_cast<float>(node.value("a", static_cast<double>(SceneColor::DEFAULT.a)));
            valid_out = child_valid;
            return child_nef;
        }

        // Non-CSG child: fall through to build_scene
    }

    // --- Non-CSG leaf/transform/color nodes (no nested CSG): use build_scene ---

    std::vector<SceneObject> objects = build_scene(node, cancel_flag);

    if (objects.empty()) {
        return Nef_polyhedron();
    }

    // Single object: return it directly
    if (objects.size() == 1) {
        color_out = objects[0].color;
        valid_out = !is_degenerate(objects[0].nef);
        return std::move(objects[0].nef);
    }

    // Multiple objects: implicit union
    Nef_polyhedron result;
    bool first = true;
    for (auto& obj : objects) {
        if (is_cancelled(cancel_flag)) {
            valid_out = false;
            return Nef_polyhedron();
        }

        if (is_degenerate(obj.nef)) {
            continue;
        }

        if (first) {
            result = std::move(obj.nef);
            color_out = obj.color;
            first = false;
        } else {
            result = result + obj.nef;
        }
    }

    valid_out = !first;
    return result;
}

// ---------------------------------------------------------------------------
// Public API: cgal_compute
// ---------------------------------------------------------------------------

ComputeResult cgal_compute(const nlohmann::json& scene,
                           std::atomic<bool>& cancel_flag) {
    ComputeResult result;

    // Check cancel before starting
    if (is_cancelled(cancel_flag)) {
        result.error_category = "CANCELLED";
        result.error_message = "Computation was cancelled before starting";
        return result;
    }

    // Validate input
    if (scene.is_null() || scene.empty()) {
        // Empty scene → empty result (not an error)
        return result;
    }

    if (!scene.contains("type") || !scene["type"].is_string()) {
        // No type field → empty result
        return result;
    }

    std::string root_type = scene["type"].get<std::string>();

    // Determine if root is a CSG node (or group, which acts as implicit union)
    bool is_csg = (root_type == "union" || root_type == "difference" ||
                   root_type == "intersection" || root_type == "group");

    if (is_csg) {
        // Process CSG recursively
        SceneColor color;
        bool valid = false;
        Nef_polyhedron nef = process_csg_node(scene, cancel_flag, color, valid);

        // Check if cancelled during processing
        if (is_cancelled(cancel_flag)) {
            result.error_category = "CANCELLED";
            result.error_message = "Computation was cancelled";
            return result;
        }

        if (!valid || is_degenerate(nef)) {
            // CSG produced empty geometry → empty result (requirement 5.6)
            return result;
        }

        // Extract mesh from the final Nef polyhedron
        extract_mesh(nef, color, result);
    } else {
        // Non-CSG root: use build_scene for all leaf primitives (implicit union)
        std::vector<SceneObject> objects = build_scene(scene, cancel_flag);

        // Check if cancelled during scene building
        if (is_cancelled(cancel_flag)) {
            result.error_category = "CANCELLED";
            result.error_message = "Computation was cancelled";
            return result;
        }

        if (objects.empty()) {
            // No geometry → empty result
            return result;
        }

        // Extract meshes from all scene objects
        extract_meshes(objects, result);
    }

    // Final cancel check
    if (is_cancelled(cancel_flag)) {
        result.error_category = "CANCELLED";
        result.error_message = "Computation was cancelled";
        result.vertices.clear();
        result.normals.clear();
        result.colors.clear();
        return result;
    }

    return result;
}
