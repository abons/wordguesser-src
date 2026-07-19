package com.hrbons.wordguesser

/** Pure Wordle scoring — no Android dependencies, so it can be unit-tested directly. */
object Wordle {
    const val ST_ABSENT = 0
    const val ST_PRESENT = 1
    const val ST_CORRECT = 2

    /**
     * Per-position result for [guess] against [target] (assumed equal length), with correct
     * duplicate-letter handling: greens are counted first, then each remaining target letter
     * can back at most one yellow.
     */
    fun evaluate(guess: String, target: String): IntArray {
        val n = guess.length
        val result = IntArray(n) { ST_ABSENT }
        val remaining = HashMap<Char, Int>()
        for (c in target) remaining[c] = (remaining[c] ?: 0) + 1

        for (i in 0 until n) {
            if (i < target.length && guess[i] == target[i]) {
                result[i] = ST_CORRECT
                remaining[guess[i]] = remaining[guess[i]]!! - 1
            }
        }
        for (i in 0 until n) {
            if (result[i] == ST_ABSENT) {
                val c = guess[i]
                if ((remaining[c] ?: 0) > 0) {
                    result[i] = ST_PRESENT
                    remaining[c] = remaining[c]!! - 1
                }
            }
        }
        return result
    }

    /**
     * Words from [words] that are consistent with every clue a guesser has seen so far: a word
     * `w` survives when, for each of [priorGuesses], scoring that guess against `w` would give the
     * exact same colours it gave against the real [target]. Words already in [priorGuesses] are
     * excluded so the guesser never repeats itself. All strings are assumed to be the same
     * (typeable) length. Used by the duel NPC to pick a plausible next guess; kept pure and here
     * so it can be unit-tested without Android.
     */
    fun consistentCandidates(
        words: List<String>,
        priorGuesses: List<String>,
        target: String,
    ): List<String> {
        if (priorGuesses.isEmpty()) return words
        val already = priorGuesses.toHashSet()
        val clues = priorGuesses.map { it to evaluate(it, target) }
        return words.filter { w ->
            w !in already && clues.all { (g, states) -> evaluate(g, w).contentEquals(states) }
        }
    }
}
