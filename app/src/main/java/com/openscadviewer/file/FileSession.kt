package com.openscadviewer.file

import android.net.Uri
import java.util.UUID

data class FileSession(
    val id: String = UUID.randomUUID().toString(),
    var uri: Uri?,
    var displayName: String,
    var content: String,
    var lastSavedContent: String,
    var cursorPosition: Int = 0,
    var lastAccessedTimestamp: Long = System.currentTimeMillis()
) {
    val isDirty: Boolean
        get() = content != lastSavedContent
}
