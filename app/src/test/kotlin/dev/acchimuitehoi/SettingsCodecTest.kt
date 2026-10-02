package dev.acchimuitehoi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsCodecTest {
    @Test
    fun roundTrip() {
        val s = AppSettings(
            tempo = Tempo.FAST,
            axes = Axes(flipLeftRight = true, flipUpDown = false),
            best = mapOf(Tempo.FAST to 12, Tempo.SLOW to 3),
            consentVersion = 1,
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun brokenValuesFallBackToDefaults() {
        val s = SettingsCodec.decode(
            mapOf("tempo" to "WARP", "flip_left_right" to "yes", "best_normal" to -5, "best_fast" to "9", "consent_version" to 2L),
        )
        assertEquals(AppSettings(), s)
    }

    @Test
    fun bestOnlyGoesUp() {
        val s = AppSettings().withScore(Tempo.NORMAL, 5)
        assertEquals(5, s.bestFor(Tempo.NORMAL))
        assertEquals(5, s.withScore(Tempo.NORMAL, 3).bestFor(Tempo.NORMAL))
        assertEquals(0, s.bestFor(Tempo.SLOW))
    }

    @Test
    fun consentIsNeededUntilAccepted() {
        assertTrue(AppSettings().needsConsent())
        assertTrue(!AppSettings().acceptConsent().needsConsent())
    }
}
