package com.bhushan.ucbrowser

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.*

val openDlgs = ArrayList<Dialog>()

fun MainActivity.showTabs() {
    val names = ArrayList<String>()
    for (t in tabs) {
        val n = if (t.home) "Home" else (t.web.title ?: "Page")
        names.add((if (t.incognito) "👻 " else "") + n)
    }
    names.add("➕ New tab")
    names.add("👻 New incognito tab")
    names.add("✖ Close this tab")
    AlertDialog.Builder(this).setTitle("Tabs")
        .setItems(names.toTypedArray()) { _, i ->
            if (i < tabs.size) { cur = i; refresh() }
            else if (i == tabs.size) newTab(false)
            else if (i == tabs.size + 1) newTab(true)
            else closeTab()
        }.show()
}

fun MainActivity.showMenu() {
    val grid = GridLayout(this)
    grid.columnCount = 5
    grid.setPadding(dp(8), dp(16), dp(8), dp(16))
    val w = (resources.displayMetrics.widthPixels - dp(16)) / 5
    val d = AlertDialog.Builder(this).setView(grid).create()
    fun add(e: String, l: String, a: () -> Unit) {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(0, dp(10), 0, dp(10))
        val i = TextView(this)
        i.text = e
        i.textSize = 26f
        i.gravity = Gravity.CENTER
        val t = TextView(this)
        t.text = l
        t.textSize = 11f
        t.gravity = Gravity.CENTER
        c.addView(i)
        c.addView(t)
        c.setOnClickListener { d.dismiss(); a() }
        val lp = GridLayout.LayoutParams()
        lp.width = w
        grid.addView(c, lp)
    }
    add("🔖", L("Bookmarks", "बुकमार्क")) { showSaved("bookmarks", "Bookmarks") }
    add("🕘", L("History", "इतिहास")) { showSaved("history", "History") }
    add("⬇️", L("Downloads", "डाउनलोड")) { showDownloads() }
    add("📁", L("Files", "फाइलें")) { showFiles() }
    add("🧰", L("Tools", "टूल्स")) { showTools() }
    add("⭐", L("Add bookmark", "जोड़ें")) { addBookmark() }
    add("🌙", L("Night", "रात")) { night = !night; for (t in tabs) applyNight(t.web); refresh() }
    add("🖥️", L("Desktop", "डेस्कटॉप")) { toggleDesktop() }
    add("🔄", L("Refresh", "रीफ्रेश")) { if (!tabs[cur].home) tabs[cur].web.reload() }
    add("📤", L("Share", "शेयर")) { shareUrl() }
    add("👻", L("Incognito", "गुप्त")) { newTab(true) }
    add("➕", L("New tab", "नया टैब")) { newTab(false) }
    add("🤖", L("AI Search", "AI सर्च")) { aiSearch() }
    add("🌐", L("Translate", "अनुवाद")) { aiTranslate() }
    add("🖼️", L("Images", "इमेज")) { imageSearch() }
    add("🎨", L("Theme", "थीम")) { pickTheme() }
    d.window?.setGravity(Gravity.BOTTOM)
    d.show()
}

fun MainActivity.showTools() {
    val n = arrayOf(
        "🚫 Ad block: " + (if (adBlock) "ON" else "OFF"),
        "🖼 Data saver (no images): " + (if (dataSaver) "ON" else "OFF"),
        "➕ Add ad-block rule",
        "📂 Download folder",
        "💾 Save page offline",
        "🔗 Copy link",
        "🧹 Clear browsing data",
        "🔍 Search engine",
        "🌐 Language / भाषा",
        "🔑 AI key"
    )
    AlertDialog.Builder(this).setTitle(L("Tools", "टूल्स")).setItems(n) { _, i ->
        when (i) {
            0 -> { adBlock = !adBlock; toast("Ad block " + (if (adBlock) "ON" else "OFF")) }
            1 -> toggleDataSaver()
            2 -> addRule()
            3 -> pickFolder()
            4 -> savePage()
            5 -> copyLink()
            6 -> clearData()
            7 -> pickEngine()
            8 -> toggleLang()
            9 -> askKey()
            else -> { }
        }
    }.show()
}

fun MainActivity.toggleDesktop() {
    desktop = !desktop
    val ua: String? = if (desktop) desktopUA else null
    for (t in tabs) {
        t.web.settings.userAgentString = ua
        if (!t.home) t.web.reload()
    }
    toast(if (desktop) "Desktop site ON" else "Desktop site OFF")
}

fun MainActivity.toggleDataSaver() {
    dataSaver = !dataSaver
    for (t in tabs) {
        t.web.settings.blockNetworkImage = dataSaver
        if (!t.home) t.web.reload()
    }
    toast("Data saver " + (if (dataSaver) "ON" else "OFF"))
}

fun MainActivity.toggleLang() {
    val hi = prefs.getString("lang", "en") == "hi"
    prefs.edit().putString("lang", if (hi) "en" else "hi").apply()
    toast(if (hi) "English" else "हिंदी")
}

fun MainActivity.pickEngine() {
    val n = ArrayList<String>()
    for (e in engines) n.add(e.split("|||")[0])
    AlertDialog.Builder(this).setTitle("Search engine")
        .setItems(n.toTypedArray()) { _, i ->
            engine = i
            prefs.edit().putInt("engine", i).apply()
            toast("Search: " + n[i])
        }.show()
}

fun MainActivity.addRule() {
    val e = EditText(this)
    e.hint = "ads.example.com"
    AlertDialog.Builder(this).setTitle("Block this host").setView(e)
        .setPositiveButton("Add") { _, _ ->
            val h = e.text.toString().trim().lowercase()
            if (h.isNotEmpty()) {
                val l = getList("blocklist")
                l.add(h)
                putList("blocklist", l)
                blockCache = l
                toast("Blocked: " + h)
            }
        }.setNegativeButton("Cancel", null).show()
}

fun MainActivity.pickFolder() {
    val e = EditText(this)
    e.setText(Dm.baseDir(this))
    AlertDialog.Builder(this).setTitle("Download folder").setView(e)
        .setPositiveButton("Save") { _, _ ->
            val p = e.text.toString().trim()
            if (p.startsWith("/")) {
                prefs.edit().putString("dldir", p).apply()
                toast("Saved")
            }
        }.setNegativeButton("Cancel", null).show()
}

fun MainActivity.savePage() {
    val t = tabs[cur]
    if (t.home || t.web.url == null) { toast("Open a page first"); return }
    if (!ensureStorage()) return
    val dir = Dm.baseDir(this) + "/Webpage"
    java.io.File(dir).mkdirs()
    val nm = (t.web.title ?: "page").replace(Regex("[^A-Za-z0-9 ]"), "").trim().take(40).ifEmpty { "page" }
    t.web.saveWebArchive(dir + "/" + nm + ".mht")
    toast("Saved offline: " + nm + ".mht")
}

fun MainActivity.copyLink() {
    val u = tabs[cur].web.url
    if (tabs[cur].home || u == null) { toast("Open a page first"); return }
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("url", u))
    toast("Link copied")
}

fun MainActivity.clearData() {
    putList("history", listOf())
    CookieManager.getInstance().removeAllCookies(null)
    WebStorage.getInstance().deleteAllData()
    for (t in tabs) t.web.clearCache(true)
    toast("Browsing data cleared")
}

fun MainActivity.listScreen(title: String, items: List<String>, extra: View?, onClick: (Dialog, Int) -> Unit, onLong: (Dialog, Int) -> Unit) {
    val d = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar)
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    val t = TextView(this)
    t.text = title
    t.textSize = 22f
    t.setTextColor(Color.BLACK)
    t.setPadding(dp(16), dp(16), dp(16), dp(8))
    col.addView(t)
    if (extra != null) col.addView(extra)
    val lv = ListView(this)
    lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    lv.setOnItemClickListener { _, _, i, _ -> onClick(d, i) }
    lv.setOnItemLongClickListener { _, _, i, _ -> onLong(d, i); true }
    col.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
    d.setContentView(col)
    openDlgs.add(d)
    d.setOnDismissListener { openDlgs.remove(d) }
    d.show()
}

fun MainActivity.showSaved(key: String, title: String) {
    val l = getList(key)
    val names = ArrayList<String>()
    for (e in l) {
        val p = e.split("|||")
        names.add(p[0] + "\n" + p[1])
    }
    var extra: View? = null
    if (key == "history") {
        val b = Button(this)
        b.text = "Clear history"
        b.setOnClickListener { putList(key, listOf()); toast("History cleared") }
        extra = b
    }
    listScreen(title, names, extra,
        { d, i -> d.dismiss(); load(l[i].split("|||")[1]) },
        { d, i -> l.removeAt(i); putList(key, l); d.dismiss(); showSaved(key, title); toast("Removed") })
}

fun MainActivity.addBookmark() {
    val t = tabs[cur]
    val u = t.web.url
    if (t.home || u == null) { toast("Open a page first"); return }
    val l = getList("bookmarks")
    l.add(0, (t.web.title ?: u).replace("\n", " ") + "|||" + u)
    putList("bookmarks", l)
    toast("Bookmark added")
}

fun MainActivity.shareUrl() {
    val t = tabs[cur]
    val u = t.web.url
    if (t.home || u == null) { toast("Open a page first"); return }
    val i = Intent(Intent.ACTION_SEND)
    i.type = "text/plain"
    i.putExtra(Intent.EXTRA_TEXT, u)
    startActivity(Intent.createChooser(i, "Share"))
}
