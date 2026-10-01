package com.repflow.app.domain.recovery

/*
 * The readiness score: one 0..100 number and a band, derived from a single
 * day's RecoveryEntry.
 *
 * This is the Claude Design prototype's own readiness engine, transcribed
 * rather than invented (remediation-1 plan CP4 item 1, adopted by the user on
 * 2026-09-30): `RepFlow.dc.html:3142-3166` (`RDY`, `RDY_BANDS`, `readiness()`)
 * for the weights, the pain gate and the bands, and `nRdyFactors` /
 * `nRdyDrivers` (`:3797-3813`) for the flagged rule and the driver sentence.
 * The entry's futsal flags are deliberately not inputs: the prototype's
 * engine does not read them.
 *
 * Nothing in the app acts on the band yet - it is a description of the
 * day, not a rule. ProgressionPolicyV1 reads the recovery entry on its own
 * terms and is unchanged by this.
 */

/**
 * One check-in scale as the readiness engine weighs it, in the prototype's
 * `RDY` order - which is also the order drivers are named in.
 *
 * Weights are held in integer tenths (1.2 -> 12) so the score is computed
 * exactly; [weightTenths] summed over every factor is [TOTAL_WEIGHT_TENTHS].
 *
 * @property label the prototype's own label, in its own casing.
 * @property inverted `true` where a high value is bad (soreness, stiffness,
 *   pain), so the engine reads `5 - value`.
 */
enum class ReadinessFactor(
    val label: String,
    val weightTenths: Int,
    val inverted: Boolean,
) {
    SLEEP_QUALITY("Sleep quality", weightTenths = 10, inverted = false),
    ENERGY("Energy", weightTenths = 12, inverted = false),
    LEG_DOMS("Leg DOMS", weightTenths = 10, inverted = true),
    HEAVY_LEGS("Heavy legs", weightTenths = 8, inverted = true),
    HEEL_STIFFNESS("Heel stiffness", weightTenths = 7, inverted = true),
    PAIN_WHILE_WALKING("Pain while walking", weightTenths = 15, inverted = true),
    ;

    /** This factor's recorded value on [entry], on the entry's own 0..5 scale. */
    fun valueIn(entry: RecoveryEntry): Int =
        when (this) {
            SLEEP_QUALITY -> entry.sleepQuality
            ENERGY -> entry.energy
            LEG_DOMS -> entry.legDoms
            HEAVY_LEGS -> entry.heavyLegs
            HEEL_STIFFNESS -> entry.heelStiffness
            PAIN_WHILE_WALKING -> entry.painWhileWalking
        }
}

/** The prototype's four bands (`RDY_BANDS` green / amber / orange / red), worst last. */
enum class ReadinessBand {
    READY,
    HOLD,
    BACK_OFF,
    PROTECT,
}

/**
 * One factor's contribution to a [ReadinessScore].
 *
 * @property value the recorded 0..5 value, as the user entered it.
 * @property normalized 0..5 where 5 is always best: [value] itself, or
 *   `5 - value` for an [ReadinessFactor.inverted] factor.
 * @property isFlagged the sheet's "pulling the score down": the factor sits in
 *   the bottom half of its scale (`normalized <= 2`).
 */
data class ReadinessFactorReading(
    val factor: ReadinessFactor,
    val value: Int,
) {
    val normalized: Int = if (factor.inverted) SCALE_MAX - value else value

    val isFlagged: Boolean = normalized <= FLAGGED_AT_OR_BELOW
}

/**
 * The readiness score for one [RecoveryEntry]. Build it with [of].
 *
 * @property score `round(100 x sum(normalized / 5 x weight) / sum(weight))`,
 *   on 0..100. The exact value is `10 * S / 31` for an integer `S`, which is
 *   never a half, so the rounding direction at `.5` never arises.
 * @property isPainGated pain while walking at 3 or more, or heel stiffness at
 *   4 or more. A gated score is always [ReadinessBand.PROTECT], whatever the
 *   number says.
 * @property factors one reading per [ReadinessFactor], in that enum's order.
 */
@ConsistentCopyVisibility
data class ReadinessScore private constructor(
    val score: Int,
    val band: ReadinessBand,
    val isPainGated: Boolean,
    val factors: List<ReadinessFactorReading>,
) {
    /** The flagged factors the driver sentence names: the first three, in [ReadinessFactor] order. */
    val drivers: List<ReadinessFactorReading>
        get() = factors.filter { it.isFlagged }.take(MAX_DRIVERS)

    /**
     * The prototype's `nRdyDrivers` sentence, verbatim in shape: each driver
     * as `<label lowercased> <value>/5` - "Driven by leg doms 3/5, heavy legs
     * 4/5." - or the all-clear line when nothing is flagged.
     */
    val driverSentence: String
        get() {
            val named = drivers
            if (named.isEmpty()) return NOTHING_FLAGGED_SENTENCE
            return named.joinToString(
                separator = ", ",
                prefix = "Driven by ",
                postfix = ".",
            ) { "${it.factor.label.lowercase()} ${it.value}/$SCALE_MAX" }
        }

    companion object {
        /** `RDY`'s weights summed, in tenths: 10 + 12 + 10 + 8 + 7 + 15. */
        const val TOTAL_WEIGHT_TENTHS = 62

        const val NOTHING_FLAGGED_SENTENCE = "Nothing is flagged — every input is in the top half of its scale."

        private const val READY_FROM = 75
        private const val HOLD_FROM = 58
        private const val BACK_OFF_FROM = 42
        private const val PAIN_WALKING_GATE = 3
        private const val HEEL_STIFFNESS_GATE = 4
        private const val MAX_DRIVERS = 3

        fun of(entry: RecoveryEntry): ReadinessScore {
            val factors = ReadinessFactor.entries.map { ReadinessFactorReading(it, it.valueIn(entry)) }
            val weighted = factors.sumOf { it.normalized * it.factor.weightTenths }
            val score = roundedScore(weighted)
            val gated =
                entry.painWhileWalking >= PAIN_WALKING_GATE ||
                    entry.heelStiffness >= HEEL_STIFFNESS_GATE
            return ReadinessScore(
                score = score,
                band = if (gated) ReadinessBand.PROTECT else bandFor(score),
                isPainGated = gated,
                factors = factors,
            )
        }

        /**
         * `round(100 * weighted / (5 * 62))`, nearest, in integers:
         * `floor((200 * weighted + 310) / 620)`, i.e. `floor(x + 1/2)`.
         */
        private fun roundedScore(weighted: Int): Int {
            val denominator = SCALE_MAX * TOTAL_WEIGHT_TENTHS
            return (2 * PERCENT * weighted + denominator) / (2 * denominator)
        }

        private fun bandFor(score: Int): ReadinessBand =
            when {
                score >= READY_FROM -> ReadinessBand.READY
                score >= HOLD_FROM -> ReadinessBand.HOLD
                score >= BACK_OFF_FROM -> ReadinessBand.BACK_OFF
                else -> ReadinessBand.PROTECT
            }
    }
}

/** Every check-in scale runs 0..5 ([RecoveryEntry]'s own range). */
private const val SCALE_MAX = 5

/** `n <= 2` - the bottom half of a 0..5 scale - is "pulling the score down". */
private const val FLAGGED_AT_OR_BELOW = 2

private const val PERCENT = 100
