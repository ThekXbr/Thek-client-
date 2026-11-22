package com.thek.cliente

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0E1320")); setPadding(60, 60, 60, 60)
        }
        root.addView(TextView(this).apply {
            text = "Thek Cliente"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        fun btn(t: String, a: () -> Unit) = root.addView(Button(this).apply { text = t; setOnClickListener { a() } })

        btn("1. Permitir sobreposicao") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("2. Iniciar HUD") {
            if (Settings.canDrawOverlays(this)) startForegroundService(Intent(this, OverlayService::class.java))
        }
        btn("Parar HUD") { stopService(Intent(this, OverlayService::class.java)) }
        setContentView(root)
    }
}
