package com.bhushan.ucbrowser

import android.webkit.WebView

class Tab(val web: WebView, val incognito: Boolean) {
    var home = true
}

class DL(val id: Long, val name: String, val status: Int, val size: Long, val mime: String)

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
    "propellerads.com", "adsterra.com", "exoclick.com",
    "google-analytics.com", "scorecardresearch.com", "hotjar.com"
)

const val desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

fun fmtSize(b: Long): String {
    if (b <= 0) return "unknown size"
    if (b >= 1073741824L) return String.format("%.2f GB", b / 1073741824.0)
    return String.format("%.1f MB", b / 1048576.0)
}

fun catOf(n: String): String {
    val e = n.substringAfterLast('.', "").lowercase()
    return when (e) {
        "mp3", "m4a", "wav", "flac", "aac", "ogg" -> "Music"
        "mp4", "mkv", "avi", "webm", "mov", "3gp" -> "Video"
        "jpg", "jpeg", "png", "gif", "webp" -> "Photo"
        "apk" -> "Apps"
        "zip", "rar", "7z", "tar", "gz" -> "Archive"
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx" -> "Docs"
        else -> "Others"
    }
}
