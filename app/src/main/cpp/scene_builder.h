#pragma once

#include <CGAL/Exact_predicates_exact_constructions_kernel.h>
#include <CGAL/Nef_polyhedron_3.h>
#include <CGAL/Polyhedron_3.h>
#include <CGAL/Aff_transformation_3.h>
#include "nlohmann/json.hpp"
#include <atomic>
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
