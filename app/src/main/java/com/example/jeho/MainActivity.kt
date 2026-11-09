package com.example.jeho

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    val repo: TripRepository by lazy { TripRepository(this) }
    private val dataSource: TripDataSource by lazy { VehicleTripDataSource(this) }
    private val writer: JournalWriter = TemplateJournalWriter()
    private val prefs by lazy { getSharedPreferences("gililgi_state", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var container: FrameLayout
    private lateinit var home: HomeView
    private lateinit var album: AlbumView
    private lateinit var detail: DetailView
    private lateinit var report: ReportView
    private lateinit var badgeView: BadgesView
    private lateinit var more: MoreView
    private lateinit var tabs: List<TextView>

    // 주행 상태
    private var tripActive = false
    private var startedAt = 0L
    private var recording = false
    private var notice = ""
    private val routePoints = mutableListOf<Pair<Double, Double>>()
    private val metrics = DriveMetrics()
    private var fuelReader: FuelReader? = null

    // 앨범 상세에서 열려 있는 여행
    private var currentId: Long? = null

    // 위치(Fused Location)
    private var fused: FusedLocation? = null
    private var fusedListener: FusedLocationListener? = null

    private val ticker = object : Runnable {
        override fun run() {
            home.tick()
            if (tripActive) handler.postDelayed(this, 1000)
        }
    }

    // 어두운 배경에 맞는 대화상자
    private fun dialog() = DarkDialog(this)

    // ---------------- 화면 전환 ----------------

    private fun show(view: View, tab: Int) {
        container.removeAllViews()
        container.addView(view)
        tabs.forEachIndexed { i, tv ->
            val on = i == tab
            tv.setTextColor(if (on) AppTheme.ink else AppTheme.gray)
            tv.background = if (on) {
                GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(AppTheme.accentSoft)
                    setStroke(dp(1), AppTheme.accent)
                }
            } else {
                null
            }
        }
    }

    fun showHome() {
        home.render()
        show(home, 0)
    }

    fun showAlbum() {
        album.render()
        show(album, 1)
    }

    private fun showReport() {
        report.render()
        show(report, 2)
    }
    private fun showBadges() {
        badgeView.render()
        show(badgeView, 3)
    }
    private fun showMore() {
        show(more, 4)
    }

    fun openDetail(id: Long) {
        val saved = repo.loadAll().firstOrNull { it.id == id } ?: return
        currentId = id
        detail.bind(saved.trip, true)
        show(detail, 1)
    }

    // ---------------- 주행 기록 ----------------

    fun isTripActive(): Boolean = tripActive

    fun elapsedText(): String {
        val sec = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
    }

    fun statusMessage(): String {
        if (!tripActive) {
            return if (notice.isEmpty()) "기록을 시작하면 경로, 거리, 시간이 자동으로 쌓여요" else notice
        }
        val route = if (recording) "경로 기록 중 (${routePoints.size}점)" else "경로 없이 기록 중"
        return route + if (notice.isEmpty()) "" else "\n$notice"
    }

    fun onMainButton() {
        if (tripActive) endTrip() else startTrip()
    }

    private fun startTrip() {
        dataSource.startTrip(
            onDone = {
                runOnUiThread {
                    tripActive = true
                    startedAt = System.currentTimeMillis()
                    routePoints.clear()
                    metrics.reset()
                    notice = ""
                    prefs.edit().putBoolean("active", true).putLong("startedAt", startedAt).apply()
                    beginLocation()
                    handler.removeCallbacks(ticker)
                    handler.post(ticker)
                }
            },
            onError = { m ->
                runOnUiThread {
                    notice = "주행 거리를 읽지 못했어요. 차량 연결을 확인하고 다시 시도해 주세요. ($m)"
                    home.tick()
                }
            }
        )
    }

    private fun endTrip() {
        dataSource.getLatestTrip(
            onResult = { trip -> runOnUiThread { finishTrip(trip) } },
            onError = { m ->
                runOnUiThread {
                    notice = "일지를 만들지 못했어요. 다시 시도해 주세요. ($m)"
                    home.tick()
                }
            }
        )
    }

    private fun finishTrip(trip: Trip) {
        recording = false
        tripActive = false
        notice = ""
        prefs.edit().putBoolean("active", false).apply()
        handler.removeCallbacks(ticker)

        val route = if (routePoints.size >= 2) routePoints.toList() else emptyList()
        val now = System.currentTimeMillis()
        val m = metrics.result()
        val base = trip.copy(
            route = route,
            dateMillis = now,
            maxSpeedKmh = m.maxSpeedKmh,
            hardAccels = m.hardAccels,
            hardBrakes = m.hardBrakes,
            safetyScore = m.safety,
            ecoScore = m.eco
        )
        repo.save(now, base)
        // 차량 연비는 비동기로 읽어서, 읽히면 카드에 반영합니다. (못 읽으면 그대로 둡니다)
        val fr = fuelReader ?: FuelReader(this).also { fuelReader = it }
        fr.read { eff ->
            if (eff.kmPerLiter > 0.0 || eff.kmPerKwh > 0.0) {
                runOnUiThread {
                    updateTrip(now) {
                        it.copy(fuelEconomy = eff.kmPerLiter, evEfficiency = eff.kmPerKwh)
                    }
                }
            }
        }

        // 자동 문장은 비어 있을 때만 채웁니다. (그 사이 사용자가 쓴 메모를 지우지 않도록)
        writer.write(base) { text ->
            runOnUiThread {
                updateTrip(now) { if (it.story.isBlank()) it.copy(story = text) else it }
            }
        }
        openDetail(now)
        detail.say("일지가 만들어졌고 여행 앨범에 저장됐어요")
    }

    // 저장된 여행을 고쳐서 다시 저장합니다.
    private fun updateTrip(id: Long, change: (Trip) -> Trip) {
        val saved = repo.loadAll().firstOrNull { it.id == id } ?: return
        val updated = change(saved.trip)
        repo.save(id, updated)
        if (currentId == id) detail.bind(updated)
    }

    private fun currentTrip(): Trip? {
        val id = currentId ?: return null
        return repo.loadAll().firstOrNull { it.id == id }?.trip
    }

    // ---------------- 위치 ----------------

    private fun beginLocation() {
        val reason = ensureFused()
        recording = reason == null
        if (recording) {
            // 지금 위치를 출발점으로 한 번 받아 둡니다.
            fusedListener?.let { fused?.getLastKnownLocation(it) }
        } else {
            notice = "위치를 쓸 수 없어 경로 없이 기록해요."
        }
    }

    // 위치 사용 준비. 준비되면 null, 안 되면 이유를 돌려줍니다.
    private fun ensureFused(): String? {
        val perm = "pleos.car.permission.FUSED_LOCATION"
        val f = fused ?: FusedLocation(applicationContext).also { fused = it }

        if (!f.isSupported()) return "이 환경에서는 위치를 쓸 수 없어요"
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(perm), 100)
            return "위치 권한이 필요해요"
        }
        if (fusedListener == null) {
            f.initialize()
            val l = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    if (location == null) return
                    runOnUiThread {
                        if (recording) {
                            addRoutePoint(location.latitude, location.longitude)
                            metrics.add(location.latitude, location.longitude, System.currentTimeMillis())
                        }
                    }
                }

                override fun onError(exception: Exception) {}
            }
            fusedListener = l
            f.registerFusedLocationCallback(l)
        }
        return null
    }

    // 직전 점에서 5m 이상 움직였을 때만 경로에 추가합니다.
    private fun addRoutePoint(lat: Double, lon: Double) {
        val last = routePoints.lastOrNull()
        if (last != null) {
            val r = FloatArray(1)
            android.location.Location.distanceBetween(last.first, last.second, lat, lon, r)
            if (r[0] < 5f) return
        }
        routePoints.add(Pair(lat, lon))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 기록 중에 위치 권한을 허용하면 그때부터 경로를 기록합니다.
        if (tripActive && !recording) {
            beginLocation()
            home.tick()
        }
    }

    // ---------------- 편집 (주행 중에는 막습니다) ----------------

    private fun editBlocked(): Boolean {
        if (tripActive) {
            detail.say("주행 중에는 편집할 수 없어요. 주행을 종료한 뒤 작성해 주세요.")
            return true
        }
        return false
    }

    fun askMemo() {
        val id = currentId ?: return
        if (editBlocked()) return
        val trip = currentTrip() ?: return
        val input = EditText(this).apply {
            hint = "오늘의 드라이브는 어땠나요?"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(trip.story)
            setSelection(text.length)
        }
        dialog()
            .setTitle("메모 쓰기")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                updateTrip(id) { it.copy(story = input.text.toString().trim()) }
                detail.say("메모를 저장했어요")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    fun askWeather() {
        val id = currentId ?: return
        if (editBlocked()) return
        val options = arrayOf("맑음", "흐림", "비", "눈", "선택 안 함")
        dialog()
            .setTitle("오늘의 날씨")
            .setItems(options) { _, which ->
                val value = if (which == options.size - 1) "" else options[which]
                updateTrip(id) { it.copy(weather = value) }
                detail.say(if (value.isEmpty()) "날씨를 지웠어요" else "날씨를 '$value'로 기록했어요")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    fun askSong() {
        val id = currentId ?: return
        if (editBlocked()) return
        val trip = currentTrip() ?: return
        val input = EditText(this).apply {
            hint = "가수 - 곡 제목"
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(40))
            setText(trip.song)
            setSelection(text.length)
        }
        dialog()
            .setTitle("오늘의 노래")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                updateTrip(id) { it.copy(song = input.text.toString().trim()) }
                detail.say("오늘의 노래를 저장했어요")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ---------------- 이미지 저장 ----------------

    // 상세 화면에서 지금 보이는 면(앞면 또는 뒷면)을 저장합니다.
    fun saveCardImage() {
        if (currentTrip() == null) {
            detail.say("저장할 여행이 없어요")
            return
        }
        saveBitmap(detail.card.renderBitmap()) { detail.say(it) }
    }

    // 카드 이미지를 Pictures/Drivary에 저장하고 미리보기를 띄웁니다.
    fun saveBitmap(bmp: android.graphics.Bitmap, report: (String) -> Unit) {
        try {
            val name = "drivary_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
            val saved: android.graphics.Bitmap?
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Drivary")
                }
                val uri = contentResolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: throw Exception("저장 위치를 만들 수 없어요")
                contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                saved = contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
                report("이미지로 저장했어요 (Pictures/Drivary)")
            } else {
                val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                val file = java.io.File(dir, name)
                file.outputStream().use { out ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                saved = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                report("이미지로 저장했어요")
            }
            showPreview(saved, report)
        } catch (e: Exception) {
            report("저장하지 못했어요. 다시 시도해 주세요. (${e.message})")
        }
    }

    private fun showPreview(bmp: android.graphics.Bitmap?, report: (String) -> Unit) {
        if (bmp == null) {
            report("저장은 됐지만 미리보기를 불러오지 못했어요")
            return
        }
        val h = (resources.displayMetrics.heightPixels * 0.65f).toInt()
        val w = (h * 0.8f).toInt()
        val view = android.widget.ImageView(this).apply {
            setImageBitmap(bmp)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            layoutParams = android.view.ViewGroup.LayoutParams(w, h)
        }
        dialog()
            .setTitle("저장된 카드")
            .setView(view)
            .setPositiveButton("닫기", null)
            .show()
    }

    // ---------------- 삭제 ----------------

    fun deleteCurrent() {
        currentId?.let { confirmDelete(it) }
    }

    fun confirmDelete(id: Long) {
        dialog()
            .setTitle("삭제할까요?")
            .setMessage("이 여행 기록은 되돌릴 수 없어요.")
            .setPositiveButton("삭제") { _, _ ->
                repo.delete(id)
                if (currentId == id) currentId = null
                showAlbum()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    fun confirmDeleteAll() {
        dialog()
            .setTitle("모든 기록을 삭제할까요?")
            .setMessage("저장된 모든 여행 기록과 경로가 지워지고 되돌릴 수 없어요.")
            .setPositiveButton("모두 삭제") { _, _ ->
                repo.clear()
                currentId = null
                showHome()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ---------------- 시작과 종료 ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        home = HomeView(this)
        album = AlbumView(this)
        detail = DetailView(this)
        report = ReportView(this)
        badgeView = BadgesView(this)
        more = MoreView(this)
        container = FrameLayout(this)

        // 왼쪽 세로 메뉴
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(28))
            addView(label("Drivary", 18f, AppTheme.ink, true))
            addView(label("Every drive,\na diary.", 10f, AppTheme.gray).apply { setPadding(0, dp(4), 0, 0) })
        }
        val names = listOf("홈", "여행 앨범", "리포트", "배지", "더보기")
        tabs = names.mapIndexed { i, name ->
            label(name, 18f, AppTheme.gray, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(16), dp(8), dp(16))
                setOnClickListener {
                    when (i) {
                        0 -> showHome()
                        1 -> showAlbum()
                        2 -> showReport()
                        3 -> showBadges()
                        else -> showMore()
                    }
                }
            }
        }
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
            setBackgroundColor(AppTheme.surface)
            addView(brand)
            tabs.forEach {
                addView(
                    it,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                )
            }
        }
        val divider = View(this).apply { setBackgroundColor(AppTheme.stroke) }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = screenBackground()
            addView(rail, LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(divider, LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(container, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        })

        // 앱이 꺼졌다 켜져도 기록 중이던 주행을 이어갑니다. (경로와 운전 점수는 다시 켠 시점부터 계산돼요)
        if (prefs.getBoolean("active", false)) {
            tripActive = true
            startedAt = prefs.getLong("startedAt", System.currentTimeMillis())
            beginLocation()
            notice = "이전 주행을 이어서 기록하고 있어요."
            handler.post(ticker)
        }

        showHome()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
        fusedListener?.let { fused?.unregisterFusedLocationCallback(it) }
        if (fusedListener != null) fused?.release()
        fuelReader?.release()
        dataSource.release()
    }
}