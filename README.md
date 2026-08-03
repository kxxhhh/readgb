# 读通鉴

原生 Android 中国史阅读器：把公开的《资治通鉴》内容整理成可搜索、可收藏、可离线阅读的个人阅读工作区。

本仓库当前由两部分组成：

- `android/`：Kotlin + Jetpack Compose 原生客户端，Room 保存本地正文、收藏、历史和笔记。
- `service/`：Python + FastAPI 数据服务，以及合规的公开数据同步、校验和 Android 资产导出工具。

日常阅读不依赖网络，App 不直接请求原网站。网络只用于维护者在项目目录内准备数据或进行本地 Backend 联调。

> 数据准备只访问已确认的公开路径，遵守同源、robots.txt、限速、重试和缓存边界；不登录、不绕过验证码、访问控制或付费墙。

## 当前真实状态

- 新一轮《资治通鉴》同步已在项目内 `service/data/` 断点运行，目标为目录中的 `1,405` 个纪年节点。
- 旧导入内容已清理；清理前快照位于 `service/data/resync-archive-20260803/`，不在 `/tmp`。
- 新同步完成前，`android/app/src/main/assets/` 不包含新的全本正文、目录或百科资产；仅保留古籍字形映射和字词提示资源。
- 详情页、TTS 控制、繁简/异体字展示、动态学习图表和关系图已经接入源码，但完整新内容导入与设备级回归仍是当前任务的一部分。
- 具体 checkpoint、数据库统计、已完成项、未完成项和最近验证命令以 [PROJECT_STATE.md](./PROJECT_STATE.md) 为准。

## 已实现能力

- 原生 Compose + Material 3 UI，不使用 WebView。
- Room/SQLite 离线优先读取，网络失败回退本地数据。
- 首页、分类目录、搜索、详情、收藏、最近阅读、笔记和百科入口。
- 详情页支持原文/译文并读、原文或译文单独阅读、字号、繁简/异体字展示、复制分享、划线笔记、历史上下文和阅读工具。
- Android 系统 TTS、句子级当前朗读状态、自动滚动和睡眠计时器；Edge-TTS 作为可选网络引擎，未配置时不影响本地阅读。
- 本地古籍字形映射表和字词提示表，转换只发生在展示层，不改写原始正文。
- 学习页按实际条目动态聚合朝代篇幅、阅读趋势、人物共现关系和文章下钻，不再使用固定的少量示例节点。
- FastAPI 统一 `{code, message, data}` 响应，提供首页、搜索、分类、目录、详情和百科接口。
- 同步器具备 robots 检查、同源请求、磁盘 JSON 缓存、Retry-After、退避、去重、checkpoint、受控 worker 和原子状态写入。
- Linux systemd 开机续跑，恢复脚本通过项目内锁文件避免重复启动。
- GitHub Actions 已配置 Python 测试/覆盖率和 Android test/lint/debug/release 构建，并启用 pip/Gradle 缓存。

## 技术栈

| 层级 | 技术 | 用途 |
| --- | --- | --- |
| Android | Kotlin、Jetpack Compose、Material 3 | 原生界面和阅读交互 |
| Android 架构 | MVVM、StateFlow、Repository | 状态管理与分层 |
| Android 本地 | Room 2.8、SQLite/FTS | 正文、收藏、历史、笔记与本地搜索 |
| Android 联调 | Retrofit 3、OkHttp、Kotlin Serialization | 只连接本项目 FastAPI |
| Android DI | Hilt | 依赖组装 |
| Backend | Python 3.12+、FastAPI、Uvicorn | REST API 和本地数据服务 |
| 数据处理 | Python 标准库、BeautifulSoup4 | JSON/HTML 解析、校验和缓存 |
| 构建 | Gradle、Docker Compose、GitHub Actions | APK 与服务部署 |

## 快速开始

### 环境

- JDK 21；Android 编译目标 Java 17。
- Android SDK Platform 35、Build Tools 35.0.0。
- Python 3.12+。
- Gradle 构建建议至少提供 8 GB 可用内存；`android/gradle.properties` 已配置 `-Xmx8g`。

### 构建 Android App

```bash
cd android
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Debug APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。未生成完整离线正文资产时，App 仍可使用 OfflineSeed、已有 Room 数据和本地映射/字词资源启动，但不能宣称包含全本正文。

Release 默认不带正式签名。配置完整的 `KEYSTORE_FILE`、`KEY_ALIAS`、`STORE_PASSWORD`、`KEY_PASSWORD` 后，`assembleRelease` 才会使用环境签名；`inspection` 变体只用于本地设备检查。

### 启动本地 Backend

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r service/requirements.txt
python -m pytest -q service/tests
uvicorn app.main:app --app-dir service --reload
```

默认监听 `http://127.0.0.1:8000`，数据库默认是 `data/dutongjian.db`。Android Emulator 联调：

```bash
cd android
./gradlew assembleDebug -PapiBaseUrl=http://10.0.2.2:8000/
```

`apiBaseUrl` 必须以 `/` 结尾，只填写本项目的本地 Backend，不填写目标网站地址。

## 数据同步

同步器只在维护者联网环境运行，所有新数据库、原始 JSON 缓存、checkpoint 和审计快照都放在项目的 `service/data/` 下：

```text
service/data/
├── dutongjian.db
├── tongjian-cache/
├── tongjian-progress.json
└── resync-archive-20260803/
```

`/tmp` 不作为新抓取内容、缓存或 checkpoint 的存储目录。`.tmp` 文件仅用于同一目标目录内的原子替换，写入完成后立即改名，不是内容临时目录。

### 手动续跑

```bash
./scripts/resume_crawler.sh
```

该脚本不清空数据，复用 checkpoint 和缓存，并通过项目内 `service/data/tongjian-sync.lock` 防止重复进程。恢复前先读取状态：

```bash
sed -n '1,240p' PROJECT_STATE.md
git status --short
jq '{total_reigns, completed: (.completed_reign_ids | length), updated_at}' \
  service/data/tongjian-progress.json
```

首次清理旧导入内容时才显式使用 `--reset`，并且只能在确认没有其他同步进程后执行：

```bash
PYTHONPATH=service python -m app.tongjian_sync \
  --allow-public-api \
  --reset \
  --database service/data/dutongjian.db \
  --cache-dir service/data/tongjian-cache \
  --checkpoint service/data/tongjian-progress.json \
  --workers 4 \
  --min-interval 0.5 \
  --respect-robots
```

同步器使用公开的 `/api/table_of_contents` 和 `/api/reign?reign_tongjian_id=...`，默认 4 个受控 worker、请求启动间隔不低于 0.5 秒、robots 检查开启、遇到 429 尊重 `Retry-After`。worker 不是用来绕过限流；请求只在缓存未命中时发出。

### 开机自动续跑

Linux 主机执行：

```bash
./scripts/install_crawler_service.sh
```

脚本安装 `readgb-crawler.service`，服务启动 `scripts/resume_crawler.sh`，不带 `--reset`，失败后自动重启。没有 systemd 作为 PID 1 的容器只能写入 unit 和 enable 链接，由宿主机下一次启动接管；这不代表当前容器内已经启动了第二个同步进程。

### 导出 Android 资产

同步完成并通过全量校验后，从项目内数据库直接导出到项目内 Android assets：

```bash
PYTHONPATH=service python -m app.export_android \
  --database service/data/dutongjian.db \
  --output android/app/src/main/assets/offline_content.ndjson.gz \
  --catalog-output android/app/src/main/assets/offline_catalog.json \
  --knowledge-output android/app/src/main/assets/offline_knowledge.json
```

默认校验 `30,989` 条真实正文、`294` 卷和 `1,405` 个纪年。校验失败不会替换目标资产。开发阶段如确有需要，可显式使用 `--allow-partial --checkpoint ...` 导出已完成章节快照，但必须在 [PROJECT_STATE.md](./PROJECT_STATE.md) 标明它不是全本。

## 项目结构

```text
.
├── android/
│   └── app/src/main/java/com/dutongjian/app/
│       ├── data/       # Retrofit、Room、Repository、TTS 和本地存储
│       ├── domain/     # 领域模型、OfflineSeed、字形/字词与 TTS contract
│       ├── di/         # Hilt、Room、Retrofit、OkHttp 组装
│       └── ui/         # Compose 页面、ViewModel、主题、学习图表
├── service/
│   ├── app/main.py             # FastAPI 路由
│   ├── app/store.py            # SQLite 内容与层级存储
│   ├── app/crawler.py          # HTML robots-aware fetcher
│   ├── app/tongjian_sync.py    # 公开 API 全本同步
│   ├── app/export_android.py   # Android 资产校验和导出
│   └── tests/                  # API、crawler、sync 和解析测试
├── deploy/readgb-crawler.service
├── scripts/resume_crawler.sh
├── scripts/install_crawler_service.sh
├── docs/site-analysis.md       # 真实站点和数据边界
├── plugin.md                   # 功能要求与实现状态
├── PROJECT_STATE.md            # 当前任务清单与恢复日志，单一状态源
├── PROJECT_STATE_HISTORY.md    # 重写前历史状态归档
├── DOCS.md                     # 详细开发文档
└── README.md
```

## 测试和 CI

本地检查：

```bash
python3 -m pytest -q service/tests
python3 -m compileall -q service/app
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

CI 位于 `.github/workflows/ci.yml`：Python 3.12 测试并生成覆盖率报告；Android 使用 JDK 21、SDK 35，执行 JVM 测试、lint、Debug/Release 构建，并缓存 pip 与 Gradle。CI 不执行真实网站同步，也不会把数据库、cache、checkpoint、APK 或签名文件提交到仓库。

最近验证结果和任何失败必须写入 [PROJECT_STATE.md](./PROJECT_STATE.md)，不要在 README 中手工维护易变的进度数字。

## 开发记录约定

每次开始工作都先读取 `PROJECT_STATE.md`，再执行 `git status --short`；每次会改变任务状态、数据路径、部署行为或构建结果的操作，都在同一个文件的任务清单或变更日志中追加记录。完成项在行末添加 `✅`，未完成项保留未勾选并写明阻塞原因或下一步。意外中断后先按恢复章节检查现有进程和 checkpoint，不删除项目内数据，不重新启动第二个爬虫。

详细边界、API、Room 导入规则、测试策略、提交规范见 [DOCS.md](./DOCS.md)。

## 许可证

本项目采用 MIT License，完整文本见 `LICENSE`。
