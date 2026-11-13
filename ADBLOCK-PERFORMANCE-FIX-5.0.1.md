# MaTRIX Co 5.0.1 — AdBlock performance hotfix

## Findings in 5.0.0
- `NetworkRuleEngine.evaluate()` iterated every compiled network rule for every WebView resource request.
- The engine compiled the entire stored rules collection synchronously during `MainActivity.onCreate()`.
- The update path ran `VACUUM` immediately after replacing rules, causing avoidable CPU and storage I/O spikes.

## Changes
- Replaced the full-scan rule loop with hostname-suffix buckets for `||host` rules.
- Bounded generic URL-pattern rules to 1,200 and host-anchored compiled rules to 28,000 to bound runtime memory and per-request work.
- Moved initial rule loading/compilation off the Android main thread.
- Removed immediate database `VACUUM` from the filter-update path.
- Preserved the existing lightweight host/domain blocking path when the network engine is still loading.

## Compatibility and limitations
- This is a performance-oriented ABP subset, not full uBlock Origin compatibility.
- Some generic URL rules beyond the runtime cap and unsupported transformation/scriptlet rules are not executed.
- The project ZIP is source code; a successful Android Gradle build and on-device profiling are still required before calling the fix verified.
