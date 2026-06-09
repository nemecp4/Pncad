package com.openscadviewer.renderer

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.content.Context
import android.opengl.GLSurfaceView

/**
 * Handles touch gestures for 3D view manipulation:
 * - Single finger drag: orbit camera
 * - Two finger drag: pan camera
 * - Pinch: zoom in/out
 */
class TouchHandler(context: Context, private val glView: GLSurfaceView, private val renderer: SceneRenderer) {

    private var previousX = 0f
    private var previousY = 0f
    private var pointerCount = 0

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.cameraDistance /= detector.scaleFactor
            renderer.cameraDistance = renderer.cameraDistance.coerceIn(0.1f, 1000f)
            glView.requestRender()
            return true
        }
    })

    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                pointerCount = 1
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerCount = event.pointerCount
                previousX = event.x
                previousY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - previousX
                val dy = event.y - previousY

                if (pointerCount == 1 && !scaleDetector.isInProgress) {
                    // Single finger: orbit
                    renderer.cameraRotY += dx * 0.5f
                    renderer.cameraRotX += dy * 0.5f
                    renderer.cameraRotX = renderer.cameraRotX.coerceIn(-89f, 89f)
                } else if (pointerCount == 2 && !scaleDetector.isInProgress) {
                    // Two fingers: pan
                    val panScale = renderer.cameraDistance * 0.002f
                    renderer.cameraPanX += dx * panScale
                    renderer.cameraPanY -= dy * panScale
                }

                previousX = event.x
                previousY = event.y
                glView.requestRender()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerCount = maxOf(0, event.pointerCount - 1)
            }
        }
        return true
    }
}
