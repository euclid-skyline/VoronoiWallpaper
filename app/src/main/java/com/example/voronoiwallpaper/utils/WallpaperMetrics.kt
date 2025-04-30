package com.example.voronoiwallpaper.utils

import android.graphics.Bitmap

interface WallpaperMetrics {
    fun startMonitoring()
    fun stopMonitoring()
    fun onFrameGenerated(durationMs: Long)
    fun onFrameRendered(durationMs: Long)
    fun onUserInteraction()
    fun onBitmapAllocated(bitmap: Bitmap)
    fun onBitmapReleased(bitmap: Bitmap)
}