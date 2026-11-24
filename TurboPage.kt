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
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.max
import kotlin.math.roundToInt

fun glassBg(ctx: Context, r: Int = 16): GradientDrawable {
    val d = ctx.resources.displayMetrics.density
    return GradientDrawable().apply {
        setColor(0x22FFFFFF)
        cornerRadius = r * d
        setStroke(d.toInt(), 0x33FFFFFF)
    }
}

class Graph(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6EE7F9.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2 * d
        strokeJoin = Paint.Join.ROUND
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = d
        pathEffect = DashPathEffect(floatArrayOf(8 * d, 6 * d), 0f)
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA4BF.toInt()
        textSize = 10 * d
        textAlign = Paint.Align.RIGHT
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val top = max(60f, Perf.stats.maxHz)
        val gw = w - 30 * d
        for (g in listOf(0f, 0.5f, 1f)) {
            val y = h - 2 * d - g * (h - 4 * d)
            c.drawLine(0f, y, gw, y, grid)
            c.drawText("${(g * top).roundToInt()}", w, y + 4 * d, txt)
        }
        val path = Path()
        val n = Perf.hist.size
        for (i in 0 until n) {
            val x = i.toFloat() / (n - 1) * gw
            val y = h - 2 * d - (Perf.hist[i] / top).coerceIn(0f, 1f) * (h - 4 * d)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        c.drawPath(path, line)
        postInvalidateDelayed(500)
    }
}

class TurboPage(ctx: Context) : LinearLayout(ctx) {
    private val d = resources.displayMetrics.density
    private var shownPkg: String? = null
    private var tStart = 0L
    private val cpuTv = TextView(ctx)
    private val gpuTv = TextView(ctx)
    private val fpsTv = TextView(ctx)
    private val gameTv = TextView(ctx)
    private val gameIc = ImageView(ctx)
    private lateinit var timerTv: TextView

    private fun px(v: Int) = (v * d).toInt()

    private fun lpw(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, -2, 1f)
        p.setMargins(px(3), px(8), px(3), 0)
        return p
    }

    private fun tile(kind: Int, t: String, a: () -> Unit): LinearLayout {
        val v = LinearLayout(context)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER
        v.setPadding(px(6), px(12), px(6), px(12))
        v.background = glassBg(context)
        v.addView(Ico(context, kind, 0xFF6EE7F9.toInt()), LinearLayout.LayoutParams(px(26), px(26)))
        val tv = TextView(context)
        tv.text = t
        tv.textSize = 11f
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, px(6), 0, 0)
        v.addView(tv)
        v.tag = tv
        v.setOnClickListener { a() }
        return v
    }

    private fun cleanMem() {
        val before = Sampler.freeMb(context)
        Sampler.clean(context)
        postDelayed({
            val gain = max(0L, Sampler.freeMb(context) - before)
            Toast.makeText(context, "Liberado: $gain MB", Toast.LENGTH_SHORT).show()
        }, 800)
    }

    private fun dnd() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            val all = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(
                if (all) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        } else {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    init {
        orientation = LinearLayout.VERTICAL
        setPadding(px(4), px(8), px(4), px(4))
        val head = LinearLayout(ctx)
        head.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(ctx)
        title.text = "THEK TURBO"
        title.textSize = 20f
        title.setTextColor(0xFF6EE7F9.toInt())
        title.typeface = Typeface.DEFAULT_BOLD
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(gameIc, LinearLayout.LayoutParams(px(26), px(26)))
        gameTv.textSize = 12f
        gameTv.setTextColor(Color.WHITE)
        gameTv.setPadding(px(6), 0, 0, 0)
        gameTv.maxWidth = px(110)
        gameTv.setSingleLine()
        head.addView(gameTv)
        addView(head)

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.background = glassBg(ctx, 18)
        card.setPadding(px(12), px(10), px(12), px(10))
        val st = LinearLayout(ctx)
        for (t in listOf(cpuTv, gpuTv)) {
            t.textSize = 14f
            t.setTextColor(Color.WHITE)
            st.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        card.addView(st)
        card.addView(Graph(ctx), LinearLayout.LayoutParams(-1, px(100)))
        val bottom = LinearLayout(ctx)
        bottom.gravity = Gravity.CENTER_VERTICAL
        fpsTv.textSize = 30f
        fpsTv.typeface = Typeface.DEFAULT_BOLD
        fpsTv.setTextColor(0xFFFFD84D.toInt())
        bottom.addView(fpsTv)
        val fl = TextView(ctx)
        fl.text = " FPS"
        fl.textSize = 12f
        fl.setTextColor(0xFF9AA4BF.toInt())
        bottom.addView(fl)
        val stop = Button(ctx)
        stop.text = "Parar"
        stop.isAllCaps = false
        stop.setOnClickListener {
            Perf.running = !Perf.running
            stop.text = if (Perf.running) "Parar" else "Iniciar"
        }
        val sp = LinearLayout.LayoutParams(0, -2, 1f)
        sp.setMargins(px(12), 0, 0, 0)
        bottom.addView(stop, sp)
        card.addView(bottom)
        val cp = LinearLayout.LayoutParams(-1, -2)
        cp.setMargins(0, px(10), 0, 0)
        addView(card, cp)

        val row = LinearLayout(ctx)
        val tTimer = tile(IC.TIMER, "Timer") {
            tStart = if (tStart == 0L) System.currentTimeMillis() else 0L
        }
        timerTv = tTimer.tag as TextView
        row.addView(tile(IC.BROOM, "Limpar memoria") { cleanMem() }, lpw())
        row.addView(tTimer, lpw())
        row.addView(tile(IC.DND, "Nao perturbe") { dnd() }, lpw())
        addView(row)
    }

    fun update() {
        val s = Perf.stats
        cpuTv.text = "CPU  ${s.cpuPct?.roundToInt() ?: "-"}%"
        gpuTv.text = "GPU  ${s.gpuPct?.roundToInt() ?: "-"}%"
        fpsTv.text = if (Hud.on) "${s.fps.roundToInt()}" else "--"
        gameTv.text = s.game ?: "Nenhum jogo"
        if (s.gamePkg != shownPkg) {
            shownPkg = s.gamePkg
            try {
                val pk = shownPkg
                gameIc.setImageDrawable(
                    if (pk == null) null else context.packageManager.getApplicationIcon(pk)
                )
            } catch (e: Exception) {
                gameIc.setImageDrawable(null)
            }
        }
        if (tStart == 0L) {
            timerTv.text = "Timer"
        } else {
            val sec = (System.currentTimeMillis() - tStart) / 1000
            timerTv.text = String.format("%02d:%02d", sec / 60, sec % 60)
        }
    }
}
