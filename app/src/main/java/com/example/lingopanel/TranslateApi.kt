package com.example.lingopanel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TranslateApi {

    // Server publik resmi libretranslate.com sekarang wajib API key berbayar,
    // jadi kita pakai beberapa mirror komunitas gratis sebagai fallback.
    // Kalau satu server down/error, otomatis coba server berikutnya di daftar ini.
    private val BASE_URLS = listOf(
        "https://translate.terraprint.co",
        "https://libretranslate.de",
        "https://lt.vern.cc"
    )

    private fun readErrorBody(conn: HttpURLConnection): String {
        return try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Deteksi bahasa dari teks. Coba tiap server di BASE_URLS satu-satu
     * sampai ada yang berhasil; kalau semua gagal, lempar exception terakhir.
     */
    suspend fun detectLanguage(text: String): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (base in BASE_URLS) {
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
                if (code !in 200..299) {
                    throw RuntimeException("HTTP $code dari $base")
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(response)
                if (arr.length() == 0) throw RuntimeException("Bahasa tidak terdeteksi")
                return@withContext arr.getJSONObject(0).getString("language")
            } catch (e: Exception) {
                lastError = e
                // lanjut coba server berikutnya
            }
        }
        throw lastError ?: RuntimeException("Deteksi bahasa gagal, semua server tidak merespons")
    }

    /**
     * Terjemahkan teks. Coba tiap server di BASE_URLS satu-satu
     * sampai ada yang berhasil; kalau semua gagal, lempar exception terakhir.
     */
    suspend fun translate(text: String, source: String, target: String): String =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            for (base in BASE_URLS) {
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
                        val err = readErrorBody(conn)
                        throw RuntimeException("HTTP $code dari $base: $err")
                    }

                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    return@withContext json.getString("translatedText")
                } catch (e: Exception) {
                    lastError = e
                    // lanjut coba server berikutnya
                }
            }
            throw lastError ?: RuntimeException("Terjemahan gagal, semua server tidak merespons")
        }
}
