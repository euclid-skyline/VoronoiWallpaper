package com.example.voronoiwallpaper

import android.graphics.*
import android.graphics.Bitmap.Config
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.graphics.createBitmap
import kotlin.random.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.voronoiwallpaper.utils.*


class VoronoiWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VoronoiEngine()

    companion object {
        // Modified parameters for color contrast
        private const val DARK_THRESHOLD = 0.4f  // More colors considered dark
        // More Natural Contrast
        private const val LIGHTEN_FACTOR = 0.6f  // 60% lightening
        private const val DARKEN_FACTOR = 0.4f   // 40% Darkening
    }

    enum class DIST { EUCLIDEAN, MANHATTAN, CHEBYSHEV, SKYLINE }

    inner class VoronoiEngine : Engine() {

        // Coroutine scope tied to engine lifecycle
        private val wallpaperScope = CoroutineScope(
    Dispatchers.Main +
            Job().apply {
                invokeOnCompletion {
                    frameChannel.close()  // Clean up channel
                }
            }
        )

        // Channel for passing rendered frames (double-buffered)
        private val frameChannel = Channel<Bitmap>(capacity = 2)

        // Track active jobs
        private var producerJob: Job? = null
        private var consumerJob: Job? = null
        // Metrics logger for performance monitoring
//        private val metricsLogger = VoronoiMetricsLogger(
//            scope = wallpaperScope,
//            context = this@VoronoiWallpaperService,
//            loggingInterval = 5000L
//        )
        private val metricsLogger by lazy {
            VoronoiMetricsLogger(
                wallpaperScope,
                this@VoronoiWallpaperService,
                5000L
            )
        }

        // Mutex for thread-safe point updates.
        // Ensures only one coroutine updates points at a time.
        private val pointsMutex = Mutex()

        private var width = 0
        private var height = 0
        private var visible = false
        private val pointAlpha = 128 // 50% transparency
        private val drawPoints = true

        private val maxTaps = 3 // Triple-tap
        // Track tap timestamps
        private val tapTimestamps = mutableListOf<Long>()
        private val tapWindow = 800L  // Total allowed time for maxTaps

        private var isPaused = false

        // Voronoi Control Points
        private val numPoints = 553

        private val colors = IntArray(numPoints) { 0 }
        private val pointColors = IntArray(numPoints) { 0 }
        private val points = Array(numPoints) { PointF() }
        private val velocities = Array(numPoints) { PointF() }

        private val random = Random.Default

        private val pixelStep = 3   // Higher values improve performance but reduce quality
        private val pointRadius = when {
            numPoints > 100 -> 4f
            else -> 5f
        }
        private val frameDelay = 16L // ~60 FPS

        private val pointPaint = Paint().apply {
            color = Color.argb(pointAlpha, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private lateinit var frameBufferRect: Rect
        private lateinit var screenRect: Rect
        // Buffer to hold all pixel colors for bulk operations
        private lateinit var bufferPixels: IntArray

        // Double buffering system.
        // This is a fundamental graphics programming pattern used
        // in games, OS compositors, and video players.
        private lateinit var framePool: Array<Bitmap>
        private var currentBufferIndex = 0
        // Protects buffer pool access separately.
        private val bufferMutex = Mutex()

        // Visual Quality when upscaling from frame buffers to screen canvas
        private val upscalePaint = Paint().apply {
            isFilterBitmap = true           // Enables bilinear filtering during scaling
            isDither = true                 // Add Dither to the Paint to reduce color banding
        }

        // Optimal grid size formula for dynamic points:
        private val gridFactor = 1.0 // Adjust to control pixel per cell
        private var gridSize = 0
        private lateinit var grid: Array<Array<MutableList<Int>>>
        private var gridWidth: Int = 0
        private var gridHeight: Int = 0
        private val useSpatialGrid = numPoints >= 500

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d("onSurfaceChanged", "onSurfaceChanged called")
            // Check if dimensions actually changed
            if (this.width == width && this.height == height) {
                return // No change, skip reinitialization
            }

            this.width = width
            this.height = height

            // Recycle existing bitmap if it exists before
            if (::framePool.isInitialized) {
                framePool.forEach {
//                    it.recycle()
                    metricsLogger.onBitmapReleased(it)
                    if (!it.isRecycled) it.recycle()
                }
            }

            // Use ceiling division to calculate the exact buffer size needed to cover the screen
            // This is to minimize the number of buffer updates needed to cover the screen
            // The formula (n + divisor - 1) / divisor rounds up to the nearest integer
            val bufferWidth = (width + pixelStep - 1) / pixelStep
            val bufferHeight = (height + pixelStep - 1) / pixelStep

            framePool = Array(2) {
                createBitmap(
                    bufferWidth,
                    bufferHeight,
                    Config.ARGB_8888   // Ensure 32-bit color depth
                ).also {
                    // Track bitmap allocation
                    bitmap -> metricsLogger.onBitmapAllocated(bitmap)
//                    metricsLogger.onBitmapAllocated(it)
                }
            }

            // Set the frame pool reference
            metricsLogger.setFramePool(framePool)

            frameBufferRect = Rect(0, 0, bufferWidth, bufferHeight)
            screenRect = Rect(0, 0, width, height)
            bufferPixels = IntArray(bufferWidth * bufferHeight)

            initializePoints()
            if (useSpatialGrid) {
                gridSize =(sqrt(((width * height).toDouble() / numPoints)) * gridFactor).toInt()
                gridWidth = (width + gridSize - 1) / gridSize
                gridHeight = (height + gridSize - 1) / gridSize
                grid = Array(gridWidth) { Array(gridHeight) { mutableListOf() } }
                updateGrid() // Initial population
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                startFrameLoop()
            } else {
                stopFrameLoop()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            // Cancel ongoing operations immediately
            wallpaperScope.coroutineContext.cancelChildren()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            // 1. Stop all coroutines
            wallpaperScope.cancel()

            // 2. Recycle bitmaps
            if (::framePool.isInitialized) {
                framePool.forEach {
                    metricsLogger.onBitmapReleased(it)
                    if (!it.isRecycled) it.recycle()
                }
            }

            // 3. Close communication channel
            frameChannel.close()

            metricsLogger.stopMonitoring()
            super.onDestroy()
        }

        override fun onTouchEvent(event: MotionEvent?) {
            metricsLogger.onUserInteraction()

            if (event?.action == MotionEvent.ACTION_UP) {
                val now = SystemClock.elapsedRealtime() // Monotonic time//System.currentTimeMillis()

                // 1. Add current tap timestamp
                tapTimestamps.add(now)

                // 2. Purge taps outside sliding window (now - window)
                tapTimestamps.removeAll { (now - it) > tapWindow }


                // 3. Check if taps in window meet maxTaps requirement
                if (tapTimestamps.size >= maxTaps) {
                    Log.d("TAP", "Triple-tap detected. Timestamps: $tapTimestamps")
                    isPaused = !isPaused
                    tapTimestamps.clear()

                    if (!isPaused) {
                        // Resume with new coroutines if needed
                        if (producerJob?.isActive != true || consumerJob?.isActive != true) {
                            startFrameLoop()
                        }
                    } else {
                        // Cancel current operations but keep scope alive
                        wallpaperScope.coroutineContext.cancelChildren()
                    }
                }
            }
            super.onTouchEvent(event)
        }

        private fun initializePoints() {
            for (i in 0 until numPoints) {
                points[i].set(
                    random.nextFloat() * width,
                    random.nextFloat() * height
                )
                velocities[i].set(
                    (random.nextFloat() - 0.5f) * 5f,
                    (random.nextFloat() - 0.5f) * 5f
                )
            }
            generateDistinctColors()
        }

        // Update the grid
        private fun updateGrid() {
            if (!useSpatialGrid) return
            // Clear previous grid data
            grid.forEach { row -> row.forEach { it.clear() } }

            // Populate grid with point indices
            points.forEachIndexed { index, point ->
                val cellX = (point.x / gridSize).toInt().coerceIn(0, gridWidth - 1)
                val cellY = (point.y / gridSize).toInt().coerceIn(0, gridHeight - 1)
                grid[cellX][cellY].add(index)
            }
        }

        private fun startFrameLoop() {
            // Cancel any existing jobs before starting a new one.
            // Ensure that when the visibility changes or the frame loop restarts,
            // any old jobs are properly cleaned up before starting new ones.
            // This is a common practice to prevent resource leaks
            // and ensure that only the latest jobs are active.
            wallpaperScope.coroutineContext.cancelChildren()

            metricsLogger.startMonitoring() // Start monitoring metrics first for this session
            // Launch new producer/consumer pair
            producerJob = wallpaperScope.launch {
                while (isActive) {
                    if (!isPaused) {
                        val startTime = SystemClock.uptimeMillis()
                        generateFrame()
                        val elapsed = SystemClock.uptimeMillis() - startTime

                        // Adaptive frame pacing
                        delay(max(0, frameDelay - elapsed))
                    } else {
                        delay(16) // Reduced CPU usage when paused
                    }
                }
            }

            consumerJob = wallpaperScope.launch {
                while (isActive) {
                    if (!isPaused) {
                        try {
                            renderFrame()
                        } catch (e: Exception) {
                            // Handle surface errors
                            // 1. Log the error
                            Log.e("Voronoi", "Rendering failed: ${e.message}", e)

                            // 2. Cancel further processing
                            wallpaperScope.coroutineContext.cancelChildren()

                            // 3. Reinitialize on next resume
                            isPaused = true
                        }
                    }
                    delay(1) // Keep UI responsive
                }
            }
        }

        private fun stopFrameLoop() {
            // Cancel ongoing frame processing. Pause but keep resources alive
            wallpaperScope.coroutineContext.cancelChildren()
            metricsLogger.stopMonitoring()
        }

        // Producer: Runs on background thread
        private suspend fun generateFrame() {
            val startTime = SystemClock.uptimeMillis()

            // 1. Update point positions (thread-safe)
            pointsMutex.withLock {
                updatePoints() // ❗ Critical for animation
            }

            bufferMutex.withLock {
                // 2. Get next buffer
                val target = framePool[currentBufferIndex]
                currentBufferIndex = (currentBufferIndex + 1) % framePool.size
                //3. Draw Voronoi to the buffer (background thread)
                drawVoronoiToBuffer(target)
                // 4. Send the frame to the channel (suspend if full)
                frameChannel.send(target)
            }
            metricsLogger.onFrameGenerated(SystemClock.uptimeMillis() - startTime)
        }

        // Consumer: Runs on main thread
        private suspend fun renderFrame() {
            val startTime = SystemClock.uptimeMillis()

            // Wait for a frame from the channel (suspend if empty)
            val frame = frameChannel.receive()

            // Render on the main thread
            withContext(Dispatchers.Main) {
                if (!visible || isPaused) return@withContext

                val holder = surfaceHolder
                val canvas = holder.lockCanvas()
                try {
                    canvas.drawColor(Color.BLACK)
                    canvas.drawBitmap(frame, frameBufferRect, screenRect, upscalePaint)
                    if (drawPoints) drawPointsToCanvas(canvas)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            metricsLogger.onFrameRendered(SystemClock.uptimeMillis() - startTime)
        }

        // Modified thread-safe version
        private fun drawVoronoiToBuffer(target: Bitmap) {
            var index = 0

            // 1. Calculate pixels in bulk
            for (yIndex in 0 until target.height) {
                val y = yIndex * pixelStep
                for (xIndex in 0 until target.width) {
                    val x = xIndex * pixelStep
                    val closest = findClosestPointIndex(x, y)

                    bufferPixels[index++] = colors[closest]
                }
            }

            // 2. Bulk write to bitmap (atomic operation)
            target.setPixels(
                bufferPixels,           // Source array
                0,          // Offset in source
                target.width,     // Source stride (same as bitmap width)
                0, 0,        // Destination (x, y)
                target.width, target.height)    // Width/height to write
        }

        private fun updatePoints() {
            for (i in 0 until numPoints) {
                with(points[i]) {
                    x += velocities[i].x
                    y += velocities[i].y

                    if (x <= 0 || x >= width) {
                        velocities[i].x *= -1 * (0.9f + random.nextFloat() * 0.2f)
                        x = x.coerceIn(0f, width.toFloat())
                    }
                    if (y <= 0 || y >= height) {
                        velocities[i].y *= -1 * (0.9f + random.nextFloat() * 0.2f)
                        y = y.coerceIn(0f, height.toFloat())
                    }
                }
            }
            updateGrid()
        }

        private fun drawPointsToCanvas(canvas: Canvas) {
            points.forEachIndexed { index, point ->
                pointPaint.color = pointColors[index]
                canvas.drawCircle(point.x, point.y, pointRadius, pointPaint)
            }
        }

        private fun generateDistinctColors() {
            val goldenAngle = 137.508f // Golden ratio-based angle for optimal distribution
            var hue = Random.nextFloat() * 360 // Random starting hue
            // Calculate how many variations we need for saturation and value
            val saturationBands = max(2, sqrt(numPoints.toFloat()).toInt())
            val valueBands = max(2, sqrt(numPoints.toFloat()).toInt())

            // 1. First generate original colors
            repeat(numPoints) { i ->
                // Advance hue by golden angle
                hue = (hue + goldenAngle) % 360

                // Calculate saturation and value bands
                val saturation = 0.65f + 0.3f * (i % saturationBands) / (saturationBands - 1)
                val value = 0.75f + 0.2f * (i / saturationBands % valueBands) / (valueBands - 1)

                colors[i] = Color.HSVToColor(floatArrayOf(
                    hue,
                    saturation.coerceIn(0.65f, 0.95f),
                    value.coerceIn(0.75f, 0.95f))
                )
            }

            // 2. Shuffle colors BEFORE creating darker versions
            // Shuffle to avoid color sequence being too predictable
            colors.apply { shuffle() }

            // 3. Generate adaptive point colors
            colors.forEachIndexed { i, color ->
                pointColors[i] = if (isDarkColor(color)) {
                    lightenColor(color).withAlpha(pointAlpha) // Apply transparency AFTER color adjustment
                } else {
                    darkenColor(color).withAlpha(pointAlpha)   // Apply transparency AFTER color adjustment
                }
            }
        }

        // Extension function to shuffle IntArray
        private fun IntArray.shuffle() {
            for (i in size - 1 downTo 1) {
                val j = Random.nextInt(i + 1)
                val temp = this[i]
                this[i] = this[j]
                this[j] = temp
            }
        }

        private fun isDarkColor(color: Int): Boolean {
            val luminance = 0.2126f * Color.red(color)/255f +
                    0.7152f * Color.green(color)/255f +
                    0.0722f * Color.blue(color)/255f
            return luminance < DARK_THRESHOLD
        }

        private fun lightenColor(color: Int): Int {
            val hsv = FloatArray(3).apply {
                Color.colorToHSV(color, this)
                // Boost both value and saturation for visibility
                this[1] *= 0.8f                 // Reduce saturation slightly
                this[2] = 1f - (1f - this[2]) * (1f - LIGHTEN_FACTOR)
            }
            return Color.HSVToColor(hsv)
        }

        private fun darkenColor(color: Int): Int {
            val hsv = FloatArray(3).apply {
                Color.colorToHSV(color, this)
                this[2] *= (1f - DARKEN_FACTOR)
            }
            return Color.HSVToColor(hsv)
        }

        // Helper to preserve original alpha
        private fun Int.withAlpha(alpha: Int): Int {
            return Color.argb(
                alpha,
                Color.red(this),
                Color.green(this),
                Color.blue(this)
            )
        }

        private fun findClosestPointIndex(x: Int, y: Int): Int {
            var closestIndex = 0
            var minDistance = Float.MAX_VALUE

            if (useSpatialGrid) {
                // Get grid cell coordinates with bounds checking
                val cellX = (x / gridSize).coerceIn(0, grid.size - 1)
                val cellY = (y / gridSize).coerceIn(0, grid[0].size - 1)

                // Check 3x3 neighborhood around current cell
                for (gridDx in -1..1) {
                    for (gridDy in -1..1) {
                        val checkX = cellX + gridDx
                        val checkY = cellY + gridDy

                        if (checkX in grid.indices && checkY in grid[0].indices) {
                            grid[checkX][checkY].forEach { index ->
                                val point = points[index]
                                val distance = calculateDistance(x, y, point, DIST.EUCLIDEAN)

                                if (distance < minDistance) {
                                    minDistance = distance
                                    closestIndex = index
                                }
                            }
                        }
                    }
                }
            } else {
                points.forEachIndexed { index, point ->
                    val distance = calculateDistance(x, y, point, DIST.EUCLIDEAN)

                    if (distance < minDistance) {
                        minDistance = distance
                        closestIndex = index
                    }
                }
            }

            return closestIndex
        }

        private fun calculateDistance(x: Int, y: Int, point: PointF, dist: DIST = DIST.EUCLIDEAN): Float {
            val distance = when (dist) {
                DIST.EUCLIDEAN -> {
                    val dx = x - point.x
                    val dy = y - point.y
                    // Use square Euclidean distance to improve performance
                    dx * dx + dy * dy
                }
                DIST.MANHATTAN -> {
                    // Use Manhattan distance. Do not use abs() fun for performance
                    val dx = if (x >= point.x) x - point.x else point.x - x
                    val dy = if (y >= point.y) y - point.y else point.y - y
                    dx + dy
                }
                DIST.CHEBYSHEV -> {
                    // Use Chebyshev distance. Do not use abs() fun for performance
                    val dx = if (x >= point.x) x - point.x else point.x - x
                    val dy = if (y >= point.y) y - point.y else point.y - y
                    max(dx, dy)
                }
                DIST.SKYLINE -> {
                    // Use Skyline distance. Do not use abs() for performance
                    val dx = if (x >= point.x) x - point.x else point.x - x
                    val dy = if (y >= point.y) y - point.y else point.y - y
                    min(dx, dy)
                }
            }
            return distance
        }
    }   // End of VoronoiEngine inner class
}       // End of VoronoiWallpaperService