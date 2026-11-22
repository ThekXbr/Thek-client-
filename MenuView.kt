package com.thek.cliente

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import kotlin.math.max
import kotlin.math.roundToInt

// Controles do HUD (usados no app e no menu do overlay)
fun hudControls(ctx: Context, root: LinearLayout) {
    val p = Prefs.get(ctx)
    val items = listOf(
        "FPS" to "show_fps", "RAM" to "show_ram", "CPU" to "show_cpu",
        "GPU" to "show_gpu", "Nome do jogo" to "show_game",
        "Player de musica" to "show_music"
    )
    for ((t, k) in items) {
        root.addView(Switch(ctx).apply {
            text = t
            setTextColor(Color.WHITE)
            isChecked = p.getBoolean(k, true)
            setOnCheckedChangeListener { _, on -> p.edit().putBoolean(k, on).apply() }
        })
    }
    val cur = p.getInt("scale", 100)
    val lbl = TextView(ctx).apply {
        setTextColor(Color.WHITE)
        text = "Tamanho: $cur%"
        setPadding(0, 24, 0, 0)
    }
    root.addView(lbl)
    root.addView(SeekBar(ctx).apply {
        max = 140
        progress = cur - 60
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, user: Boolean) {
                lbl.text = "Tamanho: ${v + 60}%"
                p.edit().putInt("scale", v + 60).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    })
}

// Grafico de FPS
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
        strokeWidth = 1 * d
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

class MenuView(base: Context, private val onClose: () -> Unit) :
    FrameLayout(ContextThemeWrapper(base, android.R.style.Theme_Material_NoActionBar)) {

    private lateinit var content: FrameLayout
    private var web: WebView? = null
    private val d = resources.displayMetrics.density
    private val wm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var sx = 0f
    private var sy = 0f
    private var ox = 0
    private var oy = 0
    private var shownPkg: String? = null
    private var tStart = 0L
    private lateinit var cpuTv: TextView
    private lateinit var gpuTv: TextView
    private lateinit var fpsTv: TextView
    private lateinit var gameTv: TextView
    private lateinit var gameIc: ImageView
    private lateinit var timerTv: TextView
    private lateinit var cover: ImageView
    private lateinit var titleTv: TextView
    private lateinit var artistTv: TextView
    private lateinit var prog: ProgressBar
    private lateinit var playTv: TextView
    private lateinit var accessBox: LinearLayout

    private fun px(v: Int) = (v * d).toInt()

    private fun glass(r: Int = 16) = GradientDrawable().apply {
        setColor(0x22FFFFFF)
        cornerRadius = r * d
        setStroke(px(1), 0x33FFFFFF)
    }

    init {
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xCC1B2236.toInt(), 0xCC0E1320.toInt())
        ).apply {
            cornerRadius = 24 * d
            setStroke(px(1), 0x55FFFFFF)
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(6), px(12), px(12))
        }
        addView(col, FrameLayout.LayoutParams(-1, -1))

        // barra para arrastar o menu
        val handle = TextView(context)
        handle.text = "\u2630  arraste aqui"
        handle.textSize = 12f
        handle.setTextColor(0xFF7A849E.toInt())
        handle.gravity = Gravity.CENTER
        handle.setPadding(0, px(8), 0, px(8))
        handle.setOnTouchListener { _, e ->
            val lp = layoutParams as? WindowManager.LayoutParams
            if (lp != null) {
                if (e.action == MotionEvent.ACTION_DOWN) {
                    sx = e.rawX
                    sy = e.rawY
                    ox = lp.x
                    oy = lp.y
                } else if (e.action == MotionEvent.ACTION_MOVE) {
                    lp.x = ox + (e.rawX - sx).toInt()
                    lp.y = oy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(this@MenuView, lp)
                }
            }
            true
        }
        col.addView(handle, LinearLayout.LayoutParams(-1, -2))

        val tabs = LinearLayout(context)
        col.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        content = FrameLayout(context)
        col.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(turboPage(), FrameLayout.LayoutParams(-1, -1))
        content.addView(webPage(), FrameLayout.LayoutParams(-1, -1))
        content.addView(musicPage(), FrameLayout.LayoutParams(-1, -1))
        content.addView(hudPage(), FrameLayout.LayoutParams(-1, -1))

        val icons = listOf("\uD83D\uDE80", "\uD83D\uDD0D", "\uD83C\uDFB5", "\u2699")
        icons.forEachIndexed { i, ic ->
            tabs.addView(TextView(context).apply {
                text = ic
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(0, px(6), 0, px(6))
                setOnClickListener { show(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        tabs.addView(TextView(context).apply {
            text = "\u2715"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(px(12), px(6), px(12), px(6))
            setOnClickListener { onClose() }
        })
        show(0)
    }

    private fun show(i: Int) {
        for (n in 0 until content.childCount) {
            content.getChildAt(n).visibility = if (n == i) View.VISIBLE else View.GONE
        }
    }

    private fun lpw() = LinearLayout.LayoutParams(0, -2, 1f).apply {
        setMargins(px(3), px(8), px(3), 0)
    }

    private fun tile(t: String, a: () -> Unit) = TextView(context).apply {
        text = t
        textSize = 12f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(px(6), px(14), px(6), px(14))
        background = glass()
        setOnClickListener { a() }
    }

    private fun cleanMem() {
        val before = Sampler.freeMb(context)
        Sampler.clean(context)
        postDelayed({
            val after = Sampler.freeMb(context)
            Toast.makeText(context, "Liberado: ${max(0L, after - before)} MB", Toast.LENGTH_SHORT).show()
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

    private fun turboPage(): View {
        val p = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(4), px(8), px(4), px(4))
        }
        val head = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(context).apply {
            text = "THEK TURBO"
            textSize = 18f
            setTextColor(0xFF6EE7F9.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        gameIc = ImageView(context)
        head.addView(gameIc, LinearLayout.LayoutParams(px(26), px(26)))
        gameTv = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(px(6), 0, 0, 0)
            maxWidth = px(110)
            setSingleLine()
        }
        head.addView(gameTv)
        p.addView(head)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(18)
            setPadding(px(12), px(10), px(12), px(10))
        }
        val st = LinearLayout(context)
        cpuTv = TextView(context).apply { textSize = 14f; setTextColor(Color.WHITE) }
        gpuTv = TextView(context).apply { textSize = 14f; setTextColor(Color.WHITE) }
        st.addView(cpuTv, LinearLayout.LayoutParams(0, -2, 1f))
        st.addView(gpuTv, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(st)
        card.addView(Graph(context), LinearLayout.LayoutParams(-1, px(100)))
        val bottom = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        fpsTv = TextView(context).apply {
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFD84D.toInt())
        }
        bottom.addView(fpsTv)
        bottom.addView(TextView(context).apply {
            text = " FPS"
            textSize = 12f
            setTextColor(0xFF9AA4BF.toInt())
        })
        val stop = Button(context)
        stop.text = "Parar"
        stop.isAllCaps = false
        stop.setOnClickListener {
            Perf.running = !Perf.running
            stop.text = if (Perf.running) "Parar" else "Iniciar"
        }
        bottom.addView(stop, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(px(12), 0, 0, 0) })
        card.addView(bottom)
        p.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, px(10), 0, 0) })

        val row = LinearLayout(context)
        timerTv = tile("\u23F1 Timer") {
            tStart = if (tStart == 0L) System.currentTimeMillis() else 0L
        }
        row.addView(tile("\uD83E\uDDF9 Limpar memoria") { cleanMem() }, lpw())
        row.addView(timerTv, lpw())
        row.addView(tile("\uD83D\uDD15 Nao perturbe") { dnd() }, lpw())
        p.addView(row)
        return p
    }

    private fun field(hint: String) = EditText(context).apply {
        this.hint = hint
        setSingleLine()
        textSize = 14f
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF7A849E.toInt())
        imeOptions = EditorInfo.IME_ACTION_GO
    }

    private fun toUrl(t: String): String {
        val s = t.trim()
        return when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            !s.contains(" ") && s.contains(".") -> "https://$s"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(s)
        }
    }

    private fun webPage(): View {
        val p = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val et = field("https://  ou  site.com  ou pesquisar")
        val go = Button(context).apply { text = "Ir" }
        val row = LinearLayout(context)
        row.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(go)
        val w = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl("https://duckduckgo.com")
        }
        web = w
        val open = { w.loadUrl(toUrl(et.text.toString())) }
        go.setOnClickListener { open() }
        et.setOnEditorActionListener { _, _, _ -> open(); true }
        val chips = LinearLayout(context)
        for (s in listOf("duckduckgo.com", "claude.ai", "wikipedia.org")) {
            chips.addView(Button(context).apply {
                text = s
                textSize = 11f
                isAllCaps = false
                setOnClickListener { et.setText(s); open() }
            })
        }
        p.addView(row)
        p.addView(chips)
        p.addView(w, LinearLayout.LayoutParams(-1, 0, 1f))
        return p
    }

    private fun spotify(q: String) {
        val s = Uri.encode(q.trim())
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$s"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(app)
        } catch (e: Exception) {
            val site = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$s"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(site)
        }
    }

    private fun ctl(t: String, a: () -> Unit) = TextView(context).apply {
        text = t
        textSize = 26f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = glass(30)
        setPadding(px(18), px(8), px(18), px(8))
        setOnClickListener {
            a()
            postDelayed({ Music.refresh(context) }, 300)
        }
    }

    private fun musicPage(): View {
        val p = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(8), px(8), px(8), px(8))
        }
        accessBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(16)
            setPadding(px(12), px(10), px(12), px(10))
        }
        accessBox.addView(TextView(context).apply {
            text = "Ative o acesso as notificacoes para ver e controlar a musica."
            textSize = 12f
            setTextColor(Color.WHITE)
        })
        accessBox.addView(Button(context).apply {
            text = "Ativar acesso"
            isAllCaps = false
            setOnClickListener {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        })
        p.addView(accessBox)

        cover = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = glass(20)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, 20 * d)
                }
            }
            clipToOutline = true
        }
        p.addView(cover, LinearLayout.LayoutParams(px(150), px(150)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, px(10), 0, px(8))
        })
        titleTv = TextView(context).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setSingleLine()
        }
        artistTv = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFF9AA4BF.toInt())
            gravity = Gravity.CENTER
            setSingleLine()
        }
        p.addView(titleTv)
        p.addView(artistTv)
        prog = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
        prog.max = 1000
        p.addView(prog, LinearLayout.LayoutParams(-1, px(8)).apply { setMargins(0, px(10), 0, px(10)) })

        val row = LinearLayout(context).apply { gravity = Gravity.CENTER }
        playTv = ctl("\u25B6") { Music.toggle() }
        row.addView(ctl("\u23EE") { Music.prev() })
        row.addView(playTv, LinearLayout.LayoutParams(-2, -2).apply { setMargins(px(12), 0, px(12), 0) })
        row.addView(ctl("\u23ED") { Music.next() })
        p.addView(row)

        val et = field("Buscar no Spotify")
        val sr = LinearLayout(context)
        sr.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        sr.addView(Button(context).apply {
            text = "Ir"
            setOnClickListener { spotify(et.text.toString()) }
        })
        p.addView(sr, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, px(10), 0, 0) })
        return ScrollView(context).apply { addView(p) }
    }

    private fun hudPage(): View {
        val ll = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(8), px(12), px(8), px(8))
        }
        hudControls(context, ll)
        return ScrollView(context).apply { addView(ll) }
    }

    private val upd = object : Runnable {
        override fun run() {
            val s = Perf.stats
            cpuTv.text = "CPU  ${s.cpuPct?.roundToInt() ?: "-"}%"
            gpuTv.text = "GPU  ${s.gpuPct?.roundToInt() ?: "-"}%"
            fpsTv.text = "${s.fps.roundToInt()}"
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
                timerTv.text = "\u23F1 Timer"
            } else {
                val sec = (System.currentTimeMillis() - tStart) / 1000
                timerTv.text = String.format("%02d:%02d", sec / 60, sec % 60)
            }
            Music.refresh(context)
            accessBox.visibility = if (Music.access) View.GONE else View.VISIBLE
            titleTv.text = Music.title ?: "Nada tocando"
            artistTv.text = Music.artist ?: ""
            cover.setImageBitmap(Music.art)
            playTv.text = if (Music.playing) "\u23F8" else "\u25B6"
            prog.progress = if (Music.dur > 0) (Music.pos * 1000 / Music.dur).toInt() else 0
            this@MenuView.postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(upd)
        alpha = 0f
        scaleX = 0.9f
        scaleY = 0.9f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).start()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(upd)
        web?.let {
            (it.parent as? ViewGroup)?.removeView
