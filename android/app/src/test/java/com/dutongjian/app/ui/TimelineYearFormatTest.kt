package com.dutongjian.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineYearFormatTest {
    @Test
    fun formatsCalibratedYearsForReadingTimeline() {
        assertEquals("公元前403年", formatPublicYear(-403, "前四○三"))
        assertEquals("公元618年", formatPublicYear(618, "六一八"))
        assertEquals("未知", formatPublicYear(null, "未知"))
    }
}
