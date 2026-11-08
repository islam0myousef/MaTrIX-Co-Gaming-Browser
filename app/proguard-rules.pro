# WebView JavaScript bridges are accessed by name from JavaScript.
-keepclassmembers class com.matrixco.browser.** {
    @android.webkit.JavascriptInterface <methods>;
}
