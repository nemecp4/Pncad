#pragma once

#include <CGAL/Exact_predicates_exact_constructions_kernel.h>
#include <CGAL/Nef_polyhedron_3.h>
#include <CGAL/Polyhedron_3.h>
#include <CGAL/Aff_transformation_3.h>
#include "nlohmann/json.hpp"
#include <atomic>
#include <array>
#include <vector>

// CGAL type aliases
using Kernel = CGAL::Exact_predicates_exact_constructions_kernel;
using Point_3 = Kernel::Point_3;
using Vector_3 = Kernel::Vector_3;
using Polyhedron = CGAL::Polyhedron_3<Kernel>;
using Nef_polyhedron = CGAL::Nef_polyhedron_3<Kernel>;
using Aff_transformation = CGAL::Aff_transformation_3<Kernel>;

/**
 * RGBA color attached to scene objects.
 */
struct SceneColor {
    float r;
    float g;
    float b;
    float a;

    static const SceneColor DEFAULT;
};

/**
 * A solid object with its associated color.
 */
struct SceneObject {
    Nef_polyhedron nef;
    SceneColor color;
};

/**
 * Build a list of SceneObjects from a JSON scene graph node.
 * Each primitive in the tree produces one SceneObject with a Nef polyhedron
 * and a color propagated from the nearest ancestor Color node.
 *
 * @param node       Root JSON node of the scene graph (or subtree)
 * @param cancel_flag  Atomic flag checked between operations; if true, returns early
 * @return Vector of SceneObjects representing all valid primitives found
 */
std::vector<SceneObject> build_scene(const nlohmann::json& node,
                                     std::atomic<bool>& cancel_flag);

/**
 * Build a Nef polyhedron from vertices and triangle faces.
 * Used by primitive builders and the text renderer to convert mesh data to Nef.
 *
 * @param vertices   List of 3D points
 * @param faces      List of triangle face index triples
 * @return Nef polyhedron (empty if input is invalid or non-manifold)
 */
Nef_polyhedron make_nef_from_mesh(const std::vector<Point_3>& vertices,
                                  const std::vector<std::array<int, 3>>& faces);
