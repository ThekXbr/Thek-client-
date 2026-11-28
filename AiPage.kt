package com.thek.cliente

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

class AiPage(context: Context) : ScrollView(context) {
    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(30, 30, 30, 30)
    }

    private val responseView = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 15f
        setPadding(0, 20, 0, 20)
    }

    private val inputField = EditText(context).apply {
        hint = "Digite sua pergunta para a IA..."
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        setPadding(30, 30, 30, 30)
        setBackgroundColor(Color.parseColor("#292929"))
    }

    init {
        addView(container)
        render()
    }

    private fun render() {
        container.removeAllViews()

        val title = TextView(context).apply {
            text = "Assistente IA"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }
        container.addView(title)

        container.addView(inputField)

        val sendBtn = TextView(context).apply {
            text = "ENVIAR PERGUNTA"
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Th.accent)
            setPadding(30, 30, 30, 30)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 20, 0, 20)
            }
            setOnClickListener {
                val prompt = inputField.text.toString().trim()
                if (prompt.isNotEmpty()) {
                    responseView.text = "Pensando..."
                    Thread {
                        val ans = queryAi(prompt)
                        post { responseView.text = ans }
                    }.start()
                }
            }
        }
        container.addView(sendBtn)
        container.addView(responseView)

        val sparkView = object : View(context) {
            private val p = Paint().apply { isAntiAlias = true }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                Ico2.SPARK(canvas, 10f, 10f, 60f, Th.accent)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                setMargins(0, 20, 0, 0)
            }
        }
        container.addView(sparkView)
    }

    private fun queryAi(prompt: String): String {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyDummyKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val json = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            }

            conn.outputStream.write(json.toString().toByteArray())
            val code = conn.responseCode
            if (code == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(res)
                val candidates = obj.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return parts.getJSONObject(0).getString("text")
                    }
                }
            }
        } catch (e: Exception) {
            return "Erro ao consultar IA: ${e.localizedMessage}"
        }
        return "Resposta vazia da IA."
    }
}
