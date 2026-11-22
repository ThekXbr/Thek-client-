package com.thek.cliente

import android.app.*
import android.app.ActivityManager
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.view.animation.OvershootInterpolator
import java.io.File
import kotlin.math.*

data class Stats(
    val fps: Float = 0f, val refresh: Float = 60f,
    val ramPct: Float? = null, val ramUsedMb: Long = 0, val ramTotalMb: Long = 0,
    val cpuPct: Float? = null, val cpuTemp: Float? = null,
    val gpuPct: Float? = null, val gpuTemp: Float? = null
)

object Sampler {
    private fun read(p: String) = try { File(p).readText().trim() } catch (e: Exception) { null }

    private fun zoneTemp(vararg keys: String): Float? {
        File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { z ->
            val type = read("${z.path}/type")?.lowercase() ?: return@forEach
            if (keys.any { type.contains(it) }) {
                val t = read("${z.path}/temp")?.toFloatOrNull() ?: return@forEach
                val c = if (t > 1000) t / 1000f else t
                if (c in 10f..120f) return c
            }
        }
        return null
    }

    private fun cpuLoad(): Float? {
        var sum = 0f; var n = 0
        File("/sys/devices/system/cpu").listFiles()?.filter { it.name.matches(Regex("cpu\\d+")) }?.forEach {
            val cur = read("${it.path}/cpufreq/scaling_cur_freq")?.toFloatOrNull()
            val max = read("${it.path}/cpufreq/cpuinfo_max_freq")?.toFloatOrNull()
            if (cur != null && max != null && max > 0) { sum += cur / max; n++ }
        }
        return if (n > 0) sum / n * 100f else null
    }

    private val gpuPaths = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/class/misc/mali0/device/utilisation"
    )
    private fun gpuLoad(): Float? = gpuPaths.firstNotNullOfOrNull { p ->
        read(p)?.filter { it.isDigit() || it == '.' }?.toFloatOrNull()
    }

    fun sample(ctx: Context, fps: Float, refresh: Float): Stats {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val total = mi.totalMem / 1048576; val used = total - mi.availMem / 1048576
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.takeIf { it > 0 }?.div(10f)
        return Stats(
            fps, refresh,
            used * 100f / total, used, total,
            cpuLoad(), zoneTemp("cpu", "tsens", "soc", "big", "little") ?: bat,
            gpuLoad(), zoneTemp("gpu")
        )
    }
}

class WindView(ctx: Context) : View(ctx) {
    var stats = Stats()
    private val d = resources.displayMetrics.density
    private val shown = FloatArray(4)
    private var phase = 0f
    private var frames = 0; private var lastT = 0L; var fps = 0f

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD90E1320.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d; color = 0x33FFFFFF }
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA4BF.toInt(); textSize = 11 * d; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 12 * d; textAlign = Paint.Align.RIGHT; typeface = Typeface.MONOSPACE }
    private val names = listOf("FPS", "RAM", "CPU", "GPU")
    private val clip = Path()

    private val cb = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            if (lastT == 0L) lastT = t
            frames++
            if (t - lastT >= 500_000_000L) { fps = frames * 1e9f / (t - lastT); frames = 0; lastT = t }
            phase += 0.03f
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(cb)
        alpha = 0f; scaleX = 0.8f; scaleY = 0.8f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(OvershootInterpolator(1.4f)).start()
    }
    override fun onDetachedFromWindow() { Choreographer.getInstance().removeFrameCallback(cb); super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val g = LinearGradient(0f, 0f, w.toFloat(), 0f, 0xFF6EE7F9.toInt(), 0xFFA78BFA.toInt(), Shader.TileMode.CLAMP)
        fill.shader = g
        wave.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0x336EE7F9, 0x33A78BFA, Shader.TileMode.CLAMP)
        clip.reset(); clip.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 18 * d, 18 * d, Path.Direction.CW)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) = setMeasuredDimension((200 * d).toInt(), (172 * d).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val pad = 14 * d
        c.save(); c.clipPath(clip)
        c.drawRect(0f, 0f, w, h, bg)
        for (k in 0..2) {
            val p = Path(); p.moveTo(0f, h)
            var x = 0f
            while (x <= w) { p.lineTo(x, h * (0.78f - k * 0.08f) + sin(x / w * 6.28f * (1 + k) + phase * (1 + k * .6f)) * 7 * d); x += 6 * d }
            p.lineTo(w, h); p.close(); c.drawPath(p, wave)
        }
        c.restore()
        c.drawRoundRect(0f, 0f, w, h, 18 * d, 18 * d, border)

        val s = stats
        val fr = floatArrayOf(
            (fps / s.refresh).coerceIn(0f, 1f), (s.ramPct ?: 0f) / 100f,
            (s.cpuPct ?: 0f) / 100f, (s.gpuPct ?: 0f) / 100f
        )
        val txt = listOf(
            "${fps.roundToInt()}",
            "${s.ramPct?.roundToInt() ?: "-"}%  ${s.ramUsedMb}/${s.ramTotalMb}MB",
            "${s.cpuPct?.roundToInt()?.toString() ?: "-"}%  ${s.cpuTemp?.roundToInt()?.toString() ?: "-"}\u00B0C",
            "${s.gpuPct?.roundToInt()?.toString() ?: "-"}%  ${s.gpuTemp?.roundToInt()?.toString() ?: "-"}\u00B0C"
        )
        val rowH = (h - pad * 2) / 4
        for (i in 0..3) {
            shown[i] += (fr[i] - shown[i]) * 0.12f
            val top = pad + i * rowH; val base = top + 14 * d
            c.drawText(names[i], pad, base, label)
            c.drawText(txt[i], w - pad, base, value)
            val by = base + 6 * d; val bw = w - pad * 2
            c.drawRoundRect(pad, by, pad + bw, by + 6 * d, 3 * d, 3 * d, track)
            c.drawRoundRect(pad, by, pad + max(6 * d, bw * shown[i]), by + 6 * d, 3 * d, 3 * d, fill)
        }
    }
}

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var view: WindView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val v = view ?: return
            val refresh = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
            v.stats = Sampler.sample(this@OverlayService, v.fps, refresh)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("hud", "Thek Cliente", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "hud").setContentTitle("Thek Cliente ativo")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).build())

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = WindView(this); view = v
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 120 }

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> { lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt(); wm.updateViewLayout(v, lp) }
            }; true
        }
        wm.addView(v, lp)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        view?.let { wm.removeView(it) }; view = null
        super.onDestroy()
    }
}
