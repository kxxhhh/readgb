# 📚 读通鉴

> 原生 Android 中国史阅读器：把《资治通鉴》及相关史论整理成可搜索、可收藏、可离线阅读的个人阅读工作区。

读通鉴采用 Kotlin + Jetpack Compose 构建原生 Android 客户端，以 Room 保存本地内容、收藏和阅读历史；Python + FastAPI 数据服务负责本地联调、内容校验和离线资产准备。日常阅读不依赖网络，也不会在运行时请求目标网站。

> ⚖️ 数据准备工具只处理人工确认的公开内容，遵守同源限制、robots 规则、限速、重试和访问边界；不登录、不绕过验证码、权限或付费墙。

## 🧭 快速导航

- [✨ 核心特性](#-核心特性)
- [🧰 技术栈](#-技术栈)
- [🚀 快速开始](#-快速开始)
- [🗂️ 项目结构](#-项目结构)
- [🧪 测试与验证](#-测试与验证)
- [📌 当前状态](#-当前状态)
- [📖 开发者文档](./DOCS.md)
- [📄 开源协议](#-开源协议)

## ✨ 核心特性

- ✅ 原生 Android UI：Kotlin、Jetpack Compose、Material 3，禁止 WebView 套壳。
- ✅ 离线优先阅读：内容来自 APK assets、OfflineSeed 和 Room；网络不可用时仍可启动、浏览和搜索。
- ✅ 多层历史目录：按 section -> volume -> year -> item 浏览栏目、卷、纪年和正文段落。
- ✅ 原文与译文并读：保存繁体原文、简体正文、白话译文、注释和关联标签。
- ✅ 搜索与筛选：支持正文关键词、分类筛选、百科关键词和百科分类。
- ✅ 个人阅读状态：Room 持久化收藏和最近阅读记录。
- ✅ 本地数据服务：FastAPI 提供统一 code/message/data REST envelope，用于本地联调、检查数据和生成资产。
- ✅ 可恢复内容同步：公开《资治通鉴》 API 导入支持限速、磁盘缓存、Retry-After 退避、checkpoint、断点续传和稳定 ID 去重。
- ✅ 安全的资产导出：只有验证到 30,989 条正文、294 卷和 1,405 个纪年后，才允许导出 Android 离线资产。
- ✅ 容器化服务：提供 Dockerfile 和 Docker Compose，SQLite 使用持久化 volume。

## 🧰 技术栈

| 层级 | 技术 | 用途 |
| --- | --- | --- |
| Android | Kotlin、Jetpack Compose、Material 3 | 原生界面、阅读交互和响应式布局 |
| Android 架构 | MVVM、StateFlow、Repository | 状态管理和分层解耦 |
| Android 本地 | Room 2.8、SQLite | 内容缓存、收藏、阅读历史 |
| Android DI | Hilt | 组装 API、数据库和 Repository |
| Android 联调 | Retrofit 3、OkHttp、Kotlin Serialization | 仅用于本地 Backend 联调 |
| Backend | Python 3.12+、FastAPI、Uvicorn | 本地 REST API 和数据检查 |
| 数据层 | SQLite、标准库 sqlite3 | 内容索引、种子、HTTP cache |
| 内容处理 | BeautifulSoup4 | 公开 HTML 解析 |
| 测试 | pytest、JUnit、Coroutines Test | 服务端和 Android 单元测试 |
| 构建 | Gradle 9.4、Docker Compose | APK 构建和服务部署 |

## 🚀 快速开始

### 环境要求

- JDK 21；Android 编译目标为 Java 17。
- Android SDK Platform 35、Build Tools 35。
- Python 3.12+。
- 至少 8 GB 可用内存用于 Gradle 构建。
- 终端用户运行 APK 不需要网络；只有维护者执行公开内容同步时才需要访问目标站点。

### 1. 构建离线 Android App

~~~bash
cd android
./gradlew assembleDebug
~~~

输出：android/app/build/outputs/apk/debug/app-debug.apk。安装后无需启动 Backend，也无需配置远程 API。

应用启动时会先显示 OfflineSeed，然后从 Room 观察本地内容；如果 APK 含有 offline_content.ndjson.gz，Repository 会按批次导入正文；如果资产不存在，则继续使用种子和已有缓存。

### 2. 可选：启动本地 Backend

Backend 只用于本地联调、数据检查和资产准备，不是 App 正常阅读的必需组件。

~~~bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r service/requirements.txt
python -m pytest -q service/tests
uvicorn app.main:app --app-dir service --reload
~~~

服务默认监听 http://127.0.0.1:8000，默认数据库为 data/dutongjian.db。容器方式：

~~~bash
cd service
docker compose up --build
~~~

Android Emulator 联调时可覆盖本地 API 地址：

~~~bash
cd android
./gradlew assembleDebug -PapiBaseUrl=http://10.0.2.2:8000/
~~~

实体设备请改成开发机局域网 IP。apiBaseUrl 必须以 / 结尾，并且只应指向自己的本地 Backend，不应填写 dutongjian.com 或 wiki.dutongjian.com。

### 3. 运行测试和检查

~~~bash
# Backend
python3 -m pytest -q service/tests
python3 -m compileall -q service/app

# Android
cd android
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
~~~

### 4. 维护者准备离线内容

以下命令只在维护者联网环境执行，不属于 App 运行时路径。

同步人工指定的公开 HTML 页面：

~~~bash
cd service
python -m app.sync \
  --base-url https://www.dutongjian.com \
  --path /公开阅读路径 \
  --kind reading \
  --database ../data/dutongjian.db
~~~

导入已确认的公开《资治通鉴》 JSON API：

~~~bash
PYTHONPATH=service python -m app.tongjian_sync \
  --allow-public-api \
  --database service/data/dutongjian.db \
  --cache-dir service/data/tongjian-cache \
  --checkpoint service/data/tongjian-progress.json \
  --min-interval 5.0
~~~

同步器默认每次新请求至少间隔 5 秒，遇到 HTTP 429 会尊重 Retry-After；每个纪年成功后原子更新 checkpoint。完成并校验全本后导出 Android assets：

~~~bash
PYTHONPATH=service python -m app.export_android \
  --database service/data/dutongjian.db \
  --output android/app/src/main/assets/offline_content.ndjson.gz \
  --catalog-output android/app/src/main/assets/offline_catalog.json
~~~

默认导出仍会拒绝不完整数据库，不会把部分抓取结果伪装成全本离线包。若要显式打包 checkpoint 已完成且已通过字段校验的章节快照，可使用：

~~~bash
PYTHONPATH=service python -m app.export_android \
  --allow-partial \
  --database service/data/dutongjian.db \
  --checkpoint service/data/tongjian-progress.json \
  --output android/app/src/main/assets/offline_content.ndjson.gz \
  --catalog-output android/app/src/main/assets/offline_catalog.json
~~~

## 🗂️ 项目结构

~~~text
.
├── android/
│   └── app/src/main/java/com/dutongjian/app/
│       ├── data/       # Retrofit、Room、Entity、Repository 实现
│       ├── domain/     # 领域模型、Repository contract、OfflineSeed
│       ├── di/         # Hilt、Room、Retrofit、OkHttp 组装
│       └── ui/         # Compose 页面、ViewModel、主题
├── service/
│   ├── app/main.py             # FastAPI 路由
│   ├── app/store.py            # SQLite 内容存储
│   ├── app/crawler.py          # robots-aware HTML fetcher
│   ├── app/parsers.py          # HTML parser
│   ├── app/sync.py             # 单页 HTML 同步
│   ├── app/tongjian_sync.py    # 公开 API 全本同步
│   ├── app/export_android.py   # Android 资产导出
│   └── tests/                  # API、parser、crawler、sync 测试
├── docs/site-analysis.md       # 站点和数据边界记录
├── PROJECT_STATE.md            # 增量进度、问题和验证记录
├── README.md
└── DOCS.md
~~~

## 🧪 测试与验证

当前已验证的基线：

- Backend：18 passed。
- Python：python3 -m compileall -q service/app 通过。
- Android：testDebugUnitTest 通过。
- Release APK 默认是 unsigned；正式签名需要由发布环境注入 keystore。

## 📌 当前状态

- 公开目录已确认 294 卷、1,405 个纪年节点；目标结构声明内容范围为 1..30,989。
- 全本同步是独立的长任务，当前进度和最近一次真实统计以 PROJECT_STATE.md 为准。
- `--allow-partial` 只打包 checkpoint 已完成且字段完整的真实章节；在全本导入完成前，不把当前 APK 宣称为完整全本。
- App 的核心阅读路径坚持离线优先；Backend 连接失败时应回退到本地内容，不因 10.0.2.2:8000 不可用而出现空壳首页。

## 🤝 贡献

请先阅读 [📖 开发者文档](./DOCS.md)，了解架构、测试、抓取边界和提交规范。

## 📄 开源协议

本项目采用 MIT License，完整文本见 LICENSE。

---

Made for focused reading of history. 🏮
