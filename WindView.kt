package com.thek.cliente

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class WindView(ctx: Context) : View(ctx) {
    var stats = Stats()
    var fps = 0f

    private val d = resources.displayMetrics.density
    private val pad = 14 * d
    private val rowH = 32 * d
    private val bw = 200 * d
    private var bh = 100 * d
    private var scale = 1f
    private var rows = listOf<Int>()
    private val shown = FloatArray(5)
    private var phase = 0f
    private var frames = 0
    private var lastT = 0L

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD90E1320.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
        color = 0x33FFFFFF
    }
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA4BF.toInt()
        textSize = 11 * d
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.1f
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12 * d
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }
    private val names = listOf("FPS", "RAM", "CPU", "GPU", "JOGO")
    private val clip = Path()

    init { applyPrefs() }

    fun applyPrefs() {
        val p = Prefs.get(context)
        scale = p.getInt("scale", 100) / 100f
        val keys = listOf("show_fps", "show_ram", "show_cpu", "show_gpu", "show_game")
        rows = keys.withIndex().filter { p.getBoolean(it.value, true) }.map { it.index }
        bh = pad * 2 + max(1, rows.size) * rowH
        fill.shader = LinearGradient(
            0f, 0f, bw, 0f,
            0xFF6EE7F9.toInt(), 0xFFA78BFA.toInt(), Shader.TileMode.CLAMP
        )
        wave.shader = LinearGradient(
            0f, 0f, bw, 0f,
            0x336EE7F9, 0x33A78BFA, Shader.TileMode.CLAMP
        )
        clip.reset()
        clip.addRoundRect(0f, 0f, bw, bh, 18 * d, 18 * d, Path.Direction.CW)
        requestLayout()
        invalidate()
    }

    private val cb = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            if (lastT == 0L) lastT = t
            frames++
            if (t - lastT >= 500_000_000L) {
                fps = frames * 1e9f / (t - lastT)
                frames = 0
                lastT = t
            }
            phase += 0.03f
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(cb)
        alpha = 0f
        scaleX = 0.8f
        scaleY = 0.8f
        animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(cb)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension((bw * scale).toInt(), (bh * scale).toInt())
    }

    override fun onDraw(c: Canvas) {
        c.save()
        c.scale(scale, scale)

        c.save()
        c.clipPath(clip)
        c.drawRect(0f, 0f, bw, bh, bg)
        for (k in 0..2) {
            val p = Path()
            p.moveTo(0f, bh)
            var x = 0f
            while (x <= bw) {
                val ang = x / bw * 6.28f * (1 + k) + phase * (1 + k * 0.6f)
                p.lineTo(x, bh * (0.78f - k * 0.08f) + sin(ang) * 7 * d)
                x += 6 * d
            }
            p.lineTo(bw, bh)
            p.close()
            c.drawPath(p, wave)
        }
        c.restore()
        c.drawRoundRect(0f, 0f, bw, bh, 18 * d, 18 * d, border)

        val s = stats
        rows.forEachIndexed { n, id ->
            val top = pad + n * rowH
            val base = top + 14 * d
            val txt: String
            val frac: Float?
            when (id) {
                0 -> {
                    txt = "${fps.roundToInt()}  /${s.maxHz.roundToInt()}Hz"
                    frac = fps / s.maxHz
                }
                1 -> {
                    txt = "${s.ramPct?.roundToInt() ?: "-"}%  ${s.ramUsedMb}/${s.ramTotalMb}MB"
                    frac = (s.ramPct ?: 0f) / 100f
                }
                2 -> {
                    txt = "${s.cpuPct?.roundToInt() ?: "-"}%  ${s.cpuTemp?.roundToInt() ?: "-"}\u00B0C"
                    frac = (s.cpuPct ?: 0f) / 100f
                }
                3 -> {
                    txt = "${s.gpuPct?.roundToInt() ?: "-"}%  ${s.gpuTemp?.roundToInt() ?: "-"}\u00B0C"
                    frac = (s.gpuPct ?: 0f) / 100f
                }
                else -> {
                    txt = (s.game ?: "-").take(16)
                    frac = null
                }
            }
            c.drawText(names[id], pad, base, label)
            c.drawText(txt, bw - pad, base, value)
            if (frac != null) {
                shown[id] += (frac.coerceIn(0f, 1f) - shown[id]) * 0.12f
                val by = base + 6 * d
                val w = bw - pad * 2
                c.drawRoundRect(pad, by, pad + w, by + 6 * d, 3 * d, 3 * d, track)
                val fw = max(6 * d, w * shown[id])
                c.drawRoundRect(pad, by, pad + fw, by + 6 * d, 3 * d, 3 * d, fill)
            }
        }
        c.restore()
    }
}
