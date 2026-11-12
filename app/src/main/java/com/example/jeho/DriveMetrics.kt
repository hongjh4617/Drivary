package com.example.jeho

import android.location.Location
import android.util.Log
import kotlin.math.abs
import kotlin.math.max

// 위치 기록의 시간 간격으로 속도와 가속도를 구해 운전 습관을 추정합니다.
// 차량 SDK에 속도 값이 없어서 위치 기반으로 계산하며, 실제 계기판 값과는 다를 수 있습니다.
class DriveMetrics {

    data class Result(
        val maxSpeedKmh: Int,
        val hardAccels: Int,
        val hardBrakes: Int,
        val safety: Int,
        val eco: Int
    )

    private class P(val t: Long, val lat: Double, val lon: Double)

    private val pts = mutableListOf<P>()
    private var lastSpeed = -1.0
    private var lastSpeedT = 0L
    private var maxSpeed = 0.0
    private var hardAccels = 0
    private var hardBrakes = 0
    private var sumAbsAccel = 0.0
    private var accelCount = 0
    private var overspeedSec = 0.0
    private var lastEventT = 0L
    private var samples = 0
    private var movedMeters = 0.0

    fun reset() {
        pts.clear()
        lastSpeed = -1.0
        lastSpeedT = 0L
        maxSpeed = 0.0
        hardAccels = 0
        hardBrakes = 0
        sumAbsAccel = 0.0
        accelCount = 0
        overspeedSec = 0.0
        lastEventT = 0L
        samples = 0
        movedMeters = 0.0
    }

    fun add(lat: Double, lon: Double, nowMillis: Long) {
        val last = pts.lastOrNull()
        var seg = 0.0
        if (last != null) {
            val r = FloatArray(1)
            Location.distanceBetween(last.lat, last.lon, lat, lon, r)
            // 위치가 거의 안 바뀌었으면 같은 위치가 반복해서 들어온 것이므로 건너뜁니다.
            if (r[0] < MIN_MOVE_M) return
            val gap = (nowMillis - last.t) / 1000.0
            if (gap < 0.5) return
            if (gap > 30.0) {
                // 간격이 너무 길면 속도 계산을 처음부터 다시 시작합니다.
                pts.clear()
                lastSpeed = -1.0
            } else {
                seg = r[0].toDouble()
            }
        }

        pts.add(P(nowMillis, lat, lon))
        while (pts.size > 2 && nowMillis - pts[0].t > 2 * WINDOW_MS) pts.removeAt(0)
        if (pts.size < 2) return

        // 속도는 약 5초 전 지점과의 거리로 구해서 흔들림을 줄입니다.
        var base = pts[0]
        for (i in pts.size - 2 downTo 0) {
            if (nowMillis - pts[i].t >= WINDOW_MS) {
                base = pts[i]
                break
            }
        }
        val r2 = FloatArray(1)
        Location.distanceBetween(base.lat, base.lon, lat, lon, r2)
        val dt = (nowMillis - base.t) / 1000.0
        val v = r2[0] / dt // m/s

        // 시속 250km가 넘으면 위치가 튄 것으로 보고 버립니다.
        if (v > 70.0) {
            pts.removeAt(pts.size - 1)
            return
        }
        Log.d("DriveMetrics", "v=${"%.1f".format(v)} m/s, seg=${"%.0f".format(seg)} m")

        if (lastSpeed >= 0) {
            val dtv = (nowMillis - lastSpeedT) / 1000.0
            if (dtv > 0.0) {
                val a = (v - lastSpeed) / dtv
                sumAbsAccel += abs(a)
                accelCount++
                if (nowMillis - lastEventT > 3000) {
                    if (a >= HARD_ACCEL) {
                        hardAccels++
                        lastEventT = nowMillis
                    } else if (a <= -HARD_BRAKE) {
                        hardBrakes++
                        lastEventT = nowMillis
                    }
                }
                if (v * 3.6 > OVERSPEED_KMH) overspeedSec += dtv
            }
        }
        maxSpeed = max(maxSpeed, v)
        movedMeters += seg
        lastSpeed = v
        lastSpeedT = nowMillis
        samples++
    }

    fun result(): Result {
        // 기록이 너무 짧으면 점수를 내지 않습니다.
        if (samples < 5 || movedMeters < 300.0) return Result(0, 0, 0, -1, -1)

        val safety = (100 - 6 * hardBrakes - 4 * hardAccels - (overspeedSec / 5.0).toInt())
            .coerceIn(0, 100)
        val meanAbs = if (accelCount > 0) sumAbsAccel / accelCount else 0.0
        val eco = (100 - meanAbs * 30.0).toInt().coerceIn(0, 100)
        return Result((maxSpeed * 3.6).toInt(), hardAccels, hardBrakes, safety, eco)
    }

    private companion object {
        const val HARD_ACCEL = 2.8   // m/s² (약 1초에 시속 10km)
        const val HARD_BRAKE = 3.2
        const val OVERSPEED_KMH = 110.0
        const val MIN_MOVE_M = 2.0f  // 이보다 덜 움직이면 같은 위치로 봅니다.
        const val WINDOW_MS = 5000L  // 속도를 구하는 구간(5초)
    }
}