# MaTRIX Co AdBlock rebuild — 5.0.0

## What changed
- Added an independent `NetworkRuleEngine` for a bounded ABP-compatible network-rule subset.
- Network rules are stored separately from hashed host rules in SQLite (`network_rules`, schema v6).
- Core downloaded ADBLOCK feeds and optional filter lists now preserve raw network rules, including exceptions and supported modifiers.
- Matching supports URL wildcards, `||` domain anchors, `|` start/end anchors, `^` separators, `@@` exceptions, `$important`, resource types, `third-party`/`3p`, `~third-party`/`1p`, and `domain=` include/exclude conditions.
- Main-frame document requests are evaluated by the network rule engine; subresource requests use inferred resource type from Accept/path.
- Rules from disabled sources are excluded when the engine snapshot is rebuilt.
- Failed/unsupported transformation rules (`redirect`, `removeparam`, `csp`, scriptlets, unknown modifiers) are ignored rather than applied incorrectly.

## Deliberate limitations
This is not a full uBlock Origin engine. Procedural cosmetic filters, scriptlets, redirects, response rewriting, header modification, and every vendor-specific modifier are not implemented. Cosmetic CSS continues through the existing separate cosmetic path. Resource-type inference in Android WebView is heuristic because `WebResourceRequest` does not expose Chromium's internal resource type.

## Validation performed
- Compiled `NetworkRuleEngine.kt` with Kotlin/JVM using small Android `Uri` and project dependency stubs.
- Ran focused matching checks for domain anchors, exceptions, third-party + script constraints, and unanchored URL patterns.
- Full Android Gradle build was not possible in this environment: Gradle Wrapper attempted to download Gradle 8.11.1 and network DNS was unavailable (`UnknownHostException: services.gradle.org`).
