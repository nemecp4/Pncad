package com.openscadviewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.openscadviewer.editor.SyntaxHighlighter
import com.openscadviewer.parser.OpenSCADParser
import com.openscadviewer.renderer.MeshGenerator
import com.openscadviewer.renderer.SceneRenderer
import com.openscadviewer.renderer.STLExporter
import com.openscadviewer.renderer.TouchHandler
import kotlinx.coroutines.*
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PICK_SCAD_FILE = 1001
        private const val SAVE_STL_FILE = 1002
    }

    private lateinit var codeEditor: EditText
    private lateinit var lineNumbers: TextView
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var tabLayout: TabLayout
    private lateinit var previewContainer: FrameLayout
    private lateinit var previewPlaceholder: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusBar: TextView

    private var glSurfaceView: GLSurfaceView? = null
    private var sceneRenderer: SceneRenderer? = null
    private var touchHandler: TouchHandler? = null
    private var syntaxHighlighter: SyntaxHighlighter? = null

    private val parser = OpenSCADParser()
    private val meshGenerator = MeshGenerator()
    private val stlExporter = STLExporter()

    private var currentMesh: MeshGenerator.Mesh? = null
    private var currentFileName: String = ""

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTabLayout()
        setupButtons()
        setupCodeEditor()

        // Check if opened with a .scad file intent
        handleIncomingIntent(intent)
    }

    private fun initViews() {
        codeEditor = findViewById(R.id.codeEditor)
        lineNumbers = findViewById(R.id.lineNumbers)
        viewFlipper = findViewById(R.id.viewFlipper)
        tabLayout = findViewById(R.id.tabLayout)
        previewContainer = findViewById(R.id.previewContainer)
        previewPlaceholder = findViewById(R.id.previewPlaceholder)
        progressBar = findViewById(R.id.progressBar)
        statusBar = findViewById(R.id.statusBar)
    }

    private fun setupTabLayout() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: 0
                viewFlipper.displayedChild = position
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btnOpenFile).setOnClickListener { openFilePicker() }
        findViewById<View>(R.id.btnPreview).setOnClickListener { generatePreview() }
        findViewById<View>(R.id.btnRender).setOnClickListener { renderAndExportSTL() }
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
                data.data?.let { uri -> loadScadFile(uri) }
            }
            SAVE_STL_FILE -> {
                data.data?.let { uri -> saveSTLToUri(uri) }
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

    // --- Preview ---

    private fun generatePreview() {
        val code = codeEditor.text?.toString()
        if (code.isNullOrBlank()) {
            showError("No code to preview")
            return
        }

        // Switch to preview tab
        tabLayout.getTabAt(1)?.select()

        progressBar.visibility = View.VISIBLE
        previewPlaceholder.visibility = View.GONE
        statusBar.text = "Generating preview..."

        coroutineScope.launch {
            try {
                val mesh = withContext(Dispatchers.Default) {
                    val scene = parser.parse(code)
                    meshGenerator.generate(scene)
                }

                currentMesh = mesh

                if (mesh.vertexCount == 0) {
                    showError("No geometry generated. Check your OpenSCAD code.")
                    progressBar.visibility = View.GONE
                    previewPlaceholder.visibility = View.VISIBLE
                    previewPlaceholder.text = "No geometry to display"
                    return@launch
                }

                setupGLView(mesh)
                progressBar.visibility = View.GONE
                statusBar.text = "Preview: ${mesh.triangleCount} triangles"

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                previewPlaceholder.visibility = View.VISIBLE
                previewPlaceholder.text = "Error: ${e.message}"
                showError("Parse error: ${e.message}")
            }
        }
    }

    private fun setupGLView(mesh: MeshGenerator.Mesh) {
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
        }

        previewPlaceholder.visibility = View.GONE
        sceneRenderer?.setMeshData(mesh.vertices, mesh.normals, mesh.colors)
        glSurfaceView?.requestRender()
    }

    // --- STL Export ---

    private fun renderAndExportSTL() {
        val code = codeEditor.text?.toString()
        if (code.isNullOrBlank()) {
            showError("No code to render")
            return
        }

        progressBar.visibility = View.VISIBLE
        statusBar.text = "Rendering STL..."

        coroutineScope.launch {
            try {
                val mesh = withContext(Dispatchers.Default) {
                    val scene = parser.parse(code)
                    meshGenerator.generate(scene)
                }

                currentMesh = mesh

                if (mesh.vertexCount == 0) {
                    showError("No geometry to export")
                    progressBar.visibility = View.GONE
                    return@launch
                }

                progressBar.visibility = View.GONE

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

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
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

    private fun showError(message: String) {
        statusBar.text = "Error: $message"
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        syntaxHighlighter?.detach()
        coroutineScope.cancel()
    }
}
