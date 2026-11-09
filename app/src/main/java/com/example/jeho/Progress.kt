package com.example.jeho

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import kotlin.math.floor
import kotlin.math.min

// 여행이 일어난 시각(밀리초). 날짜가 없으면 저장 번호(id)를 씁니다.
fun SavedTrip.whenMillis(): Long = if (trip.dateMillis > 0) trip.dateMillis else id

// ---------------- 탐험: 지도를 약 1km 격자로 나눠 처음 지나간 구역을 셉니다 ----------------
object Exploration {
    private const val CELL = 0.01 // 위도·경도 0.01도 = 약 1km

    fun cellsOf(t: Trip): Set<Long> {
        val out = HashSet<Long>()
        for ((lat, lon) in t.route) {
            val a = floor(lat / CELL).toInt()
            val b = floor(lon / CELL).toInt()
            out.add((a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL))
        }
        return out
    }

    // 시간순으로 훑어서, 각 여행이 처음 밟은 구역 수를 구합니다. (키: 여행 id)
    fun newCellsByTrip(all: List<SavedTrip>): Map<Long, Int> {
        val seen = HashSet<Long>()
        val result = HashMap<Long, Int>()
        for (s in all.sortedBy { it.whenMillis() }) {
            val cells = cellsOf(s.trip)
            result[s.id] = cells.count { it !in seen }
            seen.addAll(cells)
        }
        return result
    }

    fun totalZones(all: List<SavedTrip>): Int {
        val seen = HashSet<Long>()
        all.forEach { seen.addAll(cellsOf(it.trip)) }
        return seen.size
    }
}

// ---------------- 레벨, 챌린지, 기록 ----------------
data class LevelInfo(val level: Int, val name: String, val xp: Int, val xpInLevel: Int, val xpToNext: Int)

data class Challenge(val title: String, val desc: String, val current: Int, val target: Int) {
    val done: Boolean get() = current >= target
}

data class ExploreInfo(val total: Int, val monthNew: Int, val weekNew: Int)

data class Records(val bestSafety: Int, val bestEco: Int, val weekAvg: Int, val lastWeekAvg: Int)

object Progress {

    // 한 번의 주행으로 얻는 XP. 거리와 속도는 반영하지 않습니다.
    fun tripXp(t: Trip, newZones: Int): Int {
        var xp = 10
        xp += when {
            t.safetyScore >= 90 -> 15
            t.safetyScore >= 80 -> 8
            else -> 0
        }
        xp += when {
            t.ecoScore >= 90 -> 10
            t.ecoScore >= 80 -> 5
            else -> 0
        }
        xp += min(newZones, 5) * 8
        return xp
    }

    // 레벨 n에 도달하는 데 필요한 누적 XP = 25 * n * (n - 1)
    fun level(all: List<SavedTrip>): LevelInfo {
        val fresh = Exploration.newCellsByTrip(all)
        val xp = all.sumOf { tripXp(it.trip, fresh[it.id] ?: 0) }
        var lv = 1
        while (25 * (lv + 1) * lv <= xp) lv++
        val base = 25 * lv * (lv - 1)
        return LevelInfo(lv, levelName(lv), xp, xp - base, 50 * lv)
    }

    private fun levelName(lv: Int): String = when {
        lv >= 10 -> "드라이브 마스터"
        lv >= 7 -> "길 위의 여행자"
        lv >= 4 -> "도시 드라이버"
        lv >= 2 -> "동네 탐험가"
        else -> "새내기 드라이버"
    }

    // 이번 주(월요일 시작)의 챌린지 3개
    fun challenges(all: List<SavedTrip>, now: Long = System.currentTimeMillis()): List<Challenge> {
        val start = weekStart(now)
        val week = all.filter { it.whenMillis() >= start }
        val fresh = Exploration.newCellsByTrip(all)
        val safe = week.count { it.trip.safetyScore >= 90 }
        val smooth = week.count {
            it.trip.safetyScore >= 0 && it.trip.hardAccels == 0 && it.trip.hardBrakes == 0
        }
        val zones = week.sumOf { fresh[it.id] ?: 0 }
        return listOf(
            Challenge("안전 운전 3번", "안전 점수 90점 이상으로 3번 주행하기", safe, 3),
            Challenge("부드러운 운전 2번", "급가속·급감속 없이 2번 주행하기", smooth, 2),
            Challenge("새 구역 3곳 탐험", "이번 주에 처음 가 본 구역 3곳 채우기", zones, 3)
        )
    }

    fun explore(all: List<SavedTrip>, now: Long = System.currentTimeMillis()): ExploreInfo {
        val fresh = Exploration.newCellsByTrip(all)
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val y = nowCal.get(Calendar.YEAR)
        val m = nowCal.get(Calendar.MONTH)
        val start = weekStart(now)
        val c = Calendar.getInstance()
        var monthNew = 0
        var weekNew = 0
        for (s in all) {
            val n = fresh[s.id] ?: 0
            c.timeInMillis = s.whenMillis()
            if (c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m) monthNew += n
            if (s.whenMillis() >= start) weekNew += n
        }
        return ExploreInfo(Exploration.totalZones(all), monthNew, weekNew)
    }

    fun records(all: List<SavedTrip>, now: Long = System.currentTimeMillis()): Records {
        val start = weekStart(now)
        val prev = weekStart(now, 1)
        val scored = all.filter { it.trip.safetyScore >= 0 }
        fun avg(list: List<Int>): Int = if (list.isEmpty()) -1 else list.average().toInt()
        return Records(
            bestSafety = all.maxOfOrNull { it.trip.safetyScore } ?: -1,
            bestEco = all.maxOfOrNull { it.trip.ecoScore } ?: -1,
            weekAvg = avg(scored.filter { it.whenMillis() >= start }.map { it.trip.safetyScore }),
            lastWeekAvg = avg(scored.filter { it.whenMillis() in prev until start }.map { it.trip.safetyScore })
        )
    }

    // 이번 주 월요일 0시. weeksBack = 1이면 지난주 월요일
    private fun weekStart(now: Long, weeksBack: Long = 0): Long {
        val zone = ZoneId.systemDefault()
        val monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(weeksBack)
        return monday.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}