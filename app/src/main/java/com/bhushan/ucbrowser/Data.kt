package com.bhushan.ucbrowser

import android.webkit.WebView

class Tab(val web: WebView, val incognito: Boolean) {
    var home = true
}

const val ST_RUN = 1
const val ST_PAUSE = 2
const val ST_DONE = 3
const val ST_FAIL = 4

class DItem(val id: Long, val url: String, var name: String, var path: String) {
    var total = -1L
    var done = 0L
    var status = ST_PAUSE
    var speed = 0L
    var ua = ""
    var cookie = ""
    @Volatile var stop = false
    @Volatile var busy = false
}

val defaultSites = listOf(
    "Google|||https://www.google.com",
    "YouTube|||https://m.youtube.com",
    "X|||https://x.com",
    "Facebook|||https://m.facebook.com",
    "Instagram|||https://www.instagram.com",
    "Yandex|||https://yandex.com",
    "ChatGPT|||https://chatgpt.com",
    "Wikipedia|||https://wikipedia.org"
)

val engines = listOf(
    "Google|||https://www.google.com/search?q=",
    "Bing|||https://www.bing.com/search?q=",
    "DuckDuckGo|||https://duckduckgo.com/?q=",
    "Yandex|||https://yandex.com/search/?text="
)

val blockedHosts = listOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "adnxs.com", "taboola.com", "outbrain.com", "popads.net",
    "propellerads.com", "adsterra.com", "exoclick.com", "adservice.google",
    "google-analytics.com", "scorecardresearch.com", "hotjar.com", "facebook.net"
)

const val adCss = "(function(){var s=document.createElement('style');s.innerHTML='.adsbygoogle,[id^=google_ads],[class*=ad-banner],[id*=ad-container]{display:none!important}';document.head.appendChild(s);})()"

const val desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

const val defaultUA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

fun fmtSize(b: Long): String {
    if (b < 0) return "?"
    if (b >= 1073741824L) return String.format("%.2f GB", b / 1073741824.0)
    return String.format("%.1f MB", b / 1048576.0)
}

fun catOf(n: String): String {
    val e = n.substringAfterLast('.', "").lowercase()
    return when (e) {
        "mp3", "m4a", "wav", "flac", "aac", "ogg" -> "Music"
        "mp4", "mkv", "avi", "webm", "mov", "3gp", "m3u8" -> "Video"
        "jpg", "jpeg", "png", "gif", "webp" -> "Photo"
        "apk" -> "Apps"
        "zip", "rar", "7z", "tar", "gz" -> "Archive"
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx" -> "Docs"
        "mht", "mhtml", "html", "htm" -> "Webpage"
        else -> "Others"
    }
}
