package com.thek.cliente

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
        "GPU" to "show_gpu", "Nome do jogo" to "show_game"
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

    private fun px(v: Int) = (v * d).toInt()

    init {
        background = GradientDrawable().apply {
            setColor(0xF20E1320.toInt())
            cornerRadius = 22 * d
            setStroke(px(1), 0x44FFFFFF)
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

        content.addView(webPage(), FrameLayout.LayoutParams(-1, -1))
        content.addView(hudPage(), FrameLayout.LayoutParams(-1, -1))
        content.addView(musicPage(), FrameLayout.LayoutParams(-1, -1))

        val icons = listOf("\uD83D\uDD0D", "\uD83C\uDFAE", "\uD83C\uDFB5")
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

    private fun hudPage(): View {
        val ll = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(8), px(12), px(8), px(8))
        }
        hudControls(context, ll)
        return ScrollView(context).apply { addView(ll) }
    }

    private fun media(code: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
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

    private fun musicPage(): View {
        val p = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(8), px(16), px(8), px(8))
        }
        val et = field("Buscar musica no Spotify")
        p.addView(et)
        p.addView(Button(context).apply {
            text = "Buscar no Spotify"
            isAllCaps = false
            setOnClickListener { spotify(et.text.toString()) }
        })
        val ctl = LinearLayout(context).apply { gravity = Gravity.CENTER }
        val keys = listOf(
            "\u23EE" to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            "\u23EF" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            "\u23ED" to KeyEvent.KEYCODE_MEDIA_NEXT
        )
        for ((t, k) in keys) {
            ctl.addView(Button(context).apply {
                text = t
                textSize = 22f
                setOnClickListener { media(k) }
            })
        }
        p.addView(ctl)
        return p
    }

    override fun onDetachedFromWindow() {
        web?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        web = null
        super.onDetachedFromWindow()
    }
    }
