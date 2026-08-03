"""Read-only validation and coverage reporting for the imported 通鉴 corpus."""

from __future__ import annotations

import argparse
import json
import sqlite3
from pathlib import Path
from typing import Any


DEFAULT_EXPECTED_ITEMS = 30_989
DEFAULT_EXPECTED_VOLUMES = 294
DEFAULT_EXPECTED_YEARS = 1_405
RELATION_KEYS = (
    "ExtRef_Children_people",
    "ExtRef_Children_places",
    "ExtRef_Children_officials",
    "ExtRef_Children_topics",
    "ExtRef_Children_decisions",
)
REQUIRED_ITEM_FIELDS = (
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


def validate_dataset(
    database: str | Path,
    *,
    checkpoint: str | Path | None = None,
    expected_items: int = DEFAULT_EXPECTED_ITEMS,
    expected_volumes: int = DEFAULT_EXPECTED_VOLUMES,
    expected_years: int = DEFAULT_EXPECTED_YEARS,
) -> dict[str, Any]:
    """Return a deterministic report without changing the database or checkpoint."""

    path = Path(database)
    with sqlite3.connect(path, timeout=30) as connection:
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA busy_timeout = 30000")
        real_filter = "i.id LIKE 'zztj-%' AND i.category = '资治通鉴'"
        required_clause = " OR ".join(
            f"TRIM(COALESCE(i.{field}, '')) = ''" for field in REQUIRED_ITEM_FIELDS
        )
        real_count = _scalar(connection, f"SELECT COUNT(*) FROM items i WHERE {real_filter}")
        distinct_real_ids = _scalar(connection, f"SELECT COUNT(DISTINCT i.id) FROM items i WHERE {real_filter}")
        missing_required = _scalar(
            connection,
            "SELECT COUNT(*) FROM items i WHERE "
            f"{real_filter} AND ({required_clause})",
        )
        orphan_volumes = _scalar(
            connection,
            f"SELECT COUNT(*) FROM items i LEFT JOIN volumes v ON v.id = i.volume_id "
            f"WHERE {real_filter} AND (i.volume_id IS NULL OR v.id IS NULL OR v.section_id != 'zizhi')",
        )
        orphan_years = _scalar(
            connection,
            f"SELECT COUNT(*) FROM items i LEFT JOIN years y ON y.id = i.year_id "
            f"WHERE {real_filter} AND (i.year_id IS NULL OR y.id IS NULL OR y.volume_id != i.volume_id)",
        )
        volume_count = _scalar(
            connection,
            "SELECT COUNT(*) FROM volumes WHERE section_id = 'zizhi' AND id NOT LIKE 'zizhi-volume-%'",
        )
        year_count = _scalar(
            connection,
            "SELECT COUNT(*) FROM years y JOIN volumes v ON v.id = y.volume_id "
            "WHERE v.section_id = 'zizhi' AND v.id NOT LIKE 'zizhi-volume-%' AND y.id NOT LIKE 'zizhi-year-%'",
        )
        covered_volume_count = _scalar(
            connection,
            f"SELECT COUNT(DISTINCT i.volume_id) FROM items i JOIN volumes v ON v.id = i.volume_id "
            f"WHERE {real_filter} AND v.section_id = 'zizhi'",
        )
        covered_year_count = _scalar(
            connection,
            f"SELECT COUNT(DISTINCT i.year_id) FROM items i JOIN years y ON y.id = i.year_id "
            f"WHERE {real_filter} AND y.volume_id = i.volume_id",
        )
        empty_year_ids = [
            row[0]
            for row in connection.execute(
                "SELECT y.id FROM years y JOIN volumes v ON v.id = y.volume_id "
                "LEFT JOIN items i ON i.year_id = y.id AND i.id LIKE 'zztj-%' AND i.category = '资治通鉴' "
                "WHERE v.section_id = 'zizhi' AND v.id NOT LIKE 'zizhi-volume-%' AND y.id NOT LIKE 'zizhi-year-%' "
                "GROUP BY y.id HAVING COUNT(i.id) = 0 ORDER BY y.sort_order"
            ).fetchall()
        ]
        items = connection.execute(
            f"SELECT id, notes FROM items i WHERE {real_filter} ORDER BY id"
        ).fetchall()

    relation_counts = {key: 0 for key in RELATION_KEYS}
    invalid_notes = 0
    items_with_relations = 0
    for row in items:
        try:
            notes = json.loads(row["notes"] or "{}")
        except (TypeError, json.JSONDecodeError):
            invalid_notes += 1
            continue
        if not isinstance(notes, dict):
            invalid_notes += 1
            continue
        item_has_relation = False
        for key in RELATION_KEYS:
            relations = notes.get(key) or []
            if not isinstance(relations, list):
                invalid_notes += 1
                continue
            relation_counts[key] += len(relations)
            item_has_relation |= bool(relations)
        items_with_relations += int(item_has_relation)

    checkpoint_report = _checkpoint_report(checkpoint) if checkpoint else None
    complete = (
        real_count == expected_items
        and distinct_real_ids == expected_items
        and volume_count == expected_volumes
        and year_count == expected_years
        and not missing_required
        and not orphan_volumes
        and not orphan_years
        and not empty_year_ids
        and not invalid_notes
        and (checkpoint_report is None or (checkpoint_report["failed"] == 0 and checkpoint_report["completed"] == expected_years))
    )
    return {
        "database": str(path),
        "complete": complete,
        "expected": {"items": expected_items, "volumes": expected_volumes, "years": expected_years},
        "actual": {
            "real_items": real_count,
            "distinct_real_ids": distinct_real_ids,
            "volumes": volume_count,
            "years": year_count,
        },
        "coverage": {
            "items": _coverage(real_count, expected_items),
            "volumes_with_content": _coverage(covered_volume_count, expected_volumes),
            "years_with_content": _coverage(covered_year_count, expected_years),
            "empty_years": len(empty_year_ids),
            "empty_year_sample": empty_year_ids[:20],
        },
        "field_integrity": {
            "missing_required": missing_required,
            "orphan_volumes": orphan_volumes,
            "orphan_years": orphan_years,
            "invalid_notes": invalid_notes,
        },
        "relations": {"items_with_relations": items_with_relations, "counts": relation_counts},
        "checkpoint": checkpoint_report,
    }


def _scalar(connection: sqlite3.Connection, query: str) -> int:
    return int(connection.execute(query).fetchone()[0])


def _coverage(actual: int, expected: int) -> dict[str, Any]:
    return {"actual": actual, "expected": expected, "percent": round(actual * 100 / expected, 2) if expected else 100.0}


def _checkpoint_report(path: str | Path) -> dict[str, Any]:
    checkpoint_path = Path(path)
    try:
        payload = json.loads(checkpoint_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return {"path": str(checkpoint_path), "completed": 0, "failed": 0, "error": str(error)}
    completed = payload.get("completed_reign_ids") if isinstance(payload, dict) else None
    failed = payload.get("failed_reign_ids") if isinstance(payload, dict) else None
    return {
        "path": str(checkpoint_path),
        "completed": len(completed) if isinstance(completed, list) else 0,
        "failed": len(failed) if isinstance(failed, list) else 0,
        "updated_at": payload.get("updated_at") if isinstance(payload, dict) else None,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate and report the imported 通鉴 dataset")
    parser.add_argument("--database", default="service/data/dutongjian.db")
    parser.add_argument("--checkpoint", default="service/data/tongjian-progress.json")
    parser.add_argument("--expected-items", type=int, default=DEFAULT_EXPECTED_ITEMS)
    parser.add_argument("--expected-volumes", type=int, default=DEFAULT_EXPECTED_VOLUMES)
    parser.add_argument("--expected-years", type=int, default=DEFAULT_EXPECTED_YEARS)
    parser.add_argument("--strict", action="store_true", help="exit non-zero unless every validation gate passes")
    args = parser.parse_args()
    report = validate_dataset(
        args.database,
        checkpoint=args.checkpoint,
        expected_items=args.expected_items,
        expected_volumes=args.expected_volumes,
        expected_years=args.expected_years,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["complete"] or not args.strict else 1


if __name__ == "__main__":
    raise SystemExit(main())
