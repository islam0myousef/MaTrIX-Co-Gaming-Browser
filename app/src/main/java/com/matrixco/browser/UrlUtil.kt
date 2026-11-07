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


    /** Resolve address-bar input without guessing an arbitrary public suffix. */
    fun resolveAddressInput(input: String, searchBase: String, httpsOnly: Boolean = true): String {
        val raw = input.trim()
        if (raw.isEmpty()) return searchBase
        val query = { searchBase + android.net.Uri.encode(raw) }
        if (raw.any { it.isWhitespace() }) return query()

        // Repair common missing/typo protocol prefixes before parsing.
        val repaired = when {
            raw.startsWith("ttps://", true) -> "https://" + raw.substring(7)
            raw.startsWith("htps://", true) -> "https://" + raw.substring(7)
            raw.startsWith("htp://", true) -> "http://" + raw.substring(6)
            raw.startsWith("ttps:/", true) -> "https://" + raw.substring(6).trimStart('/')
            raw.startsWith("htps:/", true) -> "https://" + raw.substring(6).trimStart('/')
            raw.startsWith("htp:/", true) -> "http://" + raw.substring(5).trimStart('/')
            raw.startsWith("https//", true) -> "https://" + raw.substring(7)
            raw.startsWith("http//", true) -> "http://" + raw.substring(6)
            raw.startsWith("https:/", true) && !raw.startsWith("https://", true) -> "https://" + raw.substring(7).trimStart('/')
            raw.startsWith("http:/", true) && !raw.startsWith("http://", true) -> "http://" + raw.substring(6).trimStart('/')
            else -> raw
        }

        if (repaired.startsWith("https://", true) || repaired.startsWith("http://", true)) {
            val parsed = android.net.Uri.parse(repaired)
            return if (!parsed.host.isNullOrBlank()) repaired else query()
        }
        if (repaired.contains("://")) return query()

        val hostCandidate = repaired.removePrefix("//")
        val first = hostCandidate.substringBefore('/').substringBefore('?').substringBefore('#')
        val isLocal = first.equals("localhost", true) || first.startsWith("localhost:")
        val isIpv4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?$").matches(first)
        val isIpv6 = first.startsWith("[") && first.contains("]")
        val hasKnownTld = Regex("(?i)^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}(?::\\d{1,5})?(?:[/?#].*)?$").matches(hostCandidate)
        val isWww = hostCandidate.startsWith("www.", true) && hostCandidate.substringAfter("www.").contains('.')
        if (isLocal || isIpv4 || isIpv6 || hasKnownTld || isWww) {
            val scheme = if (isLocal && !httpsOnly) "http://" else "https://"
            return scheme + hostCandidate
        }

        // A bare word or a host missing its suffix is searched, never completed
        // to .com/.net by guesswork (which could navigate to an unrelated site).
        return query()
    }

    fun isIp(host: String): Boolean = Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(host) || host.contains(':')
}
