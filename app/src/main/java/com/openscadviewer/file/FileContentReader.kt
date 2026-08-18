package com.openscadviewer.file

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException

/**
 * Utility object for reading and writing file content via ContentResolver.
 * Handles UTF-8 encoding and wraps errors in Result types.
 */
object FileContentReader {

    /**
     * Reads the full content of the file at [uri] as a UTF-8 string.
     *
     * @return Result.success with the file content, or Result.failure on IOException/SecurityException.
     */
    fun readContent(contentResolver: ContentResolver, uri: Uri): Result<String> {
        return readContent(DefaultContentResolverCompat(contentResolver), uri)
    }

    /**
     * Reads the full content of the file at [uri] as a UTF-8 string.
     *
     * @return Result.success with the file content, or Result.failure on IOException/SecurityException.
     */
    fun readContent(contentResolver: ContentResolverCompat, uri: Uri): Result<String> {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: return Result.failure(IOException("Unable to open input stream for $uri"))
            val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Result.success(content)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    /**
     * Writes [content] as UTF-8 to the file at [uri] using "wt" mode (write-truncate).
     *
     * @return Result.success on success, or Result.failure on IOException/SecurityException.
     */
    fun writeContent(contentResolver: ContentResolver, uri: Uri, content: String): Result<Unit> {
        return writeContent(DefaultContentResolverCompat(contentResolver), uri, content)
    }

    /**
     * Writes [content] as UTF-8 to the file at [uri] using "wt" mode (write-truncate).
     *
     * @return Result.success on success, or Result.failure on IOException/SecurityException.
     */
    fun writeContent(contentResolver: ContentResolverCompat, uri: Uri, content: String): Result<Unit> {
        return try {
            val outputStream = contentResolver.openOutputStream(uri, "wt")
                ?: return Result.failure(IOException("Unable to open output stream for $uri"))
            outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    /**
     * Queries the content provider for the display name of the file at [uri].
     * Falls back to [uri]'s last path segment, or "untitled.scad" if unavailable.
     */
    fun getDisplayName(contentResolver: ContentResolver, uri: Uri): String {
        return getDisplayName(DefaultContentResolverCompat(contentResolver), uri)
    }

    /**
     * Queries the content provider for the display name of the file at [uri].
     * Falls back to [uri]'s last path segment, or "untitled.scad" if unavailable.
     */
    fun getDisplayName(contentResolver: ContentResolverCompat, uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                return name
                            }
                        }
                    }
                }
        } catch (_: Exception) {
            // Fall through to fallback
        }
        return uri.lastPathSegment ?: "untitled.scad"
    }
}
