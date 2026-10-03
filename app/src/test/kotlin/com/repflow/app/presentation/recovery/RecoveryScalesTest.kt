package com.repflow.app.presentation.recovery

import com.repflow.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Remediation-1 CP13 items 1 and 5: the entry screen's six scale rows, in
 * `3c`'s order, each labelled at both ends with the polarity the readiness
 * score reads - `None → Severe` exactly where a high number is bad.
 */
class RecoveryScalesTest {
    @Test
    fun `the six rows are 3c's in its order, one per scale field`() {
        assertEquals(
            listOf(
                RecoveryScaleField.SLEEP_QUALITY,
                RecoveryScaleField.ENERGY,
                RecoveryScaleField.LEG_DOMS,
                RecoveryScaleField.HEEL_STIFFNESS,
                RecoveryScaleField.PAIN_WHILE_WALKING,
                RecoveryScaleField.HEAVY_LEGS,
            ),
            recoveryScaleSpecs.map { it.field },
        )
    }

    @Test
    fun `every scale where a high number is bad runs None to Severe`() {
        val inverted = recoveryScaleSpecs.filter { it.field.readinessFactor.inverted }

        assertEquals(
            listOf(
                RecoveryScaleField.LEG_DOMS,
                RecoveryScaleField.HEEL_STIFFNESS,
                RecoveryScaleField.PAIN_WHILE_WALKING,
                RecoveryScaleField.HEAVY_LEGS,
            ),
            inverted.map { it.field },
        )
        inverted.forEach { spec ->
            assertEquals(spec.field.name, R.string.recovery_scale_severity_low, spec.lowLabelRes)
            assertEquals(spec.field.name, R.string.recovery_scale_severity_high, spec.highLabelRes)
        }
    }

    @Test
    fun `sleep and energy run from their bad word to their good one`() {
        val sleep = recoveryScaleSpecs.single { it.field == RecoveryScaleField.SLEEP_QUALITY }
        val energy = recoveryScaleSpecs.single { it.field == RecoveryScaleField.ENERGY }

        assertEquals(R.string.recovery_scale_sleep_low, sleep.lowLabelRes)
        assertEquals(R.string.recovery_scale_sleep_high, sleep.highLabelRes)
        assertEquals(R.string.recovery_scale_energy_low, energy.lowLabelRes)
        assertEquals(R.string.recovery_scale_energy_high, energy.highLabelRes)
    }

    @Test
    fun `each row is labelled with its own scale's name`() {
        assertEquals(
            listOf(
                R.string.recovery_futsal_sleep_quality,
                R.string.recovery_futsal_energy,
                R.string.recovery_futsal_leg_doms,
                R.string.recovery_futsal_heel_stiffness,
                R.string.recovery_futsal_pain_while_walking,
                R.string.recovery_futsal_heavy_legs,
            ),
            recoveryScaleSpecs.map { it.labelRes },
        )
    }
}
