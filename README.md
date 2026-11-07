# MaTRIX Co — Gaming Browser 4.1.1

متصفح Android مبني على WebView مع واجهة GX داكنة، إدارة تبويبات، أدوات ألعاب، وحماية تصفح.

## البناء

المتطلبات: JDK 17 أو أحدث، Android SDK Platform 35، Build Tools حديثة، واتصال إنترنت لأول تنزيل للاعتماديات.

```bash
chmod +x gradlew
./gradlew assembleDebug
```

الناتج: `app/build/outputs/apk/debug/app-debug.apk`.

لإصدار Release: `./gradlew assembleRelease`. يتم توقيعه تلقائيًا عند توفير المتغيرات `MX_KEYSTORE`, `MX_STORE_PASS`, `MX_KEY_ALIAS`, `MX_KEY_PASS`؛ وإذا لم تتوفر ينتج APK غير موقّع.

## 4.1.1 — Renderer Recovery

- استبدال WebView المتعطل عند خروج Renderer بدل إغلاق التبويب.
- محاولة استعادة آخر عنوان قابل للتصفح، مع تأجيل استعادة التبويبات الخلفية حتى اختيارها.
- الحفاظ على إعدادات WebView والجسر والعميل عبر إعادة استخدام مسار `configure`.
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
