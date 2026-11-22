package com.thek.cliente

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var view: WindView? = null
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

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (view != null) return START_STICKY

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("hud", "Thek Cliente", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = Notification.Builder(this, "hud")
            .setContentTitle("Thek Cliente ativo")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
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
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    ox = lp.x
                    oy = lp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ox + (e.rawX - sx).toInt()
                    lp.y = oy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(v, lp)
                }
            }
            true
        }
        wm.addView(v, lp)
        Prefs.get(this).registerOnSharedPreferenceChangeListener(listener)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(listener)
        view?.let { wm.removeView(it) }
        view = null
        super.onDestroy()
    }
}
