# 开发日志（已与项目状态合并）

本文件按要求创建于 2026-08-03。开发日志已经与项目清单合并到 PROJECT_STATE.md，以后只在那里追加任务、恢复记录和验证结果，避免两份日志互相过期。

当前恢复入口：

~~~bash
cd /mnt/workspace/readgb
sed -n '1,260p' PROJECT_STATE.md
git status --short
ps -eo pid,lstart,etime,pcpu,pmem,args | rg 'app.tongjian_sync|resume_crawler|readgb-crawler' | rg -v 'rg '
jq '{total_reigns, completed: (.completed_reign_ids | length), failed: (.failed_reign_ids // [] | length), updated_at}' service/data/tongjian-progress.json
~~~

规则：任务清单使用 `- [ ]` / `- [x]`，完成项同一行末加 ✅，未完成项写下一步或阻塞原因；变更日志使用 `### YYYY-MM-DD HH:MM Asia/Shanghai`，每项记录事项、命令、实际结果/数量、路径和恢复方式；机器意外关闭后复用原 checkpoint/cache，不启动重复爬虫。完整当前记录见 PROJECT_STATE.md。

格式模板：

```text
- [ ] 目标：...；路径：`...`；恢复：`...`；下一步：...
- [x] 目标：...；结果：...；路径：`...`；恢复：...。✅

### YYYY-MM-DD HH:MM Asia/Shanghai
- [x] 事项：...；命令：`...`；结果：...；路径：`...`；恢复：...。✅
```
