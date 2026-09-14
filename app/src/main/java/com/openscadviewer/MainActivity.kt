package com.openscadviewer

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.openscadviewer.console.ConsoleAdapter
import com.openscadviewer.console.ConsoleViewModel
import com.openscadviewer.console.LogSeverity
import com.openscadviewer.editor.BuiltinProvider
import com.openscadviewer.editor.CompletionEngine
import com.openscadviewer.editor.CompletionPopup
import com.openscadviewer.editor.CompletionTextWatcher
import com.openscadviewer.editor.DocumentScanner
import com.openscadviewer.editor.KeywordProvider
import com.openscadviewer.editor.MathProvider
import com.openscadviewer.editor.SyntaxHighlighter
import com.openscadviewer.file.CloseDialogChoice
import com.openscadviewer.file.CombinedFileMenuPopup
import com.openscadviewer.file.FileBarController
import com.openscadviewer.file.FileSession
import com.openscadviewer.file.FileTabsController
import com.openscadviewer.file.FileViewModel
import com.openscadviewer.engine.ComputeException
import com.openscadviewer.engine.EngineManager
import com.openscadviewer.engine.EngineType
import com.openscadviewer.engine.ErrorCategory
import com.openscadviewer.engine.MeshResult
import com.openscadviewer.parser.OpenSCADParser
import com.openscadviewer.renderer.SceneRenderer
import com.openscadviewer.renderer.STLExporter
import com.openscadviewer.renderer.TouchHandler
import com.openscadviewer.settings.PreferenceKeys
import com.openscadviewer.settings.SettingsActivity
import com.openscadviewer.settings.mapBackgroundColor
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PICK_SCAD_FILE = 1001
        private const val SAVE_STL_FILE = 1002
        private const val SAVE_AS_FILE = 1003

        // Toolbar tab indices (phone layout).
        private const val TAB_CODE = 0
        private const val TAB_PREVIEW = 1
        private const val TAB_CONSOLE = 2
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var codeEditor: EditText
    private lateinit var lineNumbers: TextView
    private var viewFlipper: ViewFlipper? = null
    private var tabLayout: TabLayout? = null
    private lateinit var previewContainer: FrameLayout
    private lateinit var previewPlaceholder: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusBar: TextView
    private lateinit var btnCancelCompute: MaterialButton

    private lateinit var viewModel: MainViewModel
    private lateinit var fileViewModel: FileViewModel
    private var fileBarController: FileBarController? = null
    private var fileTabsController: FileTabsController? = null
    private var isLoadingContent = false

    private val isTabletLayout: Boolean by lazy {
        findViewById<View>(R.id.paneDivider) != null
    }

    // Console views
    private lateinit var consoleContainer: LinearLayout
    private lateinit var consoleRecyclerView: RecyclerView
    private lateinit var scrollToBottomButton: ImageButton
    private lateinit var consoleCloseButton: ImageButton
    private lateinit var consoleCopyButton: ImageButton
    private lateinit var consoleCancelButton: MaterialButton

    private lateinit var consoleViewModel: ConsoleViewModel
    private lateinit var consoleAdapter: ConsoleAdapter

    private var glSurfaceView: GLSurfaceView? = null
    private var sceneRenderer: SceneRenderer? = null
    private var touchHandler: TouchHandler? = null
    private var syntaxHighlighter: SyntaxHighlighter? = null
    private lateinit var viewControlsOverlay: LinearLayout

    private val parser = OpenSCADParser()
    private lateinit var engineManager: EngineManager
    private val stlExporter = STLExporter()

    private var currentMesh: MeshResult? = null
    private var currentFileName: String = ""
    private var computeJob: Job? = null
    private var currentTabPosition: Int = 0
    private var isFallbackSinglePane = false

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            PreferenceKeys.KEY_SHOW_AXES -> {
                sceneRenderer?.showAxes = prefs.getBoolean(key, PreferenceKeys.DEFAULT_SHOW_AXES)
                glSurfaceView?.requestRender()
            }
            PreferenceKeys.KEY_SHOW_WIREFRAME -> {
                sceneRenderer?.showWireframe = prefs.getBoolean(key, PreferenceKeys.DEFAULT_SHOW_WIREFRAME)
                glSurfaceView?.requestRender()
            }
            PreferenceKeys.KEY_BACKGROUND_COLOR -> {
                sceneRenderer?.backgroundColorRgba = mapBackgroundColor(
                    prefs.getString(key, PreferenceKeys.DEFAULT_BACKGROUND_COLOR) ?: PreferenceKeys.DEFAULT_BACKGROUND_COLOR
                )
                glSurfaceView?.requestRender()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before super.onCreate so it's shown during
        // process/activity startup, then replaced by the app's normal theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        fileViewModel = ViewModelProvider(this)[FileViewModel::class.java]
        engineManager = EngineManager(this)

        initViews()
        applyNarrowPaneFallback()
        setupToolbar()
        setupTabLayout()
        setupButtons()
        setupFileManagement()
        setupCodeEditor()
        setupConsole()
        setupViewControls()

        // Restore file session on launch (handles intent URI or persisted URI)
        fileViewModel.restoreOnLaunch(intent?.data)
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        codeEditor = findViewById(R.id.codeEditor)
        lineNumbers = findViewById(R.id.lineNumbers)
        previewContainer = findViewById(R.id.previewContainer)
        previewPlaceholder = findViewById(R.id.previewPlaceholder)
        progressBar = findViewById(R.id.progressBar)
        statusBar = findViewById(R.id.statusBar)
        btnCancelCompute = findViewById(R.id.btnCancelCompute)

        // Only find TabLayout and ViewFlipper on phone layout
        if (!isTabletLayout) {
            viewFlipper = findViewById(R.id.viewFlipper)
            tabLayout = findViewById(R.id.tabLayout)
        }

        // Console views
        consoleContainer = findViewById(R.id.consoleContainer)
        consoleRecyclerView = findViewById(R.id.consoleRecyclerView)
        scrollToBottomButton = findViewById(R.id.scrollToBottomButton)
        consoleCloseButton = findViewById(R.id.consoleCloseButton)
        consoleCopyButton = findViewById(R.id.consoleCopyButton)
        consoleCancelButton = findViewById(R.id.consoleCancelButton)

        // View controls overlay — hidden until preview tab is active and mesh is loaded
        viewControlsOverlay = findViewById(R.id.viewControlsOverlay)
        viewControlsOverlay.visibility = View.GONE
    }

    /**
     * On tablet layouts, checks if the available width is too narrow for a usable split pane.
     * If each pane would be under 200dp, hides the preview pane and divider so the code
     * editor fills the screen (defensive fallback for multi-window on 600dp devices).
     */
    private fun applyNarrowPaneFallback() {
        if (!isTabletLayout) return

        val displayMetrics = resources.displayMetrics
        val availableWidthDp = displayMetrics.widthPixels / displayMetrics.density

        if (shouldFallbackToSinglePane(availableWidthDp, 2f)) {
            isFallbackSinglePane = true

            // Hide the preview pane and divider
            val paneDivider = findViewById<View>(R.id.paneDivider)
            paneDivider.visibility = View.GONE
            previewContainer.visibility = View.GONE

            // Make the code editor pane (the ScrollView sibling) fill the available space
            val codePaneParent = paneDivider.parent as? LinearLayout
            if (codePaneParent != null && codePaneParent.childCount > 0) {
                val codePane = codePaneParent.getChildAt(0)
                val params = codePane.layoutParams as? LinearLayout.LayoutParams
                if (params != null) {
                    params.weight = 1f
                    params.width = 0
                    codePane.layoutParams = params
                }
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        // File menu lives on the toolbar's navigation (top-left) icon.
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_agenda)
        toolbar.navigationContentDescription = getString(R.string.file_menu)
        toolbar.navigationIcon?.setTint(androidx.core.content.ContextCompat.getColor(this, R.color.white))
        toolbar.setNavigationOnClickListener { fileBarController?.toggle() }
    }

    private fun setupTabLayout() {
        val tabLayout = tabLayout ?: return
        val viewFlipper = viewFlipper ?: return

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: 0
                currentTabPosition = position

                // Tabs: 0 = Code, 1 = 3D Preview, 2 = Console.
                // The console is an overlay on the preview pane, so the Console tab
                // keeps the flipper on the preview child and reveals the overlay.
                viewFlipper.displayedChild = if (position == TAB_CODE) TAB_CODE else TAB_PREVIEW

                if (::consoleViewModel.isInitialized) {
                    when (position) {
                        TAB_CONSOLE -> consoleViewModel.show()
                        else -> consoleViewModel.hide()
                    }
                }

                updateViewControlsVisibility()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btnPreview).setOnClickListener { generatePreview() }
        findViewById<View>(R.id.btnRender).setOnClickListener { renderAndExportSTL() }
        btnCancelCompute.setOnClickListener { cancelComputation() }
    }

    /**
     * Toggle the console log. On phones the console is the third toolbar tab, so
     * this selects/deselects that tab; on tablets (no tabs) it toggles the overlay
     * directly. Invoked from the overflow menu.
     */
    private fun toggleConsole() {
        if (!::consoleViewModel.isInitialized) return
        val willShow = consoleViewModel.isVisible.value != true
        if (!isTabletLayout) {
            val tabs = tabLayout
            if (tabs != null) {
                // Selecting the tab drives console visibility via the tab listener.
                val target = if (willShow) TAB_CONSOLE else TAB_PREVIEW
                tabs.getTabAt(target)?.select()
                return
            }
        }
        consoleViewModel.toggleVisibility()
    }

    /**
     * Auto-dismisses the console shortly after a successful compute, unless the
     * user is actively viewing the Console tab (phone) where yanking it would be
     * jarring.
     */
    private fun autoHideConsoleAfterDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (isTabletLayout || currentTabPosition != TAB_CONSOLE) {
                consoleViewModel.hide()
            }
        }, 2000)
    }

    private fun setupFileManagement() {
        // The combined file menu is triggered from the toolbar's navigation icon
        // (see setupToolbar) and anchored to the toolbar.
        val combinedMenuPopup = CombinedFileMenuPopup(
            context = this,
            onNew = { fileViewModel.newFile() },
            onOpen = { fileViewModel.openFilePicker() },
            onSave = { fileViewModel.save() },
            onSaveAs = { fileViewModel.saveAs() },
            onClose = {
                val action = fileViewModel.closeWithConfirmation()
                if (action == FileViewModel.CloseAction.PROCEED) {
                    fileViewModel.closeActiveFile()
                }
            },
            onFileSelected = { sessionId -> fileViewModel.switchToFile(sessionId) }
        )

        fileBarController = FileBarController(
            anchor = toolbar,
            combinedMenuPopup = combinedMenuPopup,
            getSessionsData = {
                val sessions = fileViewModel.sessions.value ?: emptyList()
                val activeId = fileViewModel.activeSession.value?.id
                Pair(sessions, activeId)
            }
        )

        // File tabs strip (phone layout only — the strip views live in that layout)
        val fileTabsScroll = findViewById<HorizontalScrollView?>(R.id.fileTabsScroll)
        val fileTabsContainer = findViewById<LinearLayout?>(R.id.fileTabsContainer)
        if (fileTabsScroll != null && fileTabsContainer != null) {
            fileTabsController = FileTabsController(
                scrollView = fileTabsScroll,
                container = fileTabsContainer,
                onTabSelected = { sessionId -> fileViewModel.switchToFile(sessionId) },
                onTabClosed = { sessionId ->
                    val action = fileViewModel.requestCloseFile(sessionId)
                    if (action == FileViewModel.CloseAction.PROCEED) {
                        fileViewModel.closeActiveFile()
                    }
                }
            )
        }

        // Observe active session — update editor text and status bar
        fileViewModel.activeSession.observe(this) { session ->
            if (session != null) {
                codeEditor.isEnabled = true
                codeEditor.hint = getString(R.string.code_hint)
                val editorText = codeEditor.text?.toString() ?: ""
                if (editorText != session.content) {
                    isLoadingContent = true
                    codeEditor.setText(session.content)
                    val clampedCursor = minOf(session.cursorPosition, session.content.length)
                    codeEditor.setSelection(clampedCursor)
                    isLoadingContent = false
                }
                val prefix = if (session.isDirty) "*" else ""
                statusBar.text = "$prefix${session.displayName}"
            } else {
                // No active session — show empty/placeholder state
                isLoadingContent = true
                codeEditor.setText("")
                codeEditor.hint = "Open a file from the File menu"
                codeEditor.isEnabled = false
                isLoadingContent = false
                statusBar.text = getString(R.string.no_file_loaded)
            }
            // Keep the tabs strip in sync with the active file (highlight + dirty marker)
            fileTabsController?.render(
                fileViewModel.sessions.value ?: emptyList(),
                session?.id
            )
        }

        // Observe sessions — rebuild the file tabs strip. OpenFilesMenu still reads via getSessionsData lambda.
        fileViewModel.sessions.observe(this) { sessions ->
            fileTabsController?.render(sessions, fileViewModel.activeSession.value?.id)
        }

        // Observe status message
        fileViewModel.statusMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                statusBar.text = message
            }
        }

        // Observe error events — show Snackbar
        fileViewModel.errorEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Snackbar.make(
                    findViewById(android.R.id.content),
                    message,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        // Observe open picker event — launch SAF open document intent
        fileViewModel.openPickerEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, PICK_SCAD_FILE)
            }
        }

        // Observe save-as event — launch SAF create document intent with suggested filename
        fileViewModel.saveAsEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { suggestedName ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, suggestedName)
                }
                startActivityForResult(intent, SAVE_AS_FILE)
            }
        }

        // Observe close confirmation event — show dialog
        fileViewModel.closeConfirmEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                showCloseConfirmationDialog()
            }
        }

        // Debounced editor content change forwarding to FileViewModel
        val contentUpdateHandler = Handler(Looper.getMainLooper())
        var contentUpdateRunnable: Runnable? = null

        codeEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isLoadingContent) return
                contentUpdateRunnable?.let { contentUpdateHandler.removeCallbacks(it) }
                contentUpdateRunnable = Runnable {
                    val text = codeEditor.text?.toString() ?: ""
                    val cursor = codeEditor.selectionStart
                    fileViewModel.onEditorContentChanged(text, cursor)
                }
                contentUpdateHandler.postDelayed(contentUpdateRunnable!!, 300)
            }
        })
    }

    private fun showCloseConfirmationDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Unsaved Changes")
            .setMessage("This file has unsaved changes. What would you like to do?")
            .setPositiveButton("Save") { _, _ ->
                fileViewModel.confirmClose(CloseDialogChoice.SAVE)
            }
            .setNegativeButton("Discard") { _, _ ->
                fileViewModel.confirmClose(CloseDialogChoice.DISCARD)
            }
            .setNeutralButton("Cancel") { _, _ ->
                fileViewModel.confirmClose(CloseDialogChoice.CANCEL)
            }
            .setCancelable(false)
            .show()
    }

    private fun setupViewControls() {
        findViewById<ImageButton>(R.id.btnViewTop).setOnClickListener { snapCamera(90f, 0f) }
        findViewById<ImageButton>(R.id.btnViewFront).setOnClickListener { snapCamera(0f, 0f) }
        findViewById<ImageButton>(R.id.btnViewLeft).setOnClickListener { snapCamera(0f, 90f) }
        findViewById<ImageButton>(R.id.btnViewRight).setOnClickListener { snapCamera(0f, -90f) }
    }

    private fun snapCamera(rotX: Float, rotY: Float) {
        sceneRenderer?.let { renderer ->
            renderer.cameraRotX = rotX
            renderer.cameraRotY = rotY
            // preserve cameraDistance and cameraPanX/Y
            glSurfaceView?.requestRender()
        }
    }

    private fun updateViewControlsVisibility() {
        val isPreviewVisible = isTabletLayout || currentTabPosition == TAB_PREVIEW
        val hasMesh = currentMesh != null
        viewControlsOverlay.visibility = if (isPreviewVisible && hasMesh) View.VISIBLE else View.GONE
    }

    private fun setupCodeEditor() {
        syntaxHighlighter = SyntaxHighlighter(codeEditor)
        syntaxHighlighter?.attach()

        // Update line numbers when text changes
        codeEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateLineNumbers()
            }
        })

        // Code completion setup
        val documentScanner = DocumentScanner(lifecycleScope)
        val providers = listOf(
            documentScanner,
            KeywordProvider(),
            BuiltinProvider(),
            MathProvider()
        )
        val completionEngine = CompletionEngine(providers)

        lateinit var completionWatcher: CompletionTextWatcher
        val completionPopup = CompletionPopup(this, codeEditor) { item ->
            completionWatcher.insertCompletion(item)
        }
        completionWatcher = CompletionTextWatcher(codeEditor, completionEngine, completionPopup, documentScanner)
        codeEditor.addTextChangedListener(completionWatcher)

        // Load sample code
        val sampleCode = """// OpenSCAD Viewer - Sample
// Open a .scad file or type code below

difference() {
    cube([30, 30, 30], center=true);
    sphere(r=18);
}

translate([0, 0, 20]) {
    cylinder(h=10, r1=8, r2=4, center=true);
}
"""
        codeEditor.setText(sampleCode)
        updateLineNumbers()
    }

    private fun updateLineNumbers() {
        val text = codeEditor.text?.toString() ?: ""
        val lines = text.split("\n").size
        val numbers = (1..lines).joinToString("\n")
        lineNumbers.text = numbers
    }

    // --- Console Setup ---

    private fun setupConsole() {
        // Obtain ConsoleViewModel via ViewModelProvider
        consoleViewModel = ViewModelProvider(this)[ConsoleViewModel::class.java]

        // Initialize ConsoleAdapter and attach to RecyclerView
        consoleAdapter = ConsoleAdapter()
        consoleRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = consoleAdapter
        }

        // Observe logEntries LiveData to submit list to adapter
        consoleViewModel.logEntries.observe(this) { entries ->
            consoleAdapter.submitList(entries) {
                // After list is submitted, auto-scroll if enabled
                if (consoleViewModel.autoScroll.value == true && entries.isNotEmpty()) {
                    consoleRecyclerView.scrollToPosition(consoleAdapter.itemCount - 1)
                }
            }
        }

        // Observe isVisible LiveData to toggle consoleContainer visibility.
        // On phone the console overlay is shown only while the Console tab is
        // selected; on tablet both panes are visible so it shows whenever visible.
        consoleViewModel.isVisible.observe(this) { visible ->
            val shouldShow = visible && (isTabletLayout || currentTabPosition == TAB_CONSOLE)
            consoleContainer.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }

        // Observe autoScroll LiveData to auto-scroll RecyclerView to last position
        consoleViewModel.autoScroll.observe(this) { enabled ->
            if (enabled && consoleAdapter.itemCount > 0) {
                consoleRecyclerView.scrollToPosition(consoleAdapter.itemCount - 1)
            }
        }

        // Wire close button to hide console
        consoleCloseButton.setOnClickListener {
            consoleViewModel.hide()
        }

        // Wire scroll-to-bottom button to resume auto-scroll and scroll to end
        scrollToBottomButton.setOnClickListener {
            consoleViewModel.setAutoScroll(true)
            if (consoleAdapter.itemCount > 0) {
                consoleRecyclerView.scrollToPosition(consoleAdapter.itemCount - 1)
            }
        }

        // Wire copy button to copy all log entries to clipboard
        consoleCopyButton.setOnClickListener {
            val logText = consoleAdapter.getAllLogText()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Console Log", logText)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, "Log copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Detect manual scroll-up via OnScrollListener to pause auto-scroll
        consoleRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // If user scrolled up (dy < 0) and can still scroll down, pause auto-scroll
                if (dy < 0 && recyclerView.canScrollVertically(1)) {
                    consoleViewModel.setAutoScroll(false)
                }
            }
        })

        // Wire cancel button to cancelComputation()
        consoleCancelButton.setOnClickListener { cancelComputation() }
    }

    // --- Options Menu (Engine Selection) ---

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        updateEngineMenuState(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_engine_kotlin -> {
                engineManager.selectedType = EngineType.KOTLIN
                item.isChecked = true
                statusBar.text = "Engine: Simple (Kotlin)"
                true
            }
            R.id.menu_engine_cgal -> {
                if (!engineManager.isCgalAvailable()) {
                    handleCgalUnavailable()
                    return true
                }
                engineManager.selectedType = EngineType.CGAL
                item.isChecked = true
                statusBar.text = "Engine: Advanced (CGAL)"
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            R.id.menu_console -> {
                toggleConsole()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        updateEngineMenuState(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun updateEngineMenuState(menu: Menu?) {
        menu ?: return
        val kotlinItem = menu.findItem(R.id.menu_engine_kotlin)
        val cgalItem = menu.findItem(R.id.menu_engine_cgal)

        // Update checked state based on current selection
        when (engineManager.selectedType) {
            EngineType.KOTLIN -> kotlinItem?.isChecked = true
            EngineType.CGAL -> cgalItem?.isChecked = true
        }

        // Disable CGAL option if library is unavailable
        if (!engineManager.isCgalAvailable()) {
            cgalItem?.isEnabled = false
            cgalItem?.title = getString(R.string.menu_engine_cgal_unavailable)
        }
    }

    /**
     * Handle the case when CGAL native library is unavailable.
     * Disables the CGAL option, switches to Kotlin engine, persists the change,
     * and notifies the user via Snackbar.
     */
    private fun handleCgalUnavailable() {
        // Switch to Kotlin engine and persist
        engineManager.selectedType = EngineType.KOTLIN
        // Invalidate menu to update UI state
        invalidateOptionsMenu()
        // Notify user
        Snackbar.make(
            findViewById(android.R.id.content),
            "CGAL engine unavailable. Using Kotlin engine.",
            Snackbar.LENGTH_LONG
        ).show()
    }

    // --- File Operations ---

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, PICK_SCAD_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null) return

        when (requestCode) {
            PICK_SCAD_FILE -> {
                data.data?.let { uri -> fileViewModel.handleFileSelected(uri) }
            }
            SAVE_STL_FILE -> {
                data.data?.let { uri -> saveSTLToUri(uri) }
            }
            SAVE_AS_FILE -> {
                data.data?.let { uri -> fileViewModel.handleSaveAsDestination(uri) }
            }
        }
    }

    private fun loadScadFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                codeEditor.setText(content)

                // Extract filename
                currentFileName = uri.lastPathSegment?.substringAfterLast("/") ?: "file.scad"
                statusBar.text = "Loaded: $currentFileName"

                // Apply syntax highlighting
                syntaxHighlighter?.highlightSyntax()
            }
        } catch (e: Exception) {
            showError("Failed to open file: ${e.message}")
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null) {
            loadScadFile(uri)
        }
    }

    // --- Computation Controls ---

    private fun showComputeProgress() {
        progressBar.visibility = View.VISIBLE
        btnCancelCompute.visibility = View.VISIBLE
    }

    private fun hideComputeProgress() {
        progressBar.visibility = View.GONE
        btnCancelCompute.visibility = View.GONE
    }

    private fun cancelComputation() {
        engineManager.currentEngine.cancel()
        computeJob?.cancel()
        computeJob = null
        hideComputeProgress()
        consoleViewModel.logger.emit(LogSeverity.INFO, "Computation cancelled")
        consoleViewModel.endSession(false)
        statusBar.text = "Computation cancelled"
    }

    // --- Preview ---

    /**
     * Emits any syntax errors collected during the last [OpenSCADParser.parse] to the
     * console log, each tagged with its source line. Returns true if there were any.
     * Must be called after a parse and while a console session is active.
     */
    private fun emitParseErrors(): Boolean {
        val errors = parser.parseErrors
        if (errors.isEmpty()) return false
        for (err in errors) {
            consoleViewModel.logger.emit(
                LogSeverity.ERROR,
                "Line ${err.line}: ${err.message}"
            )
        }
        consoleViewModel.logger.emit(
            LogSeverity.WARN,
            "${errors.size} syntax problem(s) found — output may be incomplete."
        )
        // Surface the console so the user sees the errors. On phone that means
        // selecting the Console tab; on tablet the overlay shows directly.
        if (!isTabletLayout) {
            tabLayout?.getTabAt(TAB_CONSOLE)?.select()
        } else {
            consoleViewModel.show()
        }
        return true
    }

    private fun generatePreview() {
        val code = codeEditor.text?.toString()
        if (code.isNullOrBlank()) {
            showError("No code to preview")
            return
        }

        // Switch to preview tab (phone only; on tablet both panes are always visible)
        tabLayout?.getTabAt(1)?.select()

        showComputeProgress()
        previewPlaceholder.visibility = View.GONE
        statusBar.text = "Generating preview..."

        // Start console session
        consoleViewModel.startSession()
        val progressCallback = consoleViewModel.createProgressCallback()

        computeJob = coroutineScope.launch {
            try {
                val scene = withContext(Dispatchers.Default) {
                    parser.parse(code)
                }

                val hasSyntaxErrors = emitParseErrors()

                val result = engineManager.currentEngine.compute(scene, progressCallback)

                result.onSuccess { meshResult ->
                    if (meshResult.vertexCount == 0) {
                        showError("No geometry generated. Check your OpenSCAD code.")
                        hideComputeProgress()
                        consoleViewModel.endSession(false)
                        if (currentMesh == null) {
                            previewPlaceholder.visibility = View.VISIBLE
                            previewPlaceholder.text = "No geometry to display"
                        }
                        return@launch
                    }

                    currentMesh = meshResult
                    setupGLView(meshResult)
                    hideComputeProgress()
                    // A partial render can still hide syntax errors — keep the console
                    // open when any were reported so the user notices them.
                    consoleViewModel.endSession(!hasSyntaxErrors)
                    if (!hasSyntaxErrors) {
                        autoHideConsoleAfterDelay()
                    }
                    statusBar.text = "Preview: ${meshResult.triangleCount} triangles"
                }

                result.onFailure { error ->
                    hideComputeProgress()
                    consoleViewModel.endSession(false)
                    // Retain last valid geometry on error
                    if (currentMesh == null) {
                        previewPlaceholder.visibility = View.VISIBLE
                        previewPlaceholder.text = "Error: ${error.message}"
                    }
                    handleComputeError(error)
                }

            } catch (e: CancellationException) {
                // Cancelled by user — handled in cancelComputation()
            } catch (e: Exception) {
                hideComputeProgress()
                consoleViewModel.endSession(false)
                // Retain last valid geometry on error
                if (currentMesh == null) {
                    previewPlaceholder.visibility = View.VISIBLE
                    previewPlaceholder.text = "Error: ${e.message}"
                }
                showError("Parse error: ${e.message}")
            }
        }
    }

    private fun setupGLView(meshResult: MeshResult) {
        if (glSurfaceView == null) {
            glSurfaceView = GLSurfaceView(this).apply {
                setEGLContextClientVersion(2)
                sceneRenderer = SceneRenderer()
                setRenderer(sceneRenderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

                touchHandler = TouchHandler(this@MainActivity, this, sceneRenderer!!)
                setOnTouchListener { _, event ->
                    touchHandler?.onTouchEvent(event)
                    true
                }
            }
            previewContainer.addView(glSurfaceView, 0)
            // Apply stored preferences to the newly created renderer before first frame
            applyStoredPreferences()
        }

        previewPlaceholder.visibility = View.GONE
        sceneRenderer?.setMeshData(meshResult.vertices, meshResult.normals, meshResult.colors)
        glSurfaceView?.requestRender()
        updateViewControlsVisibility()
    }

    // --- STL Export ---

    private fun renderAndExportSTL() {
        val code = codeEditor.text?.toString()
        if (code.isNullOrBlank()) {
            showError("No code to render")
            return
        }

        showComputeProgress()
        statusBar.text = "Rendering STL..."

        // Start console session
        consoleViewModel.startSession()
        val progressCallback = consoleViewModel.createProgressCallback()

        computeJob = coroutineScope.launch {
            try {
                val scene = withContext(Dispatchers.Default) {
                    parser.parse(code)
                }

                val hasSyntaxErrors = emitParseErrors()

                val result = engineManager.currentEngine.compute(scene, progressCallback)

                result.onSuccess { meshResult ->
                    if (meshResult.vertexCount == 0) {
                        showError("No geometry to export")
                        hideComputeProgress()
                        consoleViewModel.endSession(false)
                        return@launch
                    }

                    currentMesh = meshResult
                    hideComputeProgress()
                    consoleViewModel.endSession(!hasSyntaxErrors)
                    if (!hasSyntaxErrors) {
                        autoHideConsoleAfterDelay()
                    }

                    // Ask user where to save
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/sla"
                        val baseName = if (currentFileName.isNotEmpty())
                            currentFileName.removeSuffix(".scad")
                        else "model"
                        putExtra(Intent.EXTRA_TITLE, "$baseName.stl")
                    }
                    startActivityForResult(intent, SAVE_STL_FILE)
                }

                result.onFailure { error ->
                    hideComputeProgress()
                    consoleViewModel.endSession(false)
                    handleComputeError(error)
                }

            } catch (e: CancellationException) {
                // Cancelled by user — handled in cancelComputation()
            } catch (e: Exception) {
                hideComputeProgress()
                consoleViewModel.endSession(false)
                showError("Render error: ${e.message}")
            }
        }
    }

    private fun saveSTLToUri(uri: Uri) {
        val mesh = currentMesh
        if (mesh == null) {
            showError("No mesh data available")
            return
        }

        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                stlExporter.exportBinary(mesh, outputStream)
                statusBar.text = "STL exported: ${mesh.triangleCount} triangles"
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "STL file saved successfully!",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            showError("Failed to save STL: ${e.message}")
        }
    }

    // --- Utilities ---

    /**
     * Handle a compute error by category:
     * - Shows user-friendly error messages via Snackbar
     * - On library load failure (COMPUTATION_FAILURE with unavailable library): falls back to Kotlin
     * - On timeout: shows timeout-specific message
     * - On cancellation: does nothing (user-initiated)
     * - Retains last valid geometry in all cases
     */
    private fun handleComputeError(error: Throwable) {
        if (error is ComputeException) {
            when (error.error.category) {
                ErrorCategory.TIMEOUT -> {
                    showError("Computation timed out (60s). Try a simpler model.")
                }
                ErrorCategory.OUT_OF_MEMORY -> {
                    showError("Model too complex. Try simplifying the geometry.")
                }
                ErrorCategory.INVALID_INPUT -> {
                    showError("Invalid model input")
                }
                ErrorCategory.CANCELLED -> {
                    // User-initiated cancellation — no error shown
                    statusBar.text = "Computation cancelled"
                }
                ErrorCategory.COMPUTATION_FAILURE -> {
                    // Check if this is a library load failure
                    if (!engineManager.isCgalAvailable() && engineManager.selectedType == EngineType.CGAL) {
                        handleCgalUnavailable()
                    } else {
                        showError("Computation failed: ${error.error.message}")
                    }
                }
            }
        } else {
            showError("Compute error: ${error.message}")
        }
    }

    private fun showError(message: String) {
        statusBar.text = "Error: $message"
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    // --- State Save/Restore ---

    /**
     * Pushes all transient UI state into MainViewModel so it survives configuration changes.
     * Called from onPause().
     */
    private fun saveStateToViewModel() {
        viewModel.editorText = codeEditor.text?.toString() ?: ""
        viewModel.cursorPosition = codeEditor.selectionStart
        viewModel.currentFileName = currentFileName
        viewModel.statusBarText = statusBar.text?.toString() ?: ""
        viewModel.meshVertices = currentMesh?.vertices
        viewModel.meshNormals = currentMesh?.normals
        viewModel.meshColors = currentMesh?.colors
        viewModel.triangleCount = currentMesh?.triangleCount ?: 0

        // Camera state
        sceneRenderer?.let { renderer ->
            viewModel.cameraRotX = renderer.cameraRotX
            viewModel.cameraRotY = renderer.cameraRotY
            viewModel.cameraDistance = renderer.cameraDistance
            viewModel.cameraPanX = renderer.cameraPanX
            viewModel.cameraPanY = renderer.cameraPanY
        }

        // Computation state
        viewModel.isComputing = computeJob?.isActive == true
    }

    /**
     * Restores transient UI state from MainViewModel after a configuration change.
     * Called at the end of onCreate() after all views and listeners are set up.
     * Only restores if non-default state exists (editorText is not empty).
     */
    private fun restoreStateFromViewModel() {
        if (viewModel.editorText.isEmpty()) return

        // Restore editor text and cursor position
        codeEditor.setText(viewModel.editorText)
        val clampedCursor = minOf(viewModel.cursorPosition, viewModel.editorText.length)
        codeEditor.setSelection(clampedCursor)

        // Restore file name and status bar
        currentFileName = viewModel.currentFileName
        statusBar.text = viewModel.statusBarText

        // Restore mesh and GL view if mesh data exists
        val vertices = viewModel.meshVertices
        val normals = viewModel.meshNormals
        val colors = viewModel.meshColors
        if (vertices != null && normals != null && colors != null) {
            val meshResult = MeshResult(vertices, normals, colors)
            currentMesh = meshResult
            setupGLView(meshResult)

            // Restore camera state on the renderer
            sceneRenderer?.let { renderer ->
                renderer.cameraRotX = viewModel.cameraRotX
                renderer.cameraRotY = viewModel.cameraRotY
                renderer.cameraDistance = viewModel.cameraDistance
                renderer.cameraPanX = viewModel.cameraPanX
                renderer.cameraPanY = viewModel.cameraPanY
            }
            glSurfaceView?.requestRender()
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(prefListener)
        applyStoredPreferences()
    }

    override fun onPause() {
        super.onPause()
        // Cancel active computation before saving state so that
        // saveStateToViewModel() stores isComputing=false and the cancelled status.
        if (computeJob?.isActive == true) {
            engineManager.currentEngine.cancel()
            computeJob?.cancel()
            computeJob = null
            statusBar.text = "Computation cancelled"
        }
        saveStateToViewModel()
        glSurfaceView?.onPause()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Defensive: if computation is still active (shouldn't be after onPause),
        // cancel it and update ViewModel so the recreated Activity sees correct state.
        if (computeJob?.isActive == true) {
            engineManager.currentEngine.cancel()
            computeJob?.cancel()
            computeJob = null
            viewModel.isComputing = false
            viewModel.statusBarText = "Computation cancelled"
        }
        syntaxHighlighter?.detach()
        coroutineScope.cancel()
    }

    /**
     * Reads all stored display preferences and applies them to the SceneRenderer.
     * Called on startup and when resuming to ensure settings persist across restarts.
     */
    private fun applyStoredPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        sceneRenderer?.let { renderer ->
            renderer.showAxes = prefs.getBoolean(
                PreferenceKeys.KEY_SHOW_AXES, PreferenceKeys.DEFAULT_SHOW_AXES
            )
            renderer.showWireframe = prefs.getBoolean(
                PreferenceKeys.KEY_SHOW_WIREFRAME, PreferenceKeys.DEFAULT_SHOW_WIREFRAME
            )
            renderer.backgroundColorRgba = mapBackgroundColor(
                prefs.getString(PreferenceKeys.KEY_BACKGROUND_COLOR, PreferenceKeys.DEFAULT_BACKGROUND_COLOR)
                    ?: PreferenceKeys.DEFAULT_BACKGROUND_COLOR
            )
            glSurfaceView?.requestRender()
        }
    }
}
