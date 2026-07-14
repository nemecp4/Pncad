#include "mesh_extractor.h"
#include <CGAL/Polygon_mesh_processing/triangulate_faces.h>
#include <CGAL/Polygon_mesh_processing/compute_normal.h>
#include <CGAL/boost/graph/convert_nef_polyhedron_to_polygon_mesh.h>
#include <cmath>
#include <cstdio>

namespace PMP = CGAL::Polygon_mesh_processing;

void extract_mesh(const Nef_polyhedron& nef, const SceneColor& color,
                  ComputeResult& result) {
    if (nef.is_empty()) {
        return;
    }

    // Convert Nef to Polyhedron using a fallback chain:
    // 1. Direct conversion (requires is_simple)
    // 2. convert_nef_polyhedron_to_polygon_mesh (handles most non-simple cases)
    // 3. Regularize + direct conversion (removes lower-dimensional artifacts)
    // 4. Regularize + convert_nef_polyhedron_to_polygon_mesh (last resort)
    Polyhedron poly;

    if (nef.is_simple()) {
        // Fast path: simple Nef → direct conversion
        nef.convert_to_polyhedron(poly);
    } else {
        bool converted = false;

        // Strategy 1: convert_nef_polyhedron_to_polygon_mesh on original
        try {
            CGAL::convert_nef_polyhedron_to_polygon_mesh(nef, poly, true);
            if (!poly.empty()) converted = true;
        } catch (...) {
            poly.clear();
        }

        // Strategy 2: regularize, then try both conversion methods
        if (!converted) {
            try {
                Nef_polyhedron regularized = nef.regularization();
                if (!regularized.is_empty()) {
                    if (regularized.is_simple()) {
                        regularized.convert_to_polyhedron(poly);
                        if (!poly.empty()) converted = true;
                    } else {
                        CGAL::convert_nef_polyhedron_to_polygon_mesh(regularized, poly, true);
                        if (!poly.empty()) converted = true;
                    }
                }
            } catch (...) {
                poly.clear();
            }
        }

        // Strategy 3: interior closure — take the interior, close it, convert
        if (!converted) {
            try {
                Nef_polyhedron interior = nef.interior();
                Nef_polyhedron closed = interior.closure();
                if (!closed.is_empty()) {
                    if (closed.is_simple()) {
                        closed.convert_to_polyhedron(poly);
                        if (!poly.empty()) converted = true;
                    } else {
                        CGAL::convert_nef_polyhedron_to_polygon_mesh(closed, poly, true);
                        if (!poly.empty()) converted = true;
                    }
                }
            } catch (...) {
                poly.clear();
            }
        }

        if (!converted) {
            fprintf(stderr, "CGAL mesh_extractor: all conversion strategies failed for non-simple Nef\n");
            return;
        }
    }

    if (poly.empty()) {
        fprintf(stderr, "CGAL mesh_extractor: conversion produced empty polyhedron\n");
        return;
    }

    // Triangulate all faces
    PMP::triangulate_faces(poly);

    // Extract triangles
    for (auto fi = poly.facets_begin(); fi != poly.facets_end(); ++fi) {
        auto hc = fi->facet_begin();

        // Get the three vertices of the triangle
        Point_3 p0 = hc->vertex()->point(); ++hc;
        Point_3 p1 = hc->vertex()->point(); ++hc;
        Point_3 p2 = hc->vertex()->point();

        // Compute face normal
        auto v1 = p1 - p0;
        auto v2 = p2 - p0;
        auto normal = CGAL::cross_product(v1, v2);

        // Convert to float (approximate)
        float nx = static_cast<float>(CGAL::to_double(normal.x()));
        float ny = static_cast<float>(CGAL::to_double(normal.y()));
        float nz = static_cast<float>(CGAL::to_double(normal.z()));

        // Normalize
        float len = std::sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0f) {
            nx /= len; ny /= len; nz /= len;
        }

        // Add vertices
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p0.x())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p0.y())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p0.z())));

        result.vertices.push_back(static_cast<float>(CGAL::to_double(p1.x())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p1.y())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p1.z())));

        result.vertices.push_back(static_cast<float>(CGAL::to_double(p2.x())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p2.y())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p2.z())));

        // Add normals (same normal for all 3 vertices of the face)
        for (int i = 0; i < 3; ++i) {
            result.normals.push_back(nx);
            result.normals.push_back(ny);
            result.normals.push_back(nz);
        }

        // Add colors (per-vertex RGBA)
        for (int i = 0; i < 3; ++i) {
            result.colors.push_back(color.r);
            result.colors.push_back(color.g);
            result.colors.push_back(color.b);
            result.colors.push_back(color.a);
        }
    }
}

void extract_meshes(const std::vector<SceneObject>& objects,
                    ComputeResult& result) {
    for (const auto& obj : objects) {
        extract_mesh(obj.nef, obj.color, result);
    }
}
