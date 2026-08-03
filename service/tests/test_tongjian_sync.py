import json
import threading
import time
from email.message import Message
from urllib.error import HTTPError

import pytest

from app.store import ContentStore
from app.tongjian_sync import ReignRef, TongjianApiClient, TongjianSync, parse_reign_items
from app.models import Item


def _catalog():
    return {
        "data": {
            "book_name": "资治通鉴",
            "juan_list": [
                {
                    "tongjian_id": "juan-1",
                    "juan": "資治通鑑卷第一",
                    "ji": "周紀一",
                    "emperor_list": [
                        {
                            "emperor_name": "威烈王",
                            "reign_list": [
                                {
                                    "tongjian_id": "reign-1",
                                    "reign_name": "二十三年",
                                    "year_anno": "（戊寅、前四○三）",
                                    "year_int": -403,
                                }
                            ],
                        }
                    ],
                }
            ],
        }
    }


def _reign():
    return {
        "data": {
            "reign_name": "二十三年",
            "year_anno": "（戊寅、前四○三）",
            "year_int": -403,
            "ExtRef_Children_contents": [
                {
                    "id": 1,
                    "number": 1,
                    "tongjian_id": "content-1",
                    "content": "1初命晉大夫魏斯、趙籍、韓虔爲諸侯。",
                    "content_jianti_auto": "1初命晋大夫魏斯、赵籍、韩虔为诸侯。",
                    "content_fanyi": "周威烈王姬午初次任命晋国大夫魏斯、赵籍、韩虔为诸侯国的国君。",
                    "class_tags": ["人事"],
                    "note_content": "此溫公書法所由始也。",
                    "note_content_jianti_auto": "此温公书法所由始也。",
                    "ExtRef_Children_topics": [{"title": "三家分晋"}],
                    "ExtRef_Children_people": [{"people_name_jianti_auto": "魏斯", "start_index": 7}],
                    "ExtRef_Children_places": [{"place_name_jianti_auto": "晋"}],
                    "ExtRef_Children_hu_notes": [{"start_index": 8, "note_content_jianti_auto": "三家分晋之始。"}],
                }
            ],
        }
    }


class FakeApi:
    def __init__(self):
        self.reign_calls = 0

    def fetch_catalog(self):
        return _catalog()

    def fetch_reign(self, reign_id):
        assert reign_id == "reign-1"
        self.reign_calls += 1
        return _reign()


def test_sync_imports_catalog_and_preserves_full_public_payload(tmp_path):
    store = ContentStore(tmp_path / "tongjian.db")
    api = FakeApi()

    result = TongjianSync(api, store, checkpoint_path=tmp_path / "progress.json").run()

    assert result.total_reigns == 1
    assert result.completed_reigns == 1
    assert result.content_records == 1
    assert store.volumes("zizhi")[0].title == "資治通鑑卷第一"
    assert store.years("juan-1")[0].title == "威烈王二十三年"
    assert store.years("juan-1")[0].year_int == -403
    item = store.get_item("zztj-content-1")
    assert item is not None
    assert item.sort_order == 1
    assert item.original == "初命晉大夫魏斯、趙籍、韓虔爲諸侯。"
    assert item.content == "初命晋大夫魏斯、赵籍、韩虔为诸侯。"
    assert item.translation.startswith("周威烈王姬午")
    notes = json.loads(item.notes)
    assert notes["note_content"] == "此溫公書法所由始也。"
    assert notes["ExtRef_Children_people"][0]["start_index"] == 6
    assert notes["ExtRef_Children_hu_notes"][0]["start_index"] == 7
    assert "魏斯" in item.tags
    assert "三家分晋" in item.tags


def test_sync_progress_counts_real_records_before_seed_cleanup(tmp_path):
    progress = []

    TongjianSync(
        FakeApi(),
        ContentStore(tmp_path / "tongjian.db"),
        checkpoint_path=tmp_path / "progress.json",
        on_progress=lambda current, _ref: progress.append(current),
    ).run()

    assert progress[-1].content_records == 1


def test_sync_rejects_empty_reign_and_keeps_it_out_of_checkpoint(tmp_path):
    class EmptyApi(FakeApi):
        def fetch_reign(self, reign_id):
            payload = _reign()
            payload["data"]["ExtRef_Children_contents"] = []
            return payload

    checkpoint = tmp_path / "progress.json"
    with pytest.raises(RuntimeError, match="reign-1"):
        TongjianSync(EmptyApi(), ContentStore(tmp_path / "tongjian.db"), checkpoint_path=checkpoint).run()

    saved = json.loads(checkpoint.read_text(encoding="utf-8"))
    assert saved["completed_reign_ids"] == []
    assert saved["failed_reign_ids"] == ["reign-1"]


def test_parse_reign_rejects_duplicate_content_ids():
    ref = ReignRef("juan-1", "卷一", "周纪一", 1, "威烈王", "reign-1", "二十三年", "前403", -403, 1)
    payload = _reign()
    payload["data"]["ExtRef_Children_contents"].append(payload["data"]["ExtRef_Children_contents"][0])

    with pytest.raises(ValueError, match="duplicate content id"):
        parse_reign_items(payload, ref, "https://example.com/api/reign")


def test_sync_resume_skips_completed_reigns(tmp_path):
    store = ContentStore(tmp_path / "tongjian.db")
    api = FakeApi()
    checkpoint = tmp_path / "progress.json"
    sync = TongjianSync(api, store, checkpoint_path=checkpoint)

    sync.run()
    result = sync.run()

    assert result.completed_reigns == 1
    assert api.reign_calls == 1
    assert json.loads(checkpoint.read_text(encoding="utf-8"))["completed_reign_ids"] == ["reign-1"]


def test_sync_uses_bounded_workers_and_preserves_completed_results_on_failure(tmp_path):
    catalog = _catalog()
    reigns = catalog["data"]["juan_list"][0]["emperor_list"][0]["reign_list"]
    reigns.extend(
        [
            {"tongjian_id": "reign-2", "reign_name": "二十四年", "year_anno": "前四○二", "year_int": -402},
            {"tongjian_id": "reign-3", "reign_name": "二十五年", "year_anno": "前四○一", "year_int": -401},
        ]
    )

    class ConcurrentApi:
        def __init__(self):
            self.active = 0
            self.max_active = 0
            self.lock = threading.Lock()

        def fetch_catalog(self):
            return catalog

        def fetch_reign(self, reign_id):
            with self.lock:
                self.active += 1
                self.max_active = max(self.max_active, self.active)
            try:
                time.sleep(0.03)
                if reign_id == "reign-2":
                    raise ValueError("malformed test reign")
                payload = _reign()
                payload["data"]["ExtRef_Children_contents"][0]["tongjian_id"] = f"content-{reign_id}"
                return payload
            finally:
                with self.lock:
                    self.active -= 1

    api = ConcurrentApi()
    store = ContentStore(tmp_path / "tongjian.db")
    checkpoint = tmp_path / "progress.json"

    with pytest.raises(RuntimeError, match="reign-2"):
        TongjianSync(api, store, checkpoint_path=checkpoint, workers=3).run()

    saved = json.loads(checkpoint.read_text(encoding="utf-8"))
    assert api.max_active >= 2
    assert len(saved["completed_reign_ids"]) == 2
    assert saved["failed_reign_ids"] == ["reign-2"]
    assert sum(item.id.startswith("zztj-") for item in store.list_items(category="资治通鉴", limit=20)) == 2


def test_api_client_respects_retry_after_for_rate_limit(tmp_path):
    waits = []
    headers = Message()
    headers["Retry-After"] = "17"

    def opener(_request, timeout):
        raise HTTPError("https://example.com/api", 429, "too many requests", headers, None)

    client = TongjianApiClient(
        "https://example.com",
        cache_dir=tmp_path / "cache",
        opener=opener,
        sleep=waits.append,
        retries=0,
    )

    with pytest.raises(RuntimeError):
        client.fetch_catalog()

    assert 17.0 in waits


def test_sync_rejects_catalog_size_mismatch(tmp_path):
    with pytest.raises(RuntimeError, match="expected 2 reigns"):
        TongjianSync(
            FakeApi(),
            ContentStore(tmp_path / "tongjian.db"),
            checkpoint_path=tmp_path / "progress.json",
            expected_reigns=2,
        ).run()


def test_year_item_listing_uses_source_order(tmp_path):
    store = ContentStore(tmp_path / "ordered.db")
    store.upsert_items(
        [
            Item("zztj-late", "后", "资治通鉴", "周纪", "", "后", "", "", year_id="year-1", original="后", sort_order=2),
            Item("zztj-first", "前", "资治通鉴", "周纪", "", "前", "", "", year_id="year-1", original="前", sort_order=1),
        ]
    )

    assert [item.id for item in store.list_items(year_id="year-1", limit=10)] == ["zztj-first", "zztj-late"]
