package com.example.lingopanel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        val btnZoomOut = view.findViewById<TextView>(R.id.btnZoomOut)
        val btnZoomIn = view.findViewById<TextView>(R.id.btnZoomIn)
        val btnMatikan = view.findViewById<Button>(R.id.btnMatikanPanel)
        val tabTranslate = view.findViewById<Button>(R.id.tabTranslate)
        val tabBooster = view.findViewById<Button>(R.id.tabBooster)
        val sectionTranslate = view.findViewById<View>(R.id.sectionTranslate)
        val sectionBooster = view.findViewById<View>(R.id.sectionBooster)
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
        // Zoom panel: skala tampilan + ukuran window ikut menyesuaikan biar area sentuh pas
        var zoomLevel = 1.0f
        var baseWidthPx = 0
        var baseHeightPx = 0

        view.post {
            if (baseWidthPx == 0) baseWidthPx = view.width
            if (baseHeightPx == 0) baseHeightPx = view.height
        }

        fun applyZoom() {
            val currentParams = panelParams ?: return
            if (baseWidthPx == 0) baseWidthPx = view.width
            if (baseHeightPx == 0) baseHeightPx = view.height

            view.pivotX = 0f
            view.pivotY = 0f
            view.scaleX = zoomLevel
            view.scaleY = zoomLevel

            currentParams.width = (baseWidthPx * zoomLevel).toInt()
            currentParams.height = (baseHeightPx * zoomLevel).toInt()
            windowManager.updateViewLayout(view, currentParams)
        }

        btnZoomOut.setOnClickListener {
            zoomLevel = (zoomLevel - 0.1f).coerceAtLeast(0.7f)
            applyZoom()
        }

        btnZoomIn.setOnClickListener {
            zoomLevel = (zoomLevel + 0.1f).coerceAtMost(1.5f)
            applyZoom()
        }

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

        val sourceAdapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line,
            Languages.SOURCE_OPTIONS.map { it.label }
        )
        val targetAdapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line,
            Languages.TARGET_OPTIONS.map { it.label }
        )
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

        // Buka daftar lagi begitu field difokus/diketik, biar bisa langsung search
        acSource.setOnClickListener { acSource.showDropDown() }
        acTarget.setOnClickListener { acTarget.showDropDown() }

        fun resolveLang(input: String, options: List<Lang>): Lang? =
            options.find { it.label.equals(input.trim(), ignoreCase = true) }

        btnClose.setOnClickListener { showBubble() }

        btnMatikan.setOnClickListener { stopSelf() }

        fun setActiveTab(isTranslate: Boolean) {
            sectionTranslate.visibility = if (isTranslate) View.VISIBLE else View.GONE
            sectionBooster.visibility = if (isTranslate) View.GONE else View.VISIBLE

            val activeColor = resources.getColor(R.color.rose, theme)
            val inactiveColor = resources.getColor(R.color.panel_dark, theme)
            val activeText = resources.getColor(R.color.text_light, theme)
            val inactiveText = resources.getColor(R.color.text_muted, theme)

            tabTranslate.backgroundTintList =
                android.content.res.ColorStateList.valueOf(if (isTranslate) activeColor else inactiveColor)
            tabTranslate.setTextColor(if (isTranslate) activeText else inactiveText)

            tabBooster.backgroundTintList =
                android.content.res.ColorStateList.valueOf(if (isTranslate) inactiveColor else activeColor)
            tabBooster.setTextColor(if (isTranslate) inactiveText else activeText)
        }

        tabTranslate.setOnClickListener { setActiveTab(true) }
        tabBooster.setOnClickListener { setActiveTab(false) }

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
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
    }
}
