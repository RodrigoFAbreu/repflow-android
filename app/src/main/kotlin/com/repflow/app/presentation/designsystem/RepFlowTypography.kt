package com.repflow.app.presentation.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.repflow.app.R

/*
 * Inter is bundled as local static-weight files under res/font (400/500/600)
 * rather than fetched through Compose UI's downloadable-font provider: no
 * first-use network fetch, no new Gradle dependency, consistent with the
 * offline-first rule. Inter 4.1 is licensed under the SIL Open Font License
 * 1.1, which permits bundling; the license text is checked in at
 * app/licenses/inter/OFL.txt.
 *
 * The design's six-slot scale is display 32/500, title 25/500, numeric 32/500
 * (tabular), body 15/400, meta 12.5/400, label 11/500 uppercase-tracked. Every
 * slot below stays at 400 or 500. Weight 600 is bundled for exactly one
 * confirmed usage - the primary button's label, authored in CP3 - and is
 * deliberately not generalised into any text style here.
 */

val RepFlowFontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

private val CenteredLineHeight =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

private fun repFlowTextStyle(
    size: TextUnit,
    weight: FontWeight,
    lineHeight: TextUnit,
    letterSpacing: TextUnit = 0.sp,
    fontFeatureSettings: String? = null,
) = TextStyle(
    fontFamily = RepFlowFontFamily,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    fontFeatureSettings = fontFeatureSettings,
    lineHeightStyle = CenteredLineHeight,
)

/** display - 32/500. */
private val Display = repFlowTextStyle(32.sp, FontWeight.Medium, 40.sp)

/** title - 25/500. */
private val Title = repFlowTextStyle(25.sp, FontWeight.Medium, 32.sp)

/** body - 15/400, plus its 500-weight variant for Material 3's title slots. */
private val Body = repFlowTextStyle(15.sp, FontWeight.Normal, 22.sp)
private val BodyEmphasis = repFlowTextStyle(15.sp, FontWeight.Medium, 22.sp)

/** meta - 12.5/400. */
private val Meta = repFlowTextStyle(12.5.sp, FontWeight.Normal, 18.sp)

/** label - 11/500, uppercase-tracked. Callers uppercase the string itself. */
private val Label = repFlowTextStyle(11.sp, FontWeight.Medium, 16.sp, letterSpacing = 0.09.em)

/**
 * The "numeric" slot Material 3 has no equivalent for: 32/500 with tabular
 * figures, so loads, reps, timers and volumes do not reflow as digits change.
 * Exposed alongside `MaterialTheme.typography` rather than inside it.
 */
val RepFlowNumericTextStyle =
    repFlowTextStyle(32.sp, FontWeight.Medium, 38.sp, fontFeatureSettings = "tnum")

/**
 * The six design slots mapped onto Material 3's fifteen, as closely as they
 * fit. Stock components read these: `TopAppBar` takes `titleLarge`, `ListItem`
 * takes `bodyLarge`/`bodyMedium`, `Button` takes `labelLarge`.
 */
val RepFlowTypography =
    Typography(
        displayLarge = Display,
        displayMedium = Display,
        displaySmall = Display,
        headlineLarge = Title,
        headlineMedium = Title,
        headlineSmall = Title,
        titleLarge = Title,
        titleMedium = BodyEmphasis,
        titleSmall = BodyEmphasis,
        bodyLarge = Body,
        bodyMedium = Body,
        bodySmall = Meta,
        labelLarge = BodyEmphasis,
        labelMedium = Meta,
        labelSmall = Label,
    )
