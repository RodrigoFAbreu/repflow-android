package com.repflow.app.presentation.recovery

import com.repflow.app.R
import com.repflow.app.domain.recovery.ReadinessFactor

/**
 * Identifies which 0-5 recovery scale field a change applies to, and the
 * readiness factor that reads the same value.
 */
enum class RecoveryScaleField(
    val readinessFactor: ReadinessFactor,
) {
    SLEEP_QUALITY(ReadinessFactor.SLEEP_QUALITY),
    ENERGY(ReadinessFactor.ENERGY),
    LEG_DOMS(ReadinessFactor.LEG_DOMS),
    HEEL_STIFFNESS(ReadinessFactor.HEEL_STIFFNESS),
    PAIN_WHILE_WALKING(ReadinessFactor.PAIN_WHILE_WALKING),
    HEAVY_LEGS(ReadinessFactor.HEAVY_LEGS),
}

/**
 * One of the entry screen's six scale rows (`3c`, `SCALES`,
 * `RepFlow.dc.html:3126-3133`): its label and the words at its two ends, as
 * string resource ids.
 */
data class RecoveryScaleSpec(
    val field: RecoveryScaleField,
    val labelRes: Int,
    val lowLabelRes: Int,
    val highLabelRes: Int,
)

/**
 * `None → Severe`: every inverted scale's ends. Declared before
 * [recoveryScaleSpecs], which reads it while the file initialises.
 */
private val severityEnds = R.string.recovery_scale_severity_low to R.string.recovery_scale_severity_high

/**
 * The six rows in `3c`'s order. The end labels follow the polarity the
 * readiness score reads (remediation-1 plan CP13 item 5): a factor that is
 * [ReadinessFactor.inverted] - high is bad - runs `None → Severe`, and the
 * other two run from their bad word to their good one. The polarity is read
 * from [ReadinessFactor] rather than restated, so the entry screen and the
 * readiness sheet cannot disagree about which end of a scale is good.
 */
val recoveryScaleSpecs: List<RecoveryScaleSpec> =
    listOf(
        scaleSpec(
            RecoveryScaleField.SLEEP_QUALITY,
            R.string.recovery_futsal_sleep_quality,
            highIsGoodEnds = R.string.recovery_scale_sleep_low to R.string.recovery_scale_sleep_high,
        ),
        scaleSpec(
            RecoveryScaleField.ENERGY,
            R.string.recovery_futsal_energy,
            highIsGoodEnds = R.string.recovery_scale_energy_low to R.string.recovery_scale_energy_high,
        ),
        scaleSpec(RecoveryScaleField.LEG_DOMS, R.string.recovery_futsal_leg_doms),
        scaleSpec(RecoveryScaleField.HEEL_STIFFNESS, R.string.recovery_futsal_heel_stiffness),
        scaleSpec(RecoveryScaleField.PAIN_WHILE_WALKING, R.string.recovery_futsal_pain_while_walking),
        scaleSpec(RecoveryScaleField.HEAVY_LEGS, R.string.recovery_futsal_heavy_legs),
    )

/**
 * @param highIsGoodEnds the bad and good words of a scale where a high number
 *   is good; required exactly when the factor is not inverted.
 */
private fun scaleSpec(
    field: RecoveryScaleField,
    labelRes: Int,
    highIsGoodEnds: Pair<Int, Int>? = null,
): RecoveryScaleSpec {
    val (low, high) =
        if (field.readinessFactor.inverted) {
            severityEnds
        } else {
            requireNotNull(highIsGoodEnds) { "$field is not inverted, so it needs its own end labels" }
        }
    return RecoveryScaleSpec(field = field, labelRes = labelRes, lowLabelRes = low, highLabelRes = high)
}
