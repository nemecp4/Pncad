package com.openscadviewer

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.IOException

/**
 * About screen.
 *
 * Renders the primary content bundled in `assets/about.txt` and offers buttons to
 * reveal the third-party licence list (`licence.txt`) and the privacy policy
 * (`privacy_policy.txt`). Selecting a document toggles its text inline on the same
 * page; tapping the active document again hides it.
 */
class AboutActivity : AppCompatActivity() {

    /**
     * A document that can be displayed on the About screen. Each entry maps to a
     * text file bundled in the app's assets folder.
     */
    private enum class AboutDocument(val assetFileName: String) {
        LICENCE("licence.txt"),
        PRIVACY_POLICY("privacy_policy.txt")
    }

    private lateinit var documentContent: TextView
    private var selectedDocument: AboutDocument? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.about_title)

        findViewById<TextView>(R.id.aboutContent).text = readAsset("about.txt")

        documentContent = findViewById(R.id.aboutDocumentContent)

        findViewById<MaterialButton>(R.id.btnShowLicence).setOnClickListener {
            toggleDocument(AboutDocument.LICENCE)
        }
        findViewById<MaterialButton>(R.id.btnShowPrivacyPolicy).setOnClickListener {
            toggleDocument(AboutDocument.PRIVACY_POLICY)
        }
    }

    /**
     * Shows [document] inline, or hides it if it is already the selected document.
     */
    private fun toggleDocument(document: AboutDocument) {
        selectedDocument = if (selectedDocument == document) null else document

        val current = selectedDocument
        if (current == null) {
            documentContent.visibility = View.GONE
            documentContent.text = ""
        } else {
            documentContent.text = readAsset(current.assetFileName)
            documentContent.visibility = View.VISIBLE
        }
    }

    /**
     * Reads a UTF-8 text file from the app's assets folder, returning a friendly
     * fallback message if the file cannot be read.
     */
    private fun readAsset(fileName: String): String {
        return try {
            assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            getString(R.string.about_content_unavailable)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
