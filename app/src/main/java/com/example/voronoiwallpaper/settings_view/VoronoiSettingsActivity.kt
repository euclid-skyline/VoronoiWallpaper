package com.example.voronoiwallpaper.settings_view

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.example.voronoiwallpaper.R


class VoronoiSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        // Setup Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_activity_voronoi_settings)
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        // Handle back button press in ActionBar
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            this.finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

            // Load the preferences from an XML resource
            setPreferencesFromResource(R.xml.voronoi_preferences, rootKey)

            // Set up number input validation for num_points
            val numPointsPref = findPreference<EditTextPreference>(getString(R.string.pref_key_num_points))
            numPointsPref?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.filters = arrayOf<InputFilter>(InputFilterMinMax(2, 2000))
            }

            // Set up summary provider for pixel_step
            val pixelStepPref = findPreference<ListPreference>(getString(R.string.pref_key_pixel_step))
            pixelStepPref?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        // Input filter for number range validation
        private inner class InputFilterMinMax(private val min: Int, private val max: Int) : InputFilter {
            override fun filter(
                source: CharSequence,
                start: Int,
                end: Int,
                dest: Spanned,
                dstart: Int,
                dend: Int,
            ): CharSequence? {
                try {
                    val input = (dest.toString() + source.toString()).toInt()
                    if (isInRange(min, max, input)) return null
                } catch (e: NumberFormatException) {
                    // Ignore invalid input
                }
                return ""
            }

            private fun isInRange(a: Int, b: Int, c: Int): Boolean {
                return if (b > a) c in a..b else c in b..a
            }
        }
    }
}