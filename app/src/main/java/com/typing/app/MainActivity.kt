package com.typing.app

import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)

        // Configure WebView settings for typing practice
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = true
            allowContentAccess = true
            defaultTextEncodingName = "utf-8"
        }

        // Enable JavaScript console logging for debugging
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: String?, lineNumber: Int, sourceId: String?): Boolean {
                if (message != null) {
                    android.util.Log.d("TypingApp", "Console [$lineNumber]: $message")
                }
                return true
            }
        }

        // Handle page navigation within WebView
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                // Keep all links within the WebView
                return false
            }
        }

        // Load the typing app HTML from assets
        webView.loadUrl("file:///android_asset/typing_app.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        // Prevent memory leak: properly destroy WebView
        webView.apply {
            (parent as? android.view.ViewGroup)?.removeView(this)
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
