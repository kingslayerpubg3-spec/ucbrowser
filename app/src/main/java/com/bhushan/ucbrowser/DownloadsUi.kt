package com.bhushan.ucbrowser

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File

fun MainActivity.startDownload(url: String, ua: String?, cd: String?, mime: String?) {
    if (!ensureStorage()) return
    val name = URLUtil.guessFileName(url, cd, mime)
    val ck = CookieManager.getInstance().getCookie(url) ?: ""
    Dm.add(applicationContext, url, name, if (ua.isNullOrEmpty()) defaultUA else ua, ck)
    toast("Download started: " + name)
}

fun dlLabel(x: DItem): String {
    val pct = if (x.total > 0) (x.done * 100 / x.total).toInt() else 0
    return when (x.status) {
        ST_RUN -> "⬇ " + x.name + "\n" + pct + "%  " + fmtSize(x.done) + " / " + fmtSize(x.total) + "  " + fmtSize(x.speed) + "/s"
        ST_PAUSE -> "⏸ " + x.name + "\nPaused " + pct + "%  " + fmtSize(x.done) + " / " + fmtSize(x.total)
        ST_DONE -> "✅ " + x.name + "\n" + fmtSize(x.total)
        else -> "⚠ " + x.name + "\nFailed " + pct + "%  (tap to resume)"
    }
}

fun MainActivity.showDownloads() {
    Dm.load(applicationContext)
    val dlg = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar)
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    val t = TextView(this)
    t.text = L("Downloads", "डाउनलोड")
    t.textSize = 22f
    t.setTextColor(Color.BLACK)
    t.setPadding(dp(16), dp(16), dp(16), dp(8))
    col.addView(t)
    val data = ArrayList<String>()
    val ad = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
    val lv = ListView(this)
    lv.adapter = ad
    var shown = listOf<DItem>()
    fun fill() {
        shown = Dm.items.reversed()
        data.clear()
        for (x in shown) data.add(dlLabel(x))
        ad.notifyDataSetChanged()
    }
    lv.setOnItemClickListener { _, _, i, _ -> if (i < shown.size) dlAction(shown[i]) }
    col.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
    dlg.setContentView(col)
    openDlgs.add(dlg)
    dlg.setOnDismissListener { openDlgs.remove(dlg) }
    val h = Handler(Looper.getMainLooper())
    val tick = object : Runnable {
        override fun run() {
            fill()
            if (dlg.isShowing) h.postDelayed(this, 1000)
        }
    }
    dlg.show()
    tick.run()
}

fun MainActivity.dlAction(x: DItem) {
    val opts = when (x.status) {
        ST_RUN -> arrayOf("Pause", "Cancel and delete")
        ST_DONE -> arrayOf("Open", "Share", "Delete")
        else -> arrayOf("Resume", "Cancel and delete")
    }
    AlertDialog.Builder(this).setTitle(x.name).setItems(opts) { _, i ->
        val o = opts[i]
        if (o == "Pause") Dm.pause(x)
        else if (o == "Resume") Dm.resume(applicationContext, x)
        else if (o == "Open") openFile(File(x.path))
        else if (o == "Share") shareFile(File(x.path))
        else Dm.cancel(applicationContext, x)
    }.show()
}

fun MainActivity.openFile(f: File) {
    val cat = catOf(f.name)
    if (cat == "Webpage") {
        for (d in ArrayList(openDlgs)) d.dismiss()
        load("file://" + f.path)
        return
    }
    if (cat == "Video" || cat == "Music") {
        startActivity(Intent(this, PlayerActivity::class.java).setData(Uri.fromFile(f)))
        return
    }
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
    val u = FileProvider.getUriForFile(this, packageName + ".fp", f)
    val i = Intent(Intent.ACTION_VIEW)
    i.setDataAndType(u, mime)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { startActivity(i) } catch (e: Exception) { toast("No app found to open") }
}

fun MainActivity.shareFile(f: File) {
    val u = FileProvider.getUriForFile(this, packageName + ".fp", f)
    val i = Intent(Intent.ACTION_SEND)
    i.type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
    i.putExtra(Intent.EXTRA_STREAM, u)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(i, "Share"))
}

fun scanDir(d: File, out: HashMap<String, ArrayList<File>>) {
    val fs = d.listFiles() ?: return
    for (f in fs) {
        if (f.isDirectory) {
            if (f.name != "Android" && !f.name.startsWith(".")) scanDir(f, out)
        } else {
            val k = catOf(f.name)
            val l = out[k]
            if (l == null) {
                val n = ArrayList<File>()
                n.add(f)
                out[k] = n
            } else {
                l.add(f)
            }
        }
    }
}

fun MainActivity.showFiles() {
    if (!ensureStorage()) return
    toast("Scanning files...")
    Thread {
        val map = HashMap<String, ArrayList<File>>()
        scanDir(Environment.getExternalStorageDirectory(), map)
        runOnUiThread { filesDialog(map) }
    }.start()
}

fun MainActivity.filesDialog(map: HashMap<String, ArrayList<File>>) {
    val st = StatFs(Environment.getExternalStorageDirectory().path)
    val total = st.totalBytes
    val free = st.availableBytes
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(dp(16), 0, dp(16), dp(8))
    val tv = TextView(this)
    tv.text = "Available: " + fmtSize(free) + " / Total: " + fmtSize(total)
    tv.setTextColor(Color.BLACK)
    val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
    pb.max = 100
    pb.progress = ((total - free) * 100 / total).toInt()
    col.addView(tv)
    col.addView(pb)
    val cats = listOf("Music", "Video", "Photo", "Apps", "Archive", "Docs", "Webpage", "Others")
    val names = ArrayList<String>()
    for (c in cats) {
        val n = map[c]?.size ?: 0
        names.add(c + "  (" + n + ")")
    }
    listScreen("Files", names, col,
        { _, i -> showCat(cats[i], map[cats[i]] ?: ArrayList<File>()) },
        { _, _ -> })
}

fun MainActivity.showCat(cat: String, l0: List<File>) {
    val l = l0.sortedByDescending { it.lastModified() }.take(500)
    val names = ArrayList<String>()
    for (f in l) names.add(f.name + "\n" + fmtSize(f.length()))
    listScreen(cat, names, null,
        { _, i -> openFile(l[i]) },
        { dlg, i -> confirmDeleteFile(dlg, l[i]) })
}

fun MainActivity.confirmDeleteFile(dlg: Dialog, f: File) {
    AlertDialog.Builder(this).setTitle("Delete?").setMessage(f.name)
        .setPositiveButton("Delete") { _, _ ->
            f.delete()
            dlg.dismiss()
            toast("Deleted")
        }.setNegativeButton("Cancel", null).show()
}
