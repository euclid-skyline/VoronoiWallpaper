package com.example.voronoiwallpaper.math

import android.graphics.PointF
import kotlin.math.sqrt

fun computeCovarianceMatrix(points: Array<PointF>): Array<FloatArray> {
    require(points.size >= 2) { "At least two points required to compute covariance" }

    val n = points.size
    var sumX = 0f
    var sumY = 0f

    // Calculate means
    for (point in points) {
        sumX += point.x
        sumY += point.y
    }
    val meanX = sumX / n
    val meanY = sumY / n

    // Calculate variance and covariance components
    var varX = 0f
    var varY = 0f
    var covXY = 0f

    for (point in points) {
        val dx = point.x - meanX
        val dy = point.y - meanY
        varX += dx * dx
        varY += dy * dy
        covXY += dx * dy
    }

    // Create covariance matrix with unbiased estimator (n-1 denominator)
    return arrayOf(
        floatArrayOf(varX / (n - 1), covXY / (n - 1)),
        floatArrayOf(covXY / (n - 1), varY / (n - 1))
    )
}

fun invert2x2Matrix(matrix: Array<FloatArray>): Array<FloatArray> {
    val (a, b) = matrix[0][0] to matrix[0][1]
    val (c, d) = matrix[1][0] to matrix[1][1]
    val det = a * d - b * c
    require(det != 0f) { "Matrix is singular (non-invertible)" }

    return arrayOf(
        floatArrayOf(d / det, -b / det),
        floatArrayOf(-c / det, a / det)
    )
}



fun mahalanobisDistance( p1: PointF, p2: PointF, sInv: Array<FloatArray>): Float {
    // Validate matrix is 2x2
    require(sInv.size == 2) { "Inverse covariance matrix must be 2x2" }
    require(sInv.all { it.size == 2 }) { "Inverse covariance matrix must be 2x2" }

    // Calculate differences
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y

    // Compute quadratic form: [dx dy] * S⁻¹ * [dx; dy]
    val sum = dx * (sInv[0][0] * dx + sInv[0][1] * dy) +
            dy * (sInv[1][0] * dx + sInv[1][1] * dy)

//    return sqrt(sum.toDouble()).toFloat() // Convert to Float for precision
    return sum
}