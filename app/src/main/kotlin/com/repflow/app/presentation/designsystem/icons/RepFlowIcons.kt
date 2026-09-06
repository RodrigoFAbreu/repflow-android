package com.repflow.app.presentation.designsystem.icons

import androidx.annotation.DrawableRes
import com.repflow.app.R

/*
 * The bounded icon set for the visual-foundation milestone.
 *
 * The target design uses Phosphor throughout, but this milestone needs a
 * couple of dozen glyphs across three surfaces - not an icon library. So the
 * glyphs actually used are bundled as local vector drawables
 * (res/drawable/ic_ph_*.xml, path data copied verbatim from the upstream SVGs
 * on Phosphor's own 256x256 grid) and reached through this one object. No
 * Gradle dependency is added, and neither material-icons-core nor
 * material-icons-extended becomes reachable as a side effect.
 *
 * Phosphor Icons is MIT licensed; the license text is bundled at
 * app/licenses/phosphor/LICENSE.
 *
 * Adding a glyph means adding its drawable and one entry here - deliberately
 * cheap, so a later surface does not reach for an icon dependency instead.
 */

/**
 * Every icon this milestone's surfaces draw, named after its upstream Phosphor
 * glyph so a reader can check any entry against the source set directly.
 *
 * Values are drawable resource ids rather than `Painter`s or `ImageVector`s so
 * that non-composable holders can carry one - [Nav] below, and the
 * `TopLevelDestination` list it feeds, are plain data. Call sites render them
 * with `Icon(painter = painterResource(RepFlowIcons.<name>), ...)`, which
 * tints the glyph from the surrounding `LocalContentColor`.
 */
object RepFlowIcons {
    /** Archive an exercise (Exercise list overflow). */
    @DrawableRes
    val archive: Int = R.drawable.ic_ph_archive

    /** Undo: the Exercise list's archive snackbar, and undo-last-set. */
    @DrawableRes
    val arrowCounterClockwise: Int = R.drawable.ic_ph_arrow_counter_clockwise

    /** Back navigation (Exercise list top app bar). */
    @DrawableRes
    val arrowLeft: Int = R.drawable.ic_ph_arrow_left

    /** Workout destination glyph - see [Nav.workout]. */
    @DrawableRes
    val barbell: Int = R.drawable.ic_ph_barbell

    /** Exercises destination glyph - see [Nav.exercises]. */
    @DrawableRes
    val books: Int = R.drawable.ic_ph_books

    /** Collapse the set-entry detail section (RPE/pain/technique). */
    @DrawableRes
    val caretDown: Int = R.drawable.ic_ph_caret_down

    /** Expand the set-entry detail section (RPE/pain/technique). */
    @DrawableRes
    val caretUp: Int = R.drawable.ic_ph_caret_up

    /** History destination glyph - see [Nav.history]. */
    @DrawableRes
    val clockCounterClockwise: Int = R.drawable.ic_ph_clock_counter_clockwise

    /** Backup destination glyph - see [Nav.backup]. */
    @DrawableRes
    val cloudArrowUp: Int = R.drawable.ic_ph_cloud_arrow_up

    /** Per-row overflow menu (Exercise list). */
    @DrawableRes
    val dotsThreeVertical: Int = R.drawable.ic_ph_dots_three_vertical

    /** Warm-up set toggle (Active Workout set entry). */
    @DrawableRes
    val fire: Int = R.drawable.ic_ph_fire

    /** Filter affordance (Exercise list). */
    @DrawableRes
    val funnel: Int = R.drawable.ic_ph_funnel

    /** Exercise-type note (Active Workout set entry). */
    @DrawableRes
    val info: Int = R.drawable.ic_ph_info

    /** Plans destination glyph - see [Nav.plans]. */
    @DrawableRes
    val listChecks: Int = R.drawable.ic_ph_list_checks

    /** Search affordance (Exercise list). */
    @DrawableRes
    val magnifyingGlass: Int = R.drawable.ic_ph_magnifying_glass

    /** Stepper decrement (weight/reps entry). */
    @DrawableRes
    val minus: Int = R.drawable.ic_ph_minus

    /** Recovery destination glyph - see [Nav.recovery]. */
    @DrawableRes
    val moonStars: Int = R.drawable.ic_ph_moon_stars

    /** Edit a logged set row (Active Workout). */
    @DrawableRes
    val pencilSimple: Int = R.drawable.ic_ph_pencil_simple

    /** Stepper increment (weight/reps entry). */
    @DrawableRes
    val plus: Int = R.drawable.ic_ph_plus

    /**
     * Exercise list floating action button. The design draws the FAB's plus at
     * Phosphor's bold weight and every other plus at regular weight, so both
     * weights are bundled rather than one being reused at the wrong stroke.
     */
    @DrawableRes
    val plusBold: Int = R.drawable.ic_ph_plus_bold

    /** Expand the set-entry detail panel (Active Workout). */
    @DrawableRes
    val sliders: Int = R.drawable.ic_ph_sliders

    /** Dismiss the rest-timer strip (Active Workout). */
    @DrawableRes
    val x: Int = R.drawable.ic_ph_x

    /** Clear the Exercise list search field. */
    @DrawableRes
    val xCircle: Int = R.drawable.ic_ph_x_circle

    /**
     * The glyph each of the six top-level destinations gets in the bottom
     * navigation bar, wired up in CP4. The mapping lives here, next to the
     * icon set it picks from, because picking it *is* this checkpoint's
     * decision - CP4 only consumes it.
     *
     * **Only [plans] and [history] are design-confirmed.** The target design
     * has a four-destination bar (Home/Plans/History/Progress); this milestone
     * keeps the app's existing six, so [exercises], [workout], [recovery] and
     * [backup] have no confirmed glyph and the picks below are reasoned
     * judgment calls, not design facts:
     *
     * - [exercises] `books` - the exercise library is a catalogue to browse,
     *   and it must not collide with [workout]'s more literal glyph.
     * - [workout] `barbell` - the one destination that is a training session.
     * - [recovery] `moon-stars` - the only Recovery glyph the design draws
     *   anywhere, though as an empty-state illustration rather than a nav tab.
     * - [backup] `cloud-arrow-up` - reads as export/save even though RepFlow's
     *   backup is entirely local.
     *
     * A later design pass may replace all four; nothing else depends on the
     * specific choice.
     */
    object Nav {
        /** Judgment call - see [Nav]. */
        @DrawableRes
        val exercises: Int = books

        /** Judgment call - see [Nav]. */
        @DrawableRes
        val workout: Int = barbell

        /** Design-confirmed (`ph-list-checks`). */
        @DrawableRes
        val plans: Int = listChecks

        /** Judgment call - see [Nav]. */
        @DrawableRes
        val recovery: Int = moonStars

        /** Design-confirmed (`ph-clock-counter-clockwise`). */
        @DrawableRes
        val history: Int = clockCounterClockwise

        /** Judgment call - see [Nav]. */
        @DrawableRes
        val backup: Int = cloudArrowUp
    }
}
