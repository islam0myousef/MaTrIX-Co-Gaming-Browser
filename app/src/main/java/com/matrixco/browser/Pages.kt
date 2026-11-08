package com.matrixco.browser

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** حجم مساحة التطبيق + التنظيف */
object Storage {
    data class Info(val cache: Long, val web: Long, val offline: Long, val db: Long) { val total get() = cache + web + offline + db }

    @Volatile private var cachedInfo: Info? = null
    @Volatile private var lastComputedAt = 0L
    private val computeLock = Any()

    fun dirSize(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        var s = 0L
        f.listFiles()?.forEach { s += dirSize(it) }
        return s
    }

    fun del(f: File) {
        if (!f.exists()) return
        if (f.isDirectory) f.listFiles()?.forEach { del(it) }
        f.delete()
    }

    private fun webDir(ctx: Context) = File(ctx.applicationInfo.dataDir, "app_webview")

    fun compute(ctx: Context): Info {
        val now = System.currentTimeMillis()
        cachedInfo?.let { if (now - lastComputedAt < 15_000L) return it }
        synchronized(computeLock) {
            val checkAt = System.currentTimeMillis()
            cachedInfo?.let { if (checkAt - lastComputedAt < 15_000L) return it }
            val w = webDir(ctx)
            val cache = dirSize(ctx.cacheDir) + dirSize(File(w, "Default/HTTP Cache")) + dirSize(File(w, "Default/Code Cache")) +
                dirSize(File(w, "Default/GPUCache")) + dirSize(File(w, "Default/Service Worker/CacheStorage"))
            val web = (dirSize(w) - (cache - dirSize(ctx.cacheDir))).coerceAtLeast(0L)
            val result = Info(cache, web, dirSize(File(ctx.filesDir, "offline")), ShieldDatabase.getInstance(ctx).dbSizeBytes())
            cachedInfo = result
            lastComputedAt = checkAt
            return result
        }
    }

    fun invalidate() { synchronized(computeLock) { cachedInfo = null; lastComputedAt = 0L } }

    fun cleanCache(ctx: Context) {
        val w = webDir(ctx)
        del(ctx.cacheDir)
        for (p in listOf("Default/HTTP Cache", "Default/Code Cache", "Default/GPUCache", "Default/Service Worker/CacheStorage")) del(File(w, p))
        invalidate()
    }

    fun fmt(b: Long): String = when {
        b >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
        b >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
        b >= 1024 -> (b / 1024).toString() + " KB"
        else -> "$b B"
    }
}

/** صفحات المتصفح الداخلية (https://mx.home/...) — تصميم هادئ بأسلوب Chrome/Brave */
object Pages {
    const val HOST = "mx.home"
    const val HOME_URL = "https://mx.home/"

    private var logoUri: String? = null
    private fun logo(ctx: Context): String {
        logoUri?.let { return it }
        val s = try { "data:image/png;base64," + Base64.encodeToString(ctx.assets.open("logo_s.png").readBytes(), Base64.NO_WRAP) } catch (_: Exception) { "" }
        logoUri = s
        return s
    }

    data class Entry(val name: String, val url: String)

    fun readEntries(key: String, def: List<Entry>): List<Entry> {
        val s = Cfg.str(key, "")
        if (s.isEmpty()) return def
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { val o = a.getJSONObject(it); Entry(o.getString("n"), o.getString("u")) }
        } catch (_: Exception) { def }
    }

    fun writeEntries(key: String, list: List<Entry>) {
        val a = JSONArray()
        for (e in list) a.put(JSONObject().put("n", e.name).put("u", e.url))
        Cfg.setStr(key, a.toString())
    }

    val defaultShortcuts = listOf(
        Entry("Conquer", "https://browser.playconqueronline.com/"),
        Entry("YouTube", "https://m.youtube.com/"),
        Entry("Facebook", "https://m.facebook.com/"),
        Entry("Google", "https://www.google.com/"),
        Entry("Discord", "https://discord.com/app"),
        Entry("Twitch", "https://m.twitch.tv/"),
        Entry("Reddit", "https://www.reddit.com/")
    )
    val defaultGames = listOf(Entry("Conquer Online", "https://browser.playconqueronline.com/"))

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private fun fmtTime(ts: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(ts))
    private fun hostOf(u: String): String = try { Uri.parse(u).host.orEmpty() } catch (_: Exception) { "" }

    private fun css(t: MxTheme): String = """
:root{--bg:${t.bg.hex()};--sf:${t.surface.hex()};--sf2:${t.surface2.hex()};--ac:${t.accent.hex()};--ac2:${t.accent2.hex()};--tx:${t.text.hex()};--sb:${t.sub.hex()};--line:rgba(255,255,255,.09);--glass:rgba(18,21,32,.82);--lime:#d2ff4e;--purple:#975cff}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html{background:var(--bg);scroll-behavior:smooth}
body{margin:0 auto;color:var(--tx);font-family:system-ui,Roboto,sans-serif;font-size:14px;padding:18px 16px 48px;max-width:820px;background:var(--bg);min-height:100vh;overflow-x:hidden}
a{color:inherit;text-decoration:none}
button,input{font:inherit}
.top{display:flex;align-items:center;justify-content:space-between;margin:0 0 26px;position:relative;z-index:2}
.brand{display:flex;align-items:center;gap:10px;font-weight:900;font-size:15px;letter-spacing:1.5px;color:var(--tx)}
.brand img{width:34px;height:34px;border-radius:11px;box-shadow:0 0 24px rgba(151,92,255,.25)}
.brand small{display:block;font-size:9px;letter-spacing:2.4px;color:var(--sb);font-weight:700;margin-top:1px}
.m{width:42px;height:42px;border:1px solid var(--line);border-radius:14px;display:flex;align-items:center;justify-content:center;color:var(--tx);font-size:21px;background:var(--glass);backdrop-filter:blur(16px)}
.hero{position:relative;padding:28px 20px 22px;border:1px solid rgba(151,92,255,.24);border-radius:28px;background:radial-gradient(ellipse at 92% 0%,rgba(151,92,255,.24),transparent 48%),radial-gradient(ellipse at 4% 100%,rgba(210,255,78,.09),transparent 42%),linear-gradient(145deg,#171a29,#10121c 72%);overflow:hidden;margin-bottom:18px;box-shadow:0 18px 48px rgba(0,0,0,.24)}
.hero:after{content:"";position:absolute;width:190px;height:190px;border:1px solid rgba(210,255,78,.16);border-radius:50%;right:-92px;top:-110px;box-shadow:0 0 0 22px rgba(210,255,78,.025),0 0 0 48px rgba(151,92,255,.035)}
.eyebrow{font-size:10px;letter-spacing:2.5px;font-weight:900;color:var(--lime);text-transform:uppercase;display:flex;align-items:center;gap:8px}
.live{width:7px;height:7px;border-radius:50%;background:var(--lime);box-shadow:0 0 12px var(--lime)}
.hero-title{font-size:35px;line-height:1.12;letter-spacing:-1.4px;font-weight:950;margin:14px 0 7px;position:relative;z-index:1;color:#fff}.hero-title b{color:var(--lime);font-weight:950}
.wm b{color:var(--lime);font-weight:950}
.tagline{font-size:12px;color:#a7adc1;margin:0 0 20px;position:relative;z-index:1}
.search{display:flex;align-items:center;gap:8px;background:#090b12;border:1px solid rgba(255,255,255,.12);border-radius:17px;padding:5px 6px 5px 14px;margin:0;position:relative;z-index:1;box-shadow:0 8px 24px rgba(0,0,0,.25)}
.search input{flex:1;background:transparent;border:0;outline:0;color:#fff;font-size:14px;padding:13px 2px;min-width:0}
.search input::placeholder{color:#777f96}
.search .b{width:42px;height:42px;border-radius:13px;display:flex;align-items:center;justify-content:center;color:#111;background:var(--lime);border:0;font-size:17px;font-weight:900}
.section{display:flex;justify-content:space-between;align-items:center;margin:24px 2px 12px}
.section .heading{display:flex;align-items:center;gap:9px;font-size:12px;letter-spacing:1.3px;font-weight:900}
.section .bar{height:17px;width:3px;border-radius:4px;background:var(--lime);box-shadow:0 0 10px rgba(210,255,78,.4)}
.section a{color:var(--lime);font-size:11px;font-weight:800}
.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}
.tile{display:flex;flex-direction:column;align-items:center;gap:8px;font-size:11px;color:#c8cce0;position:relative;text-align:center;min-width:0;padding:12px 4px;border:1px solid var(--line);border-radius:18px;background:linear-gradient(145deg,rgba(255,255,255,.045),rgba(255,255,255,.015));min-height:100px;justify-content:center}
.ic{width:48px;height:48px;border-radius:16px;background:linear-gradient(145deg,#282b40,#171a28);border:1px solid rgba(255,255,255,.08);display:flex;align-items:center;justify-content:center;font-size:18px;font-weight:900;color:var(--lime);overflow:hidden;box-shadow:0 8px 18px rgba(0,0,0,.18)}
.ic img{width:27px;height:27px;border-radius:8px;object-fit:contain}
.tile .x{position:absolute;top:5px;right:7px;width:18px;height:18px;border-radius:6px;background:#402033;color:#ff8ba6;font-size:10px;display:flex;align-items:center;justify-content:center}
.strip{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px;margin:18px 0}
.strip a{min-width:0;background:linear-gradient(145deg,#1b2030,#131722);border:1px solid var(--line);border-radius:17px;padding:13px 8px;text-align:center}
.strip b{display:block;font-size:18px;font-weight:900;margin-bottom:4px;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.strip span{font-size:9px;color:#a6aec4;display:block;line-height:1.4}
.games{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.game{display:flex;align-items:center;gap:11px;min-width:0;background:linear-gradient(145deg,#202438,#151927);border:1px solid rgba(151,92,255,.18);border-radius:19px;padding:13px;position:relative;overflow:hidden}
.game:after{content:"";position:absolute;right:-24px;bottom:-38px;width:88px;height:88px;border-radius:50%;background:rgba(151,92,255,.09)}
.game .ic{width:42px;height:42px;flex:none;border-radius:13px}
.game .ic img{width:25px;height:25px}
.game b{display:block;font-size:12px;font-weight:850;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.game small{color:#969fb6;font-size:9px;direction:ltr;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:4px}
.game .launch{margin-inline-start:auto;color:var(--lime);font-size:17px;z-index:1}
.control-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}
.control{display:flex;align-items:center;gap:10px;padding:13px;background:#171b29;border:1px solid var(--line);border-radius:16px;min-width:0}
.control .symbol{width:34px;height:34px;display:grid;place-items:center;border-radius:11px;background:rgba(151,92,255,.16);color:#c4a5ff;font-size:16px}
.control b{font-size:11px;display:block}.control small{display:block;font-size:9px;color:#929bb2;margin-top:3px}
.gx{display:flex;gap:7px;margin:5px 0 13px;overflow-x:auto;scrollbar-width:none}.gx::-webkit-scrollbar{display:none}
.gx button{flex:none;background:#1b2030;color:#aeb5ca;border:1px solid var(--line);padding:10px 16px;font-weight:800;font-size:11px;border-radius:12px;font-family:inherit}
.gx button.on{background:var(--lime);border-color:var(--lime);color:#11151c}
.gxlist{display:none}.gxlist.on{display:block}
.gxc{display:flex;gap:11px;align-items:center;background:linear-gradient(145deg,#1b2030,#131722);border:1px solid var(--line);padding:11px;margin-bottom:9px;border-radius:16px}
.gxc img{width:72px;height:58px;object-fit:cover;border-radius:11px;flex:none;background:var(--sf2)}
.gxc b{font-size:12px;display:block;font-weight:800;line-height:1.45}.gxc small{color:#929bb2;font-size:10px}
.item{display:flex;align-items:center;gap:12px;padding:11px 12px;border:1px solid var(--line);border-radius:15px;background:#151a27;margin-bottom:8px}
.item .ic{width:36px;height:36px;flex:none;font-size:14px;border-radius:11px}.item .ic img{width:22px;height:22px}.item .t{flex:1;min-width:0}.item .t b{display:block;font-size:12px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.item .t span{font-size:10px;color:#929bb2;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;display:block;direction:ltr;text-align:right}.x2{color:#ff8ba6;padding:6px 8px;font-size:15px}
.empty{text-align:center;color:#939bb0;padding:30px 10px}
.row{display:flex;justify-content:space-between;padding:11px 4px;border-bottom:1px solid var(--sf2);font-size:13px}.row span:first-child{color:var(--sb)}
.ptop{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.ptop h2{margin:0;font-size:17px;font-weight:800}
.btn{display:inline-block;background:var(--ac);color:#fff;border-radius:12px;padding:9px 18px;font-size:13px;margin:4px 3px}.btn.alt{background:var(--sf2);color:var(--tx)}
.card{background:var(--sf);border:1px solid var(--line);border-radius:17px;padding:14px;margin-bottom:12px}.card h4{margin:0 0 8px;font-size:12px;color:var(--sb);font-weight:700}
.security-hero{padding:18px;border-radius:22px;background:radial-gradient(ellipse at 95% 0%,rgba(210,255,78,.13),transparent 45%),linear-gradient(145deg,#1b2530,#121722);border:1px solid rgba(210,255,78,.2);margin-bottom:12px}.security-hero .state{font-size:10px;letter-spacing:1.8px;font-weight:900;color:var(--lime)}.security-hero h2{font-size:20px;margin:9px 0 5px}.security-hero p{font-size:11px;line-height:1.7;color:#a9b2c7;margin:0}.status-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:12px 0}.status-card{padding:12px;border:1px solid var(--line);border-radius:15px;background:#171c29;min-width:0}.status-card b{display:block;font-size:12px;margin-bottom:5px}.status-card span{font-size:10px;color:#9ca6bd;line-height:1.5}.status-dot{display:inline-block;width:7px;height:7px;border-radius:50%;margin-left:5px;background:var(--lime);box-shadow:0 0 8px rgba(210,255,78,.45)}.status-dot.off{background:#ff647c;box-shadow:0 0 8px rgba(255,100,124,.35)}
.bars{display:flex;align-items:flex-end;justify-content:space-between;gap:5px;height:80px}.bars div{flex:1;background:var(--ac);border-radius:4px 4px 1px 1px;min-height:3px;opacity:.85}
.chip{display:inline-block;padding:3px 9px;border-radius:10px;font-size:10px;font-weight:800;color:#10131b;background:var(--lime)}.small{font-size:11px;color:var(--sb)}
.clockcard{position:relative;padding:20px 18px 16px;border:1px solid rgba(210,255,78,.16);border-radius:24px;background:radial-gradient(ellipse at 88% 0%,rgba(151,92,255,.2),transparent 48%),radial-gradient(ellipse at 6% 100%,rgba(210,255,78,.07),transparent 40%),linear-gradient(150deg,#141729,#0b0d17 75%);margin-bottom:18px;overflow:hidden;box-shadow:0 14px 36px rgba(0,0,0,.22)}
.clockcard .cdate{font-size:11px;color:#9aa2b8;letter-spacing:.6px;text-align:center;margin-bottom:3px;font-weight:600}
.clockcard .ctime{font-size:48px;font-weight:950;text-align:center;letter-spacing:-1.5px;color:#fff;line-height:1.05;text-shadow:0 0 30px rgba(210,255,78,.32);direction:ltr}
.prow{display:flex;justify-content:space-between;gap:6px;margin-top:16px}
.prow .p{flex:1;text-align:center;padding:9px 2px;border-radius:14px;border:1px solid var(--line);background:rgba(255,255,255,.035);min-width:0;transition:background .3s}
.prow .p span{display:block;font-size:9px;color:#9aa2b8;margin-bottom:4px;font-weight:700}
.prow .p b{display:block;font-size:11px;font-weight:800;color:#fff;direction:ltr}
.prow .p.on{background:var(--lime);border-color:var(--lime);box-shadow:0 0 14px rgba(210,255,78,.35)}
.prow .p.on span,.prow .p.on b{color:#11151c}
canvas#bg,.aur,.wimg{position:fixed;left:0;top:0;width:100%;height:100%;z-index:-1}.aur{background:linear-gradient(135deg,var(--bg),#17152b,var(--bg));background-size:300% 300%;animation:ag 18s ease infinite}@keyframes ag{0%{background-position:0 50%}50%{background-position:100% 50%}100%{background-position:0 50%}}
.brand b{letter-spacing:1px}.wm,.mt{display:none}
body.wall .hero{background:radial-gradient(ellipse at 90% 0%,rgba(151,92,255,.26),transparent 50%),linear-gradient(145deg,#171a29,#10121c)}
body.opera .wimg.op{background:radial-gradient(ellipse at 85% 0%,rgba(151,92,255,.25),transparent 40%),radial-gradient(ellipse at 5% 95%,rgba(210,255,78,.08),transparent 35%),#0b0d15!important}
body.opera .wm{display:none}
body.opera .top{margin-bottom:22px}
@media(min-width:560px){body{padding:26px 28px 60px}.hero{padding:38px 32px 28px}.hero-title{font-size:46px}.grid{grid-template-columns:repeat(7,minmax(0,1fr))}.games{grid-template-columns:repeat(3,minmax(0,1fr))}.strip a{padding:17px}.strip b{font-size:22px}}
@media(max-width:360px){body{padding:14px 12px 40px}.hero{padding:23px 15px 17px}.grid{grid-template-columns:repeat(3,minmax(0,1fr))}.hero-title{font-size:30px}.strip{gap:6px}.strip a{padding:11px 5px}.strip b{font-size:15px}}
@media(prefers-reduced-motion:reduce){*,*:before,*:after{scroll-behavior:auto!important;animation:none!important;transition:none!important}}
"""

    private fun page(t: MxTheme, title: String, body: String, bodyClass: String = ""): String =
        "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + esc(title) + "</title><style>" + css(t) +
            ":root{--acx:" + rgbaStr(t.accent, ".22") + ";--ac2x:" + rgbaStr(t.accent, ".10") + "}</style></head><body class=\"" + bodyClass + "\">" + body + "</body></html>"


    private fun wallpaper(ctx: Context, t: MxTheme): String {
        val kind = Cfg.str(Cfg.WALL, "gx")
        if (kind == "none") return ""
        val acc = rgbaStr(t.accent, ".55"); val accSoft = rgbaStr(t.accent, ".18"); val accA = rgbaStr(t.accent, ".8"); val bg = t.bg.hex()
        return when (kind) {
            "gx" -> "<div class=\"wimg op\" style=\"background:radial-gradient(ellipse at 82% 5%,rgba(151,92,255,.20),transparent 38%),radial-gradient(ellipse at 8% 92%,rgba(210,255,78,.08),transparent 34%),#0b0d15\"></div>" +
                "<svg class=\"mt\" viewBox=\"0 0 400 160\" preserveAspectRatio=\"none\"><path d=\"M0 160 L0 105 L70 70 L125 115 L205 48 L270 112 L340 66 L400 100 L400 160Z\" fill=\"#211b3b\" opacity=\".75\"/><path d=\"M0 160 L0 132 L90 104 L160 138 L250 98 L330 138 L400 112 L400 160Z\" fill=\"#141a24\"/></svg>"
            "opera" -> "<div class=\"wimg op\" style=\"background:linear-gradient(180deg,#4c6c96 0%,#7d7fb0 30%,#c98a95 58%,#f1a67c 78%,#3a3050 100%)\"></div>" +
                "<svg class=\"mt\" viewBox=\"0 0 400 160\" preserveAspectRatio=\"none\"><path d=\"M0 160 L0 95 L60 60 L110 100 L170 40 L240 105 L300 70 L360 110 L400 85 L400 160Z\" fill=\"#3b3560\" opacity=\".85\"/><path d=\"M0 160 L0 125 L80 95 L150 130 L230 90 L320 135 L400 110 L400 160Z\" fill=\"#2b2748\"/></svg>"
            "aurora" -> "<div class=\"aur\"></div>"
            "custom" -> {
                val f = File(ctx.filesDir, "wall.jpg")
                if (!f.exists()) "" else "<div class=\"wimg\" style=\"background:linear-gradient(rgba(0,0,0,.5),rgba(0,0,0,.6)),url(data:image/jpeg;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) + ") center/cover\"></div>"
            }
            "grid" -> "<canvas id=\"bg\"></canvas><script>(function(){var c=document.getElementById('bg'),x=c.getContext('2d'),W,H,t=0;function rs(){W=c.width=innerWidth;H=c.height=innerHeight}rs();addEventListener('resize',rs);" +
                "function d(){x.clearRect(0,0,W,H);var g=x.createLinearGradient(0,0,0,H);g.addColorStop(0,'rgba(0,0,0,0)');g.addColorStop(1,'" + accSoft + "');x.fillStyle=g;x.fillRect(0,0,W,H);x.strokeStyle='" + acc + "';x.lineWidth=1;var hz=H*0.5;" +
                "for(var i=-12;i<=12;i++){x.beginPath();x.moveTo(W/2+i*18,hz);x.lineTo(W/2+i*W/5,H);x.stroke();}" +
                "for(var j=0;j<10;j++){var y=hz+Math.pow((j+(t%1))/10,2.2)*(H-hz);x.beginPath();x.moveTo(0,y);x.lineTo(W,y);x.stroke();}" +
                "t+=0.012;setTimeout(function(){requestAnimationFrame(d)},document.hidden?600:33)}d()})();</script>"
            "embers" -> "<canvas id=\"bg\"></canvas><script>(function(){var c=document.getElementById('bg'),x=c.getContext('2d'),W,H,P=[];function rs(){W=c.width=innerWidth;H=c.height=innerHeight}rs();addEventListener('resize',rs);" +
                "for(var i=0;i<55;i++)P.push({x:Math.random()*innerWidth,y:Math.random()*innerHeight,r:Math.random()*2+.6,s:Math.random()*.9+.3});" +
                "function d(){x.clearRect(0,0,W,H);x.fillStyle='" + accA + "';for(var i=0;i<P.length;i++){var p=P[i];p.y-=p.s;p.x+=Math.sin(p.y/30)*.4;if(p.y<0){p.y=H;p.x=Math.random()*W}x.beginPath();x.arc(p.x,p.y,p.r,0,6.3);x.fill()}" +
                "setTimeout(function(){requestAnimationFrame(d)},document.hidden?600:33)}d()})();</script>"
            "matrix" -> "<canvas id=\"bg\"></canvas><script>(function(){var c=document.getElementById('bg'),x=c.getContext('2d'),W,H,D=[];function rs(){W=c.width=innerWidth;H=c.height=innerHeight;D=[];for(var i=0;i<W/16;i++)D.push(Math.random()*H/16)}rs();addEventListener('resize',rs);" +
                "var S='01アイウエオカキクケコサシスセソタチツテト<>/=+*';function d(){x.fillStyle='" + bg + "33';x.fillRect(0,0,W,H);x.fillStyle='" + acc + "';x.font='14px monospace';" +
                "for(var i=0;i<D.length;i++){x.fillText(S.charAt(Math.random()*S.length|0),i*16,D[i]*16);if(D[i]*16>H&&Math.random()>.97)D[i]=0;D[i]+=.5}" +
                "setTimeout(function(){requestAnimationFrame(d)},document.hidden?600:50)}d()})();</script>"
            else -> ""
        }
    }

    /** أيقونة موقع: صورة حقيقية لو موجودة وإلا أول حرف */
    private fun icon(ctx: Context, url: String, name: String): String {
        val u = Favicons.dataUri(ctx, hostOf(url))
        return if (u != null) "<img src=\"$u\">" else esc(name.trim().take(1).uppercase())
    }

    private fun listBody(title: String, items: List<String>, emptyText: String, extraTop: String = ""): String =
        "<div class=\"ptop\"><h2>" + esc(title) + "</h2>" + extraTop + "</div>" +
            (if (items.isEmpty()) "<div class=\"empty\">" + esc(emptyText) + "</div>" else items.joinToString(""))

    private fun item(ctx: Context, title: String, sub: String, href: String, delHref: String?, iconUrl: String = sub): String =
        "<div class=\"item\"><div class=\"ic\">" + icon(ctx, iconUrl, title.ifBlank { sub }) + "</div><a class=\"t\" href=\"" + esc(href) + "\"><b>" + esc(title.ifBlank { sub }) + "</b><span>" + esc(sub) + "</span></a>" +
            (if (delHref != null) "<a class=\"x2\" href=\"" + esc(delHref) + "\">✕</a>" else "") + "</div>"

    fun respond(ctx: Context, db: ShieldDatabase, t: MxTheme, uri: Uri): WebResourceResponse {
        val path = uri.path ?: "/"
        val html = try {
            when (path) {
                "", "/" -> home(ctx, db, t)
                "/history" -> history(ctx, db, t)
                "/bookmarks" -> bookmarks(ctx, db, t)
                "/offline" -> offline(ctx, db, t)
                "/downloads" -> downloads(ctx, db, t)
                "/shield" -> shield(ctx, db, t)
                "/spy" -> spyLog(ctx, db, t)
                "/storage" -> storage(ctx, t)
                "/error" -> error(ctx, db, t, uri.getQueryParameter("u").orEmpty(), uri.getQueryParameter("m").orEmpty())
                "/warn" -> warn(t, uri.getQueryParameter("u").orEmpty())
                else -> null
            }
        } catch (e: Exception) {
            page(t, "خطأ", "<div class=\"empty\">تعذر تحميل الصفحة: " + esc(e.message ?: "") + "</div>")
        }
        if (html == null) return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
        val r = WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
        r.responseHeaders = mapOf("Cache-Control" to "no-store")
        return r
    }

    private fun home(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val sb = StringBuilder()
        val wall = wallpaper(ctx, t)
        sb.append(wall)
        val sc = readEntries("shortcuts", defaultShortcuts)
        val games = readEntries("games", defaultGames)
        val quick = db.getBookmarks(5)
        Favicons.ensure(ctx, (sc.map { hostOf(it.url) } + games.map { hostOf(it.url) } + quick.map { hostOf(it.url) }).filter { it.isNotEmpty() })

        sb.append("<div class=\"top\"><div class=\"brand\"><img src=\"").append(logo(ctx))
            .append("\"><div><b>MaTrIX Co</b><small>GAMING BROWSER</small></div></div><a class=\"m\" href=\"mx://tools\" aria-label=\"القائمة\">☰</a></div>")
        sb.append("<section class=\"hero\"><div class=\"eyebrow\"><i class=\"live\"></i> GX BROWSER • READY</div>")
        sb.append("<div class=\"hero-title\">PLAY <b>WITHOUT LIMITS</b></div><p class=\"tagline\">تصفح أسرع. تحكم أكبر. خصوصية تحت سيطرتك.</p>")
        sb.append("<form class=\"search\" onsubmit=\"return go()\"><input id=\"q\" placeholder=\"ابحث أو اكتب عنوان الموقع...\" autocomplete=\"off\" autocapitalize=\"off\"><a class=\"b\" href=\"mx://voice\" aria-label=\"بحث صوتي\">⌕</a></form></section>")

        sb.append("<div class=\"clockcard\"><div class=\"cdate\" id=\"cdate\">—</div><div class=\"ctime\" id=\"ctime\">--:--</div>" +
            "<div class=\"prow\" id=\"prow\"><div class=\"p\"><span>الفجر</span><b>--:--</b></div><div class=\"p\"><span>الظهر</span><b>--:--</b></div><div class=\"p\"><span>العصر</span><b>--:--</b></div><div class=\"p\"><span>المغرب</span><b>--:--</b></div><div class=\"p\"><span>العشاء</span><b>--:--</b></div></div></div>")

        sb.append("<div class=\"section\"><div class=\"heading\"><i class=\"bar\"></i> QUICK ACCESS</div><a href=\"mx://addsc\">＋ إضافة اختصار</a></div><div class=\"grid\">")
        val custom = Cfg.str("shortcuts", "").isNotEmpty()
        for ((i, e) in sc.withIndex()) {
            sb.append("<a class=\"tile\" href=\"").append(esc(e.url)).append("\"><div class=\"ic\">").append(icon(ctx, e.url, e.name)).append("</div><span>").append(esc(e.name.take(13))).append("</span>")
            if (custom) sb.append("<span class=\"x\" onclick=\"event.preventDefault();location.href='mx://delsc?i=").append(i).append("'\">×</span>")
            sb.append("</a>")
        }
        sb.append("<a class=\"tile\" href=\"mx://addsc\"><div class=\"ic\">＋</div><span>إضافة</span></a></div>")

        val st = Storage.compute(ctx)
        sb.append("<div class=\"strip\"><a href=\"https://mx.home/shield\"><b>").append(db.todayBlocked()).append("</b><span>🛡 محجوب اليوم</span></a>")
        sb.append("<a href=\"https://mx.home/shield\"><b>").append(db.totalSkipped()).append("</b><span>⚡ انتظار تم تخطيه</span></a>")
        sb.append("<a href=\"mx://storage\"><b>").append(Storage.fmt(st.total)).append("</b><span>💾 مساحة المتصفح</span></a></div>")

        sb.append("<div class=\"section\"><div class=\"heading\"><i class=\"bar\"></i> GAMING LIBRARY</div><a href=\"mx://addgame\">＋ إضافة لعبة</a></div><div class=\"games\">")
        for (g in games) {
            sb.append("<a class=\"game\" href=\"").append(esc(g.url)).append("\"><div class=\"ic\">").append(icon(ctx, g.url, g.name)).append("</div><div style=\"min-width:0;flex:1\"><b>").append(esc(g.name)).append("</b><small>").append(esc(hostOf(g.url))).append("</small></div><span class=\"launch\">↗</span></a>")
        }
        sb.append("</div>")

        sb.append("<div class=\"section\"><div class=\"heading\"><i class=\"bar\"></i> GX CONTROL CENTER</div><a href=\"mx://gaming\">فتح لوحة التحكم ↗</a></div>")
        sb.append("<div class=\"control-grid\"><a class=\"control\" href=\"mx://gaming\"><span class=\"symbol\">⚡</span><span><b>Gaming Mode</b><small>وضع اللعب والأداء</small></span></a>")
        sb.append("<a class=\"control\" href=\"mx://performance\"><span class=\"symbol\">▥</span><span><b>Performance Lab</b><small>الذاكرة والتبويبات</small></span></a>")
        sb.append("<a class=\"control\" href=\"https://mx.home/shield\"><span class=\"symbol\">⬡</span><span><b>Security Center</b><small>حالة الدرع والحماية</small></span></a>")
        sb.append("<a class=\"control\" href=\"mx://tabs\"><span class=\"symbol\">▣</span><span><b>Tab Manager</b><small>إدارة جلسات التصفح</small></span></a></div>")

        if (Cfg.bool(Cfg.GXCORNER, true)) {
            GxCorner.ensure(ctx)
            val d = GxCorner.cached(ctx)
            sb.append("<div class=\"section\"><div class=\"heading\"><i class=\"bar\"></i> GX CORNER</div><a href=\"mx://gxrefresh\">تحديث ↻</a></div>")
            sb.append("<div class=\"gx\"><button class=\"on\" onclick=\"gx(0,this)\">الأخبار</button><button onclick=\"gx(1,this)\">الإصدارات</button><button onclick=\"gx(2,this)\">العروض</button></div>")
            val lists = listOf(d?.news, d?.releases, d?.deals)
            for ((idx, list) in lists.withIndex()) {
                sb.append("<div class=\"gxlist").append(if (idx == 0) " on" else "").append("\" id=\"gl").append(idx).append("\">")
                if (list.isNullOrEmpty()) sb.append("<div class=\"empty\">المحتوى سيظهر بعد تحديث GX Corner.</div>")
                else for (e in list.take(5)) {
                    sb.append("<a class=\"gxc\" href=\"").append(esc(e.url)).append("\">")
                    if (e.img.startsWith("http")) sb.append("<img loading=\"lazy\" src=\"").append(esc(e.img)).append("\">")
                    sb.append("<div><b>").append(esc(e.title)).append("</b>")
                    if (e.sub.isNotEmpty()) sb.append("<small>").append(esc(e.sub)).append("</small>")
                    sb.append("</div></a>")
                }
                sb.append("</div>")
            }
        }

        sb.append("<div class=\"section\"><div class=\"heading\"><i class=\"bar\"></i> PICK UP WHERE YOU LEFT</div><a href=\"https://mx.home/bookmarks\">كل المفضلات</a></div>")
        val list = if (quick.isNotEmpty()) quick.map { Entry(it.title, it.url) } else db.getHistory(5).map { Entry(it.title, it.url) }
        if (list.isEmpty()) sb.append("<div class=\"empty\">مفيش صفحات هنا لسه. افتح موقع وهيظهر هنا.</div>")
        for (e in list) sb.append(item(ctx, e.name, e.url, e.url, null))

        sb.append("<div style=\"text-align:center;padding:22px 0 4px;color:#747e98;font-size:9px;letter-spacing:2px;font-weight:800\">MATRIX CO • BUILT FOR THE NEXT SESSION</div>")
        sb.append("<script>function go(){var v=document.getElementById('q').value.trim();if(v){location.href='mx://go?q='+encodeURIComponent(v);}return false;}function gx(i,b){var l=document.querySelectorAll('.gxlist'),k=document.querySelectorAll('.gx button');for(var j=0;j<l.length;j++){l[j].className='gxlist'+(j==i?' on':'');k[j].className=(j==i?'on':'')}}document.getElementById('q').addEventListener('keydown',function(e){if(e.key==='Escape')this.blur()});</script>")
        sb.append("<script>(function(){" +
            "var ctime=document.getElementById('ctime'),cdate=document.getElementById('cdate'),prow=document.getElementById('prow');" +
            "if(!ctime)return;" +
            "var days=['الأحد','الاثنين','الثلاثاء','الأربعاء','الخميس','الجمعة','السبت'];" +
            "var months=['يناير','فبراير','مارس','أبريل','مايو','يونيو','يوليو','أغسطس','سبتمبر','أكتوبر','نوفمبر','ديسمبر'];" +
            "var now=new Date();" +
            "cdate.textContent=days[now.getDay()]+'، '+now.getDate()+' '+months[now.getMonth()];" +
            "function tick(){var n=new Date();var h=n.getHours(),m=n.getMinutes();ctime.textContent=(h<10?'0':'')+h+':'+(m<10?'0':'')+m;}" +
            "tick();setInterval(tick,15000);" +
            "var order=[['Fajr','الفجر'],['Dhuhr','الظهر'],['Asr','العصر'],['Maghrib','المغرب'],['Isha','العشاء']];" +
            "function render(t){" +
            "var nowM=now.getHours()*60+now.getMinutes(),nextIdx=-1,best=1e9,mins=order.map(function(o){var p=(t[o[0]]||'00:00').split(':');return (+p[0])*60+(+p[1]);});" +
            "for(var i=0;i<mins.length;i++){var diff=mins[i]-nowM;if(diff>=0&&diff<best){best=diff;nextIdx=i;}}" +
            "var html='';for(var i=0;i<order.length;i++){html+='<div class=\\\"p'+(i===nextIdx?' on':'')+'\\\"><span>'+order[i][1]+'</span><b>'+(t[order[i][0]]||'--:--')+'</b></div>';}" +
            "prow.innerHTML=html;}" +
            "var key='mx_prayer_'+now.toISOString().slice(0,10);var cached=null;try{cached=localStorage.getItem(key);}catch(e){}" +
            "if(cached){try{render(JSON.parse(cached));return;}catch(e){}}" +
            "fetch('https://api.aladhan.com/v1/timingsByAddress?address=Cairo%2CEgypt&method=5').then(function(r){return r.json();}).then(function(j){" +
            "var t=j.data.timings,clean={};['Fajr','Dhuhr','Asr','Maghrib','Isha'].forEach(function(k){clean[k]=(t[k]||'').split(' ')[0];});" +
            "try{localStorage.setItem(key,JSON.stringify(clean));}catch(e){}render(clean);" +
            "}).catch(function(){prow.innerHTML='<div style=\\\"padding:8px;font-size:10px;color:#9aa2b8;text-align:center;width:100%\\\">تعذر تحميل مواقيت الصلاة — تحتاج اتصال بالإنترنت</div>';});" +
            "})();</script>")
        return page(t, "MaTrIX Co", sb.toString(), if (wall.isEmpty()) "" else "wall" + (if (Cfg.str(Cfg.WALL, "gx") == "gx") " opera" else ""))
    }

    private fun history(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val items = db.getHistory(300).map { item(ctx, it.title, it.url, it.url, "mx://delhist?id=" + it.id) }
        val clear = if (items.isEmpty()) "" else "<a class=\"btn alt\" href=\"mx://clearhist\">مسح الكل</a>"
        return page(t, "السجل", listBody("السجل", items, "لا يوجد سجل بعد", clear))
    }

    private fun bookmarks(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val items = db.getBookmarks(300).map { item(ctx, it.title, it.url, it.url, "mx://delbm?u=" + Uri.encode(it.url)) }
        return page(t, "المفضلة", listBody("المفضلة", items, "لا توجد صفحات مفضلة"))
    }

    private fun offline(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val all = db.getOffline()
        val items = all.map { item(ctx, it.title, fmtTime(it.ts) + " • " + Storage.fmt(it.size) + " • " + hostOf(it.url), "mx://openoff?id=" + it.id, "mx://deloff?id=" + it.id, it.url) }
        return page(t, "الصفحات المحفوظة", listBody("صفحات بدون إنترنت", items, "لم تحفظ أي صفحة بعد", "<span class=\"chip\" style=\"background:var(--sf2);color:var(--sb)\">" + Storage.fmt(all.sumOf { it.size }) + "</span>"))
    }

    private fun downloads(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val items = db.getDownloads(200).map { item(ctx, it.name, fmtTime(it.ts) + " • " + hostOf(it.url), "mx://opendl?id=" + it.id, "mx://deldl?id=" + it.id, it.url) }
        return page(t, "التحميلات", listBody("التحميلات", items, "لا توجد تحميلات"))
    }

    private fun shield(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val sb = StringBuilder()
        val shieldOn = AdBlocker.enabled()
        val ironOn = IronGuard.enabled()
        val fpOn = Cfg.bool(Cfg.FP, true)
        val safeOn = Cfg.bool(Cfg.SAFEB, true)
        val active = shieldOn && ironOn && fpOn && safeOn
        sb.append("<section class=\"security-hero\"><div class=\"state\">MATRIX SECURITY CENTER</div>")
        sb.append("<h2>").append(if (active) "حمايتك تعمل" else "راجع إعدادات الحماية").append("</h2>")
        sb.append("<p>لوحة حالة محلية تعرض إعدادات الدرع وسجل الحجب. حالة التشغيل لا تعني ضمانًا مطلقًا ضد كل التهديدات.</p></section>")

        fun status(name: String, detail: String, on: Boolean) {
            sb.append("<div class=\"status-card\"><b><i class=\"status-dot").append(if (on) "" else " off").append("\"></i>")
                .append(esc(name)).append("</b><span>").append(esc(if (on) detail else "متوقف — " + detail)).append("</span></div>")
        }
        sb.append("<div class=\"status-grid\">")
        status("Ad & Tracker Shield", "حجب النطاقات والإعلانات والمتتبعات", shieldOn)
        status("IronGuard", "فحص مخاطر المواقع والروابط", ironOn)
        status("Anti-Fingerprinting", "تقليل بعض إشارات بصمة المتصفح", fpOn)
        status("Safe Browsing", "حماية WebView المدعومة من Android", safeOn)
        sb.append("</div>")

        sb.append("<div class=\"section\"><div class=\"heading\"><i class=\"bar\"></i> PROTECTION ACTIVITY</div><a href=\"mx://update\">تحديث القوائم ↻</a></div>")
        sb.append("<div class=\"strip\"><a href=\"#activity\"><b>").append(db.todayBlocked()).append("</b><span>محجوب اليوم</span></a>")
        sb.append("<a href=\"#activity\"><b>").append(db.totalBlocked()).append("</b><span>إجمالي المحجوب</span></a>")
        sb.append("<a href=\"#activity\"><b>").append(db.totalSkipped()).append("</b><span>انتظار تم تخطيه</span></a></div>")

        val week = db.week()
        val maxV = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        sb.append("<div class=\"card\" id=\"activity\"><h4>نشاط الحجب خلال آخر 7 أيام</h4><div class=\"bars\">")
        for (w in week) sb.append("<div style=\"height:").append(((w.second * 100) / maxV).coerceAtLeast(4)).append("%\" title=\"").append(esc(w.first)).append(" • ").append(w.second).append("\"></div>")
        sb.append("</div></div>")
        sb.append("<div class=\"row\"><span>متتبعات محجوبة</span><span>").append(db.counter("trackers_total")).append("</span></div>")
        sb.append("<div class=\"row\"><span>نطاقات في قاعدة البيانات</span><span>").append(db.blockedHostsCount()).append("</span></div>")
        sb.append("<div class=\"row\"><span>آخر تحديث للقوائم</span><span>").append(esc(RuleUpdater.getLastUpdate(ctx) ?: "لم يتم بعد")).append("</span></div>")
        val msg = RuleUpdater.getLastMessage(ctx)
        if (!msg.isNullOrBlank()) sb.append("<div class=\"row\"><span>حالة التحديث</span><span style=\"max-width:62%;text-align:left;font-size:10px\">").append(esc(msg)).append("</span></div>")

        sb.append("<div class=\"section\"><div class=\"heading\"><i class=\"bar\"></i> TOP BLOCKED HOSTS</div><a href=\"https://mx.home/spy\">سجل الرصد</a></div>")
        val top = db.getTopBlocked(10)
        if (top.isEmpty()) sb.append("<div class=\"empty\">لا توجد بيانات حجب كافية بعد.</div>")
        for ((host, count) in top) sb.append("<div class=\"row\"><span style=\"direction:ltr\">").append(esc(host)).append("</span><span>").append(count).append("</span></div>")
        sb.append("<div style=\"margin-top:14px\"><a class=\"btn alt\" href=\"https://mx.home/spy\">فتح سجل كاشف التجسس</a></div>")
        return page(t, "Security Center", sb.toString())
    }

    private fun spyLog(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val log = db.getSpyLog(100)
        val items = log.map {
            val col = if (it.conf >= SpyGuard.AUTO) "#D93025" else if (it.conf >= SpyGuard.ASK) "#E37400" else "#5F6368"
            "<div class=\"item\"><div class=\"t\"><b>" + esc(SpyGuard.label(it.type)) + "</b><span>" + esc(it.host) + " ← " + esc(it.page) + " • " + fmtTime(it.ts) +
                "</span></div><span class=\"chip\" style=\"background:" + col + "\">" + it.conf + "% " + esc(it.decision) + "</span></div>"
        }
        val clear = if (items.isEmpty()) "" else "<a class=\"btn alt\" href=\"mx://clearspy\">مسح</a>"
        return page(t, "كاشف التجسس", listBody("سجل كاشف التجسس", items, "لا توجد حالات — المتصفح يراقب في الخلفية", clear))
    }

    private fun storage(ctx: Context, t: MxTheme): String {
        val s = Storage.compute(ctx)
        val sb = StringBuilder()
        sb.append("<div class=\"ptop\"><h2>مساحة التطبيق</h2><span class=\"chip\" style=\"background:var(--ac)\">").append(Storage.fmt(s.total)).append("</span></div>")
        sb.append("<div class=\"row\"><span>كاش التصفح</span><span>").append(Storage.fmt(s.cache)).append("</span></div>")
        sb.append("<div class=\"row\"><span>بيانات المواقع والكوكيز</span><span>").append(Storage.fmt(s.web)).append("</span></div>")
        sb.append("<div class=\"row\"><span>الصفحات المحفوظة</span><span>").append(Storage.fmt(s.offline)).append("</span></div>")
        sb.append("<div class=\"row\"><span>قاعدة الدرع</span><span>").append(Storage.fmt(s.db)).append("</span></div>")
        sb.append("<p class=\"small\">الكاش بيتنظف تلقائيًا لو عدّى الحد (").append(Cfg.int(Cfg.CACHE_MB, 150)).append(" MB). التحميلات في مجلد التنزيلات ولا تدخل ضمن الرقم ده.</p>")
        sb.append("<a class=\"btn\" href=\"mx://cleancache\">تنظيف الكاش الآن</a><a class=\"btn alt\" href=\"https://mx.home/offline\">الصفحات المحفوظة</a>")
        return page(t, "المساحة", sb.toString())
    }

    private fun error(ctx: Context, db: ShieldDatabase, t: MxTheme, url: String, msg: String): String {
        val saved = if (url.isNotEmpty()) db.getOfflineByUrl(url) else null
        val sb = StringBuilder()
        sb.append("<div class=\"empty\" style=\"padding-top:70px\"><div style=\"font-size:48px\">📡</div><h2 style=\"font-weight:600\">تعذر فتح الصفحة</h2>")
        sb.append("<div style=\"direction:ltr;word-break:break-all;font-size:12px\">").append(esc(url)).append("</div>")
        if (msg.isNotEmpty()) sb.append("<p>").append(esc(msg)).append("</p>")
        sb.append("<a class=\"btn\" href=\"").append(esc(url)).append("\">إعادة المحاولة</a>")
        if (saved != null) sb.append("<a class=\"btn alt\" href=\"mx://openoff?id=").append(saved.id).append("\">النسخة المحفوظة</a>")
        sb.append("<a class=\"btn alt\" href=\"mx://legacy?u=").append(Uri.encode(url)).append("\">وضع التوافق القديم</a></div>")
        return page(t, "خطأ", sb.toString())
    }

    private fun warn(t: MxTheme, url: String): String {
        val sb = StringBuilder()
        sb.append("<div class=\"empty\" style=\"padding-top:60px\"><div style=\"font-size:54px\">⛔</div><h2 style=\"color:#F28B82;font-weight:600\">موقع خطير</h2>")
        sb.append("<p>الدرع رصد إن الموقع ده في قوائم البرمجيات الخبيثة أو التصيّد. ننصحك بعدم المتابعة.</p>")
        sb.append("<div style=\"direction:ltr;word-break:break-all;font-size:12px\">").append(esc(url)).append("</div><br>")
        sb.append("<a class=\"btn\" href=\"mx://back\">رجوع للأمان</a><a class=\"btn alt\" href=\"mx://proceed?u=").append(Uri.encode(url)).append("\">أفهم المخاطر، تابع</a></div>")
        return page(t, "تحذير", sb.toString())
    }
}
