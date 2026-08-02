"""Export the verified full 通鉴 corpus as a compressed Android asset."""

import argparse
import gzip
import json
from pathlib import Path

from .store import ContentStore


def export_content(database: str | Path, output: str | Path, *, expected_count: int = 30_989) -> int:
    store = ContentStore(database)
    items = [item for item in store.list_items(category="资治通鉴", limit=expected_count + 100) if item.id.startswith("zztj-")]
    if len(items) != expected_count:
        raise ValueError(f"refusing Android export: expected {expected_count} real items, found {len(items)}")
    destination = Path(output)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(temporary, "wt", encoding="utf-8", compresslevel=9) as stream:
        for item in items:
            stream.write(json.dumps(item.to_dict(), ensure_ascii=False, separators=(",", ":")))
            stream.write("\n")
    temporary.replace(destination)
    return len(items)


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
    args = parser.parse_args()
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
