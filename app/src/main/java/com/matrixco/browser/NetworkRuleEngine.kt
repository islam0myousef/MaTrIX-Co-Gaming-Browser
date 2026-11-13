package com.matrixco.browser

import android.net.Uri
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * MaTRIX Network Rule Engine v1.
 * Implements a deliberately bounded ABP-compatible subset: URL patterns, exceptions,
 * resource types, third-party and domain include/exclude options.
 * Unsupported transformation rules (redirect/csp/removeparam/scriptlet) are ignored,
 * never misinterpreted as ordinary blocking rules.
 */
object NetworkRuleEngine {
    enum class ResourceType { DOCUMENT, SCRIPT, IMAGE, STYLESHEET, OBJECT, XMLHTTPREQUEST, SUBDOCUMENT, FONT, MEDIA, OTHER }
    data class RequestContext(val url: String, val pageHost: String?, val resourceType: ResourceType, val mainFrame: Boolean)
    data class Decision(val blocked: Boolean, val exception: Boolean = false, val rule: String? = null, val reason: String = "no-match", val category: Int = Cat.ADS)

    private data class Rule(
        val raw: String, val regex: Regex, val exception: Boolean, val category: Int,
        val includedTypes: Set<ResourceType>, val excludedTypes: Set<ResourceType>,
        val thirdParty: Boolean?, val includedDomains: Set<String>, val excludedDomains: Set<String>,
        val matchCase: Boolean, val important: Boolean
    )

    private val rules = AtomicReference<List<Rule>>(emptyList())
    @Volatile private var loaded = false

    fun replace(rawRules: Collection<Pair<String, String>>) {
        val compiled = ArrayList<Rule>(rawRules.size)
        val seen = HashSet<String>()
        val enabledCore = RuleUpdater.sources.filter { RuleUpdater.isOn(it) }.mapTo(HashSet()) { "core:" + it.id }
        val enabledLists = FilterEngine.all().filter { FilterEngine.isOn(it) }.mapTo(HashSet()) { "list:" + it.id }.apply { add("list:custom_rules") }
        for ((source, raw) in rawRules) {
            if (source.startsWith("core:") && source !in enabledCore) continue
            if (source.startsWith("list:") && source !in enabledLists) continue
            val line = raw.trim()
            if (line.isEmpty() || !seen.add(source + "\u0000" + line)) continue
            val category = if (source.contains("easyprivacy", true)) Cat.TRK else Cat.ADS
            parse(line, category)?.let(compiled::add)
        }
        rules.set(compiled)
        loaded = true
    }

    fun isLoaded() = loaded
    fun ruleCount() = rules.get().size



    fun evaluate(ctx: RequestContext): Decision {
        val uri = try { Uri.parse(ctx.url) } catch (_: Exception) { return Decision(false) }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return Decision(false, reason = "non-http")
        val host = (uri.host ?: return Decision(false)).lowercase(Locale.ROOT).trimEnd('.')
        val page = ctx.pageHost.orEmpty().lowercase(Locale.ROOT).trimEnd('.')
        val third = page.isNotEmpty() && registrable(host) != registrable(page)
        var normal: Rule? = null
        var important: Rule? = null
        var exception: Rule? = null
        for (r in rules.get()) {
            if (!r.regex.containsMatchIn(ctx.url)) continue
            if (r.includedTypes.isNotEmpty() && ctx.resourceType !in r.includedTypes) continue
            if (ctx.resourceType in r.excludedTypes) continue
            if (r.thirdParty != null && r.thirdParty != third) continue
            if (r.includedDomains.isNotEmpty() && r.includedDomains.none { domainMatches(page, it) }) continue
            if (r.excludedDomains.any { domainMatches(page, it) }) continue
            when {
                r.exception -> exception = exception ?: r
                r.important -> important = important ?: r
                else -> normal = normal ?: r
            }
        }
        if (important != null) return Decision(true, rule = important!!.raw, reason = "important-rule", category = important!!.category)
        if (exception != null) return Decision(false, exception = true, rule = exception!!.raw, reason = "exception-rule")
        if (normal != null) return Decision(true, rule = normal!!.raw, reason = "network-rule", category = normal!!.category)
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
        // These options transform responses or require scriptlet execution; do not pretend to support them.
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
            "xhr" to ResourceType.XMLHTTPREQUEST, "subdocument" to ResourceType.SUBDOCUMENT, "frame" to ResourceType.SUBDOCUMENT, "font" to ResourceType.FONT, "media" to ResourceType.MEDIA,
            "other" to ResourceType.OTHER
        )
        for (o in opts) {
            when {
                o == "third-party" || o == "3p" -> third = true
                o == "~third-party" || o == "~3p" || o == "1p" -> third = false
                o == "match-case" -> matchCase = true
                o == "important" -> important = true
                o.startsWith("domain=") -> {
                    for (d0 in o.substringAfter('=').split('|')) {
                        val neg = d0.startsWith('~'); val d = d0.removePrefix("~").lowercase(Locale.ROOT).trim('.')
                        if (!validDomain(d)) continue
                        if (neg) excludeDomains.add(d) else includeDomains.add(d)
                    }
                }
                o in typeMap -> included.add(typeMap.getValue(o))
                o.startsWith('~') && o.drop(1) in typeMap -> excluded.add(typeMap.getValue(o.drop(1)))
                o in setOf("popup", "webrtc", "genericblock", "generichide", "elemhide", "specifichide", "all") -> Unit
                else -> return null // Unknown modifier: safer to ignore than apply incorrectly.
            }
        }
        val regex = patternToRegex(pattern, matchCase) ?: return null
        return Rule(line, regex, exception, category, included, excluded, third, includeDomains, excludeDomains, matchCase, important)
    }

    private fun patternToRegex(pattern: String, matchCase: Boolean): Regex? {
        if (pattern.length > 2048) return null
        val domainAnchor = pattern.startsWith("||")
        val startAnchor = !domainAnchor && pattern.startsWith('|')
        val endAnchor = pattern.endsWith('|') && pattern.length > 1
        val from = if (domainAnchor) 2 else if (startAnchor) 1 else 0
        val until = if (endAnchor) pattern.length - 1 else pattern.length
        val out = StringBuilder(if (domainAnchor || startAnchor) "^" else ".*")
        if (domainAnchor) out.append("[a-zA-Z][a-zA-Z0-9+.-]*://(?:[^/?#]*\\.)?")
        for (i in from until until) {
            val c = pattern[i]
            when (c) {
                '*' -> out.append(".*")
                '^' -> out.append("(?:[^a-zA-Z0-9_.%-]|$)")
                '|' -> out.append("\\|")
                else -> { if ("\\.[]{}()+-?$".contains(c)) out.append('\\'); out.append(c) }
            }
        }
        if (endAnchor) out.append('$')
        return try { Regex(out.toString(), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)) } catch (_: Exception) { null }
    }

    private fun validDomain(d: String) = d.length in 1..253 && d.split('.').all { it.isNotEmpty() && it.length <= 63 && it.first().isLetterOrDigit() && it.last().isLetterOrDigit() && it.all { c -> c.isLetterOrDigit() || c == '-' } }
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
        val path = Uri.parse(url).lastPathSegment.orEmpty().lowercase(Locale.ROOT)
        return when {
            path.endsWith(".js") || path.endsWith(".mjs") -> ResourceType.SCRIPT
            path.endsWith(".css") -> ResourceType.STYLESHEET
            path.matches(Regex(".*\\.(png|jpe?g|gif|webp|svg|ico|avif)$")) -> ResourceType.IMAGE
            path.matches(Regex(".*\\.(woff2?|ttf|otf|eot)$")) -> ResourceType.FONT
            path.matches(Regex(".*\\.(mp4|webm|mp3|m4a|ogg|m3u8|mpd)$")) -> ResourceType.MEDIA
            else -> ResourceType.XMLHTTPREQUEST
        }
    }
}
