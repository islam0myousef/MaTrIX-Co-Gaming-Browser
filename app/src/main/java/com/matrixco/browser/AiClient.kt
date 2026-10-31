package com.matrixco.browser

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** اتصال مباشر بـ Claude API (بمفتاحك أنت) */
object AiClient {
    fun ask(apiKey: String, model: String, prompt: String): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 1200)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = try { JSONObject(text).getJSONObject("error").getString("message") } catch (_: Exception) { text.take(200) }
                return "⚠ خطأ ($code): $msg"
            }
            val arr = JSONObject(text).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("type") == "text") sb.append(o.getString("text"))
            }
            sb.toString().ifBlank { "(رد فارغ)" }
        } catch (e: Exception) {
            "⚠ تعذر الاتصال: " + (e.message ?: "")
        } finally { conn?.disconnect() }
    }
}
