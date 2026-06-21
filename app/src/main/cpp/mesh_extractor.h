#pragma once

#include "cgal_compute.h"
#include "scene_builder.h"
#include <vector>

/**
 * Extract a triangle mesh from a Nef polyhedron with an associated color.
 *
 * Converts the Nef polyhedron to a Polyhedron_3, triangulates faces,
 * and fills vertex positions, normals, and per-vertex colors into
 * the provided ComputeResult.
 *
 * @param nef    The Nef polyhedron to extract
 * @param color  The color to assign to all vertices
 * @param result The ComputeResult to append mesh data to
 */
void extract_mesh(const Nef_polyhedron& nef, const SceneColor& color,
                  ComputeResult& result);

/**
 * Extract triangle meshes from multiple SceneObjects, appending all
 * geometry into a single ComputeResult.
 *
 * @param objects Vector of SceneObjects (each with a Nef and color)
 * @param result  The ComputeResult to append mesh data to
 */
void extract_meshes(const std::vector<SceneObject>& objects,
                    ComputeResult& result);
