package com.example.lingopanel

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val overlayPermissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnMulai = findViewById<Button>(R.id.btnMulai)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnMulai.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                tvStatus.text = "Izinkan \"Tampil di atas aplikasi lain\" lalu tekan Mulai lagi"
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, overlayPermissionRequestCode)
                return@setOnClickListener
            }
            startOverlayAndMinimize()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayPermissionRequestCode && Settings.canDrawOverlays(this)) {
            startOverlayAndMinimize()
        }
    }

    private fun startOverlayAndMinimize() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        // Keluarkan app ke background — panel akan tampil mengambang
        moveTaskToBack(true)
    }
}
