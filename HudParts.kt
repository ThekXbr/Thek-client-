
package com.thek.cliente

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

object HudParts {
    private var iconPkg: String? = null
    private var iconBmp: Bitmap? = null

    fun gameIcon(ctx: Context, pkg: String?): Bitmap? {
        if (pkg == null) {
            iconPkg = null
            iconBmp = null
            return null
        }
        if (pkg == iconPkg) return iconBmp
        iconPkg = pkg
        iconBmp = try {
            val dr = ctx.packageManager.getApplicationIcon(pkg)
            val bm = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            dr.setBounds(0, 0, 64, 64)
            dr.draw(Canvas(bm))
            bm
        } catch (e: Exception) {
            null
        }
        return iconBmp
    }

    fun play(c: Canvas, p: Paint, cx: Float, cy: Float, s: Float, playing: Boolean) {
        if (playing) {
            c.drawRect(cx - s * 0.8f, cy - s, cx - s * 0.2f, cy + s, p)
            c.drawRect(cx + s * 0.2f, cy - s, cx + s * 0.8f, cy + s, p)
        } else {
            val t = Path()
            t.moveTo(cx - s * 0.6f, cy - s)
            t.lineTo(cx + s, cy)
            t.lineTo(cx - s * 0.6f, cy + s)
            t.close()
            c.drawPath(t, p)
        }
    }

    fun next(c: Canvas, p: Paint, cx: Float, cy: Float, s: Float) {
        val t = Path()
        t.moveTo(cx - s, cy - s)
        t.lineTo(cx + s * 0.6f, cy)
        t.lineTo(cx - s, cy + s)
        t.close()
        c.drawPath(t, p)
        c.drawRect(cx + s * 0.8f, cy - s, cx + s * 1.2f, cy + s, p)
    }

    // minimizar, engrenagem e fechar
    fun header(c: Canvas, p: Paint, d: Float, bw: Float, gy: Float) {
        val mx = bw - 74 * d
        c.drawLine(mx - 5 * d, gy, mx + 5 * d, gy, p)
        val gx = bw - 46 * d
        c.drawCircle(gx, gy, 5 * d, p)
        for (k in 0 until 8) {
            val a = k * 0.785f
            c.drawLine(
                gx + cos(a) * 7 * d, gy + sin(a) * 7 * d,
                gx + cos(a) * 10 * d, gy + sin(a) * 10 * d, p
            )
        }
        val xx = bw - 18 * d
        c.drawLine(xx - 5 * d, gy - 5 * d, xx + 5 * d, gy + 5 * d, p)
        c.drawLine(xx - 5 * d, gy + 5 * d, xx + 5 * d, gy - 5 * d, p)
    }
}
