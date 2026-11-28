package com.thek.cliente

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

object Th {
    var glass = false
    var accent = 0xFF4C8DFF.toInt()
    var round = 1f

    val PALETTE = listOf(
        0xFF4C8DFF.toInt(), 0xFF22D3EE.toInt(), 0xFF3FB950.toInt(),
        0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0xFFA855F7.toInt()
    )

    fun load(c: Context) {
        val p = Prefs.get(c)
        glass = p.getBoolean("glass", false)
        accent = p.getInt("accent", PALETTE[0])
        round = p.getInt("round", 100) / 100f
    }

    fun refresh(c: Context) {
        if (c is Activity) {
            c.recreate()
        } else {
            Toast.makeText(c, "Reabra o menu para aplicar", Toast.LENGTH_SHORT).show()
        }
    }

    val BG: Int get() = if (glass) 0xFF0B0E1A.toInt() else 0xFF0F1115.toInt()
    val CARD: Int get() = if (glass) 0x30FFFFFF else 0xFF171A21.toInt()
    val LINE: Int get() = if (glass) 0x55FFFFFF else 0xFF262B35.toInt()
    val DIM: Int get() = if (glass) 0xFFC3CADA.toInt() else 0xFF8B93A5.toInt()
    val ACC: Int get() = accent
    val TEXT = 0xFFE8EAF0.toInt()
    val OK = 0xFF3FB950.toInt()

    fun card(ctx: Context, r: Int = 12, fill: Int = CARD): GradientDrawable {
        val d = ctx.resources.displayMetrics.density
        val g: GradientDrawable
        if (glass && fill == CARD) {
            g = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x4DFFFFFF, 0x14FFFFFF)
            )
        } else {
            g = GradientDrawable()
            g.setColor(fill)
        }
        g.cornerRadius = r * d * round
        g.setStroke(maxOf(1, d.toInt()), LINE)
        return g
    }
}

// Fundo do app: discreto no estilo Profissional, colorido no Liquid Glass
class Backdrop(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cols = intArrayOf(0xFF4C8DFF.toInt(), 0xFFA855F7.toInt(), 0xFF22D3EE.toInt(), 0xFFF472B6.toInt())

    override fun onDraw(cv: Canvas) {
        val t = SystemClock.uptimeMillis() / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        if (Th.glass) {
            p.shader = null
            p.color = Th.BG
            cv.drawRect(0f, 0f, w, h, p)
            for (i in cols.indices) {
                val cx = w * (0.5f + 0.4f * sin(t * 0.2f + i * 1.7f))
                val cy = h * (0.5f + 0.35f * cos(t * 0.17f + i * 2.3f))
                val c1 = (0x77 shl 24) or (cols[i] and 0x00FFFFFF)
                val c2 = cols[i] and 0x00FFFFFF
                p.shader = RadialGradient(cx, cy, w * 0.75f, c1, c2, Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w, h, p)
            }
        } else {
            p.shader = LinearGradient(0f, 0f, 0f, h, Th.BG, 0xFF151A26.toInt(), Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, w, h, p)
            val cx = w * (0.5f + 0.35f * sin(t * 0.15f))
            val cy = h * (0.3f + 0.12f * cos(t * 0.11f))
            val a = Th.ACC and 0x00FFFFFF
            p.shader = RadialGradient(cx, cy, w * 0.9f, (0x22 shl 24) or a, a, Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, w, h, p)
        }
        p.shader = null
        postInvalidateOnAnimation()
    }
}

object Img {
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val ui = Handler(Looper.getMainLooper())

    fun load(url: String?, v: ImageView) {
        v.setImageDrawable(null)
        if (url.isNullOrEmpty()) return
        v.tag = url
        val c = cache[url]
        if (c != null) {
            v.setImageBitmap(c)
            return
        }
        Thread {
            try {
                val bm = BitmapFactory.decodeStream(URL(url).openStream())
                if (bm != null) {
                    cache[url] = bm
                    ui.post { if (v.tag == url) v.setImageBitmap(bm) }
                }
            } catch (e: Exception) {
            }
        }.start()
    }
}
