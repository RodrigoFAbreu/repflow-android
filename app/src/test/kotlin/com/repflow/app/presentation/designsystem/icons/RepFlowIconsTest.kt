package com.repflow.app.presentation.designsystem.icons

import com.repflow.app.R
import org.junit.Assert.assertEquals
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
        val expected =
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
                // Bottom navigation (CP4).
                "ic_ph_list_checks",
                "ic_ph_clock_counter_clockwise",
                "ic_ph_books",
                "ic_ph_barbell",
                "ic_ph_moon_stars",
                "ic_ph_cloud_arrow_up",
            )
        assertEquals(expected, bundledPhosphorDrawables().keys)
    }

    @Test
    fun everyTopLevelDestinationGetsItsOwnGlyph() {
        val nav =
            listOf(
                RepFlowIcons.Nav.exercises,
                RepFlowIcons.Nav.workout,
                RepFlowIcons.Nav.plans,
                RepFlowIcons.Nav.recovery,
                RepFlowIcons.Nav.history,
                RepFlowIcons.Nav.backup,
            )
        assertEquals("Two destinations share a nav glyph", nav.size, nav.toSet().size)
        assertTrue(catalogue().values.toSet().containsAll(nav))
    }

    @Test
    fun designConfirmedNavGlyphsAreTheOnesTheDesignNames() {
        assertEquals(RepFlowIcons.listChecks, RepFlowIcons.Nav.plans)
        assertEquals(RepFlowIcons.clockCounterClockwise, RepFlowIcons.Nav.history)
    }
}
