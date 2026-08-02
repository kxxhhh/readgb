"""Rate-limited, resumable import of the public 资治通鉴 API."""

import argparse
import hashlib
import json
import re
import sys
import time
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Protocol
from urllib.error import HTTPError
from urllib.parse import urlencode, urljoin
from urllib.request import Request, urlopen

from .models import Item, ReadingYear, Volume
from .store import ContentStore


@dataclass(frozen=True)
class ReignRef:
    juan_id: str
    juan_title: str
    ji: str
    juan_index: int
    emperor_name: str
    reign_id: str
    reign_name: str
    year_anno: str
    year_int: int | None
    sort_order: int


@dataclass(frozen=True)
class SyncProgress:
    total_reigns: int
    completed_reigns: int
    content_records: int


class PublicTongjianApi(Protocol):
    def fetch_catalog(self) -> dict[str, Any]: ...

    def fetch_reign(self, reign_id: str) -> dict[str, Any]: ...


class TongjianApiClient:
    """Small standard-library client with disk cache, retry, and one-request pacing."""

    def __init__(
        self,
        base_url: str = "https://www.dutongjian.com",
        *,
        cache_dir: str | Path = "data/tongjian-cache",
        opener: Callable = urlopen,
        sleep: Callable[[float], None] = time.sleep,
        retries: int = 3,
        min_interval: float = 5.0,
        timeout: float = 30.0,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.cache_dir = Path(cache_dir)
        self.opener = opener
        self.sleep = sleep
        self.retries = retries
        self.min_interval = max(2.0, min_interval)
        self.timeout = timeout
        self._last_request = 0.0

    def fetch_catalog(self) -> dict[str, Any]:
        return self._fetch_json("/api/table_of_contents", self.cache_dir / "catalog.json")

    def fetch_reign(self, reign_id: str) -> dict[str, Any]:
        path = f"/api/reign?{urlencode({'reign_tongjian_id': reign_id})}"
        cache_name = hashlib.sha256(reign_id.encode("utf-8")).hexdigest() + ".json"
        return self._fetch_json(path, self.cache_dir / "reigns" / cache_name)

    def _fetch_json(self, path: str, cache_path: Path) -> dict[str, Any]:
        if cache_path.exists():
            return json.loads(cache_path.read_text(encoding="utf-8"))
        url = urljoin(f"{self.base_url}/", path.lstrip("/"))
        error: Exception | None = None
        for attempt in range(self.retries + 1):
            elapsed = time.monotonic() - self._last_request
            if elapsed < self.min_interval:
                self.sleep(self.min_interval - elapsed)
            self._last_request = time.monotonic()
            try:
                request = Request(
                    url,
                    headers={
                        "Accept": "application/json",
                        "User-Agent": "dutongjian-app/1.0 (+local development)",
                    },
                )
                with self.opener(request, timeout=self.timeout) as response:
                    payload = json.loads(response.read().decode("utf-8"))
                if not isinstance(payload, dict) or not isinstance(payload.get("data"), dict):
                    raise ValueError(f"unexpected JSON envelope from {path}")
                cache_path.parent.mkdir(parents=True, exist_ok=True)
                _atomic_write_json(cache_path, payload)
                return payload
            except HTTPError as caught:
                error = caught
                if caught.code == 429:
                    self.sleep(_retry_after_seconds(caught))
                elif attempt < self.retries:
                    self.sleep(min(30.0, 2**attempt))
                if attempt == self.retries:
                    break
            except (OSError, ValueError, json.JSONDecodeError) as caught:
                error = caught
                if attempt < self.retries:
                    self.sleep(min(30.0, 2**attempt))
        raise RuntimeError(f"failed to fetch {url}: {error}") from error


def flatten_catalog(payload: dict[str, Any]) -> list[ReignRef]:
    data = payload.get("data") or {}
    juan_list = data.get("juan_list") or []
    result: list[ReignRef] = []
    for juan_index, juan in enumerate(juan_list, start=1):
        juan_id = str(juan.get("tongjian_id") or "")
        juan_title = str(juan.get("juan") or "").strip()
        ji = str(juan.get("ji") or "").strip()
        if not juan_id or not juan_title:
            raise ValueError(f"catalog juan missing id/title at index {juan_index}")
        for emperor in juan.get("emperor_list") or []:
            emperor_name = str(emperor.get("emperor_name") or "").strip()
            for reign in emperor.get("reign_list") or []:
                reign_id = str(reign.get("tongjian_id") or "")
                if not reign_id:
                    raise ValueError(f"catalog reign missing id in {juan_title}")
                year_int = reign.get("year_int")
                result.append(
                    ReignRef(
                        juan_id=juan_id,
                        juan_title=juan_title,
                        ji=ji,
                        juan_index=juan_index,
                        emperor_name=emperor_name,
                        reign_id=reign_id,
                        reign_name=str(reign.get("reign_name") or "").strip(),
                        year_anno=str(reign.get("year_anno") or "").strip(),
                        year_int=int(year_int) if year_int is not None else None,
                        sort_order=len(result) + 1,
                    )
                )
    if not result:
        raise ValueError("catalog contains no reigns")
    return result


def parse_reign_items(payload: dict[str, Any], ref: ReignRef, source_url: str) -> list[Item]:
    data = payload.get("data") or {}
    contents = data.get("ExtRef_Children_contents") or []
    fetched_at = datetime.now(timezone.utc).isoformat()
    items: list[Item] = []
    for index, raw in enumerate(contents, start=1):
        if not isinstance(raw, dict):
            continue
        content_id = str(raw.get("tongjian_id") or f"{ref.reign_id}-{index}")
        original = str(raw.get("content") or "")
        simplified = str(raw.get("content_jianti_auto") or original)
        translation = str(raw.get("content_fanyi") or "")
        display = _strip_paragraph_number(simplified)
        title = _title(display, ref, raw.get("number") or index)
        tags = _tags(raw)
        items.append(
            Item(
                id=f"zztj-{content_id}",
                title=title,
                category="资治通鉴",
                dynasty=ref.ji,
                summary=display[:160],
                content=simplified,
                source_url=source_url,
                updated_at=fetched_at,
                section="资治通鉴",
                volume_id=ref.juan_id,
                year_id=ref.reign_id,
                original=original,
                translation=translation,
                notes=json.dumps(raw, ensure_ascii=False, separators=(",", ":")),
                tags=tags,
            )
        )
    return items


class TongjianSync:
    def __init__(
        self,
        api: PublicTongjianApi,
        store: ContentStore,
        *,
        checkpoint_path: str | Path = "data/tongjian-progress.json",
        on_progress: Callable[[SyncProgress, ReignRef], None] | None = None,
    ) -> None:
        self.api = api
        self.store = store
        self.checkpoint_path = Path(checkpoint_path)
        self.on_progress = on_progress

    def run(self) -> SyncProgress:
        catalog = self.api.fetch_catalog()
        refs = flatten_catalog(catalog)
        self._upsert_catalog(refs)
        catalog_hash = hashlib.sha256(json.dumps(catalog, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()
        checkpoint = self._load_checkpoint(catalog_hash)
        completed = set(checkpoint["completed_reign_ids"])
        for ref in refs:
            if ref.reign_id in completed:
                continue
            payload = self.api.fetch_reign(ref.reign_id)
            query = urlencode({"reign_tongjian_id": ref.reign_id})
            source_url = f"{self._base_url()}/api/reign?{query}"
            items = parse_reign_items(payload, ref, source_url)
            self.store.upsert_items(items)
            completed.add(ref.reign_id)
            progress = SyncProgress(len(refs), len(completed), self.store.count_items(category="资治通鉴"))
            self._save_checkpoint(catalog_hash, refs, completed)
            if self.on_progress:
                self.on_progress(progress, ref)
        if len(completed) == len(refs):
            self.store.remove_seed_items()
            self.store.remove_seed_catalog()
        return SyncProgress(len(refs), len(completed), self.store.count_items(category="资治通鉴"))

    def _base_url(self) -> str:
        return str(getattr(self.api, "base_url", "https://www.dutongjian.com")).rstrip("/")

    def _upsert_catalog(self, refs: list[ReignRef]) -> None:
        volumes: dict[str, Volume] = {}
        years: list[ReadingYear] = []
        for ref in refs:
            volumes[ref.juan_id] = Volume(ref.juan_id, "zizhi", ref.juan_title, ref.ji, ref.juan_index)
            years.append(
                ReadingYear(
                    ref.reign_id,
                    ref.juan_id,
                    f"{ref.emperor_name}{ref.reign_name}",
                    ref.year_anno,
                    ref.sort_order,
                )
            )
        self.store.upsert_volumes(list(volumes.values()))
        self.store.upsert_years(years)

    def _load_checkpoint(self, catalog_hash: str) -> dict[str, Any]:
        if not self.checkpoint_path.exists():
            return {"catalog_hash": catalog_hash, "completed_reign_ids": []}
        payload = json.loads(self.checkpoint_path.read_text(encoding="utf-8"))
        if payload.get("catalog_hash") != catalog_hash:
            return {"catalog_hash": catalog_hash, "completed_reign_ids": []}
        return payload

    def _save_checkpoint(self, catalog_hash: str, refs: list[ReignRef], completed: set[str]) -> None:
        payload = {
            "catalog_hash": catalog_hash,
            "total_reigns": len(refs),
            "completed_reign_ids": sorted(completed),
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }
        _atomic_write_json(self.checkpoint_path, payload)


def _strip_paragraph_number(value: str) -> str:
    return re.sub(r"^\s*\d+\s*", "", value).strip() or value.strip()


def _title(value: str, ref: ReignRef, number: Any) -> str:
    if value:
        return value[:48]
    return f"{ref.juan_title} · {ref.reign_name} · 第{number}段"


def _tags(raw: dict[str, Any]) -> tuple[str, ...]:
    result: list[str] = []

    def add(value: Any) -> None:
        text = str(value or "").strip()
        if text and text not in result:
            result.append(text)

    for value in raw.get("class_tags") or []:
        add(value)
    for key in ("ExtRef_Children_topics", "ExtRef_Children_people", "ExtRef_Children_places", "ExtRef_Children_officials"):
        for entity in raw.get(key) or []:
            if not isinstance(entity, dict):
                continue
            for field in ("title", "topic_name", "people_name_jianti_auto", "place_name_jianti_auto", "official_name"):
                if field in entity:
                    add(entity[field])
                    break
    return tuple(result)


def _atomic_write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = path.with_suffix(path.suffix + ".tmp")
    temp_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    temp_path.replace(path)


def _retry_after_seconds(error: HTTPError) -> float:
    value = error.headers.get("Retry-After") if error.headers else None
    try:
        return max(1.0, float(value)) if value else 60.0
    except ValueError:
        return 60.0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Import the public 资治通鉴 API into SQLite")
    parser.add_argument("--allow-public-api", action="store_true", help="Confirm that only public unauthenticated API paths may be fetched")
    parser.add_argument("--base-url", default="https://www.dutongjian.com")
    parser.add_argument("--database", default="data/dutongjian.db")
    parser.add_argument("--cache-dir", default="data/tongjian-cache")
    parser.add_argument("--checkpoint", default="data/tongjian-progress.json")
    parser.add_argument("--min-interval", type=float, default=5.0)
    return parser


def main() -> int:
    args = _parser().parse_args()
    if not args.allow_public_api:
        print("refusing public API sync without --allow-public-api", file=sys.stderr)
        return 2
    client = TongjianApiClient(args.base_url, cache_dir=args.cache_dir, min_interval=args.min_interval)

    def report(progress: SyncProgress, ref: ReignRef) -> None:
        print(
            f"completed {progress.completed_reigns}/{progress.total_reigns} "
            f"reigns, {progress.content_records} content records; {ref.juan_title} {ref.reign_name}",
            flush=True,
        )

    result = TongjianSync(
        client,
        ContentStore(args.database),
        checkpoint_path=args.checkpoint,
        on_progress=report,
    ).run()
    print(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
