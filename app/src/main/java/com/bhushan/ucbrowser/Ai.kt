package com.bhushan.ucbrowser

import android.app.AlertDialog
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val GEMINI_MODEL = "gemini-2.5-flash"

fun MainActivity.askKey() {
    val e = EditText(this)
    e.hint = "Gemini API key"
    e.setText(prefs.getString("gkey", "") ?: "")
    AlertDialog.Builder(this).setTitle("AI key").setView(e)
        .setPositiveButton("Save") { _, _ ->
            prefs.edit().putString("gkey", e.text.toString().trim()).apply()
        }.setNegativeButton("Cancel", null).show()
}

fun MainActivity.callGemini(prompt: String, done: (String) -> Unit) {
    val key = prefs.getString("gkey", "") ?: ""
    if (key.isEmpty()) { askKey(); return }
    Thread {
        var out = ""
        try {
            val u = URL("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent")
            val c = u.openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key)
            c.doOutput = true
            val part = JSONObject().put("text", prompt)
            val cont = JSONObject().put("parts", JSONArray().put(part))
            val body = JSONObject().put("contents", JSONArray().put(cont))
            c.outputStream.write(body.toString().toByteArray())
            val code = c.responseCode
            val stream = if (code < 400) c.inputStream else c.errorStream
            val txt = stream.bufferedReader().readText()
            if (code < 400) {
                out = JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            } else {
                out = "Error " + code + ": " + txt.take(300)
            }
        } catch (e: Exception) {
            out = "Error: " + e.message
        }
        runOnUiThread { done(out) }
    }.start()
}

fun MainActivity.showText(title: String, s: String) {
    val sv = ScrollView(this)
    val t = TextView(this)
    t.text = s
    t.setTextIsSelectable(true)
    t.setPadding(dp(20), dp(12), dp(20), dp(12))
    sv.addView(t)
    AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton("OK", null).show()
}

fun MainActivity.aiSearch() {
    val e = EditText(this)
    e.hint = "Ask anything"
    AlertDialog.Builder(this).setTitle("AI Search").setView(e)
        .setPositiveButton("Ask") { _, _ ->
            toast("Thinking...")
            callGemini(e.text.toString()) { r -> showText("AI", r) }
        }.setNegativeButton("Cancel", null).show()
}

fun MainActivity.aiTranslate() {
    val t = tabs[cur]
    if (t.home) { toast("Open a page first"); return }
    val langs = arrayOf("Hindi", "English", "Marathi", "Urdu")
    AlertDialog.Builder(this).setTitle("Translate to").setItems(langs) { _, i ->
        t.web.evaluateJavascript("document.body.innerText") { v ->
            var txt = v ?: ""
            try { txt = JSONArray("[" + v + "]").getString(0) } catch (e: Exception) { }
            toast("Translating...")
            callGemini("Translate this web page text to " + langs[i] + ":\n\n" + txt.take(6000)) { r ->
                showText("Translation", r)
            }
        }
    }.show()
}

fun MainActivity.imageSearch() {
    val e = EditText(this)
    e.hint = "Search images"
    AlertDialog.Builder(this).setTitle("Image search").setView(e)
        .setPositiveButton("Search") { _, _ ->
            load("https://www.google.com/search?tbm=isch&q=" + android.net.Uri.encode(e.text.toString()))
        }.setNegativeButton("Cancel", null).show()
}

fun MainActivity.pickTheme() {
    val names = arrayOf("Light gray", "Sky blue", "Mint", "Peach", "Lavender")
    val cols = arrayOf("#F4F5F7", "#DCEBFF", "#DDF5E6", "#FFE8D6", "#E8DFFF")
    AlertDialog.Builder(this).setTitle("Theme").setItems(names) { _, i ->
        prefs.edit().putString("theme", cols[i]).apply()
        refresh()
    }.show()
}
