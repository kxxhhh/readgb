import json

from app.models import Item, ReadingYear, Volume
from app.store import ContentStore
from app.validate_tongjian import validate_dataset


def test_validate_dataset_reports_partial_coverage_and_field_errors(tmp_path):
    store = ContentStore(tmp_path / "content.db")
    store.upsert_volumes([Volume("volume-1", "zizhi", "卷一", "周纪", 1)])
    store.upsert_years([ReadingYear("year-1", "volume-1", "二十三年", "前403年", 1)])
    store.upsert_items(
        [
            Item(
                id="zztj-content-1",
                title="正文",
                category="资治通鉴",
                dynasty="周纪",
                summary="摘要",
                content="内容",
                source_url="https://www.dutongjian.com/api/reign",
                updated_at="2026-08-03",
                volume_id="volume-1",
                year_id="year-1",
                original="原文",
                translation="译文",
                notes=json.dumps({"ExtRef_Children_people": [{"name": "魏斯"}]}),
            ),
            Item(
                id="zztj-content-2",
                title="缺字段",
                category="资治通鉴",
                dynasty="周纪",
                summary="",
                content="内容",
                source_url="https://www.dutongjian.com/api/reign",
                updated_at="2026-08-03",
                volume_id="volume-1",
                year_id="year-1",
                original="原文",
                translation="译文",
            ),
        ]
    )

    report = validate_dataset(store.path, expected_items=3, expected_volumes=1, expected_years=1)

    assert report["complete"] is False
    assert report["actual"]["real_items"] == 2
    assert report["coverage"]["items"] == {"actual": 2, "expected": 3, "percent": 66.67}
    assert report["field_integrity"]["missing_required"] == 1
    assert report["relations"]["items_with_relations"] == 1


def test_validate_dataset_accepts_exact_content_and_checkpoint(tmp_path):
    store = ContentStore(tmp_path / "complete.db")
    store.remove_seed_items()
    store.remove_seed_catalog()
    store.upsert_volumes([Volume("volume-1", "zizhi", "卷一", "周纪", 1)])
    store.upsert_years([ReadingYear("year-1", "volume-1", "二十三年", "前403年", 1)])
    store.upsert_items(
        [
            Item(
                id="zztj-content-1",
                title="正文",
                category="资治通鉴",
                dynasty="周纪",
                summary="摘要",
                content="内容",
                source_url="https://www.dutongjian.com/api/reign",
                updated_at="2026-08-03",
                volume_id="volume-1",
                year_id="year-1",
                original="原文",
                translation="译文",
                notes="{}",
            )
        ]
    )
    checkpoint = tmp_path / "progress.json"
    checkpoint.write_text(json.dumps({"completed_reign_ids": ["year-1"], "failed_reign_ids": []}), encoding="utf-8")

    report = validate_dataset(store.path, checkpoint=checkpoint, expected_items=1, expected_volumes=1, expected_years=1)

    assert report["complete"] is True
    assert report["checkpoint"]["completed"] == 1


def test_validate_dataset_reports_failed_checkpoint(tmp_path):
    store = ContentStore(tmp_path / "failed.db")
    checkpoint = tmp_path / "progress.json"
    checkpoint.write_text(json.dumps({"completed_reign_ids": [], "failed_reign_ids": ["year-1"]}), encoding="utf-8")

    report = validate_dataset(store.path, checkpoint=checkpoint, expected_items=0, expected_volumes=0, expected_years=0)

    assert report["complete"] is False
    assert report["checkpoint"]["failed"] == 1
