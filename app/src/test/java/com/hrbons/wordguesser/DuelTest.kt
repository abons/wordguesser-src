package com.hrbons.wordguesser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Only the pure code-generation is unit-testable here: [Duel.parseRoom] and the REST calls lean on
 * org.json / the network, which aren't available in the JVM test harness (they're exercised on
 * device, like [Leaderboard]'s HTTP paths).
 */
class DuelTest {
    private val allowed = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toSet()

    @Test fun codeHasFixedLength() {
        repeat(200) { assertEquals(Duel.CODE_LEN, Duel.newCode(Random(it.toLong())).length) }
    }

    @Test fun codeUsesUnambiguousAlphabetOnly() {
        // No 0/O/1/I so codes are easy to read aloud and type.
        repeat(500) { seed ->
            for (ch in Duel.newCode(Random(seed.toLong()))) assertTrue("bad char '$ch'", ch in allowed)
        }
    }

    @Test fun codeIsDeterministicForAGivenSeed() {
        assertEquals(Duel.newCode(Random(42)), Duel.newCode(Random(42)))
    }
}
