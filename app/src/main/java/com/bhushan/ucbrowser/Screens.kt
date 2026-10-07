package com.bhushan.ucbrowser

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*

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
    add("🔖", "Bookmarks") { showSaved("bookmarks", "Bookmarks") }
    add("🕘", "History") { showSaved("history", "History") }
    add("⬇️", "Downloads") { showDownloads(null) }
    add("📁", "Files") { showFiles() }
    add("👻", "Incognito") { newTab(true) }
    add("⭐", "Add bookmark") { addBookmark() }
    add("🌙", "Night") { night = !night; for (t in tabs) applyNight(t.web); refresh() }
    add("🖥️", "Desktop") { toggleDesktop() }
    add("🚫", "Ad block") { adBlock = !adBlock; toast(if (adBlock) "Ad block ON" else "Ad block OFF") }
    add("🔍", "Search") { pickEngine() }
    add("🔄", "Refresh") { if (!tabs[cur].home) tabs[cur].web.reload() }
    add("📤", "Share") { shareUrl() }
    add("➕", "New tab") { newTab(false) }
    d.window?.setGravity(Gravity.BOTTOM)
    d.show()
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
