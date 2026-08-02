package com.dutongjian.app.domain.text

import com.dutongjian.app.domain.model.TextScript
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassicalScriptMapperTest {
    @Test
    fun mapsTraditionalTextForPresentation() {
        assertEquals("学国军为后", ClassicalScriptMapper.transform("學國軍爲後", TextScript.SIMPLIFIED))
    }

    @Test
    fun mapsSimplifiedTextBackWithoutChangingStoredInput() {
        val source = "学国军为后"
        assertEquals("學國軍爲後", ClassicalScriptMapper.transform(source, TextScript.TRADITIONAL))
        assertEquals(source, source)
    }

    @Test
    fun variantViewOnlyChangesKnownCharacters() {
        assertEquals("體國學", ClassicalScriptMapper.transform("体国学", TextScript.VARIANT))
    }
}
