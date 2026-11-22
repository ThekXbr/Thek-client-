package com.thek.cliente

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*

// Controles do HUD (usados no app e no menu do overlay)
fun hudControls(ctx: Context, root: LinearLayout) {
    val p = Prefs.get(ctx)
    val items = listOf(
        "FPS" to "show_fps", "RAM" to "show_ram", "CPU" to "show_cpu",
        "GPU" to "show_gpu", "Nome do jogo" to "show_game"
    )
    for ((t, k) in items) {
        root.addView(Switch(ctx).apply {
            text = t
            setTextColor(Color.WHITE)
            isChecked = p.getBoolean(k, true)
            setOnCheckedChangeListener { _, on -> p.edit().putBoolean(k, on).apply() }
        })
    }
    val cur = p.getInt("scale", 100)
    val lbl = TextView(ctx).apply {
        setTextColor(Color.WHITE)
        text = "Tamanho: $cur%"
        setPadding(0, 24, 0, 0)
    }
    root.addView(lbl)
    root.addView(SeekBar(ctx).apply {
        max = 140
        progress = cur - 60
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, user: Boolean) {
                lbl.text = "Tamanho: ${v + 60}%"
                p.edit().putInt("scale", v + 60).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    })
}

class MenuView(base: Context, private val onClose: () -> Unit) :
    FrameLayout(ContextThemeWrapper(base, android.R.style.Theme_Material_NoActionBar)) {

        private val pages = ArrayList<View>()
