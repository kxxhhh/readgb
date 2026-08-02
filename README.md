# 📚 读通鉴

> 一个以原生 Android 为阅读入口、以 FastAPI + SQLite 为数据底座的中国史阅读应用。

读通鉴将《资治通鉴》《通鉴纪事本末》《读通鉴论》和通鉴百科组织成可搜索、可收藏、可离线阅读的内容工作区。Android 端使用 Jetpack Compose 原生实现，不是 WebView 套壳；运行时只请求项目自己的 FastAPI 服务，`source_url` 仅用于内容溯源。

> ⚖️ 数据同步只面向人工指定的公开页面，遵守同源限制、`robots.txt`、请求间隔和退避策略；项目不模拟账号、不绕过登录、验证码、权限或付费墙。

## 🧭 快速导航

- [✨ 核心特性](#-核心特性)
- [🧰 技术栈](#-技术栈)
- [🚀 快速开始](#-快速开始)
- [🗂️ 项目结构](#️-项目结构)
- [📖 开发者文档](./DOCS.md)
- [📌 当前状态](#-当前状态)
- [📄 开源协议](#-开源协议)

## ✨ 核心特性

- ✅ **原生 Android 阅读体验**：Kotlin + Jetpack Compose + Material 3，支持首页、目录、百科、书架和详情页。
- ✅ **多层历史目录**：按 `section → volume → year → item` 浏览《资治通鉴》及相关内容。
- ✅ **阅读工作区**：原文、白话、注释、标签、古本入口、沙盘态势和决策卡视图。
- ✅ **搜索与筛选**：支持条目搜索、分类筛选、百科关键词搜索和百科分类筛选。
- ✅ **个人阅读状态**：使用 Room 保存收藏和最近阅读记录。
- ✅ **离线优先**：内置 `OfflineSeed`，网络不可用时回退到本地种子和 Room 缓存。
- ✅ **统一 REST API**：所有服务端响应使用 `{code, message, data}` envelope，便于 Android 端统一解析。
- ✅ **合规公开内容同步**：支持人工指定的公开 HTML 单页同步，以及带磁盘缓存、checkpoint、断点续传和去重的公开《资治通鉴》 API 导入。
- ✅ **容器化服务**：提供 `Dockerfile` 和 Docker Compose，SQLite 数据使用持久化 volume。

## 🧰 技术栈

| 层级 | 技术 | 用途 |
| --- | --- | --- |
| Android | Kotlin、Jetpack Compose、Material 3 | 原生 UI 与阅读交互 |
| Android 架构 | MVVM、`StateFlow`、Repository | UI 状态管理与分层 |
| Android 网络 | Retrofit 3、OkHttp、Kotlin Serialization | 调用 FastAPI REST API |
| Android 本地存储 | Room 2.8、SQLite | 阅读内容缓存、收藏、历史 |
| Android 依赖注入 | Hilt | 组装 Retrofit、Room 和 Repository |
| Backend | Python 3.12+、FastAPI、Uvicorn | JSON API 服务 |
| 数据存储 | SQLite（标准库） | 内容索引与 HTTP cache |
| HTML 处理 | BeautifulSoup4 | 解析调用方提供的公开 HTML |
| 测试 | pytest、pytest-cov、JUnit、Kotlin Coroutines Test | 服务端与 ViewModel 测试 |
| 构建与部署 | Gradle 9.4.0、Docker Compose | Android 构建与服务部署 |

## 🚀 快速开始

### 环境要求

- Linux Dev Container 或 GitHub Codespace（其他系统也可，但命令需按环境调整）。
- Python 3.12+。
- JDK 21；Android 编译目标为 Java 17。
- Android SDK Platform 35、Build Tools 35、Platform Tools。
- 至少 8 GB 可用内存用于 Gradle 构建。
- 若要访问真实公开页面或公开 API，需要能够解析 `dutongjian.com` 的网络环境。

### 1. 启动 Backend

在仓库根目录执行：

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r service/requirements.txt
python -m pytest -q service/tests
uvicorn app.main:app --app-dir service --reload
```

服务默认监听 `http://127.0.0.1:8000`，SQLite 默认写入 `data/dutongjian.db`。启动后可访问：

```bash
curl http://127.0.0.1:8000/api/home
```

也可以使用 Docker Compose：

```bash
cd service
docker compose up --build
```

### 2. 构建 Android

```bash
cd android
./gradlew assembleDebug
```

Debug APK 输出在 `android/app/build/outputs/apk/debug/app-debug.apk`。Android Emulator 访问宿主机服务时默认使用 `10.0.2.2:8000`。

如需覆盖 API 地址，必须保留末尾 `/`：

```bash
./gradlew assembleDebug -PapiBaseUrl=http://10.0.2.2:8000/
```

实体设备应把地址改为开发机在局域网中的 IP，例如 `http://192.168.1.20:8000/`，并确保防火墙允许访问 8000 端口。

### 3. 运行测试与质量检查

```bash
# Backend
python3 -m pytest -q service/tests
python3 -m compileall -q service/app

# Android
cd android
./gradlew testDebugUnitTest
./gradlew lintDebug
```

### 4. 按需同步公开内容

同步器只处理一个人工指定的公开路径，不会递归遍历站点：

```bash
cd service
python -m app.sync \
  --base-url https://www.dutongjian.com \
  --path /公开阅读路径 \
  --kind reading \
  --database ../data/dutongjian.db
```

百科页面将 `--kind` 改为 `knowledge`，并使用百科站点的 `--base-url`。运行前应确认目标页面公开可访问且 `robots.txt` 明确允许；无法读取 robots 规则时，抓取器默认拒绝请求。

如需导入已确认的公开《资治通鉴》 API，可使用可恢复同步器。该命令必须显式传入 `--allow-public-api`，并建议保留 cache 与 checkpoint：

```bash
PYTHONPATH=service python -m app.tongjian_sync \
  --allow-public-api \
  --database service/data/dutongjian.db \
  --cache-dir service/data/tongjian-cache \
  --checkpoint service/data/tongjian-progress.json \
  --min-interval 5.0
```

同步器只访问公开、未登录的 JSON API；默认每次请求至少间隔 5 秒，缓存单个请求结果，并在每个纪年节点完成后原子更新 checkpoint。遇到 HTTP 429 时会尊重 `Retry-After`。真实全本同步仍未完成，不能将当前部分数据库视为包含完整 `30989` 段正文。

## 🗂️ 项目结构

```text
.
├── android/                         # 原生 Android 单模块工程
│   └── app/src/main/java/com/dutongjian/app/
│       ├── data/                    # Retrofit、Room、Repository 实现
│       ├── domain/                  # 领域模型、Repository 接口、离线种子
│       ├── di/                      # Hilt、Retrofit、Room 依赖注入
│       └── ui/                      # Compose 页面、ViewModel、主题
├── service/                         # Python 数据服务
│   ├── app/main.py                  # FastAPI 路由与响应 envelope
│   ├── app/models.py                # 内容领域模型
│   ├── app/store.py                 # SQLite 内容索引与 HTTP cache
│   ├── app/crawler.py               # robots-aware 抓取器
│   ├── app/parsers.py               # 公开 HTML 结构化解析器
│   ├── app/sync.py                  # 公开 HTML 单页同步服务与 CLI
│   ├── app/tongjian_sync.py         # 公开 API 限速、缓存、断点续传同步器
│   ├── data/                        # API 同步 cache/checkpoint/SQLite 运行数据
│   └── tests/                       # API、解析器、抓取器、同步测试
├── data/                            # 默认 SQLite 运行数据，已被 gitignore 排除
├── docs/site-analysis.md            # 站点分析与数据边界记录
├── PROJECT_STATE.md                 # 增量开发状态与已知问题
├── README.md                        # 项目首页
└── DOCS.md                          # 详细开发文档
```

更详细的架构、API、调试和贡献说明请阅读：[📖 DOCS.md](./DOCS.md)。

## 📌 当前状态

- ✅ Backend 当前为 **17 passed**，包含 429 `Retry-After` 退避测试。
- ✅ `python3 -m compileall -q service/app` 当前通过。
- ✅ Android `testDebugUnitTest` 当前通过；无网络时默认显示 `OfflineSeed`，若构建包含完整资产则导入 Room。
- ⚠️ Release 构建目前是 unsigned 产物；签名配置应由部署环境注入，仓库不保存 keystore 或凭据。
- ⚠️ 全本同步仍在进行；当前数据服务包含部分真实公开 API 内容和待清理的本地演示种子，不代表已经拥有完整 `30989` 段正文。实时进度见 [PROJECT_STATE.md](./PROJECT_STATE.md)。

## 📄 开源协议

本项目采用 [MIT License](./LICENSE)。完整许可文本请查看仓库根目录的 [LICENSE](./LICENSE) 文件。

---

Made for focused reading of history. 🏮
