package com.openscadviewer.settings

import com.openscadviewer.editor.OpenScadColorScheme
import com.openscadviewer.editor.OpenScadLightColorScheme
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019

/**
 * Single source of truth for all SharedPreferences keys and default values
 * used by the preview display settings.
 */
object PreferenceKeys {
    const val KEY_SHOW_AXES = "pref_show_axes"
    const val KEY_SHOW_WIREFRAME = "pref_show_wireframe"
    const val KEY_BACKGROUND_COLOR = "pref_background_color"
    const val KEY_EDITOR_THEME = "pref_editor_theme"

    const val DEFAULT_SHOW_AXES = false
    const val DEFAULT_SHOW_WIREFRAME = false
    const val DEFAULT_BACKGROUND_COLOR = "dark_grey"
    const val DEFAULT_EDITOR_THEME = "openscad_dark"
}

/**
 * Maps a background color preference key to its RGBA float array representation.
 * Returns the default Dark Grey color for any unrecognized key.
 */
fun mapBackgroundColor(key: String): FloatArray = when (key) {
    "dark_grey" -> floatArrayOf(0.18f, 0.18f, 0.18f, 1.0f)
    "white"     -> floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)
    "yellow"    -> floatArrayOf(1.0f, 1.0f, 0.5f, 1.0f)
    else        -> floatArrayOf(0.18f, 0.18f, 0.18f, 1.0f)
}

/**
 * Maps an editor-theme preference value to a fresh [EditorColorScheme] instance.
 *
 * "openscad_dark" and "openscad_light" are the custom schemes that color the
 * OpenSCAD span categories; the remaining values are sora-editor's bundled
 * schemes, which style standard tokens only. Unknown values fall back to the
 * default OpenSCAD dark scheme.
 */
fun mapEditorColorScheme(value: String): EditorColorScheme = when (value) {
    "openscad_dark"  -> OpenScadColorScheme()
    "openscad_light" -> OpenScadLightColorScheme()
    "darcula"        -> SchemeDarcula()
    "vs2019"         -> SchemeVS2019()
    "eclipse"        -> SchemeEclipse()
    "github"         -> SchemeGitHub()
    "notepadxx"      -> SchemeNotepadXX()
    else             -> OpenScadColorScheme()
}
