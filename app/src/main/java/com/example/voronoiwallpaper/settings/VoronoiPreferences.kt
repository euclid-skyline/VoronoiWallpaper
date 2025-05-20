package com.example.voronoiwallpaper.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn

class VoronoiPreferences(context: Context) {
    private val dataStore = context.voronoiDataStore

    val settingsFlow: Flow<VoronoiSettings> = dataStore.data
        .catch { exception ->
            // dataStore.data throws an IOException if it can't read data
            if (exception is IOException) {
                Log.e("VoronoiPreferences", "Error reading preferences.", exception)
                // When an IOException occurs, emit an empty Preferences object.
                // The map operator downstream will convert this to default VoronoiSettings.
                emit(emptyPreferences())
            } else {
                throw exception // Rethrow other exceptions
            }
        }
        .map {
            VoronoiSettings(
                numPoints = it[PreferenceKeys.NUM_POINTS] ?: VoronoiSettings.DEFAULT_SETTINGS.numPoints,
                drawPoints = it[PreferenceKeys.DRAW_POINTS] ?: VoronoiSettings.DEFAULT_SETTINGS.drawPoints,
                pixelStep = it[PreferenceKeys.PIXEL_STEP] ?: VoronoiSettings.DEFAULT_SETTINGS.pixelStep,
                useSpatialGrid = it[PreferenceKeys.USE_SPATIAL_GRID] ?: VoronoiSettings.DEFAULT_SETTINGS.useSpatialGrid
            )
        }//.flowOn(Dispatchers.IO)

    suspend fun updateSettings(settings: VoronoiSettings) {
        dataStore.edit {
            it[PreferenceKeys.NUM_POINTS] = settings.numPoints.coerceIn(2, 3000)
            it[PreferenceKeys.DRAW_POINTS] = settings.drawPoints
            it[PreferenceKeys.PIXEL_STEP] = settings.pixelStep.coerceIn(1, 5)
            it[PreferenceKeys.USE_SPATIAL_GRID] = settings.useSpatialGrid

            Log.d("Settings", "Saved: $it")
        }
    }
}