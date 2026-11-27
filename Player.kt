package com.thek.cliente

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class Track(val id: String, val title: String, val artist: String, val art: String?)

object Player {
    private const val API = "https://api.audius.co/v1"
    private const val APP = "ThekCliente"
    private val ui = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    var list: List<Track> = emptyList()
    var index = -1
    var active = false
    var playing = false
    var loading = false
    var shuffle = false
    var repeat = 0
    var art: Bitmap? = null
    val current: Track? get() = list.getOrNull(index)

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun parse(arr: JSONArray): List<Track> {
        val out = ArrayList<Track>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val aw = o.optJSONObject("artwork")
            val us = o.optJSONObject("user")
            out.add(
                Track(
                    o.getString("id"),
                    o.optString("title", "?"),
                    us?.optString("name", "") ?: "",
                    aw?.optString("150x150")
                )
            )
        }
        return out
    }

    fun load(path: String, done: (List<Track>, String?) -> Unit) {
        Thread {
            var out: List<Track> = emptyList()
            var err: String? = null
            try {
                val sep = if (path.contains("?")) "&" else "?"
                val arr = JSONObject(fetch("$API$path${sep}app_name=$APP")).getJSONArray("data")
                out = parse(arr)
                if (out.isEmpty()) err = "Nenhum resultado"
            } catch (e: Exception) {
                err = "Sem conexão ou erro ao carregar"
            }
            ui.post { done(out, err) }
        }.start()
    }

    fun likes(c: Context): List<Track> {
        val s = Prefs.get(c).getString("likes", "[]") ?: "[]"
        val out = ArrayList<Track>()
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(
                    Track(
                        o.getString("id"), o.getString("t"), o.getString("a"),
                        o.optString("r", "").ifEmpty { null }
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun isLiked(c: Context, id: String): Boolean = likes(c).any { it.id == id }

    fun toggleLike(c: Context, t: Track) {
        val cur = likes(c).toMutableList()
        val ex = cur.indexOfFirst { it.id == t.id }
        if (ex >= 0) cur.removeAt(ex) else cur.add(0, t)
        val a = JSONArray()
        for (x in cur) {
            val o = JSONObject()
            o.put("id", x.id)
            o.put("t", x.title)
            o.put("a", x.artist)
            o.put("r", x.art ?: "")
            a.put(o)
        }
        Prefs.get(c).edit().putString("likes", a.toString()).apply()
    }

    private fun loadArt(u: String?) {
        if (u.isNullOrEmpty()) return
        Thread {
            try {
                val bm = BitmapFactory.decodeStream(URL(u).openStream())
                ui.post { if (current?.art == u) art = bm }
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun release() {
        try { mp?.release() } catch (e: Exception) {}
        mp = null
    }

    fun playFrom(l: List<Track>, i: Int) {
        list = l
        play(i)
    }

    fun play(i: Int) {
        val t = list.getOrNull(i) ?: return
        index = i
        active = true
        loading = true
        playing = false
        art = null
        release()
        loadArt(t.art)
        val m = MediaPlayer()
        mp = m
        m.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        m.setOnPreparedListener {
            loading = false
            it.start()
            playing = true
        }
        m.setOnCompletionListener {
            if (repeat == 2) {
                play(index)
            } else if (repeat == 0 && !shuffle && index >= list.size - 1) {
                playing = false
            } else {
                next()
            }
        }
        m.setOnErrorListener { _, _, _ ->
            loading = false
            playing = false
            true
        }
        try {
            m.setDataSource("$API/tracks/${t.id}/stream?app_name=$APP")
            m.prepareAsync()
        } catch (e: Exception) {
            loading = false
        }
    }

    fun toggle() {
        val m = mp ?: return
        try {
            if (m.isPlaying) {
                m.pause()
                playing = false
            } else {
                m.start()
                playing = true
            }
        } catch (e: Exception) {
        }
    }

    fun seek(ms: Int) {
        try { mp?.seekTo(ms) } catch (e: Exception) {}
    }

    fun next() {
        if (list.isEmpty()) return
        var n = (index + 1) % list.size
        if (shuffle && list.size > 1) {
            do {
                n = (0 until list.size).random()
            } while (n == index)
        }
        play(n)
    }

    fun prev() {
        if (list.isNotEmpty()) play((index - 1 + list.size) % list.size)
    }

    fun pos(): Long = try {
        if (mp != null && !loading) mp!!.currentPosition.toLong() else 0L
    } catch (e: Exception) { 0L }

    fun dur(): Long = try {
        if (mp != null && !loading) mp!!.duration.toLong() else 0L
    } catch (e: Exception) { 0L }
}

// Escolhe entre o player proprio e o que estiver tocando no celular
object Now {
    private val own: Boolean
        get() = Player.active && (Player.playing || Player.loading || !Music.playing)
    val title: String? get() = if (own) Player.current?.title else Music.title
    val artist: String? get() = if (own) Player.current?.artist else Music.artist
    val art: Bitmap? get() = if (own) Player.art else Music.art
    val playing: Boolean get() = if (own) Player.playing else Music.playing
    val pos: Long get() = if (own) Player.pos() else Music.pos
    val dur: Long get() = if (own) Player.dur() else Music.dur
    val access: Boolean get() = Player.active || Music.access
    fun toggle() { if (own) Player.toggle() else Music.toggle() }
    fun next() { if (own) Player.next() else Music.next() }
    fun prev() { if (own) Player.prev() else Music.prev() }
}

// Plano de fundo
object Bg {
    private var ver = -1L
    private var bmp: Bitmap? = null

    private fun file(c: Context) = File(c.filesDir, "bg.jpg")

    fun bitmap(c: Context): Bitmap? {
        val v = Prefs.get(c).getLong("bg_ver", 0L)
        if (v != ver) {
            ver = v
            bmp = if (v == 0L) null else try {
                BitmapFactory.decodeFile(file(c).path)
            } catch (e: Exception) { null }
        }
        return bmp
    }

    fun opacity(c: Context): Float = Prefs.get(c).getInt("opacity", 60) / 100f

    fun save(c: Context, uri: Uri) {
        try {
            val opt = BitmapFactory.Options()
            opt.inJustDecodeBounds = true
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
            var s = 1
            while (opt.outWidth / s > 1024 || opt.outHeight / s > 1024) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            val bm = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, o2)
            } ?: return
            file(c).outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            Prefs.get(c).edit().putLong("bg_ver", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
        }
    }

    fun clear(c: Context) {
        file(c).delete()
        Prefs.get(c).edit().putLong("bg_ver", 0L).apply()
    }
}

class PickActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(i, 7)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val u = data?.data
        if (res == RESULT_OK && u != null) Bg.save(this, u)
        finish()
    }
}
