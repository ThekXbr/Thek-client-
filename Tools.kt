package com.thek.cliente

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import java.io.File

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
        go(ctx, l ?: Int
