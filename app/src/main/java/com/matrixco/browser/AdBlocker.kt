package com.matrixco.browser

import android.net.Uri
import java.util.Locale

/**
 * MaTrIX Shield
 *
 * Lightweight host-based ad and tracker blocker for WebView.
 * Uses conservative domain matching to reduce accidental blocking
 * of legitimate game assets.
 */
object AdBlocker {

    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adnxs.com",
        "adsrvr.org",
        "adform.net",
        "advertising.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "vungle.com",
        "chartboost.com",
        "admob.com",
        "moatads.com",
        "taboola.com",
        "outbrain.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "trafficjunky.net",
        "zedo.com",
        "criteo.com",
        "casalemedia.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "smartadserver.com",
        "media.net",
        "mgid.com",
        "inmobi.com",
        "appsflyer.com",
        "adjust.com"
    )

    private fun normalizeHost(host: String?): String {
        return host.orEmpty()
            .lowercase(Locale.ROOT)
            .trimEnd('.')
    }

    private fun isBlockedHost(host: String?): Boolean {
        val normalized = normalizeHost(host)

        if (normalized.isEmpty()) return false

        return blockedHosts.any { blocked ->
            normalized == blocked ||
                normalized.endsWith(".$blocked")
        }
    }

    /**
     * Returns true only when the URL host matches a blocked domain.
     * Does not block URLs based on generic path names or file extensions.
     */
    fun shouldBlock(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)

        if (scheme != "http" && scheme != "https") {
            return false
        }

        return isBlockedHost(uri.host)
    }
}
