package com.openscadviewer.settings

/**
 * Single source of truth for all SharedPreferences keys and default values
 * used by the preview display settings.
 */
object PreferenceKeys {
    const val KEY_SHOW_AXES = "pref_show_axes"
    const val KEY_SHOW_WIREFRAME = "pref_show_wireframe"
    const val KEY_BACKGROUND_COLOR = "pref_background_color"

    const val DEFAULT_SHOW_AXES = false
    const val DEFAULT_SHOW_WIREFRAME = false
    const val DEFAULT_BACKGROUND_COLOR = "dark_grey"
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
