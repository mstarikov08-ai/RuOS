package com.ruos.mail

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class MailActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var titleView: TextView

    companion object {
        private const val YANDEX_MAIL_URL = "https://mail.yandex.ru"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        setContentView(root)

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0F0F0F"))
            setPadding(0, statusBarHeight(), 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }

        val backBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            val s = dp(44)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setOnClickListener {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        }

        titleView = TextView(this).apply {
            text = "Почта"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }

        val composeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.parseColor("#D94F3D"))
            val s = dp(44)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setOnClickListener {
                webView.loadUrl("https://mail.yandex.ru/#compose")
            }
        }

        topBar.addView(backBtn)
        topBar.addView(titleView)
        topBar.addView(composeBtn)

        val topBarParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            gravity = Gravity.TOP
        }
        root.addView(topBar, topBarParams)

        // WebView
        webView = buildWebView()
        val wvParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            topMargin = dp(44) + statusBarHeight()
        }
        root.addView(webView, wvParams)

        // Progress bar
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D94F3D"))
        }
        val pbParams = FrameLayout.LayoutParams(MATCH_PARENT, dp(3)).apply {
            gravity = Gravity.TOP
            topMargin = dp(44) + statusBarHeight()
        }
        root.addView(progress, pbParams)

        // Handle mailto: intent
        val startUrl = when {
            intent?.action == Intent.ACTION_VIEW && intent.data?.scheme == "mailto" -> {
                buildMailtoUrl(intent.data!!)
            }
            intent?.action == Intent.ACTION_SEND -> {
                val address = intent.getStringExtra(Intent.EXTRA_EMAIL) ?: ""
                val subject = Uri.encode(intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "")
                "https://mail.yandex.ru/#compose?to=$address&subject=$subject"
            }
            else -> YANDEX_MAIL_URL
        }
        webView.loadUrl(startUrl)
    }

    @SuppressLint("SetJavaScriptEnabled", "MixedContentUsage")
    private fun buildWebView(): WebView {
        return WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 RuOSMail/1.0"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    // Keep Yandex Mail and OAuth within WebView
                    if (url.contains("yandex.ru") || url.contains("yandex.com")) return false
                    // Open external links in browser
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {}
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    // Inject dark mode CSS for Yandex Mail
                    view.evaluateJavascript("""
                        (function() {
                            var existing = document.getElementById('ruos-dark-inject');
                            if (existing) return;
                            var s = document.createElement('style');
                            s.id = 'ruos-dark-inject';
                            s.textContent = 'body{background:#000!important;color:#fff!important}' +
                                '.b-page{background:#000!important}' +
                                '.mail-App{background:#000!important}';
                            document.head && document.head.appendChild(s);
                        })();
                    """, null)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
                }

                override fun onReceivedTitle(view: WebView, title: String) {
                    // Keep header as "Почта" — don't show page title
                }
            }
        }
    }

    private fun buildMailtoUrl(uri: Uri): String {
        val to = Uri.encode(uri.schemeSpecificPart?.split("?")?.firstOrNull() ?: "")
        val query = uri.query ?: ""
        val subject = Uri.encode(Regex("subject=([^&]*)").find(query)?.groupValues?.get(1) ?: "")
        val body = Uri.encode(Regex("body=([^&]*)").find(query)?.groupValues?.get(1) ?: "")
        return "https://mail.yandex.ru/#compose?to=$to&subject=$subject&body=$body"
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }
}
