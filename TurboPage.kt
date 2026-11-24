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

class TurboPage(ctx: Context) : ScrollView(ctx) {
    private val d = resources.displayMetrics.density
    private val col = LinearLayout(ctx)
    private val panelBox = LinearLayout(ctx)
    private val cpuTv = TextView(ctx)
    private val gpuTv = TextView(ctx)
    private val fpsTv = TextView(ctx)
    private val gameTv = TextView(ctx)
    private val gameIc = ImageView(ctx)
    private val monTv = TextView(ctx)
    private val stoTv = TextView(ctx)
    private val updTv = TextView(ctx)
    private val updBtn = Button(ctx)
    private val banner = LinearLayout(ctx)
    private val bannerTv = TextView(ctx)
    private val accTv = TextView(ctx)
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var timerTv: TextView
    private var shownPkg: String? = null
    private var tStart = 0L
    private var openPanel = -1

    private fun px(v: Int) = (v * d).toInt()
    private fun cyan() = 0xFF6EE7F9.toInt()
    private fun gray() = 0xFF9AA4BF.toInt()

    private fun toast(t: String) {
        Toast.makeText(context, t, Toast.LENGTH_SHORT).show()
    }

    private fun lpm(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.setMargins(px(l), px(t), px(r), px(b))
        return p
    }

    private fun label(t: String, size: Float, color: Int): TextView {
        val v = TextView(context)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        return v
    }

    private fun btn(t: String, a: () -> Unit): Button {
        val b = Button(context)
        b.text = t
        b.isAllCaps = false
        b.textSize = 12f
        b.setOnClickListener { a() }
        return b
    }

    private fun field(hint: String): EditText {
        val e = EditText(context)
        e.hint = hint
        e.setSingleLine()
        e.textSize = 14f
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(0xFF7A849E.toInt())
        return e
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(context)
        c.orientation = LinearLayout.VERTICAL
        c.background = glassBg(context, 18)
        c.setPadding(px(12), px(10), px(12), px(10))
        c.visibility = View.GONE
        return c
    }

    private fun sw(t: String, key: String): Switch {
        val s = Switch(context)
        s.text = t
        s.setTextColor(Color.WHITE)
        s.textSize = 13f
        val p = Prefs.get(context)
        s.isChecked = p.getBoolean(key, false)
        s.setOnCheckedChangeListener { _, on -> p.edit().putBoolean(key, on).apply() }
        return s
    }

    private fun tile(icon: View, t: String, a: () -> Unit): LinearLayout {
        val v = LinearLayout(context)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER
        v.setPadding(px(4), px(12), px(4), px(12))
        v.background = glassBg(context)
        v.addView(icon, LinearLayout.LayoutParams(px(26), px(26)))
        val tv = label(t, 11f, Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, px(6), 0, 0)
        v.addView(tv)
        v.tag = tv
        v.setOnClickListener { a() }
        return v
    }

    private fun grid(vararg tiles: LinearLayout): LinearLayout {
        val row = LinearLayout(context)
        for (t in tiles) {
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(px(3), px(6), px(3), 0)
            row.addView(t, lp)
        }
        return row
    }

    private fun showPanel(i: Int) {
        openPanel = if (openPanel == i) -1 else i
        for (n in 0 until panelBox.childCount) {
            panelBox.getChildAt(n).visibility = if (n == openPanel) View.VISIBLE else View.GONE
        }
        if (openPanel >= 0) post { smoothScrollTo(0, panelBox.top) }
    }

    private fun cleanMem() {
        val before = Sampler.freeMb(context)
        Sampler.clean(context)
        postDelayed({
            val gain = max(0L, Sampler.freeMb(context) - before)
            toast("Liberado: $gain MB")
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
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(4), px(8), px(4), px(8))

        val head = LinearLayout(ctx)
        head.gravity = Gravity.CENTER_VERTICAL
        val title = label("THEK TURBO", 20f, cyan())
        title.typeface = Typeface.DEFAULT_BOLD
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(gameIc, LinearLayout.LayoutParams(px(26), px(26)))
        gameTv.textSize = 12f
        gameTv.setTextColor(Color.WHITE)
        gameTv.setPadding(px(6), 0, 0, 0)
        gameTv.maxWidth = px(110)
        gameTv.setSingleLine()
        head.addView(gameTv)
        col.addView(head)

        // aviso de atualizacao
        banner.gravity = Gravity.CENTER_VERTICAL
        banner.background = glassBg(ctx, 14)
        banner.setPadding(px(12), px(6), px(8), px(6))
        bannerTv.textSize = 12f
        bannerTv.setTextColor(Color.WHITE)
        banner.addView(bannerTv, LinearLayout.LayoutParams(0, -2, 1f))
        banner.addView(btn("Atualizar") { Updater.install(context) })
        banner.visibility = View.GONE
        col.addView(banner, lpm(0, 8, 0, 0))

        // desempenho
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
        card.addView(Graph(ctx), LinearLayout.LayoutParams(-1, px(90)))
        val bottom = LinearLayout(ctx)
        bottom.gravity = Gravity.CENTER_VERTICAL
        fpsTv.textSize = 30f
        fpsTv.typeface = Typeface.DEFAULT_BOLD
        fpsTv.setTextColor(0xFFFFD84D.toInt())
        bottom.addView(fpsTv)
        bottom.addView(label(" FPS", 12f, gray()))
        val stop = btn("Parar") { }
        stop.setOnClickListener {
            Perf.running = !Perf.running
            stop.text = if (Perf.running) "Parar" else "Iniciar"
        }
        val sp = LinearLayout.LayoutParams(0, -2, 1f)
        sp.setMargins(px(12), 0, 0, 0)
        bottom.addView(stop, sp)
        card.addView(bottom)
        col.addView(card, lpm(0, 8, 0, 0))

        // botao acelerar
        val acc = LinearLayout(ctx)
        acc.gravity = Gravity.CENTER
        val gd = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFF6EE7F9.toInt(), 0xFFA78BFA.toInt())
        )
        gd.cornerRadius = 16 * d
        acc.background = gd
        acc.setPadding(px(12), px(14), px(12), px(14))
        acc.addView(Ico2(ctx, Ico2.BOLT, 0xFF0E1320.toInt()), LinearLayout.LayoutParams(px(24), px(24)))
        accTv.text = "ACELERAR"
        accTv.textSize = 16f
        accTv.typeface = Typeface.DEFAULT_BOLD
        accTv.setTextColor(0xFF0E1320.toInt())
        accTv.setPadding(px(8), 0, 0, 0)
        acc.addView(accTv)
        acc.setOnClickListener {
            accTv.text = "Acelerando..."
            Boost.accelerate(context) { msg ->
                accTv.text = msg
                ui.postDelayed({ accTv.text = "ACELERAR" }, 3500)
            }
        }
        col.addView(acc, lpm(0, 10, 0, 0))

        col.addView(sw("Limpeza automatica de memoria", "auto_clean"))
        col.addView(sw("Acelerar ao abrir um jogo", "auto_boost"))

        // ferramentas
        val tTimer = tile(Ico(ctx, IC.TIMER, cyan()), "Timer") {
            tStart = if (tStart == 0L) System.currentTimeMillis() else 0L
        }
        timerTv = tTimer.tag as TextView
        col.addView(
            grid(
                tile(Ico(ctx, IC.BROOM, cyan()), "Limpar memoria") { cleanMem() },
                tile(Ico2(ctx, Ico2.DISK, cyan()), "Armazenamento") { showPanel(1) },
                tile(Ico(ctx, IC.DND, cyan()), "Nao perturbe") { dnd() }
            )
        )
        col.addView(
            grid(
                tTimer,
                tile(Ico2(ctx, Ico2.CHART, cyan()), "Monitor") { showPanel(0) },
                tile(Ico(ctx, IC.PAD, cyan()), "Discord") { showPanel(2) }
            )
        )
        col.addView(
            grid(
                tile(Ico(ctx, IC.NOTE, cyan()), "Spotify") { showPanel(3) },
                tile(Ico2(ctx, Ico2.DOWN, cyan()), "Atualizar") {
                    if (openPanel != 4) Updater.check(context, true)
                    showPanel(4)
                },
                tile(Ico(ctx, IC.BOOK, cyan()), "Termos") { showPanel(5) }
            )
        )

        // paineis (um por vez)
        panelBox.orientation = LinearLayout.VERTICAL
        val p0 = card()
        monTv.textSize = 12f
        monTv.setTextColor(Color.WHITE)
        monTv.typeface = Typeface.MONOSPACE
        p0.addView(monTv)
        panelBox.addView(p0, lpm(0, 8, 0, 0))

        val p1 = card()
        stoTv.textSize = 13f
        stoTv.setTextColor(Color.WHITE)
        p1.addView(stoTv)
        p1.addView(
            label(
                "O Android nao deixa apps apagarem arquivos de outros apps. Eu limpo o cache do Thek e abro a tela do sistema, onde voce libera o resto.",
                11f, gray()
            )
        )
        p1.addView(btn("Limpar cache do Thek") {
            toast("Cache limpo: ${Boost.cleanCache(context) / 1024} KB")
        })
        p1.addView(btn("Abrir armazenamento do Android") { Connect.storageSettings(context) })
        panelBox.addView(p1, lpm(0, 8, 0, 0))

        val p2 = card()
        val dcEt = field("Convite (discord.gg/...) ou ID do amigo")
        p2.addView(dcEt)
        val dr = LinearLayout(ctx)
        dr.addView(btn("Abrir Discord") { Connect.discord(context) })
        dr.addView(btn("Abrir link ou perfil") { Connect.discordLink(context, dcEt.text.toString()) })
        p2.addView(dr)
        p2.addView(
            label(
                "A chamada precisa ser iniciada dentro do Discord. Apps de terceiros nao podem ligar por ele.",
                11f, gray()
            )
        )
        panelBox.addView(p2, lpm(0, 8, 0, 0))

        val p3 = card()
        val spEt = field("Musica ou artista")
        p3.addView(spEt)
        val sr = LinearLayout(ctx)
        sr.addView(btn("Abrir Spotify") { Connect.spotify(context) })
        sr.addView(btn("Buscar no Spotify") { Connect.spotifySearch(context, spEt.text.toString()) })
        p3.addView(sr)
        p3.addView(
            label(
                "A musica toca no app do Spotify. Play e proxima funcionam na aba de musica, depois de ativar o acesso as notificacoes.",
                11f, gray()
            )
        )
        panelBox.addView(p3, lpm(0, 8, 0, 0))

        val p4 = card()
        updTv.textSize = 13f
        updTv.setTextColor(Color.WHITE)
        p4.addView(updTv)
        updBtn.isAllCaps = false
        updBtn.setOnClickListener {
            if (Updater.has(context)) Updater.install(context) else Updater.check(context, true)
        }
        p4.addView(updBtn)
        p4.addView(
            label(
                "As atualizacoes vem do GitHub do desenvolvedor. O Android pede sua confirmacao antes de instalar.",
                11f, gray()
            )
        )
        panelBox.addView(p4, lpm(0, 8, 0, 0))

        val p5 = card()
        p5.addView(label(Terms.FULL, 11f, 0xFFC8D0E4.toInt()))
        panelBox.addView(p5, lpm(0, 8, 0, 0))

        col.addView(panelBox)
        addView(col)
        Updater.auto(ctx)
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
        val sto = Boost.storage()
        stoTv.text = "Livre: ${Boost.gb(sto.first)} de ${Boost.gb(sto.second)}"
        monTv.text = "FPS : ${s.fps.roundToInt()} / ${s.maxHz.roundToInt()} Hz\n" +
            "CPU : ${s.cpuPct?.roundToInt() ?: "-"}%  ${s.cpuTemp?.roundToInt() ?: "-"} C\n" +
            "GPU : ${s.gpuPct?.roundToInt() ?: "-"}%  ${s.gpuTemp?.roundToInt() ?: "-"} C\n" +
            "RAM : ${s.ramUsedMb}/${s.ramTotalMb} MB (${s.ramPct?.roundToInt() ?: "-"}%)\n" +
            "Disco livre: ${Boost.gb(sto.first)}\n" +
            "Jogo: ${s.game ?: "-"}"
        val has = Updater.has(context)
        banner.visibility = if (has) View.VISIBLE else View.GONE
        bannerTv.text = "Atualizacao v${Updater.latest} disponivel"
        updTv.text = Updater.status
        updBtn.text = if (has) "Baixar e instalar" else "Verificar agora"
    }
}
