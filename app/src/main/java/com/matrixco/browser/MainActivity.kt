package com.matrixco.browser

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.net.Uri
import java.io.ByteArrayInputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var blockedCount = 0

    private val backgroundColor = Color.rgb(12, 12, 16)
    private val accentColor = Color.rgb(235, 35, 45)
    private val textColor = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        val header = TextView(this).apply {
            text = "♛ MaTrIX Co • GAMING BROWSER"
            textSize = 18f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
        }

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            progressTintList =
                android.content.res.ColorStateList.valueOf(accentColor)
        }

        root.addView(
            progressBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                3
            )
        )

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 8)
        }

        addressBar = EditText(this).apply {
            hint = "ابحث أو اكتب عنوان الموقع"
            textSize = 14f
            setSingleLine(true)
            setTextColor(textColor)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(35, 35, 42))
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(12, 8, 12, 8)
        }

        addressBar.setOnEditorActionListener { _, actionId, event ->
            val goPressed =
                actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER

            if (goPressed) {
                openAddress(addressBar.text.toString())
                true
            } else {
                false
            }
        }

        addressRow.addView(
            addressBar,
            LinearLayout.LayoutParams(0, 48, 1f)
        )

        val goButton = Button(this).apply {
            text = "GO"
            setTextColor(textColor)
            setBackgroundColor(accentColor)
            setOnClickListener {
                openAddress(addressBar.text.toString())
            }
        }

        addressRow.addView(
            goButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                48
            )
        )

        root.addView(addressRow)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addControl(controls, "‹") {
            if (webView.canGoBack()) webView.goBack()
        }

        addControl(controls, "›") {
            if (webView.canGoForward()) webView.goForward()
        }

        addControl(controls, "⌂") {
            openAddress(HOME_URL)
        }

        addControl(controls, "↻") {
            webView.reload()
        }

        root.addView(controls)

        statusText = TextView(this).apply {
            text = "MaTrIX Shield • الحجب جاهز"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(10, 4, 10, 4)
        }

        root.addView(statusText)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.databaseEnabled = true

            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {

                    val url = request?.url?.toString()
                        ?: return super.shouldInterceptRequest(view, request)

                    if (AdBlocker.shouldBlock(url)) {
                        synchronized(this@MainActivity) {
                            blockedCount++
                        }
                        return emptyResponse()
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                @Suppress("DEPRECATION")
                override fun shouldInterceptRequest(
                    view: WebView?,
                    url: String?
                ): WebResourceResponse? {

                    val value = url
                        ?: return super.shouldInterceptRequest(view, url)

                    if (AdBlocker.shouldBlock(value)) {
                        synchronized(this@MainActivity) {
                            blockedCount++
                        }
                        return emptyResponse()
                    }

                    return super.shouldInterceptRequest(view, value)
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    addressBar.setText(url.orEmpty())
                    statusText.text = "جاري تحميل الصفحة..."
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    addressBar.setText(url.orEmpty())
                    statusText.text =
                        "MaTrIX Shield • المحجوب: $blockedCount"
                    super.onPageFinished(view, url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false

                    return uri.scheme != "http" &&
                        uri.scheme != "https"
                }
            }

            webChromeClient = object : WebChromeClient() {

                override fun onProgressChanged(
                    view: WebView?,
                    newProgress: Int
                ) {
                    progressBar.progress = newProgress
                    progressBar.visibility =
                        if (newProgress >= 100) View.GONE
                        else View.VISIBLE
                }
            }
        }

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        if (savedInstanceState != null) {
            val restored = webView.restoreState(savedInstanceState)
            if (restored == null) {
                openAddress(HOME_URL)
            }
        } else {
            openAddress(HOME_URL)
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

    private fun addControl(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            textSize = 18f
            setTextColor(textColor)
            setBackgroundColor(backgroundColor)
            setOnClickListener { action() }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 48, 1f)
        )
    }

    private fun openAddress(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return

        val url = when {
            value.startsWith("https://", true) ||
            value.startsWith("http://", true) -> value

            value.contains(" ") ||
            !value.contains(".") ->
                "https://www.google.com/search?q=" + Uri.encode(value)

            else -> "https://$value"
        }

        webView.loadUrl(url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL =
            "https://browser.playconqueronline.com/?lang=ar"
    }
}
