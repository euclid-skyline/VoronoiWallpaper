package com.example.voronoiwallpaper.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

class VoronoiPreferences(context: Context) {
    private val dataStore = context.voronoiDataStore

    val settingsFlow: Flow<VoronoiSettings> = dataStore.data
        .map { // prefs ->
//            VoronoiSettings(
//                numPoints = prefs[PreferenceKeys.NUM_POINTS] ?: DEFAULT_SETTINGS.numPoints,
//                drawPoints = prefs[PreferenceKeys.DRAW_POINTS] ?: DEFAULT_SETTINGS.drawPoints,
//                pixelStep = prefs[PreferenceKeys.PIXEL_STEP] ?: DEFAULT_SETTINGS.pixelStep,
//                useSpatialGrid = prefs[PreferenceKeys.USE_SPATIAL_GRID] ?: DEFAULT_SETTINGS.useSpatialGrid
//            )
            VoronoiSettings(
                numPoints = it[PreferenceKeys.NUM_POINTS] ?: VoronoiSettings.DEFAULT_SETTINGS.numPoints,
                drawPoints = it[PreferenceKeys.DRAW_POINTS] ?: VoronoiSettings.DEFAULT_SETTINGS.drawPoints,
                pixelStep = it[PreferenceKeys.PIXEL_STEP] ?: VoronoiSettings.DEFAULT_SETTINGS.pixelStep,
                useSpatialGrid = it[PreferenceKeys.USE_SPATIAL_GRID] ?: VoronoiSettings.DEFAULT_SETTINGS.useSpatialGrid
            )
        }.flowOn(Dispatchers.IO)

    suspend fun updateSettings(settings: VoronoiSettings) {
        dataStore.edit { // prefs ->
//            prefs[PreferenceKeys.NUM_POINTS] = settings.numPoints.coerceIn(2, 2000)
//            prefs[PreferenceKeys.DRAW_POINTS] = settings.drawPoints
//            prefs[PreferenceKeys.PIXEL_STEP] = settings.pixelStep.coerceIn(1, 5)
//            prefs[PreferenceKeys.USE_SPATIAL_GRID] = settings.useSpatialGrid

//            Log.d("Settings", "Saved: $prefs")

            it[PreferenceKeys.NUM_POINTS] = settings.numPoints.coerceIn(2, 3000)
            it[PreferenceKeys.DRAW_POINTS] = settings.drawPoints
            it[PreferenceKeys.PIXEL_STEP] = settings.pixelStep.coerceIn(1, 5)
            it[PreferenceKeys.USE_SPATIAL_GRID] = settings.useSpatialGrid

            Log.d("Settings", "Saved: $it")
        }
    }
}