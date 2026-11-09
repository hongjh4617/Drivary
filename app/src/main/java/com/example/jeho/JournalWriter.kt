package com.example.jeho

// 일지 문장을 만드는 부분. 나중에 Gleo AI 버전을 추가해서 바꿔 끼우면 됩니다.
interface JournalWriter {
    fun write(trip: Trip, onResult: (String) -> Unit)
}

class TemplateJournalWriter : JournalWriter {
    override fun write(trip: Trip, onResult: (String) -> Unit) {
        val hour = trip.startTime.substringBefore(":").toIntOrNull() ?: 12
        val part = when (hour) {
            in 5..10 -> "아침"
            in 11..16 -> "낮"
            in 17..20 -> "저녁"
            else -> "밤"
        }
        val km = String.format("%.1f", trip.distanceKm)
        val text = when {
            trip.distanceKm < 5 -> "${part}에 가볍게 한 바퀴, ${km}km를 달렸어요."
            trip.distanceKm < 50 -> "${part} ${trip.startTime}에 출발해 ${km}km. 가까운 길을 여유롭게 달렸어요."
            trip.distanceKm < 150 -> "${part}에 떠난 ${km}km의 드라이브. 창밖 풍경이 오래 남을 하루였어요."
            else -> "${part}에 시작한 긴 여정, ${km}km. 먼 길을 달려 새로운 곳에 닿았어요."
        }
        onResult(text)
    }
}