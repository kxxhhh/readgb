#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
cd "$project_dir"

exec 9>"$project_dir/service/data/extended-sync.lock"
if ! flock -n 9; then
    echo "extended sync is already running" >&2
    exit 2
fi

PYTHONPATH="$project_dir/service" "$project_dir/.venv/bin/python" -m app.extended_sync \
    --database "$project_dir/service/data/dutongjian.db" \
    --cache "$project_dir/service/data/extended-cache" \
    --progress "$project_dir/service/data/extended-progress.json" \
    --min-interval "${EXTENDED_MIN_INTERVAL:-5}"
