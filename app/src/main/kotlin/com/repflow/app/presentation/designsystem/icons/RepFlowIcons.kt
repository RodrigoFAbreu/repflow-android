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

    /**
     * Starting or resuming a workout: the Home placeholder's start affordance,
     * carried into Home's start card and the workout board. A nav tab glyph
     * until the four-destination bar retired the Workout tab.
     */
    @DrawableRes
    val barbell: Int = R.drawable.ic_ph_barbell

    /**
     * The exercise library: the Settings placeholder's `Library` row, carried
     * into the grouped Settings screen. A nav tab glyph until the
     * four-destination bar retired the Exercises tab.
     */
    @DrawableRes
    val books: Int = R.drawable.ic_ph_books

    /** Collapse the set-entry detail section (RPE/pain/technique). */
    @DrawableRes
    val caretDown: Int = R.drawable.ic_ph_caret_down

    /** Expand the set-entry detail section (RPE/pain/technique). */
    @DrawableRes
    val caretUp: Int = R.drawable.ic_ph_caret_up

    /**
     * `RepFlowListRow`'s trailing caret (`ph-caret-right`, drawn on every
     * navigating row in History, Settings and the sheets). Remediation-1 CP3.
     */
    @DrawableRes
    val caretRight: Int = R.drawable.ic_ph_caret_right

    /** Progress tab glyph, unselected - see [Nav.progress]. */
    @DrawableRes
    val chartLineUp: Int = R.drawable.ic_ph_chart_line_up

    /** Progress tab glyph, selected - see [Nav.progressSelected]. */
    @DrawableRes
    val chartLineUpFill: Int = R.drawable.ic_ph_chart_line_up_fill

    /** History tab glyph, unselected - see [Nav.history]. */
    @DrawableRes
    val clockCounterClockwise: Int = R.drawable.ic_ph_clock_counter_clockwise

    /** History tab glyph, selected - see [Nav.historySelected]. */
    @DrawableRes
    val clockCounterClockwiseFill: Int = R.drawable.ic_ph_clock_counter_clockwise_fill

    /**
     * Backup and restore: the Settings placeholder's `Backup` row, carried into
     * the grouped Settings screen's `Data` group. A nav tab glyph until the
     * four-destination bar retired the Backup tab.
     */
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

    /** Home tab glyph, unselected - see [Nav.home]. */
    @DrawableRes
    val house: Int = R.drawable.ic_ph_house

    /** Home tab glyph, selected - see [Nav.homeSelected]. */
    @DrawableRes
    val houseFill: Int = R.drawable.ic_ph_house_fill

    /** Exercise-type note (Active Workout set entry). */
    @DrawableRes
    val info: Int = R.drawable.ic_ph_info

    /** Plans tab glyph, unselected - see [Nav.plans]. */
    @DrawableRes
    val listChecks: Int = R.drawable.ic_ph_list_checks

    /** Plans tab glyph, selected - see [Nav.plansSelected]. */
    @DrawableRes
    val listChecksFill: Int = R.drawable.ic_ph_list_checks_fill

    /** Search affordance (Exercise list). */
    @DrawableRes
    val magnifyingGlass: Int = R.drawable.ic_ph_magnifying_glass

    /** Stepper decrement (weight/reps entry). */
    @DrawableRes
    val minus: Int = R.drawable.ic_ph_minus

    /**
     * Recovery: the Home placeholder's recovery `Log` row, carried into Home's
     * recovery card. A nav tab glyph until the four-destination bar retired
     * the Recovery tab.
     */
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
     * The regular/fill glyph pair each of the four top-level destinations gets
     * in the bottom navigation bar - the design's own bar (artboard `4a`,
     * `nTabs`): Home `house`, Plans `list-checks`, History
     * `clock-counter-clockwise`, Progress `chart-line-up`. The mapping lives
     * here, next to the icon set it picks from; `RepFlowDestinations` only
     * consumes it.
     *
     * **All four are design-confirmed**, and so is the treatment: the design
     * draws the selected tab at Phosphor's fill weight and every other tab at
     * regular weight (`'ph-fill '` vs `'ph '`). Each destination therefore
     * carries two glyphs - the unselected one under its plain name and the
     * selected one under `<name>Selected` - and the bar picks between them on
     * selection, on top of its colour change, so selection is never signalled
     * by colour alone.
     *
     * The six-destination bar this replaces also carried `books` (Exercises),
     * `barbell` (Workout), `moon-stars` (Recovery) and `cloud-arrow-up`
     * (Backup) as unconfirmed judgment calls. Those four destinations are no
     * longer tabs; their glyphs stay in the top-level set, worn by the
     * affordances that now lead to them (see each entry's own doc).
     */
    object Nav {
        /** Design-confirmed (`ph-house`). */
        @DrawableRes
        val home: Int = house

        /** Design-confirmed (`ph-fill ph-house`). */
        @DrawableRes
        val homeSelected: Int = houseFill

        /** Design-confirmed (`ph-list-checks`). */
        @DrawableRes
        val plans: Int = listChecks

        /** Design-confirmed (`ph-fill ph-list-checks`). */
        @DrawableRes
        val plansSelected: Int = listChecksFill

        /** Design-confirmed (`ph-clock-counter-clockwise`). */
        @DrawableRes
        val history: Int = clockCounterClockwise

        /** Design-confirmed (`ph-fill ph-clock-counter-clockwise`). */
        @DrawableRes
        val historySelected: Int = clockCounterClockwiseFill

        /** Design-confirmed (`ph-chart-line-up`). */
        @DrawableRes
        val progress: Int = chartLineUp

        /** Design-confirmed (`ph-fill ph-chart-line-up`). */
        @DrawableRes
        val progressSelected: Int = chartLineUpFill
    }
}
