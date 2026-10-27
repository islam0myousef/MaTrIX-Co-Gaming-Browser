package com.matrixco.browser

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Locale

/**
 * MaTrIX Shield
 *
 * Lightweight WebView ad and tracker blocker.
 * Uses a local domain blocklist and a first-party allowlist.
 */
object AdBlocker {

    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
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
        "branch.io",
        "appsflyer.com",
        "adjust.com"
    )

    private val blockedPathHints = listOf(
        "/ads/",
        "/adserver/",
        "/advertisement/",
        "/advertising/",
        "/banner-ad/",
        "/popunder/"
    )

    private val blockedExtensions = setOf(
        ".ad",
        ".ads"
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

    private fun isBlockedUrl(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }

        if (uri.scheme != "http" && uri.scheme != "https") {
            return false
        }

        if (isBlockedHost(uri.host)) return true

        val path = uri.path.orEmpty()
            .lowercase(Locale.ROOT)

        if (blockedPathHints.any { path.contains(it) }) {
            return true
        }

        val lastSegment = path.substringAfterLast('/')

        return blockedExtensions.any {
            lastSegment.endsWith(it)
        }
    }

    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Attach this client to a WebView to block matching requests.
     *
     * Note: This replaces the WebView's current WebViewClient.
     */
    fun install(
        webView: WebView,
        onPageStarted: ((String) -> Unit)? = null,
        onPageFinished: ((String) -> Unit)? = null
    ) {
        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()
                    ?: return super.shouldInterceptRequest(
                        view,
                        request
                    )

                return if (isBlockedUrl(url)) {
                    emptyResponse()
                } else {
                    super.shouldInterceptRequest(
                        view,
                        request
                    )
                }
            }

            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(
                view: WebView?,
                url: String?
            ): WebResourceResponse? {
                val value = url
                    ?: return super.shouldInterceptRequest(
                        view,
                        url
                    )

                return if (isBlockedUrl(value)) {
                    emptyResponse()
                } else {
                    super.shouldInterceptRequest(
                        view,
                        value
                    )
                }
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                if (url != null) onPageStarted?.invoke(url)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                if (url != null) onPageFinished?.invoke(url)
                super.onPageFinished(view, url)
            }
        }
    }

    /**
     * Check whether a URL matches the built-in block rules.
     */
    fun shouldBlock(url: String): Boolean {
        return isBlockedUrl(url)
    }
}
