package com.repflow.app.presentation.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Radii come from RepFlow's own literal component spec ("radii 8 for controls,
 * 10-12 for cards and buttons, 14-16 for sheets"), not the generic Nocturne
 * stylesheet's 4/8/14 scale, which no RepFlow screen actually uses. Each named
 * range collapses to a single representative value rather than being encoded
 * as a range.
 */

val RepFlowShapeScheme =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

/**
 * The three confirmed one-off radii that fall outside the ranges above. They
 * are enumerated design values, not approximations, so they are named here
 * instead of being hardcoded per call site - which also lets CP7's
 * verification grep treat any *other* literal radius as a real regression.
 */
object RepFlowShapes {
    /** 44x44 stepper buttons in the set-entry pad. */
    val stepper = RoundedCornerShape(9.dp)

    /** 60x60 floating action button on the Exercise list. */
    val fab = RoundedCornerShape(18.dp)

    /** Status chips and interactive pill rows. */
    val pill = RoundedCornerShape(999.dp)
}
