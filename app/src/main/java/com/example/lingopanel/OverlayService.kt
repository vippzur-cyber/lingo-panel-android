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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 200
        panelParams = params

        setupPanel(view)

        windowManager.addView(view, params)
    }

    private fun setupPanel(view: View) {
        val acSource = view.findViewById<AutoCompleteTextView>(R.id.acSource)
        val acTarget = view.findViewById<AutoCompleteTextView>(R.id.acTarget)
        val tvDetected = view.findViewById<TextView>(R.id.tvDetected)
        val etInput = view.findViewById<EditText>(R.id.etInput)
        val tvOutput = view.findViewById<TextView>(R.id.tvOutput)
        val btnTranslate = view.findViewById<Button>(R.id.btnTranslate)
        val btnClose = view.findViewById<TextView>(R.id.btnClose)
        val btnMatikan = view.findViewById<Button>(R.id.btnMatikanPanel)
        val tabTranslate = view.findViewById<Button>(R.id.tabTranslate)
        val tabBooster = view.findViewById<Button>(R.id.tabBooster)
        val sectionTranslate = view.findViewById<View>(R.id.sectionTranslate)
        val sectionBooster = view.findViewById<View>(R.id.sectionBooster)
        val btnBoost = view.findViewById<Button>(R.id.btnBoost)

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

        // Default: sumber "Deteksi Otomatis", target "Inggris"
        acSource.setText(Languages.SOURCE_OPTIONS.first { it.code == "auto" }.label, false)
        acTarget.setText(Languages.TARGET_OPTIONS.first { it.code == "en" }.label, false)

        // Buka daftar lagi begitu field difokus/diketik, biar bisa langsung search
        acSource.setOnClickListener { acSource.showDropDown() }
        acTarget.setOnClickListener { acTarget.showDropDown() }

        fun resolveLang(input: String, options: List<Lang>): Lang? =
            options.find { it.label.equals(input.trim(), ignoreCase = true) }

        btnClose.setOnClickListener { showBubble() }

        btnMatikan.setOnClickListener { stopSelf() }

        tabTranslate.setOnClickListener {
            sectionTranslate.visibility = View.VISIBLE
            sectionBooster.visibility = View.GONE
        }
        tabBooster.setOnClickListener {
            sectionTranslate.visibility = View.GONE
            sectionBooster.visibility = View.VISIBLE
        }

        btnBoost.setOnClickListener {
            val active = btnBoost.text == "Aktifkan Boost"
            btnBoost.text = if (active) "Matikan Boost" else "Aktifkan Boost"
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

            btnTranslate.isEnabled = false
            btnTranslate.text = "Menerjemahkan..."
            tvDetected.text = ""

            scope.launch {
                try {
                    var actualSource = sourceLang.code
                    if (sourceLang.code == "auto") {
                        try {
                            actualSource = TranslateApi.detectLanguage(text)
                            val label = Languages.ALL.find { it.code == actualSource }?.label ?: actualSource
                            tvDetected.text = "Terdeteksi: $label"
                        } catch (e: Exception) {
                            actualSource = "en"
                            tvDetected.text = "Deteksi gagal, pakai default Inggris"
                        }
                    }
                    val result = TranslateApi.translate(text, actualSource, targetLang.code)
                    tvOutput.text = result
                } catch (e: Exception) {
                    tvOutput.text = "Gagal menerjemahkan: ${e.message}"
                } finally {
                    btnTranslate.isEnabled = true
                    btnTranslate.text = "Terjemahkan"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
    }
}
