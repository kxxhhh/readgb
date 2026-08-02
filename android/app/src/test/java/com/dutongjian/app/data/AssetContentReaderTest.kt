package com.dutongjian.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetContentReaderTest {
    @Test
    fun readsPlainNdjsonFromAndroidPackagedAsset() {
        val input = ByteArrayInputStream("{\"id\":\"plain\"}\n".toByteArray())

        contentAssetReader(input).use { reader ->
            assertEquals("{\"id\":\"plain\"}", reader.readLine())
        }
    }

    @Test
    fun readsGzipNdjsonFromUnmodifiedAsset() {
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { gzip ->
            gzip.write("{\"id\":\"gzip\"}\n".toByteArray())
        }

        contentAssetReader(ByteArrayInputStream(compressed.toByteArray())).use { reader ->
            assertEquals("{\"id\":\"gzip\"}", reader.readLine())
        }
    }
}
