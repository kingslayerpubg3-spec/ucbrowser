package com.bhushan.ucbrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*

class MainActivity : Activity() {
    val tabs = ArrayList<Tab>()
    var cur = 0
    var night = false
    var desktop = false
    var engine = 0
    var blockCache: List<String> = listOf()
    var customView: View? = null
    var customCb: WebChromeClient.CustomViewCallback? = null
    lateinit var root: LinearLayout
    lateinit var content: FrameLayout
    lateinit var bar: EditText
    lateinit var prog: ProgressBar
    lateinit var tabBtn: TextView
    val navViews = ArrayList<TextView>()
    val prefs by lazy { getSharedPreferences("data", MODE_PRIVATE) }

    var adBlock: Boolean
        get() = prefs.getBoolean("adblock", true)
        set(v) { prefs.edit().putBoolean("adblock", v).apply() }

    var dataSaver: Boolean
        get() = prefs.getBoolean("datasaver", false)
        set(v) { prefs.edit().putBoolean("datasaver", v).apply() }

    fun dp(x: Int): Int = (x * resources.displayMetrics.density).toInt()
    fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    fun dark(): Boolean = night || tabs[cur].incognito
    fun fg(): Int = if (dark()) Color.WHITE else Color.parseColor("#222222")
    fun L(en: String, hi: String): String = if (prefs.getString("lang", "en") == "hi") hi else en

    fun getList(k: String): MutableList<String> {
        val s = prefs.getString(k, "") ?: ""
        if (s.isEmpty()) return mutableListOf<String>()
        return s.split("\n").toMutableList()
    }

    fun putList(k: String, l: List<String>) {
        prefs.edit().putString(k, l.joinToString("\n")).apply()
    }

    fun isBlocked(h: String): Boolean {
        for (x in blockedHosts) if (h.contains(x)) return true
        for (x in blockCache) if (x.isNotEmpty() && h.contains(x)) return true
        return false
    }

    fun ensureStorage(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) return true
            toast("MyBrowser ko All files access allow karo, phir wapas aao")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + packageName)))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return false
        }
        if (checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.WRITE_EXTERNAL_STORAGE"), 2)
            return false
        }
        return true
    }

    fun nav(label: String, a: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 22f
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(12), 0, dp(12))
        t.setOnClickListener { a() }
        navViews.add(t)
        return t
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        engine = prefs.getInt("engine", 0)
        blockCache = getList("blocklist")
        Dm.load(applicationContext)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        bar = EditText(this)
        bar.hint = "Search or Enter URL"
        bar.setSingleLine(true)
        bar.setSelectAllOnFocus(true)
        bar.imeOptions = EditorInfo.IME_ACTION_GO
        bar.setPadding(dp(20), dp(12), dp(20), dp(12))
        bar.setOnEditorActionListener { _, _, _ -> load(bar.text.toString()); true }
        prog = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        prog.max = 100
        prog.visibility = View.GONE
        content = FrameLayout(this)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        tabBtn = nav("1") { showTabs() }
        val items = listOf(
            nav("◀") { goBackNav() },
            nav("▶") { goForwardNav() },
            nav("☰") { showMenu() },
            tabBtn,
            nav("🏠") { goHome() }
        )
        for (v in items) row.addView(v, LinearLayout.LayoutParams(0, -2, 1f))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(dp(12), dp(10), dp(12), dp(2))
        root.addView(bar, lp)
        root.addView(prog, LinearLayout.LayoutParams(-1, dp(3)))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        newTab(false)
    }

    fun makeWeb(inc: Boolean): WebView {
        val w = WebView(this)
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.allowFileAccess = true
        s.javaScriptCanOpenWindowsAutomatically = false
        s.blockNetworkImage = dataSaver
        if (desktop) s.userAgentString = desktopUA
        if (inc) s.cacheMode = WebSettings.LOAD_NO_CACHE
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                if (tabs.size > cur && tabs[cur].web === v) bar.setText(url)
                if (!inc) addHistory(v.title ?: url, url)
                if (adBlock) v.evaluateJavascript(adCss, null)
            }

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val sc = r.url.scheme
                if (sc == "http" || sc == "https" || sc == "file") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (e: Exception) { }
                return true
            }

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                checkVideo(r.url.toString())
                if (adBlock) {
                    val h = r.url.host ?: return null
                    if (isBlocked(h)) {
                        return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                }
                return null
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (tabs.size > cur && tabs[cur].web === v) {
                    prog.progress = p
                    prog.visibility = if (p >= 100) View.GONE else View.VISIBLE
                }
            }

            override fun onShowCustomView(v: View, cb: WebChromeClient.CustomViewCallback) {
                customView = v
                customCb = cb
                setContentView(v)
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }
        w.setDownloadListener { url, ua, cd, mime, _ -> startDownload(url, ua, cd, mime) }
        applyNight(w)
        return w
    }

    fun exitFullscreen() {
        if (customView == null) return
        customCb?.onCustomViewHidden()
        customView = null
        window.decorView.systemUiVisibility = 0
        setContentView(root)
    }

    fun addHistory(t: String, u: String) {
        val l = getList("history")
        val e = t.replace("\n", " ") + "|||" + u
        if (l.isNotEmpty() && l[0] == e) return
        l.add(0, e)
        putList("history", l.take(200))
    }

    fun newTab(inc: Boolean) {
        tabs.add(Tab(makeWeb(inc), inc))
        cur = tabs.size - 1
        refresh()
    }

    fun load(q: String) {
        val s = q.trim()
        if (s.isEmpty()) return
        val t = tabs[cur]
        val u = when {
            s.startsWith("http://") || s.startsWith("https://") || s.startsWith("file://") -> s
            s.contains(".") && !s.contains(" ") -> "https://" + s
            else -> engines[engine].split("|||")[1] + Uri.encode(s)
        }
        lastVideo = null
        t.home = false
        refresh()
        t.web.loadUrl(u)
        bar.setText(u)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(bar.windowToken, 0)
    }

    fun refresh() {
        val t = tabs[cur]
        content.removeAllViews()
        if (t.home) {
            content.addView(buildHome())
            bar.setText("")
        } else {
            (t.web.parent as? ViewGroup)?.removeView(t.web)
            content.addView(t.web, FrameLayout.LayoutParams(-1, -1))
            bar.setText(t.web.url ?: "")
        }
        tabBtn.text = (if (t.incognito) "👻" else "") + tabs.size
        applyTheme()
    }

    fun applyTheme() {
        val d = dark()
        val light = prefs.getString("theme", "#F4F5F7") ?: "#F4F5F7"
        root.setBackgroundColor(Color.parseColor(if (d) "#161618" else light))
        val g = GradientDrawable()
        g.cornerRadius = dp(24).toFloat()
        g.setColor(Color.parseColor(if (d) "#2C2C30" else "#FFFFFF"))
        bar.background = g
        bar.setTextColor(fg())
        bar.setHintTextColor(Color.GRAY)
        for (v in navViews) v.setTextColor(fg())
    }

    fun applyNight(w: WebView) {
        if (night) {
            val m = ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f))
            val p = Paint()
            p.colorFilter = ColorMatrixColorFilter(m)
            w.setLayerType(View.LAYER_TYPE_HARDWARE, p)
        } else {
            w.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    fun goBackNav(): Boolean {
        val t = tabs[cur]
        if (!t.home && t.web.canGoBack()) { t.web.goBack(); return true }
        if (!t.home) { t.home = true; refresh(); return true }
        return false
    }

    fun goForwardNav() {
        val t = tabs[cur]
        if (t.home && t.web.url != null) {
            t.home = false
            refresh()
        } else if (!t.home && t.web.canGoForward()) {
            t.web.goForward()
        }
    }

    fun goHome() {
        tabs[cur].home = true
        refresh()
    }

    fun closeTab() {
        val t = tabs[cur]
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        t.web.destroy()
        tabs.removeAt(cur)
        if (tabs.isEmpty()) { newTab(false); return }
        if (cur >= tabs.size) cur = tabs.size - 1
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customView != null) { exitFullscreen(); return }
        if (goBackNav()) return
        if (tabs.size > 1) closeTab() else super.onBackPressed()
    }
}
