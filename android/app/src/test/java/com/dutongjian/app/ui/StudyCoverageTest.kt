package com.dutongjian.app.ui

import com.dutongjian.app.domain.model.ReadingItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyCoverageTest {
    @Test
    fun coverageCountsOnlyImportedTongjianItemsAndDistinctHierarchy() {
        val items = listOf(
            ReadingItem("zztj-1", "一", "资治通鉴", "周纪", "", "正文", "", "", volumeId = "v1", yearId = "y1"),
            ReadingItem("zztj-2", "二", "资治通鉴", "周纪", "", "正文", "", "", volumeId = "v1", yearId = "y1"),
            ReadingItem("zztj-3", "三", "资治通鉴", "周纪", "", "正文", "", "", volumeId = "v2", yearId = "y2"),
            ReadingItem("zizhi-tongjian-001", "种子", "资治通鉴", "周纪", "", "正文", "", "", volumeId = "seed", yearId = "seed-year"),
            ReadingItem("other-1", "其他", "通鉴纪事本末", "战国", "", "正文", "", "", volumeId = "other", yearId = "other-year"),
        )

        assertEquals(StudyCoverage(items = 3, years = 2, volumes = 2), studyCoverage(items))
    }
}
