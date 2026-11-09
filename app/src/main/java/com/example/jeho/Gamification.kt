package com.example.jeho

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

// "HH:mm" 형태의 출발 시각에서 시(hour)만 꺼냅니다. 읽지 못하면 -1
fun Trip.startHour(): Int = startTime.split(":").firstOrNull()?.trim()?.toIntOrNull() ?: -1

// ---------------- 드라이브 칭호 ----------------
object DriveTitles {
    fun of(t: Trip): String {
        val h = t.startHour()
        return when {
            t.safetyScore >= 95 && t.distanceKm >= 5 -> "모범 운전자"
            t.distanceKm >= 100 -> "장거리 여행자"
            t.weather == "눈" -> "눈길 모험가"
            t.weather == "비" -> "빗길 낭만파"
            h in 4..6 -> "새벽 드라이버"
            h >= 22 || h in 0..3 -> "심야 드라이버"
            h in 17..18 -> "노을 추격자"
            t.distanceKm < 3 -> "동네 한 바퀴"
            else -> "오늘의 드라이버"
        }
    }
}

// ---------------- 배지 ----------------
data class BadgeStatus(val name: String, val desc: String, val earned: Boolean, val progress: String)

object Badges {

    fun evaluate(all: List<SavedTrip>): List<BadgeStatus> {
        val trips = all.map { it.trip }
        val n = trips.size
        // 많이 달릴수록 보상이 되지 않도록, 안전 점수 90점 이상인 주행만 셉니다.
        val safeKm = trips.filter { it.safetyScore >= 90 }.sumOf { it.distanceKm }
        val longest = trips.maxOfOrNull { it.distanceKm } ?: 0.0
        val bestSafety = trips.maxOfOrNull { it.safetyScore } ?: -1
        val streak = longestStreak(all)
        val zones = Exploration.totalZones(all)

        fun km(v: Double) = String.format(Locale.US, "%.0f", v)

        return listOf(
            BadgeStatus("첫 발걸음", "첫 여행을 기록했어요", n >= 1, "${minOf(n, 1)} / 1회"),
            BadgeStatus("열 번째 여행", "여행을 10번 기록했어요", n >= 10, "${minOf(n, 10)} / 10회"),
            BadgeStatus("안전 100km 클럽", "안전 점수 90점 이상 주행으로 누적 100km", safeKm >= 100, "${km(minOf(safeKm, 100.0))} / 100 km"),
            BadgeStatus("안전 500km 클럽", "안전 점수 90점 이상 주행으로 누적 500km", safeKm >= 500, "${km(minOf(safeKm, 500.0))} / 500 km"),
            BadgeStatus("안전 1,000km 클럽", "안전 점수 90점 이상 주행으로 누적 1,000km", safeKm >= 1000, "${km(minOf(safeKm, 1000.0))} / 1,000 km"),
            BadgeStatus("3일 연속", "3일 연속으로 기록했어요", streak >= 3, "${minOf(streak, 3)} / 3일"),
            BadgeStatus("장거리 여행자", "한 번에 50km 이상 달렸어요", longest >= 50, "${km(minOf(longest, 50.0))} / 50 km"),
            BadgeStatus("새벽 드라이버", "새벽 4~7시에 출발했어요", trips.any { it.startHour() in 4..6 }, "새벽 출발 기록 없음"),
            BadgeStatus("심야 드라이버", "밤 10시 이후에 출발했어요", trips.any { it.startHour() >= 22 || it.startHour() in 0..3 }, "심야 출발 기록 없음"),
            BadgeStatus("빗길 낭만파", "비 오는 날 기록했어요", trips.any { it.weather == "비" }, "비 오는 날 기록 없음"),
            BadgeStatus(
                "모범 운전자", "안전 점수 90점 이상",
                bestSafety >= 90,
                if (bestSafety >= 0) "최고 $bestSafety / 90점" else "점수 기록 없음"
            ),
            BadgeStatus("동네 탐험가", "서로 다른 구역 5곳을 가 봤어요", zones >= 5, "${minOf(zones, 5)} / 5곳"),
            BadgeStatus("도시 탐험가", "서로 다른 구역 20곳을 가 봤어요", zones >= 20, "${minOf(zones, 20)} / 20곳")
        )
    }

    private fun dateOf(s: SavedTrip): LocalDate {
        val ms = if (s.trip.dateMillis > 0) s.trip.dateMillis else s.id
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun longestStreak(all: List<SavedTrip>): Int {
        val days = all.map { dateOf(it) }.distinct().sortedBy { it.toEpochDay() }
        if (days.isEmpty()) return 0
        var best = 1
        var cur = 1
        for (i in 1 until days.size) {
            if (days[i] == days[i - 1].plusDays(1)) {
                cur++
                best = maxOf(best, cur)
            } else {
                cur = 1
            }
        }
        return best
    }
}

// ---------------- 월간 리캡 ----------------
data class MonthlyRecap(
    val year: Int,
    val month: Int,
    val count: Int,
    val totalKm: Double,
    val longestKm: Double,
    val avgSafety: Int,
    val topTitle: String,
    val topSong: String,
    val routes: List<List<Pair<Double, Double>>>
) {
    companion object {
        fun of(all: List<SavedTrip>, year: Int, month: Int): MonthlyRecap {
            val cal = Calendar.getInstance()
            val list = all.filter {
                cal.timeInMillis = if (it.trip.dateMillis > 0) it.trip.dateMillis else it.id
                cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) + 1 == month
            }
            val trips = list.map { it.trip }
            val scores = trips.map { it.safetyScore }.filter { it >= 0 }
            val topTitle = trips.map { DriveTitles.of(it) }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""
            val topSong = trips.map { it.song.trim() }.filter { it.isNotEmpty() }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""
            return MonthlyRecap(
                year = year,
                month = month,
                count = trips.size,
                totalKm = trips.sumOf { it.distanceKm },
                longestKm = trips.maxOfOrNull { it.distanceKm } ?: 0.0,
                avgSafety = if (scores.isEmpty()) -1 else scores.average().toInt(),
                topTitle = topTitle,
                topSong = topSong,
                routes = trips.map { it.route }.filter { it.size >= 2 }
            )
        }
    }
}