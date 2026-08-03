import json
from urllib.parse import parse_qs, urlparse

from app.extended_sync import ExtendedContentSync
from app.store import ContentStore


class FakeExtendedFetcher:
    base_url = "https://example.com"

    def fetch(self, url):
        path = urlparse(url).path
        query = parse_qs(urlparse(url).query)
        if path == "/api/table_of_events":
            return json.dumps({"data": {"event_list": ["三家分晋"]}})
        if path == "/api/content_list_of_event":
            assert query["event_name"] == ["三家分晋"]
            return json.dumps(
                {
                    "data": {
                        "children_contents": [
                            {
                                "brief": "晋国三卿受封",
                                "paragraph": {
                                    "tongjian_id": "event-content-1",
                                    "content_jianti_auto": "1初命晋大夫魏斯、赵籍、韩虔为诸侯。",
                                    "content_fanyi": "周威烈王二十三年，三家受封为诸侯。",
                                },
                            }
                        ]
                    }
                },
                ensure_ascii=False,
            )
        if path == "/api/table_of_comments":
            return json.dumps(
                {
                    "data": {
                        "juan_list": [
                            {
                                "juan_name": "卷一",
                                "emperor_list": [
                                    {
                                        "emperor_name": "周威烈王",
                                        "topic_list": [{"topic_name": "名分"}],
                                    }
                                ],
                            }
                        ]
                    }
                },
                ensure_ascii=False,
            )
        if path == "/api/content_list_of_comment":
            assert query["topic_tongjian_id"] == ["名分"]
            return json.dumps(
                {
                    "data": {
                        "paragraph_list": [
                            {
                                "tongjian_id": "comment-content-1",
                                "wenyan_clean": "2名分既正，天下乃定。",
                                "fanyi": "名分端正以后，天下才能安定。",
                            }
                        ]
                    }
                },
                ensure_ascii=False,
            )
        raise AssertionError(f"unexpected URL: {url}")


def test_extended_sync_imports_events_and_comments(tmp_path):
    database = tmp_path / "extended.db"
    progress = tmp_path / "progress.json"
    sync = ExtendedContentSync(
        FakeExtendedFetcher(),
        ContentStore(database),
        cache_dir=tmp_path / "cache",
        progress_path=progress,
    )

    sync.sync_events()
    sync.sync_comments()

    store = ContentStore(database)
    event = store.get_item("tongjian-jishi-001")
    comment = store.get_item("dutongjian-lun-001")
    assert event is not None and event.section == "纪事本末"
    assert event.original.startswith("初命晋")
    assert comment is not None and comment.section == "读通鉴论"
    assert comment.translation.startswith("名分端正")
    assert len(store.volumes("jishi")) == 1
    assert len(store.volumes("lun")) == 1
    saved = json.loads(progress.read_text(encoding="utf-8"))
    assert saved["events_completed"] == ["三家分晋"]
    assert saved["comments_completed"] == ["卷一|周威烈王|名分"]
    assert saved["errors"] == {}
