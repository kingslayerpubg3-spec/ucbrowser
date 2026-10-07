package com.bhushan.ucbrowser

import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.*

fun MainActivity.dm(): DownloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

fun MainActivity.startDownload(url: String, ua: String?, cd: String?, mime: String?) {
    val name = URLUtil.guessFileName(url, cd, mime)
    val r = DownloadManager.Request(Uri.parse(url))
    if (mime != null) r.setMimeType(mime)
    if (ua != null) r.addRequestHeader("User-Agent", ua)
    val ck = CookieManager.getInstance().getCookie(url)
    if (ck != null) r.addRequestHeader("Cookie", ck)
    r.setTitle(name)
    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
    dm().enqueue(r)
    toast("Download started: " + name)
}

fun MainActivity.queryDownloads(): List<DL> {
    val out = ArrayList<DL>()
    val c = dm().query(DownloadManager.Query())
    while (c.moveToNext()) {
        val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
        val name = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "file"
        val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val sz = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val mime = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)) ?: ""
        out.add(DL(id, name, st, sz, mime))
    }
    c.close()
    return out.reversed()
}

fun MainActivity.showDownloads(cat: String?) {
    val all = queryDownloads()
    val l = if (cat == null) all else all.filter { catOf(it.name) == cat }
    val names = ArrayList<String>()
    for (d in l) {
        val s = when (d.status) {
            DownloadManager.STATUS_SUCCESSFUL -> "Completed"
            DownloadManager.STATUS_FAILED -> "Failed"
            else -> "Downloading..."
        }
        names.add(d.name + "\n" + s + " • " + fmtSize(d.size))
    }
    listScreen(cat ?: "Downloads", names, null,
        { _, i -> openDl(l[i]) },
        { dlg, i -> confirmDelete(dlg, l[i], cat) })
}

fun MainActivity.openDl(d: DL) {
    val uri = dm().getUriForDownloadedFile(d.id)
    if (uri == null) { toast("File not ready"); return }
    val i = Intent(Intent.ACTION_VIEW)
    i.setDataAndType(uri, if (d.mime.isEmpty()) "*/*" else d.mime)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { startActivity(i) } catch (e: Exception) { toast("No app found to open") }
}

fun MainActivity.confirmDelete(dlg: Dialog, d: DL, cat: String?) {
    AlertDialog.Builder(this).setTitle("Delete?").setMessage(d.name)
        .setPositiveButton("Delete") { _, _ ->
            dm().remove(d.id)
            dlg.dismiss()
            showDownloads(cat)
        }.setNegativeButton("Cancel", null).show()
}

fun MainActivity.showFiles() {
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
    val all = queryDownloads()
    val cats = listOf("Music", "Video", "Photo", "Apps", "Archive", "Docs", "Others")
    val names = ArrayList<String>()
    for (c in cats) {
        val n = all.count { catOf(it.name) == c }
        names.add(c + "  (" + n + ")")
    }
    listScreen("Files", names, col, { _, i -> showDownloads(cats[i]) }, { _, _ -> })
}
