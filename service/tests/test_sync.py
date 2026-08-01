from app.store import ContentStore
from app.sync import PublicContentSync


class FakeFetcher:
    def __init__(self, base_url: str, pages: dict[str, str | None]):
        self.base_url = base_url
        self.pages = pages

    def fetch(self, path: str) -> str | None:
        return self.pages.get(path)


def test_public_sync_upserts_selected_reading_page(tmp_path):
    fetcher = FakeFetcher(
        "https://www.dutongjian.com",
        {
            "/read/1": """
                <article data-entry data-id="synced-1">
                  <h2>同步条目</h2>
                  <p data-original>原文</p>
                  <p data-translation>译文</p>
                </article>
            """,
        },
    )
    store = ContentStore(tmp_path / "sync.db")

    result = PublicContentSync(fetcher, store).sync_reading(
        "/read/1",
        volume_id="volume-1",
        year_id="year-1",
    )

    assert result == result.__class__(path="/read/1", records=1, fetched=True)
    item = store.get_item("synced-1")
    assert item is not None
    assert item.original == "原文"
    assert item.volume_id == "volume-1"


def test_public_sync_upserts_selected_knowledge_page(tmp_path):
    fetcher = FakeFetcher(
        "https://wiki.dutongjian.com",
        {
            "/wiki": """
                <article data-knowledge-entry data-id="synced-wiki" data-category="人物">
                  <h2>同步人物</h2>
                  <p class="summary">人物摘要</p>
                </article>
            """,
        },
    )
    store = ContentStore(tmp_path / "knowledge.db")

    result = PublicContentSync(fetcher, store).sync_knowledge("/wiki")

    assert result.records == 1
    entry = store.get_knowledge("synced-wiki")
    assert entry is not None
    assert entry.category == "人物"
