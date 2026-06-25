package com.openscadviewer.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.openscadviewer.R

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
