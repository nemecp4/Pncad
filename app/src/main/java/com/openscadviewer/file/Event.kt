package com.openscadviewer.file

/**
 * Single-shot event wrapper for LiveData. Ensures that UI events
 * (like showing a Snackbar or navigating) are only handled once,
 * even if the observer re-subscribes after a configuration change.
 */
class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? =
        if (hasBeenHandled) null else {
            hasBeenHandled = true
            content
        }
}
