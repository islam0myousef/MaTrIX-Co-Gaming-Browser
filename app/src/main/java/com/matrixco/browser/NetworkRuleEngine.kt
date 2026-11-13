package com.matrixco.browser

import android.net.Uri
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * MaTRIX Network Rule Engine v2.
 *
 * Performance design:
 * - Host-anchored rules are indexed by hostname suffix. A request evaluates only
 *   rules relevant to its host instead of scanning every compiled regex.
 * - Generic URL rules are bounded to keep memory and per-request CPU predictable.
 * - The published state is immutable and swapped atomically after compilation.
 */
object NetworkRuleEngine {
    enum class ResourceType { DOCUMENT, SCRIPT, IMAGE, STYLESHEET, OBJECT, XMLHTTPREQUEST, SUBDOCUMENT, FONT, MEDIA, OTHER }
    data class RequestContext(val url: String, val pageHost: String?, val resourceType: ResourceType, val mainFrame: Boolean)
    data class Decision(val blocked: Boolean, val exception: Boolean = false, val rule: String? = null, val reason: String = "no-match", val category: Int = Cat.ADS)

    private data class Rule(
        val raw: String,
        val regex: Regex,
        val hostAnchor: String?,
        val exception: Boolean,
        val category: Int,
        val includedTypes: Set<ResourceType>,
        val excludedTypes: Set<ResourceType>,
        val thirdParty: Boolean?,
        val includedDomains: Set<String>,
        val excludedDomains: Set<String>,
        val important: Boolean
    )

    private data class State(
        val byHost: Map<String, List<Rule>>,
        val generic: List<Rule>,
        val compiledCount: Int,
        val skippedCount: Int
    )

    private val state = AtomicReference(State(emptyMap(), emptyList(), 0, 0))
    @Volatile private var loaded = false

    // Keep runtime cost bounded on mid-range Android devices.
    private const val MAX_HOST_RULES = 28_000
    private const val MAX_GENERIC_RULES = 1_200
    private const val MAX_RULE_LENGTH = 1_500

    fun replace(rawRules: Collection<Pair<String, String>>) {
        val hostBuckets = HashMap<String, MutableList<Rule>>()
        val generic = ArrayList<Rule>(MAX_GENERIC_RULES)
        val enabledCore = RuleUpdater.sources.filter { RuleUpdater.isOn(it) }.mapTo(HashSet()) { "core:" + it.id }
        val enabledLists = FilterEngine.all().filter { FilterEngine.isOn(it) }.mapTo(HashSet()) { "list:" + it.id }.apply { add("list:custom_rules") }
        var hostCount = 0
        var genericCount = 0
        var skipped = 0

        for ((source, raw) in rawRules) {
            if (source.startsWith("core:") && source !in enabledCore) continue
            if (source.startsWith("list:") && source !in enabledLists) continue
            val line = raw.trim()
            if (line.isEmpty() || line.length > MAX_RULE_LENGTH) {
                skipped++
                continue
            }
            val category = if (source.contains("easyprivacy", true)) Cat.TRK else Cat.ADS
            val rule = parse(line, category)
            if (rule == null) {
                skipped++
                continue
            }
            val anchor = rule.hostAnchor
            if (anchor != null) {
                if (hostCount >= MAX_HOST_RULES) { skipped++; continue }
                hostBuckets.getOrPut(anchor) { ArrayList() }.add(rule)
                hostCount++
            } else {
                if (genericCount >= MAX_GENERIC_RULES) { skipped++; continue }
                generic.add(rule)
                genericCount++
            }
        }

        val immutable = HashMap<String, List<Rule>>(hostBuckets.size)
        for ((host, list) in hostBuckets) immutable[host] = list.toList()
        state.set(State(immutable, generic.toList(), hostCount + genericCount, skipped))
        loaded = true
    }

    fun isLoaded() = loaded
    fun ruleCount() = state.get().compiledCount
    fun skippedRuleCount() = state.get().skippedCount

    fun evaluate(ctx: RequestContext): Decision {
        val uri = try { Uri.parse(ctx.url) } catch (_: Exception) { return Decision(false) }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return Decision(false, reason = "non-http")
        val host = (uri.host ?: return Decision(false)).lowercase(Locale.ROOT).trimEnd('.')
        val page = ctx.pageHost.orEmpty().lowercase(Locale.ROOT).trimEnd('.')
        val third = page.isNotEmpty() && registrable(host) != registrable(page)
        val snapshot = state.get()

        var normal: Rule? = null
        var important: Rule? = null
        var exception: Rule? = null

        fun consider(r: Rule) {
            if (!r.regex.containsMatchIn(ctx.url)) return
            if (r.includedTypes.isNotEmpty() && ctx.resourceType !in r.includedTypes) return
            if (ctx.resourceType in r.excludedTypes) return
            if (r.thirdParty != null && r.thirdParty != third) return
            if (r.includedDomains.isNotEmpty() && r.includedDomains.none { domainMatches(page, it) }) return
            if (r.excludedDomains.any { domainMatches(page, it) }) return
            when {
                r.exception -> if (exception == null) exception = r
                r.important -> if (important == null) important = r
                else -> if (normal == null) normal = r
            }
        }

        // Only walk rules indexed for this host and its parent suffixes.
        var suffix = host
        while (true) {
            snapshot.byHost[suffix]?.forEach(::consider)
            val dot = suffix.indexOf('.')
            if (dot < 0) break
            suffix = suffix.substring(dot + 1)
        }
        // Generic URL rules are strictly bounded by MAX_GENERIC_RULES.
        snapshot.generic.forEach(::consider)

        important?.let { return Decision(true, rule = it.raw, reason = "important-rule", category = it.category) }
        exception?.let { return Decision(false, exception = true, rule = it.raw, reason = "exception-rule") }
        normal?.let { return Decision(true, rule = it.raw, reason = "network-rule", category = it.category) }
        return Decision(false)
    }

    private fun parse(line: String, category: Int): Rule? {
        if (line.startsWith('!') || line.startsWith('[') || line.startsWith('#')) return null
        val exception = line.startsWith("@@")
        val body = if (exception) line.substring(2) else line
        val split = body.split('$', limit = 2)
        val pattern = split[0]
        if (pattern.isBlank() || pattern.startsWith("#") || pattern.contains("##") || pattern.contains("#@#")) return null
        val opts = split.getOrNull(1)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (opts.any { it.startsWith("redirect") || it.startsWith("redirect-rule") || it.startsWith("removeparam") || it.startsWith("csp=") || it.startsWith("replace=") || it.startsWith("permissions=") || it.startsWith("urlskip=") || it.startsWith("header=") || it.startsWith("method=") || it == "badfilter" || it.startsWith("+js(") }) return null

        val included = linkedSetOf<ResourceType>()
        val excluded = linkedSetOf<ResourceType>()
        val includeDomains = linkedSetOf<String>()
        val excludeDomains = linkedSetOf<String>()
        var third: Boolean? = null
        var matchCase = false
        var important = false
        val typeMap = mapOf(
            "document" to ResourceType.DOCUMENT, "script" to ResourceType.SCRIPT, "image" to ResourceType.IMAGE,
            "stylesheet" to ResourceType.STYLESHEET, "object" to ResourceType.OBJECT, "xmlhttprequest" to ResourceType.XMLHTTPREQUEST,
            "xhr" to ResourceType.XMLHTTPREQUEST, "subdocument" to ResourceType.SUBDOCUMENT, "frame" to ResourceType.SUBDOCUMENT,
            "font" to ResourceType.FONT, "media" to ResourceType.MEDIA, "other" to ResourceType.OTHER
        )
        for (o in opts) {
            when {
                o == "third-party" || o == "3p" -> third = true
                o == "~third-party" || o == "~3p" || o == "1p" -> third = false
                o == "match-case" -> matchCase = true
                o == "important" -> important = true
                o.startsWith("domain=") -> for (d0 in o.substringAfter('=').split('|')) {
                    val neg = d0.startsWith('~')
                    val d = d0.removePrefix("~").lowercase(Locale.ROOT).trim('.')
                    if (validDomain(d)) { if (neg) excludeDomains.add(d) else includeDomains.add(d) }
                }
                o in typeMap -> included.add(typeMap.getValue(o))
                o.startsWith('~') && o.drop(1) in typeMap -> excluded.add(typeMap.getValue(o.drop(1)))
                o in setOf("popup", "webrtc", "genericblock", "generichide", "elemhide", "specifichide", "all") -> Unit
                else -> return null
            }
        }

        val regex = patternToRegex(pattern, matchCase) ?: return null
        val anchor = extractHostAnchor(pattern)
        return Rule(line, regex, anchor, exception, category, included, excluded, third, includeDomains, excludeDomains, important)
    }

    private fun extractHostAnchor(pattern: String): String? {
        if (!pattern.startsWith("||")) return null
        val tail = pattern.substring(2)
        val end = tail.indexOfFirst { it == '/' || it == '^' || it == '*' || it == '|' || it == '?' }
        val host = (if (end < 0) tail else tail.substring(0, end)).lowercase(Locale.ROOT).trimEnd('.')
        return host.takeIf { validDomain(it) }
    }

    private fun patternToRegex(pattern: String, matchCase: Boolean): Regex? {
        if (pattern.length > MAX_RULE_LENGTH) return null
        val domainAnchor = pattern.startsWith("||")
        val startAnchor = !domainAnchor && pattern.startsWith('|')
        val endAnchor = pattern.endsWith('|') && pattern.length > 1
        val from = if (domainAnchor) 2 else if (startAnchor) 1 else 0
        val until = if (endAnchor) pattern.length - 1 else pattern.length
        val out = StringBuilder(if (domainAnchor || startAnchor) "^" else ".*")
        if (domainAnchor) out.append("[a-zA-Z][a-zA-Z0-9+.-]*://(?:[^/?#]*\\.)?")
        for (i in from until until) {
            when (val c = pattern[i]) {
                '*' -> out.append(".*")
                '^' -> out.append("(?:[^a-zA-Z0-9_.%-]|$)")
                '|' -> out.append("\\|")
                else -> { if ("\\.[]{}()+-?$".contains(c)) out.append('\\'); out.append(c) }
            }
        }
        if (endAnchor) out.append('$')
        return try { Regex(out.toString(), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)) } catch (_: Exception) { null }
    }

    private fun validDomain(d: String) = d.length in 1..253 && d.split('.').all {
        it.isNotEmpty() && it.length <= 63 && it.first().isLetterOrDigit() && it.last().isLetterOrDigit() && it.all { c -> c.isLetterOrDigit() || c == '-' }
    }
    private fun domainMatches(host: String, domain: String) = host == domain || host.endsWith(".$domain")
    private fun registrable(host: String): String {
        val p = host.split('.')
        if (p.size <= 2) return host
        val two = p[p.lastIndex - 1]
        return if (p.last().length == 2 && two in setOf("co", "com", "net", "org", "gov", "ac")) p.takeLast(3).joinToString(".") else p.takeLast(2).joinToString(".")
    }

    fun inferType(url: String, accept: String?, mainFrame: Boolean): ResourceType {
        if (mainFrame) return ResourceType.DOCUMENT
        val a = accept.orEmpty().lowercase(Locale.ROOT)
        if (a.contains("javascript") || a.contains("ecmascript")) return ResourceType.SCRIPT
        if (a.contains("text/css")) return ResourceType.STYLESHEET
        if (a.contains("image/")) return ResourceType.IMAGE
        if (a.contains("font/") || a.contains("woff") || a.contains("application/font")) return ResourceType.FONT
        if (a.contains("audio/") || a.contains("video/")) return ResourceType.MEDIA
        if (a.contains("text/html")) return ResourceType.SUBDOCUMENT
        val path = try { Uri.parse(url).lastPathSegment.orEmpty().lowercase(Locale.ROOT) } catch (_: Exception) { "" }
        return when {
            path.endsWith(".js") || path.endsWith(".mjs") -> ResourceType.SCRIPT
            path.endsWith(".css") -> ResourceType.STYLESHEET
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".gif") ||
                path.endsWith(".webp") || path.endsWith(".svg") || path.endsWith(".ico") || path.endsWith(".avif") -> ResourceType.IMAGE
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") || path.endsWith(".otf") || path.endsWith(".eot") -> ResourceType.FONT
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mp3") || path.endsWith(".m4a") ||
                path.endsWith(".ogg") || path.endsWith(".m3u8") || path.endsWith(".mpd") -> ResourceType.MEDIA
            else -> ResourceType.XMLHTTPREQUEST
        }
    }
}
