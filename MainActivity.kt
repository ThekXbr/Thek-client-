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
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val p = Prefs.get(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0E1320"))
            addView(root)
        })

        root.addView(TextView(this).apply {
            text = "Thek Cliente"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        fun btn(t: String, a: () -> Unit) = root.addView(Button(this).apply {
            text = t
            setOnClickListener { a() }
        })
        fun label(t: String) = root.addView(TextView(this).apply {
            text = t
            textSize = 16f
            setTextColor(Color.parseColor("#9AA4BF"))
            setPadding(0, 36, 0, 8)
        })
        fun sw(t: String, key: String) = root.addView(Switch(this).apply {
            text = t
            setTextColor(Color.WHITE)
            isChecked = p.getBoolean(key, true)
            setOnCheckedChangeListener { _, on ->
                p.edit().putBoolean(key, on).apply()
            }
        })

        label("Permissoes")
        btn("1. Permitir sobreposicao") {
            val u = Uri.parse("package:$packageName")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, u))
        }
        btn("2. Acesso de uso (mostrar jogo)") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btn("3. Iniciar HUD") {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
        }
        btn("Parar HUD") {
            stopService(Intent(this, OverlayService::class.java))
        }

        label("O que mostrar")
        sw("FPS", "show_fps")
        sw("RAM", "show_ram")
        sw("CPU", "show_cpu")
        sw("GPU", "show_gpu")
        sw("Nome do jogo", "show_game")

        label("Tamanho")
        val cur = p.getInt("scale", 100)
        val sizeLbl = TextView(this).apply {
            setTextColor(Color.WHITE)
            text = "$cur%"
        }
        root.addView(sizeLbl)
        root.addView(SeekBar(this).apply {
            max = 140
            progress = cur - 60
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, user: Boolean) {
                    sizeLbl.text = "${v + 60}%"
                    p.edit().putInt("scale", v + 60).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
    }
}
