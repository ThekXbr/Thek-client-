package com.thek.cliente

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.hardware.input.InputManager
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

class AnimBg(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val t = SystemClock.uptimeMillis() / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, 0f, h, Th.BG, 0xFF151A26.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        val cx = w * (0.5f + 0.35f * sin(t * 0.15f))
        val cy = h * (0.3f + 0.12f * cos(t * 0.11f))
        p.shader = RadialGradient(cx, cy, w * 0.9f, 0x224C8DFF, 0x004C8DFF, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
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
    private lateinit var music: MusicPage
    private lateinit var ins: InsightsPage
    private lateinit var tut: TutorialPage
    private lateinit var hudSw: Switch
    private var suppress = false
    private var leaving = false
    private var asked = false
    private var lastToggle = 0L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun beep() {
        try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80) } catch (e: Exception) {}
    }

    private val inListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) { checkInput() }
        override fun onInputDeviceRemoved(id: Int) { checkInput() }
        override fun onInputDeviceChanged(id: Int) {}
    }

    private fun checkInput() {
        val dv = Cursors.devices()
        if (dv.first && dv.second) {
            if (!asked) {
                asked = true
                pickCursor()
            }
        } else {
            asked = false
        }
    }

    private fun pickCursor() {
        AlertDialog.Builder(this)
            .setTitle("Teclado e mouse detectados")
            .setSingleChoiceItems(Cursors.names.toTypedArray(), Cursors.index(this)) { dlg, which ->
                Prefs.get(this).edit().putInt("cursor", which).apply()
                Cursors.apply(root)
                dlg.dismiss()
            }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!Hud.on) Perf.stats = Sampler.sample(this@MainActivity, 0f, 60f, true)
            Music.refresh(this@MainActivity)
            turbo.update()
            music.update()
            ins.update()
            tut.update()
            Cursors.apply(root)
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
                Toast.makeText(this, "Permita a sobreposição no Tutorial", Toast.LENGTH_SHORT).show()
                suppress = true
                hudSw.isChecked = false
                suppress = false
                showPage(4)
            }
        } else {
            startService(Intent(this, OverlayService::class.java).setAction("STOP"))
        }
    }

    private fun showPage(i: Int) {
        for (n in 0 until pagesBox.childCount) {
            pagesBox.getChildAt(n).visibility = if (n == i) View.VISIBLE else View.GONE
            val cell = navBox.getChildAt(n) as LinearLayout
            val col = if (n == i) Th.ACC else Th.DIM
            val v = cell.getChildAt(0)
            if (v is Ico) {
                v.set(v.kind, col)
            } else if (v is Ico2) {
                v.col = col
                v.invalidate()
            }
            (cell.getChildAt(1) as TextView).setTextColor(col)
        }
    }

    private fun buildUi() {
        ui = LinearLayout(this)
        ui.orientation = LinearLayout.VERTICAL
        ui.visibility = View.INVISIBLE
        ui.setPadding(dp(16), dp(44), dp(16), dp(12))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        val tt = TextView(this)
        tt.text = "Thek Turbo"
        tt.textSize = 18f
        tt.typeface = Typeface.DEFAULT_BOLD
        tt.setTextColor(Th.TEXT)
        top.addView(tt, LinearLayout.LayoutParams(0, -2, 1f))
        hudSw = Switch(this)
        hudSw.text = "HUD  "
        hudSw.setTextColor(Th.TEXT)
        hudSw.setOnCheckedChangeListener { _, on ->
            beep()
            if (!suppress) toggleHud(on)
        }
        top.addView(hudSw)
        ui.addView(top)

        pagesBox = FrameLayout(this)
        turbo = TurboPage(this)
        music = MusicPage(this)
        ins = InsightsPage(this)
        tut = TutorialPage(this)
        val set = LinearLayout(this)
        set.orientation = LinearLayout.VERTICAL
        set.setPadding(dp(8), dp(12), dp(8), dp(8))
        val st = TextView(this)
        st.text = "Ajustes"
        st.textSize = 20f
        st.typeface = Typeface.DEFAULT_BOLD
        st.setTextColor(Th.TEXT)
        set.addView(st)
        hudControls(this, set)
        val sv = ScrollView(this)
        sv.addView(set)
        pagesBox.addView(turbo, FrameLayout.LayoutParams(-1, -1))
        pagesBox.addView(music, FrameLayout.LayoutParams(-1, -1))
        pagesBox.addView(ins, FrameLayout.LayoutParams(-1, -1))
        pagesBox.addView(sv, FrameLayout.LayoutParams(-1, -1))
        pagesBox.addView(tut, FrameLayout.LayoutParams(-1, -1))
        ui.addView(pagesBox, LinearLayout.LayoutParams(-1, 0, 1f))

        navBox = LinearLayout(this)
        navBox.background = Th.card(this, 14)
        val items = listOf<Pair<View, String>>(
            Pair(Ico(this, IC.ROCKET, Th.DIM), "Turbo"),
            Pair(Ico(this, IC.NOTE, Th.DIM), "Música"),
            Pair(Ico2(this, Ico2.CHART, Th.DIM), "Consumo"),
            Pair(Ico(this, IC.GEAR, Th.DIM), "Ajustes"),
            Pair(Ico(this, IC.BOOK, Th.DIM), "Tutorial")
        )
        items.forEachIndexed { i, pr ->
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setPadding(0, dp(8), 0, dp(8))
            cell.addView(pr.first, LinearLayout.LayoutParams(dp(24), dp(24)))
            val tx = TextView(this)
            tx.text = pr.second
            tx.textSize = 10f
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
        showPage(if (Settings.canDrawOverlays(this)) 0 else 4)
    }

    private fun buildSplash() {
        val sp = LinearLayout(this)
        sp.orientation = LinearLayout.VERTICAL
        sp.gravity = Gravity.CENTER
        val rk = Ico(this, IC.ROCKET, Th.ACC)
        rk.alpha = 0f
        rk.translationY = dp(40).toFloat()
        sp.addView(rk, LinearLayout.LayoutParams(dp(64), dp(64)))
        val logo = TextView(this)
        logo.text = "Thek Turbo"
        logo.textSize = 28f
        logo.gravity = Gravity.CENTER
        logo.setTextColor(Th.TEXT)
        logo.typeface = Typeface.DEFAULT_BOLD
        logo.setPadding(0, dp(12), 0, 0)
        sp.addView(logo)
        val status = TextView(this)
        status.text = "Iniciando"
        status.setTextColor(Th.DIM)
        status.gravity = Gravity.CENTER
        status.setPadding(0, dp(12), 0, dp(10))
        sp.addView(status)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.progressTintList = ColorStateList.valueOf(Th.ACC)
        bar.progressBackgroundTintList = ColorStateList.valueOf(Th.LINE)
        sp.addView(bar, LinearLayout.LayoutParams(dp(200), dp(4)))
        root.addView(sp, FrameLayout.LayoutParams(-1, -1))

        rk.animate().alpha(1f).translationY(0f).setDuration(600)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
        ObjectAnimator.ofInt(bar, "progress", 0, 100).setDuration(1500).start()
        val msgs = listOf("Iniciando", "Carregando módulos", "Verificando permissões", "Pronto")
        msgs.forEachIndexed { i, m ->
            h.postDelayed({
                status.text = m
                if (i == 3) beep()
            }, 400L * i)
        }
        h.postDelayed({
            rk.animate().translationY(-dp(200).toFloat()).alpha(0f).setDuration(500).start()
            sp.animate().alpha(0f).setDuration(500).withEndAction { sp.visibility = View.GONE }.start()
            ui.visibility = View.VISIBLE
            for (i in 0 until ui.childCount) {
                val v = ui.getChildAt(i)
                v.alpha = 0f
                v.translationY = dp(30).toFloat()
                v.animate().alpha(1f).translationY(0f).setStartDelay(i * 80L).setDuration(400).start()
            }
            checkInput()
        }, 1700)
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
        setContentView(root)
        val im = getSystemService(Context.INPUT_SERVICE) as InputManager
        im.registerInputDeviceListener(inListener, h)
        h.post(tick)
        // os termos precisam ser aceitos antes de usar o app
        Terms.gate(this) { buildSplash() }
    }

    override fun onBackPressed() {
        if (leaving) return
        leaving = true
        beep()
        val bye = ByeView(this)
        bye.alpha = 0f
        bye.scaleX = 0.9f
        bye.scaleY = 0.9f
        root.addView(bye, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        bye.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start()
        ui.animate().alpha(0f).setDuration(500).start()
        h.postDelayed({
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }, 1200)
    }

    override fun onResume() {
        super.onResume()
        if (::tut.isInitialized) tut.update()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        val im = getSystemService(Context.INPUT_SERVICE) as InputManager
        im.unregisterInputDeviceListener(inListener)
        tone.release()
        super.onDestroy()
    }
}
