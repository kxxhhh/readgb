# 读通鉴当前任务清单与恢复日志

更新时间：2026-08-04 06:23（Asia/Shanghai）

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
- [x] 本轮公开 API 同步已完成；checkpoint `1405/1405`、失败 `0`、数据库 `30,989` 条真实 `zztj-*` 正文，继续使用 4 worker、5 秒全局节奏和 robots 检查。✅
- [x] `validate_tongjian --strict` 已通过 `30,989/294/1,405`、零空纪年、零外键/关联错误；公开数据中 1 条记录没有译文，报告为 `translation_fallback=1`，客户端按原文回退，不伪造译文。✅
- [x] 全量正文、目录和百科 Android assets 已导出并通过数量/分类校验；正文 `48,126`（资治通鉴 `30,989`、纪事本末 `14,176`、读通鉴论 `2,961`）、目录 `565` 卷/`2,556` 年、百科 `61,696` 条，五类齐全。✅
- [x] Android 图表代码已改为按纪年/纪·朝代覆盖全部分组，并把人物图谱改为全人物索引 + 中心人物下钻；Kotlin 编译、测试和 lint 已通过。✅
- [x] 研读页新增正文/纪年/卷三项真实 `zztj-*` 覆盖率卡片，不把 OfflineSeed 计入数据覆盖。✅
- [x] 定位 `0.1.15` 首启闪退/卡启动的主要内存路径：全量正文导入和 `SELECT *` 列表同时保留重文本；已改为 500 条批次流式导入、摘要投影和点击后单条加载，失败时保留原数据库。✅
- [x] `0.1.16` 已完成签名构建：`versionCode=13`、`versionName=0.1.16`，Release APK v2 签名通过，证书 SHA-256 为 `0bd6d2260b1da032d761c16e7d31fee2767c80362295353e3f7ea10ebd111c57`。✅
- [x] 修复正文详情被 Room 摘要流覆盖的问题：ViewModel 保留已加载详情，并在收藏/阅读时间更新后合并摘要状态，不再让“原文/原文与白话/白话”内容消失。✅
- [x] `0.1.17` 已完成签名构建：`versionCode=14`、`versionName=0.1.17`，Release APK v2 签名通过，离线正文/目录/百科资源校验通过。✅
- [x] 修复目录、子目录和扩展入口为空/加载慢：目录本地优先，空 API 响应触发回退，纪事本末/读通鉴论补种子层级，百科入口切换知识库，正文查询先于阅读记录写入。✅
- [x] `0.1.18` 已完成签名构建：`versionCode=15`、`versionName=0.1.18`，Release APK v2 签名通过。✅
- [x] `0.1.19` 发布快照已导出：正文 `46,110` 篇（资治通鉴 `30,989`、纪事本末 `14,176`、读通鉴论 `945`），目录 `544` 卷/`1,985` 纪年，百科 `61,696` 条。✅
- [x] Edge-TTS 连续朗读和无声修复：播放完成回调先清理旧请求再进入下一句；移除 Edge WebSocket 会以 `1007` 拒绝的 SSML `<s>` 标签，并补充空音频、播放器和握手错误处理。协议层已用真实 Edge 响应复现验证，设备音频仍待实体设备验收。✅
- [x] AI Markdown 渲染已接入：普通 AI 结果支持标题、段落、列表、引用、代码块、表格和行内样式，归档预览去除 Markdown 控制符；解析器和 Android JVM 测试通过。✅
- [x] 当前 Release 构建已上传并替换 `v0.1.19` 的 `app-release.apk`；Release v2 签名通过，版本 `16/0.1.19`，APK 只保留在 GitHub Release。✅
- [x] 扩展内容已完成断点同步：事件 `239/239`、史论主题 `912/912`、checkpoint 错误 `0`；最终导出正文 `48,126`、目录 `565/2,556`，其中读通鉴论正文 `2,961`。✅
- [x] 全部抓取正文已清理公开接口段号前缀；数据库迁移和导出校验均确认 `48,126` 条正文的 `content/original` 不再以数字开头，注释偏移同步扣除前缀长度。✅
- [x] 阅读页已删除“决策卡”及其本地生成逻辑；历史注释不再按不可靠的接口偏移量覆盖正文词语，改为独立“来源注释”列表。✅
- [x] `0.1.20` 最终完整数据 Release；Android 三种 APK 已完成签名构建、commit/push 和 GitHub Release 上传，Release 正式发布且三项 APK 资产齐全。✅
- [x] 正文篇次排序已贯通数据库、API、Android Room、资产导出和离线导入；详情页增加同组上一篇/下一篇和篇目选择器，读完正文无需返回目录。✅
- [x] 历史角色对话改为先选择两位人物；生成后用户可以直接插话，每轮让两位人物分别回应，角色选择和会话转录保存到 Room。✅
- [x] 史料沙盘改为可解释的证据视图：展示正文注释、人物、地点、证据链和数据边界；缺少公开兵力/粮道/路线字段时明确标记“未提供”。✅

### 当前同步速度判断

资治通鉴主体于 2026-08-03 14:57 左右完成 `1405/1405` 个纪年、`30,989` 条真实正文，失败 `0`。扩展同步使用独立 cache/checkpoint、robots、5 秒节奏和 429/Retry-After 退避；当前进程继续同步 `纪事本末`/`读通鉴论`，不得重复启动第二个实例。

## 当前任务清单

### 本轮完成与后续同步清单（2026-08-03 22:49 Asia/Shanghai）

已完成并已 push：

- [x] 逐份阅读 Git 跟踪的 Markdown/TXT 文档、历史归档和项目要求，区分当前事实、阶段性数据与未完成项。✅
- [x] 配置并验证 Python 虚拟环境、OpenJDK 21、Gradle、Android SDK/API 35、Build Tools、platform-tools；记录无 KVM/VMX/SVM 的设备限制。✅
- [x] 恢复公开 API 断点爬虫，固定项目内数据库/cache/checkpoint 路径，启用 robots、Retry-After、5 秒全局节奏、4 worker、去重和锁；阶段性内容快照已推送。✅
- [x] 增加 `validate_tongjian` 严格数据报告和研读覆盖率卡片；Backend/Android 测试、lint 和 Debug 构建已通过。✅
- [x] 增加 Release 签名与 gh release workflow 骨架；没有生产 keystore，因此未宣称真实签名发布完成。✅
- [x] 增加 AI 结果 Room 持久化、迁移、保存/删除/重开和 JVM 测试；覆盖反事实/角色等任务的结果记录。✅
- [x] 增加百科导出五类关联、非空字段、去重闸门，以及 `scripts/finalize_tongjian.sh` 全量收尾流程；持锁拒绝并发已验证。✅
- [x] 完成公开 API 全量续爬并收敛到 `1405/1405`、失败 `0`、真实正文 `30,989`；复用项目内 database/cache/checkpoint，没有重置或重复抓取。✅
- [x] 运行严格校验和全量收尾；生成正文、目录、百科 assets，并生成可推送的数据库/进度快照与独立缓存快照；两个归档均小于 GitHub 单文件限制并通过 SHA-256。✅
- [x] 完善本地 AI 能力：角色任务支持同一史料边界内的多轮追问并更新 Room 转录；语法任务支持原文定位列和命中高亮；反事实任务补齐事实/推断/假设/影响边界。✅
- [x] 全量 Android 构建完成；APK 未进入 Git 项目，已直接上传 GitHub `v0.1.14` 预发布。✅

正在进行：

- [ ] 外部验收：AI/Edge-TTS/历法/战役数据仍有外部依赖；用户已取消本地虚拟机路线，真实 Android 设备验收待设备接入。

接下来按顺序执行：

- [x] 原进程结束后运行最新版 `./scripts/resume_crawler.sh`，复核已完成但无真实正文的纪年；最终无空纪年。✅
- [x] 运行 `validate_tongjian --strict`，确认 `30989/294/1405`、零空纪年、零层级/关联错误；译文缺失单独计入 `translation_fallback=1`。✅
- [x] 运行 `./scripts/finalize_tongjian.sh`，生成并核对 `offline_content.ndjson.gz`、`offline_catalog.json`、`offline_knowledge.json` 和分卷压缩快照。✅
- [x] 用全量 assets 重跑 Android JVM test、lint、Debug/Inspection/Release 构建；三种 APK 均核对正文 `30,989`、目录 `294/1,405`、百科 `61,696` 和五类分类。✅
- [x] 更新当前数量、快照哈希和 APK 产物记录，提交并 push 全量资产与爬取快照；代码/资产 commit `7bf598a`，AI 代码 commit `11eb34a`。✅
- [x] 用 `gh release create v0.1.14` 直接上传三个 APK；Release URL：`https://github.com/kxxhhh/readgb/releases/tag/v0.1.14`。APK 未提交到项目仓库。✅
- [x] 修复全量导入/列表内存峰值并完成 `0.1.16` 签名 Debug/Inspection/Release 构建；JVM test、lint 全部通过，APK 内正文/目录/百科资产仍为 `30,989/294/1,405/61,696`。✅
- [x] 按要求取消本地虚拟机路线并清理 `readgb-api35-lite`、`readgb-api35-workspace` 及 API 35 系统镜像；当前无 emulator/qemu 进程和可用 AVD。✅
- [x] 创建并上传正式 `v0.1.16` GitHub Release；signed `app-release.apk`、debug 和 inspection APK 均已上传，APK 不进入 Git。✅
- [x] 修复正文详情显示回归并补充 `loadedDetailSurvivesSummaryRefresh` 测试；Android JVM test、lint 和 `0.1.17` 三种 APK 构建均通过。✅
- [x] 创建并上传正式 `v0.1.17` GitHub Release；signed `app-release.apk`、debug 和 inspection APK 均已上传，APK 不进入 Git。✅
- [x] 修复目录/百科空数据和 API 超时等待；Android JVM test、lint 和 `0.1.18` 三种 APK 构建均通过。✅
- [x] 创建并上传正式 `v0.1.18` GitHub Release；signed `app-release.apk`、debug 和 inspection APK 均已上传，地址：`https://github.com/kxxhhh/readgb/releases/tag/v0.1.18`。✅
- [x] 扩展同步器支持事件/史论公开接口、原始 JSON 缓存、原子进度和 429/Retry-After 重试；已抓取 `239` 个事件、`341` 个史论主题并导出 Android 快照。✅
- [x] 设置页完善为主题、字号、行距、阅读方式、字形、TTS、AI、隐私和版本信息；加入 Compose 动效开关、AnimatedContent/AnimatedVisibility、animateContentSize 和 Android 12+ blur 视觉层。✅
- [x] 运行 `EXTENDED_MIN_INTERVAL=5 ./scripts/resume_extended_crawler.sh` 直到史论 `912/912` 且错误 `0`；恢复时复用 `service/data/extended-progress.json` 和 `service/data/extended-cache/`。✅
- [x] 修复跨层篇次混乱：按 section/volume/year/sort_order/id 稳定排序，扩展内容也使用原始事件/评论序号；新增 Android 相邻篇目导航和列表选择器。✅
- [x] 接入双角色对话和用户参与入口：人物选择器只允许两位不同人物，AI 追问输入标记为“你”，响应要求两位人物分别回应。✅
- [x] 将沙盘入口改成史料证据视图，保留地点地图入口和原文注释查看，不用无来源数据绘制兵力或粮道。✅

仍未完成或有外部阻塞：

- [ ] 全本规模图表滚动、文字截断、节点重叠、关系准确性、搜索性能和真实设备验收；本地无 KVM 且虚拟机路线已取消，需接入真实 Android 设备后回归。
- [ ] AI 兼容服务真实请求/错误/超时验收，以及模型实际遵守史料边界的质量验收；本地 prompt 边界、反事实模板、角色多轮会话和语法原文定位 UI 已实现。
- [ ] 历史上的今天可信月日映射、Edge-TTS 实体设备音频、战役地图/兵力/粮道沙盘；前两项分别缺少可信历法数据和实体设备，战役字段没有公开结构化来源。
- [ ] Codespace/Actions 生产 keystore 签名和 gh release 演练；本地 release keystore 已创建并使用，GitHub Actions secrets 仍未注入。

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
- [x] 继续观察恢复后的稳定吞吐和失败率；最终完成 `1405` 个纪年、失败 `0`，没有新增失败记录。✅
- [x] 完成 1,405 个纪年后执行全量正文、目录、关联字段校验并导出 Android assets。✅

### 数据校验与覆盖看板

- [x] 增加只读数据集校验器，检查真实正文数量、唯一 ID、必填字段、卷/纪年外键、空纪年、关联 JSON 和 checkpoint；路径：`service/app/validate_tongjian.py`；测试：`service/tests/test_validate_tongjian.py`。✅
- [x] 研读页增加正文/纪年/卷覆盖率卡片，明确排除 OfflineSeed；路径：`android/app/src/main/java/com/dutongjian/app/ui/StudyCoverage.kt`、`StudyScreen.kt`；测试：`StudyCoverageTest`。✅
- [x] 同步结束后用校验器严格通过 `30,989` 条正文、`294` 卷、`1,405` 纪年、零空纪年和零层级/关联错误；译文缺失记录单独报告为 `translation_fallback=1`。✅

### 构建与验收

- [x] Backend 测试 `32 passed`、compileall、shell 语法检查和 git diff --check。✅
- [x] 全量 assets 构建和 APK 内容验收：`testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleInspection`、`assembleRelease` 均 `BUILD SUCCESSFUL`；Debug APK `104,086,521` bytes，Inspection APK `83,032,517` bytes，unsigned Release APK `83,020,229` bytes。三种 APK 直接解析得到正文 `30,989`、卷 `294`、年 `1,405`、百科 `61,696`。✅
- [x] 资产 SHA-256：`offline_content.ndjson.gz`=`2956d83ebfbac0f45db25c77c531318220f5ce959287e943fba9961d62a189ba`，`offline_catalog.json`=`66dd0f1320e564d92950ad55e678b6c7030a3bf65dce90f5d9064a60bafe9b7c`，`offline_knowledge.json`=`fa1bbc67c05c0cc00c4dfbbcfe8322f45f28bd5ba73a5ea15d2b543f9b20ec45`。✅
- [x] APK SHA-256：Debug=`ebc05879c452fd78cc4c5b65e393713f04b26572059978a56e0190a5ed3e2a32`，Inspection=`5dc87f14d5cffb4fdc797e9f8f4acc87e7ca090d083ef2dd659f19230154b0ee`，unsigned Release=`de7e204930e79e840facc6f5cadfcad9d0b638d491d59a3c2681928ab0dec099`。APK 只上传 GitHub Release，不进入 Git 项目目录。✅
- [x] 设备/真机验收不纳入本轮交付；本轮以源码、JVM test、lint、APK 编译和发布产物为验收依据。✅
- [x] 已记录 APK、数据规模、测试命令和实际警告；未进行模拟器或真机验收。✅

### 其他未完成能力

- [x] 全量百科资产：人物 `31,552`、地点 `9,896`、官职 `13,475`、主题 `519`、决策 `6,254`，已由 `export_android` 检查非空、去重并写入 `offline_knowledge.json`。✅
- [ ] 典章制度、经济史专题的全量结构化标注；当前只有 tags/关联字段和关键词筛选，下一步：依据公开字段建立可追溯的主题分类规则并补测试。
- [ ] 全本图表性能、关系准确性和人物/时期覆盖验收；下一步：全量资产导入后执行图表聚合基准和 Android UI 检查。
- [ ] “历史上的今天”完整历法匹配和自动刷新；当前 `今日金句` 只是按 day-of-year 轮换条目，缺少公开月日字段，下一步：补充可信历法映射数据后再实现。
- [ ] Edge-TTS 实体设备音频验收；协议层已验证无声根因并完成代码修复，阻塞：当前没有实体 Android 设备，下一步：接入设备后验证握手、音频解码和失败提示。
- [ ] AI OpenAI-compatible 网络调用端到端验收；配置和任务提示已实现，阻塞：没有可用 API key/模型服务，下一步：用本地兼容服务完成请求、错误、超时和结果展示验证。
- [x] AI 语法拆解的 Markdown 表格解析和结构化结果卡片；解析失败回退原文；路径：`android/app/src/main/java/com/dutongjian/app/domain/text/ClassicalGrammarAnalysis.kt`、`DutongjianApp.kt`；JVM 测试和 Android 构建已通过。✅
- [x] AI 普通结果 Markdown 渲染：标题、段落、粗斜体、行内代码、链接文本、列表、引用、分隔线、代码块和表格已解析展示；路径：`android/app/src/main/java/com/dutongjian/app/domain/text/MarkdownRenderer.kt`、`DutongjianApp.kt`；JVM 测试已通过。✅
- [x] AI 语法拆解的原文定位和高亮：提示要求模型返回连续原文片段，解析器兼容第五列，详情页对命中的原文片段着色；没有定位列或未命中时仍回退结构卡片/原始结果。✅
- [x] AI 结果记录的 Room 表、保存、删除和重开流程；路径：`android/app/src/main/java/com/dutongjian/app/data/local/AiResult*`、`ReadingViewModel.kt`、`DutongjianApp.kt`；迁移 `4→5→6` 和 JVM 测试已通过。✅
- [x] AI 反事实推演的独立模板和上下文边界：系统提示区分史料事实、合理推断、不确定假设和非正史影响，输入块使用明确数据边界；真实模型验证仍受外部服务阻塞。✅
- [x] 历史人物角色对话的同一史料多轮会话：可选择两位人物，用户可插话，最多保留 6 轮上下文，Room 同 ID 更新会话转录；事实约束和联网/离线端到端验收仍需真实模型服务。✅
- [ ] 重点战役沙盘的地图、兵力、粮道和关键决策数据；当前已实现正文注释、人物、地点和证据链视图，缺少公开结构化字段，不能宣称完整战役推演。
- [ ] 当前环境没有用户生产 keystore，下一步：注入签名 secrets 。
- [ ] gh release 自动发布闭环；已加入只允许手动/版本标签触发、必须生产 keystore secrets 的 `.github/workflows/release.yml`，但当前环境没有生产 keystore，下一步：在 Codespace/Actions secrets 配置后执行一次签名发布演练。

## 路径与边界

~~~text
service/data/dutongjian.db              当前同步数据库
service/data/tongjian-cache/            原始 JSON cache
service/data/tongjian-progress.json     断点和失败记录
service/data/tongjian-sync.lock         防重复锁
service/data/extended-cache/            扩展事件/史论原始 JSON 缓存（不提交 Git）
service/data/extended-progress.json     扩展同步断点（提交当前快照）
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
- [x] 事项：配置 Android API 35 模拟器环境；命令：`emulator -avd readgb-api35-workspace -no-window -gpu swiftshader_indirect -accel off`；结果：AVD 已调整为 `4GB RAM`、`1080x1920`、`420dpi`，ADB 坐标与物理屏幕一致；宿主机无 KVM，使用软件 TCG，首次启动/包扫描很慢。应用级验收仍未完成，不把模拟器启动写成 App 验收完成。✅
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

### 2026-08-03 22:49 Asia/Shanghai

- [x] 事项：完成公开 API 全量同步和严格收尾；命令：`./scripts/resume_crawler.sh`、`PYTHONPATH=service .venv/bin/python -m app.validate_tongjian --strict`、`./scripts/finalize_tongjian.sh`；结果：`1405/1405`、失败 `0`、正文 `30,989`、卷 `294`、年 `1,405`、百科 `61,696`，译文回退审计 `1` 条。✅
- [x] 事项：生成并校验全量 Android assets；结果：五类百科齐全，三种 APK 包内正文/目录/百科数量均与数据库一致；源码资产和 APK SHA-256 已记录在本文件。✅
- [x] 事项：生成可推送爬取快照；结果：`tongjian-snapshot-latest.tar.gz` `55,739,826` bytes，SHA-256 `eea1d5798d8e1ccfb582475cf65ad488ba809e1830c943df69bdfc8fce4c4ac5`；`tongjian-cache-snapshot-latest.tar.gz` `53,912,115` bytes，SHA-256 `2334152b4e94120894338537f98e8e8e46215d9db4b377136b7695a352ae0718`；两个文件均通过 `sha256sum -c`。✅
- [ ] 事项：提交和 push 全量代码、assets 与快照；下一步：stage 大文件并推送 `origin/main`，然后继续实现本地可完成的 AI 会话/原文定位能力。

### 2026-08-03 23:20 Asia/Shanghai

- [x] 事项：实现本地 AI 未完成能力；命令：`./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleInspection :app:assembleRelease`；结果：`BUILD SUCCESSFUL`，JVM `32` 项通过；角色多轮追问、史料边界、Room 转录更新和语法原文高亮已加入。✅
- [x] 事项：按要求不把 APK 提交到项目；结果：三种 APK 直接上传 GitHub `v0.1.14` 预发布，Release 资产状态均为 `uploaded`；没有生产 keystore，因此明确标记为未签名。✅
- [x] 事项：第二批源码和文档 push；结果：`11eb34a` 已推送 `origin/main`，工作区干净；路径：`android/app/`、`PROJECT_STATE.md`、`DOCS.md`、`guide.txt`、`plugin.md`。✅

### 2026-08-04 01:17 Asia/Shanghai

- [x] 事项：排查 `0.1.15` 闪退/文本导入不全；命令：旧版 ADB 冷启动、logcat、源码审查；结果：旧实现一次性解析全量重文本并把 `SELECT *` 全量映射到 UI，存在 Android 堆内存峰值；旧版在模拟器中停留启动画面，未观察到 App `FATAL EXCEPTION`。✅
- [x] 事项：修复离线导入与列表内存路径；路径：`ReadingRepositoryImpl.kt`、`ItemDao.kt`、`ItemEntity.kt`、`ReadingViewModel.kt`；结果：两遍流式校验/导入、500 条批处理、事务替换、摘要投影、单条正文加载；Android JVM test/lint/三种 APK 构建 `BUILD SUCCESSFUL`。✅
- [x] 事项：准备并验证 `0.1.16` 签名；命令：`apksigner verify --verbose --print-certs app-release.apk`；结果：v2 `true`，版本 `13/0.1.16`，证书指纹 `0bd6d2260b1da032d761c16e7d31fee2767c80362295353e3f7ea10ebd111c57`；APK 仍只留在本地构建目录，待 Release 上传。✅
- [x] 事项：配置并调试 AVD/ADB；结果：先观察到宿主机 `109MB` 可用导致 system_server 重启，停止 Gradle daemon 后恢复；随后 2GB AVD 在安装阶段被来宾 lowmemorykiller 杀掉 system_server，已调为 4GB；swap 文件因容器无 `CAP_SYS_ADMIN` 无法启用，失败文件已清理。✅
- [ ] 事项：应用级 ADB 回归和崩溃日志；恢复：复用 `readgb-api35-workspace`，等待 `first_boot_completed=1` 后安装 `android/app/build/outputs/apk/release/app-release.apk`；下一步：抓取 `logcat`、截图，操作首页/搜索/目录/正文和导入数量，再决定是否发布 `v0.1.16`。

### 2026-08-04 01:35 Asia/Shanghai

- [x] 事项：按用户要求先发布 `0.1.16`；结果：正式 Release 已创建并上传 `app-release.apk`、`app-debug.apk`、`app-inspection.apk`，地址：`https://github.com/kxxhhh/readgb/releases/tag/v0.1.16`。✅
- [x] 事项：取消本地虚拟机方案；结果：停止后删除 `readgb-api35-lite`、`readgb-api35-workspace` 及 API 35 系统镜像，当前无 emulator/qemu 进程和 AVD。✅
- [ ] 事项：应用级崩溃日志和真实设备回归；当前不再使用虚拟机，待接入真实 Android 设备后通过 ADB 完成。

### 2026-08-04 01:45 Asia/Shanghai

- [x] 事项：修复正文“原文/原文与白话/白话”内容一闪消失；原因：`recordOpened` 引起摘要 Flow 更新，覆盖了刚加载的完整条目；结果：增加详情缓存与摘要状态合并，补充 ViewModel 回归测试。✅
- [x] 事项：构建并验证 `0.1.17`；结果：`versionCode=14`，JVM test、lint、Debug/Inspection/Release 均成功，Release v2 签名和离线资源校验通过。✅
- [ ] 事项：真实 Android 设备回归；本地虚拟机路线已取消，待实体设备接入后验证 Tab 切换、正文显示和崩溃日志。

### 2026-08-04 02:14 Asia/Shanghai

- [x] 事项：修复目录与扩展入口空数据/加载慢；结果：目录、卷、纪年、百科改为本地优先，API 空响应不再覆盖离线数据；纪事本末和读通鉴论补齐种子卷/年/条目，通鉴百科从目录入口切到知识库。✅
- [x] 事项：优化正文打开耗时；结果：先加载正文详情并刷新界面，再安全写入阅读历史，避免记录写入阻塞正文首屏。✅
- [x] 事项：构建并发布 `0.1.18`；结果：`versionCode=15`，JVM test、lint、Debug/Inspection/Release 成功，Release v2 签名通过；地址：`https://github.com/kxxhhh/readgb/releases/tag/v0.1.18`。✅

### 2026-08-04 03:28 Asia/Shanghai

- [x] 事项：补抓扩展公开内容；命令：`./scripts/resume_extended_crawler.sh`；结果：事件接口已成功 `239/239`，史论按 5 秒节奏持续同步；进度逐条保存到 `service/data/extended-progress.json`。✅
- [x] 事项：导出当前 Android 离线快照；命令：`PYTHONPATH=service .venv/bin/python -m app.export_android`；结果：正文 `46,110`，目录 `544/1,985`，百科 `61,696`；本次资产包含 `341` 个已完成史论主题。✅
- [x] 事项：完善 Android 设置与动效；结果：主题持久化、阅读偏好、动效开关、AnimatedContent/AnimatedVisibility、animateContentSize、Android 12+ blur 兼容层已加入；Compose Kotlin 编译通过。✅
- [x] 事项：提交并发布 `0.1.19`；结果：commit `6db2b20` 已 push，JVM `34` 项、lint、Debug/Inspection/签名 Release 均通过；三种 APK 内正文 `46,110`、目录 `544/1,985`、百科 `61,696`；Release：`https://github.com/kxxhhh/readgb/releases/tag/v0.1.19`。✅

### 2026-08-04 03:43 Asia/Shanghai

- [x] 事项：核验发布资产；命令：`apksigner verify --verbose --print-certs`、`aapt dump badging`、APK asset 解包统计；结果：Release v2 签名通过，版本 `16/0.1.19`，证书 SHA-256 `0bd6d2260b1da032d761c16e7d31fee2767c80362295353e3f7ea10ebd111c57`，三个 APK 均含 `46,110/544/1,985/61,696`。✅
- [ ] 事项：扩展史论继续补抓；当前 `239/239` 事件、`462/912` 史论主题、错误 `0`；恢复命令：`EXTENDED_MIN_INTERVAL=5 ./scripts/resume_extended_crawler.sh`。⏳

### 2026-08-04 04:25 Asia/Shanghai

- [x] 事项：修复 Edge-TTS 只朗读到第一句的问题；结果：播放完成回调先清理旧音频请求，再触发下一句；句段边界和阅读页高亮统一，SSML 使用 `<s>` 显式标记句子。路径：`android/app/src/main/java/com/dutongjian/app/data/tts/EdgeTTSEngine.kt`、`ClassicalTextPreprocessor.kt`、`DutongjianApp.kt`；Android JVM test、Compose 编译通过。✅
- [x] 事项：修复 AI 结果 Markdown 源码直接显示；结果：新增纯 Kotlin Markdown 解析器和 Compose 渲染组件，支持标题、列表、引用、代码块、表格及行内样式；新增 `MarkdownRendererTest`，Android JVM test 通过。✅
- [x] 事项：编译并上传当前签名 Release；命令：`./gradlew --no-daemon :app:assembleRelease`、`gh release upload v0.1.19 android/app/build/outputs/apk/release/app-release.apk --clobber`；结果：Release v2 签名通过，版本 `16/0.1.19`，资产大小 `93,684,243` bytes，地址：`https://github.com/kxxhhh/readgb/releases/download/v0.1.19/app-release.apk`。✅
- [ ] 事项：继续扩展史论同步；当前 `239/239` 事件、`742/912` 史论主题、错误 `7`；下一步：本轮完成后自动重试失败项，达到 `912/912` 且错误 `0` 后再导出最终 Android assets。⏳

### 2026-08-04 04:53 Asia/Shanghai

- [x] 事项：完成扩展史论同步和失败重试；命令：`EXTENDED_MIN_INTERVAL=5 ./scripts/resume_extended_crawler.sh`；结果：事件 `239/239`、史论 `912/912`、错误 `0`；数据库扩展正文为纪事本末 `14,176`、读通鉴论 `2,961`。✅
- [x] 事项：导出并校验最终 Android assets；命令：`PYTHONPATH=service .venv/bin/python -m app.export_android`、资产 JSON/NDJSON 校验；结果：正文 `48,126`、目录 `565/2,556`、百科 `61,696`，三类正文 ID 唯一、必填字段完整；Python 测试 `34 passed`，严格资治通鉴校验通过。✅
- [x] 事项：构建并发布 `0.1.20`；结果：Android test/lint/Debug/Inspection/签名 Release 成功，全部源码和离线资产已 push，GitHub Release 已创建。✅

### 2026-08-04 05:15 Asia/Shanghai

- [x] 事项：移除“决策卡”；结果：详情页入口、卡片状态、策略选项和本地生成逻辑全部删除，百科中的原始“决策”关联数据保留。✅
- [x] 事项：修复短正文和段号噪声；命令：`app.normalize_tongjian_content`、`app.export_android`；结果：扫描 `48,126` 条正文，实际清理带段号的 `29,757` 条（通鉴 `21,600`、扩展正文 `8,156`、兼容旧首条 `1`），最终资源 `48,126` 条、数字前缀 `0`；最短真实正文为“蝗。”、“旱。”等原书单句纪事。✅
- [x] 事项：修复正文词与注释无关；结果：移除注释偏移量下划线/点击绑定，正文只保留真实地点和用户划线标记，注释通过独立“来源注释”区查看。✅
- [x] 事项：数据与服务测试；命令：`PYTHONPATH=service .venv/bin/pytest -q service/tests`、`validate_tongjian --strict`；结果：`34 passed`，通鉴 `30,989/294/1,405`、失败 `0`、空纪年 `0`。✅
- [x] 事项：构建并发布 `0.1.20`；结果：构建完成后已提交、push 并上传三个 APK 到 GitHub Release。✅

### 2026-08-04 05:22 Asia/Shanghai

- [x] 事项：完成 `0.1.20` Android 构建；命令：`./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleInspection :app:assembleRelease`；结果：`BUILD SUCCESSFUL`，耗时 `4m18s`，版本 `17/0.1.20`；仅有既有 `ClickableText` 弃用警告。✅
- [x] 事项：验收三种 APK；结果：Release v2 签名通过，正式证书 SHA-256 `0bd6d2260b1da032d761c16e7d31fee2767c80362295353e3f7ea10ebd111c57`；Debug `117,444,390` bytes / `142fa2c86b95d69120e9aa60c1b92ae91da6bc5311bf82930297cc108dbbf9d2`，Inspection `95,548,775` bytes / `29b07b151e6251877e0aca08db7c5767a5a1dd64f3b56cf25b1dd06a156e5571`，Release `95,548,775` bytes / `f8a77487d723ff60530d77ad96b609af2b6b1d7ac34b81803db8cc8d858e1373`；三者均含正文 `48,126`、数字前缀 `0`、目录 `565/2,556`、百科 `61,696`。✅
- [x] 事项：记录最终资产哈希；结果：`offline_content.ndjson.gz`=`440dbee3354986955a10220161c8a061a6a5be30fc4cb18eb3dccec7c542e091`，`offline_catalog.json`=`cf8d56bc563c72bd001343f1ced6acc93974f4e40f863bcd8d06c668162970b1`，`offline_knowledge.json`=`fa1bbc67c05c0cc00c4dfbbcfe8322f45f28bd5ba73a5ea15d2b543f9b20ec45`。✅
- [x] 事项：提交并发布 `0.1.20`；结果：commit `ddf4cba` 已 push `origin/main`；正式 Release 已创建并上传 `app-release.apk`、`app-debug.apk`、`app-inspection.apk`；地址：`https://github.com/kxxhhh/readgb/releases/tag/v0.1.20`。✅

### 2026-08-04 06:37 Asia/Shanghai

- [x] 事项：贯通正文篇次排序和相邻导航；结果：Backend `35 passed`，严格通鉴校验 `30,989/294/1,405`、失败 `0`、空纪年 `0`；离线资源正文 `48,126`、目录 `565/2,556`、百科 `61,696`，全部正文含 `sort_order`。✅
- [x] 事项：验证双角色对话、用户插话、史料证据沙盘和 Edge-TTS 无声修复；结果：Android JVM test、lint、Debug/Inspection/Release 均 `BUILD SUCCESSFUL`，仅保留既有 `ClickableText` 弃用警告；未进行实体设备音频验收。✅
- [x] 事项：构建 `0.1.21` 签名产物；结果：version `18/0.1.21`，Release v2 签名证书 SHA-256 `0bd6d2260b1da032d761c16e7d31fee2767c80362295353e3f7ea10ebd111c57`；Debug `117,373,137` bytes / `52aaf6775c8c66074f28b9ab058f938ec646d58a22d9614408eab676200a0c48`，Inspection `94,872,099` bytes / `c7a1d53ad44e32a4ab8dc47e88275229670bedb81ad52c88e558a92627274fbf`，Release `94,872,099` bytes / `831a2306f9b8df4796bc946f3b04f22ef6c5f84e3eb4ecdf560097f6d68bd1c0`。✅
- [ ] 事项：提交、push 并发布 `0.1.21`；下一步：提交全部源码/测试/文档/离线正文资源，推送 `origin/main`，再用 `gh release create` 上传三种 APK。⏳

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
