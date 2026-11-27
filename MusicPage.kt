package com.thek.cliente

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.*

class Ico3(ctx: Context, var kind: Int, var col: Int) : View(ctx) {
    companion object {
        const val HEART = 0
        const val HEART_ON = 1
        const val SHUFFLE = 2
        const val REPEAT = 3
        const val REPEAT_ONE = 4
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun set(k: Int, c: Int) {
        if (k != kind || c != col) {
            kind = k
            col = c
            invalidate()
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        val s = (24 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(s + paddingLeft + paddingRight, w),
            resolveSize(s + paddingTop + paddingBottom, h)
        )
    }

    override fun onDraw(c: Canvas) {
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        val k = minOf(w, h) / 24f
        c.save()
        c.translate(paddingLeft + (w - 24 * k) / 2, paddingTop + (h - 24 * k) / 2)
        c.scale(k, k)
        p.color = col
        p.strokeWidth = 2f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.STROKE
        when (kind) {
            HEART, HEART_ON -> {
                val hp = Path()
                hp.moveTo(12f, 21f)
                hp.cubicTo(4f, 14f, 2f, 9f, 6f, 6f)
                hp.cubicTo(8.5f, 4f, 11f, 5.5f, 12f, 8f)
                hp.cubicTo(13f, 5.5f, 15.5f, 4f, 18f, 6f)
                hp.cubicTo(22f, 9f, 20f, 14f, 12f, 21f)
                hp.close()
                p.style = if (kind == HEART_ON) Paint.Style.FILL else Paint.Style.STROKE
                c.drawPath(hp, p)
            }
            SHUFFLE -> {
                c.drawLine(4f, 7f, 8f, 7f, p)
                c.drawLine(8f, 7f, 16f, 17f, p)
                c.drawLine(16f, 17f, 20f, 17f, p)
                c.drawLine(4f, 17f, 8f, 17f, p)
                c.drawLine(8f, 17f, 16f, 7f, p)
                c.drawLine(16f, 7f, 20f, 7f, p)
                c.drawLine(18f, 5f, 20f, 7f, p)
                c.drawLine(18f, 9f, 20f, 7f, p)
                c.drawLine(18f, 15f, 20f, 17f, p)
                c.drawLine(18f, 19f, 20f, 17f, p)
            }
            else -> {
                c.drawRoundRect(4f, 6f, 20f, 18f, 5f, 5f, p)
                c.drawLine(15f, 3f, 18f, 6f, p)
                c.drawLine(15f, 9f, 18f, 6f, p)
                if (kind == REPEAT_ONE) {
                    c.drawLine(12f, 10f, 12f, 15f, p)
                    c.drawLine(10.5f, 11f, 12f, 10f, p)
                }
            }
        }
        c.restore()
    }
}

class MusicPage(ctx: Context) : LinearLayout(ctx) {
    private val d = resources.displayMetrics.density
    private val tabTv = ArrayList<TextView>()
    private val body = FrameLayout(ctx)
    private val homeList = LinearLayout(ctx)
    private val homeStatus = TextView(ctx)
    private val searchList = LinearLayout(ctx)
    private val status = TextView(ctx)
    private val libList = LinearLayout(ctx)
    private val libEmpty = TextView(ctx)
    private val et = EditText(ctx)
    private var trend: List<Track> = emptyList()
    private var results: List<Track> = emptyList()
    private var loadedTrend = false
    private var shownIdx = -2
    private var shownLikes = -1
    private var seeking = false

    private val cover = ImageView(ctx)
    private val titleTv = TextView(ctx)
    private val artistTv = TextView(ctx)
    private val seek = SeekBar(ctx)
    private val timeTv = TextView(ctx)
    private val heart = Ico3(ctx, Ico3.HEART, Th.DIM)
    private val shuf = Ico3(ctx, Ico3.SHUFFLE, Th.DIM)
    private val rep = Ico3(ctx, Ico3.REPEAT, Th.DIM)
    private val playIc = Ico(ctx, IC.PLAY, Th.BG)

    private fun px(v: Int) = (v * d).toInt()

    private fun round(v: View, r: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) {
                o.setRoundRect(0, 0, view.width, view.height, r * d)
            }
        }
        v.clipToOutline = true
    }

    private fun fmt(ms: Long): String = String.format("%d:%02d", ms / 60000, ms / 1000 % 60)

    private fun title(t: String): TextView {
        val v = TextView(context)
        v.text = t
        v.textSize = 18f
        v.typeface = Typeface.DEFAULT_BOLD
        v.setTextColor(Th.TEXT)
        v.setPadding(px(4), px(12), 0, px(4))
        return v
    }

    private fun chip(t: String): TextView {
        val v = TextView(context)
        v.text = t
        v.textSize = 13f
        v.setTextColor(Th.TEXT)
        v.setPadding(px(14), px(8), px(14), px(8))
        v.background = Th.card(context, 18)
        v.setOnClickListener {
            et.setText(t)
            doSearch(t)
            tab(1)
        }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.setMargins(0, px(8), px(8), px(8))
        v.layoutParams = lp
        return v
    }

    private fun doSearch(q: String) {
        if (q.isBlank()) return
        status.text = "Buscando…"
        Player.load("/tracks/search?query=" + Uri.encode(q.trim()) + "&limit=25") { l, err ->
            results = l
            status.text = err ?: ""
            fill(searchList, l)
        }
    }

    private fun fill(box: LinearLayout, list: List<Track>) {
        box.removeAllViews()
        list.forEachIndexed { i, t -> box.addView(row(t, list, i)) }
    }

    private fun row(t: Track, list: List<Track>, i: Int): View {
        val r = LinearLayout(context)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(px(4), px(6), px(4), px(6))
        val iv = ImageView(context)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.background = Th.card(context, 6)
        round(iv, 6f)
        Img.load(t.art, iv)
        r.addView(iv, LinearLayout.LayoutParams(px(44), px(44)))
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(12), 0, px(8), 0)
        val on = Player.active && Player.current?.id == t.id
        val a = TextView(context)
        a.text = t.title
        a.textSize = 14f
        a.setTextColor(if (on) Th.ACC else Th.TEXT)
        a.setSingleLine()
        val b = TextView(context)
        b.text = t.artist
        b.textSize = 12f
        b.setTextColor(Th.DIM)
        b.setSingleLine()
        col.addView(a)
        col.addView(b)
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val liked = Player.isLiked(context, t.id)
        val h = Ico3(context, if (liked) Ico3.HEART_ON else Ico3.HEART, if (liked) Th.ACC else Th.DIM)
        h.setPadding(px(8), px(8), px(8), px(8))
        h.setOnClickListener {
            Player.toggleLike(context, t)
            val now = Player.isLiked(context, t.id)
            h.set(if (now) Ico3.HEART_ON else Ico3.HEART, if (now) Th.ACC else Th.DIM)
        }
        r.addView(h)
        r.setOnClickListener {
            Player.playFrom(list, i)
            shownIdx = -2
        }
        return r
    }

    private fun tab(i: Int) {
        for (n in 0 until body.childCount) {
            body.getChildAt(n).visibility = if (n == i) View.VISIBLE else View.GONE
            tabTv[n].setTextColor(if (n == i) Th.ACC else Th.DIM)
            tabTv[n].typeface = if (n == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun homePage(): View {
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(4), 0, px(4), px(8))
        col.addView(title("Gêneros"))
        val hs = HorizontalScrollView(context)
        hs.isHorizontalScrollBarEnabled = false
        val ch = LinearLayout(context)
        for (g in listOf("Nightcore", "Lo-fi", "Phonk", "Rock", "Eletrônica", "Hip-hop", "Pop")) {
            ch.addView(chip(g))
        }
        hs.addView(ch)
        col.addView(hs)
        col.addView(title("Em alta esta semana"))
        homeStatus.textSize = 12f
        homeStatus.setTextColor(Th.DIM)
        col.addView(homeStatus)
        homeList.orientation = LinearLayout.VERTICAL
        col.addView(homeList)
        val sv = ScrollView(context)
        sv.addView(col)
        return sv
    }

    private fun searchPage(): View {
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(4), px(8), px(4), px(8))
        et.hint = "Artistas, músicas ou estilos"
        et.setSingleLine()
        et.textSize = 14f
        et.setTextColor(Th.TEXT)
        et.setHintTextColor(Th.DIM)
        et.imeOptions = EditorInfo.IME_ACTION_SEARCH
        et.setOnEditorActionListener { _, _, _ ->
            doSearch(et.text.toString())
            true
        }
        val go = Button(context)
        go.text = "Buscar"
        go.isAllCaps = false
        go.setOnClickListener { doSearch(et.text.toString()) }
        val sr = LinearLayout(context)
        sr.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        sr.addView(go)
        col.addView(sr)
        status.textSize = 12f
        status.setTextColor(Th.DIM)
        col.addView(status)
        searchList.orientation = LinearLayout.VERTICAL
        col.addView(searchList)
        val sv = ScrollView(context)
        sv.addView(col)
        return sv
    }

    private fun libPage(): View {
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(4), 0, px(4), px(8))
        col.addView(title("Músicas curtidas"))
        libEmpty.text = "Toque no coração de uma música para salvá-la aqui."
        libEmpty.textSize = 12f
        libEmpty.setTextColor(Th.DIM)
        col.addView(libEmpty)
        libList.orientation = LinearLayout.VERTICAL
        col.addView(libList)
        val sv = ScrollView(context)
        sv.addView(col)
        return sv
    }

    private fun btn(v: View, a: () -> Unit): View {
        v.setPadding(px(10), px(10), px(10), px(10))
        v.setOnClickListener { a() }
        return v
    }

    private fun playerBar(): View {
        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.VERTICAL
        bar.background = Th.card(context, 14)
        bar.setPadding(px(10), px(10), px(10), px(8))

        val top = LinearLayout(context)
        top.gravity = Gravity.CENTER_VERTICAL
        cover.scaleType = ImageView.ScaleType.CENTER_CROP
        cover.background = Th.card(context, 8)
        round(cover, 8f)
        top.addView(cover, LinearLayout.LayoutParams(px(48), px(48)))
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(12), 0, px(8), 0)
        titleTv.textSize = 14f
        titleTv.typeface = Typeface.DEFAULT_BOLD
        titleTv.setTextColor(Th.TEXT)
        titleTv.setSingleLine()
        artistTv.textSize = 12f
        artistTv.setTextColor(Th.DIM)
        artistTv.setSingleLine()
        col.addView(titleTv)
        col.addView(artistTv)
        top.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(btn(heart) {
            val t = Player.current
            if (t != null) Player.toggleLike(context, t)
        })
        bar.addView(top)

        val sr = LinearLayout(context)
        sr.gravity = Gravity.CENTER_VERTICAL
        timeTv.textSize = 11f
        timeTv.setTextColor(Th.DIM)
        seek.max = 1000
        seek.progressTintList = ColorStateList.valueOf(Th.ACC)
        seek.thumbTintList = ColorStateList.valueOf(Th.ACC)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, user: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) {
                seeking = true
            }
            override fun onStopTrackingTouch(s: SeekBar?) {
                seeking = false
                val dur = Now.dur
                if (Player.active && dur > 0) Player.seek((seek.progress * dur / 1000).toInt())
            }
        })
        sr.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        sr.addView(timeTv)
        bar.addView(sr)

        val row = LinearLayout(context)
        row.gravity = Gravity.CENTER
        row.addView(btn(shuf) { Player.shuffle = !Player.shuffle })
        row.addView(btn(Ico(context, IC.PREV, Th.TEXT)) { Now.prev() })
        val pc = GradientDrawable()
        pc.shape = GradientDrawable.OVAL
        pc.setColor(Th.TEXT)
        playIc.background = pc
        playIc.setPadding(px(10), px(10), px(10), px(10))
        playIc.setOnClickListener { Now.toggle() }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.setMargins(px(10), 0, px(10), 0)
        row.addView(playIc, lp)
        row.addView(btn(Ico(context, IC.NEXT, Th.TEXT)) { Now.next() })
        row.addView(btn(rep) { Player.repeat = (Player.repeat + 1) % 3 })
        bar.addView(row)
        return bar
    }

    init {
        orientation = LinearLayout.VERTICAL
        val tb = LinearLayout(ctx)
        listOf("Início", "Buscar", "Biblioteca").forEachIndexed { i, t ->
            val v = TextView(ctx)
            v.text = t
            v.textSize = 14f
            v.gravity = Gravity.CENTER
            v.setPadding(0, px(10), 0, px(10))
            v.setOnClickListener { tab(i) }
            tabTv.add(v)
            tb.addView(v, LinearLayout.LayoutParams(0, -2, 1f))
        }
        addView(tb)
        addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(homePage())
        body.addView(searchPage())
        body.addView(libPage())
        val bp = LinearLayout.LayoutParams(-1, -2)
        bp.setMargins(0, px(6), 0, 0)
        addView(playerBar(), bp)
        tab(0)
    }

    fun update() {
        if (!loadedTrend && isShown) {
            loadedTrend = true
            Player.load("/tracks/trending?limit=20&time=week") { l, err ->
                trend = l
                homeStatus.text = err ?: ""
                fill(homeList, l)
            }
        }
        titleTv.text = Now.title ?: "Nada tocando"
        artistTv.text = Now.artist ?: "Escolha uma música"
        cover.setImageBitmap(Now.art)
        val dur = Now.dur
        val pos = Now.pos
        if (!seeking) seek.progress = if (dur > 0) (pos * 1000 / dur).toInt() else 0
        timeTv.text = "  " + fmt(pos) + " / " + fmt(dur)
        playIc.set(if (Now.playing) IC.PAUSE else IC.PLAY)
        val cur = Player.current
        val on = Player.active && cur != null && Player.isLiked(context, cur.id)
        heart.set(if (on) Ico3.HEART_ON else Ico3.HEART, if (on) Th.ACC else Th.DIM)
        shuf.set(Ico3.SHUFFLE, if (Player.shuffle) Th.ACC else Th.DIM)
        rep.set(
            if (Player.repeat == 2) Ico3.REPEAT_ONE else Ico3.REPEAT,
            if (Player.repeat > 0) Th.ACC else Th.DIM
        )
        val n = Player.likes(context).size
        if (shownIdx != Player.index || shownLikes != n) {
            shownIdx = Player.index
            shownLikes = n
            fill(homeList, trend)
            fill(searchList, results)
            val lk = Player.likes(context)
            fill(libList, lk)
            libEmpty.visibility = if (lk.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
