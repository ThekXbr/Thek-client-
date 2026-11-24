package com.thek.cliente

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.*
import kotlin.math.cos
import kotlin.math.sin

class AnimBg(c: android.content.Context) : View(c) {
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
    private lateinit var root: FrameLayout
    private lateinit var ui: LinearLayout
    private lateinit var pagesBox: FrameLayout
    private lateinit var navBox: LinearLayout
    private lateinit var turbo: TurboPage
    private lateinit var tut: TutorialPage
    private lateinit var hudSw: Switch
    private var suppress = false
    private var leaving = false
    private var lastToggle = 0L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun beep() {
        try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80) } catch (e: Exception) {}
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!Hud.on) Perf.stats = Sampler.sample(this@MainActivity, 0f, 60f, true)
            turbo.update()
            tut.update()
            val settled = System.currentTimeMillis() - lastToggle > 2500
            if (settled && hudSw.isChecked != Hud.on) {
                suppress = true
                hudSw.isChecked = Hud.on
                suppress = false
            }
            h.postDelayed(this, 1000)
        }
    }

    private fun toggleHud(on: Boolean) {
        lastToggle = System.currentTimeMillis()
        if (on) {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            } else {
                Toast.makeText(this, "Permita a sobreposicao no Tutorial", Toast.LENGTH_SHORT).show()
                suppress = true
                hudSw.isChecked = false
                suppress = false
                showPage(2)
            }
        } else {
            startService(Intent(this, OverlayService::class.java).setAction("STOP"))
        }
    }

    private fun showPage(i: Int) {
        for (n in 0 until pagesBox.childCount) {
            pagesBox.getChildAt(n).visibility = if (n == i) View.VISIBLE else View.GONE
            val cell = navBox.getChildAt(n) as LinearLayout
            val ic = cell.getChildAt(0) as Ico
            val col = if (n == i) 0xFF6EE7F9.toInt() else 0xFF7A849E.toInt()
            ic.set(ic.kind, col)
            (cell.getChildAt(1) as TextView).setTextColor(col)
        }
    }

    private fun buildUi() {
        ui = LinearLayout(this)
        ui.orientation = LinearLayout.VERTICAL
        ui.visibility = View.INVISIBLE
        ui.setPadding(dp(14), dp(40), dp(14), dp(10))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        val tt = TextView(this)
        tt.text = "Thek Cliente"
        tt.textSize = 13f
        tt.setTextColor(0xFF9AA4BF.toInt())
        top.addView(tt, LinearLayout.LayoutParams(0, -2, 1f))
        hudSw = Switch(this)
        hudSw.text = "HUD  "
        hudSw.setTextColor(Color.WHITE)
        hudSw.setOnCheckedChangeListener { _, on ->
            beep()
            if (!suppress) toggleHud(on)
        }
        top.addView(hudSw)
        ui.addView(top)

        pagesBox = FrameLayout(this)
        turbo = TurboPage(this)
        val p0 = ScrollView(this)
        p0.addView(turbo)
        val set = LinearLayout(this)
        set.orientation = LinearLayout.VERTICAL
        set.setPadding(dp(8), dp(12), dp(8), dp(8))
        val st = TextView(this)
        st.text = "Ajustes do HUD"
        st.textSize = 20f
        st.setTextColor(0xFF6EE7F9.toInt())
        set.addView(st)
        hudControls(this, set)
        val p1 = ScrollView(this)
        p1.addView(set)
        tut = TutorialPage(this)
        pagesBox.addView(p0, FrameLayout.LayoutParams(-1, -1))
        pagesBox.addView(p1, FrameLayout.LayoutParams(-1, -1))
        pagesBox.addView(tut, FrameLayout.LayoutParams(-1, -1))
        ui.addView(pagesBox, LinearLayout.LayoutParams(-1, 0, 1f))

        navBox = LinearLayout(this)
        navBox.background = glassBg(this, 20)
        val items = listOf(IC.ROCKET to "Turbo", IC.GEAR to "Ajustes", IC.BOOK to "Tutorial")
        items.forEachIndexed { i, pr ->
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setPadding(0, dp(8), 0, dp(8))
            cell.addView(Ico(this, pr.first), LinearLayout.LayoutParams(dp(26), dp(26)))
            val tx = TextView(this)
            tx.text = pr.second
            tx.textSize = 11f
            tx.gravity = Gravity.CENTER
            cell.addView(tx)
            cell.setOnClickListener {
                beep()
                showPage(i)
            }
            navBox.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val nl = LinearLayout.LayoutParams(-1, -2)
        nl.setMargins(0, dp(8), 0, 0)
        ui.addView(navBox, nl)
        showPage(if (Settings.canDrawOverlays(this)) 0 else 2)
    }

    private fun buildSplash() {
        val sp = LinearLayout(this)
        sp.orientation = LinearLayout.VERTICAL
        sp.gravity = Gravity.CENTER
        val rk = Ico(this, IC.ROCKET, 0xFF6EE7F9.toInt())
        rk.alpha = 0f
        rk.scaleX = 0.5f
        rk.scaleY = 0.5f
        rk.translationY = dp(60).toFloat()
        sp.addView(rk, LinearLayout.LayoutParams(dp(84), dp(84)))
        val logo = TextView(this)
        logo.text = "THEK TURBO"
        logo.textSize = 34f
        logo.gravity = Gravity.CENTER
        logo.setTextColor(Color.WHITE)
        logo.typeface = Typeface.DEFAULT_BOLD
        logo.letterSpacing = 0.12f
        sp.addView(logo)
        val status = TextView(this)
        status.text = "Iniciando..."
        status.setTextColor(0xFF6EE7F9.toInt())
        status.gravity = Gravity.CENTER
        status.setPadding(0, dp(16), 0, dp(8))
        sp.addView(status)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        sp.addView(bar, LinearLayout.LayoutParams(dp(220), dp(6)))
        root.addView(sp, FrameLayout.LayoutParams(-1, -1))

        rk.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(800)
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
            rk.animate().translationY(-dp(260).toFloat()).alpha(0f).setDuration(600).start()
            sp.animate().alpha(0f).setDuration(600).withEndAction { sp.visibility = View.GONE }.start()
            ui.visibility = View.VISIBLE
            for (i in 0 until ui.childCount) {
                val v = ui.getChildAt(i)
                v.alpha = 0f
                v.translationY = dp(40).toFloat()
                v.animate().alpha(1f).translationY(0f).setStartDelay(i * 90L).setDuration(450).start()
            }
        }, 2100)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        root = FrameLayout(this)
        root.addView(AnimBg(this))
        buildUi()
        root.addView(ui, FrameLayout.LayoutParams(-1, -1))
        buildSplash()
        setContentView(root)
        h.post(tick)
    }

    // despedida ao sair do app
    override fun onBackPressed() {
        if (leaving) return
        leaving = true
        beep()
        val bye = ByeView(this)
        bye.alpha = 0f
        bye.scaleX = 0.8f
        bye.scaleY = 0.8f
        root.addView(bye, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        bye.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(350).start()
        ui.animate().alpha(0f).setDuration(500).start()
        h.postDelayed({
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }, 1300)
    }

    override fun onResume() {
        super.onResume()
        if (::tut.isInitialized) tut.update()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        tone.release()
        super.onDestroy()
    }
}
