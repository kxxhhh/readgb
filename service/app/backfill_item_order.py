"""Backfill source paragraph order from the raw crawler caches."""

import argparse
import hashlib
import json
from dataclasses import replace
from pathlib import Path
from typing import Any

from .extended_sync import _clean, _stable_id
from .store import ContentStore


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _add_order(index: dict[str, int], item_id: str, order: int) -> None:
    if item_id and item_id not in index:
        index[item_id] = order


def build_order_index(tongjian_cache: Path, extended_cache: Path) -> dict[str, int]:
    order: dict[str, int] = {}
    reign_dir = tongjian_cache / "reigns"
    for path in sorted(reign_dir.glob("*.json")):
        payload = _read_json(path)
        data = payload.get("data") or {}
        for index, raw in enumerate(data.get("ExtRef_Children_contents") or [], start=1):
            if not isinstance(raw, dict):
                continue
            content_id = str(raw.get("tongjian_id") or f"{data.get('tongjian_id', '')}-{index}").strip()
            _add_order(order, f"zztj-{content_id}", index)

    events_table = _read_json(extended_cache / "tables" / "events.json")
    events = ((events_table.get("data") or {}).get("event_list") or [])
    for event_order, event_name in enumerate(events, start=1):
        event_name = _clean(event_name)
        path = extended_cache / "events" / f"{hashlib.sha1(event_name.encode('utf-8')).hexdigest()}.json"
        if not event_name or not path.exists():
            continue
        data = (_read_json(path).get("data") or {})
        for index, child in enumerate(data.get("children_contents") or [], start=1):
            paragraph = child.get("paragraph") if isinstance(child, dict) else None
            if not isinstance(paragraph, dict):
                continue
            paragraph_id = _clean(paragraph.get("tongjian_id"))
            item_id = "tongjian-jishi-001" if event_order == 1 and index == 1 else _stable_id(
                "jishi-item", event_name, str(index), paragraph_id
            )
            _add_order(order, item_id, index)

    comments_table = _read_json(extended_cache / "tables" / "comments.json")
    juans = ((comments_table.get("data") or {}).get("juan_list") or [])
    topics: list[tuple[dict[str, Any], dict[str, Any], dict[str, Any]]] = []
    for juan in juans:
        for emperor in juan.get("emperor_list") or []:
            for topic in emperor.get("topic_list") or []:
                topics.append((juan, emperor, topic))
    for topic_order, (juan, emperor, topic) in enumerate(topics, start=1):
        juan_name = _clean(juan.get("juan_name"))
        emperor_name = _clean(emperor.get("emperor_name"))
        topic_name = _clean(topic.get("topic_name"))
        key_name = "|".join((juan_name, emperor_name, topic_name))
        path = extended_cache / "comments" / f"{hashlib.sha1(key_name.encode('utf-8')).hexdigest()}.json"
        if not topic_name or not path.exists():
            continue
        data = (_read_json(path).get("data") or {})
        for index, paragraph in enumerate(data.get("paragraph_list") or [], start=1):
            if not isinstance(paragraph, dict):
                continue
            paragraph_id = _clean(paragraph.get("tongjian_id"))
            item_id = "dutongjian-lun-001" if topic_order == 1 and index == 1 else _stable_id(
                "lun-item", juan_name, emperor_name, topic_name, str(index), paragraph_id
            )
            _add_order(order, item_id, index)
    return order


def backfill(database: str | Path, tongjian_cache: str | Path, extended_cache: str | Path) -> dict[str, int]:
    store = ContentStore(database)
    order = build_order_index(Path(tongjian_cache), Path(extended_cache))
    items = store.list_items(limit=max(1, store.count_items()))
    changed = []
    mapped = 0
    missing: list[str] = []
    for item in items:
        source_order = order.get(item.id)
        if source_order is None:
            missing.append(item.id)
            continue
        mapped += 1
        if item.sort_order != source_order:
            changed.append(replace(item, sort_order=source_order))
    if changed:
        store.upsert_items(changed)
    return {
        "scanned": len(items),
        "indexed": len(order),
        "mapped": mapped,
        "changed": len(changed),
        "missing": len(missing),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Backfill source paragraph order from crawler caches")
    parser.add_argument("--database", default="service/data/dutongjian.db")
    parser.add_argument("--tongjian-cache", default="service/data/tongjian-cache")
    parser.add_argument("--extended-cache", default="service/data/extended-cache")
    args = parser.parse_args()
    print(backfill(args.database, args.tongjian_cache, args.extended_cache))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
