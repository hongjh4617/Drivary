package com.example.jeho

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VehicleTripDataSource(context: Context) : TripDataSource {

    private val vehicle = Vehicle(context)
    private val prefs = context.getSharedPreferences("gililgi", Context.MODE_PRIVATE)

    init {
        vehicle.initialize()
    }

    // 총 주행거리계(km)를 읽습니다.
    private fun readOdometerKm(onResult: (Float) -> Unit, onError: (String) -> Unit) {
        vehicle.getOdometer().getCurrentDistanceDriven(
            onComplete = { km: Float? ->
                if (km == null) onError("주행거리계 값이 없습니다") else onResult(km)
            },
            onFailed = { e: Exception ->
                onError("주행거리계 조회 실패: ${e.message}")
            }
        )
    }

    override fun startTrip(onDone: () -> Unit, onError: (String) -> Unit) {
        readOdometerKm(
            onResult = { km ->
                prefs.edit()
                    .putFloat(KEY_START_KM, km)
                    .putLong(KEY_START_TIME, System.currentTimeMillis())
                    .apply()
                onDone()
            },
            onError = onError
        )
    }

    override fun getLatestTrip(onResult: (Trip) -> Unit, onError: (String) -> Unit) {
        if (!prefs.contains(KEY_START_TIME)) {
            onError("먼저 '주행 시작'을 눌러 주세요")
            return
        }
        val startKm = prefs.getFloat(KEY_START_KM, 0f)
        val startMillis = prefs.getLong(KEY_START_TIME, 0L)

        readOdometerKm(
            onResult = { endKm ->
                onResult(buildTrip(startKm, endKm, startMillis, System.currentTimeMillis()))
            },
            onError = onError
        )
    }

    private fun buildTrip(startKm: Float, endKm: Float, startMillis: Long, endMillis: Long): Trip {
        val distanceKm = maxOf(0.0, (endKm - startKm).toDouble())
        val hours = (endMillis - startMillis) / 3_600_000.0
        val avgSpeed = if (hours > 0) (distanceKm / hours).toInt() else 0

        val format = SimpleDateFormat("HH:mm", Locale.KOREA)
        return Trip(
            title = "오늘의 드라이브",
            startTime = format.format(Date(startMillis)),
            endTime = format.format(Date(endMillis)),
            distanceKm = Math.round(distanceKm * 10) / 10.0,
            avgSpeedKmh = avgSpeed
        )
    }

    override fun release() {
        vehicle.release()
    }

    companion object {
        private const val KEY_START_KM = "start_km"
        private const val KEY_START_TIME = "start_time"
    }
}