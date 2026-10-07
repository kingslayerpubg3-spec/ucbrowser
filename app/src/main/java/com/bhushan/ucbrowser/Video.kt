package com.bhushan.ucbrowser

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

var lastVideo: String? = null
var vidBox: LinearLayout? = null

fun MainActivity.checkVideo(u: String) {
    val p = u.substringBefore("?").lowercase()
    val dl = p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".mkv") || p.endsWith(".3gp")
    val pl = dl || p.endsWith(".m3u8")
    if (!pl || u == lastVideo) return
    lastVideo = u
    runOnUiThread {
        val old = vidBox
        if (old != null) content.removeView(old)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        val play = Button(this)
        play.text = "▶ Play"
        play.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java).setData(Uri.parse(u)))
        }
        box.addView(play)
        if (dl) {
            val b = Button(this)
            b.text = "⬇ Download"
            b.setOnClickListener {
                content.removeView(box)
                startDownload(u, null, null, null)
            }
            box.addView(b)
        }
        val x = Button(this)
        x.text = "✖"
        x.setOnClickListener { content.removeView(box) }
        box.addView(x)
        val lp = FrameLayout.LayoutParams(-2, -2)
        lp.gravity = Gravity.BOTTOM or Gravity.END
        lp.setMargins(0, 0, dp(8), dp(8))
        content.addView(box, lp)
        vidBox = box
    }
}
