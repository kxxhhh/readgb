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

规则：任务完成行末加 ✅；未完成项保留 [ ] 并写下一步；机器意外关闭后复用原 checkpoint/cache，不启动重复爬虫。完整当前记录见 PROJECT_STATE.md。
