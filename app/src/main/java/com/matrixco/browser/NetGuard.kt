package com.matrixco.browser

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import java.util.concurrent.Executor

/**
 * بدائل مجانية للخدمات المدفوعة:
 *  - تصفح عبر Tor (بروكسي Orbot المحلي) أو أي بروكسي مخصص — لتصفح المتصفح بس، من غير ما يمر على سيرفراتنا.
 *  - حالة الـ VPN المفعّل في الجهاز + فتح/تثبيت تطبيقات VPN مجانية موثوقة.
 *  - DNS مشفّر (Private DNS) بإعداد من أندرويد نفسه.
 */
object NetGuard {
    const val TOR = "net_tor"
    const val CUSTOM = "net_proxy"
    private const val ORBOT_HTTP = "127.0.0.1:8118"

    class VpnApp(val name: String, val pkg: String, val note: String)
    val vpnApps = listOf(
        VpnApp("Cloudflare WARP (1.1.1.1)", "com.cloudflare.onedotonedotonedotone", "مجاني، بدون حد بيانات، مبني على WireGuard"),
        VpnApp("Proton VPN المجاني", "ch.protonvpn.android", "مجاني بدون إعلانات وبدون حد بيانات، سياسة عدم الاحتفاظ بسجلات"),
        VpnApp("Windscribe", "com.windscribe.vpn", "مجاني 10GB في الشهر مع حاجب إعلانات")
    )
    val dnsHosts = listOf(
        "dns.adguard-dns.com" to "AdGuard — بيحجب إعلانات ومتتبعات",
        "one.one.one.one" to "Cloudflare — سريع وخصوصية قوية",
        "dns.quad9.net" to "Quad9 — بيحجب النطاقات الخبيثة"
    )

    fun rule(): String? = when {
        Cfg.bool(TOR, false) -> ORBOT_HTTP
        Cfg.str(CUSTOM, "").isNotBlank() -> Cfg.str(CUSTOM, "").trim()
        else -> null
    }

    /** بيطبّق البروكسي على كل تبويبات المتصفح (بدون رجوع للاتصال المباشر عشان ما يحصلش تسريب) */
    fun apply() {
        try {
            if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE)) return
            val ctrl = androidx.webkit.ProxyController.getInstance()
            val ex = Executor { r -> r.run() }
            val r = rule()
            if (r == null) ctrl.clearProxyOverride(ex, Runnable { })
            else ctrl.setProxyOverride(androidx.webkit.ProxyConfig.Builder().addProxyRule(r).build(), ex, Runnable { })
        } catch (_: Throwable) {}
    }

    fun proxySupported(): Boolean = try {
        androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE)
    } catch (_: Throwable) { false }

    fun vpnActive(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        val c = if (n != null) cm.getNetworkCapabilities(n) else null
        c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    } catch (_: Throwable) { false }

    fun openOrStore(ctx: Context, pkg: String) {
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) { ctx.startActivity(i); return }
        } catch (_: Exception) {}
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) {
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }
}
