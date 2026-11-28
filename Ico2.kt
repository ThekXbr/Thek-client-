package com.thek.cliente

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

object Ico2 {
    private val p = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fP = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    fun DISC(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        p.color = color
        p.style = Paint.Style.STROKE
        val cx = x + size / 2f
        val cy = y + size / 2f
        val r = size * 0.4f
        canvas.drawCircle(cx, cy, r, p)
        canvas.drawCircle(cx, cy, r * 0.2f, p)
    }

    fun CHART(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        p.color = color
        p.style = Paint.Style.STROKE
        val pad = size * 0.2f
        val w = size - pad * 2f
        val h = size - pad * 2f
        val l = x + pad
        val b = y + size - pad
        
        canvas.drawLine(l, b, l + w, b, p)
        canvas.drawLine(l, y + pad, l, b, p)
        
        val barW = w * 0.18f
        fP.color = color
        canvas.drawRect(l + w * 0.1f, b - h * 0.5f, l + w * 0.1f + barW, b, fP)
        canvas.drawRect(l + w * 0.4f, b - h * 0.8f, l + w * 0.4f + barW, b, fP)
        canvas.drawRect(l + w * 0.7f, b - h * 0.65f, l + w * 0.7f + barW, b, fP)
    }

    fun DOWN(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        p.color = color
        p.style = Paint.Style.STROKE
        val cx = x + size / 2f
        val pad = size * 0.25f
        
        canvas.drawLine(cx, y + pad, cx, y + size - pad * 1.2f, p)
        
        val path = Path().apply {
            moveTo(cx - size * 0.25f, y + size - pad * 1.6f)
            lineTo(cx, y + size - pad * 1.1f)
            lineTo(cx + size * 0.25f, y + size - pad * 1.6f)
        }
        canvas.drawPath(path, p)
        
        canvas.drawLine(x + pad, y + size - pad * 0.8f, x + size - pad, y + size - pad * 0.8f, p)
    }

    fun BOLT(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        fP.color = color
        val path = Path().apply {
            moveTo(x + size * 0.55f, y + size * 0.1f)
            lineTo(x + size * 0.15f, y + size * 0.55f)
            lineTo(x + size * 0.45f, y + size * 0.55f)
            lineTo(x + size * 0.35f, y + size * 0.9f)
            lineTo(x + size * 0.85f, y + size * 0.4f)
            lineTo(x + size * 0.55f, y + size * 0.4f)
            close()
        }
        canvas.drawPath(path, fP)
    }

    fun SPARK(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        p.color = color
        p.style = Paint.Style.STROKE
        val cx = x + size / 2f
        val cy = y + size / 2f
        val r = size * 0.35f
        
        canvas.drawLine(cx, y + size * 0.15f, cx, y + size * 0.85f, p)
        canvas.drawLine(x + size * 0.15f, cy, x + size * 0.85f, cy, p)
        
        val off = r * 0.7f
        canvas.drawLine(cx - off, cy - off, cx + off, cy + off, p)
        canvas.drawLine(cx - off, cy + off, cx + off, cy - off, p)
    }
}
