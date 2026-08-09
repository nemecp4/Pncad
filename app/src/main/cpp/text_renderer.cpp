#include "text_renderer.h"
#include "ttf2mesh/ttf2mesh.h"
#include <cmath>
#include <cstdio>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// ---------------------------------------------------------------------------
// TextRenderer implementation
// ---------------------------------------------------------------------------

TextRenderer::TextRenderer() : cached_font_(nullptr) {}

TextRenderer::~TextRenderer() {
    if (cached_font_) {
        ttf_free(cached_font_);
        cached_font_ = nullptr;
    }
}

void TextRenderer::set_font_path(const std::string& path) {
    // If path changes, invalidate cached font
    if (path != font_path_) {
        if (cached_font_) {
            ttf_free(cached_font_);
            cached_font_ = nullptr;
        }
        glyph_cache_.clear();
        font_path_ = path;
    }
}

ttf_t* TextRenderer::load_font() {
    if (cached_font_) {
        return cached_font_;
    }

    if (font_path_.empty()) {
        fprintf(stderr, "TextRenderer: font path not set\n");
        return nullptr;
    }

    ttf_t* font = nullptr;
    int result = ttf_load_from_file(font_path_.c_str(), &font, false);
    if (result != TTF_DONE || !font) {
        fprintf(stderr, "TextRenderer: failed to load font from '%s' (error %d)\n",
                font_path_.c_str(), result);
        return nullptr;
    }

    cached_font_ = font;
    return font;
}

TextRenderer::GlyphData TextRenderer::get_glyph(ttf_t* font, int codepoint) {
    // Check cache
    uint64_t cache_key = static_cast<uint64_t>(codepoint);
    auto it = glyph_cache_.find(cache_key);
    if (it != glyph_cache_.end()) {
        return it->second;
    }

    GlyphData data;
    data.advance_width = 0.0;
    data.ascent = 0.0;
    data.descent = 0.0;

    int glyph_idx = ttf_find_glyph(font, static_cast<uint32_t>(codepoint));
    if (glyph_idx < 0) {
        // Glyph not found — return a placeholder with average advance width
        data.advance_width = static_cast<double>(font->hhea.advanceWidthMax) * 0.5;
        glyph_cache_[cache_key] = data;
        return data;
    }

    ttf_glyph_t* glyph = &font->glyphs[glyph_idx];
    data.advance_width = static_cast<double>(glyph->advance);
    data.ascent = static_cast<double>(font->hhea.ascender);
    data.descent = static_cast<double>(font->hhea.descender);

    // Get linearized outline (flattened Bezier curves to line segments)
    ttf_outline_t* outline = ttf_linear_outline(glyph, TTF_QUALITY_NORMAL);
    if (!outline) {
        // No outline (e.g., space character) — just use advance width
        glyph_cache_[cache_key] = data;
        return data;
    }

    // Extract contours from the outline
    for (int c = 0; c < outline->ncontours; c++) {
        int length = outline->cont[c].length;
        if (length < 3) continue;

        std::vector<std::pair<double, double>> contour;
        contour.reserve(length);
        for (int p = 0; p < length; p++) {
            contour.emplace_back(
                static_cast<double>(outline->cont[c].pt[p].x),
                static_cast<double>(outline->cont[c].pt[p].y)
            );
        }
        data.contours.push_back(std::move(contour));
    }

    ttf_free_outline(outline);
    glyph_cache_[cache_key] = data;
    return data;
}

Nef_polyhedron TextRenderer::extrude_contours(
    const std::vector<std::vector<std::pair<double, double>>>& contours,
    double height,
    std::atomic<bool>& cancel_flag
) {
    if (contours.empty() || height <= 0) {
        return Nef_polyhedron();
    }

    // Use ttf2mesh's glyph2mesh to get triangulated faces, then extrude
    // For now, we build a mesh manually from the contours by creating
    // top/bottom faces and side walls.

    // Collect all contour points for triangulation via ttf_glyph2mesh approach.
    // Since we have linearized contours, we'll use make_nef_from_mesh with
    // a simple extrusion of each contour as a prism.

    // Strategy: For each character, get the 2D mesh via ttf_glyph2mesh,
    // then extrude to 3D. But we already have contours here.
    // Let's use make_nef_from_mesh with a simple box-extrusion of the full glyph mesh.

    // Actually the simplest correct approach for CGAL: create top and bottom face
    // triangles plus side quads from the contour edges.

    // We'll collect vertices and faces for the extruded shape.
    std::vector<Point_3> vertices;
    std::vector<std::array<int, 3>> faces;

    // For each contour, create a tube (side walls only)
    // Then cap top and bottom with the 2D triangulated mesh.
    // Since we can't easily triangulate arbitrary polygons with holes here,
    // let's use the ttf_glyph2mesh approach instead.

    // Return empty for now if we have no good triangulation path.
    // The actual triangulation will be done per-glyph in build_text().
    return Nef_polyhedron();
}

Nef_polyhedron TextRenderer::build_text(
    const std::string& text,
    double size,
    const std::string& font,
    const std::string& halign,
    const std::string& valign,
    double spacing,
    const std::string& direction,
    double extrude_height,
    std::atomic<bool>& cancel_flag
) {
    if (text.empty() || size <= 0) {
        return Nef_polyhedron();
    }

    ttf_t* ttf_font = load_font();
    if (!ttf_font) {
        return Nef_polyhedron();
    }

    // Determine effective extrusion height — if 0, use a thin slab (text needs volume for Nef)
    double effective_height = extrude_height > 0 ? extrude_height : 1.0;

    // Collect positioned glyphs with their mesh data
    struct PositionedGlyph {
        int codepoint;
        double x_offset;
        ttf_glyph_t* glyph_ptr;
    };

    std::vector<PositionedGlyph> positioned;
    double cursor_x = 0.0;

    // Layout: position each character along X axis
    for (char ch : text) {
        if (cancel_flag.load(std::memory_order_relaxed)) {
            return Nef_polyhedron();
        }

        int codepoint = static_cast<unsigned char>(ch);
        int glyph_idx = ttf_find_glyph(ttf_font, static_cast<uint32_t>(codepoint));
        if (glyph_idx < 0) {
            // Skip characters not in font
            cursor_x += ttf_font->hhea.advanceWidthMax * 0.5 * spacing;
            continue;
        }

        ttf_glyph_t* glyph = &ttf_font->glyphs[glyph_idx];
        positioned.push_back({codepoint, cursor_x, glyph});
        cursor_x += static_cast<double>(glyph->advance) * spacing;
    }

    if (positioned.empty()) {
        return Nef_polyhedron();
    }

    double total_width = cursor_x;
    double font_ascent = static_cast<double>(ttf_font->hhea.ascender);
    double font_descent = static_cast<double>(ttf_font->hhea.descender);

    // Compute alignment offsets
    double x_offset = 0.0;
    if (halign == "center") {
        x_offset = -total_width / 2.0;
    } else if (halign == "right") {
        x_offset = -total_width;
    }

    double y_offset = 0.0;
    if (valign == "bottom") {
        y_offset = -font_descent;
    } else if (valign == "top") {
        y_offset = -font_ascent;
    } else if (valign == "center") {
        y_offset = -(font_ascent + font_descent) / 2.0;
    }
    // "baseline" → y_offset = 0

    // Handle RTL direction
    if (direction == "rtl") {
        for (auto& pg : positioned) {
            int glyph_idx2 = ttf_find_glyph(ttf_font, static_cast<uint32_t>(pg.codepoint));
            if (glyph_idx2 >= 0) {
                pg.x_offset = -(pg.x_offset + ttf_font->glyphs[glyph_idx2].advance);
            }
        }
    }

    // Build Nef polyhedra for each glyph and union them
    Nef_polyhedron result;
    bool first = true;

    for (const auto& pg : positioned) {
        if (cancel_flag.load(std::memory_order_relaxed)) {
            return Nef_polyhedron();
        }

        // Get 2D mesh for this glyph using ttf2mesh
        ttf_mesh_t* mesh = nullptr;
        int mesh_result = ttf_glyph2mesh(pg.glyph_ptr, &mesh, TTF_QUALITY_NORMAL, TTF_FEATURES_DFLT);
        if (mesh_result != TTF_DONE || !mesh || mesh->nvert == 0 || mesh->nfaces == 0) {
            if (mesh) ttf_free_mesh(mesh);
            continue;
        }

        // Build 3D extruded mesh from 2D glyph mesh
        // Scale by size, apply position offsets
        double scale = size;  // ttf2mesh coordinates are in EM units (0-1 range)
        double gx = (pg.x_offset + x_offset) * scale;
        double gy = y_offset * scale;

        int nvert_2d = mesh->nvert;
        int nfaces_2d = mesh->nfaces;

        // Create vertices: bottom face (z=0) and top face (z=height)
        std::vector<Point_3> vertices;
        vertices.reserve(nvert_2d * 2);

        for (int i = 0; i < nvert_2d; i++) {
            double x = static_cast<double>(mesh->vert[i].x) * scale + gx;
            double y = static_cast<double>(mesh->vert[i].y) * scale + gy;
            vertices.emplace_back(x, y, 0.0);
        }
        for (int i = 0; i < nvert_2d; i++) {
            double x = static_cast<double>(mesh->vert[i].x) * scale + gx;
            double y = static_cast<double>(mesh->vert[i].y) * scale + gy;
            vertices.emplace_back(x, y, effective_height);
        }

        // Create faces
        std::vector<std::array<int, 3>> faces;
        faces.reserve(nfaces_2d * 2 + nvert_2d * 2);  // top + bottom + sides estimate

        // Bottom faces (z=0) — reversed winding for outward normal pointing down
        for (int i = 0; i < nfaces_2d; i++) {
            faces.push_back({mesh->faces[i].v1, mesh->faces[i].v3, mesh->faces[i].v2});
        }

        // Top faces (z=height)
        for (int i = 0; i < nfaces_2d; i++) {
            faces.push_back({
                mesh->faces[i].v1 + nvert_2d,
                mesh->faces[i].v2 + nvert_2d,
                mesh->faces[i].v3 + nvert_2d
            });
        }

        // Side faces: connect boundary edges between top and bottom
        // Use the outline contours to find boundary edges
        ttf_outline_t* outline = ttf_linear_outline(pg.glyph_ptr, TTF_QUALITY_NORMAL);
        if (outline) {
            // For each contour edge, create two triangles (a quad) connecting top and bottom
            // We need to map outline points to mesh vertex indices
            // Since ttf_glyph2mesh uses the same outline internally, the vertex order
            // in the mesh corresponds to outline points. But we can't rely on this.
            // Instead, find the closest mesh vertex for each outline point.

            // Simpler approach: walk each contour and create side quads
            // using the outline point coordinates to find matching mesh vertices
            for (int c = 0; c < outline->ncontours; c++) {
                int length = outline->cont[c].length;
                if (length < 3) continue;

                for (int p = 0; p < length; p++) {
                    int p_next = (p + 1) % length;

                    float x0 = outline->cont[c].pt[p].x;
                    float y0 = outline->cont[c].pt[p].y;
                    float x1 = outline->cont[c].pt[p_next].x;
                    float y1 = outline->cont[c].pt[p_next].y;

                    // Find closest mesh vertices
                    int vi0 = -1, vi1 = -1;
                    double min_d0 = 1e10, min_d1 = 1e10;
                    for (int v = 0; v < nvert_2d; v++) {
                        double dx0 = mesh->vert[v].x - x0;
                        double dy0 = mesh->vert[v].y - y0;
                        double d0 = dx0 * dx0 + dy0 * dy0;
                        if (d0 < min_d0) { min_d0 = d0; vi0 = v; }

                        double dx1 = mesh->vert[v].x - x1;
                        double dy1 = mesh->vert[v].y - y1;
                        double d1 = dx1 * dx1 + dy1 * dy1;
                        if (d1 < min_d1) { min_d1 = d1; vi1 = v; }
                    }

                    if (vi0 >= 0 && vi1 >= 0 && vi0 != vi1) {
                        // Create two triangles for the side quad
                        // Bottom: vi0, vi1; Top: vi0+nvert_2d, vi1+nvert_2d
                        faces.push_back({vi0, vi1, vi1 + nvert_2d});
                        faces.push_back({vi0, vi1 + nvert_2d, vi0 + nvert_2d});
                    }
                }
            }
            ttf_free_outline(outline);
        }

        ttf_free_mesh(mesh);

        // Convert to Nef polyhedron
        Nef_polyhedron glyph_nef = make_nef_from_mesh(vertices, faces);
        if (glyph_nef.is_empty()) {
            continue;
        }

        if (first) {
            result = std::move(glyph_nef);
            first = false;
        } else {
            result = result + glyph_nef;
        }
    }

    return result;
}
