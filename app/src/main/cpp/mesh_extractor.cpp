#include "mesh_extractor.h"
#include <CGAL/Polygon_mesh_processing/triangulate_faces.h>
#include <CGAL/Polygon_mesh_processing/compute_normal.h>
#include <cmath>

namespace PMP = CGAL::Polygon_mesh_processing;

void extract_mesh(const Nef_polyhedron& nef, const SceneColor& color,
                  ComputeResult& result) {
    if (nef.is_empty()) {
        return;
    }

    // Convert Nef to Polyhedron
    Polyhedron poly;
    if (!nef.is_simple()) {
        // Non-manifold result; skip
        return;
    }
    nef.convert_to_polyhedron(poly);

    if (poly.empty()) {
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
