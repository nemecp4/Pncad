package com.openscadviewer.file

import android.database.Cursor
import android.net.TestUri
import android.net.Uri
import android.provider.OpenableColumns
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Unit tests for FileContentReader.
 *
 * Uses a fake ContentResolverCompat and TestUri (in android.net package) to bypass
 * Android's final methods and package-private constructor restrictions.
 *
 * Validates: Requirements 2.6, 3.4, 4.5
 */
class FileContentReaderTest {

    // --- Test doubles ---

    /**
     * A fake ContentResolverCompat that returns configurable streams and cursors.
     */
    private class FakeContentResolver : ContentResolverCompat {
        var inputStreamToReturn: InputStream? = null
        var outputStreamToReturn: OutputStream? = null
        var inputException: Exception? = null
        var outputException: Exception? = null
        var cursorToReturn: Cursor? = null
        var queryException: Exception? = null

        override fun openInputStream(uri: Uri): InputStream? {
            inputException?.let { throw it }
            return inputStreamToReturn
        }

        override fun openOutputStream(uri: Uri, mode: String): OutputStream? {
            outputException?.let { throw it }
            return outputStreamToReturn
        }

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?
        ): Cursor? {
            queryException?.let { throw it }
            return cursorToReturn
        }
    }

    /**
     * A minimal Cursor implementation that returns a single row with a configurable display name.
     * Implements Cursor directly (rather than AbstractCursor) because AbstractCursor's
     * moveToFirst() etc. rely on internal state that doesn't work with isReturnDefaultValues.
     */
    private class FakeCursor(private val displayName: String?) : Cursor {
        private val columnNames = arrayOf(OpenableColumns.DISPLAY_NAME)
        private var position = -1
        private var closed = false

        override fun getCount(): Int = if (displayName != null) 1 else 0
        override fun getPosition(): Int = position
        override fun move(offset: Int): Boolean {
            position += offset
            return position in 0 until getCount()
        }
        override fun moveToPosition(pos: Int): Boolean {
            position = pos
            return position in 0 until getCount()
        }
        override fun moveToFirst(): Boolean = moveToPosition(0)
        override fun moveToLast(): Boolean = moveToPosition(getCount() - 1)
        override fun moveToNext(): Boolean = move(1)
        override fun moveToPrevious(): Boolean = move(-1)
        override fun isFirst(): Boolean = position == 0
        override fun isLast(): Boolean = position == getCount() - 1
        override fun isBeforeFirst(): Boolean = position < 0
        override fun isAfterLast(): Boolean = position >= getCount()
        override fun getColumnIndex(columnName: String?): Int =
            columnNames.indexOf(columnName)
        override fun getColumnIndexOrThrow(columnName: String?): Int =
            getColumnIndex(columnName).also { if (it < 0) throw IllegalArgumentException() }
        override fun getColumnName(columnIndex: Int): String? = columnNames.getOrNull(columnIndex)
        override fun getColumnNames(): Array<String> = columnNames
        override fun getColumnCount(): Int = columnNames.size
        override fun getBlob(columnIndex: Int): ByteArray? = null
        override fun getString(columnIndex: Int): String? =
            if (columnIndex == 0 && position == 0) displayName else null
        override fun copyStringToBuffer(columnIndex: Int, buffer: android.database.CharArrayBuffer?) {}
        override fun getShort(columnIndex: Int): Short = 0
        override fun getInt(columnIndex: Int): Int = 0
        override fun getLong(columnIndex: Int): Long = 0
        override fun getFloat(columnIndex: Int): Float = 0f
        override fun getDouble(columnIndex: Int): Double = 0.0
        override fun getType(columnIndex: Int): Int = Cursor.FIELD_TYPE_STRING
        override fun isNull(columnIndex: Int): Boolean = getString(columnIndex) == null
        @Deprecated("Deprecated in API")
        override fun deactivate() {}
        @Deprecated("Deprecated in API")
        override fun requery(): Boolean = true
        override fun close() { closed = true }
        override fun isClosed(): Boolean = closed
        override fun registerContentObserver(observer: android.database.ContentObserver?) {}
        override fun unregisterContentObserver(observer: android.database.ContentObserver?) {}
        override fun registerDataSetObserver(observer: android.database.DataSetObserver?) {}
        override fun unregisterDataSetObserver(observer: android.database.DataSetObserver?) {}
        override fun setNotificationUri(cr: android.content.ContentResolver?, uri: Uri?) {}
        override fun getNotificationUri(): Uri? = null
        override fun getWantsAllOnMoveCalls(): Boolean = false
        override fun setExtras(extras: android.os.Bundle?) {}
        override fun getExtras(): android.os.Bundle? = null
        override fun respond(extras: android.os.Bundle?): android.os.Bundle? = null
    }

    private val testUri: Uri = TestUri("content://com.test/document/123")

    // --- readContent tests ---

    @Test
    fun `readContent returns success with file content when stream is available`() {
        val content = "module box() { cube([1,2,3]); }"
        val resolver = FakeContentResolver().apply {
            inputStreamToReturn = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        }

        val result = FileContentReader.readContent(resolver, testUri)

        assertTrue(result.isSuccess)
        assertEquals(content, result.getOrNull())
    }

    @Test
    fun `readContent returns success with UTF-8 content including special characters`() {
        val content = "// Ünïcödé: 日本語テスト ∆∑∏"
        val resolver = FakeContentResolver().apply {
            inputStreamToReturn = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        }

        val result = FileContentReader.readContent(resolver, testUri)

        assertTrue(result.isSuccess)
        assertEquals(content, result.getOrNull())
    }

    @Test
    fun `readContent returns failure when openInputStream returns null`() {
        val resolver = FakeContentResolver().apply {
            inputStreamToReturn = null
        }

        val result = FileContentReader.readContent(resolver, testUri)

        assertTrue(result.isFailure)
        assertInstanceOf(IOException::class.java, result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Unable to open input stream"))
    }

    @Test
    fun `readContent returns failure when openInputStream throws IOException`() {
        val resolver = FakeContentResolver().apply {
            inputException = IOException("Disk read error")
        }

        val result = FileContentReader.readContent(resolver, testUri)

        assertTrue(result.isFailure)
        assertInstanceOf(IOException::class.java, result.exceptionOrNull())
        assertEquals("Disk read error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `readContent returns failure when openInputStream throws SecurityException`() {
        val resolver = FakeContentResolver().apply {
            inputException = SecurityException("Permission denied")
        }

        val result = FileContentReader.readContent(resolver, testUri)

        assertTrue(result.isFailure)
        assertInstanceOf(SecurityException::class.java, result.exceptionOrNull())
    }

    // --- writeContent tests ---

    @Test
    fun `writeContent returns success and writes content when stream is available`() {
        val content = "translate([1,0,0]) sphere(r=5);"
        val outputStream = ByteArrayOutputStream()
        val resolver = FakeContentResolver().apply {
            outputStreamToReturn = outputStream
        }

        val result = FileContentReader.writeContent(resolver, testUri, content)

        assertTrue(result.isSuccess)
        assertEquals(content, outputStream.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `writeContent handles UTF-8 content correctly`() {
        val content = "// Ünïcödé: 日本語テスト"
        val outputStream = ByteArrayOutputStream()
        val resolver = FakeContentResolver().apply {
            outputStreamToReturn = outputStream
        }

        val result = FileContentReader.writeContent(resolver, testUri, content)

        assertTrue(result.isSuccess)
        assertEquals(content, outputStream.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `writeContent returns failure when openOutputStream returns null`() {
        val resolver = FakeContentResolver().apply {
            outputStreamToReturn = null
        }

        val result = FileContentReader.writeContent(resolver, testUri, "content")

        assertTrue(result.isFailure)
        assertInstanceOf(IOException::class.java, result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Unable to open output stream"))
    }

    @Test
    fun `writeContent returns failure when openOutputStream throws IOException`() {
        val resolver = FakeContentResolver().apply {
            outputException = IOException("Disk full")
        }

        val result = FileContentReader.writeContent(resolver, testUri, "content")

        assertTrue(result.isFailure)
        assertInstanceOf(IOException::class.java, result.exceptionOrNull())
        assertEquals("Disk full", result.exceptionOrNull()?.message)
    }

    @Test
    fun `writeContent returns failure when openOutputStream throws SecurityException`() {
        val resolver = FakeContentResolver().apply {
            outputException = SecurityException("Write permission revoked")
        }

        val result = FileContentReader.writeContent(resolver, testUri, "content")

        assertTrue(result.isFailure)
        assertInstanceOf(SecurityException::class.java, result.exceptionOrNull())
    }

    // --- getDisplayName tests ---

    @Test
    fun `getDisplayName returns name from cursor when available`() {
        val cursor = FakeCursor("my_model.scad")
        val resolver = FakeContentResolver().apply {
            cursorToReturn = cursor
        }

        val result = FileContentReader.getDisplayName(resolver, testUri)

        assertEquals("my_model.scad", result)
    }

    @Test
    fun `getDisplayName falls back to last path segment when cursor is null`() {
        val resolver = FakeContentResolver().apply {
            cursorToReturn = null
        }
        val uriWithPath = TestUri("content://com.test/document/some_file.scad", "some_file.scad")

        val result = FileContentReader.getDisplayName(resolver, uriWithPath)

        assertEquals("some_file.scad", result)
    }

    @Test
    fun `getDisplayName falls back to last path segment when cursor returns blank name`() {
        val cursor = FakeCursor("   ")
        val resolver = FakeContentResolver().apply {
            cursorToReturn = cursor
        }
        val uriWithPath = TestUri("content://com.test/document/fallback.scad", "fallback.scad")

        val result = FileContentReader.getDisplayName(resolver, uriWithPath)

        assertEquals("fallback.scad", result)
    }

    @Test
    fun `getDisplayName returns untitled when cursor is null and URI has no path segment`() {
        val resolver = FakeContentResolver().apply {
            cursorToReturn = null
        }
        val uriNoPath = TestUri("content://com.test", null)

        val result = FileContentReader.getDisplayName(resolver, uriNoPath)

        assertEquals("untitled.scad", result)
    }

    @Test
    fun `getDisplayName falls back gracefully when query throws exception`() {
        val resolver = FakeContentResolver().apply {
            queryException = SecurityException("Permission denied")
        }
        val uriWithPath = TestUri("content://com.test/document/recovered.scad", "recovered.scad")

        val result = FileContentReader.getDisplayName(resolver, uriWithPath)

        assertEquals("recovered.scad", result)
    }
}
