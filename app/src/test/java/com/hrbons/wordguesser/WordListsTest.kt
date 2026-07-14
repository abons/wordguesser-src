package com.hrbons.wordguesser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordListsTest {
    private val de = WordLists.Language("de", "DE", "German", null, extra = "ß", fold = true)
    private val en = WordLists.Language("en", "EN", "English", null, fold = false)

    // ---- typeableForm: what the player types ----

    @Test fun foldsUmlautsToBaseLetters() {
        assertEquals("SCHON", WordLists.typeableForm("SCHÖN", de))
    }

    @Test fun keepsEszettWithoutExpanding() {
        // String.uppercase() would turn ß into "SS"; typeableForm must keep length.
        assertEquals("GRUß", WordLists.typeableForm("gruß", de))
        assertEquals("AUßEN", WordLists.typeableForm("AUßEN", de))
    }

    @Test fun nonFoldLanguageJustUppercases() {
        assertEquals("WORD", WordLists.typeableForm("word", en))
    }

    // ---- clean: source filtering / normalization ----

    @Test fun keepsPlainWordUppercased() {
        assertEquals("WORD", WordLists.clean("word", "", false, 4, 8))
    }

    @Test fun stripsHunspellFlags() {
        assertEquals("WORD", WordLists.clean("word/ABC", "", false, 4, 8))
    }

    @Test fun rejectsProperNounsAndAcronyms() {
        assertNull(WordLists.clean("Ansen", "", false, 4, 8)) // capitalised proper noun
        assertNull(WordLists.clean("CCVII", "", false, 4, 8)) // Roman numeral (all caps)
    }

    @Test fun rejectsOutOfRangeLengths() {
        assertNull(WordLists.clean("ab", "", false, 4, 8))
        assertNull(WordLists.clean("abcdefghi", "", false, 4, 8)) // 9 letters
    }

    @Test fun nonFoldDropsAccentedWords() {
        assertNull(WordLists.clean("café", "", false, 4, 8))
    }

    @Test fun germanStoresOriginalSpelling() {
        assertEquals("SCHÖN", WordLists.clean("schön", "ß", true, 4, 8))
        assertEquals("AUßEN", WordLists.clean("außen", "ß", true, 4, 8))
    }
}
