package com.hrbons.wordguesser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.random.Random
import org.json.JSONObject

/**
 * A self-contained Wordle-style word guessing game.
 *
 * The entire UI is built in code (no XML layouts, no AppCompat/Compose/Material) to keep
 * the app as light as possible. 6 guesses to find a hidden 5-letter word; tiles reveal
 * green (right spot), yellow (wrong spot) or gray (absent) after each guess.
 */
class MainActivity : Activity() {

    internal companion object {
        const val ROWS = 6
        const val DEFAULT_LEN = 5
        const val TIMED_DURATION_MS = 5 * 60 * 1000L // timed-mode sprint length

        // Grammatical words that make up formulaic Dutch "form-of" glosses, e.g.
        // "tweede persoon enkelvoud tegenwoordige tijd van rollen" or "verbogen vorm
        // van de stellende trap van aardig". Such glosses describe an inflected form
        // rather than a meaning, so we resolve them to the base word's real definition
        // (see formOfBase / resolveFormOf). A gloss counts as form-of only when EVERY
        // word before the final "… van <base>" is in this set — real definitions such
        // as "gemaakt van beton" or "het vormen van bubbels" contain content words and
        // are left untouched. ("vormen" is deliberately absent for that reason.)
        val FORM_OF_WORDS = setOf(
            "van", "de", "het",
            "eerste", "tweede", "derde", "persoon",
            "enkelvoud", "meervoud",
            "tegenwoordige", "verleden", "tijd",
            "voltooid", "onvoltooid", "deelwoord",
            "verbogen", "onverbogen", "vorm",
            "stellende", "vergrotende", "overtreffende", "trap",
            "partitief", "genitief", "datief", "nominatief", "accusatief",
            "aanvoegende", "gebiedende", "wijs",
            "vervoeging", "verbuiging",
            "beklemtoonde", "onbeklemtoonde", "nadrukkelijke", "onnadrukkelijke",
            "onzijdig", "mannelijk", "vrouwelijk", "mannelijke", "vrouwelijke",
            "onpersoonlijke", "persoonlijke",
            "verkleinvorm", "verkleinwoord", "verkorting",
            "verouderde", "oude", "dialectvorm", "dialectische",
            "schrijfwijze", "spelling", "spellingvariant", "spellingsvariant",
            "zelfstandig", "naamwoord", "bijvoeglijk", "werkwoord", "bijwoord", "telwoord",
        )

        /**
         * If [def] is a purely grammatical "… van <base>" form-of gloss, returns the
         * lower-cased <base> word; otherwise null. The base is the last token; every
         * word before the final " van " must be in [FORM_OF_WORDS].
         */
        fun formOfBase(def: String): String? {
            val trimmed = def.trim().trimEnd('.')
            val idx = trimmed.lowercase().lastIndexOf(" van ")
            if (idx < 0) return null
            val base = trimmed.substring(idx + 5).trim().lowercase()
            if (base.isEmpty() || base.any { it == ' ' }) return null
            val words = trimmed.substring(0, idx).lowercase().split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty() || words.any { it !in FORM_OF_WORDS }) return null
            return base
        }

        /**
         * Resolves a formulaic form-of gloss (e.g. a verb conjugation or comparative
         * form) to its base word's real definition, keeping the grammatical note. Leaves
         * [def] untouched when it isn't a form-of gloss, when the base has no entry, or
         * when the base itself is only another form-of gloss (avoids cryptic chaining).
         */
        fun resolveFormOf(def: String, map: Map<String, String>): String {
            val base = formOfBase(def) ?: return def
            val baseDef = map[base]?.trim().orEmpty()
            if (baseDef.isEmpty() || formOfBase(baseDef) != null) return def
            return "${def.trim().trimEnd('.')}:\n\n$baseDef"
        }

        // Palette (classic dark Wordle look).
        const val BG = 0xFF121213.toInt()
        const val TEXT = 0xFFFFFFFF.toInt()
        const val TILE_BORDER_EMPTY = 0xFF3A3A3C.toInt()
        const val TILE_BORDER_FILLED = 0xFF565758.toInt()
        const val CORRECT = 0xFF538D4E.toInt()
        const val PRESENT = 0xFFB59F3B.toInt()
        const val ABSENT = 0xFF3A3A3C.toInt()
        // High-contrast (colour-blind) palette — Wordle's own accessible orange/blue. Swapped in
        // for CORRECT/PRESENT tile & key fills when the setting is on (see correctColor/presentColor).
        const val CORRECT_HC = 0xFFF5793A.toInt() // orange
        const val PRESENT_HC = 0xFF85C0F9.toInt() // blue
        const val DIVIDER = 0xFF3A3A3C.toInt()    // thin separators in option-list dialogs
        const val KEY_DEFAULT = 0xFF818384.toInt()
        const val HINT_COLOR = 0xFF9AA0A6.toInt()
        const val LINK_COLOR = 0xFF8AB4F8.toInt() // tappable/accent text in dialogs (e.g. "↓" download)
        const val SHEET_BG = 0xFF2A2A2C.toInt()   // surface behind every bottom-sheet dialog
        const val DAILY = 0xFF4E7AC7.toInt() // active length in daily mode (gold = normal mode)
        const val DAILY_WON = 0xFF3DDC84.toInt()  // ✓ badge — today's daily solved
        const val DAILY_LOST = 0xFFFF5A5A.toInt() // – badge — today's daily played but not solved

        // Tile evaluation states.
        const val ST_ABSENT = 0
        const val ST_PRESENT = 1
        const val ST_CORRECT = 2

        val KEY_ROWS = arrayOf(
            "QWERTYUIOP",
            "ASDFGHJKL",
            "ZXCVBNM"
        )

        const val PREFS = "wordguesser"
        const val PREF_LANG = "lang"
        const val PREF_STRICT = "strict"
        const val PREF_LEN = "wordlen"
        const val PREF_HARD = "hard"
        const val PREF_HIGHCONTRAST = "highcontrast"
        const val PREF_NAME = "player_name"
        const val PREF_SUPPORT_LAST = "support_last"
        const val PREF_CHIP_HINT_SEEN = "chip_hint_seen"
        const val PREF_HOWTO_SEEN = "howto_seen"
        const val PREF_RECENT_LANGS = "recent_langs"
        const val GEMINI_APP = "com.google.android.apps.bard"
        const val KOFI_URL = "https://ko-fi.com/hrbons"
    }

    private lateinit var tiles: Array<Array<TextView>>
    private lateinit var tileState: Array<IntArray>   // last evaluated state per tile (-1 = uncoloured)
    private lateinit var hintButtons: Array<TextView>
    private lateinit var countViews: Array<TextView> // hard-mode "in word / in place" line per row
    private lateinit var rowIcons: Array<TextView>   // duel: who played the row (👤/🤖), leading the row
    private val keyButtons = HashMap<Char, Button>()
    private val keyState = HashMap<Char, Int>()

    private lateinit var target: String      // original spelling (may contain ä/ö/ü/ß)
    private lateinit var targetTyped: String // folded form the player actually types
    private var wordLen = DEFAULT_LEN        // number of tiles / letters per word
    private var currentRow = 0
    private var currentCol = 0
    private var gameOver = false

    private lateinit var keyboard: LinearLayout
    private lateinit var boardContainer: LinearLayout
    private lateinit var revealRow: LinearLayout   // shows the answer + "?" after a loss
    private lateinit var revealWord: TextView
    private lateinit var revealHint: TextView
    private lateinit var messageView: TextView     // transient message pill, above the keyboard
    private val hideMessage = Runnable {
        if (::messageView.isInitialized) messageView.visibility = View.GONE
    }

    // Language / word-list state.
    private lateinit var langButton: Button
    private lateinit var lengthRow: LinearLayout        // quick word-length buttons under the header
    private val lengthButtons = HashMap<Int, Button>()  // length → its button, for highlighting
    private val lengthBadges = HashMap<Int, TextView>() // length → its corner daily-result badge
    private var currentLang: WordLists.Language = WordLists.LANGUAGES[0]
    private var answersAll: List<String> = emptyList()       // all downloaded lengths
    private var answers: List<String> = emptyList()          // original spellings, current length
    private var answersTyped: Set<String> = emptySet()       // folded forms, for matching
    private var typedToOriginal: Map<String, String> = emptyMap()
    // Broader "accept" list (e.g. conjugations): recognised as real words but never targets.
    private var acceptAll: List<String> = emptyList()
    private var acceptTyped: Set<String> = emptySet()        // folded forms, current length
    private var acceptLang: String? = null
    private var loadingDialog: AlertDialog? = null
    private var strictMode = false
    private var hardMode = false
    private var highContrast = false

    // Daily puzzle state. The daily is per language: the word is global for everyone playing the
    // same language on the same UTC day + length (built from that language's answer pool).
    private var dailyMode = false
    private var dailyLen = 0
    private var dailyDateKey = ""
    private var dailyLangCode = ""
    private var dailyTargetWord = ""
    private var dailyStartMs = 0L
    private val dailyGuesses = ArrayList<String>()
    private var dailyPool: Map<Int, List<String>>? = null
    private var dailyPoolLang: String? = null   // which language dailyPool was built for

    // Game mode chosen from the "New" menu. DAILY is entered separately via the length row and
    // tracked by [dailyMode]; the two are mutually exclusive (entering one leaves the other).
    private enum class Mode { NORMAL, TIMED, DUEL }
    private var gameMode = Mode.NORMAL
    private lateinit var modeBar: TextView   // persistent status line (timer / turn), hidden in NORMAL

    // Timed mode: solve as many words as possible before the clock runs out; score → weekly board.
    private var timedEndMs = 0L
    private var timedScore = 0
    private var timedRunning = false
    private val timedTick = object : Runnable {
        override fun run() {
            if (!timedRunning) return
            if (System.currentTimeMillis() >= timedEndMs) { finishTimed(); return }
            updateModeBar()
            modeBar.postDelayed(this, 500)
        }
    }

    // Duel mode: player and NPC alternate guesses at one shared hidden word; first to solve wins.
    private var duelPlayerTurn = true
    private val duelGuesses = ArrayList<String>()   // every typed guess so far (both players)

    // Online duel (vs another human over Firebase — see [Duel]). Reuses [Mode.DUEL]: the opponent
    // simply replaces the NPC. Move index == board row (each move advances one row).
    private var duelOnline = false
    private var duelIsHost = false
    private var duelMyIndex = 0          // 0 = host, 1 = guest
    private var duelRoomCode = ""
    private var duelStarter = 0          // who guesses first this round (0 = host, 1 = guest)
    private var duelRound = 0            // rematch counter (0 = first game); host owns it
    private var duelWaiting = false      // host, before the guest has joined: block input
    private var duelPollGen = 0          // bump to cancel any in-flight poll loop
    private var waitingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        hideSystemBars()
        strictMode = prefs().getBoolean(PREF_STRICT, false)
        hardMode = prefs().getBoolean(PREF_HARD, false)
        highContrast = prefs().getBoolean(PREF_HIGHCONTRAST, false)
        initLanguage()
        // Debug-only screenshot harness: `am start … --es shot <name>` drops the app straight into
        // a named state so docs/screenshots can be regenerated by a script instead of by hand.
        val shot = if (BuildConfig.DEBUG) intent?.getStringExtra("shot") else null
        if (shot != null) applyShot(shot) else maybeShowHowToPlay()
    }

    override fun onDestroy() {
        duelPollGen++   // stop any in-flight online-duel poll loop
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------
    // Screenshot harness (debug builds only) — see update-screenshots.ps1
    // ---------------------------------------------------------------------------------

    /** Types [word] into the current row and submits it, reusing the real input path so the
     *  resulting tiles/colours are exactly what a player would see. */
    private fun shotGuess(word: String) {
        for (ch in word) onLetter(ch.uppercaseChar())
        onEnter()
    }

    /** Pins a deterministic answer so a scenario's guesses always score the same colours. */
    private fun shotTarget(word: String) {
        target = word.uppercase()
        targetTyped = WordLists.typeableForm(target, currentLang)
    }

    /** Renders [guesses] into consecutive rows using the real evaluation path, then parks the
     *  cursor past them (optionally ending the game). */
    private fun shotBoard(target: String, vararg guesses: String, over: Boolean = false) {
        shotTarget(target)
        clearBoard()
        guesses.forEachIndexed { r, g -> renderGuessRow(r, g.uppercase()) }
        currentRow = guesses.size
        currentCol = 0
        gameOver = over
    }

    /** Writes a finished daily result to prefs so its ✓ / – length-row badge shows. */
    private fun shotDailyBadge(len: Int, won: Boolean) {
        val rec = "${if (won) "W" else "L"}|83|4|PLANT|train,plank"
        prefs().edit().putString("daily_${currentLang.code}_${todayKey()}_$len", rec).apply()
    }

    /** Cosmetically shows another language's keyboard + length row at [len] with an empty board
     *  (the built-in English list is fixed at 5 letters, so board-resize / extra-key shots borrow
     *  a 4-8 language's chrome without downloading its word list — the tiles are empty anyway).
     *  Not persisted: every shot force-restarts and reloads the built-in language. */
    private fun shotLangChrome(code: String, len: Int) {
        currentLang = WordLists.LANGUAGES.first { it.code == code }
        langButton.text = currentLang.badge
        populateKeys(); populateLengthRow()
        rebuildForLength(len); updateLengthButtons()
    }

    /** Drives the app into the named screenshot state. Each name maps 1:1 to a docs/screenshots
     *  file. Board-only states just leave the UI in place; modal states open their dialog.
     *  Network-backed dialogs (leaderboards, definitions) are fed stub data so shots stay offline
     *  and deterministic. */
    private fun applyShot(name: String) {
        // Stub leaderboard rows so the boards render without a dreamlo round-trip.
        val timedRows = listOf(
            Leaderboard.Entry("Robin", 14, 0), Leaderboard.Entry("You", 11, 0),
            Leaderboard.Entry("Sam", 9, 0), Leaderboard.Entry("Alex", 7, 0),
        )
        val dailyRows = listOf(
            Leaderboard.Entry("Robin", 3, 41), Leaderboard.Entry("You", 4, 83),
            Leaderboard.Entry("Sam", 4, 126), Leaderboard.Entry("Alex", 5, 210),
        )
        // A fabricated daily badge from an earlier shot must not bleed into this one.
        for (k in prefs().all.keys.toList()) if (k.startsWith("daily_")) prefs().edit().remove(k).apply()
        updateLengthButtons()
        when (name) {
            "empty-board" -> shotTarget("PLANT")
            "midgame" -> shotBoard("PLANT", "TRAIN", "PLANK")
            "win" -> { shotBoard("PLANT", "TRAIN", "PLANK", "PLANT", over = true); toast(winMessage(2)) }
            "loss-reveal" -> {
                shotBoard("PLANT", "TRAIN", "CHOIR", "MOUSE", "BUDGE", "FLECK", "WRYLY", over = true)
                showReveal(target)
            }
            "hard-mode" -> { hardMode = true; populateBoard(); shotBoard("PLANT", "TRAIN", "PLANK") }
            "highcontrast" -> { highContrast = true; shotBoard("PLANT", "TRAIN", "PLANK") }
            "strict-reject" -> { for (ch in "ZEBRA") onLetter(ch); toast("Not in word list") }
            "length-4" -> shotLangChrome("en", 4)
            "length-8" -> shotLangChrome("en", 8)
            "length-8-extrakeys" -> shotLangChrome("de", 8) // "de" adds the ß key row
            "daily-fresh" -> {
                dailyMode = true; clearBoard(); shotTarget("PLANT")
                updateLengthButtons(); toast("Daily · 5 letters")
            }
            "daily-won-badge" -> { shotDailyBadge(5, won = true); updateLengthButtons() }
            "daily-lost-badge" -> { shotDailyBadge(5, won = false); updateLengthButtons() }
            "timed-bar" -> {
                gameMode = Mode.TIMED; timedScore = 4
                timedEndMs = System.currentTimeMillis() + 167_000L
                updateModeBar(); shotBoard("PLANT", "TRAIN")
            }
            "timed-solved" -> {
                gameMode = Mode.TIMED; timedScore = 5
                timedEndMs = System.currentTimeMillis() + 143_000L
                updateModeBar(); shotBoard("PLANT", "PLANT", over = true); toast(winMessage(0))
            }
            "duel-your-turn" -> {
                gameMode = Mode.DUEL; duelPlayerTurn = true
                shotTarget("PLANT"); clearBoard()
                renderGuessRow(0, "CRANE", forceColor = true); labelDuelRow(0, player = false)
                currentRow = 1; updateModeBar()
            }
            "duel-comp-turn" -> {
                gameMode = Mode.DUEL; duelPlayerTurn = false
                shotTarget("PLANT"); clearBoard()
                renderGuessRow(0, "SLATE"); labelDuelRow(0, player = true)
                currentRow = 1; updateModeBar()
            }
            "duel-online-turn" -> {
                gameMode = Mode.DUEL; duelOnline = true; duelPlayerTurn = true
                shotTarget("PLANT"); clearBoard()
                renderGuessRow(0, "SLATE", forceColor = true); labelDuelRow(0, player = true)
                renderGuessRow(1, "CRANE", forceColor = true); labelDuelRow(1, player = false)
                currentRow = 2; updateModeBar()
            }
            "duel-online-waiting" -> {
                gameMode = Mode.DUEL; duelOnline = true; duelIsHost = true; duelWaiting = true
                duelRoomCode = "ABCD"
                shotTarget("PLANT"); clearBoard(); updateModeBar()
                showWaitingDialog("ABCD")
            }
            "modal-newgame" -> showNewGameMenu()
            "modal-language" -> showLanguagePicker()
            "modal-settings" -> showSettings()
            "modal-stats" -> showStats()
            "modal-stats-reset" -> confirmResetStats()
            "modal-sources" -> showSources()
            "modal-howto" -> showHowToPlay()
            "modal-loading" -> showLoading(true)
            "modal-lookup" -> lookUpMenu("plant")
            "modal-definition" -> {
                val nl = WordLists.LANGUAGES.first { it.code == "nl" }
                presentDefinition("hond", nl, mapOf("hond" to "een zoogdier uit de familie van de hondachtigen, vaak als huisdier gehouden"))
            }
            "modal-timeup" -> { gameMode = Mode.TIMED; timedScore = 7; finishTimed() }
            "modal-timed-submit" -> promptSubmitTimed(7)
            "modal-timed-board" -> showTimedLeaderboardDialog(timedRows, "You")
            "modal-duel-result" -> { shotTarget("PLANT"); finishDuel("You win! 🎉") }
            "modal-daily-submit" -> {
                dailyLangCode = currentLang.code; dailyDateKey = todayKey(); dailyLen = 5
                promptSubmit(83, 4)
            }
            "modal-daily-failed" -> showDailyResult(todayKey(), 5, emptyList(), "L|126|6|PLANT|train,choir,mouse,budge,fleck,wryly")
            "modal-daily-played" -> showDailyResult(todayKey(), 5, emptyList(), "W|83|4|PLANT|train,plank,plans,plant")
            "modal-daily-board" -> showLeaderboardDialog(5, dailyRows, "You")
            else -> toast("Unknown shot: $name")
        }
    }

    /** Re-hide the system bars when the window regains focus (e.g. after a dialog). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Immersive fullscreen: hide the status + navigation bars while the app is open.
     * Bars reappear temporarily on a swipe from the edge, then auto-hide again.
     */
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    // ---------------------------------------------------------------------------------
    // UI construction
    // ---------------------------------------------------------------------------------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        root.addView(buildHeader())
        root.addView(buildLengthRow())        // one button per word length, under the header
        root.addView(buildModeBar())          // timer / turn status for timed & duel (hidden otherwise)
        root.addView(spacer(dp(8)))
        root.addView(buildBoard())            // weighted: fills the space between header + keyboard
        root.addView(buildReveal())
        root.addView(buildMessage())          // transient messages sit just above the keyboard
        root.addView(buildKeyboard())

        return root
    }

    private fun buildMessage(): View {
        messageView = TextView(this).apply {
            visibility = View.GONE
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xFF2C2C2E.toInt())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(16), dp(8), dp(16), dp(8))
            val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.setMargins(dp(12), 0, dp(12), dp(8))
            layoutParams = lp
        }
        return messageView
    }

    /** Persistent status line for timed & duel modes (countdown + score, or whose turn it is).
     *  Hidden in normal/daily play. */
    private fun buildModeBar(): View {
        modeBar = TextView(this).apply {
            visibility = View.GONE
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(2))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        return modeBar
    }

    /** Row shown under the board after a loss: the missed word + a "?" to look it up. */
    private fun buildReveal(): View {
        revealRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(12), 0, 0)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        revealWord = TextView(this).apply {
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            letterSpacing = 0.1f
        }
        revealHint = TextView(this).apply {
            text = "?"
            contentDescription = "Look up this word"
            setTextColor(HINT_COLOR)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(dp(12), 0, 0, 0)
        }
        revealRow.addView(revealWord)
        revealRow.addView(revealHint)
        return revealRow
    }

    private fun showReveal(word: String) {
        revealWord.text = word
        revealWord.contentDescription = "The word was $word"
        revealHint.setOnClickListener { lookUpWord(word) }
        revealRow.visibility = View.VISIBLE
    }

    /** Shows a friendly win dialog with a Ko-fi nudge — at most once per (local) day. Used instead
     *  of an always-on coloured row over the board, so gameplay is never covered. Falls back to a
     *  plain toast on days the nudge has already been shown. */
    private fun maybeShowWinSupport(title: String, word: String) {
        val today = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())
        if (prefs().getString(PREF_SUPPORT_LAST, "") == today) { toast(title); return }
        prefs().edit().putString(PREF_SUPPORT_LAST, today).apply()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("The word was $word.\n\nEnjoying Word Guesser?")
            .setPositiveButton("Close", null)
            .setNeutralButton("☕ Support") { _, _ -> openInBrowser(KOFI_URL) }
            .showSheet()
    }

    /** Shows the how-to-play overlay the very first time the app is opened. */
    private fun maybeShowHowToPlay() {
        if (prefs().getBoolean(PREF_HOWTO_SEEN, false)) return
        prefs().edit().putBoolean(PREF_HOWTO_SEEN, true).apply()
        window.decorView.post { showHowToPlay() }
    }

    /** Concise rules + a colour legend. Reachable any time from Settings › How to play. */
    private fun showHowToPlay() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }
        fun para(text: String, topDp: Int = 12) = TextView(this).apply {
            this.text = text
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(topDp), 0, 0)
        }
        // A small coloured example tile followed by an explanation line.
        fun legend(color: Int, letter: String, meaning: String) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = letter
                setTextColor(TEXT)
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                background = tileDrawable(color, color)
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            })
            addView(TextView(this@MainActivity).apply {
                text = meaning
                setTextColor(TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(14), 0, 0, 0)
            })
        }
        box.addView(para("Guess the hidden word in 6 tries. Each guess must be a real word of the " +
            "right length.", topDp = 0))
        box.addView(para("After each guess the tiles show how close you were:"))
        box.addView(legend(correctColor(), "A", "right letter, right spot"))
        box.addView(legend(presentColor(), "B", "in the word, wrong spot"))
        box.addView(legend(ABSENT, "C", "not in the word"))
        box.addView(para("Tap the ? next to a finished row to look up that word."))
        box.addView(para("The number row picks word length. Blue = today's daily puzzle, " +
            "gold = free play."))
        AlertDialog.Builder(this)
            .setTitle("How to play")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Got it", null)
            .showSheet()
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        langButton = smallButton(currentLang.badge) { showLanguagePicker() }
        langButton.contentDescription = "Change language"
        val title = TextView(this).apply {
            text = "Word Guesser"
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            letterSpacing = 0.01f
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        // The per-length row below the header now covers the daily, so the old "📅" picker
        // makes way for a direct Statistics button (daily results count toward these stats too).
        val stats = smallButton("📊") { showStats() }.apply { contentDescription = "Statistics" }
        val settings = smallButton("⚙") { showSettings() }.apply { contentDescription = "Settings" }
        val newGame = smallButton("New") { showNewGameMenu() }
            .apply { contentDescription = "New game" }
        header.addView(langButton)
        header.addView(title)
        header.addView(stats)
        header.addView(settings)
        header.addView(newGame)
        return header
    }

    private fun smallButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(TEXT)
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        background = keyDrawable(KEY_DEFAULT)
        stateListAnimator = null
        minWidth = dp(52)
        minimumWidth = dp(52)
        // Fixed height keeps the emoji buttons (📊/⚙) the same size as the text buttons, so the
        // header reads as one even row instead of a ragged one.
        setPadding(dp(12), 0, dp(12), 0)
        val lp = LinearLayout.LayoutParams(WRAP_CONTENT, dp(46))
        lp.setMargins(dp(2), 0, dp(2), 0)
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    /** Row of quick word-length buttons under the header. Tapping a *different* length jumps you
     *  there: into today's daily if you haven't played it yet, otherwise a normal game. Tapping
     *  the length you're *already* on toggles that length between daily and normal. The active
     *  button is tinted to show which mode it's in (gold = normal, blue = daily). */
    private fun buildLengthRow(): View {
        lengthRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(6) // breathing room under the header so the rows don't touch
            }
        }
        populateLengthRow()
        return lengthRow
    }

    /** (Re)builds the length buttons for the current language's selectable range. */
    private fun populateLengthRow() {
        if (!::lengthRow.isInitialized) return
        lengthRow.removeAllViews()
        lengthButtons.clear()
        lengthBadges.clear()
        for (len in currentLang.minLen..currentLang.maxLen) {
            lengthRow.addView(lengthCell(len))
        }
        updateLengthButtons()
    }

    /** A length button plus a small corner badge showing today's daily result for that length. */
    private fun lengthCell(len: Int): View {
        val btn = Button(this).apply {
            text = len.toString()
            isAllCaps = false
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = keyDrawable(KEY_DEFAULT)
            stateListAnimator = null
            setPadding(0, dp(6), 0, dp(6))
            layoutParams = FrameLayout.LayoutParams(dp(46), WRAP_CONTENT)
            setOnClickListener { onLengthTap(len) }
        }
        val badge = TextView(this).apply {
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            includeFontPadding = false
            setShadowLayer(dp(2).toFloat(), 0f, 0f, 0xFF000000.toInt()) // legible on any tint
            visibility = View.GONE
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dp(4), dp(1))
            }
        }
        lengthButtons[len] = btn
        lengthBadges[len] = badge
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            addView(btn)
            addView(badge) // on top of the button, in its bottom-right corner
        }
    }

    /** Tints the active length's button to show its mode (blue = daily, gold = normal) and puts a
     *  green "✓" / red "–" badge in each button's corner for today's daily result. */
    private fun updateLengthButtons() {
        if (!::lengthRow.isInitialized) return
        val date = todayKey()
        for ((len, btn) in lengthButtons) {
            btn.background = keyDrawable(
                when {
                    len != wordLen -> KEY_DEFAULT
                    dailyMode -> DAILY
                    else -> PRESENT
                }
            )
            val badge = lengthBadges[len]
            when (prefs().getString("daily_${currentLang.code}_${date}_$len", null)?.firstOrNull()) {
                'W' -> {
                    badge?.apply { text = "✓"; setTextColor(DAILY_WON); visibility = View.VISIBLE }
                    btn.contentDescription = "$len letters, daily solved"
                }
                'L' -> {
                    badge?.apply { text = "–"; setTextColor(DAILY_LOST); visibility = View.VISIBLE }
                    btn.contentDescription = "$len letters, daily not solved"
                }
                else -> {
                    badge?.visibility = View.GONE
                    btn.contentDescription = "$len letters"
                }
            }
        }
    }

    /** First time the length row is used, explain what its two active tints mean. Posted with a
     *  small delay so it lands *after* any toast the tap itself fires (and thus stays on screen). */
    private fun maybeShowChipHint() {
        if (prefs().getBoolean(PREF_CHIP_HINT_SEEN, false)) return
        prefs().edit().putBoolean(PREF_CHIP_HINT_SEEN, true).apply()
        messageView.postDelayed({ toast("Blue = today's daily · gold = free play") }, 900)
    }

    /** Handles a tap on the length-[len] button (see [buildLengthRow]). */
    private fun onLengthTap(len: Int) {
        maybeShowChipHint()
        // Tapping the length we're already on toggles it between daily and normal.
        if (len == wordLen) {
            if (dailyMode) startNormalAt(len) else openDailyFor(len)
            return
        }
        // A different length: daily if you haven't played it yet today, otherwise normal.
        val date = todayKey()
        val dailyPlayed = prefs().getString("daily_${currentLang.code}_${date}_$len", null) != null
        if (dailyPlayed) {
            startNormalAt(len)
            return
        }
        ensureDailyPool {
            // Falls back to a normal game if this language has no daily words at this length.
            val pool = dailyPool?.get(len)
            if (pool.isNullOrEmpty()) { startNormalAt(len); return@ensureDailyPool }
            startDaily(date, len, pool)
        }
    }

    /** Enters a normal game at [len]: continues the current one if we're already playing that
     *  length outside a daily, otherwise starts a fresh game at that length. */
    private fun startNormalAt(len: Int) {
        if (gameMode == Mode.NORMAL && !dailyMode && wordLen == len && !gameOver) {
            toast("$len letters"); return // already playing this length — keep going
        }
        prefs().edit().putInt(PREF_LEN, len).apply()
        applyLength() // rebuilds the board, leaves any daily, starts fresh at len
    }

    /** Enters today's daily for [len]: shows the result if already played, else starts it. */
    private fun openDailyFor(len: Int) {
        ensureDailyPool {
            val date = todayKey()
            val pool = dailyPool?.get(len)
            if (pool.isNullOrEmpty()) { toast("No daily · $len letters"); return@ensureDailyPool }
            val saved = prefs().getString("daily_${currentLang.code}_${date}_$len", null)
            if (saved != null) showDailyResult(date, len, pool, saved) else startDaily(date, len, pool)
        }
    }

    private fun buildBoard(): View {
        boardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Fill the space between header and keyboard and centre the rows in it, so the
            // board uses the screen height instead of leaving a large gap at the bottom.
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        populateBoard()
        return boardContainer
    }

    /** Keyboard key height (px). A little shorter when an extra language row is shown, so a
     *  4-row keyboard still fits comfortably. */
    private fun keyHeightPx(): Int {
        val rows = KEY_ROWS.size + if (currentLang.extra.isNotEmpty()) 1 else 0
        return if (rows >= 4) dp(52) else dp(60)
    }

    /** Tile HEIGHT (px): driven only by the vertical space left between the header and the
     *  keyboard, so the board fills the same height regardless of word length. */
    private fun tileHeightPx(): Int {
        val dm = resources.displayMetrics
        val keyRows = KEY_ROWS.size + if (currentLang.extra.isNotEmpty()) 1 else 0
        val keyboardH = keyRows * (keyHeightPx() + dp(8))
        // Hard mode shows a count line ("N in word · M in right place") under every row; reserve
        // its height for all ROWS so a full board doesn't overflow off the bottom.
        val hardExtra = if (hardMode && gameMode != Mode.DUEL) dp(22) * ROWS else 0
        val reservedH = dp(60) /*header*/ + dp(44) /*length row*/ + dp(8) /*spacer*/ + keyboardH +
            dp(12) * 2 /*root padding*/ + dp(30) /*message + slack*/ + hardExtra
        val byHeight = (dm.heightPixels - reservedH) / ROWS - dp(8)
        return byHeight.coerceIn(dp(30), dp(72))
    }

    /** Tile WIDTH (px) for [n] columns: as wide as fits the screen width, but never wider than
     *  the tile height (so tiles stay square-or-narrower). Longer words → narrower tiles, but
     *  the board keeps its full height. */
    private fun tileWidthFor(n: Int): Int {
        val dm = resources.displayMetrics
        // Width budget: root padding + leading spacer + trailing "?" + per-tile L/R margins.
        // Keep the horizontal margins small so long words don't leave the tiles too narrow.
        val reservedW = dp(12) * 2 + dp(20) * 2
        val byWidth = (dm.widthPixels - reservedW) / n - dp(4)
        return byWidth.coerceIn(dp(24), tileHeightPx())
    }

    /** (Re)builds the ROWS×[wordLen] grid, sizing tiles to fit the current word length. */
    private fun populateBoard() {
        boardContainer.removeAllViews()
        val h = tileHeightPx()
        val w = tileWidthFor(wordLen)
        val textPx = minOf(w, h).toFloat()
        val hints = arrayOfNulls<TextView>(ROWS)
        val counts = arrayOfNulls<TextView>(ROWS)
        val icons = arrayOfNulls<TextView>(ROWS)
        tiles = Array(ROWS) { r ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            // Leading marker balances the trailing "?" so the tiles stay centered; in duel mode it
            // shows who played the row (👤/🤖) instead of an empty spacer.
            val icon = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, (textPx * 0.45f).coerceAtMost(dp(24).toFloat()))
                layoutParams = LinearLayout.LayoutParams(dp(20), h)
                visibility = View.INVISIBLE
            }
            icons[r] = icon
            rowLayout.addView(icon)
            val rowTiles = Array(wordLen) { _ ->
                val tv = TextView(this).apply {
                    gravity = Gravity.CENTER
                    setTextColor(TEXT)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 0.5f)
                    background = tileDrawable(Color.TRANSPARENT, TILE_BORDER_EMPTY)
                    // Skipped by TalkBack until a submitted guess gives it a spoken label.
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    val lp = LinearLayout.LayoutParams(w, h)
                    lp.setMargins(dp(2), dp(4), dp(2), dp(4))
                    layoutParams = lp
                }
                rowLayout.addView(tv)
                tv
            }
            // Trailing "?" — shown only once the row holds a real (dictionary) word.
            val hint = TextView(this).apply {
                text = "?"
                contentDescription = "Look up this word"
                gravity = Gravity.CENTER
                setTextColor(HINT_COLOR)
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, (textPx * 0.45f).coerceAtMost(dp(24).toFloat()))
                layoutParams = LinearLayout.LayoutParams(dp(20), h)
                visibility = View.INVISIBLE
                setOnClickListener { lookUpRow(r) }
            }
            hints[r] = hint
            rowLayout.addView(hint)
            boardContainer.addView(rowLayout)
            // Hard-mode feedback line under the row (hidden until used).
            val count = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextColor(HINT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, 0, 0, dp(2))
                visibility = View.GONE
            }
            counts[r] = count
            boardContainer.addView(count)
            rowTiles
        }
        hintButtons = hints.requireNoNulls()
        countViews = counts.requireNoNulls()
        rowIcons = icons.requireNoNulls()
        tileState = Array(ROWS) { IntArray(wordLen) { -1 } }
    }

    private fun buildKeyboard(): View {
        keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        populateKeys()
        return keyboard
    }

    /** (Re)builds the keyboard for the current language, adding a row for [WordLists.Language.extra]. */
    private fun populateKeys() {
        keyboard.removeAllViews()
        keyButtons.clear()
        fun newRow() = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        KEY_ROWS.forEachIndexed { index, row ->
            val rowLayout = newRow()
            val lastRow = index == KEY_ROWS.size - 1
            // Bottom row is flanked by the action keys: DEL on the left (next to Z), ENTER right.
            if (lastRow) rowLayout.addView(actionKey("DEL") { onDelete() })
            for (ch in row) rowLayout.addView(letterKey(ch, weight = 1f))
            if (lastRow) rowLayout.addView(actionKey("ENTER") { onEnter() })
            keyboard.addView(rowLayout)
        }
        // Language-specific extra letters (e.g. German ß) as a centered row of fixed-width keys.
        val extra = currentLang.extra
        if (extra.isNotEmpty()) {
            val rowLayout = newRow()
            for (ch in extra) rowLayout.addView(letterKey(ch, weight = 0f))
            keyboard.addView(rowLayout)
        }
    }

    private fun letterKey(ch: Char, weight: Float): Button = Button(this).apply {
        text = ch.toString()
        isAllCaps = false // keep 'ß' as ß (allCaps would render it "SS")
        setTextColor(TEXT)
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (weight == 0f) 18f else 15f)
        background = keyDrawable(KEY_DEFAULT)
        stateListAnimator = null
        setPadding(0, 0, 0, 0)
        val lp = if (weight == 0f) {
            LinearLayout.LayoutParams(dp(64), keyHeightPx()) // fixed-width for the extra row
        } else {
            LinearLayout.LayoutParams(0, keyHeightPx(), weight)
        }
        lp.setMargins(dp(3), dp(4), dp(3), dp(4))
        layoutParams = lp
        setOnClickListener { onLetter(ch) }
        keyButtons[ch] = this
    }

    private fun actionKey(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        contentDescription = if (label == "DEL") "Delete letter" else "Submit guess"
        isAllCaps = true
        setTextColor(TEXT)
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        background = keyDrawable(KEY_DEFAULT)
        stateListAnimator = null
        setPadding(0, 0, 0, 0)
        val lp = LinearLayout.LayoutParams(0, keyHeightPx(), 1.5f)
        lp.setMargins(dp(3), dp(4), dp(3), dp(4))
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    // ---------------------------------------------------------------------------------
    // Game logic
    // ---------------------------------------------------------------------------------

    private fun startNewGame() {
        stopTimedDuel()   // any normal game leaves timed/duel
        gameMode = Mode.NORMAL
        duelOnline = false
        dailyMode = false // ...and leaves the daily
        if (answers.isEmpty()) return
        target = answers[Random.nextInt(answers.size)]
        targetTyped = WordLists.typeableForm(target, currentLang)
        currentRow = 0
        currentCol = 0
        gameOver = false
        clearBoard()
    }

    /** Stops any running timed clock and hides the mode status bar (shared teardown for the
     *  special modes). Safe to call even when no special mode is active. */
    private fun stopTimedDuel() {
        timedRunning = false
        stopOnlineDuel()
        if (::modeBar.isInitialized) {
            modeBar.removeCallbacks(timedTick)
            modeBar.visibility = View.GONE
        }
    }

    /** Cancels any in-flight online-duel poll loop and dismisses the "waiting" dialog. Leaves
     *  [duelOnline] alone — each entry point (startNewGame/startTimed/startDuel/beginOnlineDuel)
     *  sets that itself, so the flag survives this shared teardown. */
    private fun stopOnlineDuel() {
        duelPollGen++
        duelWaiting = false
        waitingDialog?.dismiss()
        waitingDialog = null
    }

    // ---------------------------------------------------------------------------------
    // Game-mode menu (the "New" button)
    // ---------------------------------------------------------------------------------

    /** One tappable row in an [showOptionList] dialog. The trailing glyph defaults to a
     *  navigational "›"; callers can override it (e.g. "↓" to signal a download) and tint it. */
    private data class OptionRow(
        val title: String,
        val subtitle: String? = null,
        val trailing: String = "›",
        val trailingColor: Int? = null,
        val action: () -> Unit,
    )

    /** A dark, tappable option list with a bold title, optional subtitle, a trailing glyph and thin
     *  dividers — used instead of a bare AlertDialog.setItems() so options read as tappable.
     *  An optional [caption] renders a small hint line under the title (e.g. a glyph legend). */
    private fun showOptionList(title: String, rows: List<OptionRow>, caption: String? = null) {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        caption?.let { cap ->
            list.addView(TextView(this).apply {
                text = cap
                setTextColor(HINT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(24), 0, dp(24), dp(8))
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(list) })
            .create()
        rows.forEachIndexed { i, row ->
            if (i > 0) list.addView(View(this).apply {
                setBackgroundColor(DIVIDER)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, Math.max(1, dp(1))).apply {
                    setMargins(dp(20), 0, dp(20), 0)
                }
            })
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), dp(14), dp(18), dp(14))
                isClickable = true
                isFocusable = true
                background = selectableItemBg()
                setOnClickListener { dialog.dismiss(); row.action() }
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            texts.addView(TextView(this).apply {
                text = row.title
                setTextColor(TEXT)
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            })
            row.subtitle?.let { sub ->
                texts.addView(TextView(this).apply {
                    text = sub
                    setTextColor(HINT_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, dp(2), 0, 0)
                })
            }
            rowView.addView(texts)
            rowView.addView(TextView(this).apply {
                text = row.trailing
                setTextColor(row.trailingColor ?: HINT_COLOR)
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            })
            list.addView(rowView)
        }
        dialog.show()
        styleAsBottomSheet(dialog)
    }

    /** Rounded-top surface behind every dialog so they all read as one bottom-sheet style. */
    private fun sheetBackground(): android.graphics.drawable.Drawable {
        val r = dp(20).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(SHEET_BG)
            // top-left, top-right rounded; bottom corners square (sheet sits on the screen edge)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }

    /** Anchors [dialog] to the bottom, full width, with a rounded-top sheet background and a
     *  slide-up animation — the single consistent modal style across the app. */
    private fun styleAsBottomSheet(dialog: android.app.Dialog) {
        val w = dialog.window ?: return
        w.setBackgroundDrawable(sheetBackground())
        w.setWindowAnimations(android.R.style.Animation_InputMethod) // built-in slide-from-bottom
        w.attributes = w.attributes.apply {
            width = MATCH_PARENT
            gravity = Gravity.BOTTOM
        }
    }

    /** Builds, shows and bottom-sheet-styles an [AlertDialog] in one step. Use instead of
     *  [AlertDialog.Builder.show] so message/content dialogs match the option-list sheets. */
    private fun AlertDialog.Builder.showSheet(): AlertDialog {
        val d = show()
        styleAsBottomSheet(d)
        return d
    }

    /** The framework's ripple/highlight background for a tappable list row. */
    private fun selectableItemBg(): android.graphics.drawable.Drawable? {
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val d = ta.getDrawable(0)
        ta.recycle()
        return d
    }

    private fun showNewGameMenu() {
        val rows = arrayListOf(
            OptionRow("Normal", "Guess the word in 6 tries") { startNewGame() },
            OptionRow("Timed", "5-minute sprint — solve as many as you can") { startTimed() },
            OptionRow("Duel vs computer", "Race the computer to the word") { startDuel() },
        )
        // Online duel only appears when a Firebase backend is configured (see [Duel.enabled]).
        if (Duel.enabled()) rows.add(
            OptionRow("Duel vs player", "Online turn-by-turn duel over a code") { showOnlineDuelMenu() }
        )
        showOptionList("New game", rows)
    }

    /** Updates the persistent status line for the active special mode. */
    private fun updateModeBar() {
        if (!::modeBar.isInitialized) return
        when (gameMode) {
            Mode.TIMED -> {
                val left = ((timedEndMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                modeBar.text = "⏱ ${fmtTime(left)}   ·   $timedScore solved"
                modeBar.setTextColor(if (left <= 30) DAILY_LOST else TEXT)
                modeBar.visibility = View.VISIBLE
            }
            Mode.DUEL -> {
                modeBar.text = when {
                    duelOnline && duelWaiting -> "🌐 Duel  ·  waiting for opponent  ($duelRoomCode)"
                    duelOnline -> if (duelPlayerTurn) "🌐 Duel  ·  your turn" else "🌐 Duel  ·  opponent…"
                    else -> if (duelPlayerTurn) "🤖 Duel  ·  your turn" else "🤖 Duel  ·  computer…"
                }
                modeBar.setTextColor(TEXT)
                modeBar.visibility = View.VISIBLE
            }
            Mode.NORMAL -> modeBar.visibility = View.GONE
        }
    }

    // ---------------------------------------------------------------------------------
    // Timed mode — 5-minute sprint, most words solved → weekly global leaderboard
    // ---------------------------------------------------------------------------------

    private fun startTimed() {
        if (answers.isEmpty()) { toast("Word list not loaded yet"); return }
        stopTimedDuel()
        gameMode = Mode.TIMED
        duelOnline = false
        dailyMode = false
        timedScore = 0
        timedEndMs = System.currentTimeMillis() + TIMED_DURATION_MS
        timedRunning = true
        nextTimedTarget()
        updateModeBar()
        modeBar.removeCallbacks(timedTick)
        modeBar.post(timedTick)
        toast("Go! Solve as many as you can")
    }

    /** Picks a fresh word and clears the board for the next timed round. */
    private fun nextTimedTarget() {
        target = answers[Random.nextInt(answers.size)]
        targetTyped = WordLists.typeableForm(target, currentLang)
        currentRow = 0
        currentCol = 0
        gameOver = false
        clearBoard()
    }

    /** Called when the current timed word is finished (solved or out of guesses): briefly shows
     *  the outcome, then moves on — unless the clock has run out. */
    private fun advanceTimed(solved: String?) {
        if (solved != null) { timedScore++; toast(winMessage(currentRow)) }
        else toast("Was $target")
        updateModeBar()
        modeBar.postDelayed({
            if (gameMode != Mode.TIMED || !timedRunning) return@postDelayed
            if (System.currentTimeMillis() >= timedEndMs) finishTimed()
            else { nextTimedTarget(); updateModeBar() }
        }, 800)
    }

    private fun finishTimed() {
        if (gameMode != Mode.TIMED) return
        timedRunning = false
        modeBar.removeCallbacks(timedTick)
        gameOver = true
        val score = timedScore
        AlertDialog.Builder(this)
            .setTitle("Time's up!")
            .setMessage("You solved $score ${if (score == 1) "word" else "words"} in 5 minutes.")
            .setPositiveButton("Leaderboard") { _, _ -> promptSubmitTimed(score) }
            .setNegativeButton("Done") { _, _ -> startNewGame() }
            .showSheet()
    }

    private fun promptSubmitTimed(score: Int) {
        if (score <= 0) { showTimedLeaderboard(null); return }
        val input = EditText(this).apply {
            setText(prefs().getString(PREF_NAME, ""))
            hint = "Name (optional)"
            setSingleLine()
            setTextColor(TEXT)
            setHintTextColor(HINT_COLOR)
        }
        AlertDialog.Builder(this)
            .setTitle("$score solved")
            .setMessage("Add your score to this week's leaderboard?")
            .setView(input)
            .setNegativeButton("Skip") { _, _ -> showTimedLeaderboard(null) }
            .setPositiveButton("Submit") { _, _ ->
                val name = Leaderboard.sanitizeName(input.text.toString())
                prefs().edit().putString(PREF_NAME, name).apply()
                val week = weekKey()
                showLoading(true)
                Thread {
                    try {
                        Leaderboard.submitTimed(name, score, week)
                        ifAlive { showLoading(false); showTimedLeaderboard(name) }
                    } catch (e: Exception) {
                        ifAlive { showLoading(false); toast("Submit failed: ${e.message}"); showTimedLeaderboard(null) }
                    }
                }.start()
            }
            .showSheet()
    }

    private fun showTimedLeaderboard(highlight: String?) {
        val week = weekKey()
        showLoading(true)
        Thread {
            try {
                val entries = Leaderboard.fetchTimed(week)
                ifAlive { showLoading(false); showTimedLeaderboardDialog(entries, highlight) }
            } catch (e: Exception) {
                ifAlive { showLoading(false); toast("Leaderboard unavailable: ${e.message}") }
            }
        }.start()
    }

    private fun showTimedLeaderboardDialog(entries: List<Leaderboard.Entry>, highlight: String?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        box.addView(TextView(this).apply {
            text = "This week · all languages — most words solved in a 5-minute sprint"
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(6))
        })
        if (entries.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "No scores yet this week. Be the first!"
                setTextColor(TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
        }
        entries.forEachIndexed { i, e ->
            val mine = highlight != null && e.name == highlight
            box.addView(TextView(this).apply {
                text = "${i + 1}.  ${e.name}   ·   ${e.guesses} words"
                setTextColor(if (mine) CORRECT else TEXT)
                setTypeface(if (mine) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(6), 0, 0)
            })
        }
        AlertDialog.Builder(this)
            .setTitle("Timed leaderboard")
            .setView(ScrollView(this).apply { addView(box) })
            .setNeutralButton("☕ Ko-fi") { _, _ -> openInBrowser(KOFI_URL) }
            .setPositiveButton("Close") { _, _ -> startNewGame() }
            .showSheet()
    }

    // ---------------------------------------------------------------------------------
    // Duel mode — coin toss, alternating turns on one shared board, first to solve wins
    // ---------------------------------------------------------------------------------

    private fun startDuel() {
        if (answers.isEmpty()) { toast("Word list not loaded yet"); return }
        stopTimedDuel()
        gameMode = Mode.DUEL
        duelOnline = false
        dailyMode = false
        target = answers[Random.nextInt(answers.size)]
        targetTyped = WordLists.typeableForm(target, currentLang)
        duelGuesses.clear()
        currentRow = 0
        currentCol = 0
        gameOver = false
        clearBoard()
        // Coin toss decides who guesses first.
        duelPlayerTurn = Random.nextBoolean()
        updateModeBar()
        if (duelPlayerTurn) toast("You start 🪙")
        else { toast("Computer starts 🪙"); npcTurn() }
    }

    /** The NPC's turn: off the UI thread, pick a valid word consistent with every clue on the
     *  shared board so far (random among them — smart but not perfect), then play it. */
    private fun npcTurn() {
        if (gameMode != Mode.DUEL || gameOver) return
        val row = currentRow
        val priors = ArrayList(duelGuesses)
        val pool = answersTyped.toList()
        val tgt = targetTyped
        Thread {
            val candidates = Wordle.consistentCandidates(pool, priors, tgt)
            val pick = when {
                candidates.isNotEmpty() -> candidates[Random.nextInt(candidates.size)]
                pool.isNotEmpty() -> pool[Random.nextInt(pool.size)]
                else -> tgt
            }
            ifAlive {
                if (gameMode != Mode.DUEL || gameOver || currentRow != row) return@ifAlive
                renderGuessRow(row, pick, forceColor = true)
                labelDuelRow(row, player = false)
                duelGuesses.add(pick)
                if (pick == tgt) { gameOver = true; finishDuel("Computer wins"); return@ifAlive }
                if (row == ROWS - 1) { gameOver = true; finishDuel("Draw — board full"); return@ifAlive }
                currentRow = row + 1
                currentCol = 0
                duelPlayerTurn = true
                updateModeBar()
            }
        }.start()
    }

    /** Marks board row [r] with who played it, using a leading icon in front of the row. */
    private fun labelDuelRow(r: Int, player: Boolean) {
        rowIcons[r].apply {
            text = when {
                player -> "👤"
                duelOnline -> "🧑"
                else -> "🤖"
            }
            contentDescription = when {
                player -> "you"
                duelOnline -> "opponent"
                else -> "computer"
            }
            visibility = View.VISIBLE
        }
    }

    private fun finishDuel(result: String) {
        AlertDialog.Builder(this)
            .setTitle(result)
            .setMessage("The word was $target.")
            .setPositiveButton("Rematch") { _, _ -> startDuel() }
            .setNegativeButton("Done") { _, _ -> startNewGame() }
            .showSheet()
    }

    // ---------------------------------------------------------------------------------
    // Online duel — same turn-by-turn game, the NPC replaced by another human over [Duel]
    // ---------------------------------------------------------------------------------

    private fun showOnlineDuelMenu() {
        if (answers.isEmpty()) { toast("Word list not loaded yet"); return }
        showOptionList("Online duel", listOf(
            OptionRow("Create game", "Get a code to share with a friend") { createOnlineDuel() },
            OptionRow("Join game", "Enter a friend's code") { promptJoinOnlineDuel() },
        ))
    }

    /** Host: pick a word, reserve a free code, create the room, then wait for a guest to join. */
    private fun createOnlineDuel() {
        if (answers.isEmpty()) { toast("Word list not loaded yet"); return }
        val tgtOrig = answers[Random.nextInt(answers.size)]
        val tgtTyped = WordLists.typeableForm(tgtOrig, currentLang)
        val starter = Random.nextInt(2)   // coin toss: 0 = host guesses first, 1 = guest
        val lang = currentLang.code
        val len = wordLen
        showLoading(true)
        Thread {
            try {
                // Retry a few codes so we never clobber someone else's live room.
                var code = ""
                for (t in 1..5) {
                    val c = Duel.newCode()
                    if (Duel.fetchRoom(c) == null) { code = c; break }
                }
                if (code.isEmpty()) throw IOException("Couldn't reserve a code, try again")
                Duel.createRoom(code, lang, len, tgtTyped, tgtOrig, starter)
                ifAlive {
                    showLoading(false)
                    beginOnlineDuel(code, host = true, starter = starter, round = 0,
                        tgtOrig = tgtOrig, tgtTyped = tgtTyped, waiting = true)
                    showWaitingDialog(code)
                    pollForJoin()
                }
            } catch (e: Exception) {
                ifAlive { showLoading(false); toast("Couldn't create game: ${e.message}") }
            }
        }.start()
    }

    private fun promptJoinOnlineDuel() {
        val input = EditText(this).apply {
            hint = "Code"
            setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(Duel.CODE_LEN),
                android.text.InputFilter.AllCaps())
            setTextColor(TEXT)
            setHintTextColor(HINT_COLOR)
        }
        AlertDialog.Builder(this)
            .setTitle("Join duel")
            .setMessage("Enter the ${Duel.CODE_LEN}-character code your opponent shared.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Join") { _, _ -> joinOnlineDuel(input.text.toString()) }
            .showSheet()
    }

    /** Guest: look the room up, make sure its language/length is available (download if needed),
     *  mark it started, switch to it and begin. */
    private fun joinOnlineDuel(rawCode: String) {
        val code = rawCode.uppercase().filter { it.isLetterOrDigit() }.take(Duel.CODE_LEN)
        if (code.length < Duel.CODE_LEN) { toast("Enter the ${Duel.CODE_LEN}-character code"); return }
        showLoading(true)
        Thread {
            try {
                val room = Duel.fetchRoom(code)
                when {
                    room == null -> ifAlive { showLoading(false); toast("Game not found") }
                    room.status != "waiting" -> ifAlive { showLoading(false); toast("Game already started") }
                    else -> {
                        val words = loadOrFetchWords(room.lang)   // may download; throws on failure
                        Duel.joinRoom(code)
                        ifAlive {
                            showLoading(false)
                            applyRoomLanguage(room, words)
                            beginOnlineDuel(code, host = false, starter = room.starter, round = room.round,
                                tgtOrig = room.targetOrig, tgtTyped = room.target, waiting = false)
                            toast(if (duelPlayerTurn) "Joined — your turn 🪙" else "Joined — opponent starts 🪙")
                            if (!duelPlayerTurn) pollForMove()
                        }
                    }
                }
            } catch (e: Exception) {
                ifAlive { showLoading(false); toast("Couldn't join: ${e.message}") }
            }
        }.start()
    }

    /** Shared local setup for both host and guest, for a fresh game or a rematch. */
    private fun beginOnlineDuel(
        code: String, host: Boolean, starter: Int, round: Int,
        tgtOrig: String, tgtTyped: String, waiting: Boolean,
    ) {
        stopTimedDuel()   // bumps duelPollGen, clears any prior duel/timed state
        gameMode = Mode.DUEL
        dailyMode = false
        duelOnline = true
        duelIsHost = host
        duelMyIndex = if (host) 0 else 1
        duelRoomCode = code
        duelStarter = starter
        duelRound = round
        duelWaiting = waiting
        target = tgtOrig
        targetTyped = tgtTyped
        duelGuesses.clear()
        currentRow = 0
        currentCol = 0
        gameOver = false
        clearBoard()
        duelPlayerTurn = (starter == duelMyIndex)
        updateModeBar()
    }

    /** Host poll: wait until the guest flips the room to "playing". */
    private fun pollForJoin() {
        val gen = duelPollGen
        val code = duelRoomCode
        Thread {
            try {
                while (gen == duelPollGen) {
                    val room = Duel.fetchRoom(code)
                    if (room == null) { ifAlive { if (gen == duelPollGen) onOpponentGone() }; return@Thread }
                    if (room.status == "playing") {
                        ifAlive { if (gen == duelPollGen) onOpponentJoined() }
                        return@Thread
                    }
                    Thread.sleep(1500)
                }
            } catch (e: Exception) {
                ifAlive { if (gen == duelPollGen) toast("Connection lost") }
            }
        }.start()
    }

    private fun onOpponentJoined() {
        duelWaiting = false
        waitingDialog?.dismiss(); waitingDialog = null
        toast("Opponent joined!")
        updateModeBar()
        if (!duelPlayerTurn) pollForMove()   // opponent starts
    }

    /** Poll for the opponent's next move (index == our current row). */
    private fun pollForMove() {
        if (gameOver) return
        val gen = duelPollGen
        val code = duelRoomCode
        val expected = currentRow
        Thread {
            try {
                while (gen == duelPollGen) {
                    val room = Duel.fetchRoom(code)
                    if (room == null) { ifAlive { if (gen == duelPollGen) onOpponentGone() }; return@Thread }
                    if (room.moves.size > expected) {
                        val mv = room.moves[expected]
                        ifAlive { if (gen == duelPollGen) applyOpponentMove(mv) }
                        return@Thread
                    }
                    Thread.sleep(1500)
                }
            } catch (e: Exception) {
                ifAlive { if (gen == duelPollGen) toast("Connection lost") }
            }
        }.start()
    }

    private fun applyOpponentMove(mv: Duel.Move) {
        if (gameOver || gameMode != Mode.DUEL || !duelOnline) return
        val row = currentRow
        renderGuessRow(row, mv.guess, forceColor = true)
        labelDuelRow(row, player = false)
        duelGuesses.add(mv.guess)
        when {
            mv.guess == targetTyped -> { gameOver = true; finishOnlineDuel(OnlineResult.LOSE) }
            row == ROWS - 1 -> { gameOver = true; finishOnlineDuel(OnlineResult.DRAW) }
            else -> {
                currentRow = row + 1
                currentCol = 0
                duelPlayerTurn = true
                updateModeBar()
            }
        }
    }

    /** The local player's own submitted guess: push it, then either finish or hand the turn over. */
    private fun handleMyOnlineMove(guess: String, solved: Boolean, lastRow: Boolean) {
        val row = currentRow
        val code = duelRoomCode
        val idx = duelMyIndex
        val ended = solved || lastRow
        val winner = if (solved) idx else -1
        Thread {
            try {
                Duel.pushMove(code, row, idx, guess)
                if (ended) Duel.finish(code, winner)
            } catch (e: Exception) {
                ifAlive { toast("Couldn't send move: ${e.message}") }
            }
        }.start()
        when {
            solved -> { gameOver = true; finishOnlineDuel(OnlineResult.WIN) }
            lastRow -> { gameOver = true; finishOnlineDuel(OnlineResult.DRAW) }
            else -> {
                currentRow++
                currentCol = 0
                duelPlayerTurn = false
                updateModeBar()
                pollForMove()
            }
        }
    }

    private enum class OnlineResult { WIN, LOSE, DRAW }

    private fun finishOnlineDuel(result: OnlineResult) {
        stopOnlineDuel()   // stops polling; leaves duelOnline true so labels/reveal stay correct
        // The room is kept (not deleted) so either side can offer a rematch on the same code; it's
        // torn down only when a player actually leaves the finished game (see [leaveOnlineDuel]).
        val title = when (result) {
            OnlineResult.WIN -> "You win! 🎉"
            OnlineResult.LOSE -> "Opponent wins"
            OnlineResult.DRAW -> "Draw — board full"
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("The word was $target.")
            .setPositiveButton("Rematch") { _, _ -> rematchOnlineDuel() }
            .setNegativeButton("Done") { _, _ -> leaveOnlineDuel(); startNewGame() }
            .showSheet()
    }

    /** Rematch on the same room code. The host picks a fresh word, swaps the coin toss and bumps
     *  the round, then waits for the guest; the guest waits for the host to set the new round up. */
    private fun rematchOnlineDuel() {
        val code = duelRoomCode
        if (code.isEmpty()) { showOnlineDuelMenu(); return }
        if (duelIsHost) {
            if (answers.isEmpty()) { toast("Word list not loaded yet"); return }
            val tgtOrig = answers[Random.nextInt(answers.size)]
            val tgtTyped = WordLists.typeableForm(tgtOrig, currentLang)
            val newStarter = 1 - duelStarter        // swap who goes first, for fairness
            val newRound = duelRound + 1
            val lang = currentLang.code
            val len = wordLen
            showLoading(true)
            Thread {
                try {
                    // A full PUT resets the room (clears the previous round's moves) and re-opens it.
                    Duel.createRoom(code, lang, len, tgtTyped, tgtOrig, newStarter, newRound)
                    ifAlive {
                        showLoading(false)
                        beginOnlineDuel(code, host = true, starter = newStarter, round = newRound,
                            tgtOrig = tgtOrig, tgtTyped = tgtTyped, waiting = true)
                        showWaitingDialog(code, rematch = true)
                        pollForJoin()
                    }
                } catch (e: Exception) {
                    ifAlive { showLoading(false); toast("Couldn't start rematch: ${e.message}") }
                }
            }.start()
        } else {
            // Guest: wait for the host to open the next round, then auto-join it.
            duelWaiting = true
            showRematchWaitDialog()
            pollForRematch()
        }
    }

    /** Guest poll: wait until the host opens the next round (a higher `round`, back to "waiting"). */
    private fun pollForRematch() {
        val gen = duelPollGen
        val code = duelRoomCode
        val fromRound = duelRound
        Thread {
            try {
                while (gen == duelPollGen) {
                    val room = Duel.fetchRoom(code)
                    if (room == null) { ifAlive { if (gen == duelPollGen) onOpponentGone() }; return@Thread }
                    if (room.round > fromRound && room.status == "waiting") {
                        ifAlive { if (gen == duelPollGen) onRematchOffered(room) }
                        return@Thread
                    }
                    Thread.sleep(1500)
                }
            } catch (e: Exception) {
                ifAlive { if (gen == duelPollGen) toast("Connection lost") }
            }
        }.start()
    }

    /** Guest: the host opened a new round — adopt its word/starter and join it. */
    private fun onRematchOffered(room: Duel.Room) {
        waitingDialog?.dismiss(); waitingDialog = null
        showLoading(true)
        val code = duelRoomCode
        Thread {
            try {
                Duel.joinRoom(code)
                ifAlive {
                    showLoading(false)
                    beginOnlineDuel(code, host = false, starter = room.starter, round = room.round,
                        tgtOrig = room.targetOrig, tgtTyped = room.target, waiting = false)
                    toast(if (duelPlayerTurn) "Rematch — your turn 🪙" else "Rematch — opponent starts 🪙")
                    if (!duelPlayerTurn) pollForMove()
                }
            } catch (e: Exception) {
                ifAlive { showLoading(false); toast("Couldn't start rematch: ${e.message}") }
            }
        }.start()
    }

    /** Leaves a finished/idle online duel and tears the room down (host only, after a short grace
     *  so a still-polling opponent can read the final move first). */
    private fun leaveOnlineDuel() {
        val code = duelRoomCode
        val host = duelIsHost
        stopOnlineDuel()
        duelOnline = false
        if (host && code.isNotEmpty()) Thread {
            try { Thread.sleep(3000); Duel.deleteRoom(code) } catch (_: Exception) {}
        }.start()
    }

    private fun showRematchWaitDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(20), dp(24), dp(8))
        }
        box.addView(TextView(this).apply {
            text = "Waiting for your opponent to start a rematch…"
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
        })
        waitingDialog = AlertDialog.Builder(this)
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> cancelOnlineDuel() }
            .create()
        waitingDialog?.show()
        waitingDialog?.let { styleAsBottomSheet(it) }
    }

    /** Opponent's room vanished mid-game (they quit, or a stale room was cleaned up). */
    private fun onOpponentGone() {
        if (gameOver) return
        gameOver = true
        stopOnlineDuel()
        AlertDialog.Builder(this)
            .setTitle("Opponent left")
            .setMessage("The game ended. The word was $target.")
            .setPositiveButton("New online duel") { _, _ -> showOnlineDuelMenu() }
            .setNegativeButton("Done") { _, _ -> startNewGame() }
            .showSheet()
    }

    private fun showWaitingDialog(code: String, rematch: Boolean = false) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        box.addView(TextView(this).apply {
            text = if (rematch) "Rematch! Same code — waiting for your opponent:"
                else "Share this code with your opponent:"
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
        box.addView(TextView(this).apply {
            text = code
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            letterSpacing = 0.25f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
            isClickable = true
            contentDescription = "Duel code $code, tap to share"
            setOnClickListener { shareDuelCode(code) }
        })
        box.addView(TextView(this).apply {
            text = "Tap the code to share  ·  waiting for them to join…"
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
        })
        waitingDialog = AlertDialog.Builder(this)
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> cancelOnlineDuel() }
            .create()
        waitingDialog?.show()
        waitingDialog?.let { styleAsBottomSheet(it) }
    }

    private fun shareDuelCode(code: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Let's duel in Word Guesser! Join my game with code: $code")
        }
        try { startActivity(Intent.createChooser(send, "Share code")) }
        catch (e: Exception) { toast("No app to share with") }
    }

    private fun cancelOnlineDuel() {
        val code = duelRoomCode
        val host = duelIsHost
        stopOnlineDuel()
        duelOnline = false
        if (host && code.isNotEmpty()) Thread { Duel.deleteRoom(code) }.start()
        startNewGame()
    }

    /** Returns the word list for [langCode] from cache/built-in, downloading it if needed. Runs on
     *  a background thread (callers are already off the UI thread); throws on failure. */
    @Throws(IOException::class)
    private fun loadOrFetchWords(langCode: String): List<String> {
        val lang = WordLists.LANGUAGES.firstOrNull { it.code == langCode }
            ?: throw IOException("Unknown language '$langCode'")
        if (lang.url == null) return WordBank.ANSWERS.map { it.uppercase() }
        val cache = cacheFile(lang)
        if (cache.exists()) {
            val cached = cache.readLines().filter { it.isNotBlank() }
            if (cached.isNotEmpty()) return cached
        }
        val words = WordLists.fetchAndFilter(lang)
        if (words.size < 20) throw IOException("only ${words.size} words found")
        cache.writeText(words.joinToString("\n"))
        return words
    }

    /** Switches the UI to the room's language + length without starting a normal game (that's
     *  [beginOnlineDuel]'s job). Mirrors [applyLanguage] but skips the fresh-game side effect. */
    private fun applyRoomLanguage(room: Duel.Room, words: List<String>) {
        val lang = WordLists.LANGUAGES.firstOrNull { it.code == room.lang } ?: currentLang
        currentLang = lang
        langButton.text = lang.badge
        prefs().edit().putString(PREF_LANG, lang.code).apply()
        pushRecentLang(lang.code)
        populateKeys()
        populateLengthRow()
        answersAll = words
        prefs().edit().putInt(PREF_LEN, room.len).apply()
        rebuildForLength(room.len)   // sets wordLen + answer/accept indexes + rebuilds the board
        updateLengthButtons()
    }

    private fun weekKey(): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"), Locale.US).apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            minimalDaysInFirstWeek = 4 // ISO-8601 week numbering
        }
        val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
        val year = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cal.weekYear
            else cal.get(java.util.Calendar.YEAR)
        return "%04dW%02d".format(year, week)
    }

    /** Resets tiles, hint/count lines and key colours (without picking a new target). */
    private fun clearBoard() {
        revealRow.visibility = View.GONE
        for (r in 0 until ROWS) {
            hintButtons[r].visibility = View.INVISIBLE
            countViews[r].visibility = View.GONE
            countViews[r].text = ""
            rowIcons[r].visibility = View.INVISIBLE
            rowIcons[r].text = ""
            for (c in 0 until wordLen) {
                tiles[r][c].text = ""
                tiles[r][c].contentDescription = null
                tiles[r][c].importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                tiles[r][c].background = tileDrawable(Color.TRANSPARENT, TILE_BORDER_EMPTY)
                tileState[r][c] = -1
            }
        }
        keyState.clear()
        for ((_, btn) in keyButtons) btn.background = keyDrawable(KEY_DEFAULT)
    }

    /** In duel mode the human may only type on their own turn (and, online, once the opponent
     *  has actually joined). */
    private fun inputBlocked(): Boolean = gameMode == Mode.DUEL && (duelWaiting || !duelPlayerTurn)

    private fun onLetter(ch: Char) {
        if (gameOver || inputBlocked() || currentCol >= wordLen) return
        val tile = tiles[currentRow][currentCol]
        tile.text = ch.toString()
        tile.background = tileDrawable(Color.TRANSPARENT, TILE_BORDER_FILLED)
        currentCol++
    }

    private fun onDelete() {
        if (gameOver || inputBlocked() || currentCol == 0) return
        currentCol--
        val tile = tiles[currentRow][currentCol]
        tile.text = ""
        tile.background = tileDrawable(Color.TRANSPARENT, TILE_BORDER_EMPTY)
    }

    private fun onEnter() {
        if (gameOver) return
        if (currentCol < wordLen) {
            toast("Not enough letters")
            return
        }
        val guess = (0 until wordLen).joinToString("") { tiles[currentRow][it].text.toString() }

        if (!dailyMode && strictMode && currentLang.url != null && !isKnownWord(guess)) {
            toast("Not in word list")
            return
        }

        val states = evaluate(guess, targetTyped)
        val inPlace = states.count { it == ST_CORRECT }
        val inWord = states.count { it == ST_CORRECT || it == ST_PRESENT }
        // Duel always uses colours (both players need the feedback, and the count line is reused
        // to label whose row it is), so hard mode is ignored there.
        val useHard = hardMode && gameMode != Mode.DUEL
        if (useHard) {
            // No colours. Show only aggregate feedback under the row.
            countViews[currentRow].text = "$inWord in word · $inPlace in right place"
            countViews[currentRow].visibility = View.VISIBLE
            for (c in 0 until wordLen) describeTile(currentRow, c, guess[c], null)
        } else {
            for (c in 0 until wordLen) {
                val color = stateColor(states[c])
                tileState[currentRow][c] = states[c]
                tiles[currentRow][c].background = tileDrawable(color, color)
                updateKeyColor(guess[c], states[c])
                describeTile(currentRow, c, guess[c], states[c])
            }
        }
        // Spoken summary for TalkBack (colours/tiles aren't otherwise announced).
        announce("Guess ${currentRow + 1}: $inPlace in the right place, $inWord in the word")

        // If the guess is a real word, show its original spelling (with umlauts) and a "?".
        // Works in daily mode too — the daily uses the current language's own words, so its
        // guesses can be looked up just like a normal game's.
        if (isKnownWord(guess)) {
            val display = if (guess == targetTyped) target else (typedToOriginal[guess] ?: guess)
            for (c in 0 until wordLen) tiles[currentRow][c].text = display[c].toString()
            hintButtons[currentRow].contentDescription = "Look up $display"
            hintButtons[currentRow].visibility = View.VISIBLE
        }
        if (dailyMode) dailyGuesses.add(guess)

        val solved = guess == targetTyped
        val lastRow = currentRow == ROWS - 1
        when (gameMode) {
            Mode.DUEL -> {
                labelDuelRow(currentRow, player = true)
                duelGuesses.add(guess)
                if (duelOnline) handleMyOnlineMove(guess, solved, lastRow)
                else when {
                    solved -> { gameOver = true; finishDuel("You win! 🎉") }
                    lastRow -> { gameOver = true; finishDuel("Draw — board full") }
                    else -> {
                        currentRow++
                        currentCol = 0
                        duelPlayerTurn = false
                        updateModeBar()
                        npcTurn()
                    }
                }
            }
            Mode.TIMED -> when {
                solved -> { gameOver = true; advanceTimed(solved = guess) }
                lastRow -> { gameOver = true; advanceTimed(solved = null) }
                else -> { currentRow++; currentCol = 0 }
            }
            Mode.NORMAL -> when {
                solved -> {
                    gameOver = true
                    if (dailyMode) finishDaily(won = true)
                    else {
                        recordResult(won = true, guessRow = currentRow)
                        maybeShowWinSupport(winMessage(currentRow), target)
                    }
                }
                lastRow -> {
                    gameOver = true
                    if (dailyMode) finishDaily(won = false)
                    else { recordResult(won = false, guessRow = currentRow); showReveal(target) }
                }
                else -> {
                    currentRow++
                    currentCol = 0
                    if (dailyMode) saveDailyProgress()
                }
            }
        }
    }

    /** Per-position state (delegates to the pure, unit-tested [Wordle.evaluate]). */
    private fun evaluate(guess: String, target: String): IntArray = Wordle.evaluate(guess, target)

    /** Active tile/key fill for each state — swaps to the high-contrast (colour-blind) palette
     *  when that setting is on. */
    private fun correctColor() = if (highContrast) CORRECT_HC else CORRECT
    private fun presentColor() = if (highContrast) PRESENT_HC else PRESENT
    private fun stateColor(state: Int) = when (state) {
        ST_CORRECT -> correctColor()
        ST_PRESENT -> presentColor()
        else -> ABSENT
    }

    /** Repaints every already-revealed tile and key from its stored state — used when the
     *  high-contrast setting is toggled mid-game so the change is visible immediately. */
    private fun recolorBoard() {
        if (!::tiles.isInitialized) return
        for (r in 0 until ROWS) for (c in 0 until wordLen) {
            val s = tileState[r][c]
            if (s >= 0) { val col = stateColor(s); tiles[r][c].background = tileDrawable(col, col) }
        }
        for ((ch, st) in keyState) keyButtons[ch]?.background = keyDrawable(stateColor(st))
    }

    private fun updateKeyColor(ch: Char, state: Int) {
        val prev = keyState[ch] ?: -1
        if (state <= prev) return // never downgrade a key's color
        keyState[ch] = state
        keyButtons[ch]?.background = keyDrawable(stateColor(state))
    }

    private fun winMessage(row: Int): String = when (row) {
        0 -> "Genius!"
        1 -> "Magnificent!"
        2 -> "Impressive!"
        3 -> "Splendid!"
        4 -> "Great!"
        else -> "Phew!"
    }

    /** Sets a tile's spoken label (letter + colour state) for TalkBack. state == null in hard mode. */
    private fun describeTile(row: Int, col: Int, ch: Char, state: Int?) {
        val tile = tiles[row][col]
        tile.contentDescription = when (state) {
            ST_CORRECT -> "$ch, right place"
            ST_PRESENT -> "$ch, in word, wrong place"
            ST_ABSENT -> "$ch, not in word"
            else -> ch.toString()
        }
        tile.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun announce(message: String) {
        boardContainer.announceForAccessibility(message)
    }

    private fun lookUpRow(row: Int) {
        val word = (0 until wordLen).joinToString("") { tiles[row][it].text.toString() }
        if (word.length == wordLen) lookUpWord(word)
    }

    /**
     * "?" entry point. If the current language ships a self-hosted definition list (same
     * language), show the definition directly — offline after the first fetch, no Gemini.
     * Otherwise fall back to the options menu. Works in daily mode too, since the daily now
     * uses the current language's own words.
     */
    private fun lookUpWord(word: String) {
        if (currentLang.defsUrl.isNotEmpty()) {
            showDefinition(word, currentLang)
        } else {
            lookUpMenu(word)
        }
    }

    // Parsed definition map for the currently loaded language (lazy; kept in memory).
    private var defsMap: Map<String, String>? = null
    private var defsMapLang: String? = null

    /** Shows only the definition of [word] from [lang]'s def list, loading it if needed. */
    private fun showDefinition(word: String, lang: WordLists.Language) {
        val cached = defsMap
        if (cached != null && defsMapLang == lang.code) {
            presentDefinition(word, lang, cached)
            return
        }
        showLoading(true)
        Thread {
            try {
                val map = parseDefs(readDefsJson(lang))
                ifAlive {
                    defsMap = map; defsMapLang = lang.code
                    showLoading(false)
                    presentDefinition(word, lang, map)
                }
            } catch (e: Exception) {
                // Network/parse trouble — don't dead-end; offer the usual options instead.
                ifAlive { showLoading(false); lookUpMenu(word) }
            }
        }.start()
    }

    /** Reads the def JSON from the on-device cache, downloading (and caching) it once. */
    private fun readDefsJson(lang: WordLists.Language): String {
        val cache = File(filesDir, "defs_${lang.code}_v4.json")
        if (cache.exists()) return cache.readText(Charsets.UTF_8)
        val text = downloadText(lang.defsUrl)
        cache.writeText(text, Charsets.UTF_8)
        return text
    }

    private fun parseDefs(json: String): Map<String, String> {
        val obj = JSONObject(json)
        val map = HashMap<String, String>(obj.length() * 2)
        val keys = obj.keys()
        while (keys.hasNext()) { val k = keys.next(); map[k] = obj.getString(k) }
        return map
    }

    /** Minimal robust text GET (a few retries with backoff). */
    private fun downloadText(urlStr: String): String {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000; readTimeout = 20000; requestMethod = "GET"
                }
                try {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { return it.readText() }
                } finally { conn.disconnect() }
            } catch (e: Exception) {
                last = e
                try { Thread.sleep(300L * (attempt + 1)) } catch (_: InterruptedException) {}
            }
        }
        throw last ?: IOException("download failed")
    }

    private fun presentDefinition(word: String, lang: WordLists.Language, map: Map<String, String>) {
        val raw = map[word.lowercase()]
        if (raw == null) { lookUpMenu(word); return } // not in the list — offer other options
        val def = resolveFormOf(raw, map)
        val tv = TextView(this).apply {
            text = def + "\n\n— " + lang.defsCredit
            setTextColor(TEXT)
            setTextIsSelectable(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(22), dp(14), dp(22), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(word)
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .setNeutralButton("More…") { _, _ -> lookUpMenu(word) }
            .showSheet()
    }

    /**
     * Offers ways to define/translate [word]: Gemini (in-app AI answer), Google Translate
     * (web, source language pre-set so it isn't mis-detected), Wiktionary, or the system
     * text-processing chooser.
     */
    private fun lookUpMenu(word: String) {
        val w = word.lowercase()
        val src = if (currentLang.code == "en_builtin") "en" else currentLang.code
        val dst = Locale.getDefault().language.ifBlank { "en" }
        val translateUrl =
            "https://translate.google.com/?sl=$src&tl=$dst&text=${Uri.encode(w)}&op=translate"
        val wiktionaryUrl = "https://en.wiktionary.org/wiki/${Uri.encode(w)}"

        val own = isOwnLanguage() // device language == word language → definition only
        val rows = ArrayList<OptionRow>()
        rows.add(OptionRow(if (own) "Define (Gemini)" else "Define & translate (Gemini)") { geminiLookup(word) })
        if (!own) rows.add(OptionRow("Translate (web)") { openInBrowser(translateUrl) })
        rows.add(OptionRow("Define (Wiktionary)") { openInBrowser(wiktionaryUrl) })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            rows.add(OptionRow("Other apps…") { processTextChooser(word) })
        }
        showOptionList(word, rows)
    }

    private fun geminiLookup(word: String) {
        val srcName = langName(currentLang.code)
        val dstName = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
        val prompt = if (isOwnLanguage()) {
            "Give a concise definition of the $srcName word \"$word\". Reply in $dstName."
        } else {
            "Give a concise definition and the $dstName translation of the $srcName word " +
                "\"$word\". Reply in $dstName."
        }

        // Preferred: hand the prompt to the on-device Gemini app — no API key, uses its login.
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, prompt)
            setPackage(GEMINI_APP)
        }
        if (send.resolveActivity(packageManager) != null) {
            try {
                startActivity(send)
                return
            } catch (e: Exception) {
                // fall through to the API / message
            }
        }
        // Fallback: the Gemini API, if a key was configured in local.properties.
        if (Gemini.hasKey()) {
            showLoading(true)
            Thread {
                try {
                    val result = Gemini.defineAndTranslate(word, srcName, dstName)
                    ifAlive { showLoading(false); showTextDialog(word, result) }
                } catch (e: Exception) {
                    ifAlive { showLoading(false); toast("Gemini: ${e.message}") }
                }
            }.start()
            return
        }
        toast("Install the Gemini app or add an API key")
    }

    private fun sourceLangCode(): String =
        if (currentLang.code == "en_builtin") "en" else currentLang.code

    /** True when the word's language is the same as the device language (translation is pointless). */
    private fun isOwnLanguage(): Boolean =
        sourceLangCode().equals(Locale.getDefault().language, ignoreCase = true)

    private fun langName(code: String): String = when (code) {
        "nl" -> "Dutch"; "fr" -> "French"; "de" -> "German"; "es" -> "Spanish"
        "it" -> "Italian"; "pt" -> "Portuguese"; "ca" -> "Catalan"; "ro" -> "Romanian"
        "sv" -> "Swedish"; "cs" -> "Czech"; "sk" -> "Slovak"; "da" -> "Danish"
        "nb" -> "Norwegian"; "pl" -> "Polish"; "hr" -> "Croatian"
        else -> "English"
    }

    private fun showTextDialog(title: String, text: String) {
        val tv = TextView(this).apply {
            setText(text)
            setTextColor(TEXT)
            setTextIsSelectable(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(22), dp(12), dp(22), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .showSheet()
    }

    /**
     * Opens [url] in an actual browser. We force a browser package because the Google
     * Translate app claims translate.google.com links but then ignores the sl/tl/text
     * query params (it just opens its home screen); the web page honours them.
     */
    private fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        resolveBrowserPackage()?.let { intent.setPackage(it) }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // No such browser package after all — let the system pick.
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun resolveBrowserPackage(): String? {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        val pkg = packageManager.resolveActivity(probe, 0)?.activityInfo?.packageName
        if (pkg != null && pkg != "android" && !pkg.contains("resolver")) return pkg
        return packageManager.queryIntentActivities(probe, 0)
            .firstOrNull()?.activityInfo?.packageName
    }

    private fun processTextChooser(word: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return // ACTION_PROCESS_TEXT is API 23+
        val process = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, word)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }
        if (process.resolveActivity(packageManager) != null) {
            startActivity(Intent.createChooser(process, "Look up: $word"))
        } else {
            toast("No text apps available")
        }
    }

    // ---------------------------------------------------------------------------------
    // Language switching + word-list download/cache
    // ---------------------------------------------------------------------------------

    /** Restores the last-used language on launch (from cache/built-in), then starts a game. */
    private fun initLanguage() {
        val code = prefs().getString(PREF_LANG, WordLists.LANGUAGES[0].code)
        val lang = WordLists.LANGUAGES.firstOrNull { it.code == code } ?: WordLists.LANGUAGES[0]
        val loaded = loadCachedOrBuiltin(lang)
        val words = if (loaded != null) {
            currentLang = lang
            loaded
        } else {
            // Chosen language isn't cached yet and needs a download — fall back to built-in
            // so the app is always playable offline on launch.
            currentLang = WordLists.LANGUAGES[0]
            WordBank.ANSWERS.map { it.uppercase() }
        }
        langButton.text = currentLang.badge
        populateKeys()
        populateLengthRow() // the selectable range can differ per language
        setWordSource(words) // → applyLength() rebuilds the board and starts the game
    }

    private fun showSettings() {
        val min = currentLang.minLen
        val max = currentLang.maxLen
        var chosen = wordLen.coerceIn(min, max)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        // --- Word length stepper ---
        val lenRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        lenRow.addView(TextView(this).apply {
            text = "Word length"
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        val minus = stepButton("−")
        val valueTv = TextView(this).apply {
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            minWidth = dp(40)
        }
        val plus = stepButton("+")
        fun refresh() {
            valueTv.text = chosen.toString()
            minus.isEnabled = chosen > min
            plus.isEnabled = chosen < max
            minus.alpha = if (minus.isEnabled) 1f else 0.4f
            plus.alpha = if (plus.isEnabled) 1f else 0.4f
        }
        minus.setOnClickListener { if (chosen > min) { chosen--; refresh() } }
        plus.setOnClickListener { if (chosen < max) { chosen++; refresh() } }
        refresh()
        lenRow.addView(minus)
        lenRow.addView(valueTv)
        lenRow.addView(plus)
        box.addView(lenRow)
        box.addView(TextView(this).apply {
            text = if (min == max) "Fixed at $min for ${currentLang.label}"
            else "Range for ${currentLang.label}: $min–$max"
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, dp(14))
        })

        // --- Strict mode (only for downloaded languages) ---
        val strictAvailable = currentLang.url != null
        val strictCb = android.widget.CheckBox(this).apply {
            text = "Strict mode — only real words"
            setTextColor(TEXT)
            isChecked = strictMode
        }
        if (strictAvailable) {
            box.addView(strictCb)
        } else {
            box.addView(TextView(this).apply {
                text = "Strict mode: download a language first"
                setTextColor(HINT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        }

        // --- Hard mode ---
        val hardCb = android.widget.CheckBox(this).apply {
            text = "Hard mode — no colours, only counts"
            setTextColor(TEXT)
            isChecked = hardMode
        }
        box.addView(hardCb)
        box.addView(TextView(this).apply {
            text = "Shows how many letters are in the word and in the right place, but " +
                "doesn't colour the tiles."
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(4), 0, 0, 0)
        })

        // --- High-contrast (colour-blind) palette ---
        val contrastCb = android.widget.CheckBox(this).apply {
            text = "High-contrast colours"
            setTextColor(TEXT)
            isChecked = highContrast
        }
        box.addView(contrastCb)
        box.addView(TextView(this).apply {
            text = "Uses orange and blue instead of green and yellow, easier to tell apart."
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(4), 0, 0, 0)
        })

        box.addView(TextView(this).apply {
            text = "How to play ›"
            setTextColor(0xFF8AB4F8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(18), 0, 0)
            setOnClickListener { showHowToPlay() }
        })
        box.addView(TextView(this).apply {
            text = "Statistics ›"
            setTextColor(0xFF8AB4F8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { showStats() }
        })
        box.addView(TextView(this).apply {
            text = "Sources & licenses ›"
            setTextColor(0xFF8AB4F8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { showSources() }
        })
        box.addView(TextView(this).apply {
            text = "☕  Support the developer (Ko-fi) ›"
            setTextColor(0xFFFF5E5B.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { openInBrowser("https://ko-fi.com/hrbons") }
        })

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(box)
            .setPositiveButton("Done") { _, _ ->
                if (strictAvailable) {
                    strictMode = strictCb.isChecked
                    prefs().edit().putBoolean(PREF_STRICT, strictMode).apply()
                }
                val hardChanged = hardCb.isChecked != hardMode
                if (hardChanged) {
                    hardMode = hardCb.isChecked
                    prefs().edit().putBoolean(PREF_HARD, hardMode).apply()
                }
                if (contrastCb.isChecked != highContrast) {
                    highContrast = contrastCb.isChecked
                    prefs().edit().putBoolean(PREF_HIGHCONTRAST, highContrast).apply()
                    recolorBoard() // apply to the current board without disturbing play
                }
                when {
                    chosen != wordLen -> {
                        prefs().edit().putInt(PREF_LEN, chosen).apply()
                        applyLength() // rebuilds the board + starts a fresh game
                    }
                    hardChanged -> { populateBoard(); startNewGame() } // resize tiles for the count lines, then start fresh
                }
            }
            .showSheet()
    }

    private fun stepButton(label: String): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(TEXT)
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        background = keyDrawable(KEY_DEFAULT)
        stateListAnimator = null
        setPadding(0, 0, 0, 0)
        val lp = LinearLayout.LayoutParams(dp(48), dp(44))
        lp.setMargins(dp(4), 0, dp(4), 0)
        layoutParams = lp
    }

    /** Attribution screen: every word-list source with its licence and a link. */
    private fun showSources() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        box.addView(TextView(this).apply {
            text = "Word lists are downloaded to your device from these sources (not bundled " +
                "in the app). Public-domain lists need no credit; the others are used under " +
                "their licence, with attribution.\n\n" +
                "Modifications: lists are filtered to the chosen word length, and — where " +
                "noted — accents are folded to base letters (e.g. é→e, ä→a)."
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(8))
        })
        fun link(text: String, url: String) = TextView(this).apply {
            this.text = text
            setTextColor(0xFF8AB4F8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
            isClickable = true
            setOnClickListener { openInBrowser(url) }
        }
        for (lang in WordLists.LANGUAGES) {
            if (lang.source.isEmpty()) continue
            val entry = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, 0)
            }
            entry.addView(TextView(this).apply {
                text = lang.label
                setTextColor(TEXT)
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })
            entry.addView(TextView(this).apply {
                text = "${lang.source} — ${lang.license}"
                setTextColor(HINT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            if (lang.homepage.isNotEmpty()) entry.addView(link("Source ›", lang.homepage))
            if (lang.licenseUrl.isNotEmpty()) entry.addView(link("Full licence text ›", lang.licenseUrl))
            if (lang.defsCredit.isNotEmpty()) {
                entry.addView(TextView(this).apply {
                    text = "Definitions: ${lang.defsCredit}"
                    setTextColor(HINT_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, dp(2), 0, 0)
                })
                entry.addView(link("Definitions licence (CC BY-SA 3.0) ›",
                    "https://creativecommons.org/licenses/by-sa/3.0/"))
            }
            box.addView(entry)
        }
        AlertDialog.Builder(this)
            .setTitle("Sources & licenses")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Close", null)
            .showSheet()
    }

    // ---------------------------------------------------------------------------------
    // Statistics (per language + settings bucket)
    // ---------------------------------------------------------------------------------

    /** Stats key for the current language + settings (length / strict / hard). */
    private fun statsBucket(): String =
        "stats_${currentLang.code}|$wordLen|${if (strictMode) "S" else "-"}|${if (hardMode) "H" else "-"}"

    // Extra stats slots stored after the ROWS win-counts and the loss count.
    private val LOSS_IDX = ROWS          // trailing loss count
    private val CUR_STREAK_IDX = ROWS + 1
    private val BEST_STREAK_IDX = ROWS + 2

    /**
     * Stored comma-separated: ROWS win-counts (by guess number), a loss count,
     * then the current win streak and the best win streak.
     */
    private fun recordResult(won: Boolean, guessRow: Int) {
        val key = statsBucket()
        val nums = parseStats(prefs().getString(key, null))
        if (won) {
            nums[guessRow]++
            nums[CUR_STREAK_IDX]++
            if (nums[CUR_STREAK_IDX] > nums[BEST_STREAK_IDX]) nums[BEST_STREAK_IDX] = nums[CUR_STREAK_IDX]
        } else {
            nums[LOSS_IDX]++
            nums[CUR_STREAK_IDX] = 0
        }
        prefs().edit().putString(key, nums.joinToString(",")).apply()
    }

    private fun parseStats(raw: String?): IntArray {
        val nums = IntArray(ROWS + 3)
        if (raw != null) {
            val parts = raw.split(",")
            for (i in nums.indices) nums[i] = parts.getOrNull(i)?.toIntOrNull() ?: 0
        }
        return nums
    }

    private fun showStats() {
        val buckets = prefs().all.keys.filter { it.startsWith("stats_") }.sorted()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        if (buckets.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "No games finished yet. Win or lose a game and your stats show up here."
                setTextColor(HINT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        }
        for (key in buckets) {
            val nums = parseStats(prefs().getString(key, null))
            val wins = (0 until ROWS).sumOf { nums[it] }
            val losses = nums[LOSS_IDX]
            val played = wins + losses
            val curStreak = nums[CUR_STREAK_IDX]
            val bestStreak = nums[BEST_STREAK_IDX]
            if (played == 0) continue
            val parts = key.removePrefix("stats_").split("|")
            val label = WordLists.LANGUAGES.firstOrNull { it.code == parts.getOrNull(0) }?.label
                ?: parts.getOrNull(0) ?: "?"
            val len = parts.getOrNull(1) ?: "?"
            val tags = buildString {
                append("$len letters")
                if (parts.getOrNull(2) == "S") append(" · strict")
                if (parts.getOrNull(3) == "H") append(" · hard")
            }
            val pct = wins * 100 / played
            box.addView(TextView(this).apply {
                text = label
                setTextColor(TEXT)
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(14), 0, 0)
            })
            box.addView(TextView(this).apply {
                text = "$tags\n$played played · $wins won ($pct%) · $losses lost" +
                    "\nStreak: $curStreak · Best: $bestStreak"
                setTextColor(HINT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            box.addView(TextView(this).apply {
                text = "Guess distribution"
                setTextColor(HINT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(6), 0, dp(2))
            })
            val maxCount = (0 until ROWS).maxOf { nums[it] }.coerceAtLeast(1)
            for (i in 0 until ROWS) {
                val count = nums[i]
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, dp(2))
                }
                row.addView(TextView(this).apply {
                    text = (i + 1).toString()
                    setTextColor(HINT_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    layoutParams = LinearLayout.LayoutParams(dp(16), WRAP_CONTENT)
                })
                // A full-width track; the filled part's width is proportional to this row's count.
                val track = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                }
                track.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(3).toFloat()
                        setColor(if (count > 0) correctColor() else DIVIDER)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        0, dp(16), count.toFloat().coerceAtLeast(0.001f))
                })
                if (maxCount - count > 0) track.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(16), (maxCount - count).toFloat())
                })
                row.addView(track)
                row.addView(TextView(this).apply {
                    text = count.toString()
                    setTextColor(TEXT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    gravity = Gravity.END
                    setPadding(dp(8), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(dp(28), WRAP_CONTENT)
                })
                box.addView(row)
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Statistics")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Reset…") { _, _ -> confirmResetStats() }
            .showSheet()
    }

    private fun confirmResetStats() {
        AlertDialog.Builder(this)
            .setTitle("Reset statistics?")
            .setMessage("This clears all wins and losses for every language and setting.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                val editor = prefs().edit()
                prefs().all.keys.filter { it.startsWith("stats_") }.forEach { editor.remove(it) }
                editor.apply()
                toast("Statistics reset")
            }
            .showSheet()
    }

    // ---------------------------------------------------------------------------------
    // Daily puzzle + public leaderboard
    // ---------------------------------------------------------------------------------

    private fun todayKey(): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date())
    }

    private fun fmtTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    /** Deterministic index into the pool for today (UTC) + length + language — same for everyone
     *  playing that language. String.hashCode is defined by spec, so the salt is stable. */
    private fun dailyIndex(len: Int, size: Int, langCode: String): Int {
        val dayUtc = System.currentTimeMillis() / 86_400_000L
        val salt = langCode.hashCode().toLong()
        return (((dayUtc * 1000003L + len * 131L + salt) % size + size) % size).toInt()
    }

    /** Groups the current language's answer pool by *typeable* length (sorted, so indexing is
     *  stable across devices). Uses [answersAll], which is already loaded for the language. */
    private fun buildDailyPool() {
        dailyPool = answersAll.asSequence()
            .map { it.trim() }.filter { it.isNotEmpty() }
            .groupBy { WordLists.typeableForm(it, currentLang).length }
            .mapValues { it.value.sorted() }
        dailyPoolLang = currentLang.code
    }

    /** Ensures the daily pool for the current language is built, then runs [onReady]. */
    private fun ensureDailyPool(onReady: () -> Unit) {
        if (dailyPool == null || dailyPoolLang != currentLang.code) {
            if (answersAll.isEmpty()) { toast("Word list not loaded yet"); return }
            buildDailyPool()
        }
        onReady()
    }

    /** Prefs key for a daily's *in-progress* guesses (distinct from the finished-result key). */
    private fun dailyProgKey() = "dailyprog_${dailyLangCode}_${dailyDateKey}_$dailyLen"

    /** Saves the current daily's guesses after each guess so leaving it can't reset progress. */
    private fun saveDailyProgress() {
        prefs().edit().putString(
            dailyProgKey(), listOf(dailyStartMs, dailyGuesses.joinToString(",")).joinToString("|")
        ).apply()
    }

    /** Paints an already-submitted guess [g] into board row [r] exactly as [onEnter] would (colours
     *  or hard-mode counts, plus the original spelling + look-up "?"). Used to restore an
     *  in-progress daily and to redraw a finished one. */
    private fun renderGuessRow(r: Int, g: String, forceColor: Boolean = false) {
        val states = evaluate(g, targetTyped)
        if (hardMode && !forceColor) {
            val inPlace = states.count { it == ST_CORRECT }
            val inWord = states.count { it == ST_CORRECT || it == ST_PRESENT }
            countViews[r].text = "$inWord in word · $inPlace in right place"
            countViews[r].visibility = View.VISIBLE
            for (c in g.indices) { tiles[r][c].text = g[c].toString(); describeTile(r, c, g[c], null) }
        } else {
            for (c in g.indices) {
                val color = stateColor(states[c])
                tileState[r][c] = states[c]
                tiles[r][c].text = g[c].toString()
                tiles[r][c].background = tileDrawable(color, color)
                updateKeyColor(g[c], states[c])
                describeTile(r, c, g[c], states[c])
            }
        }
        if (isKnownWord(g)) {
            val display = if (g == targetTyped) target else (typedToOriginal[g] ?: g)
            for (c in g.indices) tiles[r][c].text = display[c].toString()
            hintButtons[r].contentDescription = "Look up $display"
            hintButtons[r].visibility = View.VISIBLE
        }
    }

    private fun startDaily(date: String, len: Int, pool: List<String>) {
        stopTimedDuel()
        gameMode = Mode.NORMAL // the daily is a normal-rules game (leaves timed/duel)
        dailyMode = true
        dailyLen = len
        dailyDateKey = date
        dailyLangCode = currentLang.code
        dailyTargetWord = pool[dailyIndex(len, pool.size, dailyLangCode)]
        target = dailyTargetWord
        targetTyped = WordLists.typeableForm(dailyTargetWord, currentLang)
        // Rebuild the answer/accept match index for this length so guesses are recognised and the
        // "?" look-up shows — even when the daily's length differs from the one currently loaded.
        rebuildForLength(len)
        dailyGuesses.clear()
        currentRow = 0
        currentCol = 0
        gameOver = false
        clearBoard()
        // Restore an in-progress daily (saved after every guess) so leaving it — by switching word
        // length or toggling to a normal game — can't reset it and let you retry it (anti-cheat).
        val prog = prefs().getString(dailyProgKey(), null)?.split("|")
        dailyStartMs = prog?.getOrNull(0)?.toLongOrNull() ?: System.currentTimeMillis()
        for (g in prog?.getOrNull(1)?.split(",")?.filter { it.isNotBlank() }.orEmpty()) {
            if (currentRow >= ROWS || g.length != len) break
            renderGuessRow(currentRow, g)
            dailyGuesses.add(g)
            currentRow++
        }
        updateLengthButtons()
        toast("Daily · $len letters")
    }

    private fun finishDaily(won: Boolean) {
        val seconds = ((System.currentTimeMillis() - dailyStartMs) / 1000L).toInt()
        val guessCount = currentRow + 1
        val record = listOf(
            if (won) "W" else "L", seconds, guessCount, dailyTargetWord, dailyGuesses.joinToString(",")
        ).joinToString("|")
        prefs().edit()
            .putString("daily_${dailyLangCode}_${dailyDateKey}_$dailyLen", record)
            .remove(dailyProgKey()) // in-progress state is now superseded by the final result
            .apply()
        updateLengthButtons() // reflect the fresh ✓ / – badge right away
        recordResult(won, currentRow) // daily counts toward the current language's statistics
        if (won) promptSubmit(seconds, guessCount)
        else AlertDialog.Builder(this)
            .setTitle("Daily · $dailyLen letters")
            .setMessage("Not solved — the word was $dailyTargetWord.\nTime: ${fmtTime(seconds)}")
            .setPositiveButton("Leaderboard") { _, _ -> showLeaderboard(dailyLangCode, dailyDateKey, dailyLen, null) }
            .setNegativeButton("Close") { _, _ -> applyLength() }
            .showSheet()
    }

    private fun promptSubmit(seconds: Int, guessCount: Int) {
        val input = EditText(this).apply {
            setText(prefs().getString(PREF_NAME, ""))
            hint = "Name (optional)"
            setSingleLine()
            setTextColor(TEXT)
            setHintTextColor(HINT_COLOR)
        }
        AlertDialog.Builder(this)
            .setTitle("Solved in $guessCount · ${fmtTime(seconds)}")
            .setMessage("Add your score to today's leaderboard?")
            .setView(input)
            .setNegativeButton("Skip") { _, _ -> showLeaderboard(dailyLangCode, dailyDateKey, dailyLen, null) }
            .setPositiveButton("Submit") { _, _ ->
                val name = Leaderboard.sanitizeName(input.text.toString())
                prefs().edit().putString(PREF_NAME, name).apply()
                val lang = dailyLangCode
                val date = dailyDateKey
                val len = dailyLen
                showLoading(true)
                Thread {
                    try {
                        Leaderboard.submit(name, guessCount, seconds, lang, date, len)
                        ifAlive { showLoading(false); showLeaderboard(lang, date, len, name) }
                    } catch (e: Exception) {
                        ifAlive { showLoading(false); toast("Submit failed: ${e.message}"); showLeaderboard(lang, date, len, null) }
                    }
                }.start()
            }
            .showSheet()
    }

    /** Rebuilds the finished board (read-only) for a daily already played today. */
    private fun showDailyResult(date: String, len: Int, pool: List<String>, saved: String) {
        val parts = saved.split("|")
        val won = parts.getOrNull(0) == "W"
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val guessCount = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val targetWord = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
            ?: pool[dailyIndex(len, pool.size, currentLang.code)]
        val guesses = parts.getOrNull(4)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        stopTimedDuel()
        gameMode = Mode.NORMAL
        dailyMode = true
        dailyLen = len
        dailyDateKey = date
        dailyLangCode = currentLang.code
        dailyTargetWord = targetWord
        target = targetWord
        targetTyped = WordLists.typeableForm(targetWord, currentLang)
        // Rebuild the match index for this length so the replayed guesses are recognised and the
        // "?" look-up shows (see startDaily / rebuildForLength).
        rebuildForLength(len)
        currentRow = 0; currentCol = 0; gameOver = true
        clearBoard()
        updateLengthButtons()
        for ((r, g) in guesses.withIndex()) {
            if (r >= ROWS || g.length != len) break
            renderGuessRow(r, g)
        }
        AlertDialog.Builder(this)
            .setTitle("Daily · $len letters (played)")
            .setMessage(
                if (won) "You solved it in $guessCount · ${fmtTime(seconds)}."
                else "Not solved — the word was $targetWord."
            )
            .setPositiveButton("Leaderboard") { _, _ -> showLeaderboard(dailyLangCode, date, len, prefs().getString(PREF_NAME, null)) }
            .setNegativeButton("Close") { _, _ -> applyLength() }
            .showSheet()
    }

    private fun showLeaderboard(langCode: String, date: String, len: Int, highlight: String?) {
        showLoading(true)
        Thread {
            try {
                val entries = Leaderboard.fetch(langCode, date, len)
                ifAlive { showLoading(false); showLeaderboardDialog(len, entries, highlight) }
            } catch (e: Exception) {
                ifAlive { showLoading(false); toast("Leaderboard unavailable: ${e.message}") }
            }
        }.start()
    }

    private fun showLeaderboardDialog(len: Int, entries: List<Leaderboard.Entry>, highlight: String?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        box.addView(TextView(this).apply {
            text = "Today · ${currentLang.label} · $len letters — fewest guesses, then fastest time"
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(6))
        })
        if (entries.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "No scores yet today. Be the first!"
                setTextColor(TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
        }
        entries.forEachIndexed { i, e ->
            val mine = highlight != null && e.name == highlight
            box.addView(TextView(this).apply {
                text = "${i + 1}.  ${e.name}   ·   ${e.guesses} guesses   ·   ${fmtTime(e.seconds)}"
                setTextColor(if (mine) CORRECT else TEXT)
                setTypeface(if (mine) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(6), 0, 0)
            })
        }
        AlertDialog.Builder(this)
            .setTitle("Daily leaderboard")
            .setView(ScrollView(this).apply { addView(box) })
            .setNeutralButton("☕ Ko-fi") { _, _ -> openInBrowser(KOFI_URL) }
            .setPositiveButton("Close") { _, _ -> if (dailyMode) applyLength() }
            .showSheet()
    }

    private fun showLanguagePicker() {
        // Most-recently-used languages float to the top; the rest keep the catalog order.
        val recent = prefs().getString(PREF_RECENT_LANGS, "").orEmpty()
            .split(",").filter { it.isNotBlank() }
        val ordered = WordLists.LANGUAGES.sortedBy { lang ->
            val idx = recent.indexOf(lang.code)
            if (idx >= 0) idx else recent.size + WordLists.LANGUAGES.indexOf(lang)
        }
        // Compact single-line rows: the download status is a trailing glyph ("↓") instead of a
        // second text line, so twice as many languages fit before scrolling. A "›" means it's
        // already available and tapping just selects it.
        var anyToDownload = false
        val rows = ordered.map { lang ->
            val installed = lang.url == null || cacheFile(lang).exists()
            if (!installed) anyToDownload = true
            OptionRow(
                title = lang.label,
                trailing = if (installed) "›" else "↓",
                trailingColor = if (installed) null else LINK_COLOR,
            ) { selectLanguage(lang) }
        }
        showOptionList(
            "Choose language", rows,
            caption = if (anyToDownload) "↓  tap to download" else null,
        )
    }

    /** Remembers [code] as most-recently used, so the picker can surface it first (keeps 5). */
    private fun pushRecentLang(code: String) {
        val cur = prefs().getString(PREF_RECENT_LANGS, "").orEmpty()
            .split(",").filter { it.isNotBlank() && it != code }
        val updated = (listOf(code) + cur).take(5)
        prefs().edit().putString(PREF_RECENT_LANGS, updated.joinToString(",")).apply()
    }

    private fun selectLanguage(lang: WordLists.Language) {
        if (lang.url == null) {
            applyLanguage(lang, WordBank.ANSWERS.map { it.uppercase() })
            return
        }
        val cache = cacheFile(lang)
        if (cache.exists()) {
            applyLanguage(lang, cache.readLines().filter { it.isNotBlank() })
            return
        }
        // First time: download on a background thread, then cache and apply.
        showLoading(true)
        Thread {
            try {
                val words = WordLists.fetchAndFilter(lang)
                if (words.size < 20) throw IOException("only ${words.size} words found")
                cache.writeText(words.joinToString("\n"))
                ifAlive {
                    showLoading(false)
                    applyLanguage(lang, words)
                    toast("${lang.label}: ${words.size} words")
                }
            } catch (e: Exception) {
                ifAlive {
                    showLoading(false)
                    toast("Download failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun applyLanguage(lang: WordLists.Language, words: List<String>) {
        currentLang = lang
        langButton.text = lang.badge
        prefs().edit().putString(PREF_LANG, lang.code).apply()
        pushRecentLang(lang.code)
        populateKeys()
        populateLengthRow() // the selectable range can differ per language
        setWordSource(words) // → applyLength() rebuilds the board and starts the game
    }

    /** Stores the full downloaded word source (all lengths), then applies the chosen length. */
    private fun setWordSource(list: List<String>) {
        answersAll = list
        applyLength()
    }

    /**
     * Picks the effective word length for the current language (saved pref, clamped to the
     * language's range), filters [answersAll] to it, rebuilds the board + matching index,
     * and starts a fresh game.
     */
    private fun applyLength() {
        wordLen = prefs().getInt(PREF_LEN, DEFAULT_LEN).coerceIn(currentLang.minLen, currentLang.maxLen)
        rebuildForLength(wordLen)
        startNewGame()
        updateLengthButtons()
    }

    /** Sets [wordLen] to [len] and rebuilds the answer/accept match indexes and the board for it.
     *  Shared by [applyLength] and the daily entry points so a daily played at a length other than
     *  the one currently loaded still recognises words (and thus shows the "?" look-up). */
    private fun rebuildForLength(len: Int) {
        wordLen = len
        val origs = ArrayList<String>()
        val typedSet = HashSet<String>()
        val map = HashMap<String, String>()
        for (orig in answersAll) {
            val typed = WordLists.typeableForm(orig, currentLang)
            if (typed.length != wordLen) continue
            origs.add(orig)
            typedSet.add(typed)
            // Prefer an accented spelling so umlauts appear once a word matches.
            val existing = map[typed]
            if (existing == null || (orig != typed && existing == typed)) map[typed] = orig
        }
        answers = origs
        answersTyped = typedSet
        typedToOriginal = map
        refreshAcceptTyped()          // fold the broad accept list into this length
        ensureAcceptList(currentLang) // load it in the background if not present yet
        populateBoard()
    }

    /** A guess counts as a real word if it's a target word or in the broader accept list. */
    private fun isKnownWord(typed: String): Boolean =
        answersTyped.contains(typed) || acceptTyped.contains(typed)

    /** Recomputes [acceptTyped] for the current length and merges spellings for display. */
    private fun refreshAcceptTyped() {
        if (acceptAll.isEmpty()) { acceptTyped = emptySet(); return }
        val set = HashSet<String>()
        val map = HashMap<String, String>(typedToOriginal)
        for (orig in acceptAll) {
            val typed = WordLists.typeableForm(orig, currentLang)
            if (typed.length != wordLen) continue
            set.add(typed)
            if (!map.containsKey(typed)) map[typed] = orig
        }
        acceptTyped = set
        typedToOriginal = map
    }

    /** Loads the language's accept list (cache or download) on a background thread, once. */
    private fun ensureAcceptList(lang: WordLists.Language) {
        if (lang.acceptUrl.isEmpty()) {
            acceptAll = emptyList(); acceptTyped = emptySet(); acceptLang = lang.code
            return
        }
        if (acceptLang == lang.code && acceptAll.isNotEmpty()) return
        Thread {
            try {
                val cache = File(filesDir, "accept_${lang.code}_v1.txt")
                val list = if (cache.exists()) {
                    cache.readLines().filter { it.isNotBlank() }
                } else {
                    WordLists.fetchAndFilter(lang.copy(url = lang.acceptUrl)).also {
                        cache.writeText(it.joinToString("\n"), Charsets.UTF_8)
                    }
                }
                ifAlive { acceptAll = list; acceptLang = lang.code; refreshAcceptTyped() }
            } catch (e: Exception) {
                // Leave recognition to the answer list; try again on the next language apply.
            }
        }.start()
    }

    /** Returns the word list for [lang] if available offline (built-in or cached), else null. */
    private fun loadCachedOrBuiltin(lang: WordLists.Language): List<String>? {
        if (lang.url == null) return WordBank.ANSWERS.map { it.uppercase() }
        val cache = cacheFile(lang)
        if (!cache.exists()) return null
        val words = cache.readLines().filter { it.isNotBlank() }
        return words.ifEmpty { null }
    }

    // _v8: NL answer pool split from a broader accept list (two-tier); re-download.
    private fun cacheFile(lang: WordLists.Language): File =
        File(filesDir, "words_${lang.code}_v8.txt")

    private fun prefs(): SharedPreferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun showLoading(show: Boolean) {
        if (!show) {
            loadingDialog?.dismiss()
            loadingDialog = null
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        box.addView(ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        })
        box.addView(TextView(this).apply {
            text = "   Downloading word list…"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        loadingDialog = AlertDialog.Builder(this)
            .setView(box)
            .setCancelable(false)
            .show() // stays a small centred dialog — a full-width sheet for a spinner looks odd
    }

    // ---------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------

    private fun tileDrawable(fill: Int, border: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(dp(2), border)
        cornerRadius = dp(4).toFloat()
    }

    private fun keyDrawable(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(6).toFloat()
    }

    private fun spacer(height: Int, weight: Float = 0f): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, height, weight)
    }

    /** Runs [block] on the UI thread only if the activity is still alive (avoids dialogs/
     * view work on a finishing/destroyed activity when a background download completes late). */
    private fun ifAlive(block: () -> Unit) {
        runOnUiThread { if (!isFinishing && !isDestroyed) block() }
    }

    /** Shows a transient message pill just above the keyboard (name kept for call sites). */
    private fun toast(msg: String) {
        messageView.text = msg
        messageView.visibility = View.VISIBLE
        messageView.announceForAccessibility(msg)
        messageView.removeCallbacks(hideMessage)
        messageView.postDelayed(hideMessage, 1800)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
