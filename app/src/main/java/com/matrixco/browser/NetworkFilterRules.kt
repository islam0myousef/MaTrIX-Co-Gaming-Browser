package com.matrixco.browser

import android.net.Uri
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

/** Lightweight ABP network-rule engine. Supports URL patterns, exceptions, domain and type options. */
data class NetworkFilterRule(
    val raw: String,
    val exception: Boolean,
    val regex: Pattern,
    val includeDomains: Set<String>,
    val excludeDomains: Set<String>,
    val includedTypes: Set<String>,
    val excludedTypes: Set<String>,
    val thirdParty: Boolean?,
    val important: Boolean,
    val anchorHost: String?
) {
    fun matches(uri: Uri, pageHost: String?, type: String, third: Boolean): Boolean {
        val url = uri.toString()
        if (!regex.matcher(url).find()) return false
        val ph = pageHost.orEmpty().lowercase(Locale.ROOT).trimEnd('.')
        if (includeDomains.isNotEmpty() && includeDomains.none { ph == it || ph.endsWith(".$it") }) return false
        if (excludeDomains.any { ph == it || ph.endsWith(".$it") }) return false
        if (thirdParty != null && thirdParty != third) return false
        if (includedTypes.isNotEmpty() && type !in includedTypes) return false
        if (type in excludedTypes) return false
        return true
    }
}

object NetworkFilterRules {
    private const val MAX_RULES = 35_000
    @Volatile private var byHost: Map<String, List<NetworkFilterRule>> = emptyMap()
    @Volatile private var fallback: List<NetworkFilterRule> = emptyList()
    @Volatile private var ruleCount: Int = 0

    fun parse(raw: String): NetworkFilterRule? {
        var line = raw.trim()
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#") || line.contains("##") || line.contains("#@#") || line.contains("#$#")) return null
        val exception = line.startsWith("@@")
        if (exception) line = line.substring(2)
        if (line.isEmpty() || line.startsWith("/")) return null
        val split = line.split('$', limit = 2)
        val pattern = split[0]
        if (pattern.isEmpty()) return null
        val options = split.getOrNull(1)?.split(',')?.map { it.trim().lowercase(Locale.ROOT) }.orEmpty()
        if (options.any { it.startsWith("redirect") || it.startsWith("removeparam") || it.startsWith("csp=") || it == "badfilter" || it.startsWith("replace=") || it.startsWith("permissions=") }) return null
        val include = linkedSetOf<String>(); val exclude = linkedSetOf<String>()
        var third: Boolean? = null
        val inTypes = linkedSetOf<String>(); val outTypes = linkedSetOf<String>()
        val types = setOf("document", "subdocument", "script", "stylesheet", "image", "font", "object", "xmlhttprequest", "ping", "media", "websocket", "other")
        for (opt in options) {
            when {
                opt == "third-party" || opt == "3p" -> third = true
                opt == "~third-party" || opt == "1p" -> third = false
                opt.startsWith("domain=") -> opt.substringAfter('=').split('|').forEach { d ->
                    val x = d.trim().lowercase(Locale.ROOT); if (x.isNotEmpty()) if (x.startsWith("~")) exclude.add(x.drop(1)) else include.add(x)
                }
                opt in types -> inTypes.add(opt)
                opt == "xhr" -> inTypes.add("xmlhttprequest")
                opt == "frame" -> inTypes.add("subdocument")
                opt.startsWith("~") && opt.drop(1) in types -> outTypes.add(opt.drop(1))
                opt in setOf("match-case", "important", "badfilter", "all", "popup", "elemhide", "generichide", "specifichide", "genericblock", "inline-script", "inline-font", "websocket", "webrtc", "empty", "collapse", "donottrack", "sitekey") -> Unit
                opt.startsWith("~") && opt.drop(1) in setOf("third-party") -> Unit
                else -> if (opt.isNotEmpty()) return null
            }
        }
        val regex = try { Pattern.compile(toRegex(pattern), Pattern.CASE_INSENSITIVE) } catch (_: Exception) { return null }
        val anchor = if (pattern.startsWith("||")) pattern.substring(2).takeWhile { it != '/' && it != '^' && it != '*' && it != '|' && it != '$' }
            .lowercase(Locale.ROOT).takeIf { it.contains('.') && it.all { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '-' } } else null
        return NetworkFilterRule(raw = raw.trim(), exception = exception, regex = regex,
            includeDomains = include, excludeDomains = exclude, includedTypes = inTypes,
            excludedTypes = outTypes, thirdParty = third, important = options.contains("important"), anchorHost = anchor)
    }

    private fun toRegex(input: String): String {
        var p = input
        val out = StringBuilder()
        var i = 0
        if (p.startsWith("||")) { out.append("^https?://(?:[^/]+\\.)?"); i = 2 }
        else if (p.startsWith('|')) { out.append('^'); i = 1 }
        while (i < p.length) {
            val c = p[i]
            when (c) {
                '*' -> out.append(".*")
                '^' -> out.append("(?:[^A-Za-z0-9_.%-]|$)")
                '|' -> if (i == p.lastIndex) out.append('$') else out.append("\\|")
                else -> if ("\\.[]{}()+-?^$".contains(c)) out.append('\\').append(c) else out.append(c)
            }
            i++
        }
        return out.toString()
    }

    fun replace(rules: List<String>) {
        val indexed = HashMap<String, MutableList<NetworkFilterRule>>()
        val other = ArrayList<NetworkFilterRule>()
        var count = 0
        for (raw in rules.take(MAX_RULES)) {
            val rule = parse(raw) ?: continue
            count++
            val host = rule.anchorHost
            if (host == null) other.add(rule) else indexed.getOrPut(host) { ArrayList() }.add(rule)
        }
        byHost = indexed.mapValues { it.value.toList() }
        fallback = other.toList()
        ruleCount = count
    }

    /** -1 = explicit allow exception, 0 = no match, 1 = block */
    fun decision(uri: Uri, pageHost: String?, type: String, third: Boolean): Int {
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return 0
        val candidates = ArrayList<NetworkFilterRule>(fallback.size + 8)
        candidates.addAll(fallback)
        var h = host
        while (true) {
            byHost[h]?.let { candidates.addAll(it) }
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
            if (!h.contains('.')) break
        }
        var blocked = false
        var important = false
        var exception = false
        for (r in candidates) if (r.matches(uri, pageHost, type, third)) {
            if (r.exception) exception = true
            else { blocked = true; if (r.important) important = true }
        }
        return when { important -> 1; exception -> -1; blocked -> 1; else -> 0 }
    }

    fun count(): Int = ruleCount
}
