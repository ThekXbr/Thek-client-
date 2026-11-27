package com.thek.cliente

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

object Terms {
    const val VERSION = 2

    val BG = """Ao escolher uma imagem de plano de fundo, você declara que:

• a imagem é sua ou você tem permissão para usá-la;
• ela não viola direitos autorais, de imagem ou de terceiros e não tem conteúdo ilegal;
• ela fica salva somente neste aparelho e não é enviada a ninguém pelo Thek Cliente;
• você é o único responsável pelo conteúdo escolhido.

O Android libera apenas a foto que você escolher. O aplicativo não acessa o restante da sua galeria."""

    val FULL = """TERMOS DE USO E POLÍTICA DE PRIVACIDADE — versão 2

1. Aceite. Ao usar o Thek Cliente você concorda com estes termos. Se não concordar, não utilize o aplicativo.

2. Natureza do aplicativo. O Thek Cliente é um projeto independente e não possui vínculo, parceria ou aprovação de Discord, Spotify, Google, GitHub, Mojang, Microsoft, Audius ou de fabricantes de aparelhos. Nomes e marcas citados pertencem aos seus titulares e são usados apenas para identificar aplicativos instalados no seu aparelho.

3. Uso e responsabilidade. O aplicativo é fornecido no estado em que se encontra, sem garantias. Você o utiliza por sua conta e risco. Na extensão permitida pela lei, o desenvolvedor não responde por perdas, danos, bloqueios de contas ou penalidades aplicadas por jogos e serviços. Respeite as regras de cada jogo e serviço.

4. Dados e privacidade. Desempenho (FPS, memória, temperatura), consumo de dados por aplicativo, tempo de uso, aplicativo em primeiro plano, curtidas e preferências são processados somente no aparelho e não são enviados ao desenvolvedor. O aplicativo usa a internet para música, navegador e verificação de atualizações.

5. Permissões. Sobreposição de tela (exibir o HUD), acesso de uso (identificar jogos e medir consumo), acesso às notificações (capa e controle de música), instalação de aplicativos (atualizações) e Não perturbe. Todas são opcionais e podem ser revogadas nos ajustes do Android.

6. Música. O catálogo vem do Audius, de artistas independentes, por streaming. O conteúdo pertence aos seus titulares: não baixe nem redistribua.

7. Navegador. Os sites exibidos são de terceiros; o aplicativo não controla seu conteúdo.

8. Plano de fundo. Você declara ter direito de usar a imagem escolhida. Ela fica apenas no aparelho.

9. Discord e Spotify. O aplicativo apenas abre esses serviços, que seguem seus próprios termos.

10. Atualizações. São baixadas do repositório do desenvolvedor no GitHub e o Android solicita sua confirmação. Instale apenas versões que você esperava.

11. Alterações. Estes termos podem mudar em novas versões; o aceite será solicitado novamente quando isso ocorrer."""

    // exibido na primeira abertura: so continua se o usuario aceitar
    fun gate(a: Activity, onOk: () -> Unit) {
        val p = Prefs.get(a)
        if (p.getInt("terms_ok", 0) >= VERSION) {
            onOk()
            return
        }
        val sv = ScrollView(a)
        val tv = TextView(a)
        tv.text = FULL
        tv.textSize = 12f
        tv.setPadding(48, 24, 48, 24)
        sv.addView(tv)
        AlertDialog.Builder(a)
            .setTitle("Termos de Uso e Privacidade")
            .setView(sv)
            .setCancelable(false)
            .setPositiveButton("Aceito") { _, _ ->
                p.edit().putInt("terms_ok", VERSION).apply()
                onOk()
            }
            .setNegativeButton("Não aceito") { _, _ -> a.finish() }
            .show()
    }
}

class TermsPickActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        AlertDialog.Builder(this)
            .setTitle("Plano de fundo")
            .setMessage(Terms.BG)
            .setPositiveButton("Aceito e escolher") { _, _ -> pick() }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun pick() {
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
