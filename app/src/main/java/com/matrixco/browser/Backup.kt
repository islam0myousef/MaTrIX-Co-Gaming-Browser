package com.matrixco.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * نسخة احتياطية كاملة في ملف JSON: الإعدادات + المفضلة + قوائم السماح/التخطي + قواعد التجسس + السكريبتات
 * + الفلاتر المخصصة والكلمات المحجوبة (لأنها محفوظة في الإعدادات).
 * مش بتشمل: PIN القفل، مفتاح الـ AI، السجل، قوائم الحجب المحمّلة (بتتحمّل تاني بعد الاسترجاع).
 */
object Backup {
    private val SKIP_KEYS = setOf("api_key")
    private fun skip(k: String) = k.startsWith("pin_") || k in SKIP_KEYS

    private val TABLES: List<Pair<String, List<String>>> = listOf(
        "bookmarks" to listOf("url", "title", "ts"),
        "allowed_hosts" to listOf("host"),
        "skip_sites" to listOf("host", "mode"),
        "blocked_local" to listOf("host", "cat"),
        "spy_rules" to listOf("host", "type", "decision"),
        "user_scripts" to listOf("host", "name", "code", "type", "enabled")
    )

    fun export(ctx: Context): String {
        val o = JSONObject().put("app", "MaTrIXCo").put("v", 2)
        val p = JSONObject()
        for ((k, v) in Cfg.prefs.all) {
            if (skip(k) || v == null) continue
            val t = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is String -> "s"; else -> continue }
            p.put(k, JSONObject().put("t", t).put("v", v))
        }
        o.put("prefs", p)
        val db = ShieldDatabase.getInstance(ctx)
        val t = JSONObject()
        for ((name, cols) in TABLES) t.put(name, db.dumpTable(name, cols))
        o.put("tables", t)
        return o.toString()
    }

    /** بيرجّع عدد العناصر اللي اتسترجعت، أو بيرمي استثناء لو الملف مش نسخة صالحة */
    fun import(ctx: Context, text: String): Int {
        val o = JSONObject(text.trim())
        val db = ShieldDatabase.getInstance(ctx)
        var n = 0
        if (o.optInt("v", 1) >= 2) {
            if (o.optString("app") != "MaTrIXCo") throw IllegalArgumentException("not a backup")
            val p = o.optJSONObject("prefs")
            val e = Cfg.prefs.edit()
            if (p != null) for (k in p.keys()) {
                if (skip(k)) continue
                val x = p.optJSONObject(k) ?: continue
                try {
                    when (x.getString("t")) {
                        "b" -> e.putBoolean(k, x.getBoolean("v"))
                        "i" -> e.putInt(k, x.getInt("v"))
                        "l" -> e.putLong(k, x.getLong("v"))
                        "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                        "s" -> e.putString(k, x.getString("v"))
                        else -> continue
                    }
                    n++
                } catch (_: Exception) {}
            }
            e.apply()
            val t = o.optJSONObject("tables")
            if (t != null) for ((name, cols) in TABLES) t.optJSONArray(name)?.let { n += db.restoreTable(name, cols, it) }
        } else {
            // الصيغة القديمة (نص بيتلصق)
            o.optJSONObject("prefs")?.let { p ->
                val e = Cfg.prefs.edit()
                for (k in p.keys()) if (!skip(k)) when (val v = p.get(k)) { is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v); is String -> e.putString(k, v) }
                e.apply()
            }
            o.optString("shortcuts").takeIf { it.isNotEmpty() }?.let { Cfg.setStr("shortcuts", it) }
            o.optString("games").takeIf { it.isNotEmpty() }?.let { Cfg.setStr("games", it) }
            o.optJSONArray("bookmarks")?.let { a -> for (i in 0 until a.length()) { val b = a.getJSONObject(i); db.addBookmark(b.getString("u"), b.optString("t")); n++ } }
            o.optJSONArray("scripts")?.let { a -> for (i in 0 until a.length()) { val s = a.getJSONObject(i); db.addScript(s.getString("h"), s.optString("n"), s.getString("c"), s.optString("t", "js")); n++ } }
        }
        return n
    }
}
