from app.parsers import parse_knowledge_index, parse_main_catalog, parse_reading_entries


def test_main_catalog_uses_semantic_levels_and_absolute_urls():
    html = """
    <nav data-catalog>
      <a data-level="section" data-id="zizhi" href="/zizhi">资治通鉴</a>
      <a data-level="volume" href="/zizhi/juan-1">卷第一</a>
      <a data-level="year" data-era="公元前403年" href="/zizhi/year-1">威烈王二十三年</a>
    </nav>
    """

    nodes = parse_main_catalog(html, "https://www.dutongjian.com")

    assert [(node.title, node.level) for node in nodes] == [
        ("资治通鉴", "section"),
        ("卷第一", "volume"),
        ("威烈王二十三年", "year"),
    ]
    assert nodes[-1].source_url == "https://www.dutongjian.com/zizhi/year-1"
    assert nodes[-1].metadata == "公元前403年"


def test_reading_parser_maps_parallel_content_and_provenance():
    html = """
    <article data-entry data-id="reading-1" data-dynasty="周纪一" data-updated-at="2026-08-01">
      <a href="/read/reading-1"><h2>三家分晋</h2></a>
      <p data-summary>战国开端的重要事件。</p>
      <p data-original>初命晋大夫魏斯、赵籍、韩虔为诸侯。</p>
      <p data-translation>周威烈王二十三年，三家受封为诸侯。</p>
      <p data-notes>名分与礼制的注释。</p>
      <div class="tags"><a>名分</a><a>战国</a></div>
    </article>
    """

    items = parse_reading_entries(
        html,
        "https://www.dutongjian.com",
        section="资治通鉴",
        volume_id="volume-1",
        year_id="year-1",
    )

    assert len(items) == 1
    item = items[0]
    assert item.id == "reading-1"
    assert item.source_url == "https://www.dutongjian.com/read/reading-1"
    assert item.original.startswith("初命晋")
    assert item.translation.startswith("周威烈王")
    assert item.notes == "名分与礼制的注释。"
    assert item.tags == ("名分", "战国")
    assert item.volume_id == "volume-1"
    assert item.year_id == "year-1"


def test_knowledge_parser_maps_category_and_summary():
    html = """
    <section>
      <article data-knowledge-entry data-id="wiki-1" data-category="人物">
        <a href="/wiki/zhaowuxu"><h3>赵无恤</h3></a>
        <p class="summary">赵氏继承中的重要人物。</p>
        <p class="content">赵无恤即赵襄子，相关故事涉及识人和继承。</p>
      </article>
    </section>
    """

    entries = parse_knowledge_index(html, "https://wiki.dutongjian.com")

    assert len(entries) == 1
    assert entries[0].id == "wiki-1"
    assert entries[0].category == "人物"
    assert entries[0].source_url == "https://wiki.dutongjian.com/wiki/zhaowuxu"
    assert entries[0].content.startswith("赵无恤即赵襄子")
