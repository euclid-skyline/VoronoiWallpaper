package com.example.voronoiwallpaper

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.random.Random
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
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

        private val doubleTapThreshold = 400L // milliseconds
        private val maxTaps = 3 // Triple-tap
        private var tapCount = 0
        private var lastTapTime = 0L
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

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            this.width = width
            this.height = height

            renderBuffer = createBitmap(width / pixelStep + 1, height / pixelStep + 1)
            bufferCanvas = Canvas(renderBuffer)
            renderBufferRect = Rect(0, 0, renderBuffer.width, renderBuffer.height)
            screenRect = Rect(0, 0, width, height)
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
                val now = System.currentTimeMillis()
                val elapsedTime = now - lastTapTime

                // Test the counter before it is used and Reset count if taps are too slow
                if (tapCount < maxTaps && tapCount != 0 && elapsedTime > doubleTapThreshold) {
                    tapCount = 0
                }

                tapCount++
                lastTapTime = now

                // Check for double-tap
                if (tapCount == maxTaps) {
                    isPaused = !isPaused
                    tapCount = 0

                    if (!isPaused) {
                        handler.post(drawRunnable) // Redraw immediately when resuming from pause
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
                    it.drawBitmap(renderBuffer, renderBufferRect, screenRect, null)

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
            // 1. Draw Voronoi cells
            for (bx in 0 until renderBuffer.width) {
                val x = bx * pixelStep
                for (by in 0 until renderBuffer.height) {
                    val y = by * pixelStep
                    val closest = findClosestPointIndexEuclidean(x, y)
//                    val closest = findClosestPointIndexManhattan(x, y)
//                    val closest = findClosestPointIndexChebyshev(x, y)

                    renderBuffer[bx, by] = colors[closest]
                }
            }
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

            colors.apply { shuffle() }
        }

        private fun generateDistinctColors1() {
            val hueStep = 360f / numPoints

            for (i in 0 until numPoints) {
                val hue = (i * hueStep) % 360f
                // Keep saturation and value in vibrant ranges
                val saturation = 0.7f + 0.3f * Random.nextFloat() // 0.7-0.9
                val value = 0.8f + 0.15f * Random.nextFloat()    // 0.8-0.95

                colors[i] = Color.HSVToColor(floatArrayOf(hue, saturation, value))
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