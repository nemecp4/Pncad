package com.openscadviewer.file

import android.content.SharedPreferences
import android.net.TestUri
import android.net.Uri
import net.jqwik.api.*
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: file-management
 *
 * Property-based tests for FileSessionManager error resilience.
 *
 * Property 3: Failed I/O preserves session state
 * For any session list state and active session, when a file operation (open, save, save-as)
 * fails, the session list, active session pointer, each session's content, and each session's
 * dirty state SHALL remain identical to their pre-operation values.
 *
 * Validates: Requirements 2.6, 3.4, 4.5, 5.6
 */
class FileSessionErrorResiliencePropertyTest {

    private lateinit var manager: FileSessionManager

    @BeforeProperty
    fun setUp() {
        manager = FileSessionManager(FakeSharedPreferences())
    }

    // =========================================================================
    // Property 3: Failed I/O preserves session state
    // =========================================================================

    /**
     * When the session manager is at max capacity (20 sessions), attempting to open a new file
     * returns a failure and the entire session state (list, active pointer, content, dirty state)
     * remains unchanged.
     *
     * Validates: Requirements 2.6, 3.4, 4.5, 5.6
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 3: Failed I/O preserves session state")
    fun openFileAtMaxCapacityPreservesSessionState(
        @ForAll("fileContents") newContent: String,
        @ForAll("displayNames") newDisplayName: String,
        @ForAll("dirtyIndices") dirtyIndices: List<Int>
    ) {
        // Fill to max capacity (20 sessions) with unique URIs
        for (i in 0 until 20) {
            val uri = TestUri("content://test.provider/file_$i")
            manager.openFile(uri, "file_$i.scad", "content_$i")
        }

        // Make some sessions dirty based on generated indices
        val sessions = manager.getOrderedSessions()
        for (idx in dirtyIndices) {
            val safeIdx = idx.coerceIn(0, sessions.size - 1)
            manager.updateContent(sessions[safeIdx].id, sessions[safeIdx].content + "_modified", safeIdx)
        }

        // Capture pre-operation state
        val preState = captureState(manager)

        // Attempt to open a new file — should fail due to max capacity
        val newUri = TestUri("content://test.provider/brand_new_file")
        val result = manager.openFile(newUri, newDisplayName, newContent)

        // Verify operation failed
        assertTrue(result.isFailure, "openFile should fail at max capacity")

        // Verify state is completely unchanged
        val postState = captureState(manager)
        assertStateUnchanged(preState, postState)
    }

    /**
     * Operations on non-existent session IDs (switchTo) leave state unchanged.
     *
     * Validates: Requirements 2.6, 3.4, 4.5, 5.6
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 3: Failed I/O preserves session state")
    fun switchToNonExistentSessionPreservesState(
        @ForAll("sessionCounts") sessionCount: Int,
        @ForAll("invalidSessionIds") invalidId: String,
        @ForAll("dirtyIndices") dirtyIndices: List<Int>
    ) {
        // Create sessions using createUntitled (avoids Uri issues)
        for (i in 0 until sessionCount) {
            val session = manager.createUntitled()
            manager.updateContent(session.id, "content_$i", i)
        }

        // Make some sessions dirty
        val sessions = manager.getOrderedSessions()
        for (idx in dirtyIndices) {
            if (sessions.isNotEmpty()) {
                val safeIdx = idx.coerceIn(0, sessions.size - 1)
                manager.updateContent(sessions[safeIdx].id, sessions[safeIdx].content + "_edited", safeIdx * 2)
            }
        }

        // Capture pre-operation state
        val preState = captureState(manager)

        // Attempt to switch to a non-existent session
        val result = manager.switchTo(invalidId)

        // Verify operation returned null (session not found)
        assertNull(result, "switchTo with invalid ID should return null")

        // Verify state is completely unchanged
        val postState = captureState(manager)
        assertStateUnchanged(preState, postState)
    }

    /**
     * Operations on non-existent session IDs (updateContent) leave state unchanged.
     *
     * Validates: Requirements 2.6, 3.4, 4.5, 5.6
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 3: Failed I/O preserves session state")
    fun updateContentOnNonExistentSessionPreservesState(
        @ForAll("sessionCounts") sessionCount: Int,
        @ForAll("invalidSessionIds") invalidId: String,
        @ForAll("fileContents") newContent: String
    ) {
        // Create sessions
        for (i in 0 until sessionCount) {
            val session = manager.createUntitled()
            manager.updateContent(session.id, "original_content_$i", i)
        }

        // Capture pre-operation state
        val preState = captureState(manager)

        // Attempt to update content on a non-existent session
        manager.updateContent(invalidId, newContent, 5)

        // Verify state is completely unchanged
        val postState = captureState(manager)
        assertStateUnchanged(preState, postState)
    }

    /**
     * Operations on non-existent session IDs (markSaved) leave state unchanged.
     *
     * Validates: Requirements 2.6, 3.4, 4.5, 5.6
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 3: Failed I/O preserves session state")
    fun markSavedOnNonExistentSessionPreservesState(
        @ForAll("sessionCounts") sessionCount: Int,
        @ForAll("invalidSessionIds") invalidId: String
    ) {
        // Create sessions with some dirty
        for (i in 0 until sessionCount) {
            val session = manager.createUntitled()
            manager.updateContent(session.id, "modified_content_$i", i)
        }

        // Capture pre-operation state
        val preState = captureState(manager)

        // Attempt to mark saved on a non-existent session
        val fakeUri = TestUri("content://new/uri")
        manager.markSaved(invalidId, fakeUri, "newName.scad")

        // Verify state is completely unchanged
        val postState = captureState(manager)
        assertStateUnchanged(preState, postState)
    }

    /**
     * closeSession on a non-existent session ID leaves state unchanged and returns the
     * current active session.
     *
     * Validates: Requirements 2.6, 3.4, 4.5, 5.6
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 3: Failed I/O preserves session state")
    fun closeSessionOnNonExistentSessionPreservesState(
        @ForAll("sessionCounts") sessionCount: Int,
        @ForAll("invalidSessionIds") invalidId: String
    ) {
        // Create sessions
        for (i in 0 until sessionCount) {
            val session = manager.createUntitled()
            manager.updateContent(session.id, "content_for_close_test_$i", i)
        }

        // Capture pre-operation state
        val preState = captureState(manager)

        // Attempt to close a non-existent session
        val result = manager.closeSession(invalidId)

        // Should return the current active session (unchanged)
        assertEquals(
            preState.activeSessionId,
            result?.id,
            "closeSession with invalid ID should return current active session"
        )

        // Verify state is completely unchanged
        val postState = captureState(manager)
        assertStateUnchanged(preState, postState)
    }

    // =========================================================================
    // Generators
    // =========================================================================

    @Provide
    fun fileContents(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange(' ', '~')
            .ofMinLength(1)
            .ofMaxLength(200)
    }

    @Provide
    fun displayNames(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(15)
            .map { "$it.scad" }
    }

    @Provide
    fun sessionCounts(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 10)
    }

    @Provide
    fun invalidSessionIds(): Arbitrary<String> {
        // Generate IDs that will never match a UUID
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(5)
            .ofMaxLength(15)
            .map { "invalid_$it" }
    }

    @Provide
    fun dirtyIndices(): Arbitrary<List<Int>> {
        return Arbitraries.integers().between(0, 19).list().ofMinSize(0).ofMaxSize(5)
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Captures a complete snapshot of the session manager state.
     */
    private fun captureState(manager: FileSessionManager): SessionManagerState {
        val sessions = manager.getOrderedSessions()
        val activeSession = manager.getActiveSession()
        return SessionManagerState(
            sessionCount = sessions.size,
            activeSessionId = activeSession?.id,
            sessionSnapshots = sessions.map { session ->
                SessionSnapshot(
                    id = session.id,
                    uri = session.uri?.toString(),
                    displayName = session.displayName,
                    content = session.content,
                    lastSavedContent = session.lastSavedContent,
                    isDirty = session.isDirty,
                    cursorPosition = session.cursorPosition
                )
            }
        )
    }

    /**
     * Asserts that two state captures are identical.
     */
    private fun assertStateUnchanged(pre: SessionManagerState, post: SessionManagerState) {
        assertEquals(pre.sessionCount, post.sessionCount, "Session count changed")
        assertEquals(pre.activeSessionId, post.activeSessionId, "Active session pointer changed")
        assertEquals(pre.sessionSnapshots.size, post.sessionSnapshots.size, "Session list size changed")

        for (i in pre.sessionSnapshots.indices) {
            val preSS = pre.sessionSnapshots[i]
            val postSS = post.sessionSnapshots[i]
            assertEquals(preSS.id, postSS.id, "Session ID at index $i changed")
            assertEquals(preSS.uri, postSS.uri, "Session URI at index $i changed")
            assertEquals(preSS.displayName, postSS.displayName, "Session displayName at index $i changed")
            assertEquals(preSS.content, postSS.content, "Session content at index $i changed")
            assertEquals(preSS.lastSavedContent, postSS.lastSavedContent, "Session lastSavedContent at index $i changed")
            assertEquals(preSS.isDirty, postSS.isDirty, "Session dirty state at index $i changed")
            assertEquals(preSS.cursorPosition, postSS.cursorPosition, "Session cursorPosition at index $i changed")
        }
    }

    // =========================================================================
    // Data classes for state capture
    // =========================================================================

    private data class SessionManagerState(
        val sessionCount: Int,
        val activeSessionId: String?,
        val sessionSnapshots: List<SessionSnapshot>
    )

    private data class SessionSnapshot(
        val id: String,
        val uri: String?,
        val displayName: String,
        val content: String,
        val lastSavedContent: String,
        val isDirty: Boolean,
        val cursorPosition: Int
    )

    // =========================================================================
    // Fake SharedPreferences
    // =========================================================================

    /**
     * In-memory fake SharedPreferences for testing without Android framework.
     */
    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            data[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int =
            data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long =
            data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
            data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}

        private class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) data[key] = value; return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) data[key] = values; return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) data[key] = value; return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) data[key] = value; return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) data[key] = value; return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) data[key] = value; return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                data.remove(key); return this
            }
            override fun clear(): SharedPreferences.Editor {
                data.clear(); return this
            }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }
}
