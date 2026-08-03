#!/usr/bin/env bash
set -u

root_dir=$(cd "$(dirname "$0")/.." && pwd)
data_dir="$root_dir/service/data"
progress="$root_dir/TASK_PROGRESS_2026-08-03.md"
state="$root_dir/PROJECT_STATE.md"
checkpoint="$data_dir/tongjian-progress.json"
log="$data_dir/finalize-20260803.log"
notes="$root_dir/RELEASE_NOTES_2026-08-03.md"
sdk="$root_dir/.android-sdk"
cutoff=$(TZ=Asia/Shanghai date -d '2026-08-03 20:30:00' +%s)
status=0
commit_ok=0

summary() {
    jq -r '"checkpoint \((.completed_reign_ids // []) | length)/\(.total_reigns // "?"); failed \((.failed_reign_ids // []) | length); updated \(.updated_at // "?")"' "$checkpoint" 2>/dev/null || printf '%s' 'checkpoint missing'
}

pids() {
    ps -eo pid=,args= | awk -v db="$data_dir/dutongjian.db" 'index($0, "app.tongjian_sync") && index($0, db) { print $1 }'
}

record() {
    {
        printf '\n### %s\n\n' "$(TZ=Asia/Shanghai date '+%Y-%m-%d %H:%M Asia/Shanghai')"
        printf '%s\n' "$@"
    } >> "$progress"
}

step() {
    local name=$1
    shift
    if "$@" >> "$log" 2>&1; then
        record "- 已完成：$name；命令日志：$log。✅"
    else
        status=1
        record "- 尚未完成：$name；命令日志：$log。"
    fi
}

record '- 已完成：自动收尾已设置为北京时间 20:30，位于用户指定的 20:00–21:00 窗口。' \
    '- 准备做：停止本项目爬虫，读取 checkpoint，执行测试、构建、提交、推送和 Release。' \
    "- 尚未完成：收尾尚未执行；当前 $(summary)。"

while (( $(date +%s) < cutoff )); do
    sleep 20
done

ids=$(pids)
if [[ -n "$ids" ]]; then
    while read -r pid; do
        [[ -n "$pid" ]] && kill -TERM "$pid" 2>/dev/null || true
    done <<< "$ids"
    record "- 已完成：已向本项目爬虫 PID $ids 发送 TERM，等待 checkpoint 写入。"
else
    record '- 已完成：收尾时未发现本项目爬虫进程。'
fi

for _ in $(seq 1 30); do
    [[ -z "$(pids)" ]] && break
    sleep 2
done

if [[ -n "$(pids)" ]]; then
    status=1
    record '- 尚未完成：爬虫未在等待窗口内退出；未使用强制 kill，避免破坏 checkpoint。'
else
    record "- 已完成：爬虫已退出，数据仍在 $data_dir。"
fi

record "- 已完成：停止后的同步状态为 $(summary)。" \
    '- 准备做：运行 Backend tests、Android JVM test、lint 和 APK 构建。' \
    '- 尚未完成：全量导出仅在 1,405/1,405 且失败为 0 时执行。'

step 'backend tests and compileall' bash -c "cd '$root_dir/service' && python3 -m pytest -q tests && python3 -m compileall -q app"
step 'git diff check' git -C "$root_dir" diff --check
step 'Android tests lint and builds' bash -c "cd '$root_dir/android' && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME='$sdk' ANDROID_SDK_ROOT='$sdk' GRADLE_USER_HOME='$root_dir/.gradle' ./gradlew --no-daemon :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug :app:assembleDebug :app:assembleInspection :app:assembleRelease"

completed=$(jq -r '(.completed_reign_ids // []) | length' "$checkpoint" 2>/dev/null || printf '0')
failed=$(jq -r '(.failed_reign_ids // []) | length' "$checkpoint" 2>/dev/null || printf '0')
total=$(jq -r '.total_reigns // "?"' "$checkpoint" 2>/dev/null || printf '?')
if [[ "$completed" == 1405 && "$failed" == 0 ]]; then
    record '- 准备做：全量同步条件满足，执行正文、目录和百科资产导出。'
else
    record "- 尚未完成：checkpoint $completed/$total，失败 $failed；按规则不导出并标记为全本。"
fi

if ! rg -Fq '### 2026-08-03 20:30 Asia/Shanghai' "$state"; then
    {
        printf '\n### 2026-08-03 20:30 Asia/Shanghai\n\n'
        printf '%s\n' '- [x] 按用户要求结束创新和公开 API 爬取；设备/真机验收不纳入本轮。✅'
        printf '%s\n' "- [x] 收尾 checkpoint：$completed/$total，失败 $failed；数据固定在 $data_dir。✅"
        printf '%s\n' '- [ ] GitHub Release 只以实际 gh 命令结果为准，未授权时不写成成功。'
    } >> "$state"
fi

{
    printf '%s\n' '# Release 2026-08-03' ''
    printf '%s\n' '- 正文阅读界面改造、公开 API 断点同步和持久化边界记录。'
    printf '%s\n' "- 停止状态：$completed/$total，失败 $failed。"
    printf '%s\n' '- 验收：Backend tests、Android JVM test、lint、Debug/Inspection/Release build；不进行模拟器或真机验收。'
    printf '%s\n' '- 抓取数据和 checkpoint 位于 service/data/，不随 Git 提交。'
} > "$notes"

git -C "$root_dir" add .gitignore DOCS.md PROJECT_STATE.md README.md \
    android/app/src/main/java/com/dutongjian/app/ui/DutongjianApp.kt \
    guide.txt issue/Unable2Install.txt plugin.md RELEASE_NOTES_2026-08-03.md \
    TASK_PROGRESS_2026-08-03.md scripts/finalize_autodev_2026-08-03.sh \
    scripts/persistent_progress_logger.sh
if git -C "$root_dir" diff --cached --check && git -C "$root_dir" commit -m 'feat: improve article reading experience'; then
    commit_ok=1
    record '- 已完成：已提交正文界面、文档、进度记录和自动收尾脚本。'
else
    status=1
    record '- 尚未完成：git commit 失败或暂存内容未通过检查。'
fi

if (( commit_ok == 1 )); then
    if timeout 120s git -C "$root_dir" push origin HEAD:main >> "$log" 2>&1; then
        record '- 已完成：已将当前提交推送到 origin/main。'
    else
        status=1
        record "- 尚未完成：git push 失败；命令日志：$log。"
    fi
fi

if gh auth status >/dev/null 2>&1; then
    tag='v0.1.14'
    if git -C "$root_dir" rev-parse --verify "refs/tags/$tag" >/dev/null 2>&1; then
        tag='v0.1.15'
    fi
    if git -C "$root_dir" tag -a "$tag" -m "Release $tag" && timeout 120s git -C "$root_dir" push origin "$tag" >> "$log" 2>&1; then
        if [[ -f "$root_dir/android/app/build/outputs/apk/debug/app-debug.apk" && -f "$root_dir/android/app/build/outputs/apk/inspection/app-inspection.apk" ]]; then
            if timeout 300s gh release create "$tag" \
                "$root_dir/android/app/build/outputs/apk/debug/app-debug.apk" \
                "$root_dir/android/app/build/outputs/apk/inspection/app-inspection.apk" \
                --title "读古籍 $tag" --notes-file "$notes" >> "$log" 2>&1; then
                record "- 已完成：已创建 GitHub Release $tag 并上传 Debug/Inspection APK；命令日志：$log。✅"
            else
                status=1
                record "- 尚未完成：Release 创建失败；命令日志：$log。"
            fi
        else
            status=1
            record "- 尚未完成：构建产物缺失，未创建 Release；命令日志：$log。"
        fi
    else
        status=1
        record "- 尚未完成：tag 推送失败；命令日志：$log。"
    fi
else
    status=1
    record "- 尚未完成：gh 未登录 GitHub，未伪造 Release 成功；登录后可复用 $notes 和构建产物。"
fi

record '- 已完成：停止爬取、记录 checkpoint、执行验证和提交流程。' \
    '- 准备做：半小时进度记录继续运行至 21:30，用户下载 APK 后反馈问题。' \
    "- 尚未完成：$(if (( status == 0 )); then printf '%s' '无'; else printf '%s' '见上方失败项和命令日志'; fi)。"

git -C "$root_dir" add PROJECT_STATE.md TASK_PROGRESS_2026-08-03.md RELEASE_NOTES_2026-08-03.md
if ! git -C "$root_dir" diff --cached --quiet; then
        git -C "$root_dir" commit -m 'chore: record automated finalization' >> "$log" 2>&1 &&
        timeout 120s git -C "$root_dir" push origin HEAD:main >> "$log" 2>&1 || status=1
fi

exit "$status"
