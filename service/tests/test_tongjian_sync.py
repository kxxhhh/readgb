import json

from app.store import ContentStore
from app.tongjian_sync import TongjianSync


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
                    "ExtRef_Children_people": [{"people_name_jianti_auto": "魏斯"}],
                    "ExtRef_Children_places": [{"place_name_jianti_auto": "晋"}],
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
    item = store.get_item("zztj-content-1")
    assert item is not None
    assert item.original.startswith("1初命晉大夫")
    assert item.content.startswith("1初命晋大夫")
    assert item.translation.startswith("周威烈王姬午")
    notes = json.loads(item.notes)
    assert notes["note_content"] == "此溫公書法所由始也。"
    assert "魏斯" in item.tags
    assert "三家分晋" in item.tags


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
