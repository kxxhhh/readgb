# Dutongjian Android 重构项目状态

更新时间：2026-08-01

## 新会话启动协议

1. 首先读取本文件。
2. 然后执行 `git status --short`，只查看工作区状态，不读取 `.git` 内部对象。
3. 按“下一步任务”继续工作。
4. 不重复读取已经确认且没有变化的大型文件；默认跳过 `node_modules`、`build`、`.gradle`、Gradle cache 和大型日志。
5. 不删除现有 Plugin 或 Skill；当前会话只加载实际需要的技能。

## 项目目标

将 `https://www.dutongjian.com/` 重构为原生 Android App 加数据服务：

- 原生 Kotlin Android App，禁止 WebView 套壳。
- 提供首页、分类、搜索、详情、收藏、历史记录和离线缓存能力。
- 提供 FastAPI REST API 和合规的数据采集服务。
- 支持 SQLite 缓存、去重、限速、重试退避与 robots.txt 检查。
- 交付完整源码、README、部署说明、测试结果和可编译 APK。

## 当前阶段

当前处于后端数据服务实现阶段，Android 工程尚未创建。

- 站点分析：已完成基础分析。
- API/数据源分析：未发现可确认的官方 JSON API；待在具备站点 DNS 的环境中进一步抓取 XHR/API。
- FastAPI 服务：骨架和主要接口已创建，测试正在修正适配层。
- Android App：待实现。
- 文档和部署说明：待补齐；本状态文件已创建。
- 最终构建：尚未完成，当前没有 APK。

## 已完成工作

- 根据搜索索引确认站点主题为《资治通鉴》一站式阅读体验平台。
- 确认首页内容包含《资治通鉴》卷/纪/年入口，并关联《通鉴纪事本末》《读通鉴论》等内容。
- 创建 FastAPI 应用骨架和统一响应格式：`code`、`message`、`data`。
- 创建接口：`/api/home`、`/api/search`、`/api/items`、`/api/detail/{id}`。
- 创建 SQLite 内容索引和 HTTP 缓存表，并加入少量本地演示种子数据。
- 创建 robots-aware 抓取器，包含域名限制、robots 检查、超时、限速、重试、指数退避和内存缓存。
- 创建后端测试：API 响应、搜索、分类、详情 404、robots 拒绝、重试与缓存。
- 安装了 `service/requirements.txt` 中声明的 Python 依赖。
- 抓取器测试已通过；Python 模块编译检查已通过。
- 创建 `.gitignore`，排除 Python 字节码、测试缓存、SQLite 运行库、Gradle 构建产物和本地环境文件。

## 当前架构

```text
android/                         # 待创建：原生 Android 客户端
  app/src/main/java/.../
    data/                         # Retrofit、Room、Repository 实现
    domain/                       # 领域模型、UseCase、Repository 接口
    ui/                           # Compose、ViewModel、StateFlow
service/
  app/
    main.py                       # FastAPI 路由和统一响应
    models.py                     # 领域数据模型
    store.py                      # SQLite 内容索引和缓存
    crawler.py                    # 合规外部抓取器
  tests/                          # 后端单元/接口测试
```

依赖方向：Android `ui -> domain <- data`，App 负责组装依赖；服务端路由只编排存储和采集逻辑。

## Android 技术栈

目标技术栈：

- Kotlin。
- Jetpack Compose + Material 3。
- MVVM、StateFlow、Repository pattern。
- Retrofit + Kotlin serialization 或 Moshi，用于 REST API。
- Room，用于收藏、历史记录和离线缓存。
- Hilt，用于依赖注入。
- Coil，用于可选的封面/图片资源加载。
- Coroutines。

Android 工程尚未创建，具体版本需以本机 Android Gradle Plugin、SDK 和本地依赖缓存可用性为准，并在创建后锁定。

## Backend 技术栈

- Python 3.12。
- FastAPI 0.115.6。
- Uvicorn 0.34.0。
- SQLite（标准库）作为默认持久化和缓存存储。
- BeautifulSoup4 4.12.3，预留 HTML 解析能力。
- `pytest`、`pytest-cov` 作为测试工具。
- Docker Compose 部署 API 服务。

## 数据采集方案

当前采用“缓存优先、按需采集”的方案：

- 默认数据从 SQLite 读取；服务不在每次 API 请求中直接请求目标站点。
- 外部请求必须先通过同域名检查和 robots.txt 检查。
- 默认 User-Agent 为 `dutongjian-app/1.0 (+local development)`。
- 单请求有超时；请求间隔默认至少 1 秒。
- 失败最多重试 3 次，使用指数退避并设置 30 秒上限。
- 已成功响应按 URL 做内存缓存；SQLite HTTP cache 保存 TTL、正文和 SHA-256 内容哈希。
- 入库使用主键 `id` 去重，后续解析器应使用稳定的源 URL/内容 ID。
- 不绕过登录、验证码、访问限制或付费墙；抓取前必须人工确认 robots 规则。

当前服务内置的少量种子内容只用于本地开发和离线 UI 验证，不代表已从目标站点实时抓取。

## 已确认的网站 API / 数据结构

### 已确认

- 站点名称：读通鉴。
- 首页主要内容类型：卷/纪/年目录、历史条目、原文与白话对照、标签/主题等阅读辅助信息。
- 站点关联分类至少包括：资治通鉴、通鉴纪事本末、读通鉴论；公开介绍还提到注、表微、直解、读批、元刊本等相关著作。
- 搜索索引中可见的示例条目包括“三家分晋”“周威烈王二十三年”“智瑶与智氏”等。

### 未确认

- 未确认官方 JSON API、GraphQL、XHR/Fetch 接口、鉴权方式或稳定的字段 schema。
- 当前沙箱对 `dutongjian.com` DNS 解析失败，因此未能直接读取 robots.txt、sitemap.xml 或首页响应头/正文。
- 后续在具备网络条件的环境中，应先检查 `robots.txt`、`sitemap.xml`、HTML script 配置和浏览器网络请求，再实现正式解析器。

## 已创建的文件

### 服务端

- `service/requirements.txt`
- `service/pyproject.toml`
- `service/Dockerfile`
- `service/docker-compose.yml`
- `service/app/__init__.py`
- `service/app/models.py`
- `service/app/store.py`
- `service/app/crawler.py`
- `service/app/main.py`
- `service/tests/test_api.py`
- `service/tests/test_crawler.py`

### 运行生成文件

- `data/dutongjian.db`：本地运行时生成的 SQLite 数据库。
- `service/app/__pycache__/`、`service/tests/__pycache__/`：Python 运行生成物，后续应加入 `.gitignore`。

### 状态管理

- `PROJECT_STATE.md`：本文件。
- `.gitignore`：项目运行生成物和本地构建文件忽略规则。

## 当前编译状态

- Android：未开始；没有 `android/` 工程、Gradle wrapper 或 APK。
- Backend Python：`python3 -m compileall -q service/app` 已通过。
- Backend crawler tests：2 项已通过。
- Backend API tests：已改为 `httpx.AsyncClient + ASGITransport`，上次测试运行被会话中断，需在当前会话重新执行并记录明确结果。
- 依赖安装：`python3 -m pip install -r service/requirements.txt` 已成功；由于沙箱 DNS 限制使用了受控安装权限。
- Gradle 环境：系统 Gradle 可执行，但此前启动时报 `Failed to load native library 'libnative-platform.so'`；Android 构建尚未开始，需先验证 wrapper/Gradle 版本和本地缓存。

## 已知错误

1. 当前 Android 工程尚不存在，因此无法执行 guide 要求的 `./gradlew clean`、`./gradlew assembleDebug` 和 `./gradlew test`。
2. 当前沙箱无法解析 `dutongjian.com`，实时站点分析和正式采集器验证被网络环境限制。
3. API 测试此前使用同步 `fastapi.testclient.TestClient` 时在第一个请求处挂起；测试已改成 ASGI 异步客户端，尚未重新完成验证。
4. `data/dutongjian.db` 和 Python 字节码属于本地运行生成物，已由 `.gitignore` 排除，不进入提交。

## 已解决错误

1. FastAPI 路由返回类型 `JSONResponse | dict[str, Any]` 导致响应模型推断异常。已在详情路由加入 `response_model=None`，模块可正常导入。
2. 初始测试缺少 `service` 的 Python path 配置。已通过 `service/pyproject.toml` 的 `pythonpath = ["."]` 固定测试导入路径。
3. Python 依赖未安装。已按 `service/requirements.txt` 安装 FastAPI、Uvicorn、pytest 等依赖。
4. 运行生成物曾显示为未跟踪文件。已创建 `.gitignore`，后续只提交源码、测试、部署配置和文档。

## 下一步任务

1. 重新执行 `pytest -q service/tests --cov=service/app --cov-report=term-missing`，只记录首个真正 root cause。
2. 补齐 `.gitignore`，排除 SQLite、`__pycache__`、构建产物和本地环境文件。
3. 创建 `android/` 原生 Compose 单模块工程，按 `data/domain/ui` 分层。
4. 实现首页、分类、搜索、详情、收藏、历史和离线缓存状态。
5. 配置 Retrofit、Room、Hilt、Coil、Material 3 及测试依赖；确保 API base URL 可通过 BuildConfig 配置。
6. 完成 README、服务部署说明、站点分析报告和 Android 运行说明。
7. 验证 `./gradlew clean`、`./gradlew assembleDebug`、`./gradlew test`、`pytest`，生成并定位 APK。
8. 在可访问目标站点的环境中确认 robots/sitemap/API 后，再实现真实 HTML/API 采集和同步命令。

## 重要决策

- 选择原生 Compose，而不是 WebView，满足 guide 的硬性要求。
- 采用单 Android 模块但在源码内严格分层，降低当前空仓库的 Gradle 复杂度；未来可按功能拆模块。
- 采用 SQLite 而不是强制引入 Redis，保证本地、单机和 Docker 默认可运行；Redis 可作为后续多实例部署扩展。
- API 统一使用 `{code, message, data}`；404 也保持同一 envelope。
- 在未确认目标站点 API 前，不虚构官方接口；本地种子数据只用于开发验证，并在文档中明确标识。
- 抓取器遇到 robots 无法读取时默认拒绝请求，优先合规而不是扩大抓取范围。
- 核心 Skills 按优先级管理：P0 为 Android/Kotlin/Compose/Gradle；P1 为 Python/爬虫/Playwright/FastAPI/GitHub/审查/MCP；P2 仅在实际需要时加载。

## 环境要求

- 工作目录：`/workspaces/-app`。
- Python 3.12+；安装 `service/requirements.txt`。
- JDK：优先使用 Android Gradle Plugin 支持的 LTS JDK；当前环境为 OpenJDK 25，需在 Android 构建时验证兼容性。
- Android SDK：`/home/codespace/android-sdk`，当前已发现 platform 35、build-tools 35.0.0 和 platform-tools。
- Gradle：优先使用项目 wrapper，避免依赖系统 Gradle；当前系统 Gradle 初始化 native platform 失败，需在工程创建后处理。
- 构建内存：按 guide 要求为 Gradle 配置约 8 GB（`org.gradle.jvmargs=-Xmx8g`），但应根据容器实际可用内存调整并记录结果。
- 实时站点采集需要可解析 `dutongjian.com` DNS 的网络环境；生产环境还需持久化 `data/` 卷并配置明确的 CORS 来源。
