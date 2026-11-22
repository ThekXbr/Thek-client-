package com.thek.cliente

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.*
import kotlin.math.cos
import kotlin.math.sin

class AnimBg(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = java.util.Random(7)
    private val px = FloatArray(28) { rnd.nextFloat() }
    private val py = FloatArray(28) { rnd.nextFloat() }
    private val ps = FloatArray(28) { 0.0008f + rnd.nextFloat() * 0.0016f }

    override fun onDraw(c: Canvas) {
        val t = SystemClock.uptimeMillis() / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        val a = t * 0.25f
        val x = w / 2 + cos(a) * w / 2
        val y = h / 2 + sin(a) * h / 2
        p.shader = LinearGradient(
            w - x, h - y, x, y,
            0xFF0E1320.toInt(), 0xFF2A1F5C.toInt(), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        for (i in px.indices) {
            py[i] -= ps[i]
            if (py[i] < 0f) {
                py[i] = 1f
                px[i] = rnd.nextFloat()
            }
            p.color = 0x556EE7F9
            c.drawCircle(px[i] * w + sin(t + i) * 12f, py[i] * h, 3f + (i % 4) * 2f, p)
        }
        for (k in 0..2) {
            val path = Path()
            path.moveTo(0f, h)
            var xx = 0f
            while (xx <= w + 10f) {
                val ang = xx / w * 6.28f * (1 + k) + t * (1f + k * 0.5f)
                path.lineTo(xx, h * (0.9f - k * 0.04f) + sin(ang) * 18f)
                xx += 10f
            }
            path.lineTo(w, h)
            path.close()
            p.color = (0x30 shl 24) or (0x6EE7F9 - k * 0x203040)
            c.drawPath(path, p)
        }
        postInvalidateOnAnimation()
    }
}

class MainActivity : Activity() {
    private lateinit var tone: ToneGenerator
    private val h = Handler(Looper.getMainLooper())
    private lateinit var ovBtn: Button
    private lateinit var usBtn: Button
    private lateinit var permLbl: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun usageOk(): Boolean {
        val ao = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ao.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun beep() {
        try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80) } catch (e: Exception) {}
    }

    private fun mkBtn(t: String, a: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.background = GradientDrawable().apply {
            setColor(0x33FFFFFF)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x666EE7F9)
        }
        b.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
            } else if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
        b.setOnClickListener {
            beep()
            a()
        }
        return b
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val root = FrameLayout(this)
        root.addView(AnimBg(this))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(56), dp(24), dp(24))
        }
        root.addView(ScrollView(this).apply { addView(col) })
        setContentView(root)

        val logo = TextView(this).apply {
            text = "THEK\nCLIENTE"
            textSize = 40f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            alpha = 0f
            scaleX = 0.5f
            scaleY = 0.5f
        }
        val status = TextView(this).apply {
            text = "Iniciando..."
            setTextColor(0xFF6EE7F9.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        col.addView(logo)
        col.addView(status)
        col.addView(bar, LinearLayout.LayoutParams(-1, dp(6)))

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.INVISIBLE
        }
        col.addView(menu, LinearLayout.LayoutParams(-1, -2))

        permLbl = TextView(this).apply {
            text = "Permissoes necessarias"
            setTextColor(0xFF9AA4BF.toInt())
            setPadding(0, dp(24), 0, dp(8))
        }
        ovBtn = mkBtn("1. Permitir sobreposicao") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        usBtn = mkBtn("2. Acesso de uso (mostrar jogo)") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        val start = mkBtn("Iniciar HUD") {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            } else {
                Toast.makeText(this, "Permita a sobreposicao primeiro", Toast.LENGTH_SHORT).show()
            }
        }
        val stop = mkBtn("Parar HUD") {
            stopService(Intent(this, OverlayService::class.java))
        }
        val tip = TextView(this).apply {
            text = "No HUD: toque na engrenagem para abrir o menu, arraste o canto para mudar o tamanho e use o X para fechar."
            setTextColor(0xFF9AA4BF.toInt())
            textSize = 12f
            setPadding(0, dp(16), 0, dp(8))
        }
        menu.addView(permLbl)
        menu.addView(ovBtn)
        menu.addView(usBtn)
        menu.addView(start)
        menu.addView(stop)
        menu.addView(tip)
        hudControls(this, menu)

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(800)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
        ObjectAnimator.ofInt(bar, "progress", 0, 100).setDuration(2000).start()
        val msgs = listOf("Iniciando...", "Carregando modulos...", "Verificando permissoes...", "Pronto!")
        msgs.forEachIndexed { i, m ->
            h.postDelayed({
                status.text = m
                beep()
            }, 500L * i)
        }
        h.postDelayed({
            status.animate().alpha(0f).setDuration(300).start()
            bar.animate().alpha(0f).setDuration(300).start()
            menu.visibility = View.VISIBLE
            for (i in 0 until menu.childCount) {
                val v = menu.getChildAt(i)
                v.alpha = 0f
                v.translationY = dp(40).toFloat()
                v.animate().alpha(1f).translationY(0f).setStartDelay(i * 80L).setDuration(400).start()
            }
        }, 2100)
    }

    private fun refresh() {
        val ov = Settings.canDrawOverlays(this)
        val us = usageOk()
        ovBtn.visibility = if (ov) View.GONE else View.VISIBLE
        usBtn.visibility = if (us) View.GONE else View.VISIBLE
        permLbl.visibility = if (ov && us) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        tone.release()
        super.onDestroy()
    }
}
