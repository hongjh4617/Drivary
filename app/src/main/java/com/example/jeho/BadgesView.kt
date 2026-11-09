package com.example.jeho

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val BV_MATCH = LinearLayout.LayoutParams.MATCH_PARENT
private const val BV_WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

// 배지 탭: 배지를 4열로 한 화면에 보여 줍니다.
class BadgesView(private val host: MainActivity) : ScrollView(host) {

    private val title: TextView =
        host.label("배지", 26f, AppTheme.ink).apply { typeface = lightFont() }
    private val sub: TextView =
        host.label("", 15f, AppTheme.gray).apply { setPadding(0, host.dp(4), 0, host.dp(12)) }
    private val box = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }

    init {
        val content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(40), host.dp(28), host.dp(40), host.dp(28))
            addView(title)
            addView(sub)
            addView(box)
        }
        addView(content)
    }

    fun render() {
        val badges = Badges.evaluate(host.repo.loadAll())
        sub.text = "${badges.count { it.earned }} / ${badges.size}개 획득  ·  안전하게 달리고 새로운 곳에 가면 채워져요"
        box.removeAllViews()
        badges.chunked(COLUMNS).forEach { items ->
            val row = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
            items.forEach { b ->
                row.addView(badgeTile(b), cellParams())
            }
            // 마지막 줄이 모자라면 빈 칸으로 채워서 폭을 맞춥니다.
            repeat(COLUMNS - items.size) {
                row.addView(View(host), cellParams(1))
            }
            box.addView(row, LinearLayout.LayoutParams(BV_MATCH, BV_WRAP))
        }
    }

    private fun cellParams(height: Int = BV_WRAP) =
        LinearLayout.LayoutParams(0, height, 1f).apply {
            setMargins(host.dp(6), host.dp(6), host.dp(6), host.dp(6))
        }

    private fun badgeTile(b: BadgeStatus): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        background = if (b.earned) host.glass(20, AppTheme.accentSoft, AppTheme.accent) else host.glass(20)
        alpha = if (b.earned) 1f else 0.6f
        setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        addView(host.label(b.name, 17f, AppTheme.ink, true))
        addView(host.label(b.desc, 13f, AppTheme.gray).apply { setPadding(0, host.dp(4), 0, host.dp(8)) })
        addView(
            host.label(
                if (b.earned) "획득" else b.progress, 14f,
                if (b.earned) AppTheme.accentLight else AppTheme.gray, b.earned
            )
        )
    }

    private companion object {
        const val COLUMNS = 4
    }
}