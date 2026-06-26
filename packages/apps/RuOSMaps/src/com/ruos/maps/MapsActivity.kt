package com.ruos.maps

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
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

class MapsActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null

    companion object {
        private const val LOC_PERM = 1001
        private const val YANDEX_MAPS_URL = "https://yandex.ru/maps/"
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
            setOnClickListener { finish() }
        }

        val title = TextView(this).apply {
            text = "Карты"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }

        val locBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_mylocation)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.parseColor("#D94F3D"))
            val s = dp(44)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setOnClickListener { requestLocationAndLoad() }
        }

        topBar.addView(backBtn)
        topBar.addView(title)
        topBar.addView(locBtn)

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

        // Load from intent or default
        val geoUri = intent?.data
        val startUrl = if (geoUri != null && geoUri.scheme == "geo") {
            buildYandexGeoUrl(geoUri)
        } else if (geoUri != null && (geoUri.host?.contains("yandex") == true || geoUri.host?.contains("maps") == true)) {
            geoUri.toString()
        } else {
            YANDEX_MAPS_URL
        }
        webView.loadUrl(startUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        return WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                setGeolocationEnabled(true)
                userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 RuOSMaps/1.0"
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith("intent://") || url.startsWith("market://")) return true
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String, callback: GeolocationPermissions.Callback
                ) {
                    pendingGeoCallback = callback
                    pendingGeoOrigin = origin
                    requestLocationPermission()
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    request.grant(request.resources)
                }
            }
        }
    }

    private fun buildYandexGeoUrl(uri: Uri): String {
        val ssp = uri.schemeSpecificPart ?: return YANDEX_MAPS_URL
        val coords = ssp.split("?")[0].split(",")
        return if (coords.size >= 2) {
            val lat = coords[0].trim()
            val lon = coords[1].trim()
            "https://yandex.ru/maps/?ll=$lon,$lat&z=15&pt=$lon,$lat"
        } else {
            YANDEX_MAPS_URL
        }
    }

    private fun requestLocationAndLoad() {
        requestLocationPermission()
        webView.loadUrl("javascript:if(navigator.geolocation){navigator.geolocation.getCurrentPosition(function(p){window.location='https://yandex.ru/maps/?ll='+p.coords.longitude+','+p.coords.latitude+'&z=15';})}")
    }

    private fun requestLocationPermission() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION),
                LOC_PERM
            )
        } else {
            pendingGeoCallback?.invoke(pendingGeoOrigin, true, true)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == LOC_PERM) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
        }
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
