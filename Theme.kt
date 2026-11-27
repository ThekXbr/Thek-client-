package com.thek.cliente

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object Th {
    val BG = 0xFF0F1115.toInt()
    val CARD = 0xFF171A21.toInt()
    val LINE = 0xFF262B35.toInt()
    val TEXT = 0xFFE8EAF0.toInt()
    val DIM = 0xFF8B93A5.toInt()
    val ACC = 0xFF4C8DFF.toInt()
    val OK = 0xFF3FB950.toInt()

    fun card(ctx: Context, r: Int = 12, fill: Int = CARD): GradientDrawable {
        val d = ctx.resources.displayMetrics.density
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = r * d
        g.setStroke(maxOf(1, d.toInt()), LINE)
        return g
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
