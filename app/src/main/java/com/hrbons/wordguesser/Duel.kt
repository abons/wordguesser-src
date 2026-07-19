package com.hrbons.wordguesser

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Online turn-by-turn duel over Firebase Realtime Database — REST only, no client SDK, so the
 * APK stays tiny and F-Droid-safe (just [HttpURLConnection], like [Leaderboard]).
 *
 * Two phones share one "room" keyed by a short code:
 *
 *     rooms/<CODE>/
 *       lang, len            language + word length
 *       target, targetOrig   folded form both apps score against + original spelling for the reveal
 *       starter              0 = host guesses first, 1 = guest (coin toss, fixed at creation)
 *       status               waiting | playing | done
 *       winner               0 | 1 | -1 (draw); absent until the game ends
 *       moves/<n>            { by: 0|1, guess: "CRANE" }   (n = 0,1,2,… in play order)
 *
 * Both apps know `target`, so each scores its own guess locally with [Wordle.evaluate]; only the
 * guessed *word* (plus who played it) crosses the wire and the other app recomputes identical
 * colours. A guest could read `target` from the room and cheat — the same accepted trade-off as
 * the dreamlo write key that already ships in the APK (casual game, no sensitive data).
 *
 * Turn-by-turn isn't latency-sensitive, so callers short-poll (see [fetchRoom]) only while it's
 * the opponent's turn or while waiting for a join — never on their own turn.
 */
object Duel {
    /** Room-code alphabet: no 0/O/1/I so a code is easy to read aloud and type. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val CODE_LEN = 4

    /** True when a Firebase URL was configured (BuildConfig, from local.properties). */
    fun enabled(): Boolean = BuildConfig.FIREBASE_DB_URL.isNotBlank()

    /** A random room code from the unambiguous alphabet. Deterministic given [rng] (for tests). */
    fun newCode(rng: Random = Random.Default): String =
        buildString { repeat(CODE_LEN) { append(ALPHABET[rng.nextInt(ALPHABET.length)]) } }

    data class Move(val by: Int, val guess: String)
    data class Room(
        val lang: String,
        val len: Int,
        val target: String,
        val targetOrig: String,
        val starter: Int,
        val status: String,
        val winner: Int?,       // null until the game ends
        val round: Int,         // bumped by the host on each rematch (0 = first game)
        val moves: List<Move>,
    )

    // --- REST plumbing --------------------------------------------------------------------

    private fun base() = BuildConfig.FIREBASE_DB_URL.trimEnd('/')
    private fun roomUrl(code: String) = "${base()}/rooms/$code.json"
    private fun moveUrl(code: String, n: Int) = "${base()}/rooms/$code/moves/$n.json"

    /** GET/PUT/DELETE with three retries (HttpURLConnection has no PATCH, so PATCH goes out as a
     *  POST with Firebase's X-HTTP-Method-Override header). */
    private fun request(method: String, urlStr: String, body: String?, override: String? = null): String {
        var last: IOException? = null
        for (attempt in 1..3) {
            try {
                return requestOnce(method, urlStr, body, override)
            } catch (e: IOException) {
                last = e
                if (attempt < 3) try {
                    Thread.sleep(400L * attempt)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt(); throw e
                }
            }
        }
        throw last ?: IOException("Request failed")
    }

    private fun requestOnce(method: String, urlStr: String, body: String?, override: String?): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = method
            setRequestProperty("User-Agent", "WordGuesser")
            if (override != null) setRequestProperty("X-HTTP-Method-Override", override)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    // --- Operations -----------------------------------------------------------------------

    /** Creates (or, on rematch, fully resets) the room. A full PUT replaces the whole node, so any
     *  previous round's `moves` are cleared. The coin toss for who guesses first is fixed here via
     *  [starter]; [round] is bumped by the host on each rematch. */
    @Throws(IOException::class)
    fun createRoom(
        code: String, lang: String, len: Int, target: String, targetOrig: String,
        starter: Int, round: Int = 0,
    ) {
        val obj = JSONObject()
            .put("lang", lang).put("len", len)
            .put("target", target).put("targetOrig", targetOrig)
            .put("starter", starter).put("round", round).put("status", "waiting")
        request("PUT", roomUrl(code), obj.toString())
    }

    /** Reads a room, or null if it doesn't exist. */
    @Throws(IOException::class)
    fun fetchRoom(code: String): Room? {
        val body = request("GET", roomUrl(code), null)
        if (body.isBlank() || body == "null") return null
        return parseRoom(body)
    }

    /** Guest marks the room as started (host is polling for this). */
    @Throws(IOException::class)
    fun joinRoom(code: String) = patch(code, JSONObject().put("status", "playing"))

    /** Writes move [n] (the mover's own guess). */
    @Throws(IOException::class)
    fun pushMove(code: String, n: Int, by: Int, guess: String) {
        request("PUT", moveUrl(code, n), JSONObject().put("by", by).put("guess", guess).toString())
    }

    /** Marks the game finished so both ends converge even if one player abandons. */
    @Throws(IOException::class)
    fun finish(code: String, winner: Int) =
        patch(code, JSONObject().put("status", "done").put("winner", winner))

    /** Host removes the room once the game is over (the Spark plan has no auto-cleanup). Best
     *  effort — a leftover room is harmless (a few hundred bytes). */
    fun deleteRoom(code: String) {
        try {
            request("DELETE", roomUrl(code), null)
        } catch (_: IOException) {
        }
    }

    private fun patch(code: String, obj: JSONObject) {
        request("POST", roomUrl(code), obj.toString(), override = "PATCH")
    }

    /** Parses a room's JSON. `moves` is keyed by "0","1",… — but Firebase returns *contiguous
     *  zero-based integer keys as a JSON ARRAY*, so we accept both shapes: an array (index == move
     *  index) and an object (keys sorted numerically to preserve play order). */
    internal fun parseRoom(body: String): Room {
        val o = JSONObject(body)
        val moves = ArrayList<Move>()
        when (val mv = o.opt("moves")) {
            is org.json.JSONArray -> for (i in 0 until mv.length()) {
                mv.optJSONObject(i)?.let { moves.add(Move(it.optInt("by", 0), it.optString("guess", ""))) }
            }
            is JSONObject -> for (k in mv.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted()) {
                val m = mv.getJSONObject(k.toString())
                moves.add(Move(m.optInt("by", 0), m.optString("guess", "")))
            }
        }
        return Room(
            lang = o.optString("lang", ""),
            len = o.optInt("len", 0),
            target = o.optString("target", ""),
            targetOrig = o.optString("targetOrig", ""),
            starter = o.optInt("starter", 0),
            status = o.optString("status", "waiting"),
            winner = if (o.has("winner")) o.optInt("winner") else null,
            round = o.optInt("round", 0),
            moves = moves,
        )
    }
}
