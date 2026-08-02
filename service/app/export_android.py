"""Export the verified full 通鉴 corpus as a compressed Android asset."""

import argparse
import gzip
import json
from pathlib import Path
from typing import Iterable

from .models import Item
from .store import ContentStore


def export_content(database: str | Path, output: str | Path, *, expected_count: int = 30_989) -> int:
    store = ContentStore(database)
    category_count = store.count_items(category="资治通鉴")
    items = [
        item
        for item in store.list_items(category="资治通鉴", limit=max(1, category_count))
        if item.id.startswith("zztj-")
    ]
    if len(items) != expected_count:
        raise ValueError(f"refusing Android export: expected {expected_count} real items, found {len(items)}")
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
        "translation",
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
        if year.id in completed_year_ids
    ]
    completed_volume_ids = {year["volume_id"] for year in years}
    volumes = [volume.to_dict() for volume in store.volumes("zizhi") if volume.id in completed_volume_ids]
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


def _write_json(payload: dict, output: str | Path) -> None:
    destination = Path(output)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    temporary.replace(destination)


def export_catalog(database: str | Path, output: str | Path, *, expected_volumes: int = 294, expected_years: int = 1405) -> dict[str, int]:
    store = ContentStore(database)
    sections = [section.to_dict() for section in store.sections()]
    volumes = [volume.to_dict() for volume in store.volumes("zizhi")]
    years = []
    for volume in volumes:
        years.extend(year.to_dict() for year in store.years(volume["id"]))
    if len(volumes) != expected_volumes or len(years) != expected_years:
        raise ValueError(
            f"refusing Android catalog export: expected {expected_volumes} volumes/{expected_years} years, "
            f"found {len(volumes)} volumes/{len(years)} years"
        )
    destination = Path(output)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary.write_text(json.dumps({"sections": sections, "volumes": volumes, "years": years}, ensure_ascii=False), encoding="utf-8")
    temporary.replace(destination)
    return {"sections": len(sections), "volumes": len(volumes), "years": len(years)}


def main() -> int:
    parser = argparse.ArgumentParser(description="Export complete verified content for Android offline import")
    parser.add_argument("--database", default="data/dutongjian.db")
    parser.add_argument("--output", default="../android/app/src/main/assets/offline_content.ndjson.gz")
    parser.add_argument("--expected-count", type=int, default=30_989)
    parser.add_argument("--catalog-output", default="../android/app/src/main/assets/offline_catalog.json")
    parser.add_argument("--expected-volumes", type=int, default=294)
    parser.add_argument("--expected-years", type=int, default=1405)
    parser.add_argument("--allow-partial", action="store_true")
    parser.add_argument("--checkpoint", default="data/tongjian-progress.json")
    args = parser.parse_args()
    if args.allow_partial:
        completed_reign_ids = _completed_reign_ids(args.checkpoint)
        count = _export_partial_content(args.database, args.output, completed_reign_ids)
        catalog = _export_partial_catalog(args.database, args.catalog_output, completed_reign_ids)
    else:
        count = export_content(args.database, args.output, expected_count=args.expected_count)
        catalog = export_catalog(
            args.database,
            args.catalog_output,
            expected_volumes=args.expected_volumes,
            expected_years=args.expected_years,
        )
    print({"output": args.output, "records": count, "catalog_output": args.catalog_output, **catalog})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
