# WebView JavaScript bridges are accessed by name from JavaScript.
-keepclassmembers class com.matrixco.browser.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Tink (via security-crypto) references compile-time-only annotations that are
# absent at runtime; safe to ignore for R8.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
