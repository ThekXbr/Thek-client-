package com.thek.cliente

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class TutorialPage(context: Context) : ScrollView(context) {
    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(30, 30, 30, 30)
    }

    init {
        addView(container)
        render()
    }

    private fun render() {
        container.removeAllViews()

        val title = TextView(context).apply {
            text = "Guia & Tutorial"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }
        container.addView(title)

        val desc = TextView(context).apply {
            text = "Aprenda a configurar e extrair o máximo desempenho do Thek Cliente no seu Android."
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 30)
        }
        container.addView(desc)

        val step1 = createStepCard("1. Ativar Permissão de Sobreposição", "Permita que o HUD seja exibido por cima do jogo (Minecraft, etc).")
        container.addView(step1)

        val step2 = createStepCard("2. Otimização de FPS", "Use o modo Turbo para limpar processos em segundo plano antes de jogar.")
        container.addView(step2)
    }

    private fun createStepCard(titleText: String, subtitleText: String): TextView {
        return TextView(context).apply {
            text = "$titleText\n\n$subtitleText"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#222222"))
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 10)
            }
        }
    }
}
