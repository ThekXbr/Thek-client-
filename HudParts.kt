package com.thek.cliente

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * COMO USAR (resumo)
 *
 * 1) Escala: onde você usa `d` (densidade) para desenhar o HUD, use `d * HudPrefs.scale(ctx)`.
 *    Em HudParts.header/play/next basta passar esse valor no lugar de `d`.
 *
 * 2) Fundo: no MainActivity, abra o seletor com
 *      Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
 *    e no resultado:
 *      contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
 *      HudPrefs.setBgUri(this, uri.toString())
 *    Slider de transparência: HudPrefs.setBgAlpha(this, valor 0f..1f)
 *
 * 3) No onDraw do HUD: HudGlass.draw(canvas, rect, d, bitmapDoFundo, HudPrefs.bgAlpha(ctx))
 *    antes de desenhar textos/ícones.
 *
 * 4) Movimento suave: use Spring para posição X/Y, escala e abrir/minimizar.
 */

/** Configurações salvas. */
object HudPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("hud_style", Context.MODE_PRIVATE)

    fun scale(c: Context): Float = sp(c).getFloat("scale", 1f)
    fun setScale(c: Context, v: Float) =
        sp(c).edit().putFloat("scale", v.coerceIn(0.6f, 2.2f)).apply()

    fun bgAlpha(c: Context): Float = sp(c).getFloat("bgAlpha", 0.6f)
    fun setBgAlpha(c: Context, v: Float) =
        sp(c).edit().putFloat("bgAlpha", v.coerceIn(0f, 1f)).apply()

    fun bgUri(c: Context): String? = sp(c).getString("bgUri", null)
    fun setBgUri(c: Context, uri: String?) = sp(c).edit().putString("bgUri", uri).apply()
}

/** Carrega o plano de fundo já reduzido (evita estourar memória). */
object HudBackground {
    private var cachedUri: String? = null
    private var cachedBmp: Bitmap? = null

    fun load(ctx: Context, maxSide: Int = 1024): Bitmap? {
        val uriStr = HudPrefs.bgUri(ctx) ?: return null
        if (uriStr == cachedUri) return cachedBmp
        cachedUri = uriStr
        cachedBmp = try {
            val uri = Uri.parse(uriStr)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            null
        }
        return cachedBmp
    }

    fun clear() {
        cachedUri = null
        cachedBmp = null
    }
}

/** Desenha o painel estilo "liquid glass". */
object HudGlass {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val m = Matrix()

    /**
     * @param r        retângulo do HUD
     * @param d        densidade já multiplicada pela escala
     * @param bg       imagem de fundo (ou null)
     * @param bgAlpha  0f..1f, transparência da imagem
     */
    fun draw(c: Canvas, r: RectF, d: Float, bg: Bitmap?, bgAlpha: Float) {
        val radius = 24f * d
        path.reset()
        path.addRoundRect(r, radius, radius, Path.Direction.CW)

        c.save()
        c.clipPath(path)

        // base escura translúcida
        fill.color = Color.argb(110, 12, 14, 22)
        c.drawRect(r, fill)

        // imagem de fundo (center-crop) com transparência ajustável
        if (bg != null) {
            val s = max(r.width() / bg.width, r.height() / bg.height)
            m.reset()
            m.postScale(s, s)
            m.postTranslate(
                r.left + (r.width() - bg.width * s) / 2f,
                r.top + (r.height() - bg.height * s) / 2f
            )
            imgPaint.alpha = (bgAlpha.coerceIn(0f, 1f) * 255).toInt()
            c.drawBitmap(bg, m, imgPaint)
        }

        // camada de vidro: branco suave de cima pra baixo
        tint.shader = LinearGradient(
            r.left, r.top, r.left, r.bottom,
            intArrayOf(Color.argb(60, 255, 255, 255), Color.argb(14, 255, 255, 255)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(r, tint)

        // brilho líquido no canto superior esquerdo
        sheen.shader = RadialGradient(
            r.left + r.width() * 0.2f, r.top, r.width() * 0.9f,
            Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        c.drawRect(r, sheen)

        c.restore()

        // borda de vidro: clara em cima/esquerda, quase invisível embaixo/direita
        border.strokeWidth = 1.2f * d
        border.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(Color.argb(190, 255, 255, 255), Color.argb(25, 255, 255, 255), Color.argb(90, 255, 255, 255)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(path, border)
    }

    /** Desenha a alça de redimensionar no canto inferior direito. Retorna a área tocável. */
    fun resizeHandle(c: Canvas, r: RectF, d: Float, p: Paint): RectF {
        val s = 14f * d
        val x = r.right - 10f * d
        val y = r.bottom - 10f * d
        c.drawLine(x - s, y, x, y - s, p)
        c.drawLine(x - s * 0.55f, y, x, y - s * 0.55f, p)
        return RectF(r.right - 36f * d, r.bottom - 36f * d, r.right, r.bottom)
    }
}

/** Desfoca o que está atrás do overlay (Android 12+ e aparelhos compatíveis). */
object HudBlur {
    fun apply(wm: WindowManager, view: android.view.View, lp: WindowManager.LayoutParams, radius: Int = 40) {
        if (Build.VERSION.SDK_INT >= 31) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = radius
            wm.updateViewLayout(view, lp)
        }
    }
}

/** Mola suave: use para posição, escala, abrir/minimizar. */
class Spring(
    var value: Float,
    private val stiffness: Float = 220f,
    private val damping: Float = 24f,
    private val onUpdate: (Float) -> Unit
) {
    var target = value
        private set
    private var vel = 0f
    private var last = 0L
    private var running = false

    private val cb = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            if (last == 0L) last = t
            val dt = min((t - last) / 1_000_000_000f, 0.032f)
            last = t
            val force = -stiffness * (value - target) - damping * vel
            vel += force * dt
            value += vel * dt
            if (abs(vel) < 0.01f && abs(value - target) < 0.01f) {
                value = target
                vel = 0f
                running = false
                last = 0L
                onUpdate(value)
            } else {
                onUpdate(value)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun animateTo(t: Float) {
        target = t
        if (!running) {
            running = true
            last = 0L
            Choreographer.getInstance().postFrameCallback(cb)
        }
    }

    fun snap(v: Float) {
        value = v
        target = v
        vel = 0f
        onUpdate(v)
    }
}

/**
 * Redimensionar o HUD: pinça com dois dedos OU arrastar a alça do canto.
 *
 * No onTouchEvent do HUD:
 *   if (scaler.onTouch(e, handleRect)) return true
 */
class HudScaler(
    private val ctx: Context,
    private val baseWidthPx: () -> Float,       // largura do HUD com escala 1.0
    private val onScaleChanged: (Float) -> Unit // chame invalidate()/updateViewLayout aqui
) {
    private var dragging = false
    private var startX = 0f
    private var startScale = 1f

    private val pinch = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(det: ScaleGestureDetector): Boolean {
            apply(HudPrefs.scale(ctx) * det.scaleFactor, save = false)
            return true
        }

        override fun onScaleEnd(det: ScaleGestureDetector) {
            HudPrefs.setScale(ctx, HudPrefs.scale(ctx))
        }
    })

    private fun apply(v: Float, save: Boolean) {
        val s = v.coerceIn(0.6f, 2.2f)
        HudPrefs.setScale(ctx, s)
        onScaleChanged(s)
    }

    fun onTouch(e: MotionEvent, handle: RectF?): Boolean {
        pinch.onTouchEvent(e)
        if (pinch.isInProgress) return true

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (handle != null && handle.contains(e.x, e.y)) {
                dragging = true
                startX = e.x
                startScale = HudPrefs.scale(ctx)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val delta = (e.x - startX) / baseWidthPx()
                apply(startScale + delta, save = false)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                return true
            }
        }
        return false
    }
}
