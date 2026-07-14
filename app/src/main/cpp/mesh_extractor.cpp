#include "mesh_extractor.h"
#include <CGAL/Polygon_mesh_processing/triangulate_faces.h>
#include <CGAL/Polygon_mesh_processing/compute_normal.h>
#include <CGAL/Polygon_mesh_processing/orient_polygon_soup.h>
#include <CGAL/Polygon_mesh_processing/polygon_soup_to_polygon_mesh.h>
#include <CGAL/Polygon_mesh_processing/repair_polygon_soup.h>
#include <CGAL/boost/graph/convert_nef_polyhedron_to_polygon_mesh.h>
#include <cmath>
#include <cstdio>

namespace PMP = CGAL::Polygon_mesh_processing;

void extract_mesh(const Nef_polyhedron& nef, const SceneColor& color,
                  ComputeResult& result) {
    if (nef.is_empty()) {
        return;
    }

    Polyhedron poly;

    if (nef.is_simple()) {
        // Fast path: simple Nef → direct conversion
        nef.convert_to_polyhedron(poly);
    } else {
        bool converted = false;

        // Strategy 1: extract polygon soup, repair, orient, build mesh.
        // This handles non-manifold Nef (shared faces from exact-boundary subtractions).
        try {
            std::vector<Point_3> points;
            std::vector<std::vector<std::size_t>> polygons;
            CGAL::convert_nef_polyhedron_to_polygon_soup(nef, points, polygons, true);

            if (!points.empty() && !polygons.empty()) {
                PMP::repair_polygon_soup(points, polygons);
                PMP::orient_polygon_soup(points, polygons);
                if (PMP::is_polygon_soup_a_polygon_mesh(polygons)) {
                    PMP::polygon_soup_to_polygon_mesh(points, polygons, poly);
                    if (!poly.empty()) converted = true;
                }
            }
        } catch (...) {
            poly.clear();
        }

        // Strategy 2: regularize then try polygon soup approach
        if (!converted) {
            try {
                Nef_polyhedron regularized = nef.regularization();
                if (!regularized.is_empty()) {
                    if (regularized.is_simple()) {
                        regularized.convert_to_polyhedron(poly);
                        if (!poly.empty()) converted = true;
                    } else {
                        std::vector<Point_3> points;
                        std::vector<std::vector<std::size_t>> polygons;
                        CGAL::convert_nef_polyhedron_to_polygon_soup(regularized, points, polygons, true);
                        if (!points.empty() && !polygons.empty()) {
                            PMP::repair_polygon_soup(points, polygons);
                            PMP::orient_polygon_soup(points, polygons);
                            if (PMP::is_polygon_soup_a_polygon_mesh(polygons)) {
                                PMP::polygon_soup_to_polygon_mesh(points, polygons, poly);
                                if (!poly.empty()) converted = true;
                            }
                        }
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

        Point_3 p0 = hc->vertex()->point(); ++hc;
        Point_3 p1 = hc->vertex()->point(); ++hc;
        Point_3 p2 = hc->vertex()->point();

        auto v1 = p1 - p0;
        auto v2 = p2 - p0;
        auto normal = CGAL::cross_product(v1, v2);

        float nx = static_cast<float>(CGAL::to_double(normal.x()));
        float ny = static_cast<float>(CGAL::to_double(normal.y()));
        float nz = static_cast<float>(CGAL::to_double(normal.z()));

        float len = std::sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0f) {
            nx /= len; ny /= len; nz /= len;
        }

        result.vertices.push_back(static_cast<float>(CGAL::to_double(p0.x())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p0.y())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p0.z())));

        result.vertices.push_back(static_cast<float>(CGAL::to_double(p1.x())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p1.y())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p1.z())));

        result.vertices.push_back(static_cast<float>(CGAL::to_double(p2.x())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p2.y())));
        result.vertices.push_back(static_cast<float>(CGAL::to_double(p2.z())));

        for (int i = 0; i < 3; ++i) {
            result.normals.push_back(nx);
            result.normals.push_back(ny);
            result.normals.push_back(nz);
        }

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
