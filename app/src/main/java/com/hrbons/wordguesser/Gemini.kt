package com.hrbons.wordguesser

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Gemini (Google Generative Language API) client for the word lookup.
 * Uses Android's built-in org.json — no extra dependencies. Runs off the UI thread.
 *
 * The API key comes from BuildConfig.GEMINI_KEY (set via local.properties → not in git).
 * Note: the key still ships in the APK, so it's extractable — fine for a hobby app only.
 */
object Gemini {
    private const val MODEL = "gemini-2.0-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    fun hasKey(): Boolean = BuildConfig.GEMINI_KEY.isNotBlank()

    /** Returns a short definition + translation for [word]. Throws on network/API errors. */
    @Throws(IOException::class)
    fun defineAndTranslate(word: String, sourceLang: String, targetLang: String): String {
        val prompt = "The word \"$word\" is $sourceLang. In plain text (no markdown), give:\n" +
            "1) a concise definition (one line);\n" +
            "2) its $targetLang translation.\n" +
            "Keep it under 50 words."

        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        )

        val conn = (URL("$ENDPOINT?key=${BuildConfig.GEMINI_KEY}").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw IOException(extractError(text) ?: "HTTP $code")
            return extractText(text) ?: throw IOException("Empty response")
        } finally {
            conn.disconnect()
        }
    }

    private fun extractText(json: String): String? = try {
        JSONObject(json)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text").trim()
    } catch (e: Exception) {
        null
    }

    private fun extractError(json: String): String? = try {
        JSONObject(json).getJSONObject("error").getString("message")
    } catch (e: Exception) {
        null
    }
}
