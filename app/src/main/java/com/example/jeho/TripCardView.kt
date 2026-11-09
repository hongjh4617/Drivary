package com.example.jeho

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 여행 카드. 앞면은 일지, 뒷면은 운전 리포트입니다.
class TripCardView(context: Context) : BaseCardView(context) {

    var trip: Trip? = null
        set(value) {
            field = value
            invalidate()
        }

    // true면 뒷면(운전 리포트)을 그립니다.
    var showBack = false
        set(value) {
            field = value
            invalidate()
        }

    override fun headerDate(): String {
        val millis = trip?.dateMillis?.takeIf { it > 0 } ?: System.currentTimeMillis()
        return SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREA).format(Date(millis))
    }

    override fun drawContent(canvas: Canvas) {
        val t = trip
        when {
            t == null -> {
                drawText(canvas, "여행을 기록하면", 50f, 60f, 4.6f, gray, align = Paint.Align.CENTER)
                drawText(canvas, "이곳에 카드가 만들어져요", 50f, 67f, 4.6f, gray, align = Paint.Align.CENTER)
            }
            showBack -> drawBack(canvas, t)
            else -> drawFront(canvas, t)
        }
    }

    // ---------------- 앞면 ----------------

    private fun drawFront(canvas: Canvas, t: Trip) {
        drawChip(canvas, DriveTitles.of(t), 7f, 15f)
        drawText(canvas, t.title, 7f, 30f, 8f, ink, bold = true)

        val timeLine = if (t.weather.isBlank()) {
            "${t.startTime}  →  ${t.endTime}"
        } else {
            "${t.startTime}  →  ${t.endTime}   ·   ${t.weather}"
        }
        drawText(canvas, timeLine, 7f, 37f, 3.6f, gray)

        // 일지 문장 (최대 3줄)
        val lines = storyLines(t.story)
        lines.forEachIndexed { i, line ->
            drawText(canvas, line, 7f, 46f + i * 5.6f, 3.8f, ink)
        }

        // 경로 영역
        val routeTop = if (lines.isEmpty()) 46f else 46f + lines.size * 5.6f + 3f
        val area = RectF(12f, routeTop, 88f, 79f)
        if (t.route.size >= 2) {
            drawRoute(canvas, t.route, area)
        } else {
            drawText(canvas, "경로 없음", 50f, (area.top + area.bottom) / 2f, 3.6f, gray, align = Paint.Align.CENTER)
        }

        // 하단 유리 패널
        drawGlass(canvas, 5f, 83f, 95f, 121f)

        drawText(canvas, "총 주행 거리", 9f, 90f, 3.2f, gray)
        if (t.song.isNotBlank()) {
            drawText(canvas, fit("♪ " + t.song.trim(), 44f, 3.2f), 91f, 90f, 3.2f, gray, align = Paint.Align.RIGHT)
        }

        val dist = String.format(Locale.US, "%.1f", t.distanceKm)
        drawText(canvas, dist, 9f, 103f, 14f, ink, bold = true)
        val distW = textPaint.measureText(dist)
        drawText(canvas, "km", 9f + distW + 1.5f, 103f, 5f, sky, bold = true)

        linePaint.color = Color.argb(40, 255, 255, 255)
        linePaint.strokeWidth = 0.25f
        canvas.drawLine(9f, 107f, 91f, 107f, linePaint)

        drawText(canvas, "평균 속도", 9f, 112.5f, 3f, gray)
        drawText(canvas, "${t.avgSpeedKmh} km/h", 9f, 118f, 4.4f, ink, bold = true)
        drawText(canvas, "주행 시간", 52f, 112.5f, 3f, gray)
        drawText(canvas, durationText(t.startTime, t.endTime), 52f, 118f, 4.4f, ink, bold = true)
    }

    // 제목 위의 칭호 알약
    private fun drawChip(canvas: Canvas, text: String, x: Float, y: Float) {
        textPaint.textSize = 3.2f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        val w = textPaint.measureText(text) + 5f
        fillPaint.color = AppTheme.accentSoft
        canvas.drawRoundRect(RectF(x, y, x + w, y + 5.6f), 2.8f, 2.8f, fillPaint)
        drawText(canvas, text, x + 2.5f, y + 4f, 3.2f, sky, bold = true)
    }

    // ---------------- 뒷면 ----------------

    private fun drawBack(canvas: Canvas, t: Trip) {
        drawText(canvas, "드라이브 리포트", 7f, 30f, 8f, ink, bold = true)
        drawText(canvas, t.title, 7f, 37f, 3.6f, gray)

        drawRing(canvas, 30f, 58f, 14f, t.safetyScore, "안전 점수")
        drawRing(canvas, 70f, 58f, 14f, t.ecoScore, "에코 점수")
        if (t.safetyScore < 0) {
            drawText(canvas, "경로를 기록한 주행에서 계산돼요", 50f, 85f, 3f, gray, align = Paint.Align.CENTER)
        }

        drawGlass(canvas, 5f, 90f, 95f, 121f)

        val scored = t.safetyScore >= 0
        // 전비가 있으면 전비, 없고 연비가 있으면 연비, 둘 다 없으면 주행 시간
        val energyCell = when {
            t.evEfficiency > 0 -> Pair("전비", String.format(Locale.US, "%.1f km/kWh", t.evEfficiency))
            t.fuelEconomy > 0 -> Pair("연비", String.format(Locale.US, "%.1f km/L", t.fuelEconomy))
            else -> Pair("주행 시간", durationText(t.startTime, t.endTime))
        }
        val cells = listOf(
            Pair("최고 속도", if (t.maxSpeedKmh > 0) "${t.maxSpeedKmh} km/h" else "—"),
            energyCell,
            Pair("급가속", if (scored) "${t.hardAccels}회" else "—"),
            Pair("급감속", if (scored) "${t.hardBrakes}회" else "—")
        )

        cells.forEachIndexed { i, cell ->
            val x = if (i % 2 == 0) 9f else 52f
            val y = if (i < 2) 97f else 111.5f
            drawText(canvas, cell.first, x, y, 3f, gray)
            drawText(canvas, cell.second, x, y + 6.5f, 4.6f, ink, bold = true)
        }
    }

    // 점수를 원형 링으로 그립니다. 점수가 -1이면 빈 링과 "—"를 보여줍니다.
    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, r: Float, score: Int, label: String) {
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        linePaint.strokeWidth = 2.6f
        linePaint.color = Color.argb(40, 255, 255, 255)
        canvas.drawArc(oval, 0f, 360f, false, linePaint)
        if (score >= 0) {
            linePaint.color = if (score >= 60) sky else AppTheme.danger
            canvas.drawArc(oval, -90f, 360f * score / 100f, false, linePaint)
        }
        drawText(
            canvas, if (score >= 0) "$score" else "—",
            cx, cy + 3.2f, 10f, ink, bold = true, align = Paint.Align.CENTER
        )
        drawText(canvas, label, cx, cy + r + 7f, 3.2f, gray, align = Paint.Align.CENTER)
    }

    // ---------------- 경로 ----------------

    private fun drawRoute(canvas: Canvas, route: List<Pair<Double, Double>>, area: RectF) {
        val pts = projectRoutes(listOf(route), area)[0]
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }

        // 빛 번짐 + 본선
        linePaint.color = Color.argb(60, 90, 169, 255)
        linePaint.strokeWidth = 3.4f
        canvas.drawPath(path, linePaint)
        linePaint.color = sky
        linePaint.strokeWidth = 1f
        canvas.drawPath(path, linePaint)

        val start = pts.first()
        val end = pts.last()

        // 출발: 속이 빈 원
        fillPaint.color = Color.parseColor("#07122A")
        canvas.drawCircle(start.x, start.y, 2.2f, fillPaint)
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = 0.8f
        canvas.drawCircle(start.x, start.y, 2.2f, linePaint)

        // 도착: 파란 원
        fillPaint.color = accent
        canvas.drawCircle(end.x, end.y, 2.2f, fillPaint)
        canvas.drawCircle(end.x, end.y, 2.2f, linePaint)

        drawText(canvas, "출발", start.x.coerceIn(10f, 90f), start.y - 3.6f, 2.8f, gray, align = Paint.Align.CENTER)
        drawText(canvas, "도착", end.x.coerceIn(10f, 90f), end.y + 6f, 2.8f, gray, align = Paint.Align.CENTER)
    }

    // ---------------- 글자 ----------------

    private fun storyLines(story: String): List<String> {
        if (story.isBlank()) return emptyList()
        textPaint.textSize = 3.8f
        textPaint.typeface = Typeface.DEFAULT
        val all = wrapLines(story, 86f)
        if (all.size <= 3) return all
        val lines = all.take(3).toMutableList()
        var last = lines[2]
        while (last.isNotEmpty() && textPaint.measureText("$last…") > 86f) last = last.dropLast(1)
        lines[2] = last.trimEnd() + "…"
        return lines
    }

    private fun durationText(start: String, end: String): String {
        return try {
            val (sh, sm) = start.trim().split(":").map { it.trim().toInt() }
            val (eh, em) = end.trim().split(":").map { it.trim().toInt() }
            var d = (eh * 60 + em) - (sh * 60 + sm)
            if (d < 0) d += 24 * 60
            if (d < 60) "${d}분" else "${d / 60}시간 ${d % 60}분"
        } catch (e: Exception) {
            "-"
        }
    }
}