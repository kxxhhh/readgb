#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
data_dir="$root_dir/service/data"
database="$data_dir/dutongjian.db"
checkpoint="$data_dir/tongjian-progress.json"
lock_path="$data_dir/tongjian-sync.lock"
assets_dir="$root_dir/android/app/src/main/assets"
python_bin="$root_dir/.venv/bin/python"

if [[ ! -x "$python_bin" ]]; then
    python_bin=$(command -v python3 || true)
fi
if [[ -z "$python_bin" || ! -x "$python_bin" ]]; then
    printf 'no usable Python interpreter found\n' >&2
    exit 1
fi
for tool in flock sqlite3 tar sha256sum; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'required command not found: %s\n' "$tool" >&2
        exit 1
    fi
done
if [[ ! -f "$database" || ! -f "$checkpoint" ]]; then
    printf 'database or checkpoint is missing under %s\n' "$data_dir" >&2
    exit 1
fi

mkdir -p "$data_dir"
exec 9>"$lock_path"
if ! flock -n 9; then
    printf 'crawler is still running; refusing finalization\n' >&2
    exit 2
fi

cd "$root_dir"
PYTHONPATH=service "$python_bin" -m app.validate_tongjian \
    --database "$database" \
    --checkpoint "$checkpoint" \
    --strict

assets_stage=$(mktemp -d "$assets_dir/.finalize-assets.XXXXXX")
cleanup_assets_stage() {
    rm -rf "$assets_stage"
}
trap cleanup_assets_stage EXIT

PYTHONPATH=service "$python_bin" -m app.export_android \
    --database "$database" \
    --checkpoint "$checkpoint" \
    --output "$assets_stage/offline_content.ndjson.gz" \
    --catalog-output "$assets_stage/offline_catalog.json" \
    --knowledge-output "$assets_stage/offline_knowledge.json" \
    --expected-count 30989 \
    --expected-volumes 294 \
    --expected-years 1405

PYTHONPATH=service "$python_bin" - "$assets_stage" <<'PY'
import gzip
import json
import sys
from pathlib import Path

assets = Path(sys.argv[1])
content = assets / "offline_content.ndjson.gz"
catalog = assets / "offline_catalog.json"
knowledge = assets / "offline_knowledge.json"

with gzip.open(content, "rt", encoding="utf-8") as stream:
    content_count = sum(1 for line in stream if line.strip())
catalog_payload = json.loads(catalog.read_text(encoding="utf-8"))
knowledge_payload = json.loads(knowledge.read_text(encoding="utf-8"))
if content_count != 30989:
    raise SystemExit(f"unexpected offline content count: {content_count}")
if len(catalog_payload.get("volumes", [])) != 294 or len(catalog_payload.get("years", [])) != 1405:
    raise SystemExit("unexpected offline catalog hierarchy")
categories = {entry.get("category") for entry in knowledge_payload}
required = {"人物", "地点", "官职", "主题", "决策"}
if not required.issubset(categories):
    raise SystemExit(f"offline knowledge is missing categories: {sorted(required - categories)}")
print({"content": content_count, "volumes": len(catalog_payload["volumes"]), "years": len(catalog_payload["years"]), "knowledge": len(knowledge_payload), "categories": sorted(categories)})
PY

mv "$assets_stage/offline_content.ndjson.gz" "$assets_dir/offline_content.ndjson.gz"
mv "$assets_stage/offline_catalog.json" "$assets_dir/offline_catalog.json"
mv "$assets_stage/offline_knowledge.json" "$assets_dir/offline_knowledge.json"
trap - EXIT
rm -rf "$assets_stage"

stage_dir=$(mktemp -d "$root_dir/../tongjian-final-snapshot.XXXXXX")
trap 'rm -rf "$stage_dir"' EXIT
sqlite3 "$database" ".backup '$stage_dir/dutongjian.db'"
cp "$checkpoint" "$stage_dir/tongjian-progress.json"
cp -a "$data_dir/tongjian-cache" "$stage_dir/tongjian-cache"
tar -czf "$data_dir/tongjian-snapshot-latest.tar.gz" -C "$stage_dir" dutongjian.db tongjian-progress.json
tar -czf "$data_dir/tongjian-cache-snapshot-latest.tar.gz" -C "$stage_dir" tongjian-cache
sha256sum \
    "$data_dir/tongjian-snapshot-latest.tar.gz" \
    "$data_dir/tongjian-cache-snapshot-latest.tar.gz" \
    > "$data_dir/tongjian-snapshot-latest.sha256"
printf 'final snapshot: %s\n' "$data_dir/tongjian-snapshot-latest.tar.gz"
printf 'cache snapshot: %s\n' "$data_dir/tongjian-cache-snapshot-latest.tar.gz"
cat "$data_dir/tongjian-snapshot-latest.sha256"
