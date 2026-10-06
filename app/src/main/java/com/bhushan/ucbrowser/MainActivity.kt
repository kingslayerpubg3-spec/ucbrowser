package com.bhushan.ucbrowser

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var bar: EditText
    private val home = "https://www.google.com"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        bar = EditText(this)
        bar.hint = "Search or Enter URL"
        bar.setSingleLine(true)
        bar.imeOptions = EditorInfo.IME_ACTION_GO
        bar.setOnEditorActionListener { _, _, _ -> go(bar.text.toString()); true }

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) { bar.setText(url) }
        }
        web.setDownloadListener { url, ua, cd, mime, _ ->
            val r = DownloadManager.Request(Uri.parse(url))
            r.setMimeType(mime)
            r.addRequestHeader("User-Agent", ua)
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mime))
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(r)
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        addBtn(row, "Back") { if (web.canGoBack()) web.goBack() }
        addBtn(row, "Next") { if (web.canGoForward()) web.goForward() }
        addBtn(row, "Refresh") { web.reload() }
        addBtn(row, "Home") { web.loadUrl(home) }

        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        web.loadUrl(home)
    }

    private fun addBtn(row: LinearLayout, t: String, a: () -> Unit) {
        val b = Button(this)
        b.text = t
        b.setOnClickListener { a() }
        row.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun go(q: String) {
        val u = when {
            q.startsWith("http") -> q
            q.contains(".") && !q.contains(" ") -> "https://" + q
            else -> "https://www.google.com/search?q=" + Uri.encode(q)
        }
        web.loadUrl(u)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
