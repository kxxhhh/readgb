"""Export the verified full 通鉴 corpus as a compressed Android asset."""

import argparse
import gzip
import hashlib
import json
from pathlib import Path
from typing import Iterable

from .models import Item, KnowledgeEntry
from .store import ContentStore


KNOWLEDGE_CATEGORIES = frozenset({"人物", "地点", "官职", "主题", "决策"})


def export_content(database: str | Path, output: str | Path, *, expected_count: int = 30_989) -> int:
    store = ContentStore(database)
    all_items = store.list_items(limit=max(1, store.count_items()))
    tongjian_items = [item for item in all_items if item.id.startswith("zztj-") and item.category == "资治通鉴"]
    supplemental_items = [item for item in all_items if _is_real_supplemental_item(item)]
    items = tongjian_items + supplemental_items
    if len(tongjian_items) != expected_count:
        raise ValueError(f"refusing Android export: expected {expected_count} real items, found {len(tongjian_items)}")
    _validate_content(items)
    destination = Path(output)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(temporary, "wt", encoding="utf-8", compresslevel=9) as stream:
        for item in items:
            stream.write(json.dumps(item.to_dict(), ensure_ascii=False, separators=(",", ":")))
            stream.write("\n")
    temporary.replace(destination)
    return len(items)


def export_partial_content(
    database: str | Path,
    output: str | Path,
    checkpoint: str | Path,
) -> int:
    return _export_partial_content(database, output, _completed_reign_ids(checkpoint))


def _export_partial_content(
    database: str | Path,
    output: str | Path,
    completed_reign_ids: set[str],
) -> int:
    store = ContentStore(database)
    items = _partial_items(store, completed_reign_ids)
    if not items:
        raise ValueError("refusing partial Android export: no completed content found")
    _validate_content(items)
    _write_content(items, output)
    return len(items)


def _validate_content(items: list[Item]) -> None:
    # The public API has one known record without content_fanyi; Android falls back to content.
    required_fields = (
        "title",
        "dynasty",
        "summary",
        "content",
        "source_url",
        "updated_at",
        "section",
        "volume_id",
        "year_id",
        "original",
    )
    for item in items:
        missing = [field for field in required_fields if not str(getattr(item, field) or "").strip()]
        if missing:
            fields = ", ".join(missing)
            raise ValueError(f"refusing Android export: incomplete item {item.id}: missing {fields}")


def _write_content(items: Iterable[Item], output: str | Path) -> None:
    destination = Path(output)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(temporary, "wt", encoding="utf-8", compresslevel=9) as stream:
        for item in items:
            stream.write(json.dumps(item.to_dict(), ensure_ascii=False, separators=(",", ":")))
            stream.write("\n")
    temporary.replace(destination)


def export_partial_knowledge(
    database: str | Path,
    output: str | Path,
    checkpoint: str | Path,
) -> dict[str, int]:
    return _export_partial_knowledge(database, output, _completed_reign_ids(checkpoint))


def _export_partial_knowledge(
    database: str | Path,
    output: str | Path,
    completed_reign_ids: set[str],
) -> dict[str, int]:
    store = ContentStore(database)
    items = _partial_items(store, completed_reign_ids)
    if not items:
        raise ValueError("refusing partial Android knowledge export: no completed content found")
    _validate_content(items)
    entries = _derive_knowledge(items)
    if not entries:
        raise ValueError("refusing partial Android knowledge export: no relations found")
    _validate_knowledge_entries(entries, require_all_categories=False)
    _write_json([entry.to_dict() for entry in entries], output)
    return {"entries": len(entries)}


def export_knowledge(database: str | Path, output: str | Path) -> dict[str, int]:
    store = ContentStore(database)
    category_count = store.count_items(category="资治通鉴")
    items = [
        item
        for item in store.list_items(category="资治通鉴", limit=max(1, category_count))
        if item.id.startswith("zztj-")
    ]
    if not items:
        raise ValueError("refusing Android knowledge export: no real content found")
    _validate_content(items)
    entries = _derive_knowledge(items)
    _validate_knowledge_entries(entries, require_all_categories=True)
    _write_json([entry.to_dict() for entry in entries], output)
    category_counts = {category: sum(entry.category == category for entry in entries) for category in sorted(KNOWLEDGE_CATEGORIES)}
    return {"entries": len(entries), **{f"category_{category}": count for category, count in category_counts.items()}}


def _derive_knowledge(items: Iterable[Item]) -> list[KnowledgeEntry]:
    relation_specs = (
        ("ExtRef_Children_people", "人物", ("people_name_jianti_auto", "people_name"), ("people_note", "note", "source_snippet")),
        ("ExtRef_Children_places", "地点", ("place_name_jianti_auto", "place_name"), ("place_note", "note", "source_snippet")),
        ("ExtRef_Children_officials", "官职", ("official_name", "title", "name"), ("official_note", "note", "source_snippet")),
        ("ExtRef_Children_topics", "主题", ("title", "topic_name", "name"), ("topic_note", "note", "source_snippet")),
        ("ExtRef_Children_decisions", "决策", ("title", "decision_name", "name"), ("decision_note", "note", "source_snippet")),
    )
    aggregates: dict[tuple[str, str], dict[str, object]] = {}
    for item in items:
        try:
            payload = json.loads(item.notes or "{}")
        except (TypeError, json.JSONDecodeError):
            continue
        if not isinstance(payload, dict):
            continue
        for relation_key, category, name_fields, note_fields in relation_specs:
            relations = payload.get(relation_key) or []
            if not isinstance(relations, list):
                continue
            for relation in relations:
                if not isinstance(relation, dict):
                    continue
                title = _first_text(relation, name_fields)
                if not title:
                    continue
                aggregate = aggregates.setdefault(
                    (category, title),
                    {"category": category, "title": title, "notes": [], "items": [], "source_url": item.source_url, "updated_at": item.updated_at},
                )
                _append_unique(aggregate["notes"], _first_text(relation, note_fields))
                _append_unique(aggregate["items"], item.title)
                aggregate["updated_at"] = max(str(aggregate["updated_at"]), item.updated_at)

    entries = []
    for category, title in sorted(aggregates):
        aggregate = aggregates[(category, title)]
        related_items = aggregate["items"]
        notes = aggregate["notes"]
        summary = f"{category}“{title}”在已抓取《资治通鉴》中关联 {len(related_items)} 条正文。"
        related_text = "、".join(related_items[:8])
        content = f"相关正文：{related_text}。" if related_text else summary
        if notes:
            content = f"{content} {'；'.join(notes[:3])}"
        entry_id = "offline-knowledge-" + hashlib.sha256(f"{category}:{title}".encode("utf-8")).hexdigest()[:20]
        entries.append(
            KnowledgeEntry(
                id=entry_id,
                title=title,
                category=category,
                summary=summary,
                content=content,
                source_url=str(aggregate["source_url"]),
                updated_at=str(aggregate["updated_at"]),
            )
        )
    return entries


def _validate_knowledge_entries(entries: list[KnowledgeEntry], *, require_all_categories: bool) -> None:
    seen_ids: set[str] = set()
    seen_keys: set[tuple[str, str]] = set()
    categories: set[str] = set()
    required_fields = ("id", "title", "category", "summary", "content", "source_url", "updated_at")
    for entry in entries:
        values = entry.to_dict()
        missing = [field for field in required_fields if not str(values.get(field) or "").strip()]
        if missing:
            raise ValueError(f"refusing Android knowledge export: incomplete entry {entry.id}: missing {', '.join(missing)}")
        if entry.id in seen_ids:
            raise ValueError(f"refusing Android knowledge export: duplicate entry id {entry.id}")
        key = (entry.category, entry.title)
        if key in seen_keys:
            raise ValueError(f"refusing Android knowledge export: duplicate entry {entry.category}/{entry.title}")
        seen_ids.add(entry.id)
        seen_keys.add(key)
        categories.add(entry.category)
    if require_all_categories:
        missing_categories = sorted(KNOWLEDGE_CATEGORIES - categories)
        if missing_categories:
            raise ValueError(
                "refusing Android knowledge export: missing relation categories " + ", ".join(missing_categories)
            )


def _first_text(payload: dict, fields: Iterable[str]) -> str:
    for field in fields:
        value = str(payload.get(field) or "").strip()
        if value:
            return value
    return ""


def _append_unique(values: object, value: str) -> None:
    if value and isinstance(values, list) and value not in values:
        values.append(value)


def export_partial_catalog(
    database: str | Path,
    output: str | Path,
    checkpoint: str | Path,
) -> dict[str, int]:
    return _export_partial_catalog(database, output, _completed_reign_ids(checkpoint))


def _export_partial_catalog(
    database: str | Path,
    output: str | Path,
    completed_reign_ids: set[str],
) -> dict[str, int]:
    store = ContentStore(database)
    items = _partial_items(store, completed_reign_ids)
    _validate_content(items)
    completed_year_ids = {item.year_id for item in items if item.year_id}
    years = [
        year.to_dict()
        for volume in store.volumes("zizhi")
        for year in store.years(volume.id)
        if not _is_seed_year(year.id) and year.id in completed_year_ids
    ]
    completed_volume_ids = {year["volume_id"] for year in years}
    volumes = [
        volume.to_dict()
        for volume in store.volumes("zizhi")
        if not _is_seed_volume(volume.id) and volume.id in completed_volume_ids
    ]
    sections = [section.to_dict() for section in store.sections() if section.id == "zizhi" and volumes]
    if not years:
        raise ValueError("refusing partial Android catalog export: no completed years found")
    _write_json({"sections": sections, "volumes": volumes, "years": years}, output)
    return {"sections": len(sections), "volumes": len(volumes), "years": len(years)}


def _partial_items(store: ContentStore, completed_reign_ids: set[str]) -> list[Item]:
    category_count = store.count_items(category="资治通鉴")
    return [
        item
        for item in store.list_items(category="资治通鉴", limit=max(1, category_count))
        if item.id.startswith("zztj-") and item.year_id in completed_reign_ids
    ]


def _completed_reign_ids(checkpoint: str | Path) -> set[str]:
    path = Path(checkpoint)
    if not path.exists():
        raise ValueError(f"refusing partial Android export: checkpoint not found: {path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    completed = payload.get("completed_reign_ids")
    if not isinstance(completed, list) or not all(isinstance(value, str) and value for value in completed):
        raise ValueError("refusing partial Android export: invalid completed_reign_ids")
    return set(completed)


def _write_json(payload: object, output: str | Path) -> None:
    destination = Path(output)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    temporary.replace(destination)


def export_catalog(database: str | Path, output: str | Path, *, expected_volumes: int = 294, expected_years: int = 1405) -> dict[str, int]:
    store = ContentStore(database)
    sections = [section.to_dict() for section in store.sections()]
    tongjian_volumes = [volume for volume in store.volumes("zizhi") if not _is_seed_volume(volume.id)]
    real_supplemental_volume_ids = {
        item.volume_id
        for item in store.list_items(limit=max(1, store.count_items()))
        if _is_real_supplemental_item(item) and item.volume_id
    }
    supplemental_volumes = [
        volume
        for section in store.sections()
        if section.id != "zizhi"
        for volume in store.volumes(section.id)
        if volume.id in real_supplemental_volume_ids
    ]
    catalog_volumes = tongjian_volumes + supplemental_volumes
    volumes = [volume.to_dict() for volume in catalog_volumes]
    years = []
    for volume in catalog_volumes:
        years.extend(
            year.to_dict()
            for year in store.years(volume.id)
            if volume.section_id != "zizhi" or not _is_seed_year(year.id)
        )
    tongjian_year_count = sum(
        1
        for volume in tongjian_volumes
        for year in store.years(volume.id)
        if not _is_seed_year(year.id)
    )
    if len(tongjian_volumes) != expected_volumes or tongjian_year_count != expected_years:
        raise ValueError(
            f"refusing Android catalog export: expected {expected_volumes} volumes/{expected_years} years, "
            f"found {len(tongjian_volumes)} volumes/{tongjian_year_count} years"
        )
    destination = Path(output)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary.write_text(json.dumps({"sections": sections, "volumes": volumes, "years": years}, ensure_ascii=False), encoding="utf-8")
    temporary.replace(destination)
    return {"sections": len(sections), "volumes": len(volumes), "years": len(years)}


def _is_seed_volume(volume_id: str) -> bool:
    return volume_id.startswith("zizhi-volume-")


def _is_seed_year(year_id: str) -> bool:
    return year_id.startswith("zizhi-year-")


def _is_real_supplemental_item(item: Item) -> bool:
    if item.section not in {"纪事本末", "读通鉴论"}:
        return False
    return (
        item.id.startswith(("jishi-item-", "lun-item-"))
        or "/api/content_list_of_event" in item.source_url
        or "/api/content_list_of_comment" in item.source_url
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Export complete verified content for Android offline import")
    parser.add_argument("--database", default="data/dutongjian.db")
    parser.add_argument("--output", default="../android/app/src/main/assets/offline_content.ndjson.gz")
    parser.add_argument("--expected-count", type=int, default=30_989)
    parser.add_argument("--catalog-output", default="../android/app/src/main/assets/offline_catalog.json")
    parser.add_argument("--knowledge-output", default="../android/app/src/main/assets/offline_knowledge.json")
    parser.add_argument("--expected-volumes", type=int, default=294)
    parser.add_argument("--expected-years", type=int, default=1405)
    parser.add_argument("--allow-partial", action="store_true")
    parser.add_argument("--checkpoint", default="data/tongjian-progress.json")
    args = parser.parse_args()
    if args.allow_partial:
        completed_reign_ids = _completed_reign_ids(args.checkpoint)
        count = _export_partial_content(args.database, args.output, completed_reign_ids)
        catalog = _export_partial_catalog(args.database, args.catalog_output, completed_reign_ids)
        knowledge = _export_partial_knowledge(args.database, args.knowledge_output, completed_reign_ids)
    else:
        count = export_content(args.database, args.output, expected_count=args.expected_count)
        catalog = export_catalog(
            args.database,
            args.catalog_output,
            expected_volumes=args.expected_volumes,
            expected_years=args.expected_years,
        )
        knowledge = export_knowledge(args.database, args.knowledge_output)
    print({"output": args.output, "records": count, "catalog_output": args.catalog_output, "knowledge_output": args.knowledge_output, **catalog, **knowledge})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
