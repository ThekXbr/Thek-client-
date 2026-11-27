package com.thek.cliente

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.InputDevice
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup

object Cursors {
    val names = listOf("Padrão do sistema", "Mão", "Mira", "Texto", "Anel", "Ponto")
    private var cached: PointerIcon? = null
    private var cIdx = -1

    fun index(c: Context): Int = Prefs.get(c).getInt("cursor", 0)

    private fun custom(c: Context, ring: Boolean): PointerIcon {
        val d = c.resources.displayMetrics.density
        val s = (32 * d).toInt()
        val bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Th.ACC
        if (ring) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3 * d
            cv.drawCircle(s / 2f, s / 2f, s / 2f - 3 * d, p)
        } else {
            cv.drawCircle(s / 2f, s / 2f, 5 * d, p)
        }
        return PointerIcon.create(bm, s / 2f, s / 2f)
    }

    private fun icon(c: Context): PointerIcon? {
        val i = index(c)
        if (i == cIdx) return cached
        cIdx = i
        cached = when (i) {
            1 -> PointerIcon.getSystemIcon(c, PointerIcon.TYPE_HAND)
            2 -> PointerIcon.getSystemIcon(c, PointerIcon.TYPE_CROSSHAIR)
            3 -> PointerIcon.getSystemIcon(c, PointerIcon.TYPE_TEXT)
            4 -> custom(c, true)
            5 -> custom(c, false)
            else -> null
        }
        return cached
    }

    fun apply(v: View) {
        walk(v, icon(v.context))
    }

    private fun walk(v: View, ic: PointerIcon?) {
        v.pointerIcon = ic
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), ic)
        }
    }

    // (mouse, teclado)
    fun devices(): Pair<Boolean, Boolean> {
        var mouse = false
        var kb = false
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            if (d.isVirtual) continue
            if (d.supportsSource(InputDevice.SOURCE_MOUSE)) mouse = true
            if (d.supportsSource(InputDevice.SOURCE_KEYBOARD) &&
                d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            ) kb = true
        }
        return Pair(mouse, kb)
    }
}
