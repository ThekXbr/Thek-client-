package com.thek.cliente

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var view: WindView? = null
    private var menu: MenuView? = null
    private var maxHz = 60f
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
            val game = Prefs.get(this@OverlayService).getBoolean("show_game", true)
            v.stats = Sampler.sample(this@OverlayService, v.fps, maxHz, game)
            handler.postDelayed(this, 1000)
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
        val mh = (dm.heightPixels * 0.62f).toInt()
        val mp = WindowManager.LayoutParams(
            mw, mh,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
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

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
            preferredRefreshRate = maxHz
        }

        val v = WindView(this)
        view = v
        var ox = 0
        var oy = 0
        v.onDown = {
            ox = lp.x
            oy = lp.y
        }
        v.onMove = { dx, dy ->
            lp.x = ox + dx
            lp.y = oy + dy
            wm.updateViewLayout(v, lp)
        }
        v.onGear = { toggleMenu() }
        v.onClose = { stopSelf() }
        wm.addView(v, lp)
        Prefs.get(this).registerOnSharedPreferenceChangeListener(listener)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(listener)
        menu?.let { wm.removeView(it) }
        menu = null
        view?.let { wm.removeView(it) }
        view = null
        super.onDestroy()
    }
}
