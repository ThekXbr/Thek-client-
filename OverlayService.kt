package com.thek.cliente

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.WindowManager
import android.view.Gravity
import android.graphics.PixelFormat

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var hudView: WindView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        hudView = WindView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        try {
            windowManager?.addView(hudView, params)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "BOOST") {
            Tools.Boost(this)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (hudView != null) {
                windowManager?.removeView(hudView)
            }
        } catch (_: Exception) {}
    }
}
