package com.example.jeho

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedTrip(val id: Long, val trip: Trip)

// 여행 기록을 기기 안의 파일에 저장합니다. 서버로 보내지 않습니다.
class TripRepository(context: Context) {

    private val file = File(context.filesDir, "trips.json")

    @Synchronized
    fun loadAll(): List<SavedTrip> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { toSaved(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 같은 id가 있으면 덮어쓰고, 없으면 새로 추가합니다.
    @Synchronized
    fun save(id: Long, trip: Trip) {
        write(loadAll().filter { it.id != id } + SavedTrip(id, trip))
    }

    @Synchronized
    fun delete(id: Long) {
        write(loadAll().filter { it.id != id })
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun write(list: List<SavedTrip>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        // 임시 파일에 먼저 쓰고 바꿔서, 쓰는 도중 꺼져도 기존 기록이 깨지지 않게 합니다.
        val tmp = File(file.parentFile, "trips.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    private fun toJson(s: SavedTrip): JSONObject {
        val t = s.trip
        val route = JSONArray()
        t.route.forEach { route.put(JSONArray().put(it.first).put(it.second)) }
        return JSONObject()
            .put("id", s.id)
            .put("title", t.title)
            .put("startTime", t.startTime)
            .put("endTime", t.endTime)
            .put("distanceKm", t.distanceKm)
            .put("avgSpeedKmh", t.avgSpeedKmh)
            .put("story", t.story)
            .put("dateMillis", t.dateMillis)
            .put("weather", t.weather)
            .put("song", t.song)
            .put("maxSpeedKmh", t.maxSpeedKmh)
            .put("hardAccels", t.hardAccels)
            .put("hardBrakes", t.hardBrakes)
            .put("safetyScore", t.safetyScore)
            .put("ecoScore", t.ecoScore)
            .put("fuelEconomy", t.fuelEconomy)
            .put("evEfficiency", t.evEfficiency)
            .put("route", route)
    }

    private fun toSaved(o: JSONObject): SavedTrip {
        val routeArr = o.optJSONArray("route")
        val route = mutableListOf<Pair<Double, Double>>()
        if (routeArr != null) {
            for (i in 0 until routeArr.length()) {
                val p = routeArr.getJSONArray(i)
                route.add(Pair(p.getDouble(0), p.getDouble(1)))
            }
        }
        // 예전에 저장한 기록에는 새 항목이 없어서 기본값을 씁니다.
        val trip = Trip(
            title = o.optString("title"),
            startTime = o.optString("startTime"),
            endTime = o.optString("endTime"),
            distanceKm = o.optDouble("distanceKm"),
            avgSpeedKmh = o.optInt("avgSpeedKmh"),
            story = o.optString("story"),
            route = route,
            dateMillis = o.optLong("dateMillis"),
            weather = o.optString("weather"),
            song = o.optString("song"),
            maxSpeedKmh = o.optInt("maxSpeedKmh", 0),
            hardAccels = o.optInt("hardAccels", 0),
            hardBrakes = o.optInt("hardBrakes", 0),
            safetyScore = o.optInt("safetyScore", -1),
            ecoScore = o.optInt("ecoScore", -1),
            fuelEconomy = o.optDouble("fuelEconomy", 0.0),
            evEfficiency = o.optDouble("evEfficiency", 0.0)
        )
        return SavedTrip(o.getLong("id"), trip)
    }
}