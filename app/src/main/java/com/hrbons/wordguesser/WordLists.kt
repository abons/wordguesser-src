package com.hrbons.wordguesser

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * Language catalog + on-demand word-list downloading.
 *
 * Each language either ships in-app ([Language.url] == null, uses [WordBank]) or is
 * downloaded from a plain-text online source the first time it's picked, then filtered to
 * 5-letter words and cached on disk (see MainActivity) so later launches work offline.
 *
 * "Strict mode" (reject non-words) is only offered for downloaded languages, because the
 * built-in list is intentionally small.
 *
 * Diacritics: with [Language.fold] on, accents are stripped to the base letter so the word
 * is typeable on the plain A–Z keyboard (German ä→a, ö→o, ü→u). Letters in [Language.extra]
 * are kept as-is and get their own on-screen key (German ß). This keeps word length intact
 * (unlike the ß→ss expansion that String.uppercase() would do).
 *
 * To add a language: append a [Language] with a raw-text URL whose lines are one word each
 * (optionally hunspell `word/FLAGS` form — flags are stripped).
 */
object WordLists {

    data class Language(
        val code: String,          // unique id, used for cache filename + saved preference
        val badge: String,         // short text on the header button, e.g. "DE"
        val label: String,         // full name in the picker
        val url: String?,          // download source, or null for the built-in list
        val extra: String = "",    // extra letters kept verbatim + shown as keys (e.g. "ß")
        val fold: Boolean = false, // strip diacritics to base letters (ä→a, é→e), keep `extra`
        val minLen: Int = 5,       // selectable word-length range (counts stay large within it)
        val maxLen: Int = 5,
        val source: String = "",     // attribution: where the list comes from
        val license: String = "",    // attribution: the list's license (name)
        val homepage: String = "",   // attribution: link to the source
        val licenseUrl: String = ""  // attribution: link to the full license text
    )

    private const val WOO_TREE = "https://github.com/wooorm/dictionaries/tree/main/dictionaries"
    private const val WOO_BLOB = "https://github.com/wooorm/dictionaries/blob/main/dictionaries"

    /** Compact factory for the wooorm/dictionaries languages (hunspell, GPL/LGPL, folding). */
    private fun woo(
        code: String, badge: String, label: String, minLen: Int, maxLen: Int,
        extra: String = "", license: String = "GPL / LGPL"
    ) = Language(
        code, badge, label, "$WOOORM/$code/index.dic",
        extra = extra, fold = true, minLen = minLen, maxLen = maxLen,
        source = "wooorm/dictionaries · $code", license = license,
        homepage = "$WOO_TREE/$code", licenseUrl = "$WOO_BLOB/$code/license"
    )

    // wooorm/dictionaries hosts hunspell lists for dozens of languages (word/FLAGS form).
    private const val WOOORM =
        "https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries"

    // Length ranges chosen so every length in [min,max] still has thousands of words.
    // `license`/`source`/`homepage` power the in-app "Sources & licenses" attribution.
    val LANGUAGES: List<Language> = listOf(
        Language(
            "en_builtin", "EN", "English (built-in)", null,
            source = "Built-in list", license = "—"
        ),
        Language(
            "en", "EN", "English (large — dwyl)",
            "https://raw.githubusercontent.com/dwyl/english-words/master/words_alpha.txt",
            minLen = 4, maxLen = 8,
            source = "dwyl/english-words", license = "Unlicense (public domain)",
            homepage = "https://github.com/dwyl/english-words",
            licenseUrl = "https://unlicense.org/"
        ),
        Language(
            "nl", "NL", "Nederlands (OpenTaal)",
            // Approved base words only — far fewer abbreviations / obscure inflections.
            "https://raw.githubusercontent.com/OpenTaal/opentaal-wordlist/master/elements/basiswoorden-gekeurd.txt",
            minLen = 4, maxLen = 8,
            source = "OpenTaal (approved base words)", license = "CC BY 3.0 / BSD",
            homepage = "https://github.com/OpenTaal/opentaal-wordlist",
            licenseUrl = "https://creativecommons.org/licenses/by/3.0/"
        ),
        Language(
            "fr", "FR", "Français",
            "https://raw.githubusercontent.com/lorenbrichter/Words/master/Words/fr.txt",
            fold = true, minLen = 5, maxLen = 8, // é→e, ç→c … ; accents shown once a word matches
            source = "lorenbrichter/Words", license = "CC0 1.0 (public domain)",
            homepage = "https://github.com/lorenbrichter/Words",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/"
        ),
        Language(
            "de", "DE", "Deutsch",
            "https://raw.githubusercontent.com/enz/german-wordlist/master/words",
            extra = "ß", fold = true, minLen = 4, maxLen = 8,
            source = "enz/german-wordlist", license = "CC0 1.0 (public domain)",
            homepage = "https://github.com/enz/german-wordlist",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/"
        ),
        Language(
            "es", "ES", "Español",
            "https://raw.githubusercontent.com/lorenbrichter/Words/master/Words/es.txt",
            fold = true, minLen = 4, maxLen = 8, // á é í ó ú ü ñ → base letters (ñ→n)
            source = "lorenbrichter/Words", license = "CC0 1.0 (public domain)",
            homepage = "https://github.com/lorenbrichter/Words",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/"
        ),
        // ---- Tier 1: accents fold cleanly to A–Z, no extra keys ----
        woo("it", "IT", "Italiano", 5, 8),
        woo("pt", "PT", "Português", 4, 8),
        woo("ca", "CA", "Català", 4, 8),
        woo("ro", "RO", "Română", 4, 8),
        woo("sv", "SV", "Svenska", 4, 8),
        woo("cs", "CS", "Čeština", 4, 8),
        woo("sk", "SK", "Slovenčina", 4, 8),
        // ---- Tier 2: fold + a key for the letter(s) that can't fold ----
        woo("da", "DA", "Dansk", 4, 8, extra = "ÆØ"),
        woo("nb", "NB", "Norsk", 4, 8, extra = "ÆØ"),
        woo("pl", "PL", "Polski", 4, 8, extra = "Ł"),
        woo("hr", "HR", "Hrvatski", 5, 8, extra = "Đ")
    )

    /**
     * Downloads the list for [lang], keeping typeable words whose length is in
     * [Language.minLen]..[Language.maxLen] (uppercased, de-duped). Retries a couple of times
     * on network failure. The game filters this to the chosen length at play time.
     * Runs on a background thread — never on the UI thread.
     */
    @Throws(IOException::class)
    fun fetchAndFilter(lang: Language): List<String> {
        val urlStr = lang.url ?: return emptyList()
        var last: IOException? = null
        for (attempt in 1..3) {
            try {
                return download(urlStr, lang)
            } catch (e: IOException) {
                last = e
                if (attempt < 3) {
                    try {
                        Thread.sleep(700L * attempt) // simple linear backoff
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                }
            }
        }
        throw last ?: IOException("Download failed")
    }

    private fun download(urlStr: String, lang: Language): List<String> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WordGuesser")
        }
        try {
            val status = conn.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status")
            val out = LinkedHashSet<String>() // preserves order, drops duplicates
            conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val w = clean(line, lang.extra, lang.fold, lang.minLen, lang.maxLen)
                    if (w != null) out.add(w)
                }
            }
            return out.toList()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Strips hunspell flags, validates the word, and returns the ORIGINAL (accented) form,
     * uppercased. The *typeable* form (accents folded to base letters) decides validity +
     * length, but we store the original so a matched word can be shown with its umlauts
     * (ä/ö/ü) even though the player types a/o/u.
     */
    internal fun clean(raw: String, extra: String, fold: Boolean, minLen: Int, maxLen: Int): String? {
        var w = raw
        val slash = w.indexOf('/')
        if (slash >= 0) w = w.substring(0, slash)
        w = w.trim()
        if (w.isEmpty()) return null
        // Skip proper nouns, acronyms and Roman numerals: common words are lowercase in the
        // source, so any uppercase letter marks an entry we don't want as an answer.
        if (w.any { it.isUpperCase() }) return null
        val folded = if (fold) stripDiacritics(w) else w
        if (folded.length !in minLen..maxLen) return null
        for (c in folded) {
            val u = c.uppercaseChar()
            // `extra` holds the uppercase forms of letters kept verbatim (ß, Æ, Ø, Ł, Đ …).
            if (u !in 'A'..'Z' && u !in extra) return null
        }
        return upperNoExpand(w) // e.g. "schön" -> "SCHÖN", "brønd" -> "BRØND"
    }

    /** The form the player actually types: accents folded to base letters, `extra` (ß) kept. */
    fun typeableForm(word: String, lang: Language): String {
        val base = if (lang.fold) stripDiacritics(word) else word
        return upperNoExpand(base)
    }

    /** Uppercases per char so 'ß' stays 'ß' (String.uppercase() would expand it to "SS"). */
    private fun upperNoExpand(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(c.uppercaseChar())
        return sb.toString()
    }

    private val COMBINING = Regex("\\p{Mn}+")

    /** ä→a, ö→o, ü→u, é→e … ; leaves ß untouched (it has no diacritic to strip). */
    private fun stripDiacritics(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING, "")
}
