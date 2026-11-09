package com.example.jeho

import android.location.Location
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

    private var lastT = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastSpeed = -1.0
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
        lastT = 0L
        lastLat = 0.0
        lastLon = 0.0
        lastSpeed = -1.0
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
        if (lastT != 0L) {
            val dt = (nowMillis - lastT) / 1000.0
            if (dt in 0.5..30.0) {
                val r = FloatArray(1)
                Location.distanceBetween(lastLat, lastLon, lat, lon, r)
                val v = r[0] / dt // m/s
                // 시속 250km가 넘으면 위치가 튄 것으로 보고 버립니다.
                if (v <= 70.0) {
                    if (lastSpeed >= 0) {
                        val a = (v - lastSpeed) / dt
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
                    }
                    if (v * 3.6 > OVERSPEED_KMH) overspeedSec += dt
                    maxSpeed = max(maxSpeed, v)
                    movedMeters += r[0]
                    lastSpeed = v
                    samples++
                }
            } else {
                lastSpeed = -1.0 // 간격이 너무 길면 가속도 계산을 다시 시작합니다.
            }
        }
        lastT = nowMillis
        lastLat = lat
        lastLon = lon
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
    }
}