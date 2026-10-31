package com.matrixco.browser

import android.net.Uri
import java.util.Locale

object UrlUtil {
    private val trackingParams = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "mc_eid", "mc_cid", "igshid",
        "_hsenc", "_hsmi", "mkt_tok", "vero_id", "twclid", "ttclid", "wbraid", "gbraid", "ref_src"
    )

    private fun isTracking(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return n.startsWith("utm_") || n in trackingParams
    }

    /** يشيل بارامترات التتبع (utm_* و fbclid و gclid ...) من الرابط */
    fun stripTracking(url: String): String {
        return try {
            val uri = Uri.parse(url)
            if (!uri.isHierarchical) return url
            val names = uri.queryParameterNames
            if (names.none { isTracking(it) }) return url
            val b = uri.buildUpon().clearQuery()
            for (n in names) {
                if (isTracking(n)) continue
                for (v in uri.getQueryParameters(n)) b.appendQueryParameter(n, v)
            }
            b.build().toString()
        } catch (_: Exception) {
            url
        }
    }

    fun isIp(host: String): Boolean = Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(host) || host.contains(':')
}
