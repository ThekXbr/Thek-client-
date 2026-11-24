package com.thek.cliente

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle

object Terms {
    val BG = """Ao escolher uma imagem de fundo, voce declara que:

- a imagem e sua ou voce tem permissao para usa-la;
- ela nao viola direitos autorais, de imagem ou de terceiros e nao tem conteudo ilegal;
- ela fica salva so neste aparelho e nao e enviada a ninguem pelo Thek Cliente;
- voce e o unico responsavel pelo conteudo escolhido.

O Android libera apenas a foto que voce escolher. O app nao ve o resto da sua galeria."""

    val FULL = """TERMOS DE USO (modelo geral, nao e aconselhamento juridico)

1. Sobre o app. O Thek Cliente e um projeto independente. Nao tem relacao com Discord, Spotify, Google, GitHub, Mojang, Microsoft, Audius ou fabricantes de celular. Marcas, nomes e logos citados pertencem aos seus donos e aparecem so para identificar os apps instalados no seu aparelho.

2. Uso por sua conta. O app e oferecido como esta, sem garantias. O uso e por sua conta e risco. Na medida permitida pela lei, o desenvolvedor nao responde por perdas, danos ou problemas em jogos, contas ou aparelhos, nem por bloqueios que um jogo ou servico aplique. Respeite as regras de cada jogo e servico.

3. Plano de fundo. Voce declara ter direito de usar a imagem escolhida. Ela fica so no seu aparelho.

4. Musica. A busca usa o catalogo aberto do Audius. As musicas pertencem aos seus artistas e tocam por streaming. Nao baixe nem redistribua.

5. Navegador. Os sites abertos sao de terceiros. O app nao controla nem se responsabiliza pelo conteudo deles.

6. Discord e Spotify. O app so abre esses apps. Cada um segue os termos da propria empresa.

7. Atualizacoes. Sao baixadas de um endereco do GitHub do desenvolvedor. O Android pede sua confirmacao antes de instalar. Instale so atualizacoes que voce esperava.

8. Privacidade. Dados de desempenho (FPS, memoria, temperatura) e suas preferencias ficam no aparelho. O app usa internet para musica, navegador e verificar atualizacoes. Permissoes pedidas: sobreposicao (mostrar o HUD), acesso de uso (saber qual jogo esta aberto), notificacoes (controle da musica) e instalar apps (atualizar).

9. Mudancas. Estes termos podem mudar em novas versoes."""
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
