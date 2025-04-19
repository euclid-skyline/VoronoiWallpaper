package com.example.voronoiwallpaper

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.random.Random
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.sqrt

class VoronoiWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VoronoiEngine()

    inner class VoronoiEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())

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

        // Voronoi properties
        private val numPoints = 25

        private val colors = IntArray(numPoints) { 0 }
        private val points = Array(numPoints) { PointF() }
        private val velocities = Array(numPoints) { PointF() }

        private val random = Random.Default

        // Optimization
        private val pixelStep = 3   // Higher values improve performance but reduce quality
        private val pointRadius = 5f
        private val frameDelay = 16L // ~60 FPS

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (!isPaused) handler.postDelayed(this, frameDelay)
            }
        }

        // Double buffering system
        private lateinit var renderBuffer: Bitmap
        private lateinit var bufferCanvas: Canvas
        private val pointPaint = Paint().apply {
            color = Color.argb(pointAlpha, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private lateinit var renderBufferRect: Rect
        private lateinit var screenRect: Rect
        // Buffer to hold all pixel colors for bulk operations
        private lateinit var bufferPixels: IntArray

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d("onSurfaceChanged", "onSurfaceChanged called")
            // Check if dimensions actually changed
            if (this.width == width && this.height == height) {
                return // No change, skip reinitialization
            }

            this.width = width
            this.height = height

            // Recycle existing bitmap if it exists
            if (::renderBuffer.isInitialized && !renderBuffer.isRecycled) {
                renderBuffer.recycle()
            }

            // Use ceiling division to calculate the exact buffer size needed to cover the screen
            // This is to minimize the number of buffer updates needed to cover the screen
            // The formula (n + divisor - 1) / divisor rounds up to the nearest integer
            val bufferWidth = (width + pixelStep - 1) / pixelStep
            val bufferHeight = (height + pixelStep - 1) / pixelStep

            // Create new bitmap with updated dimensions
            renderBuffer = createBitmap(
                bufferWidth,
                bufferHeight,
                Bitmap.Config.ARGB_8888 // Ensure 32-bit color depth
            )
            bufferCanvas = Canvas(renderBuffer)
            renderBufferRect = Rect(0, 0, renderBuffer.width, renderBuffer.height)
            screenRect = Rect(0, 0, width, height)
            // Initialize bufferPixels when renderBuffer is created
            bufferPixels = IntArray(renderBuffer.width * renderBuffer.height)
            initializePoints()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        override fun onDestroy() {
            handler.removeCallbacksAndMessages(null)
            super.onDestroy()
        }

        override fun onTouchEvent(event: MotionEvent?) {
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

                    if (!isPaused) handler.post(drawRunnable)
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

        private fun drawFrame() {
            if (!visible || isPaused) return

            // 1. Update points first
            updatePoints()

            // 2. Draw to buffer (low-res)
            drawVoronoiToBuffer()

            // 3. Draw buffer to screen (fast)
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.let {
                    it.drawColor(Color.BLACK)
                    val paint = Paint().apply { isFilterBitmap = true }     // Enable bitmap filtering
                    it.drawBitmap(renderBuffer, renderBufferRect, screenRect, paint)

                    // 4. Draw Voronoi points directly to main canvas
                    if (drawPoints) {
                        drawPointsToCanvas(it)
                    }
                }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
                handler.postDelayed(::drawFrame, frameDelay)
            }
        }

        private fun drawVoronoiToBuffer() {
            var index = 0 // Tracks position in bufferPixels

            // Loop order changed to row-major (y first, then x)
            for (by in 0 until renderBuffer.height) {
                val y = by * pixelStep // Physical Y-coordinate
                for (bx in 0 until renderBuffer.width) {
                    val x = bx * pixelStep // Physical X-coordinate
                    val closest = findClosestPointIndexEuclidean(x, y)
//                    val closest = findClosestPointIndexManhattan(x, y)
//                    val closest = findClosestPointIndexChebyshev(x, y)

                    // Store color in bufferPixels instead of direct bitmap access
                    bufferPixels[index++] = colors[closest]
                }
            }

            // Bulk update the entire bitmap
            renderBuffer.setPixels(
                bufferPixels,  // Source array
                0,             // Offset in source
                renderBuffer.width, // Source stride (same as bitmap width)
                0, 0,         // Destination (x, y)
                renderBuffer.width, renderBuffer.height // Width/height to write
            )
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
        }

        private fun drawPointsToCanvas(canvas: Canvas) {
            // Draw points at full resolution with anti-aliasing
            points.forEach { point ->
                canvas.drawCircle(point.x, point.y, pointRadius, pointPaint)
            }
        }

        private fun generateDistinctColors() {
            val goldenAngle = 137.508f // Golden ratio-based angle for optimal distribution
            var hue = Random.nextFloat() * 360 // Random starting hue

            // Calculate how many variations we need for saturation and value
            val saturationBands = max(2, sqrt(numPoints.toFloat()).toInt())
            val valueBands = max(2, sqrt(numPoints.toFloat()).toInt())

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

            // Shuffle to avoid color sequence being too predictable
            colors.apply { shuffle() }
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

        @Suppress("unused")
        private fun findClosestPointIndexEuclidean(x: Int, y: Int): Int {
            var closestIndex = 0
            var minDistance = Float.MAX_VALUE

            points.forEachIndexed { index, point ->
                val dx = x - point.x
                val dy = y - point.y
                val distance = dx * dx + dy * dy // Use square Euclidean distance to improve performance

                if (distance < minDistance) {
                    minDistance = distance
                    closestIndex = index
                }
            }

            return closestIndex
        }

        @Suppress("unused")
        private fun findClosestPointIndexManhattan(x: Int, y: Int): Int {
            var closestIndex = 0
            var minDistance = Float.MAX_VALUE

            points.forEachIndexed { index, point ->

                // Use Manhattan distance. Do not use abs() for performance
                val dx = if (x >= point.x) x - point.x else point.x - x
                val dy = if (y >= point.y) y - point.y else point.y - y
                val distance = dx + dy

                if (distance < minDistance) {
                    minDistance = distance
                    closestIndex = index
                }
            }

            return closestIndex
        }

        @Suppress("unused")
        private fun findClosestPointIndexChebyshev(x: Int, y: Int): Int {
            var closestIndex = 0
            var minDistance = Float.MAX_VALUE

            points.forEachIndexed { index, point ->

                // Use Chebyshev distance. Do not use abs() for performance
                val dx = if (x >= point.x) x - point.x else point.x - x
                val dy = if (y >= point.y) y - point.y else point.y - y
                val distance = max(dx, dy)

                if (distance < minDistance) {
                    minDistance = distance
                    closestIndex = index
                }
            }

            return closestIndex
        }
    }
}