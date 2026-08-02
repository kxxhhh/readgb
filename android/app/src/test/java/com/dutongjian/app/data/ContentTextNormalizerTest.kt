package com.dutongjian.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentTextNormalizerTest {
    @Test
    fun removesEntryNumberOnlyWhenItMatchesTheTitle() {
        assertEquals("乌孙昆弥翁归靡因长罗侯常惠上书", normalizeLeadingEntryNumber("乌孙昆弥翁归靡因长罗侯常惠上书", "5乌孙昆弥翁归靡因长罗侯常惠上书"))
        assertEquals("12月无数字前缀", normalizeLeadingEntryNumber("另一个标题", "12月无数字前缀"))
        assertEquals("烏孫昆彌翁歸靡因長羅侯常惠上書", normalizeLeadingEntryNumber("乌孙昆弥翁归靡因长罗侯常惠上书", "5烏孫昆彌翁歸靡因長羅侯常惠上書", allowAlternateScript = true))
        assertEquals("5abc", normalizeLeadingEntryNumber("5abc", "5abc"))
    }
}
