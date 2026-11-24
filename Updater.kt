package com.thek.cliente

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    private const val REPO = "ThekXbr/Thek-client-"
    @Volatile var latest = 0
    @Volatile var url: String? = null
    @Volatile var busy = false
    @Volatile var status = "Toque em Verificar agora"

    @Suppress("DEPRECATION")
    fun current(ctx: Context): Int = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (e: Exception) {
        0
    }

    fun has(ctx: Context): Boolean = latest > current(ctx) && url != null

    fun auto(ctx: Context) {
        val p = Prefs.get(ctx)
        val now = System.currentTimeMillis()
        if (now - p.getLong("upd_check", 0L) < 6 * 3600 * 1000L) return
        p.edit().putLong("upd_check", now).apply()
        check(ctx, false)
    }

    fun check(ctx: Context, manual: Boolean) {
        if (busy) return
        busy = true
        if (manual) status = "Verificando..."
        Thread {
            try {
                val c = URL("https://api.github.com/repos/$REPO/releases/latest")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "ThekCliente")
                val code = c.responseCode
                if (code != 200) {
                    status = if (code == 404) {
                        "Nenhuma versao publica encontrada (o repositorio precisa ser publico)"
                    } else {
                        "Erro $code ao verificar"
                    }
                } else {
                    val j = JSONObject(c.inputStream.bufferedReader().readText())
                    latest = j.getString("tag_name").filter { it.isDigit() }.toIntOrNull() ?: 0
                    val assets = j.getJSONArray("assets")
                    url = null
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.getString("name").endsWith(".apk")) {
                            url = a.getString("browser_download_url")
                        }
                    }
                    val cur = current(ctx)
                    status = if (latest > cur && url != null) {
                        "Nova versao v$latest disponivel (voce tem v$cur)"
                    } else {
                        "Voce esta na versao mais recente (v$cur)"
                    }
                    if (has(ctx)) notifyNew(ctx)
                }
                c.disconnect()
            } catch (e: Exception) {
                status = "Sem internet ou erro ao verificar"
            }
            busy = false
        }.start()
    }

    private fun notifyNew(ctx: Context) {
        val p = Prefs.get(ctx)
        if (p.getInt("upd_notified", 0) == latest) return
        p.edit().putInt("upd_notified", latest).apply()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("upd", "Atualizacoes", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, "upd")
            .setContentTitle("Atualizacao disponivel")
            .setContentText("Thek Cliente v$latest esta pronto para instalar")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(2, n)
    }

    fun install(ctx: Context) {
        val u = url ?: return
        if (busy) return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            status = "Permita instalar apps desta fonte e toque de novo"
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        busy = true
        Thread {
            try {
                status = "Baixando..."
                val f = File(ctx.cacheDir, "update.apk")
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 15000
                val total = c.contentLength
                c.inputStream.use { inp ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(16384)
                        var sum = 0L
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            sum += n
                            if (total > 0) status = "Baixando... " + (sum * 100 / total) + "%"
                        }
                    }
                }
                c.disconnect()
                status = "Instalando..."
                val inst = ctx.packageManager.packageInstaller
                val sp = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = inst.createSession(sp)
                val s = inst.openSession(id)
                s.openWrite("thek.apk", 0, f.length()).use { out ->
                    f.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
                val intent = Intent(ctx, InstallReceiver::class.java)
                    .setAction("com.thek.cliente.INSTALL")
                var fl = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= 31) fl = fl or PendingIntent.FLAG_MUTABLE
                s.commit(PendingIntent.getBroadcast(ctx, id, intent, fl).intentSender)
                s.close()
            } catch (e: Exception) {
                status = "Falha ao baixar ou instalar: " + (e.message ?: "")
            }
            busy = false
        }.start()
    }
}

class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(c: Context, i: Intent) {
        val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val conf = i.extras?.get(Intent.EXTRA_INTENT) as? Intent
            if (conf != null) {
                conf.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                c.startActivity(conf)
            }
            Updater.status = "Confirme a instalacao na tela do Android"
        } else if (st == PackageInstaller.STATUS_SUCCESS) {
            Updater.status = "Atualizado!"
        } else {
            Updater.status = "Instalacao falhou: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "")
        }
    }
}
