# 读通鉴当前任务清单与恢复日志

更新时间：2026-08-03 21:29（Asia/Shanghai）

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
- [x] 本轮从当前项目内空数据状态启动后已转为独立 session 续跑；22:07 观察到 checkpoint `1216/1405`、失败 `0`、数据库 `23,704` 条真实 `zztj-*` 正文。运行使用 4 worker、5 秒请求间隔和 robots 检查。✅
- [ ] 同步仍未完成，当前 checkpoint `86.55%`，不能把数据库或 App 描述为全本。下一步：保持唯一后台进程运行，复用 `service/data/tongjian-progress.json`、`service/data/tongjian-cache/` 和 `service/data/dutongjian.db` 执行 `./scripts/resume_crawler.sh`；不使用 `--reset`。
- [ ] 新的正文、目录、百科 Android assets 尚未由本轮全量同步导出；导出前必须通过 `service/app/validate_tongjian.py --strict` 的 `30,989/294/1,405` 完整性校验。
- [x] Android 图表代码已改为按纪年/纪·朝代覆盖全部分组，并把人物图谱改为全人物索引 + 中心人物下钻；Kotlin 编译、测试和 lint 已通过。✅
- [x] 研读页新增正文/纪年/卷三项真实 `zztj-*` 覆盖率卡片，不把 OfflineSeed 计入数据覆盖。✅

### 当前同步速度判断

本轮于 2026-08-03 14:57 左右从空 checkpoint 启动；22:07 观察到 `1216/1405` 个纪年、`23,704` 条真实正文、失败 `0`。当前阶段没有新的失败，仍继续使用 5 秒请求间隔、4 worker 和服务端 Retry-After；4 worker 不用于绕过限流。该结论只表示已观察窗口，不代表同步已完成。

## 当前任务清单

### 本轮完成与后续同步清单（2026-08-03 22:07 Asia/Shanghai）

已完成并已 push：

- [x] 逐份阅读 Git 跟踪的 Markdown/TXT 文档、历史归档和项目要求，区分当前事实、阶段性数据与未完成项。✅
- [x] 配置并验证 Python 虚拟环境、OpenJDK 21、Gradle、Android SDK/API 35、Build Tools、platform-tools；记录无 KVM/VMX/SVM 的设备限制。✅
- [x] 恢复公开 API 断点爬虫，固定项目内数据库/cache/checkpoint 路径，启用 robots、Retry-After、5 秒全局节奏、4 worker、去重和锁；阶段性内容快照已推送。✅
- [x] 增加 `validate_tongjian` 严格数据报告和研读覆盖率卡片；Backend/Android 测试、lint 和 Debug 构建已通过。✅
- [x] 增加 Release 签名与 gh release workflow 骨架；没有生产 keystore，因此未宣称真实签名发布完成。✅
- [x] 增加 AI 结果 Room 持久化、迁移、保存/删除/重开和 JVM 测试；覆盖反事实/角色等任务的结果记录。✅
- [x] 增加百科导出五类关联、非空字段、去重闸门，以及 `scripts/finalize_tongjian.sh` 全量收尾流程；持锁拒绝并发已验证。✅

正在进行：

- [ ] 公开 API 全量同步：当前 `1216/1405`、真实正文 `23704`、失败 `0`，PID `9878`；恢复：保持唯一进程运行并复用原 checkpoint/cache。

接下来按顺序执行：

- [ ] 原进程结束后运行最新版 `./scripts/resume_crawler.sh`，复核已完成但无真实正文的纪年。
- [ ] 运行 `validate_tongjian --strict`，确认 `30989/294/1405`、零空纪年、零字段/层级/关联错误。
- [ ] 运行 `./scripts/finalize_tongjian.sh`，生成并核对 `offline_content.ndjson.gz`、`offline_catalog.json`、`offline_knowledge.json` 和最终压缩快照。
- [ ] 用全量 assets 重跑 Android JVM test、lint、Debug/Inspection/Release 构建，核对资产数量和 SHA-256。
- [ ] 更新当前数量、快照哈希和 APK 产物记录，提交并 push 全量资产与爬取快照。

仍未完成或有外部阻塞：

- [ ] 全本规模图表滚动、文字截断、节点重叠、关系准确性、搜索性能和真实设备验收；当前容器无 KVM/实体设备。
- [ ] AI 兼容服务真实请求/错误/超时验收，语法选中文本高亮，反事实结构化上下文，历史角色事实约束/多轮会话。
- [ ] 历史上的今天可信月日映射、Edge-TTS 真实端点音频、战役地图/兵力/粮道沙盘；缺少可信数据或端点。
- [ ] Codespace/Actions 生产 keystore 签名和 gh release 演练；当前只有 gh 登录，没有生产签名凭据。

### 阅读界面与研读图表

- [x] 定位原有问题：柱状图按 dynasty 合并且硬截 18 个分组，人物图谱硬截 16 人/30 条关系并使用固定网格。✅
- [x] 柱状图按 纪年 或 纪/朝代 切换，柱高使用真实篇目数，支持全部实际分组横向浏览和点选下钻。✅
- [x] 人物关系从 notes 的结构化人物字段动态聚合；人物索引不再只保留 16 人，支持选择中心人物查看共现网络和正文。✅
- [x] 运行 `:app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug :app:assembleDebug :app:assembleInspection :app:assembleRelease`；全部 BUILD SUCCESSFUL，耗时 23 分 54 秒。✅
- [ ] 在有代表性的内容规模下检查图表滚动、文字截断、节点重叠和点选回正文；下一步：全量资产导入后运行 Android UI/模拟器检查，当前容器没有 KVM，实体设备仍未提供。

### 多线程同步

- [x] 本轮目标：从当前项目内空数据状态启动公开 API 同步；路径：`service/data/dutongjian.db`、`service/data/tongjian-cache/`、`service/data/tongjian-progress.json`；恢复：持续使用 `./scripts/resume_crawler.sh`，不使用 `--reset`；已确认目录为 1,405/294，当前持续抓取。✅
- [x] 已记录宿主机持久化边界：只保证 `/mnt/workspace` 内的数据可持续保留，抓取数据库、cache、checkpoint、锁和日志固定使用 `/mnt/workspace/readgb/service/data/`；启动前核对绝对路径，不使用 `/tmp` 或其他目录。✅
- [x] TongjianSync 使用 ThreadPoolExecutor 并发获取未完成纪年，主线程顺序入库并逐纪年写 checkpoint。✅
- [x] 单个纪年的网络、解析或入库异常会写入 failed_reign_ids/last_errors，不会中断其他已完成 future。✅
- [x] scripts/resume_crawler.sh 固定使用 4 worker、5 秒最小请求间隔和 robots 检查；当前 PID 9878 持有同步锁运行，源代码已增加 1,405/294 规模门槛。✅
- [ ] 继续观察恢复后的稳定吞吐和失败率；当前已观察到 `1216` 个纪年、失败 `0`，仍需保持到任务结束；若出现 429，只遵守 Retry-After，不盲目增加并发。
- [ ] 完成 1,405 个纪年后执行全量正文、目录、关联字段校验并导出 Android assets。

### 数据校验与覆盖看板

- [x] 增加只读数据集校验器，检查真实正文数量、唯一 ID、必填字段、卷/纪年外键、空纪年、关联 JSON 和 checkpoint；路径：`service/app/validate_tongjian.py`；测试：`service/tests/test_validate_tongjian.py`。✅
- [x] 研读页增加正文/纪年/卷覆盖率卡片，明确排除 OfflineSeed；路径：`android/app/src/main/java/com/dutongjian/app/ui/StudyCoverage.kt`、`StudyScreen.kt`；测试：`StudyCoverageTest`。✅
- [ ] 同步结束后用校验器严格通过 `30,989` 条正文、`294` 卷、`1,405` 纪年、零空纪年和零字段/关联错误；下一步：爬虫结束后执行 `PYTHONPATH=service .venv/bin/python -m app.validate_tongjian --strict`。

### 构建与验收

- [x] Backend 测试 27 passed、compileall、shell 语法检查和 git diff --check。✅
- [x] 本轮 Android `:app:assembleDebug` 和 `:app:testDebugUnitTest` 均 BUILD SUCCESSFUL；Debug APK 约 20M。✅
- [x] Android testDebugUnitTest、compileDebugKotlin、lintDebug、Debug/Inspection/Release 构建全部通过；Debug APK 20M，Inspection APK 13M，未签名 Release APK 13M。✅
- [x] 设备/真机验收不纳入本轮交付；本轮以源码、JVM test、lint、APK 编译和发布产物为验收依据。✅
- [x] 已记录 APK、数据规模、测试命令和实际警告；未进行模拟器或真机验收。✅

### 其他未完成能力

- [ ] 全量百科资产：人物、地点、官职、专题、决策关联需在完整正文后导出；`export_android` 已强制检查五类分类、非空字段和去重，下一步：运行 `scripts/finalize_tongjian.sh` 并复核 `offline_knowledge.json`。
- [ ] 典章制度、经济史专题的全量结构化标注；当前只有 tags/关联字段和关键词筛选，下一步：依据公开字段建立可追溯的主题分类规则并补测试。
- [ ] 全本图表性能、关系准确性和人物/时期覆盖验收；下一步：全量资产导入后执行图表聚合基准和 Android UI 检查。
- [ ] “历史上的今天”完整历法匹配和自动刷新；当前 `今日金句` 只是按 day-of-year 轮换条目，缺少公开月日字段，下一步：补充可信历法映射数据后再实现。
- [ ] Edge-TTS 网络音频端点和真实音频验收；代码已保留可选网络引擎，阻塞：当前没有稳定端点/实体设备，下一步：在提供端点和设备后验证握手、音频解码与失败提示。
- [ ] AI OpenAI-compatible 网络调用端到端验收；配置和任务提示已实现，阻塞：没有可用 API key/模型服务，下一步：用本地兼容服务完成请求、错误、超时和结果展示验证。
- [ ] AI 语法拆解的结构化结果与选中文本高亮；当前有任务提示和结果文本，下一步：增加结构化解析协议、段落定位和 UI 高亮。
- [x] AI 结果记录的 Room 表、保存、删除和重开流程；路径：`android/app/src/main/java/com/dutongjian/app/data/local/AiResult*`、`ReadingViewModel.kt`、`DutongjianApp.kt`；迁移 `4→5` 和 JVM 测试已通过。✅
- [ ] AI 反事实推演的独立模板和上下文边界；当前结果已可保存/删除/重开，下一步：补结构化任务记录、上下文边界和端到端模型验证。
- [ ] 历史人物角色对话的事实约束、对话界面和离线/联网边界；当前只有一次性任务入口，下一步：增加角色上下文和会话记录模型。
- [ ] 重点战役沙盘的地图、兵力、粮道和关键决策数据；当前只有正文注释沙盘卡，下一步：先确认公开结构化字段，再建立可追溯数据模型。
- [ ] 当前环境没有用户生产 keystore，下一步：注入签名 secrets 。
- [ ] gh release 自动发布闭环；已加入只允许手动/版本标签触发、必须生产 keystore secrets 的 `.github/workflows/release.yml`，但当前环境没有生产 keystore，下一步：在 Codespace/Actions secrets 配置后执行一次签名发布演练。

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
- 宿主机可能清理 `/mnt/workspace` 之外的内容；抓取数据库、原始 JSON cache、checkpoint、锁和日志必须固定在 `/mnt/workspace/readgb/service/data/`，启动前核对绝对路径。
- 不登录、不绕过验证码、访问控制、robots 或付费墙。
- 不通过并发绕过站点限流；缓存命中不发网络请求。
- 阶段性快照必须标明 checkpoint，不得称为全本。

## 变更日志

### 2026-08-03

- [x] 核对真实同步状态：进程曾停止，checkpoint 为 9/1405，数据库为 47 条真实正文；缓存数量不作为已入库数量。✅
- [x] 恢复唯一多线程同步进程；停止前最新观察到 87/1405、283 条真实正文、失败 4；checkpoint 已保存。✅
- [x] 将研读柱状图从少量朝代示例改为纪年/纪·朝代全量分组，并增加点选下钻。✅
- [x] 将人物图谱从固定节点改为结构化人物全索引、中心人物关系网络和正文入口。✅
- [x] 把“每轮先读状态、任务行末加 ✅、中断可恢复、日志与项目合并”的要求写入 README.md、DOCS.md、guide.txt 和本文件。✅
- [x] 完成 Kotlin/Backend 构建和运行验证：Backend 22 passed；Android test/lint/Debug/Release BUILD SUCCESSFUL。✅
- [x] 已确认 ModelScope 实例时长预警；数据库、cache、checkpoint、项目日志和代码均位于 `/mnt/workspace/readgb`，可在实例关闭后复用原路径恢复。按用户要求已停止软件模拟器，未把设备级研读页验收写成完成。✅
- [x] 按要求停止全部本轮任务并保存：爬虫 PID 143358 已退出，没有虚拟机或其他项目任务运行；恢复时复用 `service/data/tongjian-progress.json`、`service/data/tongjian-cache/` 和 `service/data/dutongjian.db`，不使用 `--reset`。✅
- [x] 核对本轮爬取产物全部位于项目内 `service/data/`：数据库、原始 JSON cache、checkpoint、失败记录和旧数据审计快照均已落盘；未发现项目外或 `/tmp` 的同类数据。运行时数据库/cache/checkpoint 依照 `.gitignore` 不纳入 Git，push 只提交源码和文档。✅
- [x] 记录宿主机清理边界：用户确认 `/mnt/workspace` 之外的内容运行一段时间后可能被清空；已将抓取数据固定路径和启动前绝对路径核对要求写入 `README.md`、`DOCS.md`、`guide.txt` 和本文件。✅
- [x] 功能/文档提交 fca5c11 和状态提交 b35ed9a 已推送 origin/main。✅

### 2026-08-03 20:33 Asia/Shanghai

- [x] 事项：详读项目文档、源码、测试和脚本并恢复开发环境；结果：OpenJDK 21、Gradle 9.4.0、Android API 35、Build Tools 35/36 和 platform-tools 已就绪；路径：`/mnt/workspace/readgb/.android-sdk`、`.gradle`、`.venv`。✅
- [x] 事项：增强公开同步完整性；结果：真实正文计数排除 3 条 seed，空年号、非对象正文、重复正文 ID、重复纪年 ID、损坏 checkpoint 和目录规模异常均会拒绝或重试；路径：`service/app/tongjian_sync.py`、`service/app/store.py`、`scripts/resume_crawler.sh`。✅
- [x] 事项：完善客户端/API 交互；结果：年表不再截断前 24 个筛选项，笔记删除按钮与打开正文点击区分离，分类筛选可再次点击清除，空白搜索返回 422；路径：`android/app/src/main/java/com/dutongjian/app/ui/FeatureScreens.kt`、`DutongjianApp.kt`、`service/app/main.py`。✅
- [ ] 事项：持续公开 API 同步；结果：最近观察 checkpoint `508/1405`、失败 `0`、真实正文 `4,152`；下一步：保持 PID `9878` 完成全量，完成后校验 `30,989/294/1,405` 并导出 Android assets。

### 2026-08-03 21:23 Asia/Shanghai

- [x] 事项：递归阅读 Git 跟踪的 11 份 Markdown/TXT 项目文档及完整历史归档；结果：识别并区分全量数据、百科/标注、图表验收、历法、TTS、AI、沙盘、签名和发布自动化任务；路径：`README.md`、`DOCS.md`、`guide.txt`、`plugin.md`、`PROJECT_STATE.md`、`PROJECT_STATE_HISTORY.md`、`TASK_PROGRESS_2026-08-03.md`、`issue/Unable2Install.txt`、`docs/site-analysis.md`、`DEVELOPMENT_LOG.md`、`service/requirements.txt`。✅
- [x] 事项：补充只读数据集校验器和 Android 覆盖率卡片；命令：`PYTHONPATH=service .venv/bin/python -m pytest -q service/tests/test_validate_tongjian.py`、`./gradlew :app:testDebugUnitTest`；结果：校验器测试 `3 passed`，观察到真实正文 `14,195`、纪年 `911/1405`、卷 `150/294`，字段/层级/关联错误 `0`；路径：`service/app/validate_tongjian.py`、`android/app/src/main/java/com/dutongjian/app/ui/StudyCoverage.kt`。✅
- [x] 事项：加入签名发布自动化骨架；结果：`.github/workflows/release.yml` 仅接受手动/版本标签触发，要求生产 keystore secrets，执行 Android 测试、lint、签名 APK、apksigner、SHA-256 和 gh release 上传；当前未使用真实 secrets。✅
- [x] 事项：配置 Android API 35 模拟器环境；命令：`avdmanager create avd --name readgb-api35-workspace ...`、`emulator -avd readgb-api35-workspace -no-window ...`；结果：system image 和 AVD 创建成功，但启动被宿主机缺少 KVM/VMX/SVM 阻塞；路径：`.android-sdk/`、`.android-avd/`；恢复：有硬件加速的主机上复用 AVD，当前不把设备验收写成完成。✅
- [ ] 事项：继续公开 API 同步；结果：PID `9878`，checkpoint `957/1405`，真实正文 `16,285`，失败 `0`；下一步：复用项目内数据库/cache/checkpoint，结束后执行严格校验、资产导出、Android 全量构建和快照推送。

### 2026-08-03 14:59 Asia/Shanghai

- [x] 事项：记录宿主机持久化边界。命令：`./scripts/resume_crawler.sh`；结果：当前同步从 `/mnt/workspace/readgb` 启动，数据库、cache、checkpoint 和锁均位于 `/mnt/workspace/readgb/service/data/`；路径：`README.md`、`DOCS.md`、`guide.txt`、`PROJECT_STATE.md`；恢复：后续只复用项目内绝对路径，不使用 `/tmp` 或 `/mnt/workspace` 外目录。✅

### 2026-08-03 15:06 Asia/Shanghai

- [x] 事项：继续公开 API 同步并脱离本次交互会话运行。命令：`setsid -f ./scripts/resume_crawler.sh </dev/null >/dev/null 2>&1`；结果：独立进程 PID `13555`，checkpoint `58/1405`，数据库 `197` 条真实正文，失败 `0`；路径：`/mnt/workspace/readgb/service/data/`；恢复：复用同一 cache/checkpoint，确认没有现存进程后再次运行 `./scripts/resume_crawler.sh`，不使用 `--reset`。✅

### 2026-08-03 16:56 Asia/Shanghai

- [x] 事项：完成正文阅读界面改造；命令：`git diff --check`、Android Gradle 编译；结果：顶部正文标题、阅读方式切换、字形/字号控制、段落编号、朗读主按钮、复制/分享/笔记/更多工具入口、注释和字词折叠区已接入；剪贴板整篇复制不会误生成整篇划线。路径：`android/app/src/main/java/com/dutongjian/app/ui/DutongjianApp.kt`。✅
- [x] 事项：运行 Android JVM test、lint 和构建；命令：`./gradlew --no-daemon :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug :app:assembleDebug :app:assembleInspection :app:assembleRelease`；结果：`BUILD SUCCESSFUL`，23 分 54 秒；Debug `app-debug.apk` 20M，Inspection `app-inspection.apk` 13M，Release `app-release-unsigned.apk` 13M；未进行设备验收。✅
- [x] 事项：补齐构建环境；结果：OpenJDK 21、Gradle 9.4.0、Android API 35、Build Tools 35.0.0/36.0.0、platform-tools 均位于 `/mnt/workspace/readgb` 内；未安装模拟器镜像。✅
- [x] 事项：清理旧安装问题文档中的模拟器/adb 复现要求；结果：`issue/Unable2Install.txt` 已改为历史静态诊断，当前只接受 Backend/JVM/lint/APK 产物验证；历史档案保留。✅
- [ ] 事项：持续公开 API 同步；结果：checkpoint `167/1405`，失败 `19`，数据库 `737` 条正文，失败主要为 HTTP 429；下一步：继续复用项目内 cache/checkpoint，20:30 自动停止并保存。
- [x] 事项：历史 GitHub Release 记录；结果：该旧日志当时记录为 `gh auth status` 未登录，后续已恢复 gh 登录并完成多次历史预发布；当前全量资产 Release 仍需等严格校验、签名和产物检查。✅

### 2026-08-03 21:45 Asia/Shanghai

- [x] 事项：补齐 AI 结果持久化。命令：`./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`；结果：`BUILD SUCCESSFUL`，新增 Room `ai_results` 表、`4→5` 迁移、保存/删除/重开流程和 JVM 覆盖；路径：`android/app/src/main/java/com/dutongjian/app/data/local/`、`ReadingViewModel.kt`、`DutongjianApp.kt`。✅
- [x] 事项：排除本机模拟器状态。结果：`.android-avd/` 已加入 `.gitignore`，不进入源码或远程仓库。✅
- [x] 事项：增加全量收尾脚本。命令：`bash -n scripts/finalize_tongjian.sh`、在 PID `9878` 持锁期间执行脚本；结果：脚本通过语法检查，并以退出码 `2` 拒绝并发收尾；路径：`scripts/finalize_tongjian.sh`、`README.md`、`DOCS.md`。✅
- [ ] 事项：继续公开 API 同步；结果：PID `9878`，checkpoint `1069/1405`，真实正文 `17,900`，失败 `0`；下一步：完成同步后执行严格校验、资产导出、Android 全量构建和快照推送。

### 2026-08-03 22:07 Asia/Shanghai

- [x] 事项：按用户要求整理完成项、进行中任务、后续动作和外部阻塞；结果：已写入“本轮完成与后续同步清单”，并同步当前 `1216/1405`、`23,704` 条正文、失败 `0`。✅
- [ ] 事项：继续公开 API 同步；结果：PID `9878`，checkpoint `1216/1405`，失败 `0`；下一步：保持原 cache/checkpoint，完成后运行最新版恢复、严格校验、全量收尾和 push。

## 记录协议

每次开发必须执行以下规则：

1. 开始前先读本文件，再执行 git status --short。
2. 开始工作时在“当前任务清单”写目标、涉及路径和恢复方式。
3. 完成一项就在任务行末加 ✅；未完成项保持 [ ]，并写明下一步或阻塞原因。
4. 爬虫、构建、测试、部署、数据路径或文档有变化时，在本文件“变更日志”追加时间、命令、结果和实际数量。
5. 意外中断后先检查进程和 checkpoint，复用同一 cache/checkpoint；确认没有进程时才运行恢复脚本。
6. 不把 PID、速度、数量、构建或测试结果写入 README 等稳定文档；这些事实只更新本文件。

清单格式：

```text
- [ ] 目标：说明本轮任务；路径：`涉及文件`；恢复：`命令或 checkpoint`；下一步：说明原因。
- [x] 目标：说明已完成任务；结果：实际命令、数量或验收结论。✅
```

变更日志格式：

```text
### YYYY-MM-DD HH:MM Asia/Shanghai
- [x] 事项：实际动作；命令：`...`；结果：实际输出/数量；路径：`...`；恢复：`...`。✅
- [ ] 事项：未完成动作；阻塞：实际原因；下一步：恢复动作。
```

完成项必须在任务行末追加 `✅`；未完成项必须保留 `[ ]` 并写下一步或阻塞原因。日志只记录已经观察到的事实，不能把阶段性数据或未执行的设备验收写成完成。
