package com.dutongjian.app.data

import java.io.BufferedReader
import java.io.InputStream
import java.util.zip.GZIPInputStream

internal fun contentAssetReader(input: InputStream): BufferedReader {
    val buffered = input.buffered()
    buffered.mark(GZIP_MAGIC.size)
    val header = ByteArray(GZIP_MAGIC.size)
    val read = buffered.read(header)
    buffered.reset()
    val decoded = if (read == GZIP_MAGIC.size && header.contentEquals(GZIP_MAGIC)) {
        GZIPInputStream(buffered)
    } else {
        buffered
    }
    return decoded.bufferedReader(Charsets.UTF_8)
}

private val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte())
