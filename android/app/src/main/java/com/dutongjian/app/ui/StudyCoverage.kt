package com.dutongjian.app.ui

import com.dutongjian.app.domain.model.ReadingItem

internal data class StudyCoverage(
    val items: Int,
    val years: Int,
    val volumes: Int,
)

internal fun studyCoverage(items: List<ReadingItem>): StudyCoverage {
    val imported = items.filter { it.id.startsWith("zztj-") && it.category == "资治通鉴" }
    return StudyCoverage(
        items = imported.size,
        years = imported.mapNotNull { it.yearId?.takeIf(String::isNotBlank) }.distinct().size,
        volumes = imported.mapNotNull { it.volumeId?.takeIf(String::isNotBlank) }.distinct().size,
    )
}

internal const val EXPECTED_TONGJIAN_ITEMS = 30_989
internal const val EXPECTED_TONGJIAN_YEARS = 1_405
internal const val EXPECTED_TONGJIAN_VOLUMES = 294
