package com.bhushan.ucbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

object Dm {
    val items = CopyOnWriteArrayList<DItem>()
    val pool = Executors.newFixedThreadPool(3)
    var loaded = false

    fun prefs(c: Context) = c.getSharedPreferences("dm", Context.MODE_PRIVATE)

    fun baseDir(c: Context): String {
        val d = c.getSharedPreferences("data", Context.MODE_PRIVATE).getString("dldir", null)
        return d ?: (Environment.getExternalStorageDirectory().path + "/AAA/Downloads")
    }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val s = prefs(c).getString("items", "[]") ?: "[]"
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val d = DItem(o.getLong("id"), o.getString("url"), o.getString("name"), o.getString("path"))
                d.total = o.getLong("total")
                d.status = o.getInt("status")
                d.ua = o.optString("ua", "")
                d.cookie = o.optString("cookie", "")
                val f = File(d.path)
                if (d.status == ST_DONE && !f.exists()) continue
                d.done = if (f.exists()) f.length() else 0L
                if (d.status == ST_RUN) d.status = ST_PAUSE
                items.add(d)
            }
        } catch (e: Exception) { }
    }

    fun save(c: Context) {
        val a = JSONArray()
        for (d in items) {
            val o = JSONObject()
            o.put("id", d.id)
            o.put("url", d.url)
            o.put("name", d.name)
            o.put("path", d.path)
            o.put("total", d.total)
            o.put("status", d.status)
            o.put("ua", d.ua)
            o.put("cookie", d.cookie)
            a.put(o)
        }
        prefs(c).edit().putString("items", a.toString()).apply()
    }

    fun uniquePath(dir: String, name: String): String {
        var f = File(dir, name)
        var n = 1
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        while (f.exists()) {
            val nn = if (ext.isEmpty()) base + " (" + n + ")" else base + " (" + n + ")." + ext
            f = File(dir, nn)
            n++
        }
        return f.path
    }

    fun add(c: Context, url: String, name: String, ua: String, cookie: String) {
        load(c)
        val dir = baseDir(c) + "/" + catOf(name)
        File(dir).mkdirs()
        val d = DItem(System.currentTimeMillis(), url, name, uniquePath(dir, name))
        d.ua = ua
        d.cookie = cookie
        items.add(d)
        start(c, d)
    }

    fun start(c: Context, d: DItem) {
        if (d.busy) {
            d.stop = false
            d.status = ST_RUN
            return
        }
        d.stop = false
        d.busy = true
        d.status = ST_RUN
        save(c)
        c.startForegroundService(Intent(c, DlService::class.java))
        pool.execute { work(c, d) }
    }

    fun resume(c: Context, d: DItem) {
        start(c, d)
    }

    fun pause(d: DItem) {
        d.stop = true
    }

    fun cancel(c: Context, d: DItem) {
        d.stop = true
        items.remove(d)
        save(c)
        Thread {
            Thread.sleep(700)
            File(d.path).delete()
        }.start()
    }

    fun connect(u: String, d: DItem, have: Long): HttpURLConnection {
        val con = URL(u).openConnection() as HttpURLConnection
        con.connectTimeout = 15000
        con.readTimeout = 20000
        con.instanceFollowRedirects = false
        con.setRequestProperty("User-Agent", if (d.ua.isNotEmpty()) d.ua else defaultUA)
        if (d.cookie.isNotEmpty()) con.setRequestProperty("Cookie", d.cookie)
        if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-")
        return con
    }

    fun isRedirect(code: Int): Boolean {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308
    }

    fun work(c: Context, d: DItem) {
        try {
            val f = File(d.path)
            var have = if (f.exists()) f.length() else 0L
            var con = connect(d.url, d, have)
            var tries = 0
            while (isRedirect(con.responseCode) && tries < 5) {
                val loc = con.getHeaderField("Location") ?: break
                val next = URL(URL(con.url.toString()), loc).toString()
                con.disconnect()
                con = connect(next, d, have)
                tries++
            }
            val code = con.responseCode
            if (code == 416) {
                d.total = have
                d.done = have
                d.status = ST_DONE
                return
            }
            var append = true
            if (code == 200) {
                have = 0L
                append = false
            } else if (code != 206) {
                throw Exception("HTTP " + code)
            }
            val len = con.contentLengthLong
            d.total = if (len > 0) len + have else -1L
            d.done = have
            val input = con.inputStream
            val out = FileOutputStream(f, append)
            val buf = ByteArray(65536)
            var last = System.currentTimeMillis()
            var lastB = have
            while (!d.stop) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                d.done += n
                val now = System.currentTimeMillis()
                if (now - last >= 1000) {
                    d.speed = (d.done - lastB) * 1000 / (now - last)
                    last = now
                    lastB = d.done
                }
            }
            out.close()
            input.close()
            d.speed = 0
            if (d.stop) {
                d.status = ST_PAUSE
            } else if (d.total > 0 && d.done < d.total) {
                d.status = ST_FAIL
            } else {
                d.status = ST_DONE
                d.total = d.done
            }
        } catch (e: Exception) {
            d.status = ST_FAIL
            d.speed = 0
        } finally {
            d.busy = false
            save(c)
            stopIfIdle(c)
        }
    }

    fun stopIfIdle(c: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            var any = false
            for (d in items) if (d.busy) any = true
            if (!any) c.stopService(Intent(c, DlService::class.java))
        }, 3000)
    }
}

class DlService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("dl", "Downloads", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "dl")
            .setContentTitle("MyBrowser")
            .setContentText("Downloading in background")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, n)
        }
        return START_NOT_STICKY
    }
}
