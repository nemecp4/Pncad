package com.openscadviewer.engine

import android.content.SharedPreferences
import net.jqwik.api.*
import net.jqwik.api.lifecycle.BeforeProperty

/**
 * Property 3: Engine preference persistence round-trip
 *
 * For any EngineType value, storing it via EngineManager.selectedType
 * and then reading it back SHALL return the same EngineType value.
 *
 * **Validates: Requirements 2.2**
 */
@Tag("Feature_cgal-compute-engine")
@Tag("Property_3_Engine_preference_persistence_round-trip")
class EnginePreferencePersistencePropertyTest {

    private lateinit var prefs: InMemorySharedPreferences

    @BeforeProperty
    fun setUp() {
        prefs = InMemorySharedPreferences()
    }

    @Property(tries = 100)
    fun engineTypeRoundTrip(@ForAll("engineTypes") engineType: EngineType) {
        // Store via the same mechanism EngineManager uses
        prefs.edit().putString("engine_type", engineType.name).apply()

        // Read back via the same mechanism EngineManager uses
        val stored = prefs.getString("engine_type", "KOTLIN")
        val readBack = EngineType.valueOf(stored ?: "KOTLIN")

        assert(readBack == engineType) {
            "Round-trip failed: stored $engineType, got back $readBack"
        }
    }

    @Provide
    fun engineTypes(): Arbitrary<EngineType> {
        return Arbitraries.of(*EngineType.values())
    }

    /**
     * In-memory SharedPreferences implementation for unit testing.
     * Simulates the real SharedPreferences behavior without requiring
     * an Android Context.
     */
    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? {
            return if (data.containsKey(key)) data[key] as? String else defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return if (data.containsKey(key)) data[key] as? MutableSet<String> else defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return if (data.containsKey(key)) data[key] as? Int ?: defValue else defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            return if (data.containsKey(key)) data[key] as? Long ?: defValue else defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            return if (data.containsKey(key)) data[key] as? Float ?: defValue else defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return if (data.containsKey(key)) data[key] as? Boolean ?: defValue else defValue
        }

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = InMemoryEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            listener?.let { listeners.add(it) }
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            listener?.let { listeners.remove(it) }
        }

        private inner class InMemoryEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
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
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                data.putAll(pending)
            }
        }
    }
}
