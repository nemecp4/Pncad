#pragma once

#include <CGAL/Exact_predicates_exact_constructions_kernel.h>
#include <CGAL/Nef_polyhedron_3.h>
#include <CGAL/Polyhedron_3.h>
#include "nlohmann/json.hpp"
#include <atomic>
#include <vector>
#include <string>

using Kernel = CGAL::Exact_predicates_exact_constructions_kernel;
using Point_3 = Kernel::Point_3;
using Polyhedron = CGAL::Polyhedron_3<Kernel>;
using Nef_polyhedron = CGAL::Nef_polyhedron_3<Kernel>;

struct ComputeResult {
    std::vector<float> vertices;
    std::vector<float> normals;
    std::vector<float> colors;
    std::string error_category;
    std::string error_message;
};

/**
 * Compute the final triangle mesh from a JSON scene graph.
 *
 * Recursively processes CSG nodes (union, difference, intersection) by
 * performing exact Boolean operations on CGAL Nef polyhedra built from
 * primitive leaf nodes via build_scene().
 *
 * @param scene       The root JSON node of the scene graph
 * @param cancel_flag Atomic flag checked before each expensive operation;
 *                    if true, returns early with CANCELLED error
 * @return ComputeResult containing mesh float arrays on success,
 *         or error_category/error_message on failure
 */
ComputeResult cgal_compute(const nlohmann::json& scene,
                           std::atomic<bool>& cancel_flag);
