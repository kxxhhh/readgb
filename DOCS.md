# 读通鉴开发文档

[返回 README](./README.md)

本文档描述当前仓库真实存在的架构、数据边界、运行命令、恢复方式和提交规则。易变的任务进度、同步数量和最近测试结果只记录在 PROJECT_STATE.md；DEVELOPMENT_LOG.md 已与其合并，只作为入口说明；PROJECT_STATE_HISTORY.md 是重写前的只读归档，不作为当前事实来源。

## 1. 当前范围

项目是“维护者联网准备数据，Android 离线消费”的单仓库：

~~~text
公开 dutongjian.com JSON/HTML
        │ 维护者同步进程，robots + 同源 + 限速 + 缓存 + checkpoint
        ▼
service/app/tongjian_sync.py / sync.py / parsers.py
        │ 结构化 Item、目录、关联字段
        ▼
service/data/dutongjian.db
        │ 校验后导出
        ▼
android/app/src/main/assets/
        │ APK 安装后批量导入
        ▼
Room reading_items + FTS + 本地状态
        ▼
Compose UI / TTS / 学习图表 / 笔记 / 可选 AI
~~~

当前重抓任务已经删除旧的《资治通鉴》导入内容，新的同步直接写入项目内 service/data/。完整同步结束前不生成新的古籍正文资产；清理前的旧数据快照也保存在项目内 service/data/resync-archive-20260803/，没有把新内容放进 /tmp。

Android 运行时不请求原网站：Retrofit 只连接本项目的 FastAPI；source_url 仅是来源元数据。没有原站登录、会员、支付、收藏同步或绕过权限的代码。

## 2. 仓库结构和依赖方向

| 层 | 关键路径 | 职责 |
| --- | --- | --- |
| Android UI | android/app/src/main/java/com/dutongjian/app/ui/ | Compose 首页、目录、详情、学习、设置、笔记和主题 |
| Android ViewModel | ui/ReadingViewModel.kt | StateFlow、搜索、目录、收藏、历史、百科、AI/TTS 状态 |
| Android domain | domain/model/、domain/repository/、domain/text/、domain/tts/ | 领域模型、Repository contract、字形/字词和 TTS contract |
| Android data | data/、data/local/、data/network/、data/tts/ | Room、FTS、资产导入、Retrofit、TTS 和持久化状态 |
| Android DI | di/AppModule.kt | Hilt、Room、Retrofit、OkHttp、JSON 组装 |
| FastAPI | service/app/main.py | REST 路由和 {code,message,data} envelope |
| SQLite Store | service/app/store.py | items、sections、volumes、years、knowledge_entries、HTTP cache |
| HTML crawler | service/app/crawler.py | 同源、robots、限速、重试、HTML cache |
| API sync | service/app/tongjian_sync.py | 公开《资治通鉴》目录和纪年 API 的断点同步 |
| Export | service/app/export_android.py | 全量/阶段性资产校验与原子导出 |
| Deploy | deploy/、scripts/ | systemd unit、安装和断点恢复 |

Android 依赖方向为 ui -> domain <- data。UI 不直接访问 Room 或 Retrofit；Repository 负责远程结果、本地 Room、APK 资产和种子 fallback 的边界。

## 3. 已实现的 Android 行为

### 3.1 离线优先与导入

ReadingRepositoryImpl 的顺序如下：

1. 如果没有打包正文资产，写入 OfflineSeed（只补缺失项）。
2. 如果存在 offline_content.ndjson 或旧的 .gz 资产，先完整解析，再删除旧的 zztj-* 和 zizhi-tongjian-* 导入记录，最后按 500 条批量 upsert。
3. 解析失败时不删除现有 Room 数据，并记录错误；不会用空或半解析资产覆盖本地内容。
4. 导入时按 ID 保留本地 isFavorite 和 lastOpenedAt。
5. 目录从 offline_catalog.json 读取；百科从 offline_knowledge.json 读取；缺资产时才回退种子。

截至 2026-08-03 21:45，新一轮同步 checkpoint 为 `1069/1405`，真实正文 `17,900` 条；因此 `offline_content.ndjson.gz`、`offline_catalog.json`、`offline_knowledge.json` 尚未按本轮全量数据重新生成。`classical_char_map.json` 和 `classical_glossary.json` 是独立的本地静态资源，当前已经在 assets 中。

### 3.2 阅读详情页

详情页已经从纵向堆叠控制改成固定顶部信息、正文工作区和底部工具入口：

- 顶部显示返回、标题和收藏。
- 正文头部显示卷/纪年/朝代、摘要、标签和段落数。
- 原文、译文并读，以及原文/译文单独模式。
- 字号、繁简/异体字展示、复制、分享和划线笔记。
- TTS 当前句状态与自动滚动、15/30 分钟和读完当前篇停止。
- 历史上下文、关联人物/地点/官职、字词提示、知识入口和 AI 工具通过可收起工具面板进入。

底层 original、translation、notes 和原始关联 JSON 保持不改写；展示层转换只由 ClassicalScriptMapper 完成。

### 3.3 学习页和关系图

学习页不再使用固定的少量示例节点：

- 柱状图支持纪年与纪/朝代两个聚合范围；从当前 state.items 计算真实篇目数，所有实际分组通过 LazyRow 横向浏览，点选分组后下钻真实文章。
- 人物关系从文章 notes 中的公开人物关联动态计算共现；人物索引保留当前数据中的全部人物，选择中心人物后只绘制其高频邻接网络，并可从关系行点回正文。
- 趋势图使用本地阅读统计中的最近 7 天数据。

这能随真实资产规模扩展，但当前数据库仍只完成阶段性纪年，图表数量和关系覆盖不会被误写成全本。Android 设备级性能回归不纳入当前交付范围。

### 3.4 TTS、AI、Widget 和本地工具

- LocalTtsEngine 使用 Android 系统中文 TTS；EdgeTTSEngine 是可选网络引擎，失败时显示错误，不伪装成本地合成。
- TtsController 保存当前句、播放队列和睡眠计时器状态。
- AiRepositoryImpl 支持用户配置 OpenAI 兼容接口或本机模型服务，Key 只存本机；没有配置时不发远程请求。
- AI 生成结果已写入 Room `ai_results`，详情页支持隐藏当前结果、重开历史结果和删除记录；真实模型请求仍需可用服务端到端验证。
- QuoteWidgetProvider 使用 RemoteViews 展示本地名句和入口。
- ClassicalGlossary 和 ClassicalScriptMapper 在应用启动时从 assets 加载；没有资产时使用内置 fallback 映射。

AI 的真实模型质量和 Edge-TTS 网络连通性尚未在当前环境完成端到端验证；设备安装、Widget 桌面行为和 TTS 音频输出不纳入当前交付范围。

## 4. FastAPI 接口

成功响应：

~~~json
{"code": 0, "message": "success", "data": {}}
~~~

错误响应仍使用同一结构，例如详情不存在时 code=404、HTTP 404。

| Method | Path | 作用 |
| --- | --- | --- |
| GET | /api/home | 首页条目、分类和 section |
| GET | /api/search?q=&limit= | 标题、正文、原文、译文和标签搜索 |
| GET | /api/items?category=&year_id=&limit= | 分类/纪年列表 |
| GET | /api/detail/{id} | 单条详情 |
| GET | /api/sections | section 目录 |
| GET | /api/sections/{section_id}/volumes | 卷目录 |
| GET | /api/volumes/{volume_id}/years | 纪年目录 |
| GET | /api/years/{year_id}/items | 纪年正文 |
| GET | /api/knowledge?q=&category=&limit= | 百科列表和分类 |
| GET | /api/knowledge/{entry_id} | 百科详情 |

DUTONGJIAN_DB 控制 FastAPI 数据库路径，默认 data/dutongjian.db；CORS_ORIGINS 控制逗号分隔的允许来源。同步命令使用显式 --database service/data/dutongjian.db，不会依赖 Backend 的默认路径。

### 数据模型

真实正文 Item 的稳定 ID 为 zztj-<公开 tongjian_id>，字段包括：

title、category、dynasty、summary、content、original、translation、notes、tags、section、volume_id、year_id、source_url、updated_at。

notes 保存完整公开关联对象 JSON；tags 由主题、人物、地点和官职字段去重生成。同步器只派生标题和摘要，不丢弃原始 content、content_jianti_auto、content_fanyi 或关联对象。

## 5. 同步器和数据边界

### 5.1 已确认的公开入口

同步器使用：

- https://www.dutongjian.com/api/table_of_contents
- https://www.dutongjian.com/api/reign?reign_tongjian_id=<id>

目录展开为 juan_list -> emperor_list -> reign_list，当前目标是 294 卷、1,405 个纪年节点和 30,989 条正文。站点和字段说明见 docs/site-analysis.md。

### 5.2 请求控制

TongjianApiClient 当前默认：

~~~python
min_interval=5.0
workers=4
retries=3
timeout=30.0
respect_robots=True  # CLI 默认
~~~

实现约束：

- 先查项目内 JSON cache，cache 命中不发网络请求。
- robots.txt 检查在非缓存请求前执行；无法读取时拒绝请求。
- URL 必须由配置的同一 base URL 生成。
- 请求启动使用全局节奏锁；当前 5 秒间隔是根据实际 HTTP 429 反馈设置的，worker 数量受控，不用于绕过站点限流。
- HTTP 429 读取 Retry-After，其他错误指数退避，最大等待 30 秒。
- 每个纪年成功入库后原子更新 checkpoint。
- SQLite 主键和公开 ID 去重；原始 API 返回体写入项目内 cache。

--reset 只做首次清理：删除旧通鉴 item、资治通鉴卷/年层级、knowledge_entries、cache 和 checkpoint。服务恢复脚本和 systemd unit 都不带 --reset。

### 5.3 项目内路径约定

| 路径 | 用途 | 是否作为新内容源 |
| --- | --- | --- |
| service/data/dutongjian.db | 当前同步数据库 | 是 |
| service/data/tongjian-cache/ | 原始 API JSON cache | 是 |
| service/data/tongjian-progress.json | 断点进度 | 是 |
| service/data/resync-archive-20260803/ | 旧导入内容审计快照 | 否，仅恢复/比对 |
| android/app/src/main/assets/ | 校验后进入 APK 的最终资产 | 是 |

同步阶段不直接写 Android assets。原始抓取结果先保存在 `service/data/dutongjian.db` 和 `service/data/tongjian-cache/`，断点在 `service/data/tongjian-progress.json`；只有正文、卷和纪年达到目标数量并通过字段校验后，才原子生成 `offline_content.ndjson.gz`、`offline_catalog.json` 和 `offline_knowledge.json`。

.tmp 后缀只用于目标文件同目录的原子替换；它不是 /tmp 文件夹，也不承载独立内容。

宿主机持久化边界：运行环境只保证 `/mnt/workspace` 范围内的数据不会被定期清理，`/mnt/workspace` 之外的内容可能在实例运行一段时间后被清空。当前项目的抓取数据库、JSON cache、checkpoint、锁和日志必须全部位于 `/mnt/workspace/readgb/service/data/`；启动或恢复前先核对项目绝对路径和数据路径。禁止使用 `/tmp`、宿主机其他目录或未确认的相对路径承载新抓取内容。

## 6. 断点恢复和开机自启

每次开发先读取 PROJECT_STATE.md，检查进程和 checkpoint，再行动：

~~~bash
cd /mnt/workspace/readgb
sed -n '1,240p' PROJECT_STATE.md
git status --short
ps -eo pid,lstart,etime,pcpu,pmem,args | rg 'app.tongjian_sync|resume_crawler|readgb-crawler' | rg -v 'rg '
jq '{total_reigns, completed: (.completed_reign_ids | length), failed: (.failed_reign_ids // [] | length), updated_at}' service/data/tongjian-progress.json
~~~

正常恢复：

~~~bash
./scripts/resume_crawler.sh
~~~

脚本使用 service/data/tongjian-sync.lock。已有同步进程持锁时，脚本退出并保留原进程，不会启动第二个任务。脚本拒绝 --reset，清理必须手动执行并先确认没有同步进程。

安装开机服务：

~~~bash
./scripts/install_crawler_service.sh
~~~

unit 的 ExecStart 指向恢复脚本，Restart=on-failure，依赖 network-online.target，工作目录和所有数据路径固定为当前项目。容器没有 systemd PID 1 时，安装脚本仍安装 unit 和 enable 链接，但当前容器不会凭空获得宿主机的 systemd 生命周期。

## 7. 导出与 Android 资产

service/app/export_android.py 有三种边界：

- 默认全量导出：严格要求 30,989 条真实 zztj-*、294 卷和 1,405 年，并校验正文、原文、译文和层级字段。
- --allow-partial --checkpoint：仅导出 checkpoint 已完成纪年的阶段性快照，适合开发检查，不代表全本。
- 资产写入先写同目录 .tmp 再 replace，校验或进程失败不会替换旧目标。

全量导出命令：

~~~bash
PYTHONPATH=service python -m app.export_android \
  --database service/data/dutongjian.db \
  --output android/app/src/main/assets/offline_content.ndjson.gz \
  --catalog-output android/app/src/main/assets/offline_catalog.json \
  --knowledge-output android/app/src/main/assets/offline_knowledge.json
~~~

当前新抓取未达到全量门槛，因此不要运行全量导出，也不要把阶段性数据伪装成完整资产。导出后必须重新运行 Android 测试、构建并更新状态文件中的数量、版本标识和校验结果。

### 7.1 数据集覆盖报告

同步期间可以运行只读报告观察真实正文、卷、纪年、空纪年、字段完整性、关联 JSON 和 checkpoint：

~~~bash
PYTHONPATH=service .venv/bin/python -m app.validate_tongjian \
  --database service/data/dutongjian.db \
  --checkpoint service/data/tongjian-progress.json
~~~

`--strict` 只在 `30,989` 条真实 `zztj-*` 正文、`294` 卷、`1,405` 纪年、零空纪年、零字段/层级/关联错误且 checkpoint 无失败时返回成功。该报告只读，不会改变数据库、cache 或 checkpoint。

## 8. 本地开发和测试

Backend：

~~~bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r service/requirements.txt
python -m pytest -q service/tests
python -m compileall -q service/app
uvicorn app.main:app --app-dir service --reload --log-level debug
~~~

Android：

~~~bash
cd android
./gradlew testDebugUnitTest
./gradlew :app:compileDebugKotlin
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
~~~

CI：.github/workflows/ci.yml 使用 Python 3.12 和 JDK 21，执行 Backend 覆盖率测试、Android JVM test、lint、Debug/Release 构建，并缓存 pip/Gradle。CI 不触发真实站点同步。

提交前至少执行：

~~~bash
python3 -m pytest -q service/tests
python3 -m compileall -q service/app
git diff --check
cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
~~~

Android 设备安装、TTS 音频、Widget、Edge-TTS 和 AI 的设备级验收不纳入当前交付范围；本轮以 Backend 测试、Android JVM test、lint、Debug/Release 构建和 APK 产物为验收依据。仓库中的 `run_codex_autodev.sh` 仅是历史自动化脚本，不得作为本轮模拟器调试入口。

## 9. 文档和任务记录协议

PROJECT_STATE.md 同时承担项目状态、任务清单和可恢复日志；DEVELOPMENT_LOG.md 已合并到它，不在两个文件中重复记录。每次开发必须：

1. 开始前读取 PROJECT_STATE.md 和 git status --short。
2. 先在当前任务清单中写明本轮目标、涉及路径和恢复方式。
3. 做完一项就在任务行末添加 ✅；进行中或未完成项保持未勾选，并写明下一步/阻塞。
4. 爬虫、构建、测试、部署或文档发生变化时，在变更日志追加时间、命令、结果和实际路径。
5. 意外中断后先复用同一 checkpoint/cache；不删除项目内数据，不启动重复同步。
6. 用户要求推送时，提交前执行测试和 git diff --check，提交后确认远端分支和 commit。

### 记录格式

任务清单统一使用 Markdown 复选框，并把完成标记放在任务行末：

```text
- [ ] 目标：说明本轮要完成的行为；路径：`涉及文件`；恢复：`恢复命令或 checkpoint`；下一步：说明未完成原因。
- [x] 目标：说明已经完成的行为；结果：给出实际数量、命令或验收结论。✅
```

未完成项必须保留 `[ ]`，并写清下一步或阻塞原因；不能只写“待处理”。完成项必须在同一行末尾追加 `✅`，不能用文档标题或总结段落代替任务状态。

变更日志统一追加在 PROJECT_STATE.md 的“变更日志”下，使用本地时间和固定字段：

```text
### YYYY-MM-DD HH:MM Asia/Shanghai
- [x] 事项：实际执行的动作。命令：`...`；结果：实际输出或数量；路径：`...`；恢复：`...`。✅
- [ ] 事项：尚未完成的动作。阻塞：说明原因；下一步：说明恢复动作。
```

同步、构建、测试、部署、数据路径或文档变化都必须追加记录；日志只写已观察到的事实，不把预期结果、阶段性数据或未运行的设备验收写成完成。

重写前的长历史保存在 PROJECT_STATE_HISTORY.md，只用于追溯，不应把其中的旧数量、旧路径或旧构建结论复制回活动文档。

## 10. 当前未完成项

这些不是已实现声明，实际清单以 PROJECT_STATE.md 为准：

- [ ] 数据门槛：完成 `1,405/1,405` 纪年并校验 `30,989` 条真实正文、唯一 ID、必填字段、卷/纪年外键、关联 JSON 和零空纪年；当前 `1069/1405`，下一步运行 `app.validate_tongjian --strict`。
- [ ] 离线资产：严格通过数据门槛后生成并导入正文、目录、百科三个 Android assets，再从 APK 内容核对数量和 SHA-256。
- [ ] 百科和专题：完成全量人物/地点/官职/专题/决策索引，以及典章制度、经济史的可追溯结构化标注。
- [ ] 研读验收：全本规模下验证图表滚动、节点不重叠、文字不截断、关系准确性、人物/时期覆盖和搜索性能。
- [ ] 用户看板：将已有的正文/纪年/卷覆盖率卡片与全量数据复核；“历史上的今天”仍需可信月日历法映射和自动刷新。
- [ ] 设备与声音：真实 Android 设备上复验安装、详情滚动、系统 TTS、Widget、Edge-TTS 音频和网络失败提示；当前无实体设备，模拟器无 KVM。
- [ ] AI 端到端：用本地或用户提供的 OpenAI-compatible 服务验证请求、错误、超时和结果展示；结果保存/删除/重开已实现，仍需补语法高亮、反事实上下文边界、人物对话事实约束和会话界面。
- [ ] 战役沙盘：在确认公开结构化数据后补地图、兵力、粮道和决策模型；当前仅有正文注释沙盘卡。
- [ ] 发布：Codespace 注入生产 keystore 后完成签名构建；`.github/workflows/release.yml` 已加入 APK 校验、SHA-256 和 gh release 自动化，但仍需真实 secrets 演练。gh 登录/push 已可用，但不是生产签名凭据。
