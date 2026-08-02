import gzip
import json

import pytest

from app.export_android import (
    export_catalog,
    export_content,
    export_partial_catalog,
    export_partial_content,
    export_partial_knowledge,
)
from app.models import Item, ReadingYear, Volume
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


def test_partial_export_only_includes_completed_checkpoint_years(tmp_path):
    database = tmp_path / "partial.db"
    store = ContentStore(database)
    store.upsert_volumes([Volume("partial-volume", "zizhi", "卷一", "周纪", 1)])
    store.upsert_years(
        [
            ReadingYear("completed-year", "partial-volume", "二十三年", "前403年", 1),
            ReadingYear("pending-year", "partial-volume", "二十四年", "前402年", 2),
        ]
    )
    store.upsert_items(
        [
            Item(
                id="zztj-completed",
                title="已完成正文",
                category="资治通鉴",
                dynasty="周纪",
                summary="已完成摘要",
                content="已完成正文",
                source_url="https://www.dutongjian.com/api/reign?reign_tongjian_id=completed-year",
                updated_at="2026-08-02",
                volume_id="partial-volume",
                year_id="completed-year",
                original="已完成原文",
                translation="已完成译文",
            ),
            Item(
                id="zztj-pending",
                title="未完成正文",
                category="资治通鉴",
                dynasty="周纪",
                summary="未完成摘要",
                content="未完成正文",
                source_url="https://www.dutongjian.com/api/reign?reign_tongjian_id=pending-year",
                updated_at="2026-08-02",
                volume_id="partial-volume",
                year_id="pending-year",
                original="未完成原文",
                translation="未完成译文",
            ),
        ]
    )
    checkpoint = tmp_path / "progress.json"
    checkpoint.write_text(
        json.dumps({"total_reigns": 2, "completed_reign_ids": ["completed-year"]}),
        encoding="utf-8",
    )

    content_output = tmp_path / "offline_content.ndjson.gz"
    catalog_output = tmp_path / "offline_catalog.json"

    assert export_partial_content(database, content_output, checkpoint) == 1
    catalog = export_partial_catalog(database, catalog_output, checkpoint)

    with gzip.open(content_output, "rt", encoding="utf-8") as stream:
        assert [json.loads(line)["id"] for line in stream] == ["zztj-completed"]
    assert catalog == {"sections": 1, "volumes": 1, "years": 1}
    exported_catalog = json.loads(catalog_output.read_text(encoding="utf-8"))
    assert [year["id"] for year in exported_catalog["years"]] == ["completed-year"]
    assert [volume["id"] for volume in exported_catalog["volumes"]] == ["partial-volume"]


def test_partial_knowledge_export_extracts_completed_relations(tmp_path):
    database = tmp_path / "knowledge.db"
    store = ContentStore(database)
    store.upsert_items(
        [
            Item(
                id="zztj-completed",
                title="三家分晋",
                category="资治通鉴",
                dynasty="周纪",
                summary="已完成摘要",
                content="已完成正文",
                source_url="https://www.dutongjian.com/api/reign?reign_tongjian_id=completed-year",
                updated_at="2026-08-02",
                volume_id="partial-volume",
                year_id="completed-year",
                original="已完成原文",
                translation="已完成译文",
                notes=json.dumps(
                    {
                        "ExtRef_Children_people": [{"people_name_jianti_auto": "魏斯"}],
                        "ExtRef_Children_places": [{"place_name_jianti_auto": "晋"}],
                        "ExtRef_Children_officials": [{"official_name": "大将军", "official_note": "汉代官职"}],
                        "ExtRef_Children_topics": [{"title": "三家分晋"}],
                    },
                    ensure_ascii=False,
                ),
            )
        ]
    )
    checkpoint = tmp_path / "progress.json"
    checkpoint.write_text(json.dumps({"completed_reign_ids": ["completed-year"]}), encoding="utf-8")
    output = tmp_path / "offline_knowledge.json"

    assert export_partial_knowledge(database, output, checkpoint) == {"entries": 4}
    entries = json.loads(output.read_text(encoding="utf-8"))

    assert {entry["title"] for entry in entries} == {"魏斯", "晋", "大将军", "三家分晋"}
    official = next(entry for entry in entries if entry["title"] == "大将军")
    assert official["category"] == "官职"
    assert "汉代官职" in official["content"]
