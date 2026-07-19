package com.example.lingopanel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TranslateApi {

    // Server publik resmi libretranslate.com sekarang wajib API key berbayar.
    // Ini daftar mirror LibreTranslate gratis (dari docs.libretranslate.com/community/mirrors)
    // yang dicoba satu-satu sampai ada yang merespons.
    private val LIBRE_BASE_URLS = listOf(
        "https://translate.fedilab.app",
        "https://translate.cutie.dating"
    )

    private fun readErrorBody(conn: HttpURLConnection): String {
        return try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun detectLanguage(text: String): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (base in LIBRE_BASE_URLS) {
            try {
                val url = URL("$base/detect")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val body = "q=" + URLEncoder.encode(text, "UTF-8")
                conn.outputStream.use { it.write(body.toByteArray()) }

                val code = conn.responseCode
                if (code !in 200..299) throw RuntimeException("HTTP $code dari $base")

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(response)
                if (arr.length() == 0) throw RuntimeException("Bahasa tidak terdeteksi")
                return@withContext arr.getJSONObject(0).getString("language")
            } catch (e: Exception) {
                lastError = e
            }
        }
        // Semua mirror LibreTranslate gagal -> jangan bikin user stuck, default ke Inggris
        throw lastError ?: RuntimeException("Deteksi bahasa gagal")
    }

    /**
     * Terjemahkan teks. Urutan coba: mirror-mirror LibreTranslate dulu,
     * kalau semuanya down baru fallback ke MyMemory (penyedia lain, gratis,
     * tanpa API key) supaya user tetap dapat hasil terjemahan.
     */
    suspend fun translate(text: String, source: String, target: String): String =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null

            for (base in LIBRE_BASE_URLS) {
                try {
                    val url = URL("$base/translate")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000

                    val body = JSONObject().apply {
                        put("q", text)
                        put("source", source)
                        put("target", target)
                        put("format", "text")
                    }
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw RuntimeException("HTTP $code dari $base: ${readErrorBody(conn)}")
                    }

                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    return@withContext json.getString("translatedText")
                } catch (e: Exception) {
                    lastError = e
                }
            }

            // Fallback terakhir: MyMemory (gratis, tanpa API key, penyedia berbeda)
            try {
                return@withContext translateViaMyMemory(text, source, target)
            } catch (e: Exception) {
                lastError = e
            }

            throw lastError ?: RuntimeException("Terjemahan gagal, semua server tidak merespons")
        }

    private fun translateViaMyMemory(text: String, source: String, target: String): String {
        val q = URLEncoder.encode(text, "UTF-8")
        val langpair = URLEncoder.encode("$source|$target", "UTF-8")
        val url = URL("https://api.mymemory.translated.net/get?q=$q&langpair=$langpair")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val code = conn.responseCode
        if (code !in 200..299) throw RuntimeException("HTTP $code dari MyMemory")

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        return json.getJSONObject("responseData").getString("translatedText")
    }
}
