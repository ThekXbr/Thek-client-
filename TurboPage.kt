package com.thek.cliente

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class TurboPage(context: Context) : ScrollView(context) {
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
            text = "Modo Turbo & Otimização"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }
        container.addView(title)

        val desc = TextView(context).apply {
            text = "Gerencie os recursos de desempenho e otimização para jogos."
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 30)
        }
        container.addView(desc)

        // Seção 1: Botão Turbo / Boost
        val boostCard = createActionCard("Executar Aceleração Máxima", "Otimiza a memória RAM e prioriza o processo do jogo.") {
            Tools.Boost(context)
        }
        container.addView(boostCard)

        // Exemplo visual de ícone Bolt
        val iconView = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                Ico2.BOLT(canvas, 10f, 10f, 60f, Th.accent)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                setMargins(0, 20, 0, 20)
            }
        }
        container.addView(iconView)
    }

    private fun createActionCard(titleText: String, subtitleText: String, action: () -> Unit): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#222222"))
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 15, 0, 15)
            }
            setOnClickListener { action() }

            addView(TextView(context).apply {
                text = titleText
                textSize = 18f
                setTextColor(Color.WHITE)
            })

            addView(TextView(context).apply {
                text = subtitleText
                textSize = 14f
                setTextColor(Color.GRAY)
                setPadding(0, 10, 0, 0)
            })
        }
    }
}
