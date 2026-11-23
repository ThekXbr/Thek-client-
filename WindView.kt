package com.thek.cliente

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.max
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
    private val bw = 210 * d
    private val cs = 46 * d
    private var bh = 100 * d

    // escalas: alvo (scale/ms) e exibida (ds/dms), a exibida segue o alvo com mola
    private var scale = 1f
    private var ds = 1f
    private var vs = 0f
    private var ms = 1f
    private var dms = 1f
    private var vm = 0f

    private var backAlpha = 0.6f
    private var ready = false
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
    private var startMs = 1f
    private var moved = false

    // plano de fundo
    private var bgUri: String? = null
    private var bgBmp: Bitmap? = null
    private val bgM = Matrix()
    private val bgP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun paint(col: Int, f: Paint.() -> Unit = {}): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = col
        p.f()
        return p
    }

    private val bg = paint(0x99121826.toInt())
    private val gloss = paint(0)
    private val spec = paint(0)
    private val border = paint(0x55FFFFFF) { style = Paint.Style.STROKE; strokeWidth = 1.2f * d }
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

    init {
        applyPrefs()
        ds = scale
        dms = ms
        geometry()
        ready = true
    }

    private fun musicTop() = hh + max(1, rows.size) * rowH

    private fun loadBg(uriStr: String?) {
        if (uriStr == bgUri) return
        bgUri = uriStr
        bgBmp = null
        if (uriStr == null) return
        bgBmp = try {
            val uri = Uri.parse(uriStr)
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
            var sample = 1
            while (max(b.outWidth, b.outHeight) / sample > 800) sample *= 2
            val o = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        } catch (e: Exception) {
            null
        }
    }

    // recalcula tudo que depende da altura atual
    private fun geometry() {
        bh = musicTop() + (if (showMusic) mh * dms else 0f) + 14 * d
        gloss.shader = LinearGradient(0f, 0f, 0f, bh, 0x40FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP)
        spec.shader = RadialGradient(bw * 0.2f, 0f, bw * 0.9f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        border.shader = LinearGradient(
            0f, 0f, bw, bh,
            intArrayOf(0xCCFFFFFF.toInt(), 0x22FFFFFF, 0x77FFFFFF),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        clip.reset()
        clip.addRoundRect(0f, 0f, bw, bh, 18 * d, 18 * d, Path.Direction.CW)
    }

    fun applyPrefs() {
        val p = Prefs.get(context)
        scale = p.getInt("scale", 100) / 100f
        ms = p.getInt("music_scale", 100) / 100f
        backAlpha = p.getInt("bg_alpha", 60) / 100f
        val wasCollapsed = collapsed
        collapsed = p.getBoolean("collapsed", false)
        showMusic = p.getBoolean("show_music", true)
        val keys = listOf("show_fps", "show_ram", "show_cpu", "show_gpu", "show_game")
        rows = keys.withIndex().filter { p.getBoolean(it.value, true) }.map { it.index }
        loadBg(p.getString("bg_uri", null))
        val a = 0xFF6EE7F9.toInt()
        val b = 0xFFA78BFA.toInt()
        fill.shader = LinearGradient(0f, 0f, bw, 0f, a, b, Shader.TileMode.CLAMP)
        ring.shader = LinearGradient(0f, 0f, cs, cs, a, b, Shader.TileMode.CLAMP)
        wave.shader = LinearGradient(0f, 0f, bw, 0f, 0x336EE7F9, 0x33A78BFA, Shader.TileMode.CLAMP)
        geometry()
        if (ready && wasCollapsed != collapsed) {
            // "pulo" liquido ao minimizar/expandir
            scaleX = 0.7f
            scaleY = 0.7f
            animate().scaleX(1f).scaleY(1f).setDuration(380)
                .setInterpolator(OvershootInterpolator(2.2f)).start()
        }
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

            // mola das escalas (leve overshoot = movimento liquido)
            var changed = false
            vs += (scale - ds) * 0.18f
            vs *= 0.72f
            if (abs(vs) > 0.0004f || abs(scale - ds) > 0.001f) {
                ds += vs
                changed = true
            } else if (ds != scale) {
                ds = scale
                vs = 0f
                changed = true
            }
            vm += (ms - dms) * 0.18f
            vm *= 0.72f
            if (abs(vm) > 0.0004f || abs(ms - dms) > 0.001f) {
                dms += vm
                changed = true
            } else if (dms != ms) {
                dms = ms
                vm = 0f
                changed = true
            }
            if (changed) {
                geometry()
                requestLayout()
            }

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
            vx *= 0.85f
            vy *= 0.85f
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
            setMeasuredDimension((bw * ds).toInt(), (bh * ds).toInt())
        }
    }

    private fun track(e: MotionEvent) {
        vx = (vx * 0.5f + (e.rawX - lx) * 0.5f).coerceIn(-40f, 40f)
        vy = (vy * 0.5f + (e.rawY - ly) * 0.5f).coerceIn(-40f, 40f)
        lx = e.rawX
        ly = e.rawY
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val k = if (collapsed) 1f else ds
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
                startMs = ms
                mode = when {
                    // alca do canto inferior direito: tamanho do HUD inteiro
                    !collapsed && x > bw - 34 * d && y > bh - 34 * d -> 1
                    // alca do canto inferior esquerdo: tamanho so da parte de musica
                    !collapsed && showMusic && x < pad + 24 * d && y > bh - 16 * d -> 2
                    else -> 0
                }
                onDown?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - sx
                val dy = e.rawY - sy
                if (abs(dx) + abs(dy) > 8 * d) moved = true
                if (moved) {
                    when (mode) {
                        1 -> {
                            val s = ((bw * startScale + dx) / bw).coerceIn(0.6f, 2f)
                            Prefs.get(context).edit().putInt("scale", (s * 100).roundToInt()).apply()
                        }
                        2 -> {
                            val m = (startMs + dx / (140 * d)).coerceIn(0.7f, 1.6f)
                            Prefs.get(context).edit().putInt("music_scale", (m * 100).roundToInt()).apply()
                        }
                        else -> {
                            track(e)
                            onMove?.invoke(dx.toInt(), dy.toInt())
                        }
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
                    } else if (showMusic && y > musicTop() && y < musicTop() + mh * dms) {
                        val lx2 = (x - pad) / dms
                        val vw = (bw - 2 * pad) / dms
                        if (lx2 > vw - 24 * d) Music.next()
                        else if (lx2 > vw - 52 * d) Music.toggle()
                    }
                }
            }
        }
        return true
    }

    private fun fit(t: String, p: Paint, w: Float): String {
        val n = p.breakText(t, true, w, null)
        return if (n < t.length) t.take(max(0, n - 1)) + "\u2026" else t
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
        c.scale(ds, ds)
        c.save()
        c.clipPath(clip)

        // fundo (base escura + imagem) com transparencia ajustavel
        val layer = c.saveLayerAlpha(0f, 0f, bw, bh, (backAlpha.coerceIn(0f, 1f) * 255).toInt())
        c.drawRect(0f, 0f, bw, bh, bg)
        val img = bgBmp
        if (img != null) {
            val sc = max(bw / img.width, bh / img.height)
            bgM.reset()
            bgM.postScale(sc, sc)
            bgM.postTranslate((bw - img.width * sc) / 2f, (bh - img.height * sc) / 2f)
            c.drawBitmap(img, bgM, bgP)
        }
        c.restoreToCount(layer)

        // vidro: brilho de cima + reflexo no canto
        c.drawRect(0f, 0f, bw, bh, gloss)
        c.drawRect(0f, 0f, bw, bh, spec)
        for (n in 0..2) {
            val p = Path()
            p.moveTo(0f, bh)
            var x = 0f
            while (x <= bw) {
                val ang = x / bw * 6.28f * (1 + n) + phase * (1 + n * 0.6f)
                p.lineTo(x, bh * (0.8f - n * 0.08f) + sin(ang) * 7 * d)
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
        // alca direita (HUD inteiro)
        c.drawLine(bw - 14 * d, bh - 6 * d, bw - 6 * d, bh - 14 * d, icon)
        c.drawLine(bw - 9 * d, bh - 6 * d, bw - 6 * d, bh - 9 * d, icon)
        // alca esquerda (so musica)
        if (showMusic) {
            c.drawLine(pad, bh - 6 * d, pad - 8 * d, bh - 14 * d, icon)
            c.drawLine(pad - 5 * d, bh - 6 * d, pad - 8 * d, bh - 9 * d, icon)
        }

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
            c.drawLine(pad, top, bw - pad, top, border)
            // tudo da musica e desenhado numa area propria que escala sozinha
            val vw = (bw - 2 * pad) / dms
            c.save()
            c.translate(pad, top)
            c.scale(dms, dms)
            val rect = RectF(0f, 6 * d, 30 * d, 36 * d)
            val art = Music.art
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
            val maxW = vw - 38 * d - 50 * d
            val tt = Music.title ?: (if (Music.access) "Nada tocando" else "Ative o acesso")
            c.drawText(fit(tt, ttl, maxW), 38 * d, 18 * d, ttl)
            c.drawText(fit(Music.artist ?: "", dim, maxW), 38 * d, 31 * d, dim)
            HudParts.play(c, solid, vw - 38 * d, 21 * d, 6 * d, Music.playing)
            HudParts.next(c, solid, vw - 10 * d, 21 * d, 6 * d)
            c.restore()
        }
        c.restore()
    }
}
