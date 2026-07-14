package com.hrbons.wordguesser

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WordleTest {
    private val a = Wordle.ST_ABSENT
    private val p = Wordle.ST_PRESENT
    private val c = Wordle.ST_CORRECT

    @Test fun allCorrect() {
        assertArrayEquals(intArrayOf(c, c, c, c, c), Wordle.evaluate("CRANE", "CRANE"))
    }

    @Test fun greensAndAbsents() {
        // SLATE vs CRANE: A and E are in place; S, L, T are not in the word.
        assertArrayEquals(intArrayOf(a, a, c, a, c), Wordle.evaluate("SLATE", "CRANE"))
    }

    @Test fun presentButWrongPlace() {
        // ERASE vs SPEED: no letter is in the right spot; E, S, E are present.
        assertArrayEquals(intArrayOf(p, a, a, p, p), Wordle.evaluate("ERASE", "SPEED"))
    }

    @Test fun duplicateGuessLettersLimitedByTarget() {
        // EERIE vs ABIDE: target has a single E (consumed by the green at pos 4),
        // so the other two E's must be ABSENT, not PRESENT. I is present (wrong place).
        assertArrayEquals(intArrayOf(a, a, a, p, c), Wordle.evaluate("EERIE", "ABIDE"))
    }

    @Test fun duplicateTargetLettersAllowTwoGreens() {
        // EEEEE vs SPEED: the two E's in the target become greens; the rest absent.
        assertArrayEquals(intArrayOf(a, a, c, c, a), Wordle.evaluate("EEEEE", "SPEED"))
    }

    @Test fun worksForOtherLengths() {
        assertArrayEquals(intArrayOf(c, c, c, c), Wordle.evaluate("ABCD", "ABCD"))
        assertArrayEquals(intArrayOf(c, c, c, c, c, c), Wordle.evaluate("PLAYER", "PLAYER"))
    }
}
