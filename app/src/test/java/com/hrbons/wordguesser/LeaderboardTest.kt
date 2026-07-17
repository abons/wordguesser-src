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

    @Test fun rankFiltersByLangDayAndLengthAndSorts() {
        val body = """
            AXL_nl_20260714_5|3|95||d|0
            BOB_nl_20260714_5|2|140||d|1
            CAT_nl_20260714_6|4|60||d|2
            DAN_nl_20260714_5|2|80||d|3
            ZZZ_nl_20260713_5|1|10||d|4
            EVE_de_20260714_5|1|30||d|5
        """.trimIndent()

        val ranked = Leaderboard.rank(body, "nl", "20260714", 5)

        // CAT (len 6), ZZZ (other day) and EVE (other language) are excluded;
        // order is fewest guesses, then time.
        assertEquals(listOf("DAN", "BOB", "AXL"), ranked.map { it.name })
        assertEquals(2, ranked[0].guesses)
        assertEquals(80, ranked[0].seconds)
    }

    @Test fun rankSeparatesLanguages() {
        val body = """
            NLP_nl_20260714_5|2|50||d|0
            DEP_de_20260714_5|2|50||d|1
        """.trimIndent()

        assertEquals(listOf("NLP"), Leaderboard.rank(body, "nl", "20260714", 5).map { it.name })
        assertEquals(listOf("DEP"), Leaderboard.rank(body, "de", "20260714", 5).map { it.name })
    }

    @Test fun rankHandlesUnderscoreInLangCode() {
        val body = "PRO_en_builtin_20260714_5|2|50||d|0"
        assertEquals(listOf("PRO"), Leaderboard.rank(body, "en_builtin", "20260714", 5).map { it.name })
    }
}
