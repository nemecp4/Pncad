package com.openscadviewer.file

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager

/**
 * ViewModel managing file session state and operations.
 * Exposes LiveData for the UI to observe active session, session list,
 * status messages, errors, and navigation events.
 */
class FileViewModel @JvmOverloads constructor(
    application: Application,
    internal val contentResolverCompat: ContentResolverCompat = DefaultContentResolverCompat(application.contentResolver),
    private val sessionManager: FileSessionManager = FileSessionManager(
        PreferenceManager.getDefaultSharedPreferences(application)
    )
) : AndroidViewModel(application) {

    // --- Exposed state as LiveData ---

    private val _activeSession = MutableLiveData<FileSession?>(null)
    val activeSession: LiveData<FileSession?> = _activeSession

    private val _sessions = MutableLiveData<List<FileSession>>(emptyList())
    val sessions: LiveData<List<FileSession>> = _sessions

    private val _statusMessage = MutableLiveData<String>("")
    val statusMessage: LiveData<String> = _statusMessage

    private val _errorEvent = MutableLiveData<Event<String>>()
    val errorEvent: LiveData<Event<String>> = _errorEvent

    // --- Navigation events ---

    private val _openPickerEvent = MutableLiveData<Event<Unit>>()
    val openPickerEvent: LiveData<Event<Unit>> = _openPickerEvent

    private val _saveAsEvent = MutableLiveData<Event<String>>()
    val saveAsEvent: LiveData<Event<String>> = _saveAsEvent

    private val _closeConfirmEvent = MutableLiveData<Event<Unit>>()
    val closeConfirmEvent: LiveData<Event<Unit>> = _closeConfirmEvent

    // --- Close flow result ---

    enum class CloseAction {
        PROCEED,
        NEEDS_CONFIRMATION
    }

    // --- Concurrency guard for save ---
    private var isSaving = false

    companion object {
        /**
         * Placeholder template inserted into a newly created file so the editor
         * is not blank. Users replace this with their own OpenSCAD code.
         */
        const val NEW_FILE_TEMPLATE: String =
            "// New OpenSCAD file\n" +
            "// Replace this placeholder with your own code.\n\n" +
            "cube([10, 10, 10], center = true);\n"
    }

    // --- Operations ---

    /**
     * Creates a new in-memory file seeded with a placeholder template and makes it active.
     * The file has no URI and starts dirty until saved.
     * Respects the max-open-files cap; emits an error event when the cap is reached.
     */
    fun newFile() {
        if (sessionManager.getOrderedSessions().size >= sessionManager.maxSessions) {
            _errorEvent.value = Event("Maximum ${sessionManager.maxSessions} files open")
            return
        }
        sessionManager.createUntitled(NEW_FILE_TEMPLATE)
        refreshLiveData()
        // An untitled file has no URI, so this clears any persisted last-active URI.
        sessionManager.persistActiveUri()
    }

    fun openFilePicker() {
        _openPickerEvent.value = Event(Unit)
    }

    fun handleFileSelected(uri: Uri) {
        // Take persistable URI permission (best-effort, uses real content resolver)
        try {
            getApplication<Application>().contentResolver?.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers don't support persistable permissions; continue anyway
        }

        // Check if this URI is already open — just switch to it
        val existing = sessionManager.findByUri(uri)
        if (existing != null) {
            sessionManager.switchTo(existing.id)
            refreshLiveData()
            sessionManager.persistActiveUri()
            return
        }

        // Read file content
        val result = FileContentReader.readContent(contentResolverCompat, uri)
        if (result.isFailure) {
            _errorEvent.value = Event("Could not open file: ${result.exceptionOrNull()?.message ?: "unknown error"}")
            return
        }

        val content = result.getOrThrow()
        val displayName = FileContentReader.getDisplayName(contentResolverCompat, uri)

        // Open as new session
        val openResult = sessionManager.openFile(uri, displayName, content)
        if (openResult.isFailure) {
            _errorEvent.value = Event("Maximum ${sessionManager.maxSessions} files open")
            return
        }

        refreshLiveData()
        sessionManager.persistActiveUri()
    }

    fun save() {
        if (isSaving) return

        val active = sessionManager.getActiveSession() ?: return

        // If no URI, trigger Save As flow
        if (active.uri == null) {
            saveAs()
            return
        }

        isSaving = true
        val writeResult = FileContentReader.writeContent(contentResolverCompat, active.uri!!, active.content)

        if (writeResult.isSuccess) {
            sessionManager.markSaved(active.id)
            _statusMessage.value = "Saved ${active.displayName}"
            refreshLiveData()
        } else {
            _errorEvent.value = Event("Save failed: ${writeResult.exceptionOrNull()?.message ?: "unknown error"}")
        }

        isSaving = false
    }

    fun saveAs() {
        val active = sessionManager.getActiveSession() ?: return
        _saveAsEvent.value = Event(active.displayName)
    }

    fun handleSaveAsDestination(uri: Uri) {
        val active = sessionManager.getActiveSession() ?: return

        // Take persistable URI permission
        try {
            getApplication<Application>().contentResolver?.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers don't support persistable permissions; continue anyway
        }

        // Write content to new URI
        val writeResult = FileContentReader.writeContent(contentResolverCompat, uri, active.content)
        if (writeResult.isFailure) {
            _errorEvent.value = Event("Save As failed: ${writeResult.exceptionOrNull()?.message ?: "unknown error"}")
            return
        }

        // Update session with new URI and display name
        val displayName = FileContentReader.getDisplayName(contentResolverCompat, uri)
        sessionManager.markSaved(active.id, newUri = uri, newDisplayName = displayName)
        _statusMessage.value = "Saved ${displayName}"
        refreshLiveData()
        sessionManager.persistActiveUri()
    }

    fun closeActiveFile() {
        val active = sessionManager.getActiveSession() ?: return
        val newActive = sessionManager.closeSession(active.id)

        if (newActive == null) {
            // No sessions remaining — emit null active session
            _activeSession.value = null
            _sessions.value = emptyList()
            _statusMessage.value = ""
        } else {
            refreshLiveData()
        }
        sessionManager.persistActiveUri()
    }

    fun closeWithConfirmation(): CloseAction {
        val active = sessionManager.getActiveSession() ?: return CloseAction.PROCEED
        return if (active.isDirty) {
            _closeConfirmEvent.value = Event(Unit)
            CloseAction.NEEDS_CONFIRMATION
        } else {
            CloseAction.PROCEED
        }
    }

    fun confirmClose(action: CloseDialogChoice) {
        when (action) {
            CloseDialogChoice.SAVE -> {
                // Save first, then close on success
                val active = sessionManager.getActiveSession() ?: return
                if (active.uri == null) {
                    // Need Save As first — for now trigger saveAs; close will happen after save completes
                    saveAs()
                    return
                }
                val writeResult = FileContentReader.writeContent(contentResolverCompat, active.uri!!, active.content)
                if (writeResult.isSuccess) {
                    sessionManager.markSaved(active.id)
                    closeActiveFile()
                } else {
                    _errorEvent.value = Event("Save failed: ${writeResult.exceptionOrNull()?.message ?: "unknown error"}")
                }
            }
            CloseDialogChoice.DISCARD -> {
                closeActiveFile()
            }
            CloseDialogChoice.CANCEL -> {
                // No-op
            }
        }
    }

    fun switchToFile(sessionId: String) {
        sessionManager.switchTo(sessionId) ?: return
        refreshLiveData()
        sessionManager.persistActiveUri()
    }

    /**
     * Requests closing a specific file (e.g. from a tab's close button).
     * The target is made active first so the shared close-confirmation flow
     * (which operates on the active session) applies to it. Returns the close
     * action so the caller can proceed with [closeActiveFile] when no
     * confirmation is required.
     */
    fun requestCloseFile(sessionId: String): CloseAction {
        sessionManager.switchTo(sessionId) ?: return CloseAction.PROCEED
        refreshLiveData()
        sessionManager.persistActiveUri()
        return closeWithConfirmation()
    }

    fun onEditorContentChanged(content: String, cursorPosition: Int) {
        val active = sessionManager.getActiveSession() ?: return
        sessionManager.updateContent(active.id, content, cursorPosition)
        refreshLiveData()
    }

    fun restoreOnLaunch(intentUri: Uri?) {
        if (intentUri != null) {
            handleFileSelected(intentUri)
            return
        }

        val persistedUri = sessionManager.getPersistedUri()
        if (persistedUri != null) {
            // Try to take persistable permission (may already have it)
            try {
                getApplication<Application>().contentResolver?.takePersistableUriPermission(
                    persistedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Permission might not be available
            }

            val result = FileContentReader.readContent(contentResolverCompat, persistedUri)
            if (result.isSuccess) {
                val content = result.getOrThrow()
                val displayName = FileContentReader.getDisplayName(contentResolverCompat, persistedUri)
                sessionManager.openFile(persistedUri, displayName, content)
                refreshLiveData()
                sessionManager.persistActiveUri()
            } else {
                // Can't open persisted file — clear and start with a fresh new file
                sessionManager.clearPersistedUri()
                newFile()
            }
            return
        }

        // No intent URI, no persisted URI — start with a fresh new file
        newFile()
    }

    // --- Private helper ---

    private fun refreshLiveData() {
        _activeSession.value = sessionManager.getActiveSession()
        _sessions.value = sessionManager.getOrderedSessions()
    }
}
