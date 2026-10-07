package com.bhushan.ucbrowser

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

fun MainActivity.buildHome(): View {
    val inc = tabs[cur].incognito
    val sv = ScrollView(this)
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(dp(16), dp(16), dp(16), dp(16))
    val title = TextView(this)
    title.text = if (inc) "Incognito" else "MyBrowser"
    title.textSize = 30f
    title.setTextColor(fg())
    col.addView(title)
    val grid = GridLayout(this)
    grid.columnCount = 4
    val w = (resources.displayMetrics.widthPixels - dp(32)) / 4
    val sites = defaultSites + getList("sites") + "Add sites|||+"
    for (s in sites) {
        val p = s.split("|||")
        val name = p[0]
        val url = p[1]
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(0, dp(10), 0, dp(10))
        val ic = TextView(this)
        ic.text = if (url == "+") "+" else name.take(1).uppercase()
        ic.gravity = Gravity.CENTER
        ic.textSize = 22f
        ic.setTextColor(Color.WHITE)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.HSVToColor(floatArrayOf((Math.abs(name.hashCode()) % 360).toFloat(), 0.6f, 0.75f)))
        ic.background = bg
        c.addView(ic, LinearLayout.LayoutParams(dp(52), dp(52)))
        val t = TextView(this)
        t.text = name
        t.maxLines = 1
        t.textSize = 12f
        t.setTextColor(fg())
        c.addView(t)
        c.setOnClickListener { if (url == "+") addSite() else load(url) }
        c.setOnLongClickListener { removeSite(s); true }
        val lp = GridLayout.LayoutParams()
        lp.width = w
        grid.addView(c, lp)
    }
    col.addView(grid)
    if (inc) {
        val b = TextView(this)
        b.text = "Incognito mode is on\nHistory is not saved in incognito tabs."
        b.setTextColor(Color.parseColor("#C9B8FF"))
        b.setPadding(dp(16), dp(16), dp(16), dp(16))
        val g = GradientDrawable()
        g.cornerRadius = dp(16).toFloat()
        g.setColor(Color.parseColor("#2A2140"))
        b.background = g
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(16)
        col.addView(b, lp)
    }
    sv.addView(col)
    return sv
}

fun MainActivity.removeSite(s: String) {
    val l = getList("sites")
    if (l.remove(s)) {
        putList("sites", l)
        toast("Removed")
        refresh()
    }
}

fun MainActivity.addSite() {
    val e = EditText(this)
    e.hint = "example.com"
    AlertDialog.Builder(this).setTitle("Add site").setView(e)
        .setPositiveButton("Add") { _, _ ->
            var u = e.text.toString().trim()
            if (u.isNotEmpty()) {
                if (!u.startsWith("http")) u = "https://" + u
                val host = android.net.Uri.parse(u).host ?: u
                val l = getList("sites")
                l.add(host.removePrefix("www.") + "|||" + u)
                putList("sites", l)
                refresh()
            }
        }.setNegativeButton("Cancel", null).show()
}
