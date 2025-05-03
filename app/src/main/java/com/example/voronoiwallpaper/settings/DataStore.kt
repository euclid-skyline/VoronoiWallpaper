package com.example.voronoiwallpaper.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.voronoiDataStore: DataStore<Preferences> by preferencesDataStore(name = "voronoi_settings")
