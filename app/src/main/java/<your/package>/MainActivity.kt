package com.objectiveliberty.litegpt

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnCustomTab: Button
    private lateinit var btnWebView: Button
    private lateinit var btnReload: Button

    // Landing page. You can also set this to "https://chatgpt.com/?oai-dm=1" etc if desired.
    private val HOME_URL = "https://chatgpt.com"

    /**
     * WebView allowlist:
     * Keep this conservative. Add domains only if you discover ChatGPT needs them.
     *
     * Notes:
     * - "chatgpt.com" is the main app
     * - "openai.com" and "auth.openai.com" can appear during auth flows
     * - static/cdn hosts may be used for assets
     */
    private val ALLOWED_HOSTS = setOf(
        "chatgpt.com",
        "www.chatgpt.com",
        "openai.com",
        "www.openai.com",
        "auth.openai.com",
        "cdn.oaistatic.com",
        "oaistatic.com"
    )

    private fun hostAllowed(url: String): Boolean {
        return try {
            val u = Uri.parse(url)
            val host = (u.host ?: "").lowercase()
            host in ALLOWED_HOSTS || ALLOWED_HOSTS.any { host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Expects res/layout/activity_main.xml with:
        // btnCustomTab, btnWebView, btnReload, webView
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        btnCustomTab = findViewById(R.id.btnCustomTab)
        btnWebView = findViewById(R.id.btnWebView)
        btnReload = findViewById(R.id.btnReload)

        setupWebView()

        btnCustomTab.setOnClickListener { openInCustomTab(HOME_URL) }
        btnWebView.setOnClickListener { openInWebView(HOME_URL) }
        btnReload.setOnClickListener { webView.reload() }

        // Default: Custom Tabs (Brave/Chromium) for best login compatibility
        openInCustomTab(HOME_URL)
    }

    private fun openInCustomTab(url: String) {
        // Optionally hide the embedded WebView when using Custom Tabs
        webView.visibility = View.GONE

        val cti = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        cti.launchUrl(this, Uri.parse(url))
    }

    private fun openInWebView(url: String) {
        // Show the embedded WebView and load the URL (if allowed)
        webView.visibility = View.VISIBLE
        if (hostAllowed(url)) {
            webView.loadUrl(url)
        } else {
            Toast.makeText(this, "Blocked: external domain", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s: WebSettings = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true

        // Safer defaults
        s.allowFileAccess = false
        s.allowContentAccess = true
        s.mediaPlaybackRequiresUserGesture = true

        // Prevent multi-window / new-tab escapes
        s.setSupportMultipleWindows(false)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // Keep only allowlisted domains inside WebView
                if (!hostAllowed(url)) {
                    Toast.makeText(this@MainActivity, "Blocked external link", Toast.LENGTH_SHORT).show()
                    return true // block
                }
                return false // allow WebView to load
            }

            // Backward compatibility (older Android)
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val u = url ?: return false
                if (!hostAllowed(u)) {
                    Toast.makeText(this@MainActivity, "Blocked external link", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // If a link tries to open a new window (target=_blank), keep it in the same WebView
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = view
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    override fun onBackPressed() {
        // If WebView is visible, navigate its history first
        if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
