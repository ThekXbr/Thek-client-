package com.thek.cliente

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import java.io.File

object Prefs {
    fun get(c: Context): SharedPreferences =
        c.getSharedPreferences("thek", Context.MODE_PRIVATE)
}

data class Stats(
    val fps: Float = 0f,
    val maxHz: Float = 60f,
    val ramPct: Float? = null,
    val ramUsedMb: Long = 0,
    val ramTotalMb: Long = 0,
    val cpuPct: Float? = null,
    val cpuTemp: Float? = null,
    val gpuPct: Float? = null,
    val gpuTemp: Float? = null,
    val game: String? = null,
    val gamePkg: String? = null
)

// Dados compartilhados entre o HUD e o menu
object Perf {
    @Volatile var stats = Stats()
    @Volatile var running = true
    val hist = FloatArray(60)

    fun push(v: Float) {
        System.arraycopy(hist, 1, hist, 0, hist.size - 1)
        hist[hist.size - 1] = v
    }
}

class MusicListener : NotificationListenerService()

object Music {
    var title: String? = null
    var artist: String? = null
    var art: Bitmap? = null
    var playing = false
    var pos = 0L
    var dur = 0L
    var access = false
    private var ctl: MediaController? = null

    fun refresh(ctx: Context) {
        try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = msm.getActiveSessions(ComponentName(ctx, MusicListener::class.java))
            access = true
            val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list.firstOrNull()
            ctl = c
            val md = c?.metadata
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val st = c?.playbackState
            playing = st?.state == PlaybackState.STATE_PLAYING
            pos = if (st == null) 0L else if (playing) {
                st.position + ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
            } else st.position
            dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        } catch (e: SecurityException) {
            access = false
            ctl = null
            title = null
            artist = null
            art = null
            playing = false
        } catch (e: Exception) {
        }
    }

    fun toggle() {
        val c = ctl ?: return
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { ctl?.transportControls?.skipToNext() }

    fun prev() { ctl?.transportControls?.skipToPrevious() }
}

object Sampler {
    var gamePkg: String? = null
    private var lastName: String? = null
    private var homePkg: String? = null

    private fun read(p: String) =
        try { File(p).readText().trim() } catch (e: Exception) { null }

    private fun zoneTemp(vararg keys: String): Float? {
        val zones = File("/sys/class/thermal").listFiles()
        zones?.filter { it.name.startsWith("thermal_zone") }?.forEach { z ->
            val type = read("${z.path}/type")?.lowercase() ?: return@forEach
            if (keys.any { type.contains(it) }) {
                val t = read("${z.path}/temp")?.toFloatOrNull() ?: return@forEach
                val c = if (t > 1000) t / 1000f else t
                if (c in 10f..120f) return c
            }
        }
        return null
    }

    private fun cpuLoad(): Float? {
        var sum = 0f
        var n = 0
        val cpus = File("/sys/devices/system/cpu").listFiles()
        cpus?.filter { it.name.matches(Regex("cpu\\d+")) }?.forEach {
            val cur = read("${it.path}/cpufreq/scaling_cur_freq")?.toFloatOrNull()
            val max = read("${it.path}/cpufreq/cpuinfo_max_freq")?.toFloatOrNull()
            if (cur != null && max != null && max > 0) {
                sum += cur / max
                n++
            }
        }
        return if (n > 0) sum / n * 100f else null
    }

    private val gpuPaths = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/class/misc/mali0/device/utilisation"
    )

    private fun gpuLoad(): Float? = gpuPaths.firstNotNullOfOrNull { p ->
        read(p)?.filter { it.isDigit() || it == '.' }?.toFloatOrNull()
    }

    private fun foregroundGame(ctx: Context): String? {
        try {
            val pm = ctx.packageManager
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val ev = usm.queryEvents(now - 1_800_000, now)
            val e = UsageEvents.Event()
            var pkg: String? = null
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    pkg = e.packageName
                } else if (e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && e.packageName == pkg) {
                    pkg = null
                }
            }
            if (homePkg == null) {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                homePkg = pm.resolveActivity(home, 0)?.activityInfo?.packageName ?: ""
            }
            if (pkg == ctx.packageName || pkg == homePkg || pkg == "com.android.systemui") pkg = null
            if (pkg != gamePkg) {
                gamePkg = pkg
                lastName = if (pkg == null) null else try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (x: Exception) {
                    pkg
                }
            }
        } catch (x: Exception) {
        }
        return lastName
    }

    fun freeMb(ctx: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(mi)
        return mi.availMem / 1048576
    }

    fun clean(ctx: Context) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (r in ctx.packageManager.queryIntentActivities(i, 0)) {
            val p = r.activityInfo.packageName
            if (p != ctx.packageName && p != gamePkg) am.killBackgroundProcesses(p)
        }
    }

    fun sample(ctx: Context, fps: Float, maxHz: Float, wantGame: Boolean): Stats {
        val mi = ActivityManager.MemoryInfo()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(mi)
        val total = mi.totalMem / 1048576
        val used = total - mi.availMem / 1048576
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            ?.takeIf { it > 0 }?.div(10f)
        val cpuT = zoneTemp("cpu", "tsens", "soc", "big", "little") ?: bat
        val gm = if (wantGame) foregroundGame(ctx) else null
        return Stats(
            fps = fps,
            maxHz = maxHz,
            ramPct = used * 100f / total,
            ramUsedMb = used,
            ramTotalMb = total,
            cpuPct = cpuLoad(),
            cpuTemp = cpuT,
            gpuPct = gpuLoad(),
            gpuTemp = zoneTemp("gpu"),
            game = gm,
            gamePkg = if (wantGame) gamePkg else null
        )
    }
}
