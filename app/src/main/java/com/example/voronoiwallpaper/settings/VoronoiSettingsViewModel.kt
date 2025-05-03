package com.example.voronoiwallpaper.settings

// VoronoiSettingsViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
// VoronoiViewModelFactory.kt
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelProvider

class VoronoiSettingsViewModel(private val preferences: VoronoiPreferences) : ViewModel() {
    private val _uiState = MutableStateFlow(VoronoiSettings())
    val uiState = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.settingsFlow.collect { settings ->
                _uiState.value = settings
                _isLoading.value = false
            }
        }
    }

//    fun updateSettings(settings: VoronoiSettings) {
//        _uiState.value = settings
//        viewModelScope.launch {
//            preferences.updateSettings(settings)
//        }
//    }
    fun updateSettings(newSettings: VoronoiSettings) {
        viewModelScope.launch {
            // Update UI state immediately
            _uiState.value = newSettings
            // Persist to DataStore
            preferences.updateSettings(newSettings)
            try {
                // Update UI state immediately
                _uiState.value = newSettings
                // Persist to DataStore
                preferences.updateSettings(newSettings)
            } catch (e: Exception) {
                // Log error or show snackbar
                Log.e("Settings", "Failed to save settings", e)
                // Revert UI state
                _uiState.value = _uiState.value.copy()
            }
        }
    }
}



class VoronoiViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoronoiSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoronoiSettingsViewModel(VoronoiPreferences(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}