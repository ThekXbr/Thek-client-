package com.thek.cliente

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.*

class MusicPage(ctx: Context) : ScrollView(ctx) {
    private val d = resources.displayMetrics.density
    private val cover = ImageView(ctx)
    private val titleTv = TextView(ctx)
    private val artistTv = TextView(ctx)
    private val prog = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
    private val status = TextView(ctx)
    private val results = LinearLayout(ctx)
    private val accessBox = LinearLayout(ctx)
    private val et = EditText(ctx)
    private lateinit var playIc: Ico
    private var shownIdx = -2

    private fun px(v: Int) = (v * d).toInt()

    private fun doSearch(q: String) {
        if (q.isBlank()) return
        status.text = "Buscando..."
        Player.search(q.trim()) { err ->
            status.text = err ?: ""
            shownIdx = -2
            render()
        }
    }

    private fun render() {
        results.removeAllViews()
        Player.list.forEachIndexed { i, t ->
            val v = TextView(context)
            v.text = t.title + "\n" + t.artist
            v.textSize = 13f
            val on = Player.active && i == Player.index
            v.setTextColor(if (on) 0xFF6EE7F9.toInt() else Color.WHITE)
            v.setPadding(px(12), px(10), px(12), px(10))
            v.background = glassBg(context, 12)
            v.setOnClickListener {
                Player.play(i)
                render()
            }
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, px(4), 0, 0)
            results.addView(v, lp)
        }
    }

    private fun ctl(kind: Int, a: () -> Unit): Ico {
        val v = Ico(context, kind)
        v.background = glassBg(context, 30)
        v.setPadding(px(16), px(10), px(16), px(10))
        v.setOnClickListener { a() }
        return v
    }

    init {
        val p = LinearLayout(ctx)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(px(8), px(8), px(8), px(8))

        et.hint = "Estilo ou musica (ex: nightcore)"
        et.setSingleLine()
        et.textSize = 14f
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(0xFF7A849E.toInt())
        et.imeOptions = EditorInfo.IME_ACTION_SEARCH
        et.setOnEditorActionListener { _, _, _ ->
            doSearch(et.text.toString())
            true
        }
        val go = Button(ctx)
        go.text = "Buscar"
        go.setOnClickListener { doSearch(et.text.toString()) }
        val sr = LinearLayout(ctx)
        sr.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        sr.addView(go)
        p.addView(sr)

        val chips = LinearLayout(ctx)
        for (s in listOf("nightcore", "lofi", "phonk", "rock")) {
            val b = Button(ctx)
            b.text = s
            b.textSize = 11f
            b.isAllCaps = false
            b.setOnClickListener {
                et.setText(s)
                doSearch(s)
            }
            chips.addView(b)
        }
        p.addView(chips)

        status.textSize = 12f
        status.setTextColor(0xFF9AA4BF.toInt())
        p.addView(status)

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.background = glassBg(ctx, 18)
        card.setPadding(px(12), px(10), px(12), px(10))
        val top = LinearLayout(ctx)
        top.gravity = Gravity.CENTER_VERTICAL
        cover.scaleType = ImageView.ScaleType.CENTER_CROP
        cover.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, 12 * d)
            }
        }
        cover.clipToOutline = true
        cover.background = glassBg(ctx, 12)
        top.addView(cover, LinearLayout.LayoutParams(px(64), px(64)))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(12), 0, 0, 0)
        titleTv.textSize = 16f
        titleTv.typeface = Typeface.DEFAULT_BOLD
        titleTv.setTextColor(Color.WHITE)
        titleTv.setSingleLine()
        artistTv.textSize = 12f
        artistTv.setTextColor(0xFF9AA4BF.toInt())
        artistTv.setSingleLine()
        col.addView(titleTv)
        col.addView(artistTv)
        top.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(top)
        prog.max = 1000
        val pp = LinearLayout.LayoutParams(-1, px(6))
        pp.setMargins(0, px(10), 0, px(8))
        card.addView(prog, pp)
        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER
        playIc = ctl(IC.PLAY) { Now.toggle() }
        row.addView(ctl(IC.PREV) { Now.prev() })
        val rp = LinearLayout.LayoutParams(-2, -2)
        rp.setMargins(px(12), 0, px(12), 0)
        row.addView(playIc, rp)
        row.addView(ctl(IC.NEXT) { Now.next() })
        card.addView(row)
        val cp = LinearLayout.LayoutParams(-1, -2)
        cp.setMargins(0, px(8), 0, px(8))
        p.addView(card, cp)

        results.orientation = LinearLayout.VERTICAL
        p.addView(results)

        accessBox.orientation = LinearLayout.VERTICAL
        accessBox.background = glassBg(ctx)
        accessBox.setPadding(px(12), px(10), px(12), px(10))
        val msg = TextView(ctx)
        msg.text = "Para controlar outros players (ex: Spotify), ative o acesso as notificacoes."
        msg.textSize = 12f
        msg.setTextColor(Color.WHITE)
        accessBox.addView(msg)
        val ab = Button(ctx)
        ab.text = "Ativar acesso"
        ab.isAllCaps = false
        ab.setOnClickListener {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        accessBox.addView(ab)
        val ap = LinearLayout.LayoutParams(-1, -2)
        ap.setMargins(0, px(10), 0, 0)
        p.addView(accessBox, ap)
        addView(p)
    }

    fun update() {
        accessBox.visibility = if (Music.access) View.GONE else View.VISIBLE
        titleTv.text = Now.title ?: "Nada tocando"
        artistTv.text = Now.artist ?: ""
        cover.setImageBitmap(Now.art)
        playIc.set(if (Now.playing) IC.PAUSE else IC.PLAY)
        val dur = Now.dur
        prog.progress = if (dur > 0) (Now.pos * 1000 / dur).toInt() else 0
        if (shownIdx != Player.index) {
            shownIdx = Player.index
            render()
        }
    }
}
