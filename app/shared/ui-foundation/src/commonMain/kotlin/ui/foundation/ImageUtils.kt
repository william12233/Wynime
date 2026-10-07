package com.wynime.app.ui.foundation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

expect fun ImageBitmap.resize(
    width: Int,
    height: Int,
): ImageBitmap

fun ImageBitmap.themeColor(): Color {
    val width = this.width
    val height = this.height

    val pixels = IntArray(width * height)
    this.readPixels(
        buffer = pixels,
        startX = 0,
        startY = 0,
        width = width,
        height = height,
        bufferOffset = 0,
        stride = width,
    )

    val points = mutableListOf<WeightedRGBPoint>()
    val centerX = width / 2.0
    val centerY = height / 2.0
    val maxDistance = kotlin.math.sqrt(centerX * centerX + centerY * centerY)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            if ((pixel shr 24) and 0xFF <= 128) continue

            val distanceFromCenter = kotlin.math.sqrt(
                (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY),
            )
            val weight = 1.0 - (distanceFromCenter / maxDistance) * 0.5

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            points.add(WeightedRGBPoint(RGBColor(r, g, b), weight))
        }
    }

    if (points.isEmpty()) return Color.Black

    val k = 5
    val clusters = kMeansPlusPlus(points, k, maxIterations = 15)

    val dominantCluster = clusters.maxByOrNull { cluster ->
        cluster.points.sumOf { it.weight } * cluster.points.size
    } ?: return Color.Black

    val (r, g, b) = dominantCluster.centroid
    return Color(
        red = r / 255f,
        green = g / 255f,
        blue = b / 255f,
    )
}

private data class RGBColor(val r: Int, val g: Int, val b: Int) {
    fun distanceTo(other: RGBColor): Double {
        val dr = r - other.r
        val dg = g - other.g
        val db = b - other.b
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}

private data class WeightedRGBPoint(
    val rgb: RGBColor,
    val weight: Double
)

private data class Cluster(
    var centroid: RGBColor,
    val points: MutableList<WeightedRGBPoint> = mutableListOf()
)

private fun kMeansPlusPlus(
    points: List<WeightedRGBPoint>,
    k: Int,
    maxIterations: Int
): List<Cluster> {

    val centroids = mutableListOf<RGBColor>()
    val random = kotlin.random.Random.Default

    centroids.add(points.random().rgb)

    while (centroids.size < k) {
        var totalDistance = 0.0
        val distances = points.map { point ->
            val minDistance = centroids.minOf { centroid ->
                point.rgb.distanceTo(centroid)
            }
            totalDistance += minDistance * minDistance * point.weight
            totalDistance
        }

        val threshold = random.nextDouble() * totalDistance
        val nextCentroid = points[distances.indexOfFirst { it >= threshold }].rgb
        centroids.add(nextCentroid)
    }

    val clusters = centroids.map { Cluster(it) }

    var iteration = 0
    var changed: Boolean

    do {
        clusters.forEach { it.points.clear() }

        for (point in points) {
            val nearestCluster = clusters.minByOrNull {
                point.rgb.distanceTo(it.centroid)
            } ?: continue
            nearestCluster.points.add(point)
        }

        changed = false

        for (cluster in clusters) {
            if (cluster.points.isEmpty()) continue

            val totalWeight = cluster.points.sumOf { it.weight }
            val newCentroid = RGBColor(
                r = (cluster.points.sumOf { it.rgb.r * it.weight } / totalWeight).toInt(),
                g = (cluster.points.sumOf { it.rgb.g * it.weight } / totalWeight).toInt(),
                b = (cluster.points.sumOf { it.rgb.b * it.weight } / totalWeight).toInt(),
            )

            if (newCentroid.distanceTo(cluster.centroid) > 0.1) {
                changed = true
                cluster.centroid = newCentroid
            }
        }

        iteration++
    } while (changed && iteration < maxIterations)

    return clusters
}
