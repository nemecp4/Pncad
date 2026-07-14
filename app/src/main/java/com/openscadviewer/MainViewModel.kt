package com.openscadviewer

import androidx.lifecycle.ViewModel

/**
 * ViewModel that preserves all transient screen state across configuration changes.
 * This includes editor content, renderer mesh data, camera parameters, and computation state.
 */
class MainViewModel : ViewModel() {

    // Editor state
    var editorText: String = ""
    var cursorPosition: Int = 0
    var currentFileName: String = ""
    var statusBarText: String = ""

    // Renderer state
    var meshVertices: FloatArray? = null
    var meshNormals: FloatArray? = null
    var meshColors: FloatArray? = null
    var triangleCount: Int = 0

    // Camera state
    var cameraRotX: Float = 30f
    var cameraRotY: Float = -45f
    var cameraDistance: Float = 10f
    var cameraPanX: Float = 0f
    var cameraPanY: Float = 0f

    // Computation state
    var isComputing: Boolean = false
}
