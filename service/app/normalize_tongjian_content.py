"""Migrate previously imported 资治通鉴 records to the normalized reader form."""

import argparse

from .content_normalization import normalize_tongjian_item
from .store import ContentStore


def main() -> int:
    parser = argparse.ArgumentParser(description="Normalize imported historical paragraph records")
    parser.add_argument("--database", default="data/dutongjian.db")
    args = parser.parse_args()

    store = ContentStore(args.database)
    count = store.count_items()
    items = [
        item
        for item in store.list_items(limit=max(1, count))
        if item.id.startswith(("zztj-", "jishi-item-", "lun-item-", "tongjian-jishi-", "dutongjian-lun-"))
    ]
    normalized = [normalize_tongjian_item(item) for item in items]
    changed = [replacement for item, replacement in zip(items, normalized) if item != replacement]
    if changed:
        store.upsert_items(changed)
    print({"scanned": len(items), "changed": len(changed)})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
