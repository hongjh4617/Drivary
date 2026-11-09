package com.example.jeho

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min

// 여러 경로를 같은 비율로 영역 안에 맞춥니다. (경도는 cos(위도)로 보정)
fun projectRoutes(routes: List<List<Pair<Double, Double>>>, area: RectF): List<List<PointF>> {
    val all = routes.flatten()
    if (all.isEmpty()) return routes.map { emptyList() }

    val minLat = all.minOf { it.first }
    val maxLat = all.maxOf { it.first }
    val minLon = all.minOf { it.second }
    val maxLon = all.maxOf { it.second }
    val k = cos(Math.toRadians((minLat + maxLat) / 2.0))
    val w = (maxLon - minLon) * k
    val h = maxLat - minLat
    val aw = area.width().toDouble()
    val ah = area.height().toDouble()
    val scale = when {
        w <= 1e-9 && h <= 1e-9 -> 0.0
        w <= 1e-9 -> ah / h
        h <= 1e-9 -> aw / w
        else -> min(aw / w, ah / h)
    }
    val ox = area.centerX().toDouble() - (w * scale) / 2.0
    val oy = area.centerY().toDouble() - (h * scale) / 2.0

    return routes.map { route ->
        route.map {
            PointF(
                (ox + (it.second - minLon) * k * scale).toFloat(),
                (oy + (maxLat - it.first) * scale).toFloat()
            )
        }
    }
}