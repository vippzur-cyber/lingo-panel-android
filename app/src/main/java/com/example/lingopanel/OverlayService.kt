package com.example.lingopanel

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val channelId = "lingo_panel_channel"
    private val notifId = 42

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Lingo Panel", NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Lingo Panel aktif")
            .setContentText("Panel mengambang sedang berjalan")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        startForeground(notifId, notification)
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    // ---------- Bubble ----------

    private fun showBubble() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_bubble, null)
        bubbleView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 300
        bubbleParams = params

        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showPanel()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
    }

    // ---------- Panel ----------

    private fun showPanel() {
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_panel, null)
        panelView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // Sengaja TANPA FLAG_NOT_FOCUSABLE: panel ini punya EditText/AutoCompleteTextView
            // yang butuh fokus supaya keyboard bawaan bisa muncul.
            0,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 200
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        panelParams = params

        setupPanel(view)

        windowManager.addView(view, params)
    }

    private fun setupPanel(view: View) {
        val acSource = view.findViewById<AutoCompleteTextView>(R.id.acSource)
        val acTarget = view.findViewById<AutoCompleteTextView>(R.id.acTarget)
        val etInput = view.findViewById<EditText>(R.id.etInput)
        val tvOutput = view.findViewById<TextView>(R.id.tvOutput)
        val btnTranslate = view.findViewById<Button>(R.id.btnTranslate)
        val pbLoading = view.findViewById<android.widget.ProgressBar>(R.id.pbLoading)
        val btnCopy = view.findViewById<TextView>(R.id.btnCopy)
        val btnClose = view.findViewById<TextView>(R.id.btnClose)
        val tvHasilLabel = view.findViewById<TextView>(R.id.tvHasilLabel)
        val btnMatikan = view.findViewById<Button>(R.id.btnMatikanPanel)
        val tabTranslate = view.findViewById<Button>(R.id.tabTranslate)
        val tabBooster = view.findViewById<Button>(R.id.tabBooster)
        val tabTools = view.findViewById<Button>(R.id.tabTools)
        val tabSystem = view.findViewById<Button>(R.id.tabSystem)
        val sectionTranslate = view.findViewById<View>(R.id.sectionTranslate)
        val sectionBooster = view.findViewById<View>(R.id.sectionBooster)
        val sectionTools = view.findViewById<View>(R.id.sectionTools)
        val sectionSystem = view.findViewById<View>(R.id.sectionSystem)
        val btnBoost = view.findViewById<Button>(R.id.btnBoost)
        val cbBg = view.findViewById<android.widget.CheckBox>(R.id.cbBg)
        val cbRam = view.findViewById<android.widget.CheckBox>(R.id.cbRam)
        val cbNotif = view.findViewById<android.widget.CheckBox>(R.id.cbNotif)
        val panelHeader = view.findViewById<View>(R.id.panelHeader)

        // ---------- Save preferensi (SharedPreferences) ----------
        val prefs = getSharedPreferences("lingo_panel_prefs", MODE_PRIVATE)

        cbBg.isChecked = prefs.getBoolean("cb_bg", true)
        cbRam.isChecked = prefs.getBoolean("cb_ram", true)
        cbNotif.isChecked = prefs.getBoolean("cb_notif", false)

        fun savePrefs() {
            prefs.edit()
                .putBoolean("cb_bg", cbBg.isChecked)
                .putBoolean("cb_ram", cbRam.isChecked)
                .putBoolean("cb_notif", cbNotif.isChecked)
                .putString("last_source", acSource.text.toString())
                .putString("last_target", acTarget.text.toString())
                .apply()
        }

        cbBg.setOnCheckedChangeListener { _, _ -> savePrefs() }
        cbRam.setOnCheckedChangeListener { _, _ -> savePrefs() }
        cbNotif.setOnCheckedChangeListener { _, _ -> savePrefs() }

        // Bikin panel bisa digeser lewat header-nya (mirip cara bubble digeser)
        run {
            var startX = 0
            var startY = 0
            var touchStartX = 0f
            var touchStartY = 0f

            panelHeader.setOnTouchListener { _, event ->
                val currentParams = panelParams ?: return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = currentParams.x
                        startY = currentParams.y
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchStartX).toInt()
                        val dy = (event.rawY - touchStartY).toInt()
                        currentParams.x = startX + dx
                        currentParams.y = startY + dy
                        windowManager.updateViewLayout(view, currentParams)
                        true
                    }
                    else -> false
                }
            }
        }

        // Adapter khusus: SELALU nampilin daftar lengkap, ga peduli ada teks apa
        // di kolomnya. Jadi pas dipencet langsung muncul semua pilihan, bukan
        // hasil filter dari ketikan.
        class FullListAdapter(items: List<String>) :
            ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, items) {
            private val full = items
            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val r = FilterResults()
                        r.values = full
                        r.count = full.size
                        return r
                    }
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        notifyDataSetChanged()
                    }
                }
            }
        }

        val sourceAdapter = FullListAdapter(Languages.SOURCE_OPTIONS.map { it.label })
        val targetAdapter = FullListAdapter(Languages.TARGET_OPTIONS.map { it.label })
        acSource.setAdapter(sourceAdapter)
        acTarget.setAdapter(targetAdapter)

        // Default: pakai bahasa terakhir yang disimpan, kalau belum ada pakai Indonesia -> Inggris
        val savedSource = prefs.getString("last_source", null)
        val savedTarget = prefs.getString("last_target", null)
        acSource.setText(
            savedSource ?: Languages.SOURCE_OPTIONS.first { it.code == "id" }.label, false
        )
        acTarget.setText(
            savedTarget ?: Languages.TARGET_OPTIONS.first { it.code == "en" }.label, false
        )

        // Matikan keyboard & mode ketik: field ini cuma buat dipencet lalu pilih dari list
        acSource.keyListener = null
        acTarget.keyListener = null
        acSource.showSoftInputOnFocus = false
        acTarget.showSoftInputOnFocus = false

        acSource.setOnClickListener { acSource.showDropDown() }
        acTarget.setOnClickListener { acTarget.showDropDown() }

        fun resolveLang(input: String, options: List<Lang>): Lang? =
            options.find { it.label.equals(input.trim(), ignoreCase = true) }

        btnClose.setOnClickListener { showBubble() }

        btnMatikan.setOnClickListener { stopSelf() }

        fun setActiveTab(activeIndex: Int) {
            sectionTranslate.visibility = if (activeIndex == 0) View.VISIBLE else View.GONE
            sectionBooster.visibility = if (activeIndex == 1) View.VISIBLE else View.GONE
            sectionTools.visibility = if (activeIndex == 2) View.VISIBLE else View.GONE
            sectionSystem.visibility = if (activeIndex == 3) View.VISIBLE else View.GONE

            val activeColor = resources.getColor(R.color.rose, theme)
            val inactiveColor = resources.getColor(R.color.panel_dark, theme)
            val activeText = resources.getColor(R.color.text_light, theme)
            val inactiveText = resources.getColor(R.color.text_muted, theme)

            val tabs = listOf(tabTranslate, tabBooster, tabTools, tabSystem)
            tabs.forEachIndexed { i, tab ->
                val isActive = i == activeIndex
                tab.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(if (isActive) activeColor else inactiveColor)
                tab.setTextColor(if (isActive) activeText else inactiveText)
            }
        }

        tabTranslate.setOnClickListener { setActiveTab(0) }
        tabBooster.setOnClickListener { setActiveTab(1) }
        tabTools.setOnClickListener { setActiveTab(2) }
        tabSystem.setOnClickListener { setActiveTab(3) }

        setupTools(view, prefs)
        setupSystem(view)

        btnBoost.setOnClickListener {
            val hasUsageAccess = hasUsageAccessPermission()
            if (!hasUsageAccess) {
                android.widget.Toast.makeText(
                    this,
                    "Butuh izin \"Akses Penggunaan\" dulu. Membuka Settings...",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                return@setOnClickListener
            }

            var killedCount = 0
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager

            if (cbBg.isChecked) {
                killedCount = closeBackgroundApps(am)
            }
            if (cbRam.isChecked) {
                System.gc()
            }
            if (cbNotif.isChecked) {
                val nm = getSystemService(NotificationManager::class.java)
                nm.cancelAll()
            }

            android.widget.Toast.makeText(
                this,
                "Boost dijalankan. $killedCount proses background ditutup.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        btnCopy.setOnClickListener {
            val text = tvOutput.text.toString()
            if (text.isNotBlank()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Hasil terjemahan", text)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "Disalin ke clipboard", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        btnTranslate.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val sourceLang = resolveLang(acSource.text.toString(), Languages.SOURCE_OPTIONS)
            val targetLang = resolveLang(acTarget.text.toString(), Languages.TARGET_OPTIONS)

            if (sourceLang == null || targetLang == null) {
                tvOutput.text = "Pilih bahasa dari daftar yang muncul saat mengetik"
                return@setOnClickListener
            }

            savePrefs()

            btnTranslate.isEnabled = false
            btnTranslate.text = "Menerjemahkan..."
            pbLoading.visibility = View.VISIBLE

            scope.launch {
                try {
                    val result = TranslateApi.translate(text, sourceLang.code, targetLang.code)
                    tvOutput.text = result
                } catch (e: Exception) {
                    tvOutput.text = "Gagal menerjemahkan: ${e.message}"
                } finally {
                    btnTranslate.isEnabled = true
                    btnTranslate.text = "Terjemahkan"
                    pbLoading.visibility = View.GONE
                }
            }
        }
    }

    // ---------- Tools tab: 5 utilitas ringan ----------

    private var stopwatchJob: Job? = null
    private var stopwatchSeconds = 0
    private var stopwatchRunning = false

    private fun setupTools(view: View, prefs: android.content.SharedPreferences) {
        // --- 1) Stopwatch ---
        val tvStopwatch = view.findViewById<TextView>(R.id.tvStopwatch)
        val btnStopwatchToggle = view.findViewById<Button>(R.id.btnStopwatchToggle)
        val btnStopwatchReset = view.findViewById<Button>(R.id.btnStopwatchReset)

        fun formatStopwatch(total: Int): String {
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return "%02d:%02d:%02d".format(h, m, s)
        }
        tvStopwatch.text = formatStopwatch(stopwatchSeconds)
        btnStopwatchToggle.text = if (stopwatchRunning) "Jeda" else "Mulai"

        fun startStopwatch() {
            stopwatchRunning = true
            btnStopwatchToggle.text = "Jeda"
            stopwatchJob?.cancel()
            stopwatchJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    stopwatchSeconds++
                    tvStopwatch.text = formatStopwatch(stopwatchSeconds)
                }
            }
        }

        btnStopwatchToggle.setOnClickListener {
            if (stopwatchRunning) {
                stopwatchRunning = false
                stopwatchJob?.cancel()
                btnStopwatchToggle.text = "Mulai"
            } else {
                startStopwatch()
            }
        }
        btnStopwatchReset.setOnClickListener {
            stopwatchRunning = false
            stopwatchJob?.cancel()
            stopwatchSeconds = 0
            tvStopwatch.text = formatStopwatch(0)
            btnStopwatchToggle.text = "Mulai"
        }
        if (stopwatchRunning) startStopwatch()

        // --- 2) Quick Notes (auto-save ke SharedPreferences) ---
        val etNotes = view.findViewById<EditText>(R.id.etNotes)
        etNotes.setText(prefs.getString("quick_notes", ""))
        etNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("quick_notes", s?.toString() ?: "").apply()
            }
        })

        // --- 3) Kalkulator mini (parser +,-,*,/ tanpa library eksternal) ---
        val etCalc = view.findViewById<EditText>(R.id.etCalc)
        val tvCalcResult = view.findViewById<TextView>(R.id.tvCalcResult)
        etCalc.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val expr = s?.toString().orEmpty()
                val result = evalSimpleExpression(expr)
                tvCalcResult.text = if (result != null) "= ${trimZero(result)}" else "= ?"
            }
        })

        // --- 4) Reminder (AlarmManager, tetap jalan walau app ditutup) ---
        val etReminderText = view.findViewById<EditText>(R.id.etReminderText)
        val etReminderMinutes = view.findViewById<EditText>(R.id.etReminderMinutes)
        val btnSetReminder = view.findViewById<Button>(R.id.btnSetReminder)

        btnSetReminder.setOnClickListener {
            val text = etReminderText.text.toString().trim()
            val minutes = etReminderMinutes.text.toString().toIntOrNull()
            if (text.isEmpty() || minutes == null || minutes <= 0) {
                android.widget.Toast.makeText(this, "Isi teks & menit yang valid dulu", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val triggerAt = System.currentTimeMillis() + minutes * 60_000L
            val reqCode = System.currentTimeMillis().toInt()

            val intent = Intent(this, ReminderReceiver::class.java).apply {
                putExtra("reminder_text", text)
                putExtra("req_code", reqCode)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)

            android.widget.Toast.makeText(this, "Reminder dipasang $minutes menit lagi", android.widget.Toast.LENGTH_SHORT).show()
            etReminderText.setText("")
            etReminderMinutes.setText("")
        }

        // --- 5) Clipboard Manager (simpan manual, karena Android modern
        //          nggak izinkan baca clipboard otomatis di background) ---
        val clipboardList = view.findViewById<LinearLayout>(R.id.clipboardList)
        val btnClipboardSave = view.findViewById<Button>(R.id.btnClipboardSave)
        val btnClipboardClear = view.findViewById<Button>(R.id.btnClipboardClear)

        fun loadClipboardItems(): MutableList<String> {
            val raw = prefs.getString("clipboard_items", "") ?: ""
            return if (raw.isEmpty()) mutableListOf() else raw.split("\u0001").toMutableList()
        }
        fun saveClipboardItems(items: List<String>) {
            prefs.edit().putString("clipboard_items", items.joinToString("\u0001")).apply()
        }

        fun renderClipboardList() {
            clipboardList.removeAllViews()
            val items = loadClipboardItems()
            for (item in items.asReversed()) {
                val row = TextView(this).apply {
                    text = item
                    textSize = 12f
                    setTextColor(resources.getColor(R.color.text_light, theme))
                    setBackgroundResource(R.drawable.input_bg)
                    setPadding(8, 8, 8, 8)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = 6
                    layoutParams = lp
                    setOnClickListener {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Clipboard item", item))
                        android.widget.Toast.makeText(this@OverlayService, "Disalin", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                clipboardList.addView(row)
            }
        }
        renderClipboardList()

        btnClipboardSave.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString().trim()
                if (text.isNotEmpty()) {
                    val items = loadClipboardItems()
                    items.remove(text)
                    items.add(text)
                    if (items.size > 20) items.removeAt(0)
                    saveClipboardItems(items)
                    renderClipboardList()
                } else {
                    android.widget.Toast.makeText(this, "Clipboard kosong", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnClipboardClear.setOnClickListener {
            saveClipboardItems(emptyList())
            renderClipboardList()
        }
    }

    // ---------- System tab: info & kontrol sistem ----------

    private var cameraId: String? = null
    private var flashlightOn = false

    private fun setupSystem(view: View) {
        val tvBatteryInfo = view.findViewById<TextView>(R.id.tvBatteryInfo)
        val tvRamInfo = view.findViewById<TextView>(R.id.tvRamInfo)

        fun refreshBatteryAndRam() {
            val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
            val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = bm.isCharging
            tvBatteryInfo.text = if (isCharging) "🔋 Baterai: $pct% (mengisi daya)" else "🔋 Baterai: $pct%"

            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val usedMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
            val totalMb = memInfo.totalMem / (1024 * 1024)
            tvRamInfo.text = "🧠 RAM: ${usedMb}MB / ${totalMb}MB terpakai"
        }
        refreshBatteryAndRam()
        scope.launch {
            while (isActive) {
                delay(5000)
                refreshBatteryAndRam()
            }
        }

        // --- Volume media ---
        val tvVolLevel = view.findViewById<TextView>(R.id.tvVolLevel)
        val btnVolDown = view.findViewById<Button>(R.id.btnVolDown)
        val btnVolUp = view.findViewById<Button>(R.id.btnVolUp)
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager

        fun refreshVolume() {
            val cur = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            tvVolLevel.text = "$cur / $max"
        }
        refreshVolume()
        btnVolDown.setOnClickListener {
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_LOWER, 0
            )
            refreshVolume()
        }
        btnVolUp.setOnClickListener {
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_RAISE, 0
            )
            refreshVolume()
        }

        // --- Brightness (butuh izin Modify System Settings) ---
        val tvBrightLevel = view.findViewById<TextView>(R.id.tvBrightLevel)
        val btnBrightDown = view.findViewById<Button>(R.id.btnBrightDown)
        val btnBrightUp = view.findViewById<Button>(R.id.btnBrightUp)

        fun canWriteSettings() = android.provider.Settings.System.canWrite(this)
        fun requestWriteSettings() {
            android.widget.Toast.makeText(this, "Butuh izin \"Modify System Settings\". Membuka Settings...", android.widget.Toast.LENGTH_LONG).show()
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:$packageName"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        fun refreshBrightness() {
            val level = try {
                android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) { -1 }
            tvBrightLevel.text = if (level >= 0) "$level / 255" else "--"
        }
        refreshBrightness()

        fun changeBrightness(delta: Int) {
            if (!canWriteSettings()) { requestWriteSettings(); return }
            val current = try {
                android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) { 128 }
            val newVal = (current + delta).coerceIn(0, 255)
            android.provider.Settings.System.putInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, newVal)
            refreshBrightness()
        }
        btnBrightDown.setOnClickListener { changeBrightness(-25) }
        btnBrightUp.setOnClickListener { changeBrightness(25) }

        // --- Senter ---
        val btnFlashlight = view.findViewById<Button>(R.id.btnFlashlight)
        val cameraManager = getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        if (cameraId == null) {
            cameraId = try {
                cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            } catch (e: Exception) { null }
        }
        btnFlashlight.setOnClickListener {
            val id = cameraId
            if (id == null) {
                android.widget.Toast.makeText(this, "Perangkat ini nggak punya flash", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                flashlightOn = !flashlightOn
                cameraManager.setTorchMode(id, flashlightOn)
                btnFlashlight.text = if (flashlightOn) "🔦 Senter (ON)" else "🔦 Senter"
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Gagal akses senter: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // --- DND (butuh izin Notification Policy Access) ---
        val btnDnd = view.findViewById<Button>(R.id.btnDnd)
        val notificationManager = getSystemService(NotificationManager::class.java)

        fun refreshDndButton() {
            val isDnd = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            btnDnd.text = if (isDnd) "🔇 DND (ON)" else "🔇 DND"
        }
        refreshDndButton()
        btnDnd.setOnClickListener {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                android.widget.Toast.makeText(this, "Butuh izin \"Notification Policy Access\". Membuka Settings...", android.widget.Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                return@setOnClickListener
            }
            val isDnd = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            notificationManager.setInterruptionFilter(
                if (isDnd) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
            refreshDndButton()
        }

        // --- Kunci orientasi layar (butuh izin Modify System Settings) ---
        val btnOrientationLock = view.findViewById<Button>(R.id.btnOrientationLock)
        fun refreshOrientationButton() {
            val autoRotate = try {
                android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION) == 1
            } catch (e: Exception) { true }
            btnOrientationLock.text = if (!autoRotate) "🔒 Rotasi (Terkunci)" else "🔒 Kunci Rotasi"
        }
        refreshOrientationButton()
        btnOrientationLock.setOnClickListener {
            if (!canWriteSettings()) { requestWriteSettings(); return@setOnClickListener }
            val autoRotate = try {
                android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION) == 1
            } catch (e: Exception) { true }
            android.provider.Settings.System.putInt(
                contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION,
                if (autoRotate) 0 else 1
            )
            refreshOrientationButton()
        }

        // --- Kontrol musik (dispatch media key event) ---
        val btnMusicPrev = view.findViewById<Button>(R.id.btnMusicPrev)
        val btnMusicPlayPause = view.findViewById<Button>(R.id.btnMusicPlayPause)
        val btnMusicNext = view.findViewById<Button>(R.id.btnMusicNext)

        fun sendMediaKey(keyCode: Int) {
            val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
        }
        btnMusicPrev.setOnClickListener { sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        btnMusicPlayPause.setOnClickListener { sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        btnMusicNext.setOnClickListener { sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) }

        // --- Shortcut buka app lain ---
        fun openApp(packageName: String, label: String) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(launchIntent)
            } else {
                android.widget.Toast.makeText(this, "$label belum terinstall", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.btnOpenDiscord).setOnClickListener { openApp("com.discord", "Discord") }
        view.findViewById<Button>(R.id.btnOpenWhatsapp).setOnClickListener { openApp("com.whatsapp", "WhatsApp") }
        view.findViewById<Button>(R.id.btnOpenBrowser).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://google.com"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    /** Parser ekspresi aritmatika sederhana: +, -, *, /, tanda kurung. Return null kalau invalid. */
    private fun evalSimpleExpression(expr: String): Double? {
        if (expr.isBlank()) return null
        return try {
            val pos = intArrayOf(0)
            val result = calcParseExpr(expr, pos)
            if (pos[0] != expr.length) null else result
        } catch (e: Exception) {
            null
        }
    }

    private fun calcPeek(expr: String, pos: IntArray): Char? =
        if (pos[0] < expr.length) expr[pos[0]] else null

    private fun calcParseExpr(expr: String, pos: IntArray): Double {
        var value = calcParseTerm(expr, pos)
        while (true) {
            when (calcPeek(expr, pos)) {
                '+' -> { pos[0]++; value += calcParseTerm(expr, pos) }
                '-' -> { pos[0]++; value -= calcParseTerm(expr, pos) }
                else -> return value
            }
        }
    }

    private fun calcParseTerm(expr: String, pos: IntArray): Double {
        var value = calcParseFactor(expr, pos)
        while (true) {
            when (calcPeek(expr, pos)) {
                '*' -> { pos[0]++; value *= calcParseFactor(expr, pos) }
                '/' -> { pos[0]++; value /= calcParseFactor(expr, pos) }
                else -> return value
            }
        }
    }

    private fun calcParseFactor(expr: String, pos: IntArray): Double {
        if (calcPeek(expr, pos) == '(') {
            pos[0]++
            val v = calcParseExpr(expr, pos)
            if (calcPeek(expr, pos) == ')') pos[0]++
            return v
        }
        if (calcPeek(expr, pos) == '-') {
            pos[0]++
            return -calcParseFactor(expr, pos)
        }
        val start = pos[0]
        while (calcPeek(expr, pos)?.let { it.isDigit() || it == '.' } == true) pos[0]++
        if (start == pos[0]) throw NumberFormatException()
        return expr.substring(start, pos[0]).toDouble()
    }

    private fun trimZero(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun hasUsageAccessPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    /**
     * Nutup proses background app lain yang baru dipakai (via UsageStats),
     * pakai ActivityManager.killBackgroundProcesses. Ini permintaan resmi ke sistem,
     * bukan simulasi -- tapi Android modern sendiri sudah mengelola memori dengan
     * efisien, jadi efeknya bisa saja kecil/tidak terasa untuk performa game.
     */
    private fun closeBackgroundApps(am: android.app.ActivityManager): Int {
        val usm = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 1000 * 60 * 60 * 6 // 6 jam terakhir
        val stats = usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_BEST, start, end
        ) ?: return 0

        val ownPackage = packageName
        val skip = setOf(
            ownPackage,
            "android", "com.android.systemui", "com.android.settings"
        )

        var count = 0
        for (stat in stats) {
            val pkg = stat.packageName
            if (pkg in skip) continue
            try {
                am.killBackgroundProcesses(pkg)
                count++
            } catch (e: Exception) {
                // Sebagian paket sistem tidak boleh ditutup, wajar dan aman diabaikan
            }
        }
        return count
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        if (flashlightOn) {
            try {
                cameraId?.let {
                    (getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager).setTorchMode(it, false)
                }
            } catch (e: Exception) { }
        }
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
    }
}
