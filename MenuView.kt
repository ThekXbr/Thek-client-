
package com.thek.cliente

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.cos
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
    private var scale = 1f
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
    private var moved = false
    private var iconPkg: String? = null
    private var iconBmp: Bitmap? = null

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99121826.toInt() }
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
        color = 0x55FFFFFF
    }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * d
        strokeCap = Paint.Cap.ROUND
        color = 0xCCFFFFFF.toInt()
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * d
    }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20 * d
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
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
    private val ttl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11 * d
        typeface = Typeface.DEFAULT_BOLD
    }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA4BF.toInt()
        textSize = 10 * d
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

    private fun musicTop() = hh + max(1, rows.size) * rowH

    fun applyPrefs() {
        val p = Prefs.get(context)
        scale = p.getInt("scale", 100) / 100f
        collapsed = p.getBoolean("collapsed", false)
        showMusic = p.getBoolean("show_music", true)
        val keys = listOf("show_fps", "show_ram", "show_cpu", "show_gpu", "show_game")
        rows = keys.withIndex().filter { p.getBoolean(it.value, true) }.map { it.index }
        bh = musicTop() + (if (showMusic) mh else 0f) + pad * 0.6f
        fill.shader = LinearGradient(
            0f, 0f, bw, 0f,
            0xFF6EE7F9.toInt(), 0xFFA78BFA.toInt(), Shader.TileMode.CLAMP
        )
        ring.shader = LinearGradient(
            0f, 0f, cs, cs,
            0xFF6EE7F9.toInt(), 0xFFA78BFA.toInt(), Shader.TileMode.CLAMP
        )
        wave.shader = LinearGradient(
            0f, 0f, bw, 0f,
            0x336EE7F9, 0x33A78BFA, Shader.TileMode.CLAMP
        )
        gloss.shader = LinearGradient(
            0f, 0f, 0f, bh,
            0x38FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP
        )
        clip.reset()
        clip.addRoundRect(0f, 0f, bw, bh, 18 * d, 18 * d, Path.Direction.CW)
        requestLayout()
        invalidate()
    }

    private fun setCollapsed(on: Boolean) {
        Prefs.get(context).edit().putBoolean("collapsed", on).apply()
    }

    private fun gameIcon(pkg: String?): Bitmap? {
        if (pkg == null) {
            iconPkg = null
            iconBmp = null
            return null
        }
        if (pkg == iconPkg) return iconBmp
        iconPkg = pkg
        iconBmp = try {
            val dr = context.packageManager.getApplicationIcon(pkg)
            val bm = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            dr.setBounds(0, 0, 64, 64)
            dr.draw(Canvas(bm))
            bm
        } catch (e: Exception) {
            null
        }
        return iconBmp
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

    override
