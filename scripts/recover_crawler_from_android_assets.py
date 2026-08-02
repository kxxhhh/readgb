#!/usr/bin/env python3
"""Recover crawler progress from the content snapshot shipped in the APK."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.models import Item, ReadingYear, Volume
from app.store import ContentStore
from app.tongjian_sync import flatten_catalog


def _read_json(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError(f"expected JSON object: {path}")
    return payload


def _required_text(payload: dict[str, Any], field: str, *, line: int | None = None) -> str:
    value = payload.get(field)
    if not isinstance(value, str) or not value.strip():
        suffix = f" at line {line}" if line is not None else ""
        raise ValueError(f"missing non-empty {field}{suffix}")
    return value


def _load_asset_catalog(path: Path) -> tuple[list[str], list[Volume], list[ReadingYear]]:
    payload = _read_json(path)
    raw_volumes = payload.get("volumes")
    raw_years = payload.get("years")
    if not isinstance(raw_volumes, list) or not isinstance(raw_years, list) or not raw_years:
        raise ValueError(f"invalid offline catalog: {path}")

    volumes: list[Volume] = []
    for raw in raw_volumes:
        if not isinstance(raw, dict):
            raise ValueError("offline catalog contains a non-object volume")
        volumes.append(
            Volume(
                _required_text(raw, "id"),
                _required_text(raw, "section_id"),
                _required_text(raw, "title"),
                _required_text(raw, "dynasty"),
                int(raw.get("sort_order", 0)),
            )
        )

    years: list[ReadingYear] = []
    for raw in raw_years:
        if not isinstance(raw, dict):
            raise ValueError("offline catalog contains a non-object year")
        year_int = raw.get("year_int")
        years.append(
            ReadingYear(
                _required_text(raw, "id"),
                _required_text(raw, "volume_id"),
                _required_text(raw, "title"),
                _required_text(raw, "era"),
                int(raw.get("sort_order", 0)),
                int(year_int) if year_int is not None else None,
            )
        )

    year_ids = [year.id for year in years]
    if len(year_ids) != len(set(year_ids)):
        raise ValueError("offline catalog contains duplicate year IDs")
    volume_ids = {volume.id for volume in volumes}
    if any(year.volume_id not in volume_ids for year in years):
        raise ValueError("offline catalog contains a year with an unknown volume")
    return year_ids, volumes, years


def _load_asset_items(path: Path, year_ids: set[str]) -> list[Item]:
    items: list[Item] = []
    seen_ids: set[str] = set()
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, start=1):
            if not line.strip():
                continue
            raw = json.loads(line)
            if not isinstance(raw, dict):
                raise ValueError(f"content line {line_number} is not an object")
            item_id = _required_text(raw, "id", line=line_number)
            year_id = _required_text(raw, "year_id", line=line_number)
            if not item_id.startswith("zztj-"):
                raise ValueError(f"unexpected item ID at line {line_number}: {item_id}")
            if item_id in seen_ids:
                raise ValueError(f"duplicate item ID at line {line_number}: {item_id}")
            if year_id not in year_ids:
                raise ValueError(f"item year is absent from offline catalog at line {line_number}: {year_id}")
            tags = raw.get("tags") or []
            if not isinstance(tags, list) or not all(isinstance(tag, str) for tag in tags):
                raise ValueError(f"invalid tags at line {line_number}")
            items.append(
                Item(
                    id=item_id,
                    title=_required_text(raw, "title", line=line_number),
                    category=_required_text(raw, "category", line=line_number),
                    dynasty=_required_text(raw, "dynasty", line=line_number),
                    summary=_required_text(raw, "summary", line=line_number),
                    content=_required_text(raw, "content", line=line_number),
                    source_url=_required_text(raw, "source_url", line=line_number),
                    updated_at=_required_text(raw, "updated_at", line=line_number),
                    section=_required_text(raw, "section", line=line_number),
                    volume_id=_required_text(raw, "volume_id", line=line_number),
                    year_id=year_id,
                    original=_required_text(raw, "original", line=line_number),
                    translation=_required_text(raw, "translation", line=line_number),
                    notes=str(raw.get("notes") or ""),
                    tags=tuple(tags),
                )
            )
            seen_ids.add(item_id)
    if not items:
        raise ValueError(f"offline content is empty: {path}")
    return items


def _write_checkpoint(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def recover(args: argparse.Namespace) -> dict[str, int]:
    asset_catalog = Path(args.asset_catalog)
    asset_content = Path(args.asset_content)
    catalog_cache = Path(args.catalog_cache)
    checkpoint_path = Path(args.checkpoint)

    asset_year_ids, volumes, years = _load_asset_catalog(asset_catalog)
    current_catalog = _read_json(catalog_cache)
    refs = flatten_catalog(current_catalog)
    current_year_ids = [ref.reign_id for ref in refs]
    if current_year_ids[: len(asset_year_ids)] != asset_year_ids:
        raise ValueError("APK catalog is not an exact prefix of the current public catalog")
    catalog_hash = hashlib.sha256(
        json.dumps(current_catalog, ensure_ascii=False, sort_keys=True).encode("utf-8")
    ).hexdigest()

    items = _load_asset_items(asset_content, set(asset_year_ids))
    checkpoint = _read_json(checkpoint_path) if checkpoint_path.exists() else {}
    if checkpoint and checkpoint.get("catalog_hash") != catalog_hash:
        raise ValueError("checkpoint catalog hash does not match the cached public catalog")
    completed = set(checkpoint.get("completed_reign_ids") or [])
    unknown_completed = completed - set(current_year_ids)
    if unknown_completed:
        raise ValueError(f"checkpoint contains unknown year IDs: {sorted(unknown_completed)[:3]}")
    completed.update(asset_year_ids)

    store = ContentStore(args.database)
    before = store.count_items(category="资治通鉴")
    store.upsert_volumes(volumes)
    store.upsert_years(years)
    store.upsert_items(items)
    after = store.count_items(category="资治通鉴")
    _write_checkpoint(
        checkpoint_path,
        {
            "catalog_hash": catalog_hash,
            "total_reigns": len(current_year_ids),
            "completed_reign_ids": sorted(completed),
            "updated_at": datetime.now(timezone.utc).isoformat(),
        },
    )
    return {
        "asset_years": len(asset_year_ids),
        "asset_items": len(items),
        "completed_years": len(completed),
        "total_years": len(current_year_ids),
        "category_items_before": before,
        "category_items_after": after,
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset-catalog", default="android/app/src/main/assets/offline_catalog.json")
    parser.add_argument("--asset-content", default="android/app/src/main/assets/offline_content.ndjson.gz")
    parser.add_argument("--catalog-cache", default="service/data/tongjian-cache/catalog.json")
    parser.add_argument("--database", default="service/data/dutongjian.db")
    parser.add_argument("--checkpoint", default="service/data/tongjian-progress.json")
    return parser


if __name__ == "__main__":
    result = recover(_parser().parse_args())
    print(json.dumps(result, ensure_ascii=False, indent=2))
