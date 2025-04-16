package com.example.voronoiwallpaper

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.random.Random
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

class VoronoiWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VoronoiEngine()

    inner class VoronoiEngine : Engine() {


        private val handler = Handler(Looper.getMainLooper())


        private var width = 0
        private var height = 0
        private var visible = false
        private val pointAlpha = 128 // 50% transparency
        private val drawPoints = true

        // Voronoi properties
        private val numPoints = 25
        private val points = Array(numPoints) { PointF() }
        private val colors = IntArray(numPoints)
        private val velocities = Array(numPoints) { PointF() }
        private val random = Random.Default

        // Optimization
        private val pixelStep = 4 // Higher values improve performance but reduce quality
        private val pointRadius = 5f
        private val frameDelay = 50L // 20 FPS

        private val drawRunnable = Runnable { drawFrame() }


        // Double buffering system
        private lateinit var renderBuffer: Bitmap
        private lateinit var bufferCanvas: Canvas
        private val pointPaint = Paint().apply {
            color = Color.argb(pointAlpha, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                drawFrame()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            this.width = width
            this.height = height

            renderBuffer = createBitmap(width / pixelStep + 1, height / pixelStep + 1)
            bufferCanvas = Canvas(renderBuffer)
            initializePoints()
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        private fun initializePoints() {
            for (i in 0 until numPoints) {
                points[i].set(
                    random.nextFloat() * width,
                    random.nextFloat() * height
                )
                colors[i] = Color.HSVToColor(floatArrayOf(
                    random.nextFloat() * 360f,
                    0.7f + random.nextFloat() * 0.3f,
                    0.8f + random.nextFloat() * 0.2f
                ))
                velocities[i].set(
                    (random.nextFloat() - 0.5f) * 5f,
                    (random.nextFloat() - 0.5f) * 5f
                )
            }
        }

        private fun drawFrame() {
            if (!visible) return

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
                    it.drawBitmap(
                        renderBuffer,
                        Rect(0, 0, renderBuffer.width, renderBuffer.height),
                        Rect(0, 0, width, height),
                        null
                    )

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

                    var closest = 0
                    var minDist = Float.MAX_VALUE
                    for (i in 0 until numPoints) {
                        val dx = x - points[i].x
                        val dy = y - points[i].y
                        val dist = dx * dx + dy * dy

                        if (dist < minDist) {
                            minDist = dist
                            closest = i
                        }
                    }
                    renderBuffer[bx, by] = colors[closest]
                }
            }

//            // 2. Draw transparent black points
//            if (drawPoints) {
//                points.forEach { point ->
//                    // Convert screen coordinates to buffer space
//                    val bufferX = point.x / pixelStep
//                    val bufferY = point.y / pixelStep
//                    val bufferRadius = pointRadius / pixelStep
//
//                    bufferCanvas.drawCircle(
//                        bufferX,
//                        bufferY,
//                        bufferRadius.coerceAtLeast(1.5f), // Minimum visible radius
//                        pointPaint
//                    )
//                }
//            }
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
    }
}