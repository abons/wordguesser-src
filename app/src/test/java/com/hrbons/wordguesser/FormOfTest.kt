package com.hrbons.wordguesser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormOfTest {

    private val map = mapOf(
        "rollen" to "zich wentelend over een oppervlak bewegen",
        "aardig" to "vriendelijk",
        "lopen" to "stappen, gaan, wandelen",
        // A base whose own definition is itself a form-of gloss (should NOT chain).
        "geleend" to "voltooid deelwoord van lenen",
        "beton" to "steenachtig bouwmateriaal",
    )

    @Test fun presentTenseSecondPersonResolvesToBase() {
        assertEquals(
            "tweede persoon enkelvoud tegenwoordige tijd van rollen:\n\n" +
                "zich wentelend over een oppervlak bewegen",
            MainActivity.resolveFormOf(
                "tweede persoon enkelvoud tegenwoordige tijd van rollen", map))
    }

    @Test fun presentTenseFirstPersonResolvesToBase() {
        assertEquals("lopen", MainActivity.formOfBase(
            "eerste persoon enkelvoud tegenwoordige tijd van lopen"))
    }

    @Test fun pastTenseResolvesToBase() {
        assertEquals("rollen", MainActivity.formOfBase("enkelvoud verleden tijd van rollen"))
        assertEquals("rollen", MainActivity.formOfBase("voltooid deelwoord van rollen"))
    }

    @Test fun comparativeWithNestedVanResolvesToBase() {
        // "verbogen vorm van de stellende trap van aardig" — two " van "; base is the last token.
        assertEquals("aardig", MainActivity.formOfBase(
            "verbogen vorm van de stellende trap van aardig"))
    }

    @Test fun realDefinitionWithVanIsLeftUntouched() {
        // Content words ("gemaakt", "het vormen van") mean this is a real meaning, not a form-of.
        assertNull(MainActivity.formOfBase("bak gemaakt van beton"))
        assertNull(MainActivity.formOfBase("het vormen van bubbels"))
        val def = "bak gemaakt van beton"
        assertEquals(def, MainActivity.resolveFormOf(def, map))
    }

    @Test fun doesNotChainIntoAnotherFormOfGloss() {
        // "geleend"'s own definition is itself a form-of gloss, so we keep the original.
        val def = "verbogen vorm van geleend"
        assertEquals(def, MainActivity.resolveFormOf(def, map))
    }

    @Test fun unknownBaseIsLeftUntouched() {
        val def = "enkelvoud verleden tijd van fietsen"
        assertEquals(def, MainActivity.resolveFormOf(def, map))
    }

    @Test fun plainDefinitionIsNotAFormOf() {
        assertNull(MainActivity.formOfBase("zich wentelend over een oppervlak bewegen"))
    }
}
