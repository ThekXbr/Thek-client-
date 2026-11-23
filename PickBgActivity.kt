package com.thek.cliente

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Abre o seletor de imagens e salva a escolhida em Prefs ("bg_uri").
 * Abrir de dentro do menu com:
 *   context.startActivity(Intent(context, PickBgActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
 * Para remover o fundo:
 *   Prefs.get(context).edit().remove("bg_uri").apply()
 */
class PickBgActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, 1)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (res == RESULT_OK && uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            Prefs.get(this).edit().putString("bg_uri", uri.toString()).apply()
        }
        finish()
    }
}
