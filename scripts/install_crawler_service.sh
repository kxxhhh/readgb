#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
template="$root_dir/deploy/readgb-crawler.service"
target=/etc/systemd/system/readgb-crawler.service

if [[ ! -x "$root_dir/.venv/bin/python" ]]; then
    printf 'missing virtualenv interpreter: %s\n' "$root_dir/.venv/bin/python" >&2
    exit 1
fi

mkdir -p /etc/systemd/system
sed "s#@ROOT_DIR@#$root_dir#g" "$template" > "$target"
chmod 0644 "$target"

if systemctl daemon-reload >/dev/null 2>&1; then
    systemctl enable readgb-crawler.service >/dev/null
    if ! systemctl is-active --quiet readgb-crawler.service; then
        systemctl start readgb-crawler.service
    fi
    printf 'readgb-crawler.service enabled and started\n'
else
    # Containers without systemd as PID 1 can leave the enablement link in
    # place; the host systemd instance will load the unit on the next boot.
    wants=/etc/systemd/system/multi-user.target.wants
    mkdir -p "$wants"
    ln -sfn "$target" "$wants/readgb-crawler.service"
    printf 'unit installed and enabled for the next systemd boot\n'
fi
