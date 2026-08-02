import gzip
import json

import pytest

from app.export_android import export_catalog, export_content
from app.models import Item
from app.store import ContentStore


def test_export_android_requires_verified_full_count_and_writes_ndjson(tmp_path):
    database = tmp_path / "content.db"
    store = ContentStore(database)
    store.upsert_items(
        [
            Item(
                id="zztj-content-1",
                title="正文",
                category="资治通鉴",
                dynasty="周紀一",
                summary="摘要",
                content="正文",
                source_url="https://www.dutongjian.com/api/reign",
                updated_at="2026-08-02",
                volume_id="juan-1",
                year_id="reign-1",
                original="原文",
                translation="译文",
            )
        ]
    )

    output = tmp_path / "offline_content.ndjson.gz"
    assert export_content(database, output, expected_count=1) == 1

    with gzip.open(output, "rt", encoding="utf-8") as stream:
        assert json.loads(stream.readline())["id"] == "zztj-content-1"


def test_export_android_rejects_incomplete_item_without_replacing_output(tmp_path):
    database = tmp_path / "incomplete.db"
    store = ContentStore(database)
    store.upsert_items(
        [
            Item(
                id="zztj-content-1",
                title="正文",
                category="资治通鉴",
                dynasty="周紀一",
                summary="摘要",
                content="正文",
                source_url="https://www.dutongjian.com/api/reign",
                updated_at="2026-08-02",
                volume_id="juan-1",
                year_id="reign-1",
            )
        ]
    )

    output = tmp_path / "offline_content.ndjson.gz"
    output.write_text("previous export", encoding="utf-8")

    with pytest.raises(ValueError, match="incomplete item"):
        export_content(database, output, expected_count=1)

    assert output.read_text(encoding="utf-8") == "previous export"


def test_export_catalog_refuses_incomplete_hierarchy(tmp_path):
    store = ContentStore(tmp_path / "catalog.db")
    output = tmp_path / "offline_catalog.json"

    with pytest.raises(ValueError, match="refusing Android catalog export"):
        export_catalog(store.path, output, expected_volumes=294, expected_years=1405)

    assert not output.exists()
