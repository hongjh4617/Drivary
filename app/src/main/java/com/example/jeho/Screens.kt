package com.example.jeho

import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

// ---------------- 홈 ----------------
class HomeView(private val host: MainActivity) : LinearLayout(host) {

    private val recLabel: TextView =
        host.label("● 기록 중", 20f, AppTheme.accentLight, true).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
    private val headline: TextView =
        host.label("오늘도,\n어디로 떠나볼까요?", 44f, AppTheme.ink).apply {
            typeface = lightFont()
            gravity = Gravity.CENTER
        }
    private val timer: TextView =
        host.label("00:00:00", 72f, AppTheme.ink).apply {
            typeface = lightFont()
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
    private val sub: TextView =
        host.label("달리는 동안 경로와 시간이 자동으로 기록돼요", 18f, AppTheme.gray).apply {
            gravity = Gravity.CENTER
            setPadding(0, host.dp(12), 0, host.dp(36))
        }
    private val startButton: Button =
        host.pillButton("기록 시작", AppTheme.accent, Color.WHITE, 24f) { host.onMainButton() }
    private val endButton: Button =
        host.pillButton("주행 종료, 일지 만들기", AppTheme.accent, Color.WHITE, 22f) { host.onMainButton() }
            .apply { visibility = View.GONE }
    private val statusText: TextView =
        host.label("", 18f, AppTheme.gray).apply {
            gravity = Gravity.CENTER
            setPadding(0, host.dp(20), 0, 0)
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(host.dp(64), 0, host.dp(64), 0)

        addView(recLabel)
        addView(headline)
        addView(timer)
        addView(sub)
        addView(startButton, LinearLayout.LayoutParams(host.dp(560), host.dp(84)))
        addView(endButton, LinearLayout.LayoutParams(host.dp(560), host.dp(84)))
        addView(statusText)
    }

    // 기록 중에는 1초마다 호출됩니다.
    fun tick() {
        val active = host.isTripActive()
        recLabel.visibility = if (active) View.VISIBLE else View.GONE
        timer.visibility = if (active) View.VISIBLE else View.GONE
        headline.visibility = if (active) View.GONE else View.VISIBLE
        sub.visibility = if (active) View.GONE else View.VISIBLE
        startButton.visibility = if (active) View.GONE else View.VISIBLE
        endButton.visibility = if (active) View.VISIBLE else View.GONE
        if (active) timer.text = host.elapsedText()
        statusText.text = host.statusMessage()
    }

    fun render() {
        tick()
    }
}

// ---------------- 여행 앨범 ----------------
class AlbumView(private val host: MainActivity) : LinearLayout(host) {

    private val title: TextView =
        host.label("여행 앨범", 32f, AppTheme.ink).apply { typeface = lightFont() }
    private val empty: TextView = host.label(
        "아직 저장된 여행이 없어요.\n기록을 시작하면 이곳에 앨범이 쌓여요.", 20f, AppTheme.gray
    ).apply { setPadding(0, host.dp(24), 0, 0) }
    private val row = LinearLayout(host).apply {
        orientation = HORIZONTAL
        setPadding(0, host.dp(16), 0, host.dp(8))
    }

    init {
        orientation = VERTICAL
        setPadding(host.dp(48), host.dp(32), host.dp(48), host.dp(16))

        addView(title)
        addView(empty)
        val scroll = HorizontalScrollView(host).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    fun render() {
        val items = host.repo.loadAll().sortedByDescending { it.id }
        title.text = "여행 앨범 (${items.size})"
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        row.removeAllViews()
        val cardH = (resources.displayMetrics.heightPixels * 0.46f).toInt()
        items.forEach { saved ->
            val tile = host.buildTile(
                saved, cardH, { host.openDetail(saved.id) }, { host.confirmDelete(saved.id) }
            )
            row.addView(
                tile,
                LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    setMargins(0, 0, host.dp(20), 0)
                }
            )
        }
    }
}

// ---------------- 앨범 상세 ----------------
class DetailView(private val host: MainActivity) : LinearLayout(host) {

    val card = TripCardView(host)

    private val titleText: TextView =
        host.label("", 30f, AppTheme.ink).apply { typeface = lightFont() }
    private val metaText: TextView =
        host.label("", 16f, AppTheme.gray).apply { setPadding(0, host.dp(4), 0, host.dp(12)) }
    private val info: TextView =
        host.label("", 16f, AppTheme.gray).apply { setPadding(0, host.dp(8), 0, 0) }
    private val hint: TextView =
        host.label("카드를 누르면 뒷면이 보여요", 14f, AppTheme.gray)

    // 한 줄짜리 유리 행: [이름] [현재 값] [›]
    private val memoRow = infoRow("메모") { host.askMemo() }
    private val weatherRow = infoRow("날씨") { host.askWeather() }
    private val songRow = infoRow("노래") { host.askSong() }

    private var flipping = false

    init {
        orientation = HORIZONTAL

        val panel = LinearLayout(host).apply {
            orientation = VERTICAL
            setPadding(host.dp(48), host.dp(24), host.dp(24), host.dp(24))
        }

        panel.addView(
            host.label("‹ 여행 앨범", 18f, AppTheme.accentLight, true).apply {
                setPadding(0, host.dp(8), host.dp(16), host.dp(16))
                setOnClickListener { host.showAlbum() }
            }
        )
        panel.addView(titleText)
        panel.addView(metaText)

        listOf(memoRow, weatherRow, songRow).forEach {
            panel.addView(
                it.first,
                LinearLayout.LayoutParams(MATCH, host.dp(64)).apply { topMargin = host.dp(10) }
            )
        }

        panel.addView(
            host.pillButton("이미지로 저장", AppTheme.accent, Color.WHITE, 20f) { host.saveCardImage() },
            LinearLayout.LayoutParams(MATCH, host.dp(60)).apply { topMargin = host.dp(24) }
        )
        panel.addView(
            host.label("이 여행 삭제", 16f, AppTheme.danger).apply {
                gravity = Gravity.CENTER
                setPadding(0, host.dp(16), 0, host.dp(16))
                setOnClickListener { host.deleteCurrent() }
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        panel.addView(info)

        val scroll = ScrollView(host).apply { addView(panel) }

        val cardH = (resources.displayMetrics.heightPixels * 0.70f).toInt()
        val cardW = (cardH * 0.8f).toInt()

        // 카드를 누르면 뒤집힙니다.
        card.cameraDistance = 12000f * resources.displayMetrics.density
        card.setOnClickListener { flip() }

        val right = FrameLayout(host).apply {
            addView(card, FrameLayout.LayoutParams(cardW, cardH, Gravity.CENTER))
            addView(
                hint,
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                    .apply { bottomMargin = host.dp(16) }
            )
        }

        addView(scroll, LinearLayout.LayoutParams(0, MATCH, 1f))
        addView(right, LinearLayout.LayoutParams(0, MATCH, 1f))
    }

    // 90도까지 돌린 뒤 면을 바꾸고, 반대쪽에서 0도까지 돌려서 뒤집는 느낌을 냅니다.
    private fun flip() {
        if (flipping) return
        flipping = true
        card.animate().rotationY(90f).setDuration(180).withEndAction {
            card.showBack = !card.showBack
            hint.text = if (card.showBack) "카드를 누르면 앞면이 보여요" else "카드를 누르면 뒷면이 보여요"
            card.rotationY = -90f
            card.animate().rotationY(0f).setDuration(180).withEndAction { flipping = false }.start()
        }.start()
    }

    private fun infoRow(name: String, onClick: () -> Unit): Pair<LinearLayout, TextView> {
        val value = host.label("", 18f, AppTheme.ink).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val row = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = host.glass(20)
            setPadding(host.dp(20), 0, host.dp(16), 0)
            setOnClickListener { onClick() }
            addView(host.label(name, 16f, AppTheme.gray), LinearLayout.LayoutParams(host.dp(64), WRAP))
            addView(value, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(host.label("›", 26f, AppTheme.gray))
        }
        return Pair(row, value)
    }

    private fun setValue(tv: TextView, value: String) {
        if (value.isBlank()) {
            tv.text = "추가하기"
            tv.setTextColor(AppTheme.gray)
        } else {
            tv.text = value.replace("\n", " ")
            tv.setTextColor(AppTheme.ink)
        }
    }

    // resetFace가 true면 앞면부터 보여줍니다. (다른 여행을 새로 열 때)
    fun bind(trip: Trip, resetFace: Boolean = false) {
        if (resetFace) {
            card.animate().cancel()
            card.showBack = false
            card.rotationY = 0f
            flipping = false
            hint.text = "카드를 누르면 뒷면이 보여요"
        }
        card.trip = trip
        titleText.text = trip.title
        val millis = if (trip.dateMillis > 0) trip.dateMillis else System.currentTimeMillis()
        val date = SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREA).format(Date(millis))
        metaText.text = date + "  ·  " + String.format(Locale.US, "%.1f km", trip.distanceKm)
        setValue(memoRow.second, trip.story)
        setValue(weatherRow.second, trip.weather)
        setValue(songRow.second, trip.song)
        info.text = ""
    }

    fun say(message: String) {
        info.text = message
    }
}

// ---------------- 더보기 ----------------
class MoreView(private val host: MainActivity) : LinearLayout(host) {

    init {
        orientation = VERTICAL
        setPadding(host.dp(64), host.dp(40), host.dp(64), host.dp(24))

        val version = try {
            host.packageManager.getPackageInfo(host.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

        addView(host.label("더보기", 32f, AppTheme.ink).apply { typeface = lightFont() })
        addView(
            host.label("Drivary $version", 20f, AppTheme.ink, true)
                .apply { setPadding(0, host.dp(24), 0, 0) }
        )
        addView(
            host.label(
                "Every drive, a diary.\n달린 길이 하루의 이야기가 되는 여행 일지 서비스예요.", 18f, AppTheme.gray
            ).apply { setPadding(0, host.dp(8), 0, host.dp(32)) }
        )

        addView(host.label("개인정보 안내", 20f, AppTheme.ink, true))
        addView(
            host.label(
                "· 위치는 '기록 시작'을 누른 뒤 주행 기록 중에만 사용해요.\n" +
                        "· 여행 기록과 경로는 이 차량 안에만 저장되고 외부로 전송되지 않아요.\n" +
                        "· 기록은 '여행 앨범'이나 아래 버튼으로 언제든 삭제할 수 있어요.",
                17f, AppTheme.gray
            ).apply { setPadding(0, host.dp(8), 0, host.dp(32)) }
        )

        addView(
            host.pillButton("모든 기록 삭제", AppTheme.surface, AppTheme.danger, 18f) { host.confirmDeleteAll() },
            LinearLayout.LayoutParams(host.dp(320), host.dp(56))
        )
    }
}