package com.thek.cliente

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import java.io.File

object Tools {
    fun Boost(context: Context): String {
        var freed = 0L
        try {
            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                freed += deleteDir(cacheDir)
            }
            val extCache = context.externalCacheDir
            if (extCache != null && extCache.exists()) {
                freed += deleteDir(extCache)
            }
        } catch (_: Exception) {}

        try {
            System.gc()
            Runtime.getRuntime().gc()
        } catch (_: Exception) {}

        val mb = freed / (1024 * 1024)
        return if (mb > 0) "Liberados ${mb}MB de lixo!" else "Memória e cache otimizados!"
    }

    private fun deleteDir(dir: File): Long {
        var count = 0L
        val files = dir.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.isDirectory) {
                    count += deleteDir(f)
                } else {
                    val len = f.length()
                    if (f.delete()) {
                        count += len
                    }
                }
            }
        }
        return count
    }

    fun Connect(context: Context, appName: String) {
        try {
            if (appName == "discord") {
                val intent = context.packageManager.getLaunchIntentForPackage("com.discord")
                if (intent != null) {
                    context.startActivity(intent)
                    return
                }
            } else if (appName == "spotify") {
                val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                if (intent != null) {
                    context.startActivity(intent)
                    return
                }
            }
        } catch (_: Exception) {}

        val url = if (appName == "discord") "https://discord.com" else "https://open.spotify.com"
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(browserIntent)
        } catch (_: Exception) {}
    }
}
