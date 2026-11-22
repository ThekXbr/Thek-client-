package com.thek.cliente

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var view: WindView? = null
    private var menu: MenuView? = null
    private var maxHz = 60f
    private var tx = 40
    private var ty = 120
    private val handler = Handler(Looper.getMainLooper())

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val v = view
        if (v != null) {
            v.applyPrefs()
            wm.updateViewLayout(v, lp)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val v = view ?: return
            val p = Prefs.get(this@OverlayService)
            if (Perf.running) {
                val s = Sampler.sample(this@OverlayService, v.fps, maxHz, p.getBoolean("show_game", true))
                v.stats = s
                Perf.stats = s
                Perf.push(v.fps)
            }
            if (p.getBoolean("show_music", true)) Music.refresh(this@OverlayService)
            handler.postDelayed(this, 1000)
        }
    }

    // movimento suave: a janela persegue a posicao alvo
    private val ease = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            val v = view ?: return
            val dx = tx - lp.x
            val dy = ty - lp.y
            if (dx != 0 || dy != 0) {
                lp.x += if (abs(dx) < 2) dx else (dx * 0.4f).toInt()
                lp.y += if (abs(dy) < 2) dy else (dy * 0.4f).toInt()
                wm.updateViewLayout(v, lp)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onBind(i: Intent?) = null

    private fun toggleMenu() {
        val m = menu
        if (m != null) {
            wm.removeView(m)
            menu = null
            return
        }
        val dm = resources.displayMetrics
        val mw = minOf(dm.widthPixels * 0.94f, 380 * dm.density).toInt()
        val mh = (dm.heightPixels * 0.66f).toInt()
        var fl = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (Build.VERSION.SDK_INT >= 31) fl = fl or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        val mp = WindowManager.LayoutParams(
            mw, mh,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            fl,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            if (Build.VERSION.SDK_INT >= 31) blurBehindRadius = 40
        }
        val nm = MenuView(this) { toggleMenu() }
        menu = nm
        wm.addView(nm, mp)
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view != null) return START_STICKY

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("hud", "Thek Cliente", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val act = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Fechar HUD", stopPi
        ).build()
        val notif = Notification.Builder(this, "hud")
            .setContentTitle("Thek Cliente ativo")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .addAction(act)
            .build()
        startForeground(1, notif)

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        maxHz = wm.defaultDisplay.supportedModes.maxOf { it.refreshRate }

        var hf = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (Build.VERSION.SDK_INT >= 31) hf = hf or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            hf,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = tx
            y = ty
            preferredRefreshRate = maxHz
            if (Build.VERSION.SDK_INT >= 31) blurBehindRadius = 30
        }

        val v = WindView(this)
        view = v
        var ox = 0
        var oy = 0
        v.onDown = {
            ox = tx
            oy = ty
        }
        v.onMove = { dx, dy ->
            tx = ox + dx
            ty = oy + dy
        }
        v.onGear = { toggleMenu() }
        v.onClose = { stopSelf() }
        wm.addView(v, lp)
        Prefs.get(this).registerOnSharedPreferenceChangeListener(listener)
        handler.post(tick)
        Choreographer.getInstance().postFrameCallback(ease)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(ease)
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(listener)
        menu?.let { wm.removeView(it) }
        menu = null
        view?.let { wm.removeView(it) }
        view = null
        super.onDestroy()
    }
}
