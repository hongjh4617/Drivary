package com.example.jeho

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// 주행 종료 때 차량의 이번 주행 에너지 효율을 한 번 읽습니다.
// 내연기관차는 연비(km/L), 전기차는 전비(km/kWh). 읽지 못한 값은 0.0입니다.
class FuelReader(context: Context) {

    data class Efficiency(val kmPerLiter: Double, val kmPerKwh: Double)

    private val vehicle = Vehicle(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false

    private fun ensureReady(): Boolean {
        if (ready) return true
        return try {
            vehicle.initialize()
            ready = true
            true
        } catch (e: Exception) {
            false
        }
    }

    // 연비와 전비를 동시에 요청하고, 둘 다 답하거나 2초가 지나면 결과를 돌려줍니다.
    // 콜백이 어느 스레드에서 올 수 있으니, 화면을 바꾸는 쪽에서 runOnUiThread를 쓰세요.
    fun read(onResult: (Efficiency) -> Unit) {
        if (!ensureReady()) {
            onResult(Efficiency(0.0, 0.0))
            return
        }

        val fuel = AtomicReference(0.0)
        val ev = AtomicReference(0.0)
        val done = AtomicBoolean(false)
        val pending = AtomicInteger(2)

        fun deliver() {
            if (done.compareAndSet(false, true)) onResult(Efficiency(fuel.get(), ev.get()))
        }
        fun oneFinished() {
            if (pending.decrementAndGet() == 0) deliver()
        }

        handler.postDelayed({ deliver() }, 2000)

        try {
            vehicle.getFuel().getCurrentFuelEconomy(
                onComplete = { v ->
                    fuel.set(toKmPerLiter(v))
                    oneFinished()
                },
                onFailed = { oneFinished() }
            )
        } catch (e: Exception) {
            oneFinished()
        }

        try {
            vehicle.getEvBattery().getCurrentEvEfficiency(
                onComplete = { v ->
                    ev.set(toKmPerKwh(v))
                    oneFinished()
                },
                onFailed = { oneFinished() }
            )
        } catch (e: Exception) {
            oneFinished()
        }
    }

    // 연비는 L/100km로 옵니다. 0, 비정상 값은 "없음"으로 처리합니다.
    private fun toKmPerLiter(litersPer100km: Float?): Double {
        if (litersPer100km == null || litersPer100km.isNaN() || litersPer100km <= 0f) return 0.0
        val km = 100.0 / litersPer100km
        return if (km.isFinite() && km in 0.5..80.0) km else 0.0
    }

    // 전비는 km/kWh로 옵니다. 0, 비정상 값은 "없음"으로 처리합니다.
    private fun toKmPerKwh(kmPerKwh: Float?): Double {
        if (kmPerKwh == null || kmPerKwh.isNaN() || kmPerKwh <= 0f) return 0.0
        val v = kmPerKwh.toDouble()
        return if (v in 0.5..30.0) v else 0.0
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        if (ready) {
            try {
                vehicle.release()
            } catch (e: Exception) {
                // 종료 중 오류는 무시합니다.
            }
            ready = false
        }
    }
}