package com.repflow.app.presentation.home

import com.repflow.app.presentation.designsystem.RepFlowDarkColorScheme
import com.repflow.app.presentation.designsystem.RepFlowDarkExtraColors
import com.repflow.app.presentation.designsystem.RepFlowLightColorScheme
import com.repflow.app.presentation.designsystem.RepFlowLightExtraColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.pow

/** Home's plain-value formatting and the start card's own ground (remediation-1 CP5). */
class HomeFormattingTest {
    @Test
    fun elapsedIsMinutesAndSecondsUntilAnHourThenGainsHours() {
        assertEquals("0:00", elapsedLabel(0))
        assertEquals("4:05", elapsedLabel(245))
        assertEquals("59:59", elapsedLabel(3_599))
        assertEquals("1:01:00", elapsedLabel(3_660))
        assertEquals("0:00", elapsedLabel(-30))
    }

    @Test
    fun durationRoundsToTheNearestMinute() {
        val start = Instant.parse("2026-08-10T10:00:00Z")
        assertEquals(61L, durationMinutes(start, start.plusSeconds(61 * 60 + 29)))
        assertEquals(62L, durationMinutes(start, start.plusSeconds(61 * 60 + 30)))
    }

    @Test
    fun theWorkoutDayIsTodayYesterdayAWeekdayOrADate() {
        val today = LocalDate.parse("2026-08-11") // a Tuesday

        fun on(date: String) = workoutDayOf(Instant.parse("${date}T18:00:00Z"), today, ZoneOffset.UTC)

        assertEquals(WorkoutDay.Today, on("2026-08-11"))
        assertEquals(WorkoutDay.Yesterday, on("2026-08-10"))
        assertEquals(WorkoutDay.Weekday(DayOfWeek.WEDNESDAY), on("2026-08-05"))
        assertEquals(WorkoutDay.OnDate(LocalDate.parse("2026-08-04")), on("2026-08-04"))
    }

    /** `4a` draws the start card's label and meta on a gradient from `#262a60`; both must stay readable on it. */
    @Test
    fun theStartCardsTextClearsTheFloorOnItsOwnGround() {
        val dark = homeStartCardColors(RepFlowDarkColorScheme, RepFlowDarkExtraColors.hairline)
        val light = homeStartCardColors(RepFlowLightColorScheme, RepFlowLightExtraColors.hairline)
        listOf(dark to RepFlowDarkColorScheme, light to RepFlowLightColorScheme).forEach { (colors, scheme) ->
            listOf(colors.fillTop, colors.fillBottom).forEach { ground ->
                assertTrue("label on $ground", contrast(colors.label, ground) >= TEXT_FLOOR)
                assertTrue("title on $ground", contrast(scheme.onSurface, ground) >= TEXT_FLOOR)
            }
        }
        assertEquals(RepFlowLightColorScheme.surface, light.fillTop)
    }

    private fun contrast(
        a: androidx.compose.ui.graphics.Color,
        b: androidx.compose.ui.graphics.Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(c: androidx.compose.ui.graphics.Color): Double {
        fun channel(v: Float): Double = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private companion object {
        const val TEXT_FLOOR = 4.5
    }
}
