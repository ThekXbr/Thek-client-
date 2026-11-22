package com.thek.cliente

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
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
    val game: String? = null
)

object Sampler {
    private var lastPkg: String? = null
    private var lastName: String? = null

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
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE)
                as UsageStatsManager
            val now = System.currentTimeMillis()
            val ev = usm.queryEvents(now - 1_800_000, now)
            val e = UsageEvents.Event()
            var pkg: String? = null
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                val fg = e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (fg && e.packageName != ctx.packageName) pkg = e.packageName
            }
            if (pkg != null && pkg != lastPkg) {
                lastPkg = pkg
                lastName = try {
                    val pm = ctx.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (x: Exception) {
                    pkg
                }
            }
        } catch (x: Exception) {
        }
        return lastName
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
            game = if (wantGame) foregroundGame(ctx) else null
        )
    }
}
