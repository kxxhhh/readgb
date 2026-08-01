"""Explicit, rate-limited synchronization of public HTML into the local store."""

import argparse
from dataclasses import dataclass

from .crawler import RobotsAwareFetcher
from .parsers import parse_knowledge_index, parse_reading_entries
from .store import ContentStore


@dataclass(frozen=True)
class SyncResult:
    path: str
    records: int
    fetched: bool


class PublicContentSync:
    """Sync only caller-selected public pages; no link crawling is performed."""

    def __init__(self, fetcher: RobotsAwareFetcher, store: ContentStore) -> None:
        self.fetcher = fetcher
        self.store = store

    def sync_reading(
        self,
        path: str,
        *,
        section: str = "资治通鉴",
        volume_id: str | None = None,
        year_id: str | None = None,
    ) -> SyncResult:
        html = self.fetcher.fetch(path)
        if html is None:
            return SyncResult(path=path, records=0, fetched=False)
        items = parse_reading_entries(
            html,
            self.fetcher.base_url,
            section=section,
            volume_id=volume_id,
            year_id=year_id,
        )
        self.store.upsert_items(items)
        return SyncResult(path=path, records=len(items), fetched=True)

    def sync_knowledge(self, path: str) -> SyncResult:
        html = self.fetcher.fetch(path)
        if html is None:
            return SyncResult(path=path, records=0, fetched=False)
        entries = parse_knowledge_index(html, self.fetcher.base_url)
        self.store.upsert_knowledge(entries)
        return SyncResult(path=path, records=len(entries), fetched=True)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Sync explicitly selected public dutongjian HTML pages")
    parser.add_argument("--base-url", required=True, help="Allowed source origin, for example https://www.dutongjian.com")
    parser.add_argument("--path", required=True, help="One public path or URL; no recursive crawling is performed")
    parser.add_argument("--kind", choices=("reading", "knowledge"), default="reading")
    parser.add_argument("--section", default="资治通鉴")
    parser.add_argument("--volume-id")
    parser.add_argument("--year-id")
    parser.add_argument("--database", default="data/dutongjian.db")
    parser.add_argument("--min-interval", type=float, default=1.0)
    return parser


def main() -> int:
    args = _parser().parse_args()
    fetcher = RobotsAwareFetcher(args.base_url, min_interval=max(0.5, args.min_interval))
    sync = PublicContentSync(fetcher, ContentStore(args.database))
    if args.kind == "knowledge":
        result = sync.sync_knowledge(args.path)
    else:
        result = sync.sync_reading(
            args.path,
            section=args.section,
            volume_id=args.volume_id,
            year_id=args.year_id,
        )
    print({"path": result.path, "records": result.records, "fetched": result.fetched})
    return 0 if result.fetched else 1


if __name__ == "__main__":
    raise SystemExit(main())
