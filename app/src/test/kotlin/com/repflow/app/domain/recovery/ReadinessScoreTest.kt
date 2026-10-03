package com.repflow.app.domain.recovery

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Pins the readiness engine transcribed from the prototype
 * (`RepFlow.dc.html:3142-3166`), per remediation-1 plan CP4 item 5.
 *
 * Every boundary input below was found by enumerating all 6^6 check-ins; the
 * comment beside each gives the exact pre-rounding value (`10 * S / 31`).
 */
class ReadinessScoreTest {
    // region Weights and inversion

    @Test
    fun `the six weights are the prototype's, in its order`() {
        assertEquals(
            listOf(
                ReadinessFactor.SLEEP_QUALITY to 10,
                ReadinessFactor.ENERGY to 12,
                ReadinessFactor.LEG_DOMS to 10,
                ReadinessFactor.HEAVY_LEGS to 8,
                ReadinessFactor.HEEL_STIFFNESS to 7,
                ReadinessFactor.PAIN_WHILE_WALKING to 15,
            ),
            ReadinessFactor.entries.map { it to it.weightTenths },
        )
        assertEquals(ReadinessScore.TOTAL_WEIGHT_TENTHS, ReadinessFactor.entries.sumOf { it.weightTenths })
    }

    @Test
    fun `the four soreness scales are inverted and sleep and energy are not`() {
        assertEquals(
            setOf(
                ReadinessFactor.LEG_DOMS,
                ReadinessFactor.HEAVY_LEGS,
                ReadinessFactor.HEEL_STIFFNESS,
                ReadinessFactor.PAIN_WHILE_WALKING,
            ),
            ReadinessFactor.entries.filter { it.inverted }.toSet(),
        )
        val readings = score(sleep = 4, energy = 1, legDoms = 4, heelStiffness = 1).factors.associateBy { it.factor }
        assertEquals(4, readings.getValue(ReadinessFactor.SLEEP_QUALITY).normalized)
        assertEquals(1, readings.getValue(ReadinessFactor.ENERGY).normalized)
        assertEquals(1, readings.getValue(ReadinessFactor.LEG_DOMS).normalized)
        assertEquals(4, readings.getValue(ReadinessFactor.HEEL_STIFFNESS).normalized)
    }

    @Test
    fun `every factor reads its own field of the entry`() {
        val readings =
            score(sleep = 1, energy = 2, legDoms = 3, heavyLegs = 4, heelStiffness = 0, pain = 2)
                .factors
                .associate { it.factor to it.value }
        assertEquals(
            mapOf(
                ReadinessFactor.SLEEP_QUALITY to 1,
                ReadinessFactor.ENERGY to 2,
                ReadinessFactor.LEG_DOMS to 3,
                ReadinessFactor.HEAVY_LEGS to 4,
                ReadinessFactor.HEEL_STIFFNESS to 0,
                ReadinessFactor.PAIN_WHILE_WALKING to 2,
            ),
            readings,
        )
    }

    // endregion

    // region Score and rounding

    @Test
    fun `all best is 100 and Ready`() {
        val readiness = score(sleep = 5, energy = 5, legDoms = 0, heavyLegs = 0, heelStiffness = 0, pain = 0)
        assertEquals(100, readiness.score)
        assertEquals(ReadinessBand.READY, readiness.band)
    }

    @Test
    fun `all worst is 0 and Protect`() {
        val readiness = score(sleep = 0, energy = 0, legDoms = 5, heavyLegs = 5, heelStiffness = 5, pain = 5)
        assertEquals(0, readiness.score)
        assertEquals(ReadinessBand.PROTECT, readiness.band)
    }

    @Test
    fun `the prototype's seed check-in rounds down to 75 Ready`() {
        // 75.16
        val readiness = seed()
        assertEquals(75, readiness.score)
        assertEquals(ReadinessBand.READY, readiness.band)
        assertFalse(readiness.isPainGated)
    }

    @Test
    fun `the seed with heavy legs 3 rounds up to 73 Hold`() {
        // 72.58
        val readiness = seed(heavyLegs = 3)
        assertEquals(73, readiness.score)
        assertEquals(ReadinessBand.HOLD, readiness.band)
    }

    // endregion

    // region Band thresholds, each from both sides

    @Test
    fun `75 is Ready and 74 is Hold`() {
        // 75.16 and 73.87
        assertBand(75, ReadinessBand.READY, score(sleep = 0, energy = 4, legDoms = 0, heavyLegs = 0, heelStiffness = 0, pain = 1))
        assertBand(74, ReadinessBand.HOLD, score(sleep = 0, energy = 3, legDoms = 0, heavyLegs = 0, heelStiffness = 1, pain = 0))
    }

    @Test
    fun `58 is Hold and 57 is Back off`() {
        // 57.74 and 57.42
        assertBand(58, ReadinessBand.HOLD, score(sleep = 0, energy = 0, legDoms = 0, heavyLegs = 0, heelStiffness = 3, pain = 0))
        assertBand(57, ReadinessBand.BACK_OFF, score(sleep = 0, energy = 0, legDoms = 0, heavyLegs = 0, heelStiffness = 1, pain = 1))
    }

    @Test
    fun `42 is Back off and 41 is Protect`() {
        // 42.26 and 41.29
        assertBand(42, ReadinessBand.BACK_OFF, score(sleep = 0, energy = 0, legDoms = 0, heavyLegs = 4, heelStiffness = 1, pain = 2))
        assertBand(41, ReadinessBand.PROTECT, score(sleep = 0, energy = 0, legDoms = 1, heavyLegs = 4, heelStiffness = 0, pain = 2))
    }

    @Test
    fun `a Protect reached by the score alone is not reported as pain-gated`() {
        assertFalse(score(sleep = 0, energy = 0, legDoms = 1, heavyLegs = 4, heelStiffness = 0, pain = 2).isPainGated)
    }

    // endregion

    // region Pain gate

    @Test
    fun `pain while walking gates at 3, not 2`() {
        // Otherwise all best: pain 2 scores 90 and stays Ready.
        val below = best(pain = 2)
        assertBand(90, ReadinessBand.READY, below)
        assertFalse(below.isPainGated)

        val at = best(pain = 3)
        assertEquals(ReadinessBand.PROTECT, at.band)
        assertTrue(at.isPainGated)
        assertEquals("the gate overrides the band, not the number", 85, at.score)
    }

    @Test
    fun `heel stiffness gates at 4, not 3`() {
        val below = best(heelStiffness = 3)
        assertBand(93, ReadinessBand.READY, below)
        assertFalse(below.isPainGated)

        val at = best(heelStiffness = 4)
        assertEquals(ReadinessBand.PROTECT, at.band)
        assertTrue(at.isPainGated)
        assertEquals(91, at.score)
    }

    // endregion

    // region Drivers

    @Test
    fun `a factor is flagged at normalized 2 and fine at 3`() {
        val flagged = best(sleep = 2).factors.first { it.factor == ReadinessFactor.SLEEP_QUALITY }
        val fine = best(sleep = 3).factors.first { it.factor == ReadinessFactor.SLEEP_QUALITY }
        assertTrue(flagged.isFlagged)
        assertFalse(fine.isFlagged)

        // Inverted: DOMS 3 normalizes to 2 (flagged), DOMS 2 to 3 (fine).
        assertTrue(best(legDoms = 3).factors.first { it.factor == ReadinessFactor.LEG_DOMS }.isFlagged)
        assertFalse(best(legDoms = 2).factors.first { it.factor == ReadinessFactor.LEG_DOMS }.isFlagged)
    }

    @Test
    fun `the driver sentence lowercases the whole label and names the recorded value`() {
        assertEquals(
            "Driven by leg doms 3/5, heavy legs 4/5.",
            best(legDoms = 3, heavyLegs = 4).driverSentence,
        )
    }

    @Test
    fun `the driver sentence names at most three, in the prototype's order`() {
        // Four flagged; heel stiffness is fourth in RDY order and is dropped.
        val readiness = best(sleep = 1, energy = 2, heavyLegs = 5, heelStiffness = 3)
        assertEquals(
            listOf(
                ReadinessFactor.SLEEP_QUALITY,
                ReadinessFactor.ENERGY,
                ReadinessFactor.HEAVY_LEGS,
            ),
            readiness.drivers.map { it.factor },
        )
        assertEquals("Driven by sleep quality 1/5, energy 2/5, heavy legs 5/5.", readiness.driverSentence)
    }

    @Test
    fun `nothing flagged gives the all-clear sentence`() {
        assertTrue(seed().drivers.isEmpty())
        assertEquals("Nothing is flagged — every input is in the top half of its scale.", seed().driverSentence)
    }

    @Test
    fun `the per-factor rows keep the label's own casing`() {
        assertEquals(
            listOf("Sleep quality", "Energy", "Leg DOMS", "Heavy legs", "Heel stiffness", "Pain while walking"),
            seed().factors.map { it.factor.label },
        )
    }

    // endregion

    @Test
    fun `the futsal flags do not change the result`() {
        val without = ReadinessScore.of(entry(4, 3, 2, 2, 1, 0, futsalPrevious = false, futsalNext = false))
        val with = ReadinessScore.of(entry(4, 3, 2, 2, 1, 0, futsalPrevious = true, futsalNext = true))
        assertEquals(without, with)
    }

    private fun assertBand(
        expectedScore: Int,
        expectedBand: ReadinessBand,
        readiness: ReadinessScore,
    ) {
        assertEquals(expectedScore, readiness.score)
        assertEquals(expectedBand, readiness.band)
    }

    /** The prototype's seed: sleep 4, energy 3, DOMS 2, heel 1, pain 0, heavy legs 2. */
    private fun seed(heavyLegs: Int = 2): ReadinessScore =
        score(sleep = 4, energy = 3, legDoms = 2, heavyLegs = heavyLegs, heelStiffness = 1, pain = 0)

    /** Every input at its best unless named. */
    @Suppress("LongParameterList")
    private fun best(
        sleep: Int = 5,
        energy: Int = 5,
        legDoms: Int = 0,
        heavyLegs: Int = 0,
        heelStiffness: Int = 0,
        pain: Int = 0,
    ): ReadinessScore = score(sleep, energy, legDoms, heavyLegs, heelStiffness, pain)

    @Suppress("LongParameterList")
    private fun score(
        sleep: Int = 5,
        energy: Int = 5,
        legDoms: Int = 0,
        heavyLegs: Int = 0,
        heelStiffness: Int = 0,
        pain: Int = 0,
    ): ReadinessScore = ReadinessScore.of(entry(sleep, energy, legDoms, heavyLegs, heelStiffness, pain))

    @Suppress("LongParameterList")
    private fun entry(
        sleep: Int,
        energy: Int,
        legDoms: Int,
        heavyLegs: Int,
        heelStiffness: Int,
        pain: Int,
        futsalPrevious: Boolean = false,
        futsalNext: Boolean = false,
    ): RecoveryEntry {
        val at = Instant.parse("2026-08-11T07:00:00Z")
        val result =
            RecoveryEntry.create(
                id = RecoveryEntryId("recovery-1"),
                date = LocalDate.parse("2026-08-11"),
                sleepQuality = sleep,
                energy = energy,
                legDoms = legDoms,
                heelStiffness = heelStiffness,
                painWhileWalking = pain,
                heavyLegs = heavyLegs,
                futsalInPrevious24h = futsalPrevious,
                futsalExpectedNext24h = futsalNext,
                notes = null,
                createdAt = at,
                updatedAt = at,
            )
        return (result as DomainResult.Success).value
    }
}
