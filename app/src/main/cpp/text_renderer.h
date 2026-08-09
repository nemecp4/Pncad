#pragma once

#include "scene_builder.h"
#include <string>
#include <vector>
#include <unordered_map>
#include <atomic>
#include <cstdint>

// Forward declaration — ttf2mesh types
struct ttf_file;
typedef struct ttf_file ttf_t;

/**
 * Manages font loading and glyph-to-polygon conversion using ttf2mesh.
 * Caches glyph outlines for reuse across characters.
 *
 * The font file path must be set before any text rendering calls via set_font_path().
 * On Android, this is the path to the extracted font in the app cache directory.
 * On desktop, this is a filesystem path to the bundled font file.
 */
class TextRenderer {
public:
    TextRenderer();
    ~TextRenderer();

    /**
     * Set the filesystem path to the TrueType font file.
     * Must be called before build_text() to enable font loading.
     * Thread-safe: can be called from any thread before rendering begins.
     */
    void set_font_path(const std::string& path);

    /**
     * Get the currently configured font path.
     */
    const std::string& get_font_path() const { return font_path_; }

    /**
     * Build a Nef polyhedron from text content.
     * Uses ttf2mesh to extract glyph meshes and positions them according to
     * layout parameters (size, alignment, spacing, direction).
     *
     * @param text          The text string to render
     * @param size          Font size in OpenSCAD units
     * @param font          Font name (currently only bundled font is supported)
     * @param halign        Horizontal alignment: "left", "center", "right"
     * @param valign        Vertical alignment: "baseline", "bottom", "top", "center"
     * @param spacing       Character spacing multiplier
     * @param direction     Text direction: "ltr" or "rtl"
     * @param extrude_height  Extrusion height (0 = flat 2D polygon)
     * @param cancel_flag   Atomic cancel flag for early termination
     * @return Nef polyhedron representing the text geometry (empty if rendering fails)
     */
    Nef_polyhedron build_text(
        const std::string& text,
        double size,
        const std::string& font,
        const std::string& halign,
        const std::string& valign,
        double spacing,
        const std::string& direction,
        double extrude_height,
        std::atomic<bool>& cancel_flag
    );

private:
    struct GlyphData {
        std::vector<std::vector<std::pair<double, double>>> contours;
        double advance_width;
        double ascent;
        double descent;
    };

    /**
     * Load the font from the configured font path.
     * Returns nullptr if font cannot be loaded.
     */
    ttf_t* load_font();

    /**
     * Extract glyph polygon data for a given character.
     * Returns cached data if available.
     */
    GlyphData get_glyph(ttf_t* font, int codepoint);

    /**
     * Build a Nef polyhedron from 2D contours by extruding them.
     */
    Nef_polyhedron extrude_contours(
        const std::vector<std::vector<std::pair<double, double>>>& contours,
        double height,
        std::atomic<bool>& cancel_flag
    );

    std::string font_path_;
    ttf_t* cached_font_;
    std::unordered_map<uint64_t, GlyphData> glyph_cache_;
};
