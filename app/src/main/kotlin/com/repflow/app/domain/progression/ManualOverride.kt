package com.repflow.app.domain.progression

import java.time.Instant

/**
 * A user decision to perform something different from the system
 * recommendation, per `docs/DOMAIN_GLOSSARY.md`'s "Manual override".
 *
 * The original [ProgressionRecommendation] is never mutated or replaced -
 * only annotated with this override, so both are preserved.
 */
data class ManualOverride(
    val result: ProgressionResult,
    val overriddenAt: Instant,
)
