"""Rate-limited, resumable import of the public 资治通鉴 API."""

import argparse
import concurrent.futures
import hashlib
import json
import re
import shutil
import sys
import threading
import time
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Any, Protocol
from urllib.error import HTTPError
from urllib.parse import urlencode, urljoin
from urllib.request import Request, urlopen
from urllib.robotparser import RobotFileParser

from .content_normalization import normalize_tongjian_item
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
    """Public API client with raw-response caching and one global request pace."""

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
        respect_robots: bool = False,
        robots_checker: Callable[[str], bool] | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.cache_dir = Path(cache_dir)
        self.opener = opener
        self.sleep = sleep
        self.retries = retries
        self.min_interval = max(2.0, min_interval)
        self.timeout = timeout
        self.respect_robots = respect_robots
        self.robots_checker = robots_checker
        self._last_request = 0.0
        self._next_request_at = 0.0
        self._pace_lock = threading.Lock()
        self._robots_lock = threading.Lock()
        self._robots: RobotFileParser | None = None

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
        if not self._allowed(url):
            raise RuntimeError(f"robots.txt disallows {url}")
        error: Exception | None = None
        for attempt in range(self.retries + 1):
            self._pace()
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
                    self._wait_after_rate_limit(_retry_after_seconds(caught))
                elif attempt < self.retries:
                    self.sleep(min(30.0, 2**attempt))
                if attempt == self.retries:
                    break
            except (OSError, ValueError, json.JSONDecodeError) as caught:
                error = caught
                if attempt < self.retries:
                    self.sleep(min(30.0, 2**attempt))
        raise RuntimeError(f"failed to fetch {url}: {error}") from error

    def _pace(self) -> None:
        # Network I/O can overlap, but request starts share both the normal
        # interval and any server-provided rate-limit cooldown.
        with self._pace_lock:
            now = time.monotonic()
            next_request = max(self._last_request + self.min_interval, self._next_request_at)
            if next_request > now:
                self.sleep(next_request - now)
            self._last_request = time.monotonic()
            if self._next_request_at <= self._last_request:
                self._next_request_at = 0.0

    def _wait_after_rate_limit(self, delay: float) -> None:
        with self._pace_lock:
            now = time.monotonic()
            next_request = max(self._next_request_at, now + delay)
            self._next_request_at = next_request
        self.sleep(max(0.0, next_request - now))
        with self._pace_lock:
            if self._next_request_at <= time.monotonic():
                self._next_request_at = 0.0

    def _allowed(self, url: str) -> bool:
        if not self.respect_robots:
            return True
        if self.robots_checker is not None:
            return self.robots_checker(url)
        with self._robots_lock:
            if self._robots is None:
                parser = RobotFileParser(urljoin(f"{self.base_url}/", "robots.txt"))
                try:
                    parser.read()
                except OSError:
                    return False
                self._robots = parser
            return self._robots.can_fetch("dutongjian-app/1.0", url)


def flatten_catalog(payload: dict[str, Any]) -> list[ReignRef]:
    data = payload.get("data") or {}
    juan_list = data.get("juan_list") or []
    result: list[ReignRef] = []
    seen_reign_ids: set[str] = set()
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
                if reign_id in seen_reign_ids:
                    raise ValueError(f"catalog contains duplicate reign id {reign_id}")
                seen_reign_ids.add(reign_id)
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
    contents = data.get("ExtRef_Children_contents")
    if not isinstance(contents, list) or not contents:
        raise ValueError(f"reign {ref.reign_id} contains no content records")
    fetched_at = datetime.now(timezone.utc).isoformat()
    items: list[Item] = []
    seen_content_ids: set[str] = set()
    for index, raw in enumerate(contents, start=1):
        if not isinstance(raw, dict):
            raise ValueError(f"reign {ref.reign_id} content {index} is not an object")
        content_id = str(raw.get("tongjian_id") or f"{ref.reign_id}-{index}").strip()
        if not content_id:
            raise ValueError(f"reign {ref.reign_id} content {index} has no id")
        if content_id in seen_content_ids:
            raise ValueError(f"reign {ref.reign_id} contains duplicate content id {content_id}")
        seen_content_ids.add(content_id)
        original = str(raw.get("content") or "")
        simplified = str(raw.get("content_jianti_auto") or original)
        translation = str(raw.get("content_fanyi") or "")
        display = _strip_paragraph_number(simplified)
        title = _title(display, ref, raw.get("number") or index)
        tags = _tags(raw)
        item = Item(
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
        items.append(normalize_tongjian_item(item))
    return items


class TongjianSync:
    def __init__(
        self,
        api: PublicTongjianApi,
        store: ContentStore,
        *,
        checkpoint_path: str | Path = "data/tongjian-progress.json",
        on_progress: Callable[[SyncProgress, ReignRef], None] | None = None,
        workers: int = 1,
        expected_reigns: int | None = None,
        expected_volumes: int | None = None,
    ) -> None:
        self.api = api
        self.store = store
        self.checkpoint_path = Path(checkpoint_path)
        self.on_progress = on_progress
        self.workers = max(1, workers)
        self.expected_reigns = expected_reigns
        self.expected_volumes = expected_volumes

    def run(self) -> SyncProgress:
        catalog = self.api.fetch_catalog()
        refs = flatten_catalog(catalog)
        if self.expected_reigns is not None and len(refs) != self.expected_reigns:
            raise RuntimeError(f"catalog contains {len(refs)} reigns; expected {self.expected_reigns} reigns")
        volume_count = len({ref.juan_id for ref in refs})
        if self.expected_volumes is not None and volume_count != self.expected_volumes:
            raise RuntimeError(f"catalog contains {volume_count} volumes; expected {self.expected_volumes} volumes")
        self._upsert_catalog(refs)
        catalog_hash = hashlib.sha256(json.dumps(catalog, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()
        checkpoint = self._load_checkpoint(catalog_hash)
        valid_reign_ids = {ref.reign_id for ref in refs}
        completed = {
            reign_id
            for reign_id in checkpoint["completed_reign_ids"]
            if reign_id in valid_reign_ids and self.store.count_real_items(year_id=reign_id) > 0
        }
        failures: dict[str, str] = {}
        pending = [ref for ref in refs if ref.reign_id not in completed]
        with concurrent.futures.ThreadPoolExecutor(max_workers=self.workers) as executor:
            futures = {executor.submit(self.api.fetch_reign, ref.reign_id): ref for ref in pending}
            for future in concurrent.futures.as_completed(futures):
                ref = futures[future]
                try:
                    payload = future.result()
                    query = urlencode({"reign_tongjian_id": ref.reign_id})
                    source_url = f"{self._base_url()}/api/reign?{query}"
                    # Preserve the public source fields; only title/summary are derived.
                    items = parse_reign_items(payload, ref, source_url)
                    self.store.upsert_items(items)
                except Exception as error:
                    failures[ref.reign_id] = f"{type(error).__name__}: {error}"
                    self._save_checkpoint(catalog_hash, refs, completed, failures)
                    continue
                completed.add(ref.reign_id)
                failures.pop(ref.reign_id, None)
                progress = SyncProgress(len(refs), len(completed), self.store.count_real_items())
                self._save_checkpoint(catalog_hash, refs, completed, failures)
                if self.on_progress:
                    self.on_progress(progress, ref)
        if failures:
            failed = ", ".join(sorted(failures)[:5])
            suffix = "..." if len(failures) > 5 else ""
            raise RuntimeError(f"{len(failures)} reigns failed; checkpoint preserved for retry: {failed}{suffix}")
        if len(completed) == len(refs):
            self.store.remove_seed_items()
            self.store.remove_seed_catalog()
        return SyncProgress(len(refs), len(completed), self.store.count_real_items())

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
                    ref.year_int,
                )
            )
        self.store.upsert_volumes(list(volumes.values()))
        self.store.upsert_years(years)

    def _load_checkpoint(self, catalog_hash: str) -> dict[str, Any]:
        if not self.checkpoint_path.exists():
            return {"catalog_hash": catalog_hash, "completed_reign_ids": []}
        try:
            payload = json.loads(self.checkpoint_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return {"catalog_hash": catalog_hash, "completed_reign_ids": []}
        if not isinstance(payload, dict) or not isinstance(payload.get("completed_reign_ids"), list):
            return {"catalog_hash": catalog_hash, "completed_reign_ids": []}
        if payload.get("catalog_hash") != catalog_hash:
            return {"catalog_hash": catalog_hash, "completed_reign_ids": []}
        return payload

    def _save_checkpoint(
        self,
        catalog_hash: str,
        refs: list[ReignRef],
        completed: set[str],
        failures: dict[str, str] | None = None,
    ) -> None:
        failures = failures or {}
        payload = {
            "catalog_hash": catalog_hash,
            "total_reigns": len(refs),
            "completed_reign_ids": sorted(completed),
            "updated_at": datetime.now(timezone.utc).isoformat(),
            "failed_reign_ids": sorted(failures),
            "last_errors": {reign_id: failures[reign_id] for reign_id in sorted(failures)[-20:]},
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
        if not value:
            return 60.0
        try:
            retry_at = parsedate_to_datetime(value)
            if retry_at.tzinfo is None:
                retry_at = retry_at.replace(tzinfo=timezone.utc)
            return max(1.0, (retry_at - datetime.now(timezone.utc)).total_seconds())
        except (TypeError, ValueError, OverflowError):
            return 60.0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Import the public 资治通鉴 API into SQLite")
    parser.add_argument("--allow-public-api", action="store_true", help="Confirm that only public unauthenticated API paths may be fetched")
    parser.add_argument("--base-url", default="https://www.dutongjian.com")
    parser.add_argument("--database", default="data/dutongjian.db")
    parser.add_argument("--cache-dir", default="data/tongjian-cache")
    parser.add_argument("--checkpoint", default="data/tongjian-progress.json")
    parser.add_argument("--min-interval", type=float, default=5.0, help="minimum seconds between request starts")
    parser.add_argument("--workers", type=int, default=4, help="bounded concurrent requests")
    parser.add_argument("--expected-reigns", type=int, default=None, help="refuse catalogs with a different reign count")
    parser.add_argument("--expected-volumes", type=int, default=None, help="refuse catalogs with a different volume count")
    parser.add_argument("--reset", action="store_true", help="clear old corpus, cache, and checkpoint before syncing")
    parser.add_argument("--respect-robots", action=argparse.BooleanOptionalAction, default=True)
    return parser


def main() -> int:
    args = _parser().parse_args()
    if not args.allow_public_api:
        print("refusing public API sync without --allow-public-api", file=sys.stderr)
        return 2
    cache_dir = Path(args.cache_dir)
    checkpoint_path = Path(args.checkpoint)
    store = ContentStore(args.database)
    if args.reset:
        store.clear_tongjian_content()
        if cache_dir.exists():
            shutil.rmtree(cache_dir)
        checkpoint_path.unlink(missing_ok=True)
    client = TongjianApiClient(
        args.base_url,
        cache_dir=cache_dir,
        min_interval=args.min_interval,
        respect_robots=args.respect_robots,
    )

    def report(progress: SyncProgress, ref: ReignRef) -> None:
        print(
            f"completed {progress.completed_reigns}/{progress.total_reigns} "
            f"reigns, {progress.content_records} content records; {ref.juan_title} {ref.reign_name}",
            flush=True,
        )

    try:
        result = TongjianSync(
            client,
            store,
            checkpoint_path=checkpoint_path,
            on_progress=report,
            workers=args.workers,
            expected_reigns=args.expected_reigns,
            expected_volumes=args.expected_volumes,
        ).run()
    except RuntimeError as error:
        print(f"sync incomplete: {error}", file=sys.stderr)
        return 1
    print(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
