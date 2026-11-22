package com.thek.cliente

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*

class MusicPage(ctx: Context) : ScrollView(ctx) {
    private val d = resources.displayMetrics.density
    private val cover = ImageView(ctx)
    private val titleTv = TextView(ctx)
    private val artistTv = TextView(ctx)
    private val prog = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
    private val accessBox = LinearLayout(ctx)
    private lateinit var playTv: TextView

    private fun px(v: Int) = (v * d).toInt()

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

    private fun ctl(t: String, a: () -> Unit): TextView {
        val v = TextView(context)
        v.text = t
        v.textSize = 26f
        v.setTextColor(Color.WHITE)
        v.gravity = Gravity.CENTER
        v.background = glassBg(context, 30)
        v.setPadding(px(18), px(8), px(18), px(8))
        v.setOnClickListener {
            a()
            v.postDelayed({ Music.refresh(context) }, 300)
        }
        return v
    }

    init {
        val p = LinearLayout(ctx)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(px(8), px(8), px(8), px(8))

        accessBox.orientation = LinearLayout.VERTICAL
        accessBox.background = glassBg(ctx)
        accessBox.setPadding(px(12), px(10), px(12), px(10))
        val msg = TextView(ctx)
        msg.text = "Ative o acesso as notificacoes para ver e controlar a musica."
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
        p.addView(accessBox)

        cover.scaleType = ImageView.ScaleType.CENTER_CROP
        cover.background = glassBg(ctx, 20)
        cover.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, 20 * d)
            }
        }
        cover.clipToOutline = true
        val cp = LinearLayout.LayoutParams(px(150), px(150))
        cp.gravity = Gravity.CENTER_HORIZONTAL
        cp.setMargins(0, px(10), 0, px(8))
        p.addView(cover, cp)

        titleTv.textSize = 18f
        titleTv.typeface = Typeface.DEFAULT_BOLD
        titleTv.setTextColor(Color.WHITE)
        titleTv.gravity = Gravity.CENTER
        titleTv.setSingleLine()
        artistTv.textSize = 13f
        artistTv.setTextColor(0xFF9AA4BF.toInt())
        artistTv.gravity = Gravity.CENTER
        artistTv.setSingleLine()
        p.addView(titleTv)
        p.addView(artistTv)
        prog.max = 1000
        val pp = LinearLayout.LayoutParams(-1, px(8))
        pp.setMargins(0, px(10), 0, px(10))
        p.addView(prog, pp)

        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER
        playTv = ctl("\u25B6") { Music.toggle() }
        row.addView(ctl("\u23EE") { Music.prev() })
        val rp = LinearLayout.LayoutParams(-2, -2)
        rp.setMargins(px(12), 0, px(12), 0)
        row.addView(playTv, rp)
        row.addView(ctl("\u23ED") { Music.next() })
        p.addView(row)

        val et = EditText(ctx)
        et.hint = "Buscar no Spotify"
        et.setSingleLine()
        et.textSize = 14f
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(0xFF7A849E.toInt())
        val sr = LinearLayout(ctx)
        sr.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        val go = Button(ctx)
        go.text = "Ir"
        go.setOnClickListener { spotify(et.text.toString()) }
        sr.addView(go)
        val sp = LinearLayout.LayoutParams(-1, -2)
        sp.setMargins(0, px(10), 0, 0)
        p.addView(sr, sp)
        addView(p)
    }

    fun update() {
        accessBox.visibility = if (Music.access) View.GONE else View.VISIBLE
        titleTv.text = Music.title ?: "Nada tocando"
        artistTv.text = Music.artist ?: ""
        cover.setImageBitmap(Music.art)
        playTv.text = if (Music.playing) "\u23F8" else "\u25B6"
        prog.progress = if (Music.dur > 0) (Music.pos * 1000 / Music.dur).toInt() else 0
    }
}
