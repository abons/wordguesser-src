package com.hrbons.wordguesser

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Public arcade-style leaderboard via dreamlo (a free, no-signup service).
 *
 * One dreamlo board holds every daily entry; each entry's name encodes the language, day and
 * length as "<NAME>_<lang>_<yyyymmdd>_<len>" so we can filter to a single daily (per language)
 * client-side and rank by fewest guesses, then fastest time. Only solved games are submitted.
 *
 * NOTE: this free board is HTTP-only (see the network-security-config), and the write key
 * ships in the app — fine for a casual leaderboard, but a determined user could tamper.
 */
object Leaderboard {
    private const val PRIVATE = "SLJX0Xo9sEWhtxtRJj3LwAkzBc3zk7WU-gRsRUyvkqig"
    private const val PUBLIC = "6a55696e8f40bc1318992e79"
    private const val BASE = "http://dreamlo.com/lb"

    data class Entry(val name: String, val guesses: Int, val seconds: Int)

    /** Uppercase A–Z/0–9, max 10 chars; falls back to ANON. */
    fun sanitizeName(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(10).ifEmpty { "ANON" }

    private fun read(url: String): String {
        var last: IOException? = null
        for (attempt in 1..3) {
            try {
                return readOnce(url)
            } catch (e: IOException) {
                last = e
                if (attempt < 3) {
                    try {
                        Thread.sleep(500L * attempt)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                }
            }
        }
        throw last ?: IOException("Request failed")
    }

    private fun readOnce(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "WordGuesser")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    fun submit(name: String, guesses: Int, seconds: Int, langCode: String, dateKey: String, len: Int) {
        val entry = "${sanitizeName(name)}_${langCode}_${dateKey}_$len"
        val body = read("$BASE/$PRIVATE/add/$entry/$guesses/$seconds")
        if (body.startsWith("ERROR")) throw IOException(body.trim())
    }

    /** Entries for one language + day + length, ranked by fewest guesses then fastest time. */
    @Throws(IOException::class)
    fun fetch(langCode: String, dateKey: String, len: Int): List<Entry> =
        rank(read("$BASE/$PUBLIC/pipe"), langCode, dateKey, len)

    // --- Timed mode: a single global, weekly board (most words solved in one 5-minute run) ---
    //
    // Shares the same dreamlo board; timed entries are tagged "<NAME>_T_<weekKey>" so they never
    // collide with a daily entry (which always ends in a numeric length). Score = words solved.

    @Throws(IOException::class)
    fun submitTimed(name: String, words: Int, weekKey: String) {
        val entry = "${sanitizeName(name)}_T_$weekKey"
        // dreamlo ranks by highest score by default; words is exactly that, so no seconds needed.
        val body = read("$BASE/$PRIVATE/add/$entry/$words/0")
        if (body.startsWith("ERROR")) throw IOException(body.trim())
    }

    /** This week's timed entries, ranked by most words solved. */
    @Throws(IOException::class)
    fun fetchTimed(weekKey: String): List<Entry> = rankTimed(read("$BASE/$PUBLIC/pipe"), weekKey)

    /** Parses dreamlo pipe output, filters to the given week's timed entries and ranks by most
     *  words solved (stored in the score field). */
    internal fun rankTimed(pipeBody: String, weekKey: String): List<Entry> {
        val suffix = "_T_$weekKey"
        val out = ArrayList<Entry>()
        for (line in pipeBody.lineSequence()) {
            if (line.isBlank()) continue
            val f = line.split("|")
            if (f.size < 3 || !f[0].endsWith(suffix)) continue
            val words = f[1].toIntOrNull() ?: continue
            out.add(Entry(f[0].removeSuffix(suffix), words, f[2].toIntOrNull() ?: 0))
        }
        out.sortWith(compareByDescending { it.guesses })
        return out
    }

    /** Parses dreamlo pipe output (name|score|seconds|text|datetime|rank), filters to the given
     *  language + day + length (encoded in the name suffix) and ranks by fewest guesses then
     *  fastest time. */
    internal fun rank(pipeBody: String, langCode: String, dateKey: String, len: Int): List<Entry> {
        val suffix = "_${langCode}_${dateKey}_$len"
        val out = ArrayList<Entry>()
        for (line in pipeBody.lineSequence()) {
            if (line.isBlank()) continue
            val f = line.split("|")
            if (f.size < 3 || !f[0].endsWith(suffix)) continue
            val guesses = f[1].toIntOrNull() ?: continue
            out.add(Entry(f[0].removeSuffix(suffix), guesses, f[2].toIntOrNull() ?: 0))
        }
        out.sortWith(compareBy({ it.guesses }, { it.seconds }))
        return out
    }
}
