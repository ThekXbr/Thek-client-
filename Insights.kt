package com.thek.cliente

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import kotlin.math.abs

class UseRow(val label: String, val value: Long, val text: String)

object Insights {
    private fun name(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun isApp(ctx: Context, pkg: String): Boolean =
        pkg != ctx.packageName && ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun size(b: Long): String =
        if (b >= 1073741824L) String.format("%.1f GB", b / 1073741824.0) else "${b / 1048576} MB"

    fun wifi(ctx: Context): List<UseRow> {
        try {
            val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val now = System.currentTimeMillis()
            val st = nsm.querySummary(ConnectivityManager.TYPE_WIFI, null, now - 86400000L, now)
            val b = NetworkStats.Bucket()
            val map = HashMap<Int, Long>()
            while (st.hasNextBucket()) {
                st.getNextBucket(b)
                map[b.uid] = (map[b.uid] ?: 0L) + b.rxBytes + b.txBytes
            }
            st.close()
            val pm = ctx.packageManager
            val out = ArrayList<UseRow>()
            for (e in map.entries.sortedByDescending { it.value }) {
                if (out.size >= 6) break
                if (e.key < 10000 || e.value < 1048576L) continue
                val pk = pm.getPackagesForUid(e.key)?.firstOrNull() ?: continue
                out.add(UseRow(name(ctx, pk), e.value, size(e.value)))
            }
            return out
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun usage(ctx: Context): List<UseRow> {
        try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val m = HashMap<String, Long>()
            for (u in usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86400000L, now)) {
                m[u.packageName] = (m[u.packageName] ?: 0L) + u.totalTimeInForeground
            }
            return m.entries
                .filter { it.value > 60000L && isApp(ctx, it.key) }
                .sortedByDescending { it.value }
                .take(6)
                .map { UseRow(name(ctx, it.key), it.value, "${it.value / 60000} min") }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun recent(ctx: Context): List<UseRow> {
        try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            return usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 1800000L, now)
                .filter { it.lastTimeUsed > now - 1800000L && isApp(ctx, it.packageName) }
                .sortedByDescending { it.lastTimeUsed }
                .distinctBy { it.packageName }
                .take(8)
                .map {
                    UseRow(
                        name(ctx, it.packageName),
                        1800000L - (now - it.lastTimeUsed),
                        "há ${(now - it.lastTimeUsed) / 60000} min"
                    )
                }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun battery(ctx: Context): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lv = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val t = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chg = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val cur = abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) / 1000
        val pct = if (lv >= 0) lv * 100 / sc else 0
        return "Bateria $pct%  ·  $t °C  ·  " + (if (chg) "Carregando" else "Descarregando") + "  ·  $cur mA"
    }
}

class InsightsPage(ctx: Context) : ScrollView(ctx) {
    private val d = resources.displayMetrics.density
    private val col = LinearLayout(ctx)
    private val batTv = TextView(ctx)
    private val ramTv = TextView(ctx)
    private val wifiBox = LinearLayout(ctx)
    private val useBox = LinearLayout(ctx)
    private val recBox = LinearLayout(ctx)
    private val ui = Handler(Looper.getMainLooper())
    private var last = 0L
    private var busy = false

    private fun px(v: Int) = (v * d).toInt()

    private fun head(t: String): TextView {
        val v = TextView(context)
        v.text = t
        v.textSize = 12f
        v.typeface = Typeface.DEFAULT_BOLD
        v.setTextColor(Th.DIM)
        v.setPadding(0, px(20), 0, px(6))
        return v
    }

    private fun note(t: String): TextView {
        val v = TextView(context)
        v.text = t
        v.textSize = 11f
        v.setTextColor(Th.DIM)
        return v
    }

    private fun fill(box: LinearLayout, rows: List<UseRow>) {
        box.removeAllViews()
        if (rows.isEmpty()) {
            box.addView(note("Sem dados. Conceda o acesso de uso na aba Tutorial."))
            return
        }
        val top = rows.maxOf { it.value }.coerceAtLeast(1L)
        for (r in rows) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(0, px(6), 0, px(6))
            val line = LinearLayout(context)
            val n = TextView(context)
            n.text = r.label
            n.textSize = 14f
            n.setTextColor(Th.TEXT)
            n.setSingleLine()
            line.addView(n, LinearLayout.LayoutParams(0, -2, 1f))
            val v = TextView(context)
            v.text = r.text
            v.textSize = 13f
            v.setTextColor(Th.DIM)
            line.addView(v)
            row.addView(line)
            val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
            bar.max = 100
            bar.progress = (r.value * 100 / top).toInt()
            bar.progressTintList = ColorStateList.valueOf(Th.ACC)
            bar.progressBackgroundTintList = ColorStateList.valueOf(Th.LINE)
            row.addView(bar, LinearLayout.LayoutParams(-1, px(4)))
            box.addView(row)
        }
    }

    init {
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(8), px(12), px(8), px(12))
        val t = TextView(ctx)
        t.text = "Consumo"
        t.textSize = 20f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(Th.TEXT)
        col.addView(t)
        for (v in listOf(batTv, ramTv)) {
            v.textSize = 13f
            v.setTextColor(Th.TEXT)
            v.setPadding(0, px(8), 0, 0)
            col.addView(v)
        }
        col.addView(note("O Android não informa a RAM nem a bateria de cada aplicativo. Abaixo estão os dados que o sistema libera."))
        for (b in listOf(wifiBox, useBox, recBox)) b.orientation = LinearLayout.VERTICAL
        col.addView(head("MAIOR USO DE WI-FI (24 H)"))
        col.addView(wifiBox)
        col.addView(head("MAIS TEMPO EM USO (24 H) — ESTIMATIVA DE BATERIA"))
        col.addView(useBox)
        col.addView(head("ATIVOS RECENTEMENTE (30 MIN)"))
        col.addView(recBox)
        val kill = Button(ctx)
        kill.text = "Encerrar apps em segundo plano"
        kill.isAllCaps = false
        kill.setOnClickListener {
            Sampler.clean(context)
            Toast.makeText(context, "Apps em segundo plano encerrados", Toast.LENGTH_SHORT).show()
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.gravity = Gravity.CENTER
        lp.setMargins(0, px(12), 0, 0)
        col.addView(kill, lp)
        addView(col)
    }

    fun update() {
        if (!isShown || busy) return
        val now = System.currentTimeMillis()
        if (now - last < 5000) return
        last = now
        busy = true
        val s = Perf.stats
        batTv.text = Insights.battery(context)
        ramTv.text = "RAM em uso: ${s.ramUsedMb} de ${s.ramTotalMb} MB"
        Thread {
            val w = Insights.wifi(context)
            val u = Insights.usage(context)
            val r = Insights.recent(context)
            ui.post {
                fill(wifiBox, w)
                fill(useBox, u)
                fill(recBox, r)
                busy = false
            }
        }.start()
    }
}
