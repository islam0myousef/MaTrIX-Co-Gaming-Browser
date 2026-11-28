package com.matrixco.browser

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** اتصال مباشر بـ Gemini API (بمفتاحك أنت). لو الموديل عليه ضغط (503/429) أو اسمه قديم (404) بيجرب موديلات flash تانية تلقائي. */
object AiClient {
    private var cachedModels: List<String>? = null

    private fun open(apiKey: String, url: String, post: Boolean): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = if (post) "POST" else "GET"
        conn.connectTimeout = 20000
        conn.readTimeout = 90000
        conn.doOutput = post
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey.trim())
        return conn
    }

    private fun bodyOf(prompt: String): ByteArray {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        return body.toString().toByteArray(Charsets.UTF_8)
    }

    private fun errMsg(text: String): String =
        try { JSONObject(text).getJSONObject("error").getString("message") } catch (_: Exception) { text.take(200) }

    private fun textOf(o: JSONObject): String {
        val sb = StringBuilder()
        val cands = o.optJSONArray("candidates") ?: return ""
        if (cands.length() == 0) return ""
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return ""
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString()
    }

    private fun retryable(code: Int) = code == 503 || code == 429 || code == 404 || code == 500

    /** موديلات flash المتاحة لمفتاحك (بتتحمل مرة واحدة) */
    private fun fallbackModels(apiKey: String): List<String> {
        cachedModels?.let { return it }
        var conn: HttpURLConnection? = null
        return try {
            conn = open(apiKey, "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", false)
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONObject(text).getJSONArray("models")
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").removePrefix("models/")
                val methods = o.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                if (name.startsWith("gemini") && name.contains("flash") && methods.contains("generateContent")
                    && !name.contains("image") && !name.contains("tts") && !name.contains("live") && !name.contains("audio")) out.add(name)
            }
            val sorted = out.sortedWith(compareBy<String> { it.contains("lite") || it.contains("preview") }.thenByDescending { it })
            cachedModels = sorted
            sorted
        } catch (_: Exception) { emptyList() } finally { conn?.disconnect() }
    }

    private fun candidates(apiKey: String, first: String, failed: Boolean): List<String> {
        val f = first.trim().removePrefix("models/")
        // لو الخانة فاضية أو مكتوب فيها اسم claude القديم: اختار تلقائي أحدث موديلات flash المتاحة
        if (f.isBlank() || f.startsWith("claude")) {
            val all = fallbackModels(apiKey)
            return if (!failed) all.take(3) else all.drop(3).take(3)
        }
        if (!failed) return listOf(f)
        return fallbackModels(apiKey).filter { it != f }.take(3)
    }

    fun ask(apiKey: String, model: String, prompt: String): String {
        var last = ""
        var list = candidates(apiKey, model, false)
        var i = 0
        var triedFallback = false
        while (i < list.size) {
            val m = list[i]
            var conn: HttpURLConnection? = null
            try {
                conn = open(apiKey, "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent", true)
                conn.outputStream.use { it.write(bodyOf(prompt)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) return textOf(JSONObject(text)).ifBlank { "(رد فارغ)" }
                last = "⚠ خطأ ($code): " + errMsg(text)
                if (!retryable(code)) return last
            } catch (e: Exception) {
                last = "⚠ تعذر الاتصال: " + (e.message ?: "")
            } finally { conn?.disconnect() }
            i++
            if (i >= list.size && !triedFallback) {
                triedFallback = true
                list = candidates(apiKey, model, true)
                i = 0
            }
        }
        return last.ifBlank { "⚠ مفيش موديل متاح دلوقتي" }
    }

    /** رد بالتدفق: كل قطعة نص بتوصل لـ onUpdate مع الرد المتراكم. */
    fun askStream(apiKey: String, model: String, prompt: String, onUpdate: (String) -> Unit) {
        var last = ""
        var list = candidates(apiKey, model, false)
        var i = 0
        var triedFallback = false
        while (i < list.size) {
            val m = list[i]
            var conn: HttpURLConnection? = null
            val acc = StringBuilder()
            try {
                conn = open(apiKey, "https://generativelanguage.googleapis.com/v1beta/models/$m:streamGenerateContent?alt=sse", true)
                conn.outputStream.use { it.write(bodyOf(prompt)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val text = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    last = "⚠ خطأ ($code): " + errMsg(text)
                    if (!retryable(code)) { onUpdate(last); return }
                } else {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                        while (true) {
                            val line = r.readLine() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data.isEmpty() || data == "[DONE]") continue
                            try {
                                val t = textOf(JSONObject(data))
                                if (t.isNotEmpty()) { acc.append(t); onUpdate(acc.toString()) }
                            } catch (_: Exception) {}
                        }
                    }
                    if (acc.isEmpty()) onUpdate("(رد فارغ)")
                    return
                }
            } catch (e: Exception) {
                if (acc.isNotEmpty()) { onUpdate(acc.toString() + "\n\n⚠ انقطع الاتصال"); return }
                last = "⚠ تعذر الاتصال: " + (e.message ?: "")
            } finally { conn?.disconnect() }
            i++
            if (i >= list.size && !triedFallback) {
                triedFallback = true
                list = candidates(apiKey, model, true)
                i = 0
            }
        }
        onUpdate(last.ifBlank { "⚠ مفيش موديل متاح دلوقتي" })
    }
}
