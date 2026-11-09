package com.example.jeho

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.Locale

// 월간 리캡 카드. 그달의 모든 경로를 한 장에 겹쳐서 보여줍니다.
class RecapCardView(context: Context) : BaseCardView(context) {

    var recap: MonthlyRecap? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun headerDate(): String {
        val r = recap ?: return ""
        return String.format(Locale.US, "%d.%02d", r.year, r.month)
    }

    override fun drawContent(canvas: Canvas) {
        val r = recap ?: return

        drawText(canvas, "${r.month}월의 드라이브", 7f, 30f, 8f, ink, bold = true)
        val sub = if (r.count == 0) {
            "이번 달 기록이 아직 없어요"
        } else {
            "${r.count}번 달렸어요" + if (r.topSong.isEmpty()) "" else "  ·  ♪ ${r.topSong}"
        }
        drawText(canvas, fit(sub, 86f, 3.6f), 7f, 37f, 3.6f, gray)

        // 이번 달의 모든 경로
        val area = RectF(10f, 42f, 90f, 80f)
        if (r.routes.isNotEmpty()) {
            val projected = projectRoutes(r.routes, area)
            projected.forEach { pts ->
                if (pts.size >= 2) {
                    val path = Path()
                    pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                    linePaint.color = Color.argb(40, 90, 169, 255)
                    linePaint.strokeWidth = 2.6f
                    canvas.drawPath(path, linePaint)
                    linePaint.color = Color.argb(210, 90, 169, 255)
                    linePaint.strokeWidth = 0.8f
                    canvas.drawPath(path, linePaint)
                }
            }
        } else {
            val msg = if (r.count == 0) "달리면 이곳에 내 길이 그려져요" else "경로가 기록된 여행이 없어요"
            drawText(canvas, msg, 50f, 61f, 3.6f, gray, align = Paint.Align.CENTER)
        }

        // 하단 요약 패널
        drawGlass(canvas, 5f, 83f, 95f, 121f)

        drawText(canvas, "총 주행 거리", 9f, 90f, 3.2f, gray)
        if (r.topTitle.isNotEmpty()) {
            drawText(canvas, "이달의 칭호", 91f, 90f, 3.2f, gray, align = Paint.Align.RIGHT)
            drawText(canvas, fit(r.topTitle, 34f, 4.4f, true), 91f, 97f, 4.4f, sky, bold = true, align = Paint.Align.RIGHT)
        }

        val km = String.format(Locale.US, "%.1f", r.totalKm)
        drawText(canvas, km, 9f, 104f, 13f, ink, bold = true)
        val kmW = textPaint.measureText(km)
        drawText(canvas, "km", 9f + kmW + 1.5f, 104f, 5f, sky, bold = true)

        linePaint.color = Color.argb(40, 255, 255, 255)
        linePaint.strokeWidth = 0.25f
        canvas.drawLine(9f, 108f, 91f, 108f, linePaint)

        drawText(canvas, "주행 횟수", 9f, 113.3f, 3f, gray)
        drawText(canvas, "${r.count}회", 9f, 118.6f, 4.2f, ink, bold = true)
        drawText(canvas, "최장 거리", 38f, 113.3f, 3f, gray)
        drawText(canvas, String.format(Locale.US, "%.1f km", r.longestKm), 38f, 118.6f, 4.2f, ink, bold = true)
        drawText(canvas, "안전 점수", 67f, 113.3f, 3f, gray)
        drawText(canvas, if (r.avgSafety >= 0) "${r.avgSafety}" else "—", 67f, 118.6f, 4.2f, ink, bold = true)
    }
}