# MaTRIX Co — Gaming Browser 4.5.5

متصفح Android مبني على WebView مع واجهة GX داكنة، إدارة تبويبات، أدوات ألعاب، وحماية تصفح.

## البناء

المتطلبات: JDK 17 أو أحدث، Android SDK Platform 35، Build Tools حديثة، واتصال إنترنت لأول تنزيل للاعتماديات.

```bash
chmod +x gradlew build-universal-release.sh
./build-universal-release.sh
```

السكريبت يشغّل `lintRelease` أولًا ثم يبني Release مع استثناء `lintVitalRelease` فقط، ويستخدم AAPT2 المحلي المتاح في AndroidIDE إن وُجد. يحفظ التقرير في `~/matrix-reports/`. الناتج: `app/build/outputs/apk/release/app-release-unsigned.apk`.

يتم توقيع Release تلقائيًا عند توفير المتغيرات `MX_KEYSTORE`, `MX_STORE_PASS`, `MX_KEY_ALIAS`, `MX_KEY_PASS`؛ وإلا يكون APK غير موقّع ولا يمكن تثبيته كتحديث فوق نسخة موقعة بمفتاح آخر.

## 4.5.5 — Universal Compatibility & Safer Navigation

- Release metadata: `versionName = 4.5.5`, `versionCode = 455`.
- Universal Android package: no ABI filters or ABI splits are configured; Android selects compatible native components automatically. The current project contains no packaged `.so` native libraries.
- HTTPS upgrade failure no longer silently falls back to unencrypted HTTP or permanently marks a host as legacy.
- The browser now asks before trying HTTP after an HTTPS-upgrade failure, explains the risk, and offers one-time or remembered per-site permission.
- HTTP navigation is allowed for a general-purpose browser; WebView mixed content remains blocked by the browser settings.
- The in-app updater now refuses to install an update unless GitHub provides a valid SHA-256 digest and the downloaded APK matches it.
- Release lint analysis is enabled. The supplied build helper runs `lintRelease` and excludes only `lintVitalRelease` from `assembleRelease`.
- Added `.gitignore` rules for Gradle/build outputs and local signing material; added the referenced ProGuard rules file.
- Build and physical-device verification must still be completed in AndroidIDE/Termux before calling this release production-verified.

## 4.2.0 — Privacy, Security & Gaming Consolidation

- Fingerprint protection now uses per-document deterministic perturbation for Canvas and audio readouts. Repeated reads in the same document remain consistent instead of receiving fresh random noise on every call.
- Removed fixed 412×915 screen-dimension spoofing. Real screen geometry and color depth are retained to avoid contradictions with viewport dimensions and reduce game/layout compatibility problems.
- Release builds disable WebView remote debugging; debug builds retain it for development diagnostics.
- Consolidated and retained the 4.1.x protections: no JavaScript-to-native bridge, native request filtering, third-party-cookie preference, mixed-content restrictions, Safe Browsing preference, external-scheme restrictions, and safe download handling.
- Retained game-mode renderer prioritization, background-tab sleeping, and renderer recovery. These are existing capabilities, not claims of a new FPS boost.
- Updated version metadata to `4.2.0` / `versionCode = 420`.

### Scope and limitations
- WebRTC behavior is not forcibly disabled because that can break game voice chat and video calls. WebView does not provide a universal in-app DNS leak guarantee; use Android Private DNS or a trusted VPN for device-level DNS/IP protection.
- Fingerprint defenses reduce some exposed signals; they do not make the browser anonymous or guarantee a unique/undetectable fingerprint.
- Static checks do not replace Android WebView testing on a physical device. Gradle build success must be verified separately.

## 4.1.3 — JavaScript Bridge Security

- إزالة `addJavascriptInterface` وواجهة `__mxb` بالكامل؛ لم تعد صفحات الويب أو الإطارات التابعة لها تمتلك نقطة دخول JavaScript إلى كود Android.
- إزالة `@JavascriptInterface` و`ShieldBridge` بعد التأكد أن دالة الجسر كانت مستخدمة فقط لفحص طلبات الشبكة.
- الإبقاء على فحص وحجب الطلبات في `WebViewClient.shouldInterceptRequest`، بما يشمل موارد الصفحة وطلبات `fetch` و`XMLHttpRequest` التي تمر عبر طبقة تحميل WebView.
- إزالة أغلفة JavaScript القديمة لـ fetch/XHR/Beacon التي كانت تعتمد على الجسر، لتجنب إيهام الصفحة بأن هناك فحصًا إضافيًا غير موجود.
- الإبقاء على إدارة النوافذ المنبثقة مشروطة بإيماءة مستخدم، وعلى رفض أذونات الكاميرا والميكروفون والموقع تلقائيًا.
- تحديث الإصدار إلى `4.1.3` ورقم البناء `413`.

## 4.1.2 — Isolated Timer Engine

- فصل محرك تسريع المؤقتات عن سكريبتات الحماية من البصمة.
- تثبيت محرك الوقت بشكل مستقل عند بداية المستند عبر WebView DOCUMENT_START_SCRIPT عند توفره.
- إضافة مسار حقن احتياطي متكرر بأمان لمزودي WebView الذين لا يدعمون Document Start Scripts.
- جعل تسريع الوقت معتمدًا على واجهة `__mxTimer` مستقلة، ولا يعمل إلا عند تفعيل وضع التخطي.
- الإبقاء على `Date` و`performance.now` متزامنين مع ساعة افتراضية، مع استخدام ساعة حقيقية لانتهاء مدة Turbo.
- تحديد حد أقصى للسرعة 20x لمنع القيم غير المنضبطة.
- تحديث الإصدار إلى `4.1.2` ورقم البناء `412`.

## 4.1.1 — Renderer Recovery

- استبدال WebView المتعطل عند خروج Renderer بدل إغلاق التبويب.
- محاولة استعادة آخر عنوان قابل للتصفح، مع تأجيل استعادة التبويبات الخلفية حتى اختيارها.
- الحفاظ على إعدادات WebView والعميل عبر إعادة استخدام مسار `configure`؛ تمت إزالة جسر JavaScript لاحقًا في 4.1.3.
- ترقية رقم الإصدار إلى `4.1.1` ورقم البناء `411`.

## 4.1.0 — Core Stability (initial maintenance release)\n\n- ترقية AudioService إلى Foreground Service مبكرًا من `onCreate()` لتفادي انتهاء مهلة بدء الخدمة قبل استدعاء `startForeground()`.\n- إضافة قناة إشعارات ثابتة وإشعار تشغيل منخفض الأهمية، مع تحديد نوع الخدمة `mediaPlayback` على Android 10 وما بعده.\n- إيقاف الخدمة بأمان عند فشل الترقية إلى Foreground بدل ترك خدمة بدأت دون حالة Foreground.\n- تسجيل فشل بدء خدمة الصوت في Logcat، وإيقاف معالجة WebView مؤقتًا كمسار احتياطي عند فشل تشغيل الخدمة.\n- توحيد رقم الإصدار إلى `4.1.0` ورقم البناء `410` مع الحفاظ على `applicationId`.\n\n## 4.0.0 — GX Rebuild

- إعادة بناء صفحة البداية بهوية MaTrIX GX: Hero search، Quick Access، Gaming Library، GX Control Center، إحصائيات الدرع، GX Corner، وآخر المواقع.
- إعادة تصميم Security Center ليعرض حالة Ad/Tracker Shield وIronGuard وAnti-Fingerprinting وSafe Browsing، مع نشاط الحجب وأعلى النطاقات المحجوبة.
- توحيد ألوان Tab Manager والقائمة الرئيسية وشرائط المتصفح على لوحة GX Neon مع إبقاء تمييز الجلسة الخاصة.
- تصميم Responsive للموبايل والشاشات الأوسع، مع تقليل الحركة عند تفعيل إعداد Reduce Motion في النظام.
- نظام ألوان GX Neon: خلفية فحمية، بنفسجي، ولمسات ليمونية، مع الحفاظ على إمكانية اختيار الثيمات والألوان من GX Mods.
- تحديث شريط العنوان والشريط السفلي والقائمة السريعة لتتوافق مع الهوية الجديدة.
- إضافة Performance Lab يعرض PSS للعملية، ذاكرة النظام المتاحة، عدد التبويبات النشطة/النائمة، والطلبات المحجوبة في التبويب الحالي.
- أزرار مباشرة لتنويم التبويبات الخلفية وتنظيف الكاش من Performance Lab.
- تخزين مؤقت لنتيجة حساب مساحة التطبيق لمدة 15 ثانية لتجنب إعادة مسح مجلد WebView والكاش بشكل متكرر عند فتح الصفحة الرئيسية؛ يتم إبطال التخزين المؤقت بعد تنظيف الكاش.
- تسمية قياس الشبكة في HUD باسم TCP بدل Ping؛ القياس الحالي هو زمن إنشاء اتصال TCP إلى المضيف على المنفذ 443 وليس Ping ICMP أو زمن استجابة خادم اللعبة.
- ربط بطاقات Gaming Center وPerformance Lab وSecurity Center وTab Manager بإجراءات المتصفح الفعلية.
- استعادة ملف `app/build.gradle.kts` المفقود، وتحديد namespace/applicationId/SDK/version/dependencies.
- استخدام Gradle 8.9 المتوافق مع Android Gradle Plugin 8.7.3 وإزالة مسار AAPT2 المحلي الخاص بجهاز واحد.

## الميزات الموروثة التي يجب الحفاظ عليها

- AdBlocker وFilterEngine وقوائم hosts وEasyList وما يتصل بها.
- IronGuard وSpyGuard وAdultGuard وتقارير الحماية.
- PIN/Biometric lock، النسخ الاحتياطي، التحديث، التنزيلات، الصفحات Offline، المفضلة والسجل.
- التصفح الخاص، إدارة التبويبات، الإيماءات، التبويبات النائمة، Picture-in-Picture، واختيار سطح المكتب.
- حماية البصمة عبر `androidx.webkit` عند دعم WebView لها، وخيارات البروكسي وDNS.

## ملاحظات مهمة

- Performance Lab يعرض مؤشرات Android المتاحة للتطبيق؛ لا يدّعي التحكم في CPU أو قياس FPS حقيقيًا من داخل WebView.
- مؤشر الشبكة الموجود في وضع الألعاب ليس قياسًا مضمونًا لزمن استجابة خادم اللعبة؛ قد يكون اختبار اتصال TCP إلى المضيف.
- ميزات الحماية لا تعتبر اختبار اختراق أو ضمانًا مطلقًا. يجب اختبارها على مواقع وأجهزة فعلية.
- لم يتم اعتماد APK من هذا التعديل حتى ينجح بناء Gradle واختبار التشغيل على جهاز Android.
- تم الإبقاء على `applicationId` الأصلي `com.matrixco.browser` للمحافظة على مسار التحديث. لكن تثبيت APK فوق نسخة سابقة يتطلب أن يكون توقيعه بنفس مفتاح النسخة المثبتة؛ خذ نسخة احتياطية قبل التحديث.

## البنية

- Kotlin + Android Views + Android WebView
- `androidx.webkit:webkit:1.12.1`
- `minSdk 23`, `targetSdk 28`, `compileSdk 35`
- Java/Kotlin JVM target 17


## v4.1.4 — Safe Navigation & Downloads
- Restrict external URI handlers to explicit user-gesture `mailto`, `tel`, and `sms` links.
- Block arbitrary `intent://` and unknown external schemes instead of dispatching untrusted intents.
- Accept downloads only from valid HTTP(S) URLs without embedded user-info credentials.
- Sanitize downloaded filenames against path separators, control characters, and reserved filename characters.
- Send a Referer only for same-origin downloads, reduced to the origin root to avoid leaking page paths and query strings.


## v4.2.1 — BuildConfig Generation Fix
- Explicitly enable Android BuildConfig generation for AGP 8+.
- Fixes `Unresolved reference: BuildConfig` in `MxApp.kt`.
- No runtime behavior or security policy changes.


## v4.3.0 — Privacy Hardening & Smart URL
- Stabilized anti-fingerprinting perturbation with a native per-tab, origin-derived seed across reloads; the seed is not stored in page-readable Web Storage.
- Expanded AudioAnalyser protection to frequency and time-domain snapshot APIs.
- Removed mutation of live AudioBuffer channel data to avoid altering real playback.
- Added safe address-bar protocol repair for common missing/typo HTTPS prefixes.
- Recognizes explicit domains and adds HTTPS; ambiguous bare words or missing TLDs are sent to the configured search engine rather than guessing `.com`/`.net`.
- WebRTC local-candidate exposure remains a known limitation requiring dedicated compatibility testing; no claim of complete IP-leak prevention is made.


## v4.3.1 — Canvas interceptor correction
- Removed the second randomized Canvas mutation from the SpyGuard script.
- Kept a single deterministic Canvas perturbation layer in the document-start HARDEN script.
- Restricted Canvas perturbation to small, detached canvases commonly used for fingerprint probes; connected canvases used by games and page rendering are not modified.
- This reduces double-processing and rendering compatibility risks. It does not by itself prove fingerprint resistance; verify with repeated Canvas tests and image export checks.


## v4.3.2 — Canvas export coverage
- Canvas export protection now covers small canvases whether attached to the DOM or detached.
- `toDataURL` and `toBlob` perturb a temporary copy only; the original page canvas is not modified.
- `getImageData` remains unmodified for connected canvases to reduce the risk of affecting game rendering and page logic.
- This is a targeted compatibility/privacy improvement, not a claim of complete fingerprint protection. Verify exported PNG validity and repeatability on-device.


## v4.3.3 — Canvas pipeline and injection diagnostics
- Canvas `getImageData()` protection now covers small connected and detached canvases. It mutates only the returned ImageData buffer, not the source canvas pixels.
- `toDataURL()` and `toBlob()` continue to export a deterministic, perturbed temporary copy for small canvases.
- The hardening layer exposes `window.__mxH` and `window.__mxCanvasProtection` version markers for runtime diagnostics.
- Document-start registration now exposes `window.__mxDocumentStart` and logs whether the WebView supports true document-start injection.
- Runtime fallback installs hardening before the main script layer. This fallback is still late relative to page scripts; true first-script protection requires DOCUMENT_START_SCRIPT support.


## v4.4.0 — Automatic Threat Feed Updates
- Added AndroidX WorkManager periodic refresh for the existing remote domain/threat feeds.
- Refresh is scheduled every 6 hours with a connected-network constraint; Android may defer execution under Doze/battery optimizations.
- App foreground startup retains the existing stale-feed check, now using a 6-hour freshness window.
- Existing RuleUpdater validation and atomic database replacement behavior are retained; failed/undersized updates do not replace the last known-good database.
- This release updates domain/URL threat intelligence feeds already supported by RuleUpdater. It does not remotely patch Chromium/WebView vulnerabilities or dynamically add new Canvas/WebGL/Audio/WebRTC countermeasures; those require reviewed application code updates.


## v4.5.0 — GX Animated Update Experience
- Redesigned the in-app update dialog with a compact neon purple/cyan gaming layout.
- Added the supplied animated dragon GIF as a full-bleed dialog background with a dark readability scrim.
- Added Glide 4.16.0 for GIF decoding and lifecycle cleanup when the dialog closes.
- Added resumable APK downloads using HTTP Range / If-Range validators, a persistent `.part` file, and ETag/Last-Modified metadata.
- Resume depends on server byte-range support; if the host ignores Range or changes the file, the updater safely restarts rather than appending invalid bytes.
