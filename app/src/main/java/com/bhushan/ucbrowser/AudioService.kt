package com.bhushan.ucbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder

class AudioService : Service() {
    var mp: MediaPlayer? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i != null && i.action == "stop") {
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("audio", "Background play", NotificationManager.IMPORTANCE_LOW))
        val si = Intent(this, AudioService::class.java)
        si.action = "stop"
        val pi = PendingIntent.getService(this, 0, si, PendingIntent.FLAG_IMMUTABLE)
        val act = Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stop", pi).build()
        val n = Notification.Builder(this, "audio")
            .setContentTitle("MyBrowser")
            .setContentText("Playing in background")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(act)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(2, n)
        }
        val u = i?.data
        val pos = i?.getIntExtra("pos", 0) ?: 0
        if (u != null) {
            try {
                mp?.release()
                val m = MediaPlayer()
                mp = m
                m.setDataSource(applicationContext, u)
                m.setOnPreparedListener { p -> p.seekTo(pos); p.start() }
                m.setOnCompletionListener { stopSelf() }
                m.prepareAsync()
            } catch (e: Exception) {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mp?.release()
        mp = null
        super.onDestroy()
    }
}
