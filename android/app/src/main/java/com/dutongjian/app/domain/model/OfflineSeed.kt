package com.dutongjian.app.domain.model

object OfflineSeed {
    val items = listOf(
        ReadingItem(
            id = "zizhi-tongjian-001",
            title = "三家分晋",
            category = "资治通鉴",
            dynasty = "周纪",
            summary = "周威烈王二十三年，晋国大夫魏斯、赵籍、韩虔受封为诸侯。",
            content = "臣光曰：天子的职责没有比尊崇礼制更重要的，尊崇礼制没有比匡正名分更重要的。",
            sourceUrl = "https://www.dutongjian.com/",
            updatedAt = "2026-01-01T00:00:00Z",
            volumeId = "zizhi-volume-001",
            yearId = "zizhi-year-001",
            original = "初命晋大夫魏斯、赵籍、韩虔为诸侯。",
            translation = "周威烈王二十三年，初次任命晋国大夫魏斯、赵籍、韩虔为诸侯。",
            notes = "胡三省注和司马光史论可在阅读器中展开。",
            tags = listOf("名分", "三家分晋"),
        ),
        ReadingItem(
            id = "zizhi-tongjian-002",
            title = "周威烈王二十三年",
            category = "资治通鉴",
            dynasty = "周纪一",
            summary = "卷第一，记录战国开端的重要政局变化。",
            content = "周威烈王二十三年（公元前403年），晋国三家分立，历史进入新的阶段。",
            sourceUrl = "https://www.dutongjian.com/",
            updatedAt = "2026-01-01T00:00:00Z",
            volumeId = "zizhi-volume-001",
            yearId = "zizhi-year-001",
            original = "周威烈王二十三年。",
            translation = "公元前403年，战国历史由此展开。",
            tags = listOf("周纪", "战国"),
        ),
        ReadingItem(
            id = "zizhi-tongjian-003",
            title = "智瑶与智氏",
            category = "资治通鉴",
            dynasty = "周纪一",
            summary = "从继承人选择观察才与德的关系。",
            content = "智果认为智瑶有五项长处，却有不仁这一项短处，不能只以才能判断继承人。",
            sourceUrl = "https://www.dutongjian.com/",
            updatedAt = "2026-01-01T00:00:00Z",
            volumeId = "zizhi-volume-001",
            yearId = "zizhi-year-002",
            original = "智果曰：赵孟之良，赵氏之利也。",
            translation = "智果认为，观察继承人不能只看才能，也要看德行。",
            tags = listOf("人物", "继承"),
        ),
        ReadingItem(
            id = "tongjian-jishi-001",
            title = "三家分晋纪事本末",
            category = "通鉴纪事本末",
            dynasty = "战国",
            summary = "以事件为纲，梳理三家分晋的前因后果。",
            content = "本条目按事件组织相关史料，便于从时间线理解历史决策。",
            sourceUrl = "https://www.dutongjian.com/",
            updatedAt = "2026-01-01T00:00:00Z",
            section = "纪事本末",
            volumeId = "jishi-volume-001",
            yearId = "jishi-year-001",
            original = "三家分晋纪事本末。",
            translation = "按事件时间线梳理三家分晋的前因后果。",
            tags = listOf("事件", "时间线"),
        ),
        ReadingItem(
            id = "dutongjian-lun-001",
            title = "读通鉴论：名分与治道",
            category = "读通鉴论",
            dynasty = "史论",
            summary = "从史论角度阅读名分、制度与政局演变。",
            content = "读通鉴论以通鉴所载史实评论历代成败兴亡，适合与原文对照阅读。",
            sourceUrl = "https://www.dutongjian.com/",
            updatedAt = "2026-01-01T00:00:00Z",
            section = "读通鉴论",
            volumeId = "lun-volume-001",
            yearId = "lun-year-001",
            original = "名分与治道。",
            translation = "从史论角度阅读制度与政局演变。",
            tags = listOf("史论", "治道"),
        ),
    )

    val categories = items.map(ReadingItem::category).distinct().sorted()

    val sections = listOf(
        LibrarySection("zizhi", "资治通鉴", "司马光编年体史书，按卷、纪、年阅读。", "https://www.dutongjian.com/", 1),
        LibrarySection("jishi", "纪事本末", "以事件为纲，重建历史因果与时间线。", "https://www.dutongjian.com/", 2),
        LibrarySection("lun", "读通鉴论", "以通鉴史实为基础的历代成败评论。", "https://www.dutongjian.com/", 3),
        LibrarySection("wiki", "通鉴百科", "人物、战争、地点、政权与典故知识库。", "https://wiki.dutongjian.com/", 4),
    )

    val volumes = listOf(
        Volume("zizhi-volume-001", "zizhi", "卷第一（周纪一）", "周纪", 1),
        Volume("zizhi-volume-002", "zizhi", "卷第二（周纪二）", "周纪", 2),
        Volume("jishi-volume-001", "jishi", "三家分晋", "战国", 1),
        Volume("lun-volume-001", "lun", "读通鉴论卷一", "史论", 1),
    )

    val years = listOf(
        ReadingYear("zizhi-year-001", "zizhi-volume-001", "威烈王二十三年", "公元前403年", 1),
        ReadingYear("zizhi-year-002", "zizhi-volume-001", "威烈王二十四年", "公元前402年", 2),
        ReadingYear("jishi-year-001", "jishi-volume-001", "三家分晋纪事", "战国初年", 1),
        ReadingYear("lun-year-001", "lun-volume-001", "名分与治道", "史论", 1),
    )

    val knowledge = listOf(
        KnowledgeEntry("wiki-three-jin", "三家分晋", "事件", "魏、赵、韩受封为诸侯，标志战国时代展开。", "三家分晋是资治通鉴开篇的重要历史事件，也常作为理解名分、制度和权力转移的入口。", "https://wiki.dutongjian.com/三家分晋", "2026-01-01T00:00:00Z"),
        KnowledgeEntry("wiki-zhaowuxu", "赵无恤", "人物", "赵氏继承与战国政治中的重要人物。", "赵无恤即赵襄子，相关故事涉及识人、继承与代地经营，可从资治通鉴卷一交叉阅读。", "https://wiki.dutongjian.com/趙無恤", "2026-01-01T00:00:00Z"),
        KnowledgeEntry("wiki-xuanwumen", "玄武门之变", "事件", "唐初宫廷政变与太宗即位。", "玄武门之变可与资治通鉴唐纪和读通鉴论相关评论关联阅读。", "https://wiki.dutongjian.com/玄武门之变", "2026-01-01T00:00:00Z"),
        KnowledgeEntry("wiki-song", "宋", "政权", "宋代政治、史学与文化知识条目。", "宋代史学发达，资治通鉴的编修也发生在这一历史背景中。", "https://wiki.dutongjian.com/宋", "2026-01-01T00:00:00Z"),
    )
}
