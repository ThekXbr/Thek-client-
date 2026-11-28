package com.thek.cliente

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.max
import kotlin.math.roundToInt

fun glassBg(ctx: Context, r: Int = 16): GradientDrawable {
    return Th.card(ctx, r)
}

class Graph(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FL
