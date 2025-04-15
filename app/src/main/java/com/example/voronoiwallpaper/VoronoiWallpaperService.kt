package com.example.voronoiwallpaper

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
//import kotlin.math.pow
//import kotlin.math.sqrt
import kotlin.random.Random

class VoronoiWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VoronoiEngine()

    inner class VoronoiEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private var width = 0
        private var height = 0
        private var visible = false

        // Voronoi properties
        private val random = Random.Default
        private var points = mutableListOf<PointF>()
        private var colors = mutableListOf<Int>()
        private var velocities = mutableListOf<PointF>()
        private val numPoints = 25

        // Optimization
        private val pixelStep = 4 // Higher values improve performance but reduce quality
        private val pointRadius = 5f
        private val frameDelay = 50L // 20 FPS

        private val drawRunnable = Runnable { drawFrame() }

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
            initializePoints()
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        private fun initializePoints() {
            points.clear()
            colors.clear()
            velocities.clear()

            repeat(numPoints) {
                points.add(PointF(
                    random.nextFloat() * width,
                    random.nextFloat() * height
                ))

                colors.add(Color.HSVToColor(floatArrayOf(
                    random.nextFloat() * 360f,
                    0.7f + random.nextFloat() * 0.3f,
                    0.8f + random.nextFloat() * 0.2f
                )))

                velocities.add(PointF(
                    (random.nextFloat() - 0.5f) * 5f,
                    (random.nextFloat() - 0.5f) * 5f
                ))
            }
        }

        private fun updatePoints() {
            points.forEachIndexed { index, point ->
                val velocity = velocities[index]

                point.x += velocity.x
                point.y += velocity.y

                // Bounce off walls with slight randomness
                if (point.x <= 0 || point.x >= width) {
                    velocity.x *= -1 * (0.9f + random.nextFloat() * 0.2f)
                    point.x = point.x.coerceIn(0f, width.toFloat())
                }
                if (point.y <= 0 || point.y >= height) {
                    velocity.y *= -1 * (0.9f + random.nextFloat() * 0.2f)
                    point.y = point.y.coerceIn(0f, height.toFloat())
                }
            }
        }

        private fun drawFrame() {
            if (!visible) return

            val holder = surfaceHolder
            var canvas: Canvas? = null

            try {
                canvas = holder.lockCanvas()
                canvas?.let {
                    drawVoronoi(it)
                    updatePoints()
                }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }

            handler.removeCallbacks(drawRunnable)
            if (visible) {
                handler.postDelayed(drawRunnable, frameDelay)
            }
        }

        private fun drawVoronoi(canvas: Canvas) {
            // Clear canvas
            canvas.drawColor(Color.BLACK)

            // Draw Voronoi cells
            for (x in 0 until width step pixelStep) {
                for (y in 0 until height step pixelStep) {
                    val closestIndex = findClosestPointIndex(x, y)
                    paint.color = colors[closestIndex]
                    canvas.drawRect(
                        x.toFloat(),
                        y.toFloat(),
                        (x + pixelStep).toFloat(),
                        (y + pixelStep).toFloat(),
                        paint
                    )
                }
            }

            // Draw points (optional)
            paint.color = Color.WHITE
            points.forEach { point ->
                canvas.drawCircle(point.x, point.y, pointRadius, paint)
            }
        }

        private fun findClosestPointIndex(x: Int, y: Int): Int {
            var closestIndex = 0
            var minDistance = Float.MAX_VALUE

            points.forEachIndexed { index, point ->
                val dx = x - point.x
                val dy = y - point.y
                val distance = dx * dx + dy * dy // Squared distance for comparison

                if (distance < minDistance) {
                    minDistance = distance
                    closestIndex = index
                }
            }

            return closestIndex
        }
    }
}