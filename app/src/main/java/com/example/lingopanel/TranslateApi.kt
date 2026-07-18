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
    // jadi kita pakai mirror komunitas yang masih gratis tanpa API key.
    private const val BASE_URL = "https://translate.terraprint.co"

    private fun readErrorBody(conn: HttpURLConnection): String {
        return try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Deteksi bahasa dari teks memakai endpoint publik LibreTranslate (mirror gratis).
     * Ada rate limit — kalau gagal, fungsi ini melempar exception dan pemanggil
     * sebaiknya fallback ke bahasa default (misal "en").
     */
    suspend fun detectLanguage(text: String): String = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL/detect")
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
            val err = readErrorBody(conn)
            throw RuntimeException("Deteksi bahasa gagal (HTTP $code) $err")
        }

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONArray(response)
        if (arr.length() == 0) throw RuntimeException("Bahasa tidak terdeteksi")
        arr.getJSONObject(0).getString("language")
    }

    /**
     * Terjemahkan teks memakai endpoint publik LibreTranslate (mirror gratis).
     * Ada rate limit — kalau gagal, fungsi ini melempar exception dan pemanggil
     * sebaiknya menampilkan pesan error ke pengguna.
     */
    suspend fun translate(text: String, source: String, target: String): String =
        withContext(Dispatchers.IO) {
            val url = URL("$BASE_URL/translate")
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
                throw RuntimeException("Terjemahan gagal (HTTP $code) $err")
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            json.getString("translatedText")
        }
}
