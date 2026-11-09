package com.example.jeho

data class Trip(
    val title: String,
    val startTime: String,
    val endTime: String,
    val distanceKm: Double,
    val avgSpeedKmh: Int,
    val story: String = "",
    // (위도, 경도) 순서의 경로 좌표 목록
    val route: List<Pair<Double, Double>> = emptyList(),
    // 여행 날짜(밀리초). 0이면 카드가 오늘 날짜를 씁니다.
    val dateMillis: Long = 0L,
    // 사용자가 고른 날씨와 오늘의 노래
    val weather: String = "",
    val song: String = "",
    // 운전 습관 (위치 변화로 계산한 추정값). 계산하지 못하면 점수는 -1
    val maxSpeedKmh: Int = 0,
    val hardAccels: Int = 0,
    val hardBrakes: Int = 0,
    val safetyScore: Int = -1,
    val ecoScore: Int = -1,
    // 차량 연비(km/L), 전비(km/kWh). 0이면 측정값 없음
    val fuelEconomy: Double = 0.0,
    val evEfficiency: Double = 0.0
)

interface TripDataSource {
    fun startTrip(onDone: () -> Unit, onError: (String) -> Unit) {
        onDone()
    }

    fun getLatestTrip(onResult: (Trip) -> Unit, onError: (String) -> Unit)

    fun release() {}
}