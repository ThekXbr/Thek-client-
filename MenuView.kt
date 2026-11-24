package com.thek.cliente

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
    fun slider(title: String, key: String, lo: Int, hi: Int, def: Int) {
        val cur = p.getInt(key, def)
        val lbl = TextView(ctx)
        lbl.setTextColor(Color.WHITE)
        lbl.text = "$title: $cur%"
        lbl.setPadding(0, 24, 0, 0)
        root.addView(lbl)
        val sb = SeekBar(ctx)
        sb.max = hi - lo
        sb.progress = cur - lo
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, user: Boolean) {
                lbl.text = "$title: ${v + lo}%"
                p.edit().putInt(key, v + lo).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(sb)
    }
    slider("Tamanho", "scale", 60, 200, 100)
    slider("Opacidade", "opacity", 20, 100, 60)

    val pick = Button(ctx)
    pick.text = "Escolher plano de fundo"
    pick.isAllCaps = false
    pick.setOnClickListener {
        ctx.startActivity(
            Intent(ctx, PickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    root.addView(pick)
    val clr = Button(ctx)
    clr.text = "Remover fundo"
    clr.isAllCaps = false
    clr.setOnClickListener { Bg.clear(ctx) }
    root.addView(clr)
}

class MenuView(base: Context, private val onClose: () -> Unit) :
    FrameLayout(ContextThemeWrapper(base, android.R.style.Theme_Material_NoActionBar)) {

    private lateinit var content: FrameLayout
    private lateinit var turbo: TurboPage
    private lateinit var music: MusicPage
    private val bgIv = ImageView(context)
    private val panel = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xCC1B2236.toInt(), 0xCC0E1320.toInt())
    )
    private var web: WebView? = null
    private val d = resources.displayMetrics.density
    private val wm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var sx = 0f
    private var sy = 0f
    private var ox = 0
    private var oy = 0

    private fun px(v: Int) = (v * d).toInt()

    init {
        panel.cornerRadius = 24 * d
        panel.setStroke(px(1), 0x55FFFFFF)
        background = panel

        bgIv.scaleType = ImageView.ScaleType.CENTER_CROP
        bgIv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, 24 * d)
            }
        }
        bgIv.clipToOutline = true
        addView(bgIv, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(6), px(12), px(12))
        }
        addView(col, FrameLayout.LayoutParams(-1, -1))

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

        turbo = TurboPage(context)
        music = MusicPage(context)
        content.addView(turbo, FrameLayout.LayoutParams(-1, -1))
        content.addView(webPage(), FrameLayout.LayoutParams(-1, -1))
        content.addView(music, FrameLayout.LayoutParams(-1, -1))
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
        val et = EditText(context)
        et.hint = "https://  ou  site.com  ou pesquisar"
        et.setSingleLine()
        et.textSize = 14f
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(0xFF7A849E.toInt())
        et.imeOptions = EditorInfo.IME_ACTION_GO
        val go = Button(context)
        go.text = "Ir"
        val row = LinearLayout(context)
        row.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(go)
        val w = WebView(context)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.webViewClient = WebViewClient()
        w.loadUrl("https://duckduckgo.com")
        web = w
        val open = { w.loadUrl(toUrl(et.text.toString())) }
        go.setOnClickListener { open() }
        et.setOnEditorActionListener { _, _, _ -> open(); true }
        val chips = LinearLayout(context)
        for (s in listOf("duckduckgo.com", "claude.ai", "wikipedia.org")) {
            val b = Button(context)
            b.text = s
            b.textSize = 11f
            b.isAllCaps = false
            b.setOnClickListener { et.setText(s); open() }
            chips.addView(b)
        }
        p.addView(row)
        p.addView(chips)
        p.addView(w, LinearLayout.LayoutParams(-1, 0, 1f))
        return p
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
            Music.refresh(context)
            turbo.update()
            music.update()
            val op = Bg.opacity(context)
            bgIv.setImageBitmap(Bg.bitmap(context))
            bgIv.alpha = op
            panel.alpha = (120 + 135 * op).toInt()
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
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        web = null
        super.onDetachedFromWindow()
    }
    }
