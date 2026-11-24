package com.thek.cliente

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class WindView(ctx: Context) : View(ctx) {
    var stats = Stats()
    var fps = 0f
    var onGear: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onDown: (() -> Unit)? = null
    var onMove: ((Int, Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val pad = 14 * d
    private val hh = 32 * d
    private val rowH = 32 * d
    private val mh = 42 * d
    private val foot = 18 * d
    private val bw = 210 * d
    private val cs = 46 * d
    private var bh = 100 * d
    private var scale = 1f
    private var op = 0.6f
    private var collapsed = false
    private var showMusic = true
    private var rows = listOf<Int>()
    private val shown = FloatArray(5)
    private var phase = 0f
    private var frames = 0
    private var lastT = 0L
    private var mode = 0
    private var sx = 0f
    private var sy = 0f
    private var lx = 0f
    private var ly = 0f
    private var vx = 0f
    private var vy = 0f
    private var blurred = false
    private var startScale = 1f
    private var pinch = 1f
    private var moved = false

    private fun paint(col: Int, f: Paint.() -> Unit = {}): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = col
        p.f()
        return p
    }

    private val bg = paint(0)
    private val img = paint(Color.WHITE)
    private val gloss = paint(0)
    private val sheen = paint(0)
    private val sheenM = Matrix()
    private val border = paint(0x55FFFFFF) { style = Paint.Style.STROKE; strokeWidth = 1.4f * d }
    private val icon = paint(0xCCFFFFFF.toInt()) {
        style = Paint.Style.STROKE
        strokeWidth = 2 * d
        strokeCap = Paint.Cap.ROUND
    }
    private val solid = paint(Color.WHITE)
    private val ring = paint(0) { style = Paint.Style.STROKE; strokeWidth = 3 * d }
    private val bubble = paint(Color.WHITE) {
        textSize = 20 * d
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val wave = paint(0)
    private val track = paint(0x22FFFFFF)
    private val fill = paint(0)
    private val label = paint(0xFF9AA4BF.toInt()) {
        textSize = 11 * d
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.1f
    }
    private val ttl = paint(Color.WHITE) { textSize = 11 * d; typeface = Typeface.DEFAULT_BOLD }
    private val dim = paint(0xFF9AA4BF.toInt()) { textSize = 10 * d }
    private val value = paint(Color.WHITE) {
        textSize = 12 * d
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }
    private val names = listOf("FPS", "RAM", "CPU", "GPU", "JOGO")
    private val clip = Path()

    // pinca com 2 dedos para mudar o tamanho
    private val sgd = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(g: ScaleGestureDetector): Boolean {
            pinch = scale
            return true
        }

        override fun onScale(g: ScaleGestureDetector): Boolean {
            pinch = (pinch * g.scaleFactor).coerceIn(0.6f, 2f)
            Prefs.get(context).edit().putInt("scale", (pinch * 100).roundToInt()).apply()
            return true
        }
    })

    init { applyPrefs() }

    private fun musicTop() = hh + max(1, rows.size) * rowH

    fun applyPrefs() {
        val p = Prefs.get(context)
        scale = p.getInt("scale", 100) / 100f
        op = p.getInt("opacity", 60) / 100f
        collapsed = p.getBoolean("collapsed", false)
        showMusic = p.getBoolean("show_music", true)
        val keys = listOf("show_fps", "show_ram", "show_cpu", "show_gpu", "show_game")
        rows = keys.withIndex().filter { p.getBoolean(it.value, true) }.map { it.index }
        bh = musicTop() + (if (showMusic) mh else 0f) + foot
        bg.color = Color.argb((op * 230).toInt(), 0x12, 0x18, 0x26)
        img.alpha = (op * 230).toInt()
        val a = 0xFF6EE7F9.toInt()
        val b = 0xFFA78BFA.toInt()
        fill.shader = LinearGradient(0f, 0f, bw, 0f, a, b, Shader.TileMode.CLAMP)
        ring.shader = LinearGradient(0f, 0f, cs, cs, a, b, Shader.TileMode.CLAMP)
        wave.shader = LinearGradient(0f, 0f, bw, 0f, 0x336EE7F9, 0x33A78BFA, Shader.TileMode.CLAMP)
        gloss.shader = LinearGradient(0f, 0f, 0f, bh, 0x38FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP)
        border.shader = LinearGradient(
            0f, 0f, bw, bh,
            intArrayOf(0x99FFFFFF.toInt(), 0x14FFFFFF, 0x66FFFFFF), null, Shader.TileMode.CLAMP
        )
        sheen.shader = LinearGradient(
            0f, 0f, bw * 0.7f, bh * 0.7f,
            intArrayOf(0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.MIRROR
        )
        clip.reset()
        clip.addRoundRect(0f, 0f, bw, bh, 18 * d, 18 * d, Path.Direction.CW)
        requestLayout()
        invalidate()
    }

    private fun setCollapsed(on: Boolean) {
        Prefs.get(context).edit().putBoolean("collapsed", on).apply()
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
            if (Build.VERSION.SDK_INT >= 31) {
                val bx = abs(vx) * 0.5f
                val by = abs(vy) * 0.5f
                if (bx + by > 1.5f) {
                    setRenderEffect(
                        RenderEffect.createBlurEffect(
                            max(bx, 0.1f).coerceAtMost(24f),
                            max(by, 0.1f).coerceAtMost(24f),
                            Shader.TileMode.CLAMP
                        )
                    )
                    blurred = true
                } else if (blurred) {
                    setRenderEffect(null)
                    blurred = false
                }
            }
            vx *= 0.88f
            vy *= 0.88f
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
        if (collapsed) {
            setMeasuredDimension(cs.toInt(), cs.toInt())
        } else {
            setMeasuredDimension((bw * scale).toInt(), (bh * scale).toInt())
        }
    }

    private fun track(e: MotionEvent) {
        vx = (vx * 0.5f + (e.rawX - lx) * 0.5f).coerceIn(-40f, 40f)
        vy = (vy * 0.5f + (e.rawY - ly) * 0.5f).coerceIn(-40f, 40f)
        lx = e.rawX
        ly = e.rawY
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        sgd.onTouchEvent(e)
        if (sgd.isInProgress || e.pointerCount > 1) {
            moved = true
            return true
        }
        val k = if (collapsed) 1f else scale
        val x = e.x / k
        val y = e.y / k
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.rawX
                sy = e.rawY
                lx = sx
                ly = sy
                moved = false
                startScale = scale
                mode = if (!collapsed && x > bw - 50 * d && y > bh - 22 * d) 1 else 0
                onDown?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - sx
                val dy = e.rawY - sy
                if (abs(dx) + abs(dy) > 8 * d) moved = true
                if (moved) {
                    if (mode == 1) {
                        val s = ((bw * startScale + dx) / bw).coerceIn(0.6f, 2f)
                        Prefs.get(context).edit().putInt("scale", (s * 100).roundToInt()).apply()
                    } else {
                        track(e)
                        onMove?.invoke(dx.toInt(), dy.toInt())
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    if (collapsed) {
                        setCollapsed(false)
                    } else if (y < hh + 4 * d) {
                        if (x > bw - 32 * d) onClose?.invoke()
                        else if (x > bw - 60 * d) onGear?.invoke()
                        else if (x > bw - 88 * d) setCollapsed(true)
                    } else if (showMusic && y > musicTop() && y < musicTop() + mh) {
                        if (x > bw - 38 * d) Now.next()
                        else if (x > bw - 66 * d) Now.toggle()
                    }
                }
            }
        }
        return true
    }

    private fun cropSrc(bm: Bitmap): Rect {
        val ar = bw / bh
        val br = bm.width.toFloat() / bm.height
        return if (br > ar) {
            val w = (bm.height * ar).toInt()
            Rect((bm.width - w) / 2, 0, (bm.width + w) / 2, bm.height)
        } else {
            val h = (bm.width / ar).toInt()
            Rect(0, (bm.height - h) / 2, bm.width, (bm.height + h) / 2)
        }
    }

    override fun onDraw(c: Canvas) {
        if (collapsed) {
            val r = cs / 2
            c.drawCircle(r, r, r - 2 * d, bg)
            c.drawCircle(r, r, r - 2 * d, gloss)
            c.drawCircle(r, r, r - 2 * d, ring)
            c.drawText("T", r, r - (bubble.ascent() + bubble.descent()) / 2, bubble)
            return
        }
        c.save()
        // achata um pouco na direcao do movimento (efeito liquido)
        val sqx = 1f - min(abs(vx) * 0.003f, 0.05f)
        val sqy = 1f - min(abs(vy) * 0.003f, 0.05f)
        c.scale(scale * sqx, scale * sqy)

        c.save()
        c.clipPath(clip)
        c.drawRect(0f, 0f, bw, bh, bg)
        val bm = Bg.bitmap(context)
        if (bm != null) c.drawBitmap(bm, cropSrc(bm), RectF(0f, 0f, bw, bh), img)
        c.drawRect(0f, 0f, bw, bh, gloss)
        sheenM.setTranslate(sin(phase * 0.5f) * bw * 0.4f + vx * 3f, cos(phase * 0.4f) * bh * 0.2f)
        sheen.shader.setLocalMatrix(sheenM)
        c.drawRect(0f, 0f, bw, bh, sheen)
        for (k in 0..2) {
            val p = Path()
            p.moveTo(0f, bh)
            var x = 0f
            while (x <= bw) {
                val ang = x / bw * 6.28f * (1 + k) + phase * (1 + k * 0.6f)
                p.lineTo(x, bh * (0.8f - k * 0.08f) + sin(ang) * 7 * d)
                x += 6 * d
            }
            p.lineTo(bw, bh)
            p.close()
            c.drawPath(p, wave)
        }
        c.restore()
        c.drawRoundRect(0f, 0f, bw, bh, 18 * d, 18 * d, border)

        val s = stats
        val ic = HudParts.gameIcon(context, s.gamePkg)
        var tx0 = pad
        if (ic != null) {
            c.drawBitmap(ic, null, RectF(pad, 7 * d, pad + 18 * d, 25 * d), null)
            tx0 = pad + 24 * d
        }
        c.drawText("THEK", tx0, hh * 0.66f, label)
        HudParts.header(c, icon, d, bw, hh / 2 + d)
        // puxador de redimensionar (faixa propria embaixo)
        c.drawLine(bw - 16 * d, bh - 5 * d, bw - 6 * d, bh - 15 * d, icon)
        c.drawLine(bw - 11 * d, bh - 5 * d, bw - 6 * d, bh - 10 * d, icon)

        rows.forEachIndexed { n, id ->
            val base = hh + n * rowH + 14 * d
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
                c.drawRoundRect(pad, by, pad + max(6 * d, w * shown[id]), by + 6 * d, 3 * d, 3 * d, fill)
            }
        }

        if (showMusic) {
            val top = musicTop()
            c.drawLine(pad, top, bw - pad, top, track)
            val rect = RectF(pad, top + 6 * d, pad + 30 * d, top + 36 * d)
            val art = Now.art
            if (art != null) {
                val pth = Path()
                pth.addRoundRect(rect, 6 * d, 6 * d, Path.Direction.CW)
                c.save()
                c.clipPath(pth)
                c.drawBitmap(art, null, rect, null)
                c.restore()
            } else {
                c.drawRoundRect(rect, 6 * d, 6 * d, track)
            }
            val tt = Now.title ?: "Nada tocando"
            c.drawText(tt.take(15), pad + 38 * d, top + 18 * d, ttl)
            c.drawText((Now.artist ?: "").take(18), pad + 38 * d, top + 31 * d, dim)
            HudParts.play(c, solid, bw - 52 * d, top + 21 * d, 6 * d, Now.playing)
            HudParts.next(c, solid, bw - 24 * d, top + 21 * d, 6 * d)
        }
        c.restore()
    }
}
