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

class MenuView(context: Context) : ScrollView(context) {
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
            text = "Menu de Ajustes"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }
        container.addView(title)

        val controlsHeader = TextView(context).apply {
            text = "Controles do HUD"
            textSize = 18f
            setTextColor(Th.accent)
            setPadding(0, 20, 0, 10)
        }
        container.addView(controlsHeader)

        val boostItem = createControlItem("Acelerar Sistema") {
            Tools.Boost(context)
        }
        container.addView(boostItem)

        iconTestRow()

        val themeHeader = TextView(context).apply {
            text = "Estilo Visual"
            textSize = 18f
            setTextColor(Th.accent)
            setPadding(0, 30, 0, 10)
        }
        container.addView(themeHeader)

        val themeDesc = TextView(context).apply {
            text = "Modo padrão ativo"
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 20)
        }
        container.addView(themeDesc)
    }

    private fun createControlItem(label: String, action: () -> Unit): View {
        return TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#292929"))
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 10)
            }
            setOnClickListener { action() }
        }
    }

    private fun iconTestRow() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        val icons = listOf(0, 1, 2, 3, 4)
        val size = 60f

        icons.forEach { iconType ->
            val iconView = object : View(context) {
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val x = 0f
                    val y = 0f
                    when(iconType) {
                        0 -> Ico2.DISC(canvas, x, y, size, Th.accent)
                        1 -> Ico2.CHART(canvas, x, y, size, Th.accent)
                        2 -> Ico2.DOWN(canvas, x, y, size, Th.accent)
                        3 -> Ico2.BOLT(canvas, x, y, size, Th.accent)
                        4 -> Ico2.SPARK(canvas, x, y, size, Th.accent)
                    }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                    setMargins(10, 0, 10, 0)
                }
            }
            row.addView(iconView)
        }
        container.addView(row)
    }
}
