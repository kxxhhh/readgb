# 读通鉴当前任务清单与恢复日志

更新时间：2026-08-03（Asia/Shanghai）

本文件是项目的单一状态源，同时承担项目清单、断点恢复记录和开发日志。PROJECT_STATE_HISTORY.md 只保留旧历史，不作为当前事实来源；DEVELOPMENT_LOG.md 是本文件的合并入口说明，不再单独维护第二套日志。

## 恢复入口

工作目录：/mnt/workspace/readgb

机器重启、会话中断或准备继续开发时，按下面顺序执行：

~~~bash
cd /mnt/workspace/readgb
sed -n '1,260p' PROJECT_STATE.md
git status --short
ps -eo pid,lstart,etime,pcpu,pmem,args | rg 'app.tongjian_sync|resume_crawler|readgb-crawler' | rg -v 'rg '
jq '{total_reigns, completed: (.completed_reign_ids | length), failed: (.failed_reign_ids // [] | length), updated_at}' service/data/tongjian-progress.json
~~~

如果没有同步进程，使用 ./scripts/resume_crawler.sh 续跑。脚本通过 service/data/tongjian-sync.lock 防止重复启动，不带 --reset；已有进程持锁时不要再启动第二个任务。不要删除数据库、cache 或 checkpoint。

## 当前事实快照

- [x] 已确认项目为 Kotlin/Compose Android 客户端 + Python/FastAPI 数据服务，App 运行时不请求原网站。✅
- [x] 公开同步目标已确认：294 卷、1,405 个纪年节点、30,989 条正文；公开入口为 /api/table_of_contents 和 /api/reign。✅
- [x] 旧导入内容清理前快照保存在 service/data/resync-archive-20260803/；新同步数据只写入 service/data/。✅
- [x] 同步器使用受控多线程：4 个 worker、全局请求节奏、robots 检查、缓存、Retry-After、退避、去重和原子 checkpoint。✅
- [x] 当前唯一同步进程快照：PID 143358，4 worker，5 秒请求间隔，checkpoint 74/1405，失败 2；数据库 232 条真实 zztj-* 正文、覆盖 74 个纪年。快照时间约为 2026-08-03 09:32。✅
- [ ] 同步仍未完成，当前约 5.27%，不能把数据库或 App 描述为全本。下一步：保留当前进程，持续观察 checkpoint；2 个 HTTP 429 失败项已写入 checkpoint，后续复用 cache/checkpoint 重试。
- [ ] 新的正文、目录、百科 Android assets 尚未由本轮全量同步导出；导出前必须通过 30,989/294/1,405 完整性校验。
- [ ] Android 图表代码已改为按纪年/纪·朝代覆盖全部分组，并把人物图谱改为全人物索引 + 中心人物下钻；下一步是 Kotlin 编译、测试和设备/模拟器检查。

### 当前同步速度判断

2026-08-03 08:21 至 08:57 的旧缓存写入为 41 个纪年，跨度约 2,206 秒，平均约 55.2 秒/纪年；这段期间进程随后停止，不能作为稳定吞吐。恢复后从约 9/1405 推进到 74/1405；已有 cache 命中阶段约 25 秒推进到 34/1405，之后使用 5 秒请求间隔并受站点 Retry-After 影响，速度仍未稳定。当前有 2 个 HTTP 429 失败项，已写入 checkpoint；4 worker 不用于绕过限流。

## 当前任务清单

### 阅读界面与研读图表

- [x] 定位原有问题：柱状图按 dynasty 合并且硬截 18 个分组，人物图谱硬截 16 人/30 条关系并使用固定网格。✅
- [x] 柱状图按 纪年 或 纪/朝代 切换，柱高使用真实篇目数，支持全部实际分组横向浏览和点选下钻。✅
- [x] 人物关系从 notes 的结构化人物字段动态聚合；人物索引不再只保留 16 人，支持选择中心人物查看共现网络和正文。✅
- [x] 运行 :app:compileDebugKotlin、单元测试和 lint；Android testDebugUnitTest、lintDebug、assembleDebug、assembleRelease 全部 BUILD SUCCESSFUL。✅
- [ ] 在有代表性的内容规模下检查图表滚动、文字截断、节点重叠和点选回正文。

### 多线程同步

- [x] TongjianSync 使用 ThreadPoolExecutor 并发获取未完成纪年，主线程顺序入库并逐纪年写 checkpoint。✅
- [x] 单个纪年的网络、解析或入库异常会写入 failed_reign_ids/last_errors，不会中断其他已完成 future。✅
- [x] scripts/resume_crawler.sh 固定使用 4 worker、5 秒最小请求间隔和 robots 检查；当前 PID 143358 已使用新配置运行。✅
- [ ] 观察恢复后的稳定吞吐和失败率；若出现 429，只遵守 Retry-After，不盲目增加并发。
- [ ] 完成 1,405 个纪年后执行全量正文、目录、关联字段校验并导出 Android assets。

### 构建与验收

- [x] Backend 测试 21 passed、compileall 和 git diff --check。✅
- [x] Android testDebugUnitTest、compileDebugKotlin、lintDebug、Debug/Release 构建全部通过；构建耗时 1 分 36 秒。✅
- [ ] 模拟器/真机检查研读页：纪年柱状图全量滚动、人物索引、关系下钻和空数据态。
- [ ] 记录 APK、数据资产数量、测试命令和任何失败；不把未实际运行的结果写成完成。

### 其他未完成能力

- [ ] 全量百科资产、全本图表性能和关系准确性验收。
- [ ] 真实设备上的 TTS 音频、Widget、Edge-TTS、AI 网络调用和正式签名发布验收。
- [ ] AI 语法拆解、反事实推演、人物角色对话和战役沙盘的完整实现。

## 路径与边界

~~~text
service/data/dutongjian.db              当前同步数据库
service/data/tongjian-cache/            原始 JSON cache
service/data/tongjian-progress.json     断点和失败记录
service/data/tongjian-sync.lock         防重复锁
service/data/resync-archive-20260803/   清理前可恢复审计快照
android/app/src/main/assets/            校验后的 APK 资产
~~~

- 新抓取内容不得放入 /tmp；.tmp 只用于同一目标目录的原子替换。
- 不登录、不绕过验证码、访问控制、robots 或付费墙。
- 不通过并发绕过站点限流；缓存命中不发网络请求。
- 阶段性快照必须标明 checkpoint，不得称为全本。

## 变更日志

### 2026-08-03

- [x] 核对真实同步状态：进程曾停止，checkpoint 为 9/1405，数据库为 47 条真实正文；缓存数量不作为已入库数量。✅
- [x] 恢复唯一多线程同步进程；最新观察到 74/1405、232 条真实正文、失败 2；当前进程按 5 秒请求间隔运行。✅
- [x] 将研读柱状图从少量朝代示例改为纪年/纪·朝代全量分组，并增加点选下钻。✅
- [x] 将人物图谱从固定节点改为结构化人物全索引、中心人物关系网络和正文入口。✅
- [x] 把“每轮先读状态、任务行末加 ✅、中断可恢复、日志与项目合并”的要求写入 README.md、DOCS.md、guide.txt 和本文件。✅
- [x] 完成 Kotlin/Backend 构建和运行验证：Backend 21 passed；Android test/lint/Debug/Release BUILD SUCCESSFUL。✅
- [x] 功能/文档提交 fca5c11 和状态提交 b35ed9a 已推送 origin/main。✅

## 记录协议

每次开发必须执行以下规则：

1. 开始前先读本文件，再执行 git status --short。
2. 开始工作时在“当前任务清单”写目标、涉及路径和恢复方式。
3. 完成一项就在任务行末加 ✅；未完成项保持 [ ]，并写明下一步或阻塞原因。
4. 爬虫、构建、测试、部署、数据路径或文档有变化时，在本文件“变更日志”追加时间、命令、结果和实际数量。
5. 意外中断后先检查进程和 checkpoint，复用同一 cache/checkpoint；确认没有进程时才运行恢复脚本。
6. 不把 PID、速度、数量、构建或测试结果写入 README 等稳定文档；这些事实只更新本文件。
