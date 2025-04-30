package com.example.voronoiwallpaper.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class VoronoiMetricsLogger(
    private val scope: CoroutineScope,
    private val context: Context,
    private val loggingInterval: Long = 5000L
) : WallpaperMetrics {
    // Atomic counters for thread safety
    private val frameGenTime = AtomicLong(0)
    private val frameGenCount = AtomicInteger(0)
    private val frameRenderTime = AtomicLong(0)
    private val frameRenderCount = AtomicInteger(0)
    private val fpsCounter = AtomicInteger(0)
    private val interactionCount = AtomicInteger(0)

    // Power consumption metrics
    private val activeRenderTime = AtomicLong(0)
    private var lastBatterySnapshot: BatterySnapshot? = null

    // Memory metrics
    private val bitmapAllocations = AtomicLong(0)
    private var maxBitmapMemory = 0L

    // Battery tracking
    private var batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private var metricsJob: Job? = null
    private var lastLogTime = 0L

    override fun startMonitoring() {
        if (metricsJob?.isActive == true) return

        lastBatterySnapshot = takeBatterySnapshot()
        metricsJob = scope.launch(Dispatchers.Default) {
            lastLogTime = SystemClock.uptimeMillis()

            while (isActive) {
                try {
                    val now = SystemClock.uptimeMillis()
                    val elapsed = now - lastLogTime

                    if (elapsed >= loggingInterval) {
                        logMetrics()
                        lastLogTime = now
                    }

                    // Calculate next check time
                    val nextCheck = loggingInterval - (elapsed % loggingInterval)
                    delay(max(1, nextCheck))
                } catch (e: Exception) {
                    Log.e("Metrics", "Monitoring error: ${e.message}")
                    delay(loggingInterval) // Prevent tight loop on errors
                }
            }
        }
    }

    // This is a fixed delay
//    override fun startMonitoring() {
//        if (metricsJob?.isActive == true) return
//
//        metricsJob = scope.launch(Dispatchers.Default) {
//            logMetrics() // Initial immediate log
//            while (isActive) {
//                delay(loggingInterval)
//                logMetrics()
//            }
//        }
//    }

    override fun stopMonitoring() {
        metricsJob?.cancel()
    }

    override fun onFrameGenerated(durationMs: Long) {
        frameGenTime.addAndGet(durationMs)
        frameGenCount.incrementAndGet()
    }

    override fun onFrameRendered(durationMs: Long) {
        frameRenderTime.addAndGet(durationMs)
        frameRenderCount.incrementAndGet()
        fpsCounter.incrementAndGet()
    }

    override fun onUserInteraction() {
        interactionCount.incrementAndGet()
    }

    // Add to metrics interface:
    override fun onBitmapAllocated(bitmap: Bitmap) {
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            bitmap.allocationByteCount
        } else {
            bitmap.byteCount
        }
        bitmapAllocations.addAndGet(bytes.toLong())
    }

    override fun onBitmapReleased(bitmap: Bitmap) {
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            bitmap.allocationByteCount
        } else {
            bitmap.byteCount
        }
        bitmapAllocations.addAndGet(-bytes.toLong())
    }

    private fun logMetrics() {
        try {
            val currentBattery = takeBatterySnapshot()
            val memoryInfo = Debug.MemoryInfo().apply {
                Debug.getMemoryInfo(this)
            }

//            Log.d("VoronoiMetrics", """
//                |==== Performance Metrics [${SystemClock.uptimeMillis()}] ====
//                |FPS: ${fpsCounter.get().toFloat() / (loggingInterval / 1000f)}
//                |CPU Gen: ${frameGenTime.get() / max(1, frameGenCount.get())}ms
//                |CPU Render: ${frameRenderTime.get() / max(1, frameRenderCount.get())}ms
//                |Native Mem: ${memoryInfo.nativePss}KB
//                |Total Mem: ${Runtime.getRuntime().totalMemory() / 1024}KB
//                |Interactions: ${interactionCount.get()}
//                |""".trimMargin())

            Log.d("VoronoiMetrics", """
                |==== Performance Metrics [${System.currentTimeMillis()}] ====
                |FPS: ${fpsCounter.get().toFloat() / (loggingInterval / 1000f)}
                |CPU Usage:
                |  Gen: ${safeDivide(frameGenTime.get(), frameGenCount.get())}ms
                |  Render: ${safeDivide(frameRenderTime.get(), frameRenderCount.get())}ms
                |Memory:
                |  Bitmaps: ${formatBytes(bitmapAllocations.get())} (Max: ${formatBytes(maxBitmapMemory)})
                |  Native: ${memoryInfo.nativePss}KB
                |  Total: ${Runtime.getRuntime().totalMemory() / 1024}KB
                |Power:
                |  Active: ${"%.1f".format(activeRenderTime.get().toFloat() / loggingInterval * 100)}%
                |  Est. Consumption: ${calculatePowerConsumption(currentBattery)} mA
                |Battery:
                |  Current: ${currentBattery?.currentNow?.div(1000)} mA
                |  Capacity: ${currentBattery?.chargeCounter?.div(1000)} mAh
                |  Interactions: ${interactionCount.get()}
                |""".trimMargin())

            // Reset counters
            fpsCounter.set(0)
            frameGenTime.set(0)
            frameGenCount.set(0)
            frameRenderTime.set(0)
            frameRenderCount.set(0)
            activeRenderTime.set(0)
            interactionCount.set(0)

        } catch (e: Exception) {
            Log.e("Metrics", "Logging failed: ${e.message}")
        }
    }

    // Update initialization to track bitmap memory
    fun setFramePool(pool: Array<Bitmap>) {
        maxBitmapMemory = pool.sumOf {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                it.allocationByteCount.toLong()
            } else {
                it.byteCount.toLong()  // Fallback for older APIs
            }
        }
    }

    private fun takeBatterySnapshot(): BatterySnapshot? {
        return try {
            BatterySnapshot(
                currentNow = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0,
                chargeCounter = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0,
                timestamp = SystemClock.elapsedRealtime()
            )
        } catch (e: SecurityException) {
            // Handle missing permissions
            null
        }
    }

    private fun calculatePowerConsumption(currentBattery: BatterySnapshot?): Float {
        val previous = lastBatterySnapshot ?: return 0f
        currentBattery ?: return 0f

        val timeDelta = (currentBattery.timestamp - previous.timestamp).coerceAtLeast(1)
        val currentDelta = (previous.currentNow - currentBattery.currentNow).toFloat()

        lastBatterySnapshot = currentBattery
        return (currentDelta / timeDelta) * 1000 // Convert to mA
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    private fun safeDivide(numerator: Long, denominator: Int) =
        if (denominator > 0) numerator / denominator else 0

    private data class BatterySnapshot(
        val currentNow: Int,
        val chargeCounter: Int,
        val timestamp: Long
    )
}

