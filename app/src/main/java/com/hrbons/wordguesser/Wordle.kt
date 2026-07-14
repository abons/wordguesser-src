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
}
