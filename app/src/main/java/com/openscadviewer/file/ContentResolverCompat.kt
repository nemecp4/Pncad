package com.openscadviewer.file

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/**
 * Thin abstraction over ContentResolver to enable unit testing of file I/O operations
 * without mocking libraries. Production code uses [DefaultContentResolverCompat] which
 * delegates to the real Android ContentResolver.
 */
interface ContentResolverCompat {
    fun openInputStream(uri: Uri): InputStream?
    fun openOutputStream(uri: Uri, mode: String): OutputStream?
    fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor?
}

/**
 * Production implementation that delegates to Android's ContentResolver.
 */
class DefaultContentResolverCompat(private val contentResolver: ContentResolver) : ContentResolverCompat {
    override fun openInputStream(uri: Uri): InputStream? = contentResolver.openInputStream(uri)
    override fun openOutputStream(uri: Uri, mode: String): OutputStream? = contentResolver.openOutputStream(uri, mode)
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
}
