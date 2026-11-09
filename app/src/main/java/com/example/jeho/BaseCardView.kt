package com.example.jeho

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

// 4:5 카드의 공통 바탕. 가로 100, 세로 125 단위의 좌표계로 그립니다.
abstract class BaseCardView(context: Context) : View(context) {

    // 이미지로 저장할 때는 모서리를 각지게 채웁니다.
    protected var exporting = false

    protected val ink = AppTheme.ink
    protected val gray = AppTheme.gray
    protected val accent = AppTheme.accent
    protected val sky = AppTheme.accentLight

    protected val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // 한글 자간이 틀어지지 않도록 subpixel, linear 옵션을 켭니다.
    protected val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)

    protected abstract fun headerDate(): String
    protected abstract fun drawContent(canvas: Canvas)

    override fun onDraw(canvas: Canvas) {
        drawCard(canvas, width.toFloat())
    }

    fun renderBitmap(widthPx: Int = 1080): Bitmap {
        val h = (widthPx * 1.25f).toInt()
        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
        exporting = true
        try {
            drawCard(Canvas(bmp), widthPx.toFloat())
        } finally {
            exporting = false
        }
        return bmp
    }

    private fun drawCard(canvas: Canvas, widthPx: Float) {
        canvas.save()
        val s = widthPx / 100f
        canvas.scale(s, s)

        val radius = if (exporting) 0f else 6f
        val rect = RectF(0f, 0f, 100f, 125f)

        // 배경: 짙은 남색 + 오른쪽 위 파란 빛
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, 125f,
            intArrayOf(Color.parseColor("#102B55"), Color.parseColor("#07122A"), Color.parseColor("#040A14")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        fillPaint.shader = RadialGradient(
            88f, 8f, 70f,
            Color.argb(120, 47, 123, 255), Color.argb(0, 47, 123, 255), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        fillPaint.shader = null

        // 얇은 테두리
        linePaint.color = Color.argb(46, 255, 255, 255)
        linePaint.strokeWidth = 0.3f
        canvas.drawRoundRect(RectF(0.15f, 0.15f, 99.85f, 124.85f), radius, radius, linePaint)

        drawBrand(canvas)
        drawContent(canvas)
        canvas.restore()
    }

    private fun drawBrand(canvas: Canvas) {
        fillPaint.color = Color.argb(30, 255, 255, 255)
        canvas.drawRoundRect(RectF(7f, 6f, 31f, 13f), 3.5f, 3.5f, fillPaint)
        fillPaint.color = accent
        canvas.drawCircle(11f, 9.5f, 1.3f, fillPaint)
        drawText(canvas, "Drivary", 14f, 10.8f, 3.4f, ink, bold = true)
        drawText(canvas, headerDate(), 93f, 10.8f, 3.4f, gray, align = Paint.Align.RIGHT)
    }

    protected fun drawGlass(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float = 4f) {
        fillPaint.color = Color.argb(30, 255, 255, 255)
        canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, fillPaint)
        linePaint.color = Color.argb(46, 255, 255, 255)
        linePaint.strokeWidth = 0.3f
        canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, linePaint)
    }

    protected fun drawText(
        canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.textAlign = align
        canvas.drawText(text, x, y, textPaint)
    }

    // 너무 길면 끝을 …로 줄입니다.
    protected fun fit(text: String, maxW: Float, size: Float, bold: Boolean = false): String {
        textPaint.textSize = size
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (textPaint.measureText(text) <= maxW) return text
        val n = textPaint.breakText(text, true, maxW - size, null)
        return text.substring(0, maxOf(1, n)).trimEnd() + "…"
    }

    // 단어 단위로 줄을 나눕니다. 한 단어가 너무 길면 글자 단위로 자릅니다.
    protected fun wrapLines(text: String, maxW: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var cur = ""
        for (word in words) {
            var w = word
            while (textPaint.measureText(w) > maxW && w.length > 1) {
                val n = textPaint.breakText(w, true, maxW, null).coerceAtLeast(1)
                if (cur.isNotEmpty()) {
                    lines.add(cur)
                    cur = ""
                }
                lines.add(w.substring(0, n))
                w = w.substring(n)
            }
            val candidate = if (cur.isEmpty()) w else "$cur $w"
            if (textPaint.measureText(candidate) <= maxW) {
                cur = candidate
            } else {
                if (cur.isNotEmpty()) lines.add(cur)
                cur = w
            }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return lines
    }
}