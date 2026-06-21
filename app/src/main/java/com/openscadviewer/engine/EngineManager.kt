package com.openscadviewer.engine

import android.content.Context
import android.content.SharedPreferences

/**
 * Available compute engine types.
 */
enum class EngineType {
    KOTLIN,
    CGAL
}

/**
 * Manages engine selection and persistence.
 * Handles fallback to Kotlin engine when CGAL is unavailable.
 */
class EngineManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("engine_prefs", Context.MODE_PRIVATE)

    private val kotlinEngine = KotlinComputeEngine()
    private val cgalEngine = CgalComputeEngine()

    /**
     * The user's selected engine type, persisted across app restarts.
     * Defaults to KOTLIN on first launch.
     */
    var selectedType: EngineType
        get() {
            val stored = prefs.getString("engine_type", "KOTLIN")
            return try {
                EngineType.valueOf(stored ?: "KOTLIN")
            } catch (e: IllegalArgumentException) {
                EngineType.KOTLIN
            }
        }
        set(value) {
            prefs.edit().putString("engine_type", value.name).apply()
        }

    /**
     * The engine to use for computation based on the user's selection.
     * Falls back to Kotlin engine if CGAL is selected but unavailable.
     */
    val currentEngine: ComputeEngine
        get() = when (selectedType) {
            EngineType.KOTLIN -> kotlinEngine
            EngineType.CGAL -> {
                if (cgalEngine.isAvailable()) cgalEngine else kotlinEngine
            }
        }

    /**
     * Check whether the CGAL native library is available on this device.
     */
    fun isCgalAvailable(): Boolean = cgalEngine.isAvailable()
}
