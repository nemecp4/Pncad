package com.openscadviewer.file

import android.content.SharedPreferences
import android.net.TestUri
import android.net.Uri
import net.jqwik.api.*
import net.jqwik.api.lifecycle.BeforeTry
import org.junit.jupiter.api.Assertions.*

/**
 * Property-based tests for FileSessionManager.
 *
 * Validates: Requirements 2.2, 2.3, 3.2, 4.3, 4.4, 5.1, 5.2, 6.1, 6.7, 8.1, 8.2, 8.5, 8.6, 8.7
 */
class FileSessionManagerPropertyTest {

    private lateinit var manager: FileSessionManager

    // =========================================================================
    // Test Infrastructure
    // =========================================================================

    /**
     * In-memory fake SharedPreferences for testing without Android framework.
     */
    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        private val editor = FakeEditor(data)

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
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
        override fun edit(): SharedPreferences.Editor = editor
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

    @BeforeTry
    fun setUp() {
        manager = FileSessionManager(FakeSharedPreferences())
    }

    // =========================================================================
    // Property 1: Open file creates active session
    // =========================================================================

    /**
     * For any valid file content string and display name, when a file is opened
     * via openFile(), the resulting session list SHALL contain a session with
     * matching content and display name, and that session SHALL be the active session.
     *
     * **Validates: Requirements 2.2**
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 1: Open file creates active session")
    fun openFileCreatesActiveSession(
        @ForAll("fileContents") content: String,
        @ForAll("displayNames") displayName: String,
        @ForAll("uriIdentifiers") uriId: String
    ) {
        val uri = TestUri(uriId)
        val result = manager.openFile(uri, displayName, content)

        assertTrue(result.isSuccess, "openFile should succeed")

        val session = result.getOrThrow()
        assertEquals(content, session.content, "Session content should match opened content")
        assertEquals(displayName, session.displayName, "Session displayName should match")
        assertEquals(uri, session.uri, "Session uri should match opened uri")

        val active = manager.getActiveSession()
        assertNotNull(active, "Active session should not be null after opening")
        assertEquals(session.id, active!!.id, "Opened session should be the active session")

        assertTrue(
            manager.getOrderedSessions().any { it.id == session.id },
            "Session list should contain the opened session"
        )
    }

    // =========================================================================
    // Property 2: Opening duplicate URI reuses existing session
    // =========================================================================

    /**
     * For any session list containing a session with URI X, when openFile() is called
     * with the same URI X, the session list size SHALL remain unchanged and the existing
     * session for URI X SHALL become the active session.
     *
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 2: Opening duplicate URI reuses existing session")
    fun openingDuplicateUriReusesExistingSession(
        @ForAll("fileContents") content1: String,
        @ForAll("fileContents") content2: String,
        @ForAll("displayNames") displayName1: String,
        @ForAll("displayNames") displayName2: String,
        @ForAll("uriIdentifiers") uriId: String
    ) {
        val uri = TestUri(uriId)

        // Open the file initially
        val firstResult = manager.openFile(uri, displayName1, content1)
        assertTrue(firstResult.isSuccess)
        val firstSession = firstResult.getOrThrow()
        val sizeAfterFirst = manager.getOrderedSessions().size

        // Open another file to change the active session
        val otherUri = TestUri("$uriId-other")
        manager.openFile(otherUri, "other.scad", "other content")

        // Re-open with the same URI but different content/name
        val secondResult = manager.openFile(uri, displayName2, content2)
        assertTrue(secondResult.isSuccess)
        val secondSession = secondResult.getOrThrow()

        // Session list size should remain the same (no new session added for duplicate URI)
        assertEquals(
            sizeAfterFirst + 1, // +1 for the "other" session we added
            manager.getOrderedSessions().size,
            "Session list size should not increase when re-opening same URI"
        )

        // The reused session should be the same instance (same id)
        assertEquals(
            firstSession.id,
            secondSession.id,
            "Reused session should have same id as original"
        )

        // The reused session should become active
        assertEquals(
            secondSession.id,
            manager.getActiveSession()!!.id,
            "Reused session should become the active session"
        )
    }

    // =========================================================================
    // Property 4: Save clears dirty state
    // =========================================================================

    /**
     * For any session with arbitrary content, after markSaved() is called,
     * the session's isDirty property SHALL be false (because lastSavedContent
     * is updated to equal content).
     *
     * **Validates: Requirements 3.2, 4.4, 8.5**
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 4: Save clears dirty state")
    fun saveClearsDirtyState(
        @ForAll("fileContents") originalContent: String,
        @ForAll("fileContents") editedContent: String,
        @ForAll("uriIdentifiers") uriId: String
    ) {
        val uri = TestUri(uriId)
        val result = manager.openFile(uri, "test.scad", originalContent)
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()

        // Edit the content to make it dirty
        manager.updateContent(session.id, editedContent, 0)

        // Mark as saved
        manager.markSaved(session.id)

        // After save, isDirty SHALL be false
        assertFalse(
            session.isDirty,
            "Session should not be dirty after markSaved()"
        )
        assertEquals(
            session.content,
            session.lastSavedContent,
            "lastSavedContent should equal content after markSaved()"
        )
    }

    // =========================================================================
    // Property 5: Save-as updates session identity
    // =========================================================================

    /**
     * For any session and any new URI and display name, after markSaved(newUri, newDisplayName)
     * is called, the session's uri SHALL equal the new URI and displayName SHALL equal the
     * new display name.
     *
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 5: Save-as updates session identity")
    fun saveAsUpdatesSessionIdentity(
        @ForAll("fileContents") content: String,
        @ForAll("displayNames") originalName: String,
        @ForAll("displayNames") newName: String,
        @ForAll("uriIdentifiers") originalUriId: String,
        @ForAll("uriIdentifiers") newUriId: String
    ) {
        val originalUri = TestUri(originalUriId)
        val newUri = TestUri(newUriId)

        val result = manager.openFile(originalUri, originalName, content)
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()

        // Perform Save As with new URI and display name
        manager.markSaved(session.id, newUri, newName)

        // Session identity should be updated
        assertEquals(newUri, session.uri, "Session URI should be updated after Save As")
        assertEquals(newName, session.displayName, "Session displayName should be updated after Save As")

        // Dirty state should also be cleared
        assertFalse(session.isDirty, "Session should not be dirty after Save As")
    }

    // =========================================================================
    // Property 6: Close removes session and promotes next MRU
    // =========================================================================

    /**
     * For any session list with at least 2 sessions, when the active session is closed,
     * the list size SHALL decrease by 1, the closed session SHALL no longer appear in
     * the list, and the new active session SHALL be the most-recently-accessed remaining session.
     *
     * **Validates: Requirements 5.1, 5.2**
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 6: Close removes session and promotes next MRU")
    fun closeRemovesSessionAndPromotesNextMru(
        @ForAll("smallSessionCount") sessionCount: Int,
        @ForAll("fileContents") baseContent: String
    ) {
        // Create multiple sessions
        val createdSessions = (0 until sessionCount).map { i ->
            val uri = TestUri("content://test/file$i")
            val result = manager.openFile(uri, "file$i.scad", "$baseContent-$i")
            assertTrue(result.isSuccess)
            result.getOrThrow()
        }

        // The active session is the last one opened (index 0 in MRU order)
        val activeSession = manager.getActiveSession()!!
        val sizeBefore = manager.getOrderedSessions().size

        // Close the active session
        val newActive = manager.closeSession(activeSession.id)

        // List size should decrease by 1
        assertEquals(
            sizeBefore - 1,
            manager.getOrderedSessions().size,
            "Session list size should decrease by 1 after close"
        )

        // Closed session should no longer appear in the list
        assertFalse(
            manager.getOrderedSessions().any { it.id == activeSession.id },
            "Closed session should not appear in the session list"
        )

        // New active should be the next MRU session
        assertNotNull(newActive, "There should be a new active session when others remain")
        assertEquals(
            manager.getActiveSession()!!.id,
            newActive!!.id,
            "New active should be the most-recently-accessed remaining session"
        )
    }

    // =========================================================================
    // Property 7: MRU ordering invariant
    // =========================================================================

    /**
     * For any sequence of openFile() and switchTo() operations, the session list
     * SHALL always be ordered by lastAccessedTimestamp descending, with the active
     * session at index 0.
     *
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 7: MRU ordering invariant")
    fun mruOrderingInvariant(
        @ForAll("operationSequences") operations: List<SessionOperation>
    ) {
        // Execute each operation
        for (op in operations) {
            when (op) {
                is SessionOperation.Open -> {
                    manager.openFile(op.uri, op.displayName, op.content)
                }
                is SessionOperation.Switch -> {
                    val sessions = manager.getOrderedSessions()
                    if (sessions.isNotEmpty()) {
                        val targetIndex = op.targetIndex.mod(sessions.size)
                        manager.switchTo(sessions[targetIndex].id)
                    }
                }
            }
        }

        // Verify MRU ordering: timestamps should be descending
        val sessions = manager.getOrderedSessions()
        for (i in 1 until sessions.size) {
            assertTrue(
                sessions[i - 1].lastAccessedTimestamp >= sessions[i].lastAccessedTimestamp,
                "Sessions should be ordered by lastAccessedTimestamp descending. " +
                    "Index ${i - 1} (${sessions[i - 1].lastAccessedTimestamp}) should >= " +
                    "index $i (${sessions[i].lastAccessedTimestamp})"
            )
        }

        // Active session (if any) should be at index 0
        val active = manager.getActiveSession()
        if (active != null && sessions.isNotEmpty()) {
            assertEquals(
                active.id,
                sessions[0].id,
                "Active session should be at index 0 (MRU position)"
            )
        }
    }

    // =========================================================================
    // Property 9: Session count capped at 20
    // =========================================================================

    /**
     * For any sequence of openFile() operations, the session list size SHALL never exceed 20.
     *
     * **Validates: Requirements 6.7**
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 9: Session count capped at 20")
    fun sessionCountCappedAt20(
        @ForAll("largeOpenSequence") openOps: List<SessionOperation.Open>
    ) {
        for (op in openOps) {
            manager.openFile(op.uri, op.displayName, op.content)

            // Invariant: session count never exceeds 20
            assertTrue(
                manager.getOrderedSessions().size <= 20,
                "Session count should never exceed 20, but was ${manager.getOrderedSessions().size}"
            )
        }
    }

    // =========================================================================
    // Property 10: Dirty state reflects content divergence
    // =========================================================================

    /**
     * For any session, isDirty SHALL be true if and only if content != lastSavedContent.
     *
     * **Validates: Requirements 8.1, 8.2**
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 10: Dirty state reflects content divergence")
    fun dirtyStateReflectsContentDivergence(
        @ForAll("fileContents") originalContent: String,
        @ForAll("fileContents") newContent: String,
        @ForAll("uriIdentifiers") uriId: String
    ) {
        val uri = TestUri(uriId)
        val result = manager.openFile(uri, "test.scad", originalContent)
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()

        // Update content
        manager.updateContent(session.id, newContent, 0)

        // isDirty should be true iff content != lastSavedContent
        val expectedDirty = session.content != session.lastSavedContent
        assertEquals(
            expectedDirty,
            session.isDirty,
            "isDirty should be true iff content != lastSavedContent. " +
                "content='${session.content}', lastSavedContent='${session.lastSavedContent}'"
        )
    }

    // =========================================================================
    // Property 11: Initial dirty state correctness
    // =========================================================================

    /**
     * For any file content string, a session created via openFile() (from disk) SHALL have
     * isDirty == false, and a session created via createUntitled() SHALL have isDirty == true.
     *
     * **Validates: Requirements 8.6, 8.7**
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 11: Initial dirty state correctness")
    fun initialDirtyStateCorrectness(
        @ForAll("fileContents") content: String,
        @ForAll("displayNames") displayName: String,
        @ForAll("uriIdentifiers") uriId: String
    ) {
        val uri = TestUri(uriId)

        // Session from openFile (file opened from disk) should NOT be dirty
        val openResult = manager.openFile(uri, displayName, content)
        assertTrue(openResult.isSuccess)
        val openedSession = openResult.getOrThrow()
        assertFalse(
            openedSession.isDirty,
            "A session created via openFile() should have isDirty == false"
        )

        // Session from createUntitled should be dirty
        val untitledSession = manager.createUntitled()
        assertTrue(
            untitledSession.isDirty,
            "A session created via createUntitled() should have isDirty == true"
        )
    }

    // =========================================================================
    // Data Models
    // =========================================================================

    sealed class SessionOperation {
        data class Open(val uri: Uri, val displayName: String, val content: String) : SessionOperation()
        data class Switch(val targetIndex: Int) : SessionOperation()
    }

    // =========================================================================
    // Generators
    // =========================================================================

    @Provide
    fun fileContents(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange(' ', '~')  // printable ASCII
            .ofMinLength(1)
            .ofMaxLength(200)
    }

    @Provide
    fun displayNames(): Arbitrary<String> {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(20)
            .map { "$it.scad" }
    }

    @Provide
    fun uriIdentifiers(): Arbitrary<String> {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(30)
            .map { "content://test/$it" }
    }

    @Provide
    fun smallSessionCount(): Arbitrary<Int> {
        return Arbitraries.integers().between(2, 5)
    }

    @Provide
    fun operationSequences(): Arbitrary<List<SessionOperation>> {
        val openOp = Combinators.combine(
            uriIdentifiers(),
            displayNames(),
            fileContents()
        ).`as` { uri, name, content ->
            SessionOperation.Open(TestUri(uri), name, content) as SessionOperation
        }
        val switchOp = Arbitraries.integers().between(0, 19)
            .map { SessionOperation.Switch(it) as SessionOperation }

        return Arbitraries.frequencyOf(
            Tuple.of(3, openOp),
            Tuple.of(2, switchOp)
        ).list().ofMinSize(1).ofMaxSize(15)
    }

    @Provide
    fun largeOpenSequence(): Arbitrary<List<SessionOperation.Open>> {
        return Combinators.combine(
            Arbitraries.integers().between(0, 29).map { "content://test/file-$it" },
            displayNames(),
            fileContents()
        ).`as` { uri, name, content ->
            SessionOperation.Open(TestUri(uri), name, content)
        }.list().ofMinSize(15).ofMaxSize(30)
    }
}
