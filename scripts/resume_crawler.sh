#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
data_dir="$root_dir/service/data"
lock_path="$data_dir/tongjian-sync.lock"

for argument in "$@"; do
    if [[ "$argument" == "--reset" ]]; then
        printf '%s\n' 'resume_crawler.sh refuses --reset; use the documented clean-sync command explicitly.' >&2
        exit 2
    fi
done

mkdir -p "$data_dir"
if command -v flock >/dev/null 2>&1; then
    exec 9>"$lock_path"
    if ! flock -n 9; then
        printf 'crawler already owns %s; leaving the existing process running\n' "$lock_path"
        exit 0
    fi
fi

python_bin="$root_dir/.venv/bin/python"
if [[ ! -x "$python_bin" ]]; then
    python_bin=$(command -v python3 || true)
fi
if [[ -z "$python_bin" || ! -x "$python_bin" ]]; then
    printf 'no usable Python interpreter found under %s or PATH\n' "$root_dir/.venv/bin/python" >&2
    exit 1
fi

cd "$root_dir"
exec env PYTHONPATH="$root_dir/service" PYTHONUNBUFFERED=1 "$python_bin" -m app.tongjian_sync \
    --allow-public-api \
    --database "$data_dir/dutongjian.db" \
    --cache-dir "$data_dir/tongjian-cache" \
    --checkpoint "$data_dir/tongjian-progress.json" \
    --workers 4 \
    --min-interval 5.0 \
    --respect-robots \
    "$@"
