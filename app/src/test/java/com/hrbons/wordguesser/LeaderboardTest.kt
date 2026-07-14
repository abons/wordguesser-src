package com.hrbons.wordguesser

import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardTest {

    @Test fun sanitizeNameKeepsAlnumUppercase() {
        assertEquals("AXEL", Leaderboard.sanitizeName("Axel!"))
        assertEquals("ABC", Leaderboard.sanitizeName("a b c"))
    }

    @Test fun sanitizeNameFallsBackToAnon() {
        assertEquals("ANON", Leaderboard.sanitizeName(""))
        assertEquals("ANON", Leaderboard.sanitizeName("!!!"))
    }

    @Test fun sanitizeNameCapsAtTenChars() {
        assertEquals("VERYLONGNA", Leaderboard.sanitizeName("verylongname123"))
    }

    @Test fun rankFiltersByDayAndLengthAndSorts() {
        val body = """
            AXL_20260714_5|3|95||d|0
            BOB_20260714_5|2|140||d|1
            CAT_20260714_6|4|60||d|2
            DAN_20260714_5|2|80||d|3
            ZZZ_20260713_5|1|10||d|4
        """.trimIndent()

        val ranked = Leaderboard.rank(body, "20260714", 5)

        // CAT (len 6) and ZZZ (other day) are excluded; order is fewest guesses, then time.
        assertEquals(listOf("DAN", "BOB", "AXL"), ranked.map { it.name })
        assertEquals(2, ranked[0].guesses)
        assertEquals(80, ranked[0].seconds)
    }
}
