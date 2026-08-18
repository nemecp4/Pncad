package com.openscadviewer.file

import android.content.SharedPreferences
import android.net.Uri

open class FileSessionManager(
    private val prefs: SharedPreferences
) {
    private val sessions: MutableList<FileSession> = mutableListOf()
    val maxSessions = 20

    companion object {
        private const val PREF_KEY_LAST_ACTIVE_URI = "last_active_file_uri"
        // Sentinel value used for lastSavedContent of untitled files to ensure isDirty == true.
        // This is a value that real file content will never equal.
        internal const val UNTITLED_SENTINEL = "\u0000__NEVER_SAVED__"
    }

    /**
     * Returns the list of open sessions ordered by most-recently-accessed first.
     */
    fun getOrderedSessions(): List<FileSession> = sessions.toList()

    /**
     * Returns the active session (first in the MRU-ordered list), or null if no sessions exist.
     */
    fun getActiveSession(): FileSession? = sessions.firstOrNull()

    /**
     * Opens a file as a new session, or reuses an existing session if the URI matches.
     * Enforces the max 20 session cap.
     * The opened/reused session is moved to the MRU position (index 0).
     *
     * Returns Result.success with the session, or Result.failure if max capacity is reached.
     */
    fun openFile(uri: Uri, displayName: String, content: String): Result<FileSession> {
        // Check if a session with this URI already exists
        val existing = findByUri(uri)
        if (existing != null) {
            // Reuse existing session — move to MRU position
            sessions.remove(existing)
            existing.lastAccessedTimestamp = System.currentTimeMillis()
            sessions.add(0, existing)
            return Result.success(existing)
        }

        // Enforce max sessions cap
        if (sessions.size >= maxSessions) {
            return Result.failure(IllegalStateException("Maximum $maxSessions files open"))
        }

        // Create new session
        val session = FileSession(
            uri = uri,
            displayName = displayName,
            content = content,
            lastSavedContent = content,
            cursorPosition = 0,
            lastAccessedTimestamp = System.currentTimeMillis()
        )
        sessions.add(0, session)
        return Result.success(session)
    }

    /**
     * Switches to the session with the given ID.
     * Updates the timestamp and moves it to MRU position (index 0).
     * Returns the session if found, null otherwise.
     */
    fun switchTo(sessionId: String): FileSession? {
        val session = sessions.find { it.id == sessionId } ?: return null
        sessions.remove(session)
        session.lastAccessedTimestamp = System.currentTimeMillis()
        sessions.add(0, session)
        return session
    }

    /**
     * Updates the content and cursor position of a session.
     */
    fun updateContent(sessionId: String, content: String, cursorPosition: Int) {
        val session = sessions.find { it.id == sessionId } ?: return
        session.content = content
        session.cursorPosition = cursorPosition
    }

    /**
     * Marks a session as saved by updating lastSavedContent to match current content.
     * Optionally updates the URI and display name (for Save As operations).
     */
    fun markSaved(sessionId: String, newUri: Uri? = null, newDisplayName: String? = null) {
        val session = sessions.find { it.id == sessionId } ?: return
        session.lastSavedContent = session.content
        if (newUri != null) {
            session.uri = newUri
        }
        if (newDisplayName != null) {
            session.displayName = newDisplayName
        }
    }

    /**
     * Closes (removes) the session with the given ID.
     * Returns the new active session (the next MRU), or null if no sessions remain.
     */
    fun closeSession(sessionId: String): FileSession? {
        val session = sessions.find { it.id == sessionId } ?: return getActiveSession()
        sessions.remove(session)
        return sessions.firstOrNull()
    }

    /**
     * Finds a session by its URI. Returns null if no matching session is found.
     * Only matches sessions that have a non-null URI.
     */
    fun findByUri(uri: Uri): FileSession? {
        return sessions.find { it.uri != null && it.uri == uri }
    }

    /**
     * Creates a new untitled session with empty content.
     * The session has no URI, starts dirty (content != lastSavedContent),
     * and is placed at MRU position.
     *
     * Per requirement 8.6: a new file that has not been saved to disk starts dirty immediately.
     * We achieve this by setting lastSavedContent to a sentinel value that differs from the
     * empty initial content.
     */
    fun createUntitled(): FileSession {
        val session = FileSession(
            uri = null,
            displayName = "untitled.scad",
            content = "",
            lastSavedContent = UNTITLED_SENTINEL,
            cursorPosition = 0,
            lastAccessedTimestamp = System.currentTimeMillis()
        )
        sessions.add(0, session)
        return session
    }

    /**
     * Persists the active session's URI to SharedPreferences.
     * If no active session or active session has no URI, clears the persisted value.
     */
    fun persistActiveUri() {
        val activeUri = getActiveSession()?.uri
        if (activeUri != null) {
            prefs.edit().putString(PREF_KEY_LAST_ACTIVE_URI, activeUri.toString()).apply()
        } else {
            clearPersistedUri()
        }
    }

    /**
     * Retrieves the persisted last-active file URI from SharedPreferences.
     * Returns null if no URI is persisted or if the stored value is invalid.
     */
    open fun getPersistedUri(): Uri? {
        val uriString = prefs.getString(PREF_KEY_LAST_ACTIVE_URI, null) ?: return null
        return try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            clearPersistedUri()
            null
        }
    }

    /**
     * Clears the persisted URI from SharedPreferences.
     */
    fun clearPersistedUri() {
        prefs.edit().remove(PREF_KEY_LAST_ACTIVE_URI).apply()
    }
}
