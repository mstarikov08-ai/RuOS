package com.ruos.browser

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import org.json.JSONArray

// ─────────────────────────── HELPER ──────────────────────────────────────────

private fun dp(ctx: Context, v: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

private fun roundRect(ctx: Context, color: Int, radius: Float = 12f): GradientDrawable =
    GradientDrawable().also { it.setColor(color); it.cornerRadius = dp(ctx, radius).toFloat() }

private fun colorInt(hex: String) = Color.parseColor(hex)

// ─────────────────────────── HOME PAGE HTML ──────────────────────────────────

private val HOME_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  * { box-sizing: border-box; }
  body { background:#000; color:#fff; font-family:sans-serif; text-align:center;
         margin:0; padding:40px 20px; }
  .logo { font-size:48px; font-weight:100; color:#D94F3D; margin-bottom:8px; }
  .subtitle { color:#8E8E93; font-size:14px; margin-bottom:40px; }
  .search-form { margin-bottom:40px; }
  .search-input { width:80%; padding:14px; border-radius:12px; border:none;
                  background:#1C1C1E; color:white; font-size:16px; }
  .search-input:focus { outline:none; box-shadow:0 0 0 2px #D94F3D; }
  .section-label { color:#8E8E93; font-size:12px; margin-bottom:8px; letter-spacing:0.04em; }
  .news-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; text-align:left; }
  .news-card { background:#1C1C1E; border-radius:12px; padding:12px; cursor:pointer;
               text-decoration:none; display:block; }
  .news-card:hover { background:#2C2C2E; }
  .news-source { color:#8E8E93; font-size:11px; margin-bottom:4px; }
  .news-title { font-size:13px; color:white; }
</style>
</head>
<body>
  <div class="logo">RuOS</div>
  <div class="subtitle">Браузер</div>
  <div class="search-form">
    <form action="https://yandex.ru/search/" method="get">
      <input class="search-input" name="text" placeholder="Поиск в Яндексе или адрес сайта"
             autofocus>
    </form>
  </div>
  <div class="section-label">ЧАСТО ПОСЕЩАЕМЫЕ</div>
  <div class="news-grid">
    <a class="news-card" href="https://yandex.ru">
      <div class="news-source">Яндекс</div>
      <div class="news-title">yandex.ru</div>
    </a>
    <a class="news-card" href="https://vk.com">
      <div class="news-source">ВКонтакте</div>
      <div class="news-title">vk.com</div>
    </a>
    <a class="news-card" href="https://gosuslugi.ru">
      <div class="news-source">Госуслуги</div>
      <div class="news-title">gosuslugi.ru</div>
    </a>
    <a class="news-card" href="https://mail.ru">
      <div class="news-source">Mail.ru</div>
      <div class="news-title">mail.ru</div>
    </a>
  </div>
</body>
</html>
""".trimIndent()

// ─────────────────────────── MAIN ACTIVITY ───────────────────────────────────

class BrowserActivity : android.app.Activity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageView
    private lateinit var btnForward: ImageView
    private lateinit var btnReload: ImageView
    private var isPrivate = false
    private var bookmarks: JSONArray = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = colorInt("#000000")

        loadBookmarks()
        setContentView(buildUI())

        configureWebView()
        handleIncomingIntent(intent)
    }

    // ────────────────────────── UI BUILDER ───────────────────────────────────

    private fun buildUI(): View {
        val ctx = this

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorInt("#000000"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Progress bar
        progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(colorInt("#D94F3D"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 3f)
            )
            visibility = View.GONE
        }
        root.addView(progressBar)

        // Top bar
        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorInt("#1C1C1E"))
            setPadding(dp(ctx, 8f), dp(ctx, 8f), dp(ctx, 8f), dp(ctx, 8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 52f)
            )
        }

        btnBack = makeNavIconBtn(ctx, arrowDrawable(ctx, left = true, colorInt("#8E8E93"))).also {
            it.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        }
        topBar.addView(btnBack)

        btnForward = makeNavIconBtn(ctx, arrowDrawable(ctx, left = false, colorInt("#8E8E93"))).also {
            it.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        }
        topBar.addView(btnForward)

        urlBar = EditText(ctx).apply {
            hint = "Поиск или адрес"
            setHintTextColor(colorInt("#8E8E93"))
            setTextColor(Color.WHITE)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            background = roundRect(ctx, colorInt("#2C2C2E"), 10f)
            setPadding(dp(ctx, 10f), dp(ctx, 6f), dp(ctx, 10f), dp(ctx, 6f))
            maxLines = 1
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = dp(ctx, 6f); it.marginEnd = dp(ctx, 6f)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) setText(webView.url ?: "")
                else {
                    val u = webView.url ?: ""
                    setText(extractDomain(u))
                }
            }
            setOnEditorActionListener { _, _, _ ->
                navigateTo(text.toString().trim())
                clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(windowToken, 0)
                true
            }
        }
        topBar.addView(urlBar)

        btnReload = makeNavIconBtn(ctx, refreshDrawable(ctx, colorInt("#8E8E93"))).also {
            it.setOnClickListener { webView.reload() }
        }
        topBar.addView(btnReload)

        // Private mode toggle
        val btnPrivate = makeNavBtn(ctx, "ИНК").also {
            it.textSize = 12f
            it.setOnClickListener {
                isPrivate = !isPrivate
                it.setTextColor(if (isPrivate) colorInt("#D94F3D") else colorInt("#8E8E93"))
                applyPrivateMode()
                Toast.makeText(
                    this,
                    if (isPrivate) "Приватный режим включён" else "Приватный режим выключен",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        topBar.addView(btnPrivate)

        root.addView(topBar)

        // WebView
        webView = WebView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(webView)

        // Bottom tab bar
        val bottomBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colorInt("#1C1C1E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 52f)
            )
        }

        val bottomIconBtns = listOf(
            arrowDrawable(ctx, left = true, colorInt("#8E8E93")) to { if (webView.canGoBack()) webView.goBack() },
            arrowDrawable(ctx, left = false, colorInt("#8E8E93")) to { if (webView.canGoForward()) webView.goForward() },
            shareIconDrawable(ctx, colorInt("#8E8E93")) to { shareCurrentPage() },
            tabsIconDrawable(ctx, colorInt("#8E8E93")) to { showTabsPlaceholder() }
        )
        for ((icon, action) in bottomIconBtns) {
            bottomBar.addView(makeBottomTabIconBtn(ctx, icon, action))
        }
        // Bookmarks button with canvas-drawn star icon
        val bookmarkBtn = ImageView(ctx).apply {
            setImageDrawable(starDrawable(false, colorInt("#8E8E93")))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setPadding(dp(ctx, 12f), dp(ctx, 10f), dp(ctx, 12f), dp(ctx, 10f))
            isClickable = true
            isFocusable = true
            setOnClickListener { showBookmarks() }
        }
        bottomBar.addView(bookmarkBtn)
        root.addView(bottomBar)

        return root
    }

    private fun makeNavIconBtn(ctx: Context, drawable: android.graphics.drawable.Drawable): ImageView =
        ImageView(ctx).apply {
            setImageDrawable(drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(ctx, 8f), dp(ctx, 4f), dp(ctx, 8f), dp(ctx, 4f))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 44f), LinearLayout.LayoutParams.MATCH_PARENT)
        }

    private fun makeBottomTabIconBtn(ctx: Context, drawable: android.graphics.drawable.Drawable, action: () -> Unit): ImageView =
        ImageView(ctx).apply {
            setImageDrawable(drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setPadding(dp(ctx, 12f), dp(ctx, 10f), dp(ctx, 12f), dp(ctx, 10f))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun arrowDrawable(ctx: Context, left: Boolean, color: Int): android.graphics.drawable.Drawable {
        val sz = dp(ctx, 24f)
        val bmp = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = android.graphics.Paint.Style.STROKE
            strokeWidth = sz * 0.12f; strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val path = android.graphics.Path()
        if (left) {
            path.moveTo(sz * 0.65f, sz * 0.2f); path.lineTo(sz * 0.3f, sz * 0.5f); path.lineTo(sz * 0.65f, sz * 0.8f)
        } else {
            path.moveTo(sz * 0.35f, sz * 0.2f); path.lineTo(sz * 0.7f, sz * 0.5f); path.lineTo(sz * 0.35f, sz * 0.8f)
        }
        c.drawPath(path, p)
        return android.graphics.drawable.BitmapDrawable(ctx.resources, bmp)
    }

    private fun refreshDrawable(ctx: Context, color: Int): android.graphics.drawable.Drawable {
        val sz = dp(ctx, 24f)
        val bmp = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = android.graphics.Paint.Style.STROKE
            strokeWidth = sz * 0.12f; strokeCap = android.graphics.Paint.Cap.ROUND
        }
        c.drawArc(android.graphics.RectF(sz*0.12f, sz*0.12f, sz*0.88f, sz*0.88f), -60f, 300f, false, p)
        val hp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = android.graphics.Paint.Style.FILL
        }
        val arrowPath = android.graphics.Path()
        arrowPath.moveTo(sz*0.88f, sz*0.28f); arrowPath.lineTo(sz*0.72f, sz*0.18f); arrowPath.lineTo(sz*0.78f, sz*0.42f); arrowPath.close()
        c.drawPath(arrowPath, hp)
        return android.graphics.drawable.BitmapDrawable(ctx.resources, bmp)
    }

    private fun shareIconDrawable(ctx: Context, color: Int): android.graphics.drawable.Drawable {
        val sz = dp(ctx, 24f)
        val bmp = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = android.graphics.Paint.Style.STROKE
            strokeWidth = sz * 0.1f; strokeCap = android.graphics.Paint.Cap.ROUND
        }
        c.drawLine(sz*0.5f, sz*0.85f, sz*0.5f, sz*0.3f, p)
        val ap = android.graphics.Path()
        ap.moveTo(sz*0.25f, sz*0.55f); ap.lineTo(sz*0.5f, sz*0.25f); ap.lineTo(sz*0.75f, sz*0.55f)
        c.drawPath(ap, p)
        p.style = android.graphics.Paint.Style.STROKE
        c.drawRoundRect(android.graphics.RectF(sz*0.15f, sz*0.55f, sz*0.85f, sz*0.9f), sz*0.1f, sz*0.1f, p)
        return android.graphics.drawable.BitmapDrawable(ctx.resources, bmp)
    }

    private fun tabsIconDrawable(ctx: Context, color: Int): android.graphics.drawable.Drawable {
        val sz = dp(ctx, 24f)
        val bmp = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = android.graphics.Paint.Style.STROKE
            strokeWidth = sz * 0.09f; strokeCap = android.graphics.Paint.Cap.ROUND
        }
        c.drawRoundRect(android.graphics.RectF(sz*0.3f, sz*0.1f, sz*0.9f, sz*0.7f), sz*0.1f, sz*0.1f, p)
        c.drawRoundRect(android.graphics.RectF(sz*0.1f, sz*0.3f, sz*0.7f, sz*0.9f), sz*0.1f, sz*0.1f, p)
        return android.graphics.drawable.BitmapDrawable(ctx.resources, bmp)
    }

    // ────────────────────────── WEBVIEW CONFIG ───────────────────────────────

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled   = true
            domStorageEnabled   = true
            loadWithOverviewMode = true
            useWideViewPort     = true
            setSupportZoom(true)
            builtInZoomControls  = true
            displayZoomControls  = false
            userAgentString = "RuOS Browser/1.0 (Android 14)"
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                updateNavButtons()
                val domain = extractDomain(url)
                if (!urlBar.hasFocus()) urlBar.setText(domain)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {}
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress  = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                // could update title bar if needed
            }
        }

        // Load home page
        loadHomePage()
    }

    private fun loadHomePage() {
        webView.loadDataWithBaseURL("about:blank", HOME_HTML, "text/html", "UTF-8", null)
        urlBar.setText("")
        urlBar.hint = "Поиск или адрес"
    }

    private fun applyPrivateMode() {
        if (isPrivate) {
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            CookieManager.getInstance().setAcceptCookie(false)
            webView.clearCache(true)
            webView.clearHistory()
        } else {
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
            CookieManager.getInstance().setAcceptCookie(true)
        }
    }

    // ────────────────────────── NAVIGATION ───────────────────────────────────

    private fun navigateTo(input: String) {
        val url = when {
            input.isEmpty()     -> { loadHomePage(); return }
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".")  -> "https://$input"
            else                -> "https://yandex.ru/search/?text=${Uri.encode(input)}"
        }
        webView.loadUrl(url)
        updateNavButtons()
    }

    private fun updateNavButtons() {
        btnBack.alpha = if (webView.canGoBack()) 1f else 0.35f
        btnForward.alpha = if (webView.canGoForward()) 1f else 0.35f
    }

    private fun extractDomain(url: String): String {
        return try {
            val u = Uri.parse(url)
            u.host?.removePrefix("www.") ?: url
        } catch (_: Exception) { url }
    }

    // ────────────────────────── BOOKMARKS ────────────────────────────────────

    private fun loadBookmarks() {
        val prefs = getSharedPreferences("browser_prefs", MODE_PRIVATE)
        val saved = prefs.getString("bookmarks", "[]") ?: "[]"
        try { bookmarks = JSONArray(saved) } catch (_: Exception) { bookmarks = JSONArray() }
    }

    private fun saveBookmarks() {
        getSharedPreferences("browser_prefs", MODE_PRIVATE).edit()
            .putString("bookmarks", bookmarks.toString()).apply()
    }

    private fun addBookmark() {
        val url   = webView.url   ?: return
        val title = webView.title ?: url
        val entry = org.json.JSONObject()
        entry.put("url", url); entry.put("title", title)
        bookmarks.put(entry)
        saveBookmarks()
        Toast.makeText(this, "Закладка добавлена", Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarks() {
        val ctx   = this
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorInt("#1C1C1E"))
            setPadding(dp(ctx, 16f), dp(ctx, 16f), dp(ctx, 16f), dp(ctx, 32f))
        }

        sheet.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply {
                text = "Закладки"
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(ctx).apply {
                text = "+ Добавить"
                setTextColor(colorInt("#D94F3D"))
                textSize = 14f
                setPadding(dp(ctx, 8f), 0, 0, 0)
                setOnClickListener { addBookmark() }
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(ctx, 16f) }
        })

        if (bookmarks.length() == 0) {
            sheet.addView(TextView(ctx).apply {
                text = "Закладок пока нет"
                setTextColor(colorInt("#8E8E93"))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dp(ctx, 24f), 0, dp(ctx, 24f))
            })
        } else {
            val scroll = ScrollView(ctx)
            val inner  = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            for (i in 0 until bookmarks.length()) {
                val entry = bookmarks.getJSONObject(i)
                val url   = entry.getString("url")
                val title = entry.optString("title", url)
                val row   = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity     = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(ctx, 10f), 0, dp(ctx, 10f))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { navigateTo(url) }
                }
                row.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(ctx).apply {
                        text = title; setTextColor(Color.WHITE); textSize = 14f
                    })
                    addView(TextView(ctx).apply {
                        text = extractDomain(url); setTextColor(colorInt("#8E8E93")); textSize = 12f
                    })
                })
                inner.addView(row)
                if (i < bookmarks.length() - 1) {
                    inner.addView(View(ctx).apply {
                        setBackgroundColor(colorInt("#2C2C2E"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                    })
                }
            }
            scroll.addView(inner)
            sheet.addView(scroll)
        }

        val dialog = android.app.Dialog(ctx, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        dialog.setContentView(sheet)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(null)
            decorView.background = GradientDrawable().apply {
                setColor(colorInt("#1C1C1E"))
                cornerRadii = floatArrayOf(dp(ctx, 16f).toFloat(), dp(ctx, 16f).toFloat(),
                    dp(ctx, 16f).toFloat(), dp(ctx, 16f).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        dialog.show()
    }

    private fun shareCurrentPage() {
        val url = webView.url ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, webView.title ?: url)
        }
        startActivity(Intent.createChooser(intent, "Поделиться"))
    }

    private fun showTabsPlaceholder() {
        Toast.makeText(this, "Управление вкладками (в разработке)", Toast.LENGTH_SHORT).show()
    }

    // ────────────────────────── LIFECYCLE ────────────────────────────────────

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val url = intent?.data?.toString()
        if (!url.isNullOrEmpty()) navigateTo(url)
    }

    private fun starDrawable(filled: Boolean, color: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = if (filled) android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE
                strokeWidth = bounds.width() * 0.08f
            }
            val b = bounds; val cx = b.exactCenterX(); val cy = b.exactCenterY(); val r = b.width() * 0.42f
            val path = android.graphics.Path()
            for (i in 0..4) {
                val outerAngle = Math.toRadians((i * 72.0 - 90.0))
                val innerAngle = Math.toRadians((i * 72.0 - 90.0 + 36.0))
                val ox = (cx + r * Math.cos(outerAngle)).toFloat()
                val oy = (cy + r * Math.sin(outerAngle)).toFloat()
                val ix = (cx + r * 0.4f * Math.cos(innerAngle)).toFloat()
                val iy = (cy + r * 0.4f * Math.sin(innerAngle)).toFloat()
                if (i == 0) path.moveTo(ox, oy) else path.lineTo(ox, oy)
                path.lineTo(ix, iy)
            }
            path.close(); canvas.drawPath(path, p)
        }
        override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}
