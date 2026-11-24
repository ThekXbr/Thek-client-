package com.thek.cliente

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.*

private class Slot(val ic: Ico, val btn: Button?, val check: (() -> Boolean)?)

class TutorialPage(ctx: Context) : ScrollView(ctx) {
    private val d = resources.displayMetrics.density
    private val col = LinearLayout(ctx)

    private fun px(v: Int) = (v * d).toInt()

    private fun go(i: Intent) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    private fun pkgUri() = Uri.parse("package:" + context.packageName)

    private fun usageOk(): Boolean {
        val ao = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val m = ao.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.packageName
        )
        return m == AppOpsManager.MODE_ALLOWED
    }

    private fun listenerOk(): Boolean {
        val s = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return s != null && s.contains(context.packageName)
    }

    private fun batteryOk(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun label(t: String, size: Float, color: Int): TextView {
        val v = TextView(context)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        return v
    }

    private fun step(
        title: String, desc: String, btn: String?,
        check: (() -> Boolean)?, open: () -> Unit
    ) {
        val row = LinearLayout(context)
        row.background = glassBg(context)
        row.setPadding(px(12), px(12), px(12), px(12))
        val ic = Ico(context, IC.DOT, 0xFF7A849E.toInt())
        row.addView(ic, LinearLayout.LayoutParams(px(26), px(26)))
        val c2 = LinearLayout(context)
        c2.orientation = LinearLayout.VERTICAL
        c2.setPadding(px(12), 0, 0, 0)
        c2.addView(label(title, 15f, 0xFFFFFFFF.toInt()))
        c2.addView(label(desc, 12f, 0xFF9AA4BF.toInt()))
        var b: Button? = null
        if (btn != null) {
            val bb = Button(context)
            bb.text = btn
            bb.isAllCaps = false
            bb.setOnClickListener { open() }
            c2.addView(bb)
            b = bb
        }
        row.addView(c2, LinearLayout.LayoutParams(0, -2, 1f))
        row.tag = Slot(ic, b, check)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, px(8), 0, 0)
        col.addView(row, lp)
    }

    init {
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(8), px(12), px(8), px(12))
        col.addView(label("Tutorial", 20f, 0xFF6EE7F9.toInt()))
        col.addView(
            label(
                "Para o Android 8 ou mais novo. Siga os passos para o Thek Cliente funcionar bem.",
                12f, 0xFF9AA4BF.toInt()
            )
        )
        step(
            "1. Sobreposicao", "Mostra o HUD por cima dos jogos.", "Permitir",
            { Settings.canDrawOverlays(context) }
        ) { go(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri())) }
        step(
            "2. Acesso de uso", "Descobre qual jogo esta aberto.", "Permitir",
            { usageOk() }
        ) { go(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        step(
            "3. Acesso as notificacoes", "Mostra a capa e controla a musica de outros players.",
            "Permitir", { listenerOk() }
        ) { go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        step(
            "4. Bateria sem restricoes", "Impede o Android de fechar o HUD. Escolha Thek Cliente > Nao otimizar.",
            "Abrir", { batteryOk() }
        ) { go(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        step(
            "5. Inicio automatico",
            "Xiaomi: Seguranca > Permissoes > Inicio automatico. Samsung: Bateria > Limites em segundo plano > Nunca suspender. Outros: Apps > Thek Cliente > Bateria > Sem restricoes.",
            "Abrir", null
        ) { go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri())) }
        step(
            "6. Fixar nos recentes",
            "Abra os apps recentes, toque no icone do Thek Cliente e escolha Fixar ou Bloquear.",
            null, null
        ) {}
        addView(col)
    }

    fun update() {
        for (i in 0 until col.childCount) {
            val s = col.getChildAt(i).tag as? Slot ?: continue
            val ok = s.check?.invoke() ?: false
            s.ic.set(
                if (ok) IC.CHECK else IC.DOT,
                if (ok) 0xFF4ADE80.toInt() else 0xFF7A849E.toInt()
            )
            s.btn?.visibility = if (ok) View.GONE else View.VISIBLE
        }
    }
}
