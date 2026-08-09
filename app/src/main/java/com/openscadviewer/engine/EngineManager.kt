package com.openscadviewer.engine

import android.content.Context
import android.content.SharedPreferences
import com.openscadviewer.engine.text.AndroidFontProvider
import java.io.File

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
class EngineManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("engine_prefs", Context.MODE_PRIVATE)

    private val kotlinEngine = KotlinComputeEngine(AndroidFontProvider(context))
    private val cgalEngine = CgalComputeEngine()

    init {
        // Extract bundled font for CGAL native text rendering
        initCgalFontPath()
    }

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

    /**
     * Extract the bundled Liberation Sans font from APK assets to the app cache directory
     * and pass the path to the CGAL native text renderer.
     * The font is only extracted on first use or if the cached file is missing.
     */
    private fun initCgalFontPath() {
        if (!cgalEngine.isAvailable()) return

        val fontDir = File(context.cacheDir, "fonts")
        val fontFile = File(fontDir, "LiberationSans-Regular.ttf")

        if (!fontFile.exists()) {
            try {
                fontDir.mkdirs()
                context.assets.open("fonts/LiberationSans-Regular.ttf").use { input ->
                    fontFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // Font extraction failed — text rendering will be unavailable in CGAL
                return
            }
        }

        cgalEngine.setFontPath(fontFile.absolutePath)
    }
}
