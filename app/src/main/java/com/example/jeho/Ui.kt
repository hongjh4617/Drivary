package com.example.jeho

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 차량 화면에서는 멀리서 보니 모든 글자를 키웁니다. 이 숫자 하나로 전체 크기가 바뀝니다.
const val TEXT_SCALE = 1.3f

// 앱 전체 색상. 디자인을 바꿀 때는 여기부터 고칩니다.
object AppTheme {
    val bgTop = Color.parseColor("#0B1B33")
    val bgBottom = Color.parseColor("#040A14")
    val surface = Color.argb(20, 255, 255, 255)       // 유리 패널 채움 (약 8%)
    val stroke = Color.argb(46, 255, 255, 255)        // 유리 패널 테두리
    val ink = Color.parseColor("#EAF2FF")
    val gray = Color.parseColor("#8FA3BF")
    val accent = Color.parseColor("#3D8BFF")
    val accentLight = Color.parseColor("#5AA9FF")
    val accentSoft = Color.argb(64, 61, 139, 255)
    val danger = Color.parseColor("#FF6B6B")
    val line = stroke
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun lightFont(): Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

fun Context.label(text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp * TEXT_SCALE
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

// 유리 느낌 패널 배경
fun Context.glass(
    radiusDp: Int = 24,
    fill: Int = AppTheme.surface,
    stroke: Int = AppTheme.stroke
): GradientDrawable = GradientDrawable().apply {
    cornerRadius = dp(radiusDp).toFloat()
    setColor(fill)
    setStroke(dp(1), stroke)
}

// 짙은 남색 그라데이션 + 오른쪽 위 파란 빛
fun Context.screenBackground(): Drawable {
    val base = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(AppTheme.bgTop, AppTheme.bgBottom)
    )
    val glow = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        setGradientRadius(resources.displayMetrics.widthPixels * 0.7f)
        setGradientCenter(0.85f, 0.05f)
        setColors(intArrayOf(Color.argb(110, 47, 123, 255), Color.argb(0, 47, 123, 255)))
    }
    return LayerDrawable(arrayOf(base, glow))
}

fun Context.stylePill(b: Button, bgColor: Int, textColor: Int) {
    b.setTextColor(textColor)
    b.background = GradientDrawable().apply {
        cornerRadius = dp(40).toFloat()
        setColor(bgColor)
        setStroke(dp(1), AppTheme.stroke)
    }
}

fun Context.pillButton(
    label: String, bgColor: Int, textColor: Int, sizeSp: Float = 20f, onClick: () -> Unit
): Button = Button(this).apply {
    text = label
    setAllCaps(false)
    textSize = sizeSp * TEXT_SCALE
    stateListAnimator = null
    stylePill(this, bgColor, textColor)
    setOnClickListener { onClick() }
}

// 앨범에서 쓰는 여행 카드 한 칸 (유리 패널 안에 카드 + 날짜 + 요약 + 삭제)
fun MainActivity.buildTile(
    saved: SavedTrip, cardH: Int, onOpen: () -> Unit, onDelete: (() -> Unit)?
): LinearLayout {
    val tile = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = glass(24)
        setPadding(dp(12), dp(12), dp(12), dp(6))
    }
    val card = TripCardView(this).apply {
        trip = saved.trip
        setOnClickListener { onOpen() }
    }
    tile.addView(card, LinearLayout.LayoutParams((cardH * 0.8f).toInt(), cardH))

    val date = SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREA).format(Date(saved.id))
    val t = saved.trip
    val meta = String.format(Locale.US, "%.1f km", t.distanceKm) +
            if (t.weather.isBlank()) "" else "  ·  ${t.weather}"

    val texts = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(date, 16f, AppTheme.ink, true))
        addView(label(meta, 14f, AppTheme.gray).apply { setPadding(0, dp(2), 0, 0) })
    }
    val footer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(10), dp(4), 0)
        addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (onDelete != null) {
            addView(label("삭제", 15f, AppTheme.danger).apply {
                setPadding(dp(14), dp(14), dp(6), dp(14))
                setOnClickListener { onDelete() }
            })
        }
    }
    tile.addView(footer)
    return tile
}