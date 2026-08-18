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

    // --- Operations ---

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
                // Can't open persisted file — clear and show empty editor
                sessionManager.clearPersistedUri()
                _activeSession.value = null
                _sessions.value = emptyList()
            }
            return
        }

        // No intent URI, no persisted URI — empty editor
        _activeSession.value = null
        _sessions.value = emptyList()
    }

    // --- Private helper ---

    private fun refreshLiveData() {
        _activeSession.value = sessionManager.getActiveSession()
        _sessions.value = sessionManager.getOrderedSessions()
    }
}
