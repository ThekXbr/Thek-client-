package com.thek.cliente

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Ai {
    private const val CLAUDE = "claude-haiku-5-5"
    private const val GROQ = "llama-3.3-70b-versatile"
    private const val GBASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private var gModel = "gemini-flash-latest"
    val history = ArrayList<Pair<String, String>>()

    fun key(c: Context): String = Prefs.get(c).getString("ai_key", "") ?: ""

    // 0 = Gemini, 1 = Groq, 2 = Anthropic
    private fun kind(k: String): Int = when {
        k.startsWith("AIza") -> 0
        k.startsWith("gsk_") -> 1
        else -> 2
    }

    private fun system(c: Context): String {
        var s = "Você é o assistente do Thek Cliente, um aplicativo Android de desempenho para jogos. " +
            "Responda em português do Brasil, de forma curta, clara e prática. " +
            "Foque em desempenho, bateria, memória, rede e configurações do Android. Não invente dados."
        if (Prefs.get(c).getBoolean("ai_share", true)) {
            val st = Perf.stats
            s += "\nEstado atual do aparelho: FPS ${st.fps.toInt()}, " +
                "CPU ${st.cpuPct?.toInt() ?: "?"}%, GPU ${st.gpuPct?.toInt() ?: "?"}%, " +
                "RAM ${st.ramUsedMb}/${st.ramTotalMb} MB, jogo aberto: ${st.game ?: "nenhum"}."
        }
        return s
    }

    private fun msg(role: String, text: String): JSONObject {
        val o = JSONObject()
        o.put("role", role)
        o.put("content", text)
        return o
    }

    private fun part(t: String): JSONObject {
        val o = JSONObject()
        o.put("text", t)
        return o
    }

    private fun http(url: String, method: String, hdr: Map<String, String>, body: String?): Pair<Int, String> {
        val con = URL(url).openConnection() as HttpURLConnection
        con.requestMethod = method
        con.connectTimeout = 10000
        con.readTimeout = 60000
        con.setRequestProperty("user-agent", "ThekCliente/1.0")
        for ((a, b) in hdr) con.setRequestProperty(a, b)
        if (body != null) {
            con.doOutput = true
            con.setRequestProperty("content-type", "application/json")
            con.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = con.responseCode
        val stream = if (code in 200..299) con.inputStream else con.errorStream
        val s = stream?.bufferedReader()?.readText() ?: ""
        con.disconnect()
        return Pair(code, s)
    }

    private fun findModel(k: String): String? {
        val r = http("$GBASE?pageSize=200", "GET", mapOf("x-goog-api-key" to k), null)
        if (r.first != 200) return null
        val arr = JSONObject(r.second).getJSONArray("models")
        val bad = listOf("image", "tts", "live", "audio", "embed", "robotics", "computer")
        val names = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val n = o.getString("name").removePrefix("models/")
            val gen = o.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") ?: false
            if (gen && n.contains("flash") && bad.none { n.contains(it) }) names.add(n)
        }
        return names.sortedDescending().firstOrNull()
    }

    fun ask(c: Context, text: String, done: (String, Boolean) -> Unit) {
        history.add(Pair("user", text))
        while (history.size > 12) history.removeAt(0)
        while (history.isNotEmpty() && history[0].first != "user") history.removeAt(0)
        val snap = ArrayList(history)
        val sys = system(c)
        val k = key(c)
        Thread {
            var out = ""
            var ok = true
            try {
                val kd = kind(k)
                var r: Pair<Int, String>
                if (kd == 0) {
                    val contents = JSONArray()
                    for (m in snap) {
                        val o = JSONObject()
                        o.put("role", if (m.first == "user") "user" else "model")
                        o.put("parts", JSONArray().put(part(m.second)))
                        contents.put(o)
                    }
                    val b = JSONObject()
                    val si = JSONObject()
                    si.put("parts", JSONArray().put(part(sys)))
                    b.put("systemInstruction", si)
                    b.put("contents", contents)
                    val gc = JSONObject()
                    gc.put("maxOutputTokens", 700)
                    b.put("generationConfig", gc)
                    val hdr = mapOf("x-goog-api-key" to k)
                    r = http("$GBASE/$gModel:generateContent", "POST", hdr, b.toString())
                    if (r.first == 404) {
                        val m = findModel(k)
                        if (m != null) {
                            gModel = m
                            r = http("$GBASE/$m:generateContent", "POST", hdr, b.toString())
                        }
                    }
                } else {
                    val arr = JSONArray()
                    if (kd == 1) arr.put(msg("system", sys))
                    for (m in snap) arr.put(msg(m.first, m.second))
                    val b = JSONObject()
                    b.put("model", if (kd == 1) GROQ else CLAUDE)
                    b.put("max_tokens", 700)
                    b.put("messages", arr)
                    if (kd == 2) b.put("system", sys)
                    r = if (kd == 1) {
                        http(
                            "https://api.groq.com/openai/v1/chat/completions", "POST",
                            mapOf("authorization" to "Bearer $k"), b.toString()
                        )
                    } else {
                        http(
                            "https://api.anthropic.com/v1/messages", "POST",
                            mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01"), b.toString()
                        )
                    }
                }
                val j = JSONObject(r.second)
                if (r.first in 200..299) {
                    if (kd == 0) {
                        val ps = j.getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                        val sb = StringBuilder()
                        for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
                        out = sb.toString().trim()
                    } else if (kd == 1) {
                        out = j.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content").trim()
                    } else {
                        val ca = j.getJSONArray("content")
                        val sb = StringBuilder()
                        for (i in 0 until ca.length()) {
                            val b = ca.getJSONObject(i)
                            if (b.optString("type") == "text") sb.append(b.getString("text"))
                        }
                        out = sb.toString().trim()
                    }
                    if (out.isEmpty()) {
                        ok = false
                        out = "Resposta vazia ou bloqueada. Tente reformular."
                    } else {
                        history.add(Pair("assistant", out))
                    }
                } else {
                    ok = false
                    out = when (r.first) {
                        400, 401, 403 -> "Chave inválida ou sem acesso. Confira a chave (no Google, talvez seja preciso verificar idade e telefone na conta) ou use a chave do Groq."
                        429 -> "Limite gratuito atingido. Aguarde um pouco e tente de novo."
                        else -> j.optJSONObject("error")?.optString("message") ?: "Erro ${r.first}"
                    }
                }
            } catch (e: Exception) {
                ok = false
                out = "Sem conexão ou erro ao falar com a IA."
            }
            if (!ok && history.isNotEmpty() && history.last().first == "user") {
                history.removeAt(history.size - 1)
            }
            Handler(Looper.getMainLooper()).post { done(out, ok) }
        }.start()
    }
}

class AiPage(ctx: Context) : LinearLayout(ctx) {
    private val d = resources.displayMetrics.density
    private val setupWrap = ScrollView(ctx)
    private val chat = LinearLayout(ctx)
    private val greet = LinearLayout(ctx)
    private val msgs = LinearLayout(ctx)
    private val scroll = ScrollView(ctx)
    private val input = EditText(ctx)
    private val keyEt = EditText(ctx)
    private var busy = false

    private fun px(v: Int) = (v * d).toInt()

    private fun txt(t: String, size: Float, color: Int): TextView {
        val v = TextView(context)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        return v
    }

    private fun bubble(t: String, user: Boolean): TextView {
        val v = TextView(context)
        v.text = t
        v.textSize = 14f
        v.setTextColor(Th.TEXT)
        v.setTextIsSelectable(true)
        v.setPadding(px(12), px(10), px(12), px(10))
        v.background = Th.card(context, 14, if (user) (Th.ACC and 0x00FFFFFF) or (0x66 shl 24) else Th.CARD)
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.gravity = if (user) Gravity.END else Gravity.START
        lp.setMargins(if (user) px(40) else 0, px(4), if (user) 0 else px(40), px(4))
        msgs.addView(v, lp)
        return v
    }

    private fun syncGreet() {
        greet.visibility = if (msgs.childCount == 0) View.VISIBLE else View.GONE
    }

    private fun scrollDown() {
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun refresh() {
        val has = Ai.key(context).isNotBlank()
        setupWrap.visibility = if (has) View.GONE else View.VISIBLE
        chat.visibility = if (has) View.VISIBLE else View.GONE
    }

    private fun open(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun send(t: String) {
        val q = t.trim()
        if (busy || q.isEmpty()) return
        busy = true
        input.setText("")
        bubble(q, true)
        syncGreet()
        val wait = bubble("Pensando…", false)
        scrollDown()
        Ai.ask(context, q) { out, ok ->
            msgs.removeView(wait)
            val b = bubble(out, false)
            if (!ok) b.setTextColor(0xFFFF7B72.toInt())
            busy = false
            scrollDown()
        }
    }

    init {
        orientation = LinearLayout.VERTICAL

        val setup = LinearLayout(ctx)
        setup.orientation = LinearLayout.VERTICAL
        setup.setPadding(px(8), px(12), px(8), px(12))
        val st = txt("Assistente IA", 20f, Th.TEXT)
        st.typeface = Typeface.DEFAULT_BOLD
        setup.addView(st)
        setup.addView(
            txt(
                "Usa o Gemini, do Google, no plano gratuito. Toque em \"Criar chave grátis (Gemini)\", " +
                    "entre com sua conta Google, copie a chave (começa com AIza) e cole aqui. " +
                    "O plano grátis tem limites e pode mudar. Se a sua conta for restrita, use a alternativa do Groq. " +
                    "Chaves do Groq (gsk_) e da Anthropic (paga) também funcionam.\n\n" +
                    "A chave fica salva só neste aparelho. Suas mensagens (e, se você deixar ativado, FPS, CPU, RAM e o jogo aberto) " +
                    "são enviadas ao provedor da chave para gerar as respostas.",
                12f, Th.DIM
            )
        )
        val site = Button(ctx)
        site.text = "Criar chave grátis (Gemini)"
        site.isAllCaps = false
        site.setOnClickListener { open("https://aistudio.google.com/apikey") }
        setup.addView(site)
        val alt = Button(ctx)
        alt.text = "Alternativa grátis: Groq"
        alt.isAllCaps = false
        alt.setOnClickListener { open("https://console.groq.com/keys") }
        setup.addView(alt)
        keyEt.hint = "Cole sua chave de API"
        keyEt.setSingleLine()
        keyEt.textSize = 14f
        keyEt.setTextColor(Th.TEXT)
        keyEt.setHintTextColor(Th.DIM)
        keyEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setup.addView(keyEt)
        val save = Button(ctx)
        save.text = "Salvar chave"
        save.isAllCaps = false
        save.setOnClickListener {
            val k = keyEt.text.toString().trim()
            if (k.isNotEmpty()) {
                Prefs.get(context).edit().putString("ai_key", k).apply()
                keyEt.setText("")
                refresh()
            }
        }
        setup.addView(save)
        setupWrap.addView(setup)
        addView(setupWrap, LinearLayout.LayoutParams(-1, 0, 1f))

        chat.orientation = LinearLayout.VERTICAL

        // boas-vindas
        greet.orientation = LinearLayout.VERTICAL
        greet.gravity = Gravity.CENTER
        greet.setPadding(px(8), px(16), px(8), px(8))
        greet.addView(Ico2(ctx, Ico2.SPARK, Th.ACC), LinearLayout.LayoutParams(px(32), px(32)))
        val gt = txt("Olá! Como posso ajudar?", 22f, Th.TEXT)
        gt.typeface = Typeface.DEFAULT_BOLD
        gt.setPadding(0, px(8), 0, 0)
        greet.addView(gt)
        greet.addView(txt("Pergunte sobre desempenho, bateria ou configurações.", 12f, Th.DIM))
        chat.addView(greet)

        msgs.orientation = LinearLayout.VERTICAL
        scroll.addView(msgs)
        chat.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(ctx)
        val sug = listOf(
            "Por que meu FPS está baixo?",
            "Dicas para o jogo atual",
            "Como economizar bateria jogando?"
        )
        for (s in sug) {
            val c = TextView(ctx)
            c.text = s
            c.textSize = 12f
            c.setTextColor(Th.TEXT)
            c.setPadding(px(12), px(8), px(12), px(8))
            c.background = Th.card(ctx, 16)
            c.setOnClickListener { send(s) }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.setMargins(0, px(6), px(8), px(6))
            chips.addView(c, lp)
        }
        hs.addView(chips)
        chat.addView(hs)

        val row = LinearLayout(ctx)
        input.hint = "Pergunte algo"
        input.setSingleLine()
        input.textSize = 14f
        input.setTextColor(Th.TEXT)
        input.setHintTextColor(Th.DIM)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, _, _ ->
            send(input.text.toString())
            true
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val go = Button(ctx)
        go.text = "Enviar"
        go.isAllCaps = false
        go.setOnClickListener { send(input.text.toString()) }
        row.addView(go)
        chat.addView(row)

        val foot = LinearLayout(ctx)
        foot.gravity = Gravity.CENTER_VERTICAL
        val sw = Switch(ctx)
        sw.text = "Enviar dados do aparelho"
        sw.textSize = 11f
        sw.setTextColor(Th.DIM)
        sw.isChecked = Prefs.get(ctx).getBoolean("ai_share", true)
        sw.setOnCheckedChangeListener { _, on ->
            Prefs.get(context).edit().putBoolean("ai_share", on).apply()
        }
        foot.addView(sw, LinearLayout.LayoutParams(0, -2, 1f))
        val clr = Button(ctx)
        clr.text = "Limpar"
        clr.textSize = 11f
        clr.isAllCaps = false
        clr.setOnClickListener {
            Ai.history.clear()
            msgs.removeAllViews()
            syncGreet()
        }
        foot.addView(clr)
        val chg = Button(ctx)
        chg.text = "Trocar chave"
        chg.textSize = 11f
        chg.isAllCaps = false
        chg.setOnClickListener {
            Prefs.get(context).edit().putString("ai_key", "").apply()
            refresh()
        }
        foot.addView(chg)
        chat.addView(foot)
        addView(chat, LinearLayout.LayoutParams(-1, 0, 1f))

        for (m in Ai.history) bubble(m.second, m.first == "user")
        syncGreet()
        refresh()
    }
}
