package com.matrixco.browser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(val id: Long, val url: String, val title: String, val ts: Long, val visits: Int)
data class BookmarkItem(val url: String, val title: String, val ts: Long)
data class DownloadItem(val id: Long, val dmId: Long, val name: String, val url: String, val mime: String, val ts: Long)
data class OfflineItem(val id: Long, val url: String, val title: String, val path: String, val size: Long, val ts: Long)
data class UserScript(val id: Long, val host: String, val name: String, val code: String, val type: String, val enabled: Boolean)
data class WatchItem(val id: Long, val url: String, val label: String, val hash: String, val ts: Long, val changed: Boolean)
data class SpyLogItem(val id: Long, val ts: Long, val host: String, val page: String, val type: String, val conf: Int, val decision: String)

/** هاش 64-بت للنطاق (FNV-1a) — يخلّي قاعدة الحجب أصغر بكتير من تخزين النصوص */
object Hash {
    fun of(s: String): Long {
        var h = -3750763034362895579L
        for (ch in s) {
            h = h xor ch.lowercaseChar().code.toLong()
            h *= 1099511628211L
        }
        return h
    }
}

/** تصنيفات الحجب */
object Cat { const val ADS = 1; const val TRK = 2; const val MAL = 3; const val SOC = 4; const val SPY = 5 }

class ShieldDatabase private constructor(private val ctx: Context) :
    SQLiteOpenHelper(ctx, "matrix_shield.db", null, 4) {

    private val lock = Any()

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE allowed_hosts (host TEXT PRIMARY KEY COLLATE NOCASE)")
        db.execSQL("CREATE TABLE shield_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO shield_meta(key, value) VALUES ('schema_version', '3')")
        createAll(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        createAll(db)
        if (oldVersion < 3) {
            try {
                db.execSQL("INSERT OR IGNORE INTO blocked_local(host, cat) SELECT host, 1 FROM blocked_hosts WHERE source <> 'remote'")
            } catch (_: Exception) {}
            db.execSQL("DROP TABLE IF EXISTS blocked_hosts")
            db.execSQL("INSERT OR REPLACE INTO shield_meta(key, value) VALUES ('remote_last_ms', '0')")
            db.execSQL("INSERT OR REPLACE INTO shield_meta(key, value) VALUES ('needs_vacuum', '1')")
            db.execSQL("INSERT OR REPLACE INTO shield_meta(key, value) VALUES ('schema_version', '3')")
        }
    }

    private fun createAll(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS fl_dom (h INTEGER NOT NULL, l INTEGER NOT NULL, c INTEGER NOT NULL, PRIMARY KEY(h, l)) WITHOUT ROWID")
        db.execSQL("CREATE TABLE IF NOT EXISTS fl_cosm (l INTEGER NOT NULL, h INTEGER NOT NULL, sel TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS fl_cosm_h ON fl_cosm(h)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bh (h INTEGER PRIMARY KEY, c INTEGER NOT NULL) WITHOUT ROWID")
        db.execSQL("CREATE TABLE IF NOT EXISTS blocked_local (host TEXT PRIMARY KEY COLLATE NOCASE, cat INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, title TEXT, ts INTEGER NOT NULL, visits INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_history_url ON history(url)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_ts ON history(ts)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (url TEXT PRIMARY KEY, title TEXT, ts INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS skip_sites (host TEXT PRIMARY KEY COLLATE NOCASE, mode INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS downloads (id INTEGER PRIMARY KEY AUTOINCREMENT, dm_id INTEGER, name TEXT, url TEXT, mime TEXT, ts INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS offline_pages (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, title TEXT, path TEXT NOT NULL, size INTEGER, ts INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS stats (day TEXT PRIMARY KEY, blocked INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS top_blocked (host TEXT PRIMARY KEY, cnt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS user_scripts (id INTEGER PRIMARY KEY AUTOINCREMENT, host TEXT NOT NULL, name TEXT, code TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'js', enabled INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE IF NOT EXISTS watches (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, label TEXT, hash TEXT, ts INTEGER, changed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS spy_log (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, host TEXT, page TEXT, type TEXT, conf INTEGER, decision TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS spy_rules (host TEXT NOT NULL COLLATE NOCASE, type TEXT NOT NULL, decision TEXT NOT NULL, PRIMARY KEY(host, type))")
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_hosts (host TEXT PRIMARY KEY COLLATE NOCASE, flags INTEGER NOT NULL)")
    }

    // ───────────── الحجب ─────────────

    /** يرجّع تصنيف الحجب (0 = غير محجوب) */
    fun blockedCategory(cands: List<String>): Int {
        if (cands.isEmpty()) return 0
        val ph = cands.joinToString(",") { "?" }
        readableDatabase.rawQuery("SELECT cat FROM blocked_local WHERE host IN ($ph) LIMIT 1", cands.toTypedArray()).use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        val hs = Array(cands.size) { Hash.of(cands[it]).toString() }
        readableDatabase.rawQuery("SELECT c FROM bh WHERE h IN ($ph) LIMIT 1", hs).use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    // ───────────── عوامل تصفية المحتوى ─────────────

    fun flReplace(l: Int, cat: Int, doms: LongArray, cosm: List<Pair<Long, String>>): Boolean = synchronized(lock) {
        val db = writableDatabase
        try {
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM fl_dom WHERE l = $l"); db.execSQL("DELETE FROM fl_cosm WHERE l = $l")
                val st = db.compileStatement("INSERT OR IGNORE INTO fl_dom(h, l, c) VALUES(?, ?, ?)")
                for (v in doms) { st.clearBindings(); st.bindLong(1, v); st.bindLong(2, l.toLong()); st.bindLong(3, cat.toLong()); st.executeInsert() }
                st.close()
                val st2 = db.compileStatement("INSERT INTO fl_cosm(l, h, sel) VALUES(?, ?, ?)")
                for ((h, sel) in cosm) { st2.clearBindings(); st2.bindLong(1, l.toLong()); st2.bindLong(2, h); st2.bindString(3, sel); st2.executeInsert() }
                st2.close()
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            true
        } catch (_: Exception) { false }
    }

    fun flDelete(l: Int) = synchronized(lock) {
        try { writableDatabase.execSQL("DELETE FROM fl_dom WHERE l = $l"); writableDatabase.execSQL("DELETE FROM fl_cosm WHERE l = $l") } catch (_: Exception) {}
        Unit
    }

    fun flHit(cands: List<String>): Int {
        if (cands.isEmpty()) return 0
        val ph = cands.joinToString(",") { "?" }
        val hs = Array(cands.size) { Hash.of(cands[it]).toString() }
        readableDatabase.rawQuery("SELECT c FROM fl_dom WHERE h IN ($ph) LIMIT 1", hs).use { if (it.moveToFirst()) return it.getInt(0) }
        return 0
    }

    // ───────────── النسخ الاحتياطي ─────────────

    fun dumpTable(table: String, cols: List<String>): org.json.JSONArray {
        val out = org.json.JSONArray()
        readableDatabase.rawQuery("SELECT " + cols.joinToString(",") + " FROM " + table, null).use { c ->
            while (c.moveToNext()) {
                val o = org.json.JSONObject()
                for ((i, k) in cols.withIndex()) when (c.getType(i)) {
                    android.database.Cursor.FIELD_TYPE_NULL -> {}
                    android.database.Cursor.FIELD_TYPE_INTEGER -> o.put(k, c.getLong(i))
                    else -> o.put(k, c.getString(i))
                }
                out.put(o)
            }
        }
        return out
    }

    fun restoreTable(table: String, cols: List<String>, rows: org.json.JSONArray): Int = synchronized(lock) {
        var n = 0
        val db = writableDatabase
        try {
            db.beginTransaction()
            try {
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    if (table == "user_scripts") {
                        val dup = db.rawQuery("SELECT 1 FROM user_scripts WHERE host = ? AND COALESCE(name,'') = ? AND code = ? LIMIT 1",
                            arrayOf(r.optString("host"), r.optString("name"), r.optString("code"))).use { it.moveToFirst() }
                        if (dup) continue
                    }
                    val cv = ContentValues()
                    for (k in cols) {
                        if (!r.has(k) || r.isNull(k)) continue
                        val v = r.get(k)
                        if (v is Number) cv.put(k, v.toLong()) else cv.put(k, v.toString())
                    }
                    if (db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L) n++
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (_: Exception) {}
        n
    }

    fun flAdd(l: Int, cat: Int, h: Long) = synchronized(lock) {
        try { writableDatabase.execSQL("INSERT OR IGNORE INTO fl_dom(h, l, c) VALUES(?, ?, ?)", arrayOf<Any>(h, l, cat)) } catch (_: Exception) {}
        Unit
    }

    fun flCount(l: Int): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM fl_dom WHERE l = $l", null).use { if (it.moveToFirst()) return it.getInt(0) }
        return 0
    }

    fun flHitList(cands: List<String>, l: Int): Boolean {
        if (cands.isEmpty()) return false
        val ph = cands.joinToString(",") { "?" }
        val args = Array(cands.size + 1) { if (it < cands.size) Hash.of(cands[it]).toString() else l.toString() }
        readableDatabase.rawQuery("SELECT 1 FROM fl_dom WHERE h IN ($ph) AND l = ? LIMIT 1", args).use { return it.moveToFirst() }
    }

    fun flCosmetic(hs: List<Long>, limit: Int): List<String> {
        if (hs.isEmpty()) return emptyList()
        val ph = hs.joinToString(",") { "?" }
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT DISTINCT sel FROM fl_cosm WHERE h IN ($ph) LIMIT $limit", Array(hs.size) { hs[it].toString() }).use { while (it.moveToNext()) out.add(it.getString(0)) }
        return out
    }

    fun blockedHostsCount(): Int =
        readableDatabase.rawQuery("SELECT (SELECT COUNT(*) FROM bh) + (SELECT COUNT(*) FROM blocked_local)", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun getAllowedHosts(): Set<String> {
        val result = linkedSetOf<String>()
        readableDatabase.rawQuery("SELECT host FROM allowed_hosts", null).use { while (it.moveToNext()) result.add(it.getString(0)) }
        return result
    }

    fun setAllowedHost(host: String, allowed: Boolean) = synchronized(lock) {
        if (allowed) {
            val v = ContentValues().apply { put("host", host.lowercase(Locale.ROOT)) }
            writableDatabase.insertWithOnConflict("allowed_hosts", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        } else writableDatabase.delete("allowed_hosts", "host = ?", arrayOf(host.lowercase(Locale.ROOT)))
        Unit
    }

    fun addLocalBlocked(host: String, cat: Int) = synchronized(lock) {
        val v = ContentValues().apply { put("host", host.lowercase(Locale.ROOT)); put("cat", cat) }
        writableDatabase.insertWithOnConflict("blocked_local", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    /** استبدال القوائم البعيدة (هاشات فقط) — سريع وخفيف */
    fun replaceRemote(sets: Map<Int, LongArray>): Boolean = synchronized(lock) {
        val total = sets.values.sumOf { it.size }
        if (total < 1000) return false
        val db = writableDatabase
        try {
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM bh")
                val st = db.compileStatement("INSERT OR IGNORE INTO bh(h, c) VALUES(?, ?)")
                for ((cat, arr) in sets.entries.sortedByDescending { it.key }) {
                    for (v in arr) { st.clearBindings(); st.bindLong(1, v); st.bindLong(2, cat.toLong()); st.executeInsert() }
                }
                st.close()
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            true
        } catch (_: Exception) { false }
    }

    /** VACUUM لتقليل حجم الملف بعد الحذف/الاستبدال */
    fun vacuum() = synchronized(lock) {
        try { writableDatabase.execSQL("VACUUM"); setMeta("needs_vacuum", "0") } catch (_: Exception) {}
    }

    fun dbSizeBytes(): Long {
        val f = ctx.getDatabasePath("matrix_shield.db")
        var s = if (f.exists()) f.length() else 0L
        val w = java.io.File(f.path + "-wal"); if (w.exists()) s += w.length()
        return s
    }

    // ───────────── Meta ─────────────

    fun getMeta(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM shield_meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    fun setMeta(key: String, value: String): Boolean = synchronized(lock) {
        val v = ContentValues().apply { put("key", key); put("value", value) }
        writableDatabase.insertWithOnConflict("shield_meta", null, v, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    fun addCounter(key: String, n: Int) { if (n > 0) setMeta(key, ((getMeta(key)?.toLongOrNull() ?: 0L) + n).toString()) }
    fun counter(key: String): Long = getMeta(key)?.toLongOrNull() ?: 0L

    // ───────────── السجل ─────────────

    fun addHistory(url: String, title: String) = synchronized(lock) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val st = db.compileStatement("UPDATE history SET title = ?, ts = ?, visits = visits + 1 WHERE url = ?")
        st.bindString(1, title); st.bindLong(2, now); st.bindString(3, url)
        val changed = st.executeUpdateDelete()
        st.close()
        if (changed == 0) {
            val v = ContentValues().apply { put("url", url); put("title", title); put("ts", now); put("visits", 1) }
            db.insert("history", null, v)
            db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY ts DESC LIMIT 1500)")
        }
    }

    fun getHistory(limit: Int): List<HistoryItem> {
        val out = ArrayList<HistoryItem>()
        readableDatabase.rawQuery("SELECT id, url, COALESCE(title,''), ts, visits FROM history ORDER BY ts DESC LIMIT $limit", null).use {
            while (it.moveToNext()) out.add(HistoryItem(it.getLong(0), it.getString(1), it.getString(2), it.getLong(3), it.getInt(4)))
        }
        return out
    }

    fun deleteHistory(id: Long) = synchronized(lock) { writableDatabase.delete("history", "id = ?", arrayOf(id.toString())); Unit }
    fun clearHistory() = synchronized(lock) { writableDatabase.delete("history", null, null); Unit }

    fun suggest(q: String, n: Int): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()
        val like = "%" + q.replace("%", "") + "%"
        readableDatabase.rawQuery(
            "SELECT url, COALESCE(title,'') FROM (SELECT url, title, ts+1000000000000 AS ts FROM bookmarks UNION ALL SELECT url, title, ts FROM history) WHERE url LIKE ? OR title LIKE ? ORDER BY ts DESC LIMIT 30",
            arrayOf(like, like)
        ).use { while (it.moveToNext() && out.size < n) out.putIfAbsent(it.getString(0), it.getString(1)) }
        return out.entries.map { it.key to it.value }
    }

    // ───────────── المفضلة ─────────────

    fun isBookmarked(url: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE url = ?", arrayOf(url)).use { it.moveToFirst() }

    fun addBookmark(url: String, title: String) = synchronized(lock) {
        val v = ContentValues().apply { put("url", url); put("title", title); put("ts", System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict("bookmarks", null, v, SQLiteDatabase.CONFLICT_REPLACE); Unit
    }

    fun removeBookmark(url: String) = synchronized(lock) { writableDatabase.delete("bookmarks", "url = ?", arrayOf(url)); Unit }

    fun getBookmarks(limit: Int): List<BookmarkItem> {
        val out = ArrayList<BookmarkItem>()
        readableDatabase.rawQuery("SELECT url, COALESCE(title,''), ts FROM bookmarks ORDER BY ts DESC LIMIT $limit", null).use {
            while (it.moveToNext()) out.add(BookmarkItem(it.getString(0), it.getString(1), it.getLong(2)))
        }
        return out
    }

    // ───────────── تخطي الانتظار ─────────────

    fun getSkipMode(host: String): Int? =
        readableDatabase.rawQuery("SELECT mode FROM skip_sites WHERE host = ?", arrayOf(host.lowercase(Locale.ROOT))).use { if (it.moveToFirst()) it.getInt(0) else null }

    fun setSkipMode(host: String, mode: Int?) = synchronized(lock) {
        val h = host.lowercase(Locale.ROOT)
        if (mode == null) writableDatabase.delete("skip_sites", "host = ?", arrayOf(h))
        else {
            val v = ContentValues().apply { put("host", h); put("mode", mode) }
            writableDatabase.insertWithOnConflict("skip_sites", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
        Unit
    }

    // ───────────── التحميلات ─────────────

    fun addDownload(dmId: Long, name: String, url: String, mime: String?) = synchronized(lock) {
        val v = ContentValues().apply { put("dm_id", dmId); put("name", name); put("url", url); put("mime", mime ?: ""); put("ts", System.currentTimeMillis()) }
        writableDatabase.insert("downloads", null, v); Unit
    }

    fun getDownloads(limit: Int): List<DownloadItem> {
        val out = ArrayList<DownloadItem>()
        readableDatabase.rawQuery("SELECT id, dm_id, COALESCE(name,''), COALESCE(url,''), COALESCE(mime,''), ts FROM downloads ORDER BY ts DESC LIMIT $limit", null).use {
            while (it.moveToNext()) out.add(DownloadItem(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getString(4), it.getLong(5)))
        }
        return out
    }

    fun getDownload(id: Long): DownloadItem? = getDownloads(500).firstOrNull { it.id == id }
    fun deleteDownload(id: Long) = synchronized(lock) { writableDatabase.delete("downloads", "id = ?", arrayOf(id.toString())); Unit }

    // ───────────── الأوفلاين ─────────────

    fun addOffline(url: String, title: String, path: String, size: Long) = synchronized(lock) {
        val v = ContentValues().apply { put("url", url); put("title", title); put("path", path); put("size", size); put("ts", System.currentTimeMillis()) }
        writableDatabase.insert("offline_pages", null, v); Unit
    }

    fun getOffline(): List<OfflineItem> {
        val out = ArrayList<OfflineItem>()
        readableDatabase.rawQuery("SELECT id, url, COALESCE(title,''), path, COALESCE(size,0), ts FROM offline_pages ORDER BY ts DESC", null).use {
            while (it.moveToNext()) out.add(OfflineItem(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getLong(4), it.getLong(5)))
        }
        return out
    }

    fun getOfflineById(id: Long): OfflineItem? = getOffline().firstOrNull { it.id == id }
    fun getOfflineByUrl(url: String): OfflineItem? = getOffline().firstOrNull { it.url == url }

    fun deleteOffline(id: Long): String? = synchronized(lock) {
        val item = getOfflineById(id)
        writableDatabase.delete("offline_pages", "id = ?", arrayOf(id.toString()))
        item?.path
    }

    // ───────────── الإحصائيات ─────────────

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun addBlocked(n: Int) = synchronized(lock) {
        if (n <= 0) return@synchronized
        val db = writableDatabase
        db.execSQL("INSERT OR IGNORE INTO stats(day, blocked, skipped) VALUES(?, 0, 0)", arrayOf(today()))
        db.execSQL("UPDATE stats SET blocked = blocked + ? WHERE day = ?", arrayOf(n, today()))
    }

    fun addSkipped() = synchronized(lock) {
        val db = writableDatabase
        db.execSQL("INSERT OR IGNORE INTO stats(day, blocked, skipped) VALUES(?, 0, 0)", arrayOf(today()))
        db.execSQL("UPDATE stats SET skipped = skipped + 1 WHERE day = ?", arrayOf(today()))
    }

    fun bumpTop(counts: Map<String, Int>) = synchronized(lock) {
        if (counts.isEmpty()) return@synchronized
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((h, c) in counts) {
                db.execSQL("INSERT OR IGNORE INTO top_blocked(host, cnt) VALUES(?, 0)", arrayOf(h))
                db.execSQL("UPDATE top_blocked SET cnt = cnt + ? WHERE host = ?", arrayOf(c, h))
            }
            db.execSQL("DELETE FROM top_blocked WHERE host NOT IN (SELECT host FROM top_blocked ORDER BY cnt DESC LIMIT 100)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun getTopBlocked(limit: Int): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        readableDatabase.rawQuery("SELECT host, cnt FROM top_blocked ORDER BY cnt DESC LIMIT $limit", null).use { while (it.moveToNext()) out.add(it.getString(0) to it.getInt(1)) }
        return out
    }

    fun todayBlocked(): Int = readableDatabase.rawQuery("SELECT blocked FROM stats WHERE day = ?", arrayOf(today())).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun totalBlocked(): Long = readableDatabase.rawQuery("SELECT COALESCE(SUM(blocked),0) FROM stats", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
    fun totalSkipped(): Long = readableDatabase.rawQuery("SELECT COALESCE(SUM(skipped),0) FROM stats", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    /** آخر 7 أيام (من الأقدم للأحدث) */
    fun week(): List<Triple<String, Int, Int>> {
        val out = ArrayList<Triple<String, Int, Int>>()
        readableDatabase.rawQuery("SELECT day, blocked, skipped FROM stats ORDER BY day DESC LIMIT 7", null).use {
            while (it.moveToNext()) out.add(Triple(it.getString(0), it.getInt(1), it.getInt(2)))
        }
        return out.reversed()
    }

    // ───────────── كاشف التجسس ─────────────

    fun addSpyLog(host: String, page: String, type: String, conf: Int, decision: String) = synchronized(lock) {
        val v = ContentValues().apply { put("ts", System.currentTimeMillis()); put("host", host); put("page", page); put("type", type); put("conf", conf); put("decision", decision) }
        val db = writableDatabase
        db.insert("spy_log", null, v)
        db.execSQL("DELETE FROM spy_log WHERE id NOT IN (SELECT id FROM spy_log ORDER BY id DESC LIMIT 300)")
    }

    fun getSpyLog(limit: Int): List<SpyLogItem> {
        val out = ArrayList<SpyLogItem>()
        readableDatabase.rawQuery("SELECT id, ts, COALESCE(host,''), COALESCE(page,''), COALESCE(type,''), COALESCE(conf,0), COALESCE(decision,'') FROM spy_log ORDER BY id DESC LIMIT $limit", null).use {
            while (it.moveToNext()) out.add(SpyLogItem(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5), it.getString(6)))
        }
        return out
    }

    fun clearSpyLog() = synchronized(lock) { writableDatabase.delete("spy_log", null, null); Unit }

    fun setSpyRule(host: String, type: String, decision: String?) = synchronized(lock) {
        if (decision == null) writableDatabase.delete("spy_rules", "host = ? AND type = ?", arrayOf(host, type))
        else {
            val v = ContentValues().apply { put("host", host.lowercase(Locale.ROOT)); put("type", type); put("decision", decision) }
            writableDatabase.insertWithOnConflict("spy_rules", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
        Unit
    }

    fun getSpyRule(host: String, type: String): String? =
        readableDatabase.rawQuery("SELECT decision FROM spy_rules WHERE host = ? AND type = ?", arrayOf(host.lowercase(Locale.ROOT), type)).use { if (it.moveToFirst()) it.getString(0) else null }

    fun blockedTypesFor(host: String): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT type FROM spy_rules WHERE host = ? AND decision = 'block'", arrayOf(host.lowercase(Locale.ROOT))).use { while (it.moveToNext()) out.add(it.getString(0)) }
        return out
    }

    // ───────────── وضع التوافق (راوترات/مواقع قديمة) ─────────────
    // flags: 1 = HTTP مسموح ، 2 = تجاوز أخطاء الشهادة ، 4 = محتوى مختلط مسموح

    fun getLegacy(host: String): Int =
        readableDatabase.rawQuery("SELECT flags FROM legacy_hosts WHERE host = ?", arrayOf(host.lowercase(Locale.ROOT))).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun setLegacy(host: String, flags: Int) = synchronized(lock) {
        val h = host.lowercase(Locale.ROOT)
        if (flags == 0) writableDatabase.delete("legacy_hosts", "host = ?", arrayOf(h))
        else {
            val v = ContentValues().apply { put("host", h); put("flags", flags) }
            writableDatabase.insertWithOnConflict("legacy_hosts", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
        Unit
    }

    // ───────────── سكريبتات المستخدم ─────────────

    fun addScript(host: String, name: String, code: String, type: String) = synchronized(lock) {
        val v = ContentValues().apply { put("host", host.lowercase(Locale.ROOT)); put("name", name); put("code", code); put("type", type); put("enabled", 1) }
        writableDatabase.insert("user_scripts", null, v); Unit
    }

    fun getScripts(): List<UserScript> {
        val out = ArrayList<UserScript>()
        readableDatabase.rawQuery("SELECT id, host, COALESCE(name,''), code, type, enabled FROM user_scripts ORDER BY id DESC", null).use {
            while (it.moveToNext()) out.add(UserScript(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5) == 1))
        }
        return out
    }

    fun scriptsFor(host: String): List<UserScript> = getScripts().filter {
        it.enabled && (it.host == "*" || host == it.host || host.endsWith("." + it.host))
    }

    fun setScriptEnabled(id: Long, on: Boolean) = synchronized(lock) {
        val v = ContentValues().apply { put("enabled", if (on) 1 else 0) }
        writableDatabase.update("user_scripts", v, "id = ?", arrayOf(id.toString())); Unit
    }

    fun deleteScript(id: Long) = synchronized(lock) { writableDatabase.delete("user_scripts", "id = ?", arrayOf(id.toString())); Unit }

    // ───────────── مراقبة الصفحات ─────────────

    fun addWatch(url: String, label: String, hash: String) = synchronized(lock) {
        val v = ContentValues().apply { put("url", url); put("label", label); put("hash", hash); put("ts", System.currentTimeMillis()); put("changed", 0) }
        writableDatabase.insert("watches", null, v); Unit
    }

    fun getWatches(): List<WatchItem> {
        val out = ArrayList<WatchItem>()
        readableDatabase.rawQuery("SELECT id, url, COALESCE(label,''), COALESCE(hash,''), COALESCE(ts,0), changed FROM watches ORDER BY id DESC", null).use {
            while (it.moveToNext()) out.add(WatchItem(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getLong(4), it.getInt(5) == 1))
        }
        return out
    }

    fun updateWatch(id: Long, hash: String, changed: Boolean) = synchronized(lock) {
        val v = ContentValues().apply { put("hash", hash); put("ts", System.currentTimeMillis()); put("changed", if (changed) 1 else 0) }
        writableDatabase.update("watches", v, "id = ?", arrayOf(id.toString())); Unit
    }

    fun deleteWatch(id: Long) = synchronized(lock) { writableDatabase.delete("watches", "id = ?", arrayOf(id.toString())); Unit }

    companion object {
        @Volatile private var instance: ShieldDatabase? = null
        fun getInstance(context: Context): ShieldDatabase =
            instance ?: synchronized(this) { instance ?: ShieldDatabase(context.applicationContext).also { instance = it } }
    }
}
