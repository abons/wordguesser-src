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
import android.widget.Button
import android.widget.EditText
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

    private companion object {
        const val ROWS = 6
        const val DEFAULT_LEN = 5

        // Palette (classic dark Wordle look).
        const val BG = 0xFF121213.toInt()
        const val TEXT = 0xFFFFFFFF.toInt()
        const val TILE_BORDER_EMPTY = 0xFF3A3A3C.toInt()
        const val TILE_BORDER_FILLED = 0xFF565758.toInt()
        const val CORRECT = 0xFF538D4E.toInt()
        const val PRESENT = 0xFFB59F3B.toInt()
        const val ABSENT = 0xFF3A3A3C.toInt()
        const val KEY_DEFAULT = 0xFF818384.toInt()
        const val HINT_COLOR = 0xFF9AA0A6.toInt()

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
        const val PREF_NAME = "player_name"
        const val PREF_SUPPORT_LAST = "support_last"
        const val GEMINI_APP = "com.google.android.apps.bard"
        const val KOFI_URL = "https://ko-fi.com/hrbons"
    }

    private lateinit var tiles: Array<Array<TextView>>
    private lateinit var hintButtons: Array<TextView>
    private lateinit var countViews: Array<TextView> // hard-mode "in word / in place" line per row
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
    private lateinit var supportRow: TextView      // subtle Ko-fi nudge after a win
    private val hideMessage = Runnable {
        if (::messageView.isInitialized) messageView.visibility = View.GONE
    }

    // Language / word-list state.
    private lateinit var langButton: Button
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

    // Daily puzzle state (uses a fixed shared English pool so the word is global per day+length).
    private var dailyMode = false
    private var dailyLen = 0
    private var dailyDateKey = ""
    private var dailyTargetWord = ""
    private var dailyStartMs = 0L
    private val dailyGuesses = ArrayList<String>()
    private var dailyPool: Map<Int, List<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        strictMode = prefs().getBoolean(PREF_STRICT, false)
        hardMode = prefs().getBoolean(PREF_HARD, false)
        initLanguage()
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
        root.addView(spacer(dp(8)))
        root.addView(buildBoard())
        root.addView(buildReveal())
        root.addView(buildSupport())
        root.addView(spacer(0, weight = 1f)) // push keyboard to the bottom
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

    /** Subtle, tappable Ko-fi line shown under the board after a win. */
    private fun buildSupport(): View {
        supportRow = TextView(this).apply {
            text = "Enjoying it?  ☕ Support the dev ›"
            setTextColor(0xFFFF7A78.toInt()) // soft Ko-fi red
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(12), dp(12), dp(12), 0)
            setOnClickListener { openInBrowser(KOFI_URL) }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        return supportRow
    }

    /** Shows the subtle support nudge after a win — at most once per (local) day. */
    private fun showSupportPrompt() {
        val today = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())
        if (prefs().getString(PREF_SUPPORT_LAST, "") == today) return
        prefs().edit().putString(PREF_SUPPORT_LAST, today).apply()
        supportRow.visibility = View.VISIBLE
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
            text = "WORD GUESSER"
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            letterSpacing = 0.02f
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val daily = smallButton("📅") { openDaily() }.apply { contentDescription = "Daily puzzle" }
        val settings = smallButton("⚙") { showSettings() }.apply { contentDescription = "Settings" }
        val newGame = smallButton("New") {
            if (dailyMode) applyLength() else startNewGame() // restore the normal length after a daily
        }.apply { contentDescription = "New game" }
        header.addView(langButton)
        header.addView(title)
        header.addView(daily)
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
        setPadding(dp(12), dp(8), dp(12), dp(8))
        val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        lp.setMargins(dp(2), 0, dp(2), 0)
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    private fun buildBoard(): View {
        boardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        populateBoard()
        return boardContainer
    }

    /** Tile edge length (px) so [n] tiles + the side gutters fit the screen width. */
    private fun tileSizeFor(n: Int): Int {
        val screen = resources.displayMetrics.widthPixels
        val reserved = dp(12) * 2 + dp(30) * 2 // root padding + leading spacer + trailing "?"
        val per = (screen - reserved) / n
        return (per - dp(6)).coerceIn(dp(26), dp(56)) // minus per-tile L/R margins
    }

    /** (Re)builds the ROWS×[wordLen] grid, sizing tiles to fit the current word length. */
    private fun populateBoard() {
        boardContainer.removeAllViews()
        val size = tileSizeFor(wordLen)
        val hints = arrayOfNulls<TextView>(ROWS)
        val counts = arrayOfNulls<TextView>(ROWS)
        tiles = Array(ROWS) { r ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            // Leading spacer balances the trailing "?" so the tiles stay centered.
            rowLayout.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(30), size)
                visibility = View.INVISIBLE
            })
            val rowTiles = Array(wordLen) { _ ->
                val tv = TextView(this).apply {
                    gravity = Gravity.CENTER
                    setTextColor(TEXT)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.5f)
                    background = tileDrawable(Color.TRANSPARENT, TILE_BORDER_EMPTY)
                    // Skipped by TalkBack until a submitted guess gives it a spoken label.
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    val lp = LinearLayout.LayoutParams(size, size)
                    lp.setMargins(dp(3), dp(3), dp(3), dp(3))
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
                setTextSize(TypedValue.COMPLEX_UNIT_PX, (size * 0.45f).coerceAtMost(dp(22).toFloat()))
                layoutParams = LinearLayout.LayoutParams(dp(30), size)
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
            LinearLayout.LayoutParams(dp(64), dp(52)) // fixed-width for the extra row
        } else {
            LinearLayout.LayoutParams(0, dp(52), weight)
        }
        lp.setMargins(dp(2), dp(3), dp(2), dp(3))
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
        val lp = LinearLayout.LayoutParams(0, dp(52), 1.5f)
        lp.setMargins(dp(2), dp(3), dp(2), dp(3))
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    // ---------------------------------------------------------------------------------
    // Game logic
    // ---------------------------------------------------------------------------------

    private fun startNewGame() {
        dailyMode = false // any normal game leaves the daily
        if (answers.isEmpty()) return
        target = answers[Random.nextInt(answers.size)]
        targetTyped = WordLists.typeableForm(target, currentLang)
        currentRow = 0
        currentCol = 0
        gameOver = false
        clearBoard()
    }

    /** Resets tiles, hint/count lines and key colours (without picking a new target). */
    private fun clearBoard() {
        revealRow.visibility = View.GONE
        supportRow.visibility = View.GONE
        for (r in 0 until ROWS) {
            hintButtons[r].visibility = View.INVISIBLE
            countViews[r].visibility = View.GONE
            countViews[r].text = ""
            for (c in 0 until wordLen) {
                tiles[r][c].text = ""
                tiles[r][c].contentDescription = null
                tiles[r][c].importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                tiles[r][c].background = tileDrawable(Color.TRANSPARENT, TILE_BORDER_EMPTY)
            }
        }
        keyState.clear()
        for ((_, btn) in keyButtons) btn.background = keyDrawable(KEY_DEFAULT)
    }

    private fun onLetter(ch: Char) {
        if (gameOver || currentCol >= wordLen) return
        val tile = tiles[currentRow][currentCol]
        tile.text = ch.toString()
        tile.background = tileDrawable(Color.TRANSPARENT, TILE_BORDER_FILLED)
        currentCol++
    }

    private fun onDelete() {
        if (gameOver || currentCol == 0) return
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
        if (hardMode) {
            // No colours. Show only aggregate feedback under the row.
            countViews[currentRow].text = "$inWord in word · $inPlace in right place"
            countViews[currentRow].visibility = View.VISIBLE
            for (c in 0 until wordLen) describeTile(currentRow, c, guess[c], null)
        } else {
            for (c in 0 until wordLen) {
                val color = when (states[c]) {
                    ST_CORRECT -> CORRECT
                    ST_PRESENT -> PRESENT
                    else -> ABSENT
                }
                tiles[currentRow][c].background = tileDrawable(color, color)
                updateKeyColor(guess[c], states[c])
                describeTile(currentRow, c, guess[c], states[c])
            }
        }
        // Spoken summary for TalkBack (colours/tiles aren't otherwise announced).
        announce("Guess ${currentRow + 1}: $inPlace in the right place, $inWord in the word")

        // If the guess is a real word, show its original spelling (with umlauts) and a "?".
        if (!dailyMode && isKnownWord(guess)) {
            val display = if (guess == targetTyped) target else (typedToOriginal[guess] ?: guess)
            for (c in 0 until wordLen) tiles[currentRow][c].text = display[c].toString()
            hintButtons[currentRow].contentDescription = "Look up $display"
            hintButtons[currentRow].visibility = View.VISIBLE
        }
        if (dailyMode) dailyGuesses.add(guess)

        when {
            guess == targetTyped -> {
                gameOver = true
                if (dailyMode) finishDaily(won = true)
                else {
                    recordResult(won = true, guessRow = currentRow)
                    toast(winMessage(currentRow))
                    showSupportPrompt()
                }
            }
            currentRow == ROWS - 1 -> {
                gameOver = true
                if (dailyMode) finishDaily(won = false)
                else { recordResult(won = false, guessRow = currentRow); showReveal(target) }
            }
            else -> {
                currentRow++
                currentCol = 0
            }
        }
    }

    /** Per-position state (delegates to the pure, unit-tested [Wordle.evaluate]). */
    private fun evaluate(guess: String, target: String): IntArray = Wordle.evaluate(guess, target)

    private fun updateKeyColor(ch: Char, state: Int) {
        val prev = keyState[ch] ?: -1
        if (state <= prev) return // never downgrade a key's color
        keyState[ch] = state
        val color = when (state) {
            ST_CORRECT -> CORRECT
            ST_PRESENT -> PRESENT
            else -> ABSENT
        }
        keyButtons[ch]?.background = keyDrawable(color)
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
     * Otherwise (or in daily mode, which uses English words) fall back to the options menu.
     */
    private fun lookUpWord(word: String) {
        if (!dailyMode && currentLang.defsUrl.isNotEmpty()) {
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
        val cache = File(filesDir, "defs_${lang.code}_v3.json")
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
        val def = map[word.lowercase()]
        if (def == null) { lookUpMenu(word); return } // not in the list — offer other options
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
            .show()
    }

    /**
     * Offers ways to define/translate [word]: Gemini (in-app AI answer), Google Translate
     * (web, source language pre-set so it isn't mis-detected), Wiktionary, or the system
     * text-processing chooser.
     */
    private fun lookUpMenu(word: String) {
        val w = word.lowercase()
        val src = if (dailyMode || currentLang.code == "en_builtin") "en" else currentLang.code
        val dst = Locale.getDefault().language.ifBlank { "en" }
        val translateUrl =
            "https://translate.google.com/?sl=$src&tl=$dst&text=${Uri.encode(w)}&op=translate"
        val wiktionaryUrl = "https://en.wiktionary.org/wiki/${Uri.encode(w)}"

        val own = isOwnLanguage() // device language == word language → definition only
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        labels.add(if (own) "Define (Gemini)" else "Define & translate (Gemini)")
        actions.add { geminiLookup(word) }
        if (!own) { labels.add("Translate (web)"); actions.add { openInBrowser(translateUrl) } }
        labels.add("Define (Wiktionary)"); actions.add { openInBrowser(wiktionaryUrl) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            labels.add("Other apps…"); actions.add { processTextChooser(word) }
        }
        AlertDialog.Builder(this)
            .setTitle(word)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun geminiLookup(word: String) {
        val srcName = if (dailyMode) "English" else langName(currentLang.code)
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
        if (dailyMode) "en" else if (currentLang.code == "en_builtin") "en" else currentLang.code

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
            .show()
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

        box.addView(TextView(this).apply {
            text = "Statistics ›"
            setTextColor(0xFF8AB4F8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(18), 0, 0)
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
                when {
                    chosen != wordLen -> {
                        prefs().edit().putInt(PREF_LEN, chosen).apply()
                        applyLength() // rebuilds the board + starts a fresh game
                    }
                    hardChanged -> startNewGame() // apply the difficulty change cleanly
                }
            }
            .show()
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
            .show()
    }

    // ---------------------------------------------------------------------------------
    // Statistics (per language + settings bucket)
    // ---------------------------------------------------------------------------------

    /** Stats key for the current language + settings (length / strict / hard). */
    private fun statsBucket(): String =
        "stats_${currentLang.code}|$wordLen|${if (strictMode) "S" else "-"}|${if (hardMode) "H" else "-"}"

    /** Stored as ROWS win-counts (by guess number) + a trailing loss count, comma-separated. */
    private fun recordResult(won: Boolean, guessRow: Int) {
        val key = statsBucket()
        val nums = parseStats(prefs().getString(key, null))
        if (won) nums[guessRow]++ else nums[ROWS]++
        prefs().edit().putString(key, nums.joinToString(",")).apply()
    }

    private fun parseStats(raw: String?): IntArray {
        val nums = IntArray(ROWS + 1)
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
            val losses = nums[ROWS]
            val played = wins + losses
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
                text = "$tags\n$played played · $wins won ($pct%) · $losses lost"
                setTextColor(HINT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            val dist = (0 until ROWS).joinToString("   ") { "${it + 1}:${nums[it]}" }
            box.addView(TextView(this).apply {
                text = "Guesses  $dist"
                setTextColor(TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(2), 0, 0)
            })
        }
        AlertDialog.Builder(this)
            .setTitle("Statistics")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Reset…") { _, _ -> confirmResetStats() }
            .show()
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
            .show()
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

    /** Deterministic index into the pool for today (UTC) + length — same for everyone. */
    private fun dailyIndex(len: Int, size: Int): Int {
        val dayUtc = System.currentTimeMillis() / 86_400_000L
        return (((dayUtc * 1000003L + len) % size + size) % size).toInt()
    }

    /** Groups the shared English list by word length (sorted, so indexing is stable). */
    private fun buildDailyPool(lines: List<String>) {
        dailyPool = lines.asSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .groupBy { it.length }.mapValues { it.value.sorted() }
    }

    /** Ensures the shared English pool is loaded (downloading it once if needed), then runs [onReady]. */
    private fun ensureDailyPool(onReady: () -> Unit) {
        if (dailyPool != null) { onReady(); return }
        val en = WordLists.LANGUAGES.first { it.code == "en" }
        val cache = cacheFile(en)
        if (cache.exists()) {
            buildDailyPool(cache.readLines())
            onReady()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Daily puzzle")
            .setMessage("The daily uses a shared English word list, so everyone gets the same " +
                "word each day. Download it once now?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Download") { _, _ ->
                showLoading(true)
                Thread {
                    try {
                        val words = WordLists.fetchAndFilter(en)
                        cache.writeText(words.joinToString("\n"))
                        ifAlive { showLoading(false); buildDailyPool(words); onReady() }
                    } catch (e: Exception) {
                        ifAlive { showLoading(false); toast("Download failed: ${e.message}") }
                    }
                }.start()
            }
            .show()
    }

    /** Lets the player pick a daily by word length (4–8), showing today's status for each. */
    private fun openDaily() {
        ensureDailyPool {
            val date = todayKey()
            val lengths = (4..8).filter { dailyPool?.get(it)?.isNotEmpty() == true }
            if (lengths.isEmpty()) { toast("No daily available"); return@ensureDailyPool }
            val labels = lengths.map { len ->
                val saved = prefs().getString("daily_${date}_$len", null)
                val status = when {
                    saved == null -> "play"
                    saved.startsWith("W") -> "solved in ${saved.split("|").getOrNull(2) ?: "?"}"
                    else -> "not solved"
                }
                "$len letters   ·   $status"
            }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Daily puzzle")
                .setItems(labels) { _, which ->
                    val len = lengths[which]
                    val pool = dailyPool?.get(len) ?: return@setItems
                    val saved = prefs().getString("daily_${date}_$len", null)
                    if (saved != null) showDailyResult(date, len, pool, saved)
                    else startDaily(date, len, pool)
                }
                .show()
        }
    }

    private fun startDaily(date: String, len: Int, pool: List<String>) {
        dailyMode = true
        dailyLen = len
        dailyDateKey = date
        dailyTargetWord = pool[dailyIndex(len, pool.size)]
        dailyGuesses.clear()
        dailyStartMs = System.currentTimeMillis()
        target = dailyTargetWord
        targetTyped = dailyTargetWord
        if (wordLen != len) { wordLen = len; populateBoard() }
        currentRow = 0
        currentCol = 0
        gameOver = false
        clearBoard()
        toast("Daily · $len letters")
    }

    private fun finishDaily(won: Boolean) {
        val seconds = ((System.currentTimeMillis() - dailyStartMs) / 1000L).toInt()
        val guessCount = currentRow + 1
        val record = listOf(
            if (won) "W" else "L", seconds, guessCount, dailyTargetWord, dailyGuesses.joinToString(",")
        ).joinToString("|")
        prefs().edit().putString("daily_${dailyDateKey}_$dailyLen", record).apply()
        if (won) promptSubmit(seconds, guessCount)
        else AlertDialog.Builder(this)
            .setTitle("Daily · $dailyLen letters")
            .setMessage("Not solved — the word was $dailyTargetWord.\nTime: ${fmtTime(seconds)}")
            .setPositiveButton("Leaderboard") { _, _ -> showLeaderboard(dailyDateKey, dailyLen, null) }
            .setNegativeButton("Close") { _, _ -> applyLength() }
            .show()
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
            .setNegativeButton("Skip") { _, _ -> showLeaderboard(dailyDateKey, dailyLen, null) }
            .setPositiveButton("Submit") { _, _ ->
                val name = Leaderboard.sanitizeName(input.text.toString())
                prefs().edit().putString(PREF_NAME, name).apply()
                val date = dailyDateKey
                val len = dailyLen
                showLoading(true)
                Thread {
                    try {
                        Leaderboard.submit(name, guessCount, seconds, date, len)
                        ifAlive { showLoading(false); showLeaderboard(date, len, name) }
                    } catch (e: Exception) {
                        ifAlive { showLoading(false); toast("Submit failed: ${e.message}"); showLeaderboard(date, len, null) }
                    }
                }.start()
            }
            .show()
    }

    /** Rebuilds the finished board (read-only) for a daily already played today. */
    private fun showDailyResult(date: String, len: Int, pool: List<String>, saved: String) {
        val parts = saved.split("|")
        val won = parts.getOrNull(0) == "W"
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val guessCount = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val targetWord = parts.getOrNull(3)?.takeIf { it.isNotEmpty() } ?: pool[dailyIndex(len, pool.size)]
        val guesses = parts.getOrNull(4)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        dailyMode = true
        dailyLen = len
        dailyDateKey = date
        dailyTargetWord = targetWord
        target = targetWord
        targetTyped = targetWord
        if (wordLen != len) { wordLen = len; populateBoard() }
        currentRow = 0; currentCol = 0; gameOver = true
        clearBoard()
        for ((r, g) in guesses.withIndex()) {
            if (r >= ROWS || g.length != len) break
            val states = evaluate(g, targetWord)
            for (c in 0 until len) {
                tiles[r][c].text = g[c].toString()
                val color = when (states[c]) {
                    ST_CORRECT -> CORRECT
                    ST_PRESENT -> PRESENT
                    else -> ABSENT
                }
                tiles[r][c].background = tileDrawable(color, color)
                describeTile(r, c, g[c], states[c])
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Daily · $len letters (played)")
            .setMessage(
                if (won) "You solved it in $guessCount · ${fmtTime(seconds)}."
                else "Not solved — the word was $targetWord."
            )
            .setPositiveButton("Leaderboard") { _, _ -> showLeaderboard(date, len, prefs().getString(PREF_NAME, null)) }
            .setNegativeButton("Close") { _, _ -> applyLength() }
            .show()
    }

    private fun showLeaderboard(date: String, len: Int, highlight: String?) {
        showLoading(true)
        Thread {
            try {
                val entries = Leaderboard.fetch(date, len)
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
            text = "Today · $len letters — fewest guesses, then fastest time"
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
            .show()
    }

    private fun showLanguagePicker() {
        val labels = WordLists.LANGUAGES.map { lang ->
            val installed = lang.url == null || cacheFile(lang).exists()
            if (installed) lang.label else lang.label + "  ⬇"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose language")
            .setItems(labels) { _, which -> selectLanguage(WordLists.LANGUAGES[which]) }
            .show()
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
        populateKeys()
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
        startNewGame()
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
            .show()
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
