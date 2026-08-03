#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
log_file="$root_dir/TASK_PROGRESS_2026-08-03.md"
checkpoint_file="$root_dir/service/data/tongjian-progress.json"
end_epoch=$(TZ=Asia/Shanghai date -d '2026-08-03 21:30:00' +%s)

task_lines() {
    local marker=$1
    awk -v marker="$marker" '
        /^## 当前任务清单/ { inside = 1; next }
        /^## 路径与边界/ { inside = 0 }
        inside && $0 ~ "^- \\[" marker "\\]" {
            sub(/^- \[[ x]\] /, "")
            print
        }
    ' "$root_dir/PROJECT_STATE.md"
}

progress_summary() {
    if [[ ! -f "$checkpoint_file" ]]; then
        printf '%s\n' 'checkpoint 尚未生成'
        return
    fi
    local completed total failed updated errors
    completed=$(jq -r '(.completed_reign_ids // []) | length' "$checkpoint_file")
    total=$(jq -r '.total_reigns // "?"' "$checkpoint_file")
    failed=$(jq -r '(.failed_reign_ids // []) | length' "$checkpoint_file")
    updated=$(jq -r '.updated_at // "?"' "$checkpoint_file")
    errors=$(jq -r '(.last_errors // {} | to_entries | .[:3] | map("\(.key): \(.value)") | join(" | "))' "$checkpoint_file")
    printf 'checkpoint %s/%s；失败 %s；更新时间 %s' "$completed" "$total" "$failed" "$updated"
    if [[ -n "$errors" ]]; then
        printf '；错误 %s' "$errors"
    fi
}

process_summary() {
    local pid cwd
    pid=$(ps -eo pid=,args= | awk '/app\.tongjian_sync/ { print $1; exit }')
    if [[ -z "$pid" ]]; then
        printf '%s\n' '同步进程未发现'
        return
    fi
    cwd=$(readlink -f "/proc/$pid/cwd" 2>/dev/null || printf '%s' '未知')
    printf '同步进程 PID %s；cwd %s' "$pid" "$cwd"
}

append_update() {
    local heading=$1
    local done next pending
    done=$(task_lines x | head -3 | paste -sd '；' -)
    pending=$(task_lines ' ' | head -3 | paste -sd '；' -)
    next=$(task_lines ' ' | head -1)
    [[ -n "$done" ]] || done='当前任务清单暂无新增完成项'
    [[ -n "$pending" ]] || pending='当前任务清单暂无未完成项'
    [[ -n "$next" ]] || next='检查全量校验和发布收尾状态'
    {
        printf '### %s\n\n' "$heading"
        printf '%s\n' "- 已完成：$done。"
        printf '%s\n' "- 准备做：$next。"
        printf '%s\n' "- 尚未完成：$pending。"
        printf '%s\n' "- 同步状态：$(progress_summary)。"
        printf '%s\n\n' "- 运行位置：$(process_summary)。"
    } >> "$log_file"
}

mkdir -p "$(dirname "$log_file")"
last_heading=$(awk '/^### / { line = substr($0, 5) } END { print line }' "$log_file" 2>/dev/null || true)

while :; do
    now_epoch=$(date +%s)
    if (( now_epoch > end_epoch )); then
        break
    fi
    heading=$(TZ=Asia/Shanghai date '+%Y-%m-%d %H:%M')' Asia/Shanghai'
    minute=$(TZ=Asia/Shanghai date '+%M')
    if [[ "$minute" == '00' || "$minute" == '30' ]] && [[ "$heading" != "$last_heading" ]]; then
        append_update "$heading"
        last_heading=$heading
    fi
    if (( now_epoch >= end_epoch )); then
        break
    fi
    sleep 20
done
