# 读通鉴

原生 Android 中国史阅读器：把公开的《资治通鉴》内容整理成可搜索、可收藏、可离线阅读的个人阅读工作区。

## 仓库范围

- android/：Kotlin + Jetpack Compose 原生客户端，Room 保存正文、收藏、历史和笔记。
- service/：Python + FastAPI 数据服务、公开数据同步、校验和 Android 资产导出。
- deploy/、scripts/：systemd 开机续跑、锁文件和恢复脚本。
- PROJECT_STATE.md：当前任务清单、恢复入口和唯一开发日志。
- DOCS.md：稳定架构、接口、同步规则、测试命令。
- DEVELOPMENT_LOG.md：已合并到 PROJECT_STATE.md 的日志入口说明。

App 日常阅读不依赖网络，也不直接请求原网站。Retrofit 只用于连接本项目 FastAPI；source_url 只保存来源元数据。

## 当前实际边界

- 公开同步目标是 294 卷、1,405 个纪年节点、30,989 条正文。
- 当前新一轮同步仍是阶段性数据，不能称为全本；实时 checkpoint、数据库数量、PID、速度和测试结果只看 PROJECT_STATE.md。
- 新同步数据库、原始 JSON cache、checkpoint 和旧内容审计快照固定在 service/data/，不把新内容放到 /tmp。
- 全量正文、目录、百科资产必须通过严格数量和字段校验后才可导出进 APK。
- 不登录、不绕过验证码、访问控制、robots 或付费墙；受控 worker 不用于绕过站点限流。

## 已实现能力

- Compose + Material 3 原生 UI，不使用 WebView。
- 离线优先的 Room/SQLite 内容、收藏、最近阅读、笔记和 FTS 搜索。
- 首页、目录、搜索、详情、书架、年表、百科和研读页。
- 详情页支持原文/译文并读、原文/白话单独模式、字号、繁简/异体字展示、复制、分享、划线笔记和历史上下文。
- 系统 TTS 控制器、句子状态、自动滚动和睡眠计时器代码；Edge-TTS 是可选网络引擎，真实音频仍需设备验证。
- 研读页的纪年/纪·朝代篇目柱状图：按当前真实条目动态聚合，全部实际分组可横向浏览，点选后进入史料下钻。
- 研读页的人物索引和关系图：从 notes 的公开结构化人物字段计算覆盖人物、共现关系，选择中心人物后查看网络和正文入口。
- FastAPI 统一 {code, message, data} 响应，提供首页、搜索、目录、详情和百科接口。
- 同步器支持 cache、robots、同源 URL、Retry-After、退避、去重、4 worker 受控并发和原子 checkpoint。
- Linux systemd 开机续跑，恢复脚本使用项目内锁避免重复同步。

## 技术栈

| 层级 | 技术 | 用途 |
| --- | --- | --- |
| Android | Kotlin、Jetpack Compose、Material 3 | 原生界面和阅读交互 |
| Android 架构 | MVVM、StateFlow、Repository | 状态管理和分层 |
| Android 本地 | Room、SQLite/FTS | 正文、收藏、历史、笔记和搜索 |
| Android 网络 | Retrofit、OkHttp、Kotlin Serialization | 只连接本项目 FastAPI |
| Android DI | Hilt | 依赖组装 |
| Backend | Python 3.12+、FastAPI、Uvicorn | REST API 和本地服务 |
| 数据处理 | Python 标准库、BeautifulSoup4 | 同步、解析、校验和缓存 |
| 构建 | Gradle、Docker Compose、GitHub Actions | APK 与服务部署 |

## 快速开始

环境要求：JDK 21、Android SDK Platform 35、Build Tools 35.0.0、Python 3.12+。Android Gradle 构建建议至少 8 GB 可用内存。

### Android

~~~bash
cd android
./gradlew testDebugUnitTest
./gradlew :app:compileDebugKotlin
./gradlew lintDebug
./gradlew assembleDebug
~~~

Debug APK 输出到 android/app/build/outputs/apk/debug/app-debug.apk。没有全量离线资产时，App 仍可用 OfflineSeed、Room 和本地字形/字词资源启动，但不能宣称含有全本正文。

### Backend

~~~bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r service/requirements.txt
python -m pytest -q service/tests
python -m compileall -q service/app
uvicorn app.main:app --app-dir service --reload
~~~

默认服务地址是 http://127.0.0.1:8000，数据库默认值只适合普通 Backend 联调。同步任务使用 service/data/dutongjian.db 的显式路径。

## 数据同步

日常续跑：

~~~bash
./scripts/resume_crawler.sh
~~~

恢复脚本固定使用：

~~~text
database:  service/data/dutongjian.db
cache:     service/data/tongjian-cache/
checkpoint: service/data/tongjian-progress.json
lock:      service/data/tongjian-sync.lock
archive:   service/data/resync-archive-20260803/
~~~

宿主机只保证 `/mnt/workspace` 范围内的数据不会被定期清理；`/mnt/workspace` 之外的内容可能在实例运行一段时间后被清空。抓取数据库、原始 JSON cache、checkpoint、锁和日志必须使用 `/mnt/workspace/readgb/service/data/` 下的路径，启动前应核对项目绝对路径和各数据路径。禁止把新抓取内容放到 `/tmp`、宿主机其他目录或未确认的相对路径。

同步器默认使用 4 个 worker、5 秒最小请求启动间隔、robots 检查和 Retry-After 共享冷却。cache 命中不发请求；单个纪年网络、解析或入库失败会记录到 failed_reign_ids/last_errors，不丢失其他已完成结果。5 秒间隔是根据目标站点 429 反馈调整的，不能为了提速擅自降低。

查看状态：

~~~bash
sed -n '1,260p' PROJECT_STATE.md
git status --short
ps -eo pid,lstart,etime,pcpu,pmem,args | rg 'app.tongjian_sync|resume_crawler|readgb-crawler' | rg -v 'rg '
jq '{total_reigns, completed: (.completed_reign_ids | length), failed: (.failed_reign_ids // [] | length), updated_at}' service/data/tongjian-progress.json
~~~

不要在已有进程运行时再启动一个同步任务。resume_crawler.sh 会通过锁文件拒绝重复启动。只有首次清理且确认没有同步进程时才使用 --reset：

~~~bash
PYTHONPATH=service python -m app.tongjian_sync \
  --allow-public-api \
  --reset \
  --database service/data/dutongjian.db \
  --cache-dir service/data/tongjian-cache \
  --checkpoint service/data/tongjian-progress.json \
  --workers 4 \
  --min-interval 5.0 \
  --respect-robots
~~~

机器重启后不要删除数据库、cache 或 checkpoint，先按 PROJECT_STATE.md 的恢复入口检查，再执行正常续跑。

### 开机续跑

~~~bash
./scripts/install_crawler_service.sh
~~~

systemd unit 使用 scripts/resume_crawler.sh，不带 --reset，失败后自动重启。容器没有 systemd PID 1 时，安装脚本只能留下 unit 和 enable 链接，宿主机启动后接管。

### Android 资产导出

同步完成并通过严格校验后：

~~~bash
PYTHONPATH=service python -m app.export_android \
  --database service/data/dutongjian.db \
  --output android/app/src/main/assets/offline_content.ndjson.gz \
  --catalog-output android/app/src/main/assets/offline_catalog.json \
  --knowledge-output android/app/src/main/assets/offline_knowledge.json
~~~

默认要求 30,989 条真实正文、294 卷和 1,405 个纪年。阶段性导出必须显式使用 --allow-partial --checkpoint，并在 PROJECT_STATE.md 标明快照范围。

## 项目结构

~~~text
android/app/src/main/java/com/dutongjian/app/
  data/       Room、Retrofit、资产导入、TTS 和本地存储
  domain/     领域模型、字形/字词、TTS contract
  ui/         Compose 页面、ViewModel、主题、研读图表
service/app/
  main.py             FastAPI 路由
  store.py            SQLite 内容与目录存储
  crawler.py          HTML robots-aware fetcher
  parsers.py          纯 HTML 解析器
  tongjian_sync.py    公开 JSON API 多线程断点同步
  export_android.py   Android 资产校验和导出
service/tests/        API、crawler、parser、sync 测试
deploy/               systemd unit
scripts/               续跑和安装脚本
docs/site-analysis.md  站点和数据边界
plugin.md              功能验收清单
PROJECT_STATE.md       当前任务清单与合并日志
DOCS.md               详细开发文档
~~~

## 测试和记录

提交前至少执行：

~~~bash
python3 -m pytest -q service/tests
python3 -m compileall -q service/app
git diff --check
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
~~~

CI 位于 .github/workflows/ci.yml，执行 Python 测试/覆盖率和 Android JVM test、lint、Debug/Release 构建；CI 不执行真实网站同步，也不提交数据库、cache、checkpoint、APK 或 keystore。

每次开发开始前先读 PROJECT_STATE.md，再执行 git status --short；开始时把目标写入任务清单，完成项行末加 ✅，未完成项保留 [ ] 并写明下一步。同步、构建、测试、部署和数据路径变化追加到 PROJECT_STATE.md 的变更日志。意外中断后复用原 checkpoint/cache，不启动重复爬虫。

记录格式固定为：任务清单使用 `- [ ]` 表示未完成、`- [x]` 表示已完成，已完成行末必须追加 `✅`；未完成行必须写下一步或阻塞原因。变更日志按 `### YYYY-MM-DD HH:MM Asia/Shanghai` 分段，每项写明事项、实际命令、结果/数量、产物路径和恢复方式。只记录已经观察到的事实；阶段性同步数据不能写成全本，设备级验收不属于本轮交付范围。

详细架构、API、Room 导入规则和同步边界见 DOCS.md；站点字段见 docs/site-analysis.md；功能验收见 plugin.md。

许可证：MIT，见 LICENSE。
