package com.thek.cliente

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.view.View
import java.io.File
import kotlin.math.min

object Boost {
    private var lastPkg: String? = null
    private var lastClean = 0L

    fun storage(): Pair<Long, Long> {
        val st = StatFs(Environment.getDataDirectory().path)
        return Pair(st.availableBytes, st.totalBytes)
    }

    fun gb(b: Long): String = String.format("%.1f GB", b / 1073741824.0)

    private fun del(f: File?): Long {
        if (f == null || !f.exists()) return 0L
        var n = 0L
        if (f.isDirectory) {
            f.listFiles()?.forEach { n += del(it) }
        } else if (f.name != "update.apk") {
            n += f.length()
            f.delete()
        }
        return n
    }

    // apaga o cache do proprio app
    fun cleanCache(ctx: Context): Long = del(ctx.cacheDir) + del(ctx.externalCacheDir)

    fun dnd(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return false
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return true
    }

    fun accelerate(ctx: Context, done: (String) -> Unit) {
        val before = Sampler.freeMb(ctx)
        Sampler.clean(ctx)
        cleanCache(ctx)
        val quiet = dnd(ctx)
        Thread {
            Thread.sleep(900)
            val gain = maxOf(0L, Sampler.freeMb(ctx) - before)
            val msg = "Pronto! +$gain MB livres" + (if (quiet) " e nao perturbe ativo" else "")
            Handler(Looper.getMainLooper()).post { done(msg) }
        }.start()
    }

    // chamado a cada segundo pelo servico do HUD
    fun auto(ctx: Context, s: Stats) {
        val p = Prefs.get(ctx)
        val now = System.currentTimeMillis()
        if (p.getBoolean("auto_clean", false) && (s.ramPct ?: 0f) > 85f && now - lastClean > 120000L) {
            Sampler.clean(ctx)
            lastClean = now
        }
        if (p.getBoolean("auto_boost", false) && s.gamePkg != null && s.gamePkg != lastPkg) {
            accelerate(ctx) { }
        }
        lastPkg = s.gamePkg
    }
}

object Connect {
    private fun go(ctx: Context, i: Intent) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    private fun open(ctx: Context, pkg: String, web: String) {
        val l = ctx.packageManager.getLaunchIntentForPackage(pkg)
        go(ctx, l ?: Intent(Intent.ACTION_VIEW, Uri.parse(web)))
    }

    fun discord(ctx: Context) = open(ctx, "com.discord", "https://discord.com/app")

    fun discordLink(ctx: Context, t: String) {
        val s = t.trim()
        val url = when {
            s.startsWith("http") -> s
            s.startsWith("discord.gg") || s.startsWith("discord.com") -> "https://$s"
            s.isNotEmpty() && s.all { it.isDigit() } -> "https://discord.com/users/$s"
            else -> return
        }
        go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun spotify(ctx: Context) = open(ctx, "com.spotify.music", "https://open.spotify.com")

    fun spotifySearch(ctx: Context, q: String) {
        val s = Uri.encode(q.trim())
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$s"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$s")))
        }
    }

    fun storageSettings(ctx: Context) = go(ctx, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
}

// Icones extras desenhados em codigo (grade 24x24)
class Ico2(ctx: Context, var kind: Int, var col: Int = Color.WHITE) : View(ctx) {
    companion object {
        const val DISK = 0
        const val CHART = 1
        const val DOWN = 2
        const val BOLT = 3
        const val SPARK = 4
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        val s = (24 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(s, w), resolveSize(s, h))
    }

    override fun onDraw(c: Canvas) {
        val k = min(width, height) / 24f
        c.save()
        c.scale(k, k)
        p.color = col
        p.strokeWidth = 2f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.STROKE
        when (kind) {
            DISK -> {
                c.drawRoundRect(3f, 5f, 21f, 19f, 3f, 3f, p)
                c.drawLine(3f, 14f, 21f, 14f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(17f, 17f, 1.2f, p)
            }
            CHART -> {
                val l = Path()
                l.moveTo(3f, 17f)
                l.lineTo(9f, 11f)
                l.lineTo(13f, 15f)
                l.lineTo(21f, 6f)
                c.drawPath(l, p)
                c.drawLine(3f, 21f, 21f, 21f, p)
            }
            DOWN -> {
                c.drawLine(12f, 4f, 12f, 16f, p)
                c.drawLine(6f, 11f, 12f, 17f, p)
                c.drawLine(18f, 11f, 12f, 17f, p)
                c.drawLine(5f, 21f, 19f, 21f, p)
            }
