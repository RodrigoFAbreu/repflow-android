package com.repflow.app.data.exercise

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The escaping matrix approved as plan.md additional implementation
 * corrections 5 and 6: escape order is backslash, then `%`, then `_`, and an
 * empty query means "no filter" (D-20).
 */
class ExerciseSearchPatternTest {
    @Test
    fun `an empty query matches everything`() {
        assertEquals("%%", buildNameSearchPattern(""))
    }

    @Test
    fun `a plain query is wrapped in wildcards`() {
        assertEquals("%bench press%", buildNameSearchPattern("bench press"))
    }

    @Test
    fun `a percent in the query is escaped`() {
        assertEquals("%100\\% effort%", buildNameSearchPattern("100% effort"))
    }

    @Test
    fun `an underscore in the query is escaped`() {
        assertEquals("%leg\\_press%", buildNameSearchPattern("leg_press"))
    }

    @Test
    fun `a literal backslash in the query is escaped before percent and underscore`() {
        assertEquals("%a\\\\b\\%c\\_d%", buildNameSearchPattern("a\\b%c_d"))
    }

    @Test
    fun `backslash escaping happens first so an escaped percent is not double-escaped`() {
        // A literal backslash immediately followed by a percent: the backslash must be
        // escaped to \\ first, then the percent separately escaped to \%, rather than the
        // pre-existing "\%" being read as already-escaped and left alone.
        assertEquals("%a\\\\\\%b%", buildNameSearchPattern("a\\%b"))
    }
}
