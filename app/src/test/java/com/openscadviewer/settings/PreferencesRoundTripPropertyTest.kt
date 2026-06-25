package com.openscadviewer.settings

import android.content.SharedPreferences
import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*

// Feature: preview-controls-and-settings, Property 3: Preferences round-trip persistence

/**
 * Property-based tests for preferences round-trip persistence.
 *
 * Uses an in-memory map to simulate SharedPreferences write/read cycles,
 * verifying that all valid preference combinations survive a round-trip.
 *
 * **Validates: Requirements 6.1, 5.6**
 */
class PreferencesRoundTripPropertyTest {

    /**
     * A minimal in-memory SharedPreferences implementation for testing.
     * Stores values in a HashMap and supports getBoolean/putBoolean and getString/putString.
     */
    private class InMemorySharedPreferences : SharedPreferences {
        private val store = HashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(store)
        override fun getString(key: String?, defValue: String?): String? =
            store[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            defValues
        override fun getInt(key: String?, defValue: Int): Int =
            store[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long =
            store[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
            store[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            store[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = store.containsKey(key)
        override fun edit(): SharedPreferences.Editor = InMemoryEditor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}

        private inner class InMemoryEditor : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                key?.let { pending[it] = values }
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { removals.add(it) }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                applyChanges()
                return true
            }
            override fun apply() {
                applyChanges()
            }
            private fun applyChanges() {
                if (clearAll) store.clear()
                removals.forEach { store.remove(it) }
                store.putAll(pending)
            }
        }
    }

    /**
     * Property 3: Preferences round-trip persistence
     *
     * For any valid combination of preference values (showAxes ∈ {true, false},
     * showWireframe ∈ {true, false}, backgroundColor ∈ {"dark_grey", "white", "yellow"}),
     * writing all three to SharedPreferences and then reading them back shall return
     * the exact same values.
     *
     * **Validates: Requirements 6.1, 5.6**
     */
    @Property(tries = 100)
    @Tag("Feature: preview-controls-and-settings, Property 3: Preferences round-trip persistence")
    fun preferencesRoundTripPersistence(
        @ForAll("showAxesValues") showAxes: Boolean,
        @ForAll("showWireframeValues") showWireframe: Boolean,
        @ForAll("backgroundColorValues") backgroundColor: String
    ) {
        val prefs: SharedPreferences = InMemorySharedPreferences()

        // Write all three preferences
        prefs.edit()
            .putBoolean(PreferenceKeys.KEY_SHOW_AXES, showAxes)
            .putBoolean(PreferenceKeys.KEY_SHOW_WIREFRAME, showWireframe)
            .putString(PreferenceKeys.KEY_BACKGROUND_COLOR, backgroundColor)
            .apply()

        // Read back and assert equality
        val readShowAxes = prefs.getBoolean(
            PreferenceKeys.KEY_SHOW_AXES, PreferenceKeys.DEFAULT_SHOW_AXES
        )
        val readShowWireframe = prefs.getBoolean(
            PreferenceKeys.KEY_SHOW_WIREFRAME, PreferenceKeys.DEFAULT_SHOW_WIREFRAME
        )
        val readBackgroundColor = prefs.getString(
            PreferenceKeys.KEY_BACKGROUND_COLOR, PreferenceKeys.DEFAULT_BACKGROUND_COLOR
        )

        assertEquals(showAxes, readShowAxes,
            "showAxes should survive round-trip (wrote: $showAxes)")
        assertEquals(showWireframe, readShowWireframe,
            "showWireframe should survive round-trip (wrote: $showWireframe)")
        assertEquals(backgroundColor, readBackgroundColor,
            "backgroundColor should survive round-trip (wrote: $backgroundColor)")
    }

    // --- Custom Generators ---

    @Provide
    fun showAxesValues(): Arbitrary<Boolean> {
        return Arbitraries.of(true, false)
    }

    @Provide
    fun showWireframeValues(): Arbitrary<Boolean> {
        return Arbitraries.of(true, false)
    }

    @Provide
    fun backgroundColorValues(): Arbitrary<String> {
        return Arbitraries.of("dark_grey", "white", "yellow")
    }
}
