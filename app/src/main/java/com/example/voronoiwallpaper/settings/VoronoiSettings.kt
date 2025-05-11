package com.example.voronoiwallpaper.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

data class VoronoiSettings(
    val numPoints: Int = 553,
    val drawPoints: Boolean = true,
    val pixelStep: Int = 3,
    val useSpatialGrid: Boolean = true
) {
    companion object {
        // Default settings
        val DEFAULT_SETTINGS = VoronoiSettings()
    }
}

object PreferenceKeys {
    val NUM_POINTS = intPreferencesKey("num_points")
    val DRAW_POINTS = booleanPreferencesKey("draw_points")
    val PIXEL_STEP = intPreferencesKey("pixel_step")
    val USE_SPATIAL_GRID = booleanPreferencesKey("use_spatial_grid")
}