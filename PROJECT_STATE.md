# 读通鉴项目任务清单与恢复日志

更新时间：2026-08-03 08:55（Asia/Shanghai）

本文件是当前唯一活动状态源，同时承担任务清单、断点恢复说明和变更日志。旧版长状态已保存为 PROJECT_STATE_HISTORY.md，只用于追溯，不代表当前事实。

## 每次开发必须遵守

- [x] 开始前读取本文件，再执行 git status --short。✅
- [x] 任务、爬虫、构建、部署和文档变更都记录在本文件，不另建并行活动日志。✅
- [x] 完成项使用 [x]，并在行末追加 ✅；未完成项保持 [ ]，写明下一步或阻塞原因。✅
- [x] 新抓取内容、数据库、cache、checkpoint 和审计快照都放在项目目录，不使用 /tmp 作为内容目录。✅
- [x] 意外中断后复用同一数据库、cache 和 checkpoint，不删除数据、不启动重复爬虫。✅
- [ ] 每次本轮任务结束后提交、推送，并在本文件记录 commit 和远端分支。

## 项目目标

- [x] 原生 Kotlin Android App：Compose、Material 3、MVVM、StateFlow、Repository、Room、Hilt。✅
- [x] 首页、目录、搜索、详情、收藏、历史、笔记、百科和本地阅读工具。✅
- [x] Python/FastAPI 数据服务和统一 code/message/data REST API。✅
- [x] 公开内容同步器：robots、同源、限速、缓存、Retry-After、退避、去重和 checkpoint。✅
- [ ] 完成公开《资治通鉴》全量同步，校验 294 卷、1,405 纪年和 30,989 条正文。
- [ ] 将校验后的正文、目录和百科资产导入 Android，重新构建 APK。
- [ ] 完成真实 Android 设备上的安装、阅读、TTS、Widget、图表和 AI 回归。

## 当前任务清单

### 数据清理和同步

- [x] 停止旧同步进程，避免旧任务继续修改数据库。✅
- [x] 删除旧导入的资治通鉴正文、资治通鉴目录和关联百科记录。✅
- [x] 将清理前的数据库、旧 Android assets、旧 cache/checkpoint 归档到 service/data/resync-archive-20260803/。✅
- [x] 新同步直接使用 service/data/dutongjian.db、service/data/tongjian-cache/ 和 service/data/tongjian-progress.json。✅
- [x] 同步器增加 4 个有界 worker、全局请求节奏和共享 Retry-After 冷却。✅
- [x] 单纪年失败时继续收集其他任务，并把 failed_reign_ids/last_errors 写入 checkpoint。✅
- [ ] 从当前 checkpoint 56/1405 继续同步剩余 1,349 个纪年。
- [ ] 校验 30,989 条正文的 ID、原文、简体、译文、关联字段、卷和纪年层级。
- [ ] 同步完成后清理残留演示 seed，并生成最终 Android assets。

### Android 离线内容

- [x] Room 导入前完整解析资产，解析失败不删除已有本地内容。✅
- [x] 新资产导入前删除旧 zztj-* 和 zizhi-tongjian-* 内容，同时保留收藏和最近阅读时间。✅
- [x] 添加 classical_char_map.json 和 classical_glossary.json，并在 App 启动时加载。✅
- [ ] 重新生成并放入项目 android/app/src/main/assets/offline_content.ndjson.gz、offline_catalog.json、offline_knowledge.json。
- [ ] 安装新 APK 后确认 Room 实际正文数、目录数和百科分类数。

### 阅读体验和功能

- [x] 重构详情页：顶部元信息、正文工作区、原文/译文模式、底部工具面板。✅
- [x] 加入字号、复制、分享、收藏、划线笔记、历史上下文和字词提示。✅
- [x] 加入展示层繁简/异体字转换，不改写数据库原文。✅
- [x] 接入系统 TTS、当前句状态、自动滚动和睡眠计时器代码。✅
- [x] 将学习柱状图、趋势图和人物关系图改为动态数据聚合，并支持文章下钻。✅
- [ ] 在全本数据规模检查关系图和柱状图覆盖的人物、时期、数量和性能。
- [ ] 在真实设备检查 TTS 音频、Widget、Edge-TTS 和 AI 配置。
- [ ] 实现 plugin.md 中尚未完成的语法拆解、反事实推演、人物角色对话和战役沙盘。

### 开机恢复和工程化

- [x] 新增 scripts/resume_crawler.sh，复用项目内路径，拒绝 --reset，并用 tongjian-sync.lock 防止重复进程。✅
- [x] deploy/readgb-crawler.service 的 ExecStart 指向恢复脚本，失败自动重启。✅
- [x] 运行 scripts/install_crawler_service.sh；当前容器没有 systemd PID 1，但 unit 和 enable 链接已写入，宿主机启动时接管。✅
- [x] 将 README.md、DOCS.md、plugin.md、guide.txt 和 docs/site-analysis.md 按当前实现重写。✅
- [x] 将本文件和旧状态归档合并为“任务清单 + 恢复日志”方案。✅
- [x] 本轮功能、文档和部署变更已提交并推送到 origin/main，commit fca5c11。✅

## 当前同步快照

记录时间：2026-08-03 09:16

- 同步进程：scripts/resume_crawler.sh 正在运行。
- checkpoint：56/1405，failed_reign_ids 为 2。
- 新正文：188 条 zztj-*。
- 原始纪年 cache：58 个 JSON。
- 最近 checkpoint 更新时间：2026-08-03 09:15:20 +0800。
- 上一轮实际表现：约 30 分钟没有 checkpoint 增长；缓存仍每分钟变化，进程线程处于 Retry-After/网络等待，不能算加速。
- 原因判断：4 个 worker 同时遭遇服务端 429/Retry-After，旧实现对 future 异常处理不足，可能在等待线程池收尾时使 checkpoint 停止。
- 修复状态：已加入共享服务端退避窗口、逐任务异常记录和非零失败退出；缓存阶段已从 9/1405 快速推进到 34/1405，未缓存部分遵守站点退避。当前 2 个纪年因 HTTP 429 失败，已写入 checkpoint，下一轮会重试。

## 数据路径约定

| 路径 | 用途 | 状态 |
| --- | --- | --- |
| service/data/dutongjian.db | 当前同步 SQLite | 项目内，运行时文件 |
| service/data/tongjian-cache/ | 原始公开 API JSON | 项目内，运行时文件 |
| service/data/tongjian-progress.json | checkpoint 和失败记录 | 项目内，运行时文件 |
| service/data/resync-archive-20260803/ | 清理前可恢复审计快照 | 项目内，保留 |
| android/app/src/main/assets/ | 通过校验后进入 APK 的最终资源 | 当前仅字形/字词资源 |

/tmp 不承载新的正文、数据库、cache 或 checkpoint。同步器写入的 .tmp 只是目标文件同目录内的原子替换中间文件。

## 意外关闭恢复

先检查是否已有进程：

~~~bash
cd /mnt/workspace/readgb
sed -n '1,240p' PROJECT_STATE.md
git status --short
ps -eo pid,etime,cmd | rg 'tongjian_sync|readgb-crawler' | rg -v 'rg '
jq '{total_reigns, completed: (.completed_reign_ids | length), failed: (.failed_reign_ids | length), updated_at}' \
  service/data/tongjian-progress.json
~~~

确认没有同步进程后正常恢复：

~~~bash
./scripts/resume_crawler.sh
~~~

恢复脚本固定使用：

~~~text
PYTHONPATH=service
service/data/dutongjian.db
service/data/tongjian-cache/
service/data/tongjian-progress.json
workers=4
min_interval=0.5
respect_robots=true
~~~

脚本不接受 --reset。已有进程持有 service/data/tongjian-sync.lock 时，脚本会退出而不启动第二个爬虫。只有明确需要重新清空全部通鉴内容时，才在确认进程停止后手动执行带 --reset 的同步命令。

开机服务安装：

~~~bash
./scripts/install_crawler_service.sh
~~~

## 已验证结果

- [x] python3 -m pytest -q service/tests：21 passed（2026-08-03）。✅
- [x] python3 -m compileall -q service/app：通过（2026-08-03）。✅
- [x] bash -n scripts/resume_crawler.sh scripts/install_crawler_service.sh：通过。✅
- [x] Android testDebugUnitTest：此前通过，当前文档/服务端修复未改变 Android 编译输入。✅
- [x] Android :app:compileDebugKotlin：此前通过，当前文档/服务端修复未改变 Android 编译输入。✅
- [x] 当前改动后的 lintDebug、assembleDebug、assembleRelease：BUILD SUCCESSFUL，111 actionable tasks，1 分 36 秒。✅
- [ ] 无连接设备，尚未完成安装、TTS 音频、Widget 和真实 UI 回归。

## 变更日志

### 2026-08-03 08:55

- 重写所有活动开发文档：README.md、DOCS.md、plugin.md、guide.txt、docs/site-analysis.md。✅
- 把任务日志和项目状态合并到本文件；旧版保存为 PROJECT_STATE_HISTORY.md。✅
- 新增 scripts/resume_crawler.sh，并让 systemd unit 通过它恢复；增加项目内 flock 锁。✅
- 修复同步器共享 Retry-After 冷却和 future 异常处理。✅
- 待完成：继续同步、全量校验、资产导出、提交和推送。

### 2026-08-03 09:10

- 使用 scripts/resume_crawler.sh 从 9/1405 恢复；缓存阶段约 25 秒推进到 34/1405，随后继续按站点退避请求。✅
- 当前 checkpoint 42/1405，143 条正文，53 个 cache，0 个失败；同步进程保持运行。✅
- Android testDebugUnitTest、lintDebug、assembleDebug、assembleRelease 全部通过。✅

### 2026-08-03 09:16

- 同步继续到 56/1405、188 条正文、58 个 cache；2 个纪年返回 HTTP 429，失败 ID 和错误已写入 checkpoint，未丢失已完成数据。✅
- 功能与文档提交 fca5c11 已推送 origin/main；本状态补记待随后提交。✅

### 2026-08-03 08:24

- 观察到 checkpoint 9/1405、47 条新正文、38 个 cache；旧进程因服务端退避实际停滞。
- 安全停止旧进程，保留数据库、cache、checkpoint 和项目内审计归档。
