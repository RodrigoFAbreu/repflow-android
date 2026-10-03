package com.repflow.app.presentation.designsystem.icons

import com.repflow.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Pins the icon catalogue against the drawables it exposes.
 *
 * Every entry in [RepFlowIcons] compiles whichever drawable it names, so the
 * failure this checkpoint can actually ship is a *mis*-pointed entry - a
 * copy-paste that leaves `caretUp` and `caretDown` on the same glyph, or a
 * bundled drawable that no name ever reaches. Both are invisible to the
 * compiler and to a screenshot of one screen; they are exactly what the two
 * set comparisons below catch.
 */
class RepFlowIconsTest {
    private fun catalogue(): Map<String, Int> =
        RepFlowIcons::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Int::class.java }
            .associate { it.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar) to it.invoke(RepFlowIcons) as Int }

    private fun bundledPhosphorDrawables(): Map<String, Int> =
        R.drawable::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.java && it.name.startsWith("ic_ph_") }
            .associate { it.name to it.getInt(null) }

    @Test
    fun everyGlyphNameResolvesToADistinctDrawable() {
        val byId = catalogue().entries.groupBy({ it.value }, { it.key })
        val aliased = byId.values.filter { it.size > 1 }
        assertTrue("Glyph names sharing one drawable: $aliased", aliased.isEmpty())
    }

    @Test
    fun catalogueCoversExactlyTheBundledIconSet() {
        assertEquals(bundledPhosphorDrawables().values.toSet(), catalogue().values.toSet())
    }

    @Test
    fun bundledIconSetIsTheGlyphSetThePlanEnumerates() {
        assertEquals(EXPECTED_GLYPHS, bundledPhosphorDrawables().keys)
    }

    /**
     * The four-tab bar carries a regular/fill pair per destination: each pair
     * must be two different glyphs (or selection would fall back to colour
     * alone), and no glyph may be shared between destinations in either state.
     */
    @Test
    fun everyTopLevelDestinationGetsItsOwnGlyphPair() {
        val pairs =
            listOf(
                RepFlowIcons.Nav.home to RepFlowIcons.Nav.homeSelected,
                RepFlowIcons.Nav.plans to RepFlowIcons.Nav.plansSelected,
                RepFlowIcons.Nav.history to RepFlowIcons.Nav.historySelected,
                RepFlowIcons.Nav.progress to RepFlowIcons.Nav.progressSelected,
            )
        pairs.forEach { (regular, fill) -> assertNotEquals("A destination's two glyphs are the same", regular, fill) }
        val all = pairs.flatMap { listOf(it.first, it.second) }
        assertEquals("Two destinations share a nav glyph", all.size, all.toSet().size)
        assertTrue(catalogue().values.toSet().containsAll(all))
    }

    /** All four tabs are the design's own (`4a`'s `nTabs`): regular weight unselected, fill weight selected. */
    @Test
    fun designConfirmedNavGlyphsAreTheOnesTheDesignNames() {
        assertEquals(RepFlowIcons.house, RepFlowIcons.Nav.home)
        assertEquals(RepFlowIcons.houseFill, RepFlowIcons.Nav.homeSelected)
        assertEquals(RepFlowIcons.listChecks, RepFlowIcons.Nav.plans)
        assertEquals(RepFlowIcons.listChecksFill, RepFlowIcons.Nav.plansSelected)
        assertEquals(RepFlowIcons.clockCounterClockwise, RepFlowIcons.Nav.history)
        assertEquals(RepFlowIcons.clockCounterClockwiseFill, RepFlowIcons.Nav.historySelected)
        assertEquals(RepFlowIcons.chartLineUp, RepFlowIcons.Nav.progress)
        assertEquals(RepFlowIcons.chartLineUpFill, RepFlowIcons.Nav.progressSelected)
    }

    /** The glyphs each milestone surface draws, grouped by the checkpoint that added them. */
    private companion object {
        val EXPECTED_GLYPHS: Set<String> =
            setOf(
                // Exercise list (CP5).
                "ic_ph_arrow_left",
                "ic_ph_magnifying_glass",
                "ic_ph_x_circle",
                "ic_ph_funnel",
                "ic_ph_dots_three_vertical",
                "ic_ph_archive",
                "ic_ph_plus_bold",
                "ic_ph_arrow_counter_clockwise",
                // Active Workout set entry + rest timer (CP6).
                "ic_ph_minus",
                "ic_ph_plus",
                "ic_ph_fire",
                "ic_ph_pencil_simple",
                "ic_ph_sliders",
                "ic_ph_caret_down",
                "ic_ph_caret_up",
                "ic_ph_info",
                "ic_ph_x",
                // Bottom navigation (parent CP4): the two design-confirmed tab
                // glyphs at regular weight, plus the four the six-tab bar used as
                // judgment calls, which remediation-1 CP2 keeps as the glyphs of the
                // affordances that now lead to those destinations. `cloud-arrow-up`
                // (Backup) went with the Backup screen when remediation-1 CP14 moved
                // its actions into Settings' Data group, which wears `4a`'s glyphs.
                "ic_ph_list_checks",
                "ic_ph_clock_counter_clockwise",
                "ic_ph_books",
                "ic_ph_barbell",
                "ic_ph_moon_stars",
                // Four-tab bar (remediation-1 CP2): exactly six additions - the two
                // tabs the six-tab bar never had (Home, Progress) at both weights,
                // and the fill weight of the two tabs it keeps (Plans, History).
                "ic_ph_house",
                "ic_ph_house_fill",
                "ic_ph_chart_line_up",
                "ic_ph_chart_line_up_fill",
                "ic_ph_list_checks_fill",
                "ic_ph_clock_counter_clockwise_fill",
                // Structural primitives (remediation-1 CP3): RepFlowListRow's
                // trailing caret.
                "ic_ph_caret_right",
                // Home (remediation-1 CP5): the header gear, the start card's label
                // and `Start workout`, the resume card's running marker and abandon,
                // the start sheet's `Empty workout` row, and `1d`'s first-run and
                // history-error cards.
                "ic_ph_gear_six",
                "ic_ph_calendar_check",
                "ic_ph_play_fill",
                "ic_ph_record_fill",
                "ic_ph_trash",
                "ic_ph_lightning",
                "ic_ph_list_plus",
                "ic_ph_warning_circle",
                // Progression recommendation (remediation-1 CP6): one outcome glyph per
                // `ProgressionResult` (`6a`/`6c`), the reason-row glyphs, and the
                // override record card.
                "ic_ph_trend_up",
                "ic_ph_trend_down",
                "ic_ph_arrow_right",
                "ic_ph_heartbeat",
                "ic_ph_hourglass_medium",
                "ic_ph_check_circle",
                "ic_ph_arrow_down",
                "ic_ph_user_circle",
                // Workout board (remediation-1 CP7): the status chip's three glyphs
                // (`6b`), the elapsed clock, and the picker's create action.
                "ic_ph_check_fat",
                "ic_ph_dot_outline",
                "ic_ph_circle",
                "ic_ph_timer",
                "ic_ph_plus_circle",
                // Workout focus mode (remediation-1 CP8): `Board` and the suggestion strip.
                "ic_ph_list_bullets",
                "ic_ph_pulse",
                // Done screen (remediation-1 CP9): the best-set card.
                "ic_ph_medal_fill",
                // History (remediation-1 CP12): the `invalidated` badge and the
                // invalidate action, and the date filter chips.
                "ic_ph_prohibit",
                "ic_ph_calendar_blank",
                // Recovery (remediation-1 CP13): the futsal mark on the entry's load
                // line and across the recovery history.
                "ic_ph_soccer_ball",
                // Settings (remediation-1 CP14): the Data group's export and CSV rows, the
                // export row's `Saved`, and the `Irreversible` card.
                "ic_ph_database",
                "ic_ph_table",
                "ic_ph_check",
                "ic_ph_warning",
                // Backup screen (remediation-1-remediation-1 CP8, `8c`): the hero's two
                // shields, `Export backup now` and `Restore from a backup`.
                "ic_ph_shield_check_fill",
                "ic_ph_shield_warning",
                "ic_ph_download_simple",
                "ic_ph_upload_simple",
            )
    }
}
