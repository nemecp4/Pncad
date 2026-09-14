package com.openscadviewer.file

import android.content.SharedPreferences
import android.net.Uri
import net.jqwik.api.*
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: file-management
 *
 * Property-based test for Property 8: Session state isolation on switch.
 *
 * For any two sessions A and B with distinct content and cursor positions,
 * switching from A to B and back to A SHALL preserve A's content, cursor position,
 * and dirty state exactly as they were before the switch.
 *
 * Validates: Requirements 6.2
 */
class FileSessionStateIsolationPropertyTest {

    private lateinit var manager: FileSessionManager

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

    @BeforeProperty
    fun setUp() {
        manager = FileSessionManager(FakeSharedPreferences())
    }

    // =========================================================================
    // Property 8: Session state isolation on switch
    // =========================================================================

    /**
     * Feature: file-management, Property 8: Session state isolation on switch
     *
     * For any two sessions A and B with distinct content and cursor positions,
     * switching from A to B and back to A SHALL preserve A's content, cursor
     * position, and dirty state exactly as they were before the switch.
     *
     * Validates: Requirements 6.2
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 8: Session state isolation on switch")
    fun switchingBetweenSessionsPreservesState(
        @ForAll("fileContents") contentA: String,
        @ForAll("fileContents") contentB: String,
        @ForAll("cursorPositions") cursorA: Int,
        @ForAll("cursorPositions") cursorB: Int,
        @ForAll("editedContents") editedContentA: String
    ) {
        // Create two sessions via openFile with Uri.parse (returns null stub with returnDefaultValues=true)
        // Since both URIs parse to the same null stub, we use createUntitled to get distinct sessions
        val sessionA = manager.createUntitled()
        val sessionB = manager.createUntitled()

        // Clamp cursor positions to valid range for the content
        val clampedCursorA = cursorA.coerceIn(0, editedContentA.length)
        val clampedCursorB = cursorB.coerceIn(0, contentB.length)

        // Set distinct content and cursor positions on each session
        manager.updateContent(sessionA.id, editedContentA, clampedCursorA)
        manager.updateContent(sessionB.id, contentB, clampedCursorB)

        // Capture session A's state before switching
        val stateBeforeSwitch = SessionSnapshot(
            content = sessionA.content,
            cursorPosition = sessionA.cursorPosition,
            isDirty = sessionA.isDirty
        )

        // Switch to session B (A is no longer active)
        manager.switchTo(sessionB.id)

        // Switch back to session A
        manager.switchTo(sessionA.id)

        // Assert session A's state is preserved exactly
        assertEquals(
            stateBeforeSwitch.content,
            sessionA.content,
            "Session A content should be preserved after switching away and back"
        )
        assertEquals(
            stateBeforeSwitch.cursorPosition,
            sessionA.cursorPosition,
            "Session A cursor position should be preserved after switching away and back"
        )
        assertEquals(
            stateBeforeSwitch.isDirty,
            sessionA.isDirty,
            "Session A dirty state should be preserved after switching away and back"
        )
    }

    /**
     * Extended isolation test: switching through multiple sessions preserves all states.
     *
     * Validates: Requirements 6.2
     */
    @Property(tries = 100)
    @Tag("Feature: file-management, Property 8: Session state isolation on switch")
    fun multipleSessionSwitchesPreserveAllStates(
        @ForAll("smallSessionCount") sessionCount: Int,
        @ForAll("fileContents") baseContent: String
    ) {
        // Create N sessions with distinct content
        val sessions = (0 until sessionCount).map { i ->
            val session = manager.createUntitled()
            val content = "$baseContent-session-$i"
            val cursor = i.coerceIn(0, content.length)
            manager.updateContent(session.id, content, cursor)
            session
        }

        // Snapshot all session states
        val snapshots = sessions.map { session ->
            session.id to SessionSnapshot(
                content = session.content,
                cursorPosition = session.cursorPosition,
                isDirty = session.isDirty
            )
        }.toMap()

        // Switch through all sessions in reverse order
        for (i in sessions.indices.reversed()) {
            manager.switchTo(sessions[i].id)
        }

        // Switch back through all in original order
        for (session in sessions) {
            manager.switchTo(session.id)
        }

        // Verify all sessions preserved their state
        for (session in sessions) {
            val snapshot = snapshots[session.id]!!
            assertEquals(
                snapshot.content, session.content,
                "Session ${session.id} content should be preserved after multiple switches"
            )
            assertEquals(
                snapshot.cursorPosition, session.cursorPosition,
                "Session ${session.id} cursor should be preserved after multiple switches"
            )
            assertEquals(
                snapshot.isDirty, session.isDirty,
                "Session ${session.id} dirty state should be preserved after multiple switches"
            )
        }
    }

    // =========================================================================
    // Data Classes
    // =========================================================================

    private data class SessionSnapshot(
        val content: String,
        val cursorPosition: Int,
        val isDirty: Boolean
    )

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
    fun editedContents(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange(' ', '~')
            .ofMinLength(1)
            .ofMaxLength(200)
    }

    @Provide
    fun cursorPositions(): Arbitrary<Int> {
        return Arbitraries.integers().between(0, 200)
    }

    @Provide
    fun smallSessionCount(): Arbitrary<Int> {
        return Arbitraries.integers().between(2, 5)
    }
}
