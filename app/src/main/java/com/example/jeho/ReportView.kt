package com.example.jeho

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

private const val RV_MATCH = LinearLayout.LayoutParams.MATCH_PARENT
private const val RV_WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

// 리포트 탭: 월간 리캡 카드 + 레벨·챌린지·탐험·기록
class ReportView(private val host: MainActivity) : LinearLayout(host) {

    private val now = Calendar.getInstance()
    private var year = now.get(Calendar.YEAR)
    private var month = now.get(Calendar.MONTH) + 1

    private val recapCard = RecapCardView(host)
    private val monthLabel: TextView =
        host.label("", 22f, AppTheme.ink, true).apply { gravity = Gravity.CENTER }
    private val info: TextView =
        host.label("", 16f, AppTheme.gray).apply { setPadding(0, host.dp(8), 0, 0) }
    private val progressBox = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }

    init {
        orientation = LinearLayout.HORIZONTAL

        val cardH = (resources.displayMetrics.heightPixels * 0.56f).toInt()
        val cardW = (cardH * 0.8f).toInt()

        val nav = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                host.label("‹", 30f, AppTheme.accentLight, true).apply {
                    setPadding(host.dp(16), 0, host.dp(16), 0)
                    setOnClickListener { moveMonth(-1) }
                }
            )
            addView(monthLabel, LinearLayout.LayoutParams(0, RV_WRAP, 1f))
            addView(
                host.label("›", 30f, AppTheme.accentLight, true).apply {
                    setPadding(host.dp(16), 0, host.dp(16), 0)
                    setOnClickListener { moveMonth(1) }
                }
            )
        }

        val left = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(48), host.dp(24), host.dp(16), host.dp(16))
            addView(nav, LinearLayout.LayoutParams(cardW, RV_WRAP))
            addView(
                recapCard,
                LinearLayout.LayoutParams(cardW, cardH).apply { topMargin = host.dp(8) }
            )
            addView(
                host.pillButton("리캡 이미지로 저장", AppTheme.accent, Color.WHITE, 18f) {
                    host.saveBitmap(recapCard.renderBitmap()) { info.text = it }
                },
                LinearLayout.LayoutParams(cardW, host.dp(56)).apply { topMargin = host.dp(16) }
            )
            addView(info)
        }

        val rightContent = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(16), host.dp(24), host.dp(48), host.dp(24))
            addView(progressBox)
        }
        val right = ScrollView(host).apply { addView(rightContent) }

        addView(left, LinearLayout.LayoutParams(RV_WRAP, RV_MATCH))
        addView(right, LinearLayout.LayoutParams(0, RV_MATCH, 1f))
    }

    // 이번 달보다 미래로는 넘어가지 않습니다.
    private fun moveMonth(delta: Int) {
        var y = year
        var m = month + delta
        if (m < 1) {
            m = 12
            y--
        } else if (m > 12) {
            m = 1
            y++
        }
        val cur = Calendar.getInstance()
        val curKey = cur.get(Calendar.YEAR) * 12 + cur.get(Calendar.MONTH)
        if (y * 12 + (m - 1) > curKey) return
        year = y
        month = m
        render()
    }

    fun render() {
        val all = host.repo.loadAll()
        info.text = ""
        monthLabel.text = "${year}년 ${month}월"
        recapCard.recap = MonthlyRecap.of(all, year, month)
        renderProgress(all)
    }

    // ---------------- 레벨 · 챌린지 · 탐험 · 기록 ----------------
    private fun renderProgress(all: List<SavedTrip>) {
        progressBox.removeAllViews()

        // 레벨
        val lv = Progress.level(all)
        val levelPanel = panel()
        levelPanel.addView(host.label("Lv.${lv.level}  ${lv.name}", 26f, AppTheme.ink, true))
        levelPanel.addView(
            bar(lv.xpInLevel.toFloat() / lv.xpToNext),
            LinearLayout.LayoutParams(RV_MATCH, host.dp(10)).apply { topMargin = host.dp(12) }
        )
        levelPanel.addView(
            host.label("다음 레벨까지 ${lv.xpToNext - lv.xpInLevel} XP  ·  총 ${lv.xp} XP", 15f, AppTheme.gray)
                .apply { setPadding(0, host.dp(8), 0, 0) }
        )
        levelPanel.addView(
            host.label(
                "XP는 주행마다 안전·에코 점수와 새로 가 본 구역으로 쌓여요. 주행 거리와 속도는 반영되지 않아요.",
                13f, AppTheme.gray
            ).apply { setPadding(0, host.dp(6), 0, 0) }
        )
        progressBox.addView(levelPanel, panelParams(0))

        // 이번 주 챌린지
        progressBox.addView(sectionTitle("이번 주 챌린지"), panelParams(20))
        val challengePanel = panel()
        Progress.challenges(all).forEachIndexed { i, c ->
            val row = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
            val head = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    host.label(c.title, 17f, AppTheme.ink, true),
                    LinearLayout.LayoutParams(0, RV_WRAP, 1f)
                )
                addView(
                    host.label(
                        if (c.done) "완료" else "${c.current} / ${c.target}", 15f,
                        if (c.done) AppTheme.accentLight else AppTheme.gray, c.done
                    )
                )
            }
            row.addView(head)
            row.addView(
                host.label(c.desc, 13f, AppTheme.gray).apply { setPadding(0, host.dp(2), 0, host.dp(8)) }
            )
            row.addView(
                bar(c.current.toFloat() / c.target),
                LinearLayout.LayoutParams(RV_MATCH, host.dp(8))
            )
            challengePanel.addView(
                row,
                LinearLayout.LayoutParams(RV_MATCH, RV_WRAP).apply { if (i > 0) topMargin = host.dp(16) }
            )
        }
        progressBox.addView(challengePanel, panelParams(8))

        // 탐험
        val ex = Progress.explore(all)
        progressBox.addView(sectionTitle("탐험"), panelParams(20))
        val explorePanel = panel()
        explorePanel.addView(
            statRow(
                listOf(
                    Pair("${ex.total}곳", "탐험한 구역"),
                    Pair("${ex.monthNew}곳", "이번 달 새 구역"),
                    Pair("${ex.weekNew}곳", "이번 주 새 구역")
                )
            )
        )
        explorePanel.addView(
            host.label(
                "약 1km 크기의 구역을 처음 지나가면 채워져요. 위치는 이 기기 안에서만 계산해요.",
                13f, AppTheme.gray
            ).apply { setPadding(0, host.dp(10), 0, 0) }
        )
        progressBox.addView(explorePanel, panelParams(8))

        // 내 기록
        val rc = Progress.records(all)
        fun score(v: Int) = if (v >= 0) "${v}점" else "—"
        val avgLabel =
            if (rc.weekAvg >= 0 && rc.lastWeekAvg >= 0)
                "이번 주 평균 안전 (지난주 대비 " +
                        String.format(Locale.US, "%+d", rc.weekAvg - rc.lastWeekAvg) + ")"
            else "이번 주 평균 안전"
        progressBox.addView(sectionTitle("내 기록"), panelParams(20))
        val recordPanel = panel()
        recordPanel.addView(
            statRow(
                listOf(
                    Pair(score(rc.bestSafety), "최고 안전 점수"),
                    Pair(score(rc.bestEco), "최고 에코 점수"),
                    Pair(score(rc.weekAvg), avgLabel)
                )
            )
        )
        progressBox.addView(recordPanel, panelParams(8))
    }

    private fun panel(): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        background = host.glass(20)
        setPadding(host.dp(20), host.dp(18), host.dp(20), host.dp(18))
    }

    private fun panelParams(topDp: Int) =
        LinearLayout.LayoutParams(RV_MATCH, RV_WRAP).apply {
            topMargin = host.dp(topDp)
            leftMargin = host.dp(6)
            rightMargin = host.dp(6)
        }

    private fun sectionTitle(text: String): TextView =
        host.label(text, 20f, AppTheme.ink, true).apply { setPadding(host.dp(2), 0, 0, 0) }

    // 가로로 같은 폭의 숫자 칸을 나란히 놓습니다.
    private fun statRow(items: List<Pair<String, String>>): LinearLayout =
        LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            items.forEach { (value, caption) ->
                val cell = LinearLayout(host).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(host.label(value, 28f, AppTheme.ink, true))
                    addView(host.label(caption, 13f, AppTheme.gray).apply { setPadding(0, host.dp(2), 0, 0) })
                }
                addView(cell, LinearLayout.LayoutParams(0, RV_WRAP, 1f))
            }
        }

    // 진행 막대. ratio는 0~1
    private fun bar(ratio: Float): LinearLayout {
        val r = ratio.coerceIn(0f, 1f)
        return LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            background = host.glass(6)
            addView(
                View(host).apply { background = host.glass(6, AppTheme.accent, AppTheme.accent) },
                LinearLayout.LayoutParams(0, RV_MATCH, r)
            )
            addView(View(host), LinearLayout.LayoutParams(0, RV_MATCH, 1f - r))
        }
    }
}