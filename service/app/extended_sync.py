"""Resumable synchronization for the public event and commentary APIs."""

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlencode

from .crawler import RobotsAwareFetcher
from .content_normalization import normalize_tongjian_item
from .models import Item, ReadingYear, Volume
from .store import ContentStore


EVENT_TABLE = "/api/table_of_events"
EVENT_CONTENT = "/api/content_list_of_event"
COMMENT_TABLE = "/api/table_of_comments"
COMMENT_CONTENT = "/api/content_list_of_comment"


def _clean(value: Any) -> str:
    return " ".join(str(value or "").split()).strip()


def _stable_id(prefix: str, *values: str) -> str:
    value = "|".join(values)
    return f"{prefix}-{hashlib.sha1(value.encode('utf-8')).hexdigest()[:20]}"


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _source_url(base_url: str, path: str, params: dict[str, str]) -> str:
    return f"{base_url.rstrip('/')}{path}?{urlencode(params)}"


def _read_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"events_completed": [], "comments_completed": [], "errors": {}}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"events_completed": [], "comments_completed": [], "errors": {}}
    if not isinstance(payload, dict):
        return {"events_completed": [], "comments_completed": [], "errors": {}}
    payload.setdefault("events_completed", [])
    payload.setdefault("comments_completed", [])
    payload.setdefault("errors", {})
    return payload


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def _fetch_json(fetcher: RobotsAwareFetcher, path: str, params: dict[str, str], cache_path: Path) -> dict[str, Any] | None:
    if cache_path.exists():
        try:
            payload = json.loads(cache_path.read_text(encoding="utf-8"))
            return payload if isinstance(payload, dict) else None
        except (OSError, json.JSONDecodeError):
            cache_path.unlink(missing_ok=True)
    body = fetcher.fetch(f"{path}?{urlencode(params)}")
    if body is None:
        return None
    try:
        payload = json.loads(body)
    except json.JSONDecodeError:
        return None
    if not isinstance(payload, dict):
        return None
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = cache_path.with_suffix(cache_path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    temporary.replace(cache_path)
    return payload


def _event_records(base_url: str, event_name: str, order: int, payload: dict[str, Any]) -> tuple[Volume, ReadingYear, list[Item]]:
    event_key = _stable_id("jishi-event", str(order), event_name)
    volume_id = "jishi-volume-001" if order == 1 else event_key
    year_id = "jishi-year-001" if order == 1 else _stable_id("jishi-year", str(order), event_name)
    volume = Volume(volume_id, "jishi", event_name, "纪事本末", order)
    year = ReadingYear(year_id, volume_id, event_name, "事件", order)
    data = payload.get("data") or {}
    children = data.get("children_contents") if isinstance(data, dict) else []
    items: list[Item] = []
    for index, child in enumerate(children or [], start=1):
        paragraph = child.get("paragraph") if isinstance(child, dict) else None
        if not isinstance(paragraph, dict):
            continue
        original = _clean(paragraph.get("content_jianti_auto") or paragraph.get("content"))
        if not original:
            continue
        translation = _clean(paragraph.get("content_fanyi"))
        path = _clean(paragraph.get("path"))
        item_id = "tongjian-jishi-001" if order == 1 and index == 1 else _stable_id("jishi-item", event_name, str(index), _clean(paragraph.get("tongjian_id")))
        source = _source_url(base_url, EVENT_CONTENT, {"event_name": event_name})
        items.append(normalize_tongjian_item(Item(
            id=item_id,
            title=f"{event_name} · 第{index}段",
            category="通鉴纪事本末",
            dynasty="纪事本末",
            summary=_clean(child.get("brief"))[:240] or (translation or original)[:240],
            content=original,
            source_url=source,
            updated_at=_now(),
            section="纪事本末",
            volume_id=volume_id,
            year_id=year_id,
            original=original,
            translation=translation or original,
            notes=json.dumps({"event_name": event_name, "path": path}, ensure_ascii=False),
            tags=("事件", event_name),
            sort_order=index,
        )))
    return volume, year, items


def _comment_records(base_url: str, juan: dict[str, Any], emperor: dict[str, Any], topic: dict[str, Any], order: int, payload: dict[str, Any]) -> tuple[Volume, ReadingYear, list[Item]]:
    juan_name = _clean(juan.get("juan_name"))
    emperor_name = _clean(emperor.get("emperor_name"))
    topic_name = _clean(topic.get("topic_name"))
    volume_id = "lun-volume-001" if order == 1 else _stable_id("lun-volume", juan_name)
    volume = Volume(volume_id, "lun", juan_name, "读通鉴论", order)
    year_id = "lun-year-001" if order == 1 else _stable_id("lun-topic", juan_name, emperor_name, topic_name)
    year = ReadingYear(year_id, volume_id, topic_name, emperor_name or juan_name, order)
    data = payload.get("data") or {}
    paragraphs = data.get("paragraph_list") if isinstance(data, dict) else []
    items: list[Item] = []
    for index, paragraph in enumerate(paragraphs or [], start=1):
        if not isinstance(paragraph, dict):
            continue
        original = _clean(paragraph.get("wenyan_clean"))
        if not original:
            continue
        translation = _clean(paragraph.get("fanyi"))
        item_id = "dutongjian-lun-001" if order == 1 and index == 1 else _stable_id("lun-item", juan_name, emperor_name, topic_name, str(index), _clean(paragraph.get("tongjian_id")))
        source = _source_url(base_url, COMMENT_CONTENT, {"topic_tongjian_id": topic_name})
        items.append(normalize_tongjian_item(Item(
            id=item_id,
            title=f"{topic_name} · 第{index}段",
            category="读通鉴论",
            dynasty=emperor_name or juan_name,
            summary=(translation or original)[:240],
            content=original,
            source_url=source,
            updated_at=_now(),
            section="读通鉴论",
            volume_id=volume_id,
            year_id=year_id,
            original=original,
            translation=translation or original,
            notes=json.dumps({"juan": juan_name, "emperor": emperor_name, "topic": topic_name}, ensure_ascii=False),
            tags=("史论", emperor_name, topic_name),
            sort_order=index,
        )))
    return volume, year, items


class ExtendedContentSync:
    def __init__(self, fetcher: RobotsAwareFetcher, store: ContentStore, *, cache_dir: Path, progress_path: Path) -> None:
        self.fetcher = fetcher
        self.store = store
        self.cache_dir = cache_dir
        self.progress_path = progress_path
        self.progress = _read_json(progress_path)

    def _save_progress(self) -> None:
        self.progress["updated_at"] = _now()
        _write_json(self.progress_path, self.progress)

    def sync_events(self) -> None:
        table = _fetch_json(self.fetcher, EVENT_TABLE, {}, self.cache_dir / "tables" / "events.json")
        events = ((table or {}).get("data") or {}).get("event_list") if table else []
        for order, event_name in enumerate(events or [], start=1):
            event_name = _clean(event_name)
            if not event_name or event_name in self.progress["events_completed"]:
                continue
            try:
                key = hashlib.sha1(event_name.encode("utf-8")).hexdigest()
                payload = _fetch_json(self.fetcher, EVENT_CONTENT, {"event_name": event_name}, self.cache_dir / "events" / f"{key}.json")
                if payload is None:
                    raise RuntimeError("event content request failed")
                volume, year, items = _event_records(self.fetcher.base_url, event_name, order, payload)
                if not items:
                    raise RuntimeError("event content returned no readable paragraphs")
                self.store.upsert_catalog([volume], [year])
                self.store.upsert_items(items)
                self.progress["events_completed"].append(event_name)
                self.progress["errors"].pop(f"event:{event_name}", None)
            except Exception as error:  # keep the checkpoint moving across independent public pages
                self.progress["errors"][f"event:{event_name}"] = str(error)
            self._save_progress()
            print({"kind": "event", "completed": len(self.progress["events_completed"]), "total": len(events or []), "name": event_name}, flush=True)

    def sync_comments(self) -> None:
        table = _fetch_json(self.fetcher, COMMENT_TABLE, {}, self.cache_dir / "tables" / "comments.json")
        juans = ((table or {}).get("data") or {}).get("juan_list") if table else []
        topics: list[tuple[dict[str, Any], dict[str, Any], dict[str, Any]]] = []
        for juan in juans or []:
            for emperor in juan.get("emperor_list") or []:
                for topic in emperor.get("topic_list") or []:
                    topics.append((juan, emperor, topic))
        for order, (juan, emperor, topic) in enumerate(topics, start=1):
            topic_name = _clean(topic.get("topic_name"))
            key_name = "|".join((_clean(juan.get("juan_name")), _clean(emperor.get("emperor_name")), topic_name))
            if not topic_name or key_name in self.progress["comments_completed"]:
                continue
            try:
                key = hashlib.sha1(key_name.encode("utf-8")).hexdigest()
                payload = _fetch_json(self.fetcher, COMMENT_CONTENT, {"topic_tongjian_id": topic_name}, self.cache_dir / "comments" / f"{key}.json")
                if payload is None:
                    raise RuntimeError("comment content request failed")
                volume, year, items = _comment_records(self.fetcher.base_url, juan, emperor, topic, order, payload)
                if not items:
                    raise RuntimeError("comment content returned no readable paragraphs")
                self.store.upsert_catalog([volume], [year])
                self.store.upsert_items(items)
                self.progress["comments_completed"].append(key_name)
                self.progress["errors"].pop(f"comment:{key_name}", None)
            except Exception as error:
                self.progress["errors"][f"comment:{key_name}"] = str(error)
            self._save_progress()
            print({"kind": "comment", "completed": len(self.progress["comments_completed"]), "total": len(topics), "name": topic_name}, flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Resume public 纪事本末/读通鉴论 API synchronization")
    parser.add_argument("--base-url", default="https://www.dutongjian.com")
    parser.add_argument("--database", default="service/data/dutongjian.db")
    parser.add_argument("--cache", default="service/data/extended-cache")
    parser.add_argument("--progress", default="service/data/extended-progress.json")
    parser.add_argument("--kind", choices=("events", "comments", "all"), default="all")
    parser.add_argument("--min-interval", type=float, default=5.0)
    args = parser.parse_args()
    fetcher = RobotsAwareFetcher(args.base_url, min_interval=max(1.0, args.min_interval), retries=3, timeout=60.0)
    sync = ExtendedContentSync(fetcher, ContentStore(args.database), cache_dir=Path(args.cache), progress_path=Path(args.progress))
    if args.kind in {"events", "all"}:
        sync.sync_events()
    if args.kind in {"comments", "all"}:
        sync.sync_comments()
    print({"events": len(sync.progress["events_completed"]), "comments": len(sync.progress["comments_completed"]), "errors": len(sync.progress["errors"])})
    return 0 if not sync.progress["errors"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
