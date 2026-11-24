package com.thek.cliente

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object Hud {
    @Volatile var on = false
}

object IC {
    const val ROCKET = 0
    const val SEARCH = 1
    const val NOTE = 2
    const val GEAR = 3
    const val CLOSE = 4
    const val BROOM = 5
    const val TIMER = 6
    const val DND = 7
    const val BOOK = 8
    const val CHECK = 9
    const val PLAY = 10
    const val PAUSE = 11
    const val NEXT = 12
    const val PREV = 13
    const val PAD = 14
    const val DOT = 15
}

// Icone desenhado em codigo (grade 24x24)
class Ico(ctx: Context, var kind: Int, var col: Int = Color.WHITE) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun set(k: Int, c: Int) {
        if (k != kind || c != col) {
            kind = k
            col = c
            invalidate()
        }
    }

    fun set(k: Int) = set(k, col)

    override fun onMeasure(w: Int, h: Int) {
        val s = (24 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(s + paddingLeft + paddingRight, w),
            resolveSize(s + paddingTop + paddingBottom, h)
        )
    }

    override fun onDraw(c: Canvas) {
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        val k = min(w, h) / 24f
        c.save()
        c.translate(paddingLeft + (w - 24 * k) / 2, paddingTop + (h - 24 * k) / 2)
        c.scale(k, k)
        p.color = col
        p.strokeWidth = 2f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.STROKE
        when (kind) {
            IC.SEARCH -> {
                c.drawCircle(10f, 10f, 6f, p)
                c.drawLine(14.5f, 14.5f, 20f, 20f, p)
            }
            IC.GEAR -> {
                c.drawCircle(12f, 12f, 4f, p)
                c.drawCircle(12f, 12f, 7f, p)
                for (i in 0 until 8) {
                    val a = i * 0.7854f
                    c.drawLine(
                        12f + cos(a) * 7f, 12f + sin(a) * 7f,
                        12f + cos(a) * 10f, 12f + sin(a) * 10f, p
                    )
                }
            }
            IC.NOTE -> {
                p.style = Paint.Style.FILL
                c.drawCircle(8f, 18f, 3.5f, p)
                c.drawCircle(18f, 16f, 3.5f, p)
                p.style = Paint.Style.STROKE
                c.drawLine(11.5f, 18f, 11.5f, 6f, p)
                c.drawLine(21.5f, 16f, 21.5f, 4f, p)
                c.drawLine(11.5f, 6f, 21.5f, 4f, p)
            }
            IC.ROCKET -> {
                val r = Path()
                r.moveTo(12f, 2f)
                r.quadTo(19f, 8f, 16.5f, 16f)
                r.lineTo(7.5f, 16f)
                r.quadTo(5f, 8f, 12f, 2f)
                c.drawPath(r, p)
                c.drawCircle(12f, 9f, 2f, p)
                c.drawLine(7.5f, 13f, 4f, 18f, p)
                c.drawLine(16.5f, 13f, 20f, 18f, p)
                c.drawLine(12f, 18.5f, 12f, 22f, p)
            }
            IC.CLOSE -> {
                c.drawLine(6f, 6f, 18f, 18f, p)
                c.drawLine(18f, 6f, 6f, 18f, p)
            }
            IC.BROOM -> {
                c.drawLine(19f, 3f, 12f, 12f, p)
                val b = Path()
                b.moveTo(12f, 12f)
                b.lineTo(16f, 15f)
                b.lineTo(10f, 21f)
                b.lineTo(4f, 17f)
                b.close()
                c.drawPath(b, p)
            }
            IC.TIMER -> {
                c.drawCircle(12f, 13f, 8f, p)
                c.drawLine(12f, 13f, 12f, 8f, p)
                c.drawLine(12f, 13f, 15f, 15f, p)
                c.drawLine(9f, 3f, 15f, 3f, p)
            }
            IC.DND -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawLine(7f, 12f, 17f, 12f, p)
            }
            IC.BOOK -> {
                c.drawRoundRect(4f, 4f, 20f, 20f, 2f, 2f, p)
                c.drawLine(12f, 4f, 12f, 20f, p)
            }
            IC.CHECK -> {
                c.drawLine(5f, 13f, 10f, 18f, p)
                c.drawLine(10f, 18f, 19f, 7f, p)
            }
            IC.PLAY -> {
                p.style = Paint.Style.FILL
                val t = Path()
                t.moveTo(7f, 4f)
                t.lineTo(20f, 12f)
                t.lineTo(7f, 20f)
                t.close()
                c.drawPath(t, p)
            }
            IC.PAUSE -> {
                p.style = Paint.Style.FILL
                c.drawRect(6f, 4f, 10f, 20f, p)
                c.drawRect(14f, 4f, 18f, 20f, p)
            }
            IC.NEXT -> {
                p.style = Paint.Style.FILL
                val t = Path()
                t.moveTo(5f, 4f)
                t.lineTo(16f, 12f)
                t.lineTo(5f, 20f)
                t.close()
                c.drawPath(t, p)
                c.drawRect(17f, 4f, 20f, 20f, p)
            }
            IC.PREV -> {
                p.style = Paint.Style.FILL
                val t = Path()
                t.moveTo(19f, 4f)
                t.lineTo(8f, 12f)
                t.lineTo(19f, 20f)
                t.close()
                c.drawPath(t, p)
                c.drawRect(4f, 4f, 7f, 20f, p)
            }
            IC.PAD -> {
                c.drawRoundRect(2f, 7f, 22f, 18f, 5f, 5f, p)
                c.drawLine(7f, 10f, 7f, 15f, p)
                c.drawLine(4.5f, 12.5f, 9.5f, 12.5f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(15f, 11f, 1.2f, p)
                c.drawCircle(18f, 14f, 1.2f, p)
            }
            else -> {
                p.style = Paint.Style.FILL
                c.drawCircle(12f, 12f, 4f, p)
            }
        }
        c.restore()
    }
}

// Mensagem de despedida
class ByeView(ctx: Context) : LinearLayout(ctx) {
    init {
        val d = resources.displayMetrics.density
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding((36 * d).toInt(), (24 * d).toInt(), (36 * d).toInt(), (24 * d).toInt())
        val bg = GradientDrawable()
        bg.setColor(0xE60E1320.toInt())
        bg.cornerRadius = 26 * d
        bg.setStroke(d.toInt(), 0x66FFFFFF)
        background = bg
        val a = TextView(ctx)
        a.text = "At\u00E9 logo!"
        a.textSize = 26f
        a.setTextColor(0xFF6EE7F9.toInt())
        a.typeface = Typeface.DEFAULT_BOLD
        a.gravity = Gravity.CENTER
        addView(a)
        val b = TextView(ctx)
        b.text = "Thek Cliente encerrando..."
        b.textSize = 13f
        b.setTextColor(0xFF9AA4BF.toInt())
        b.gravity = Gravity.CENTER
        addView(b)
    }
}
