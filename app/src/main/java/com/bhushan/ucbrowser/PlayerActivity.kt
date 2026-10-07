package com.bhushan.ucbrowser

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class PlayerActivity : Activity() {
    var mp: MediaPlayer? = null
    var speed = 1f
    lateinit var vv: VideoView

    fun btn(t: String, a: (Button) -> Unit): Button {
        val x = Button(this)
        x.text = t
        x.setOnClickListener { a(x) }
        return x
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        stopService(Intent(this, AudioService::class.java))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val fl = FrameLayout(this)
        fl.setBackgroundColor(0xFF000000.toInt())
        vv = VideoView(this)
        fl.addView(vv, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        val mc = MediaController(this)
        mc.setAnchorView(vv)
        vv.setMediaController(mc)
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.addView(btn("1.0x") { x ->
            speed = if (speed >= 2f) 0.5f else speed + 0.5f
            x.text = speed.toString() + "x"
            try { mp?.playbackParams = PlaybackParams().setSpeed(speed) } catch (e: Exception) { }
        })
        bar.addView(btn("Float") { enterPip() })
        bar.addView(btn("Audio") { toBackground() })
        fl.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        setContentView(fl)
        vv.setOnPreparedListener { m -> mp = m; vv.start() }
        vv.setVideoURI(intent.data)
    }

    fun enterPip() {
        try { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) } catch (e: Exception) { }
    }

    fun toBackground() {
        val i = Intent(this, AudioService::class.java)
        i.data = intent.data
        i.putExtra("pos", vv.currentPosition)
        startForegroundService(i)
        finish()
    }
}
