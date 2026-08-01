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

当前处于 Android 客户端基础架构阶段，后端 API 基线已通过，Compose UI 和最终构建待完成。

- 站点分析：已完成基础分析。
- API/数据源分析：未发现可确认的官方 JSON API；待在具备站点 DNS 的环境中进一步抓取 XHR/API。
- FastAPI 服务：骨架和主要接口已创建，API/采集器测试均已通过，待完成覆盖率确认。
- Android App：Gradle 单模块工程、data/domain/ui 基础代码已创建，Compose UI 待实现。
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
- 创建 Android 单模块工程，包含 Compose、Retrofit、Room、Hilt、Coil、Coroutines 配置。
- 创建 Android domain 模型/Repository 接口、Retrofit API、Room DAO/数据库、Repository 实现和 StateFlow ViewModel。

## 当前架构

```text
android/                         # 原生 Android 客户端
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

Android 工程已创建；当前锁定 AGP 9.0.0、Kotlin 2.4.10、Compose BOM 2026.07.00、compileSdk/targetSdk 35、minSdk 26。实际构建仍需下载依赖并验证版本兼容性。

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

### Android 客户端

- `android/settings.gradle.kts`
- `android/build.gradle.kts`
- `android/gradle.properties`
- `android/app/build.gradle.kts`
- `android/app/proguard-rules.pro`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values/themes.xml`
- `android/app/src/main/res/xml/backup_rules.xml`
- `android/app/src/main/java/com/dutongjian/app/DutongjianApplication.kt`
- `android/app/src/main/java/com/dutongjian/app/domain/model/ReadingItem.kt`
- `android/app/src/main/java/com/dutongjian/app/domain/repository/ReadingRepository.kt`
- `android/app/src/main/java/com/dutongjian/app/data/network/ApiModels.kt`
- `android/app/src/main/java/com/dutongjian/app/data/network/DutongjianApi.kt`
- `android/app/src/main/java/com/dutongjian/app/data/local/ItemEntity.kt`
- `android/app/src/main/java/com/dutongjian/app/data/local/ItemDao.kt`
- `android/app/src/main/java/com/dutongjian/app/data/local/AppDatabase.kt`
- `android/app/src/main/java/com/dutongjian/app/data/ReadingRepositoryImpl.kt`
- `android/app/src/main/java/com/dutongjian/app/di/AppModule.kt`
- `android/app/src/main/java/com/dutongjian/app/ui/ReadingViewModel.kt`

## 当前编译状态

- Android：工程骨架和 Kotlin 分层代码已创建；尚未运行 Gradle 编译，没有 APK。
- Backend Python：`python3 -m compileall -q service/app` 已通过。
- Backend crawler tests：2 项已通过。
- Backend API tests：`test_api.py` 4 项通过；采集器测试 2 项通过；Python 编译检查通过。完整覆盖率命令仍待执行。
- 依赖安装：`python3 -m pip install -r service/requirements.txt` 已成功；由于沙箱 DNS 限制使用了受控安装权限。
- Gradle 环境：系统 Gradle 9.4.0 在 JDK 21 下可启动；默认 JDK 25 曾触发 `Failed to load native library 'libnative-platform.so'`。Android 工程尚未下载依赖或完成构建。

## 已知错误

1. Android 工程尚未配置 Gradle wrapper，也未完成 `./gradlew clean`、`./gradlew assembleDebug` 和 `./gradlew test`。
2. 当前沙箱无法解析 `dutongjian.com`，实时站点分析和正式采集器验证被网络环境限制。
3. Android 依赖尚未下载，AGP/Kotlin/Compose/Room/Hilt 版本兼容性尚未经过实际编译验证。
4. `data/dutongjian.db` 和 Python 字节码属于本地运行生成物，已由 `.gitignore` 排除，不进入提交。

## 已解决错误

1. FastAPI 路由返回类型 `JSONResponse | dict[str, Any]` 导致响应模型推断异常。已在详情路由加入 `response_model=None`，模块可正常导入。
2. 初始测试缺少 `service` 的 Python path 配置。已通过 `service/pyproject.toml` 的 `pythonpath = ["."]` 固定测试导入路径。
3. Python 依赖未安装。已按 `service/requirements.txt` 安装 FastAPI、Uvicorn、pytest 等依赖。
4. 运行生成物曾显示为未跟踪文件。已创建 `.gitignore`，后续只提交源码、测试、部署配置和文档。
5. 同步 FastAPI 路由在线程池中阻塞 ASGI 测试。已将只读 API 路由改为 `async def`，避免该线程池路径；4 个 API 测试已通过。

## 下一步任务

1. 补 Compose UI：首页推荐/分类筛选、搜索、详情、收藏、历史、加载/错误/空状态和深色模式。
2. 修正并验证 Repository/Room/StateFlow 代码的 Kotlin 编译问题。
3. 生成 Gradle wrapper，使用 JDK 21 执行 clean、assembleDebug、test；记录首个真实 root cause。
4. 完成 README、服务部署说明、站点分析报告和 Android 运行说明。
5. 重新执行完整 `pytest` 和覆盖率，更新本状态文件。
6. 在可访问目标站点的环境中确认 robots/sitemap/API 后，再实现真实 HTML/API 采集和同步命令。

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

---

## 增量阶段更新：Compose UI 实现（2026-08-01）

本节为新增记录，保留前文全部历史状态、完成项、错误和决策。

### 本阶段完成

- 新增 `MainActivity` 和 Hilt Android Application 入口。
- 新增 Material 3 主题，支持系统深色模式和手动切换。
- 新增首页：搜索框、分类筛选、推荐条目、加载/错误/空状态、刷新入口。
- 新增书架：收藏和最近阅读两个视图。
- 新增详情页：返回、收藏、导读、正文和来源信息。
- 新增条目卡片和淡入淡出页面过渡。
- 修正 Room Flow 的 `first()` 调用，并允许本地开发 API 使用 HTTP。

### 当前增量文件

- `android/app/src/main/java/com/dutongjian/app/MainActivity.kt`
- `android/app/src/main/java/com/dutongjian/app/ui/DutongjianApp.kt`
- `android/app/src/main/java/com/dutongjian/app/ui/theme/Theme.kt`

### 本阶段验证状态

- Compose UI 尚未完成 Gradle 编译验证。
- 首轮构建使用 JDK 21；默认 JDK 25 的 Gradle native platform 问题仍保留在前文错误记录中。
- 下一步先生成 Gradle wrapper，再执行 `clean`、`assembleDebug`、`test`，每次失败只追加第一个 root cause。
- Android Debug/Release 构建显式关闭 `isMinifyEnabled` 和 `isShrinkResources`，按用户要求跳过 R8 和资源收缩。

### 增量构建修复

- 已按首个 Android 依赖 root cause 将 Compose BOM 从不存在的 `2026.07.00` 修正为官方示例使用的 `2026.06.00`。
- 本次修复保留 R8 跳过配置；下一轮构建继续只记录首个新的 root cause。

### 第四个 Android 构建 root cause 记录

- Root cause：`android/app/build.gradle.kts` 中 `io.coil-kt:coil-compose:3.3.0` 无法解析。
- 相关文件：`android/app/build.gradle.kts`。
- 原因：Coil 3 使用新的 Maven group `io.coil-kt.coil3`，旧 group 坐标不再匹配当前发行版。
- 修复方案：改用 `io.coil-kt.coil3:coil-compose:3.5.0`，并加入对应的 OkHttp 网络模块。
- 修复结果：待下一轮 `clean assembleDebug` 确认；R8 仍显式关闭。

### 首轮 Gradle root cause 记录

- Root cause：`android/build.gradle.kts:4` 声明的 KSP 插件版本 `2.4.10-2.0.2` 无法从 Gradle Plugin Portal/Google/Maven Central 解析。
- 相关文件：`android/build.gradle.kts`。
- 原因：该版本号不是当前可解析的 KSP 插件坐标；官方 KSP 快速入门示例使用独立版本号 `2.3.10`。
- 修复方案：将 KSP 插件版本改为 `2.3.10`，不改动 Kotlin、AGP 或业务代码。
- 修复结果：待重新运行 Gradle wrapper 任务确认。

### 第三个 Gradle root cause 记录

- Root cause：`android/app/build.gradle.kts:40` 的旧版 `kotlinOptions { jvmTarget = "17" }` DSL 在 Kotlin 2.4/AGP 9 配置阶段无法解析。
- 相关文件：`android/app/build.gradle.kts`。
- 原因：新版 Kotlin Gradle 插件使用 `compilerOptions` DSL 管理编译器选项。
- 修复方案：改用 `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`，保持 JVM 17 编译目标。
- 修复结果：待重新运行 Gradle wrapper 任务确认。

### 第二个 Gradle root cause 记录

- Root cause：`android/app/build.gradle.kts:5` 应用 `com.google.dagger.hilt.android` 时失败，报 `Android BaseExtension not found`。
- 相关文件：`android/build.gradle.kts`、`android/app/build.gradle.kts`。
- 原因：当前 Hilt Gradle 插件仍依赖旧 Android BaseExtension API，与 AGP 9.0 的插件 API 不兼容。
- 修复方案：移除 Hilt Gradle 插件声明和模块应用，保留 Hilt runtime/compiler、`@HiltAndroidApp`、`@Inject` 和 KSP 代码生成能力。
- 修复结果：待重新运行 Gradle wrapper 任务确认。

---

## 增量构建更新：API 35 依赖收敛（2026-08-01）

本节继续追加当前会话进展，不修改或删除前文历史记录。

### 本次唯一首个 Android 构建 root cause

- Root cause：`androidx.core:core-ktx:1.19.0` 的 AAR metadata 要求 `compileSdk 37` 和更高版本的 Android Gradle Plugin；当前容器只有 Android API 35，工程使用 AGP 9.0.0。
- 相关文件：`android/app/build.gradle.kts`；环境相关目录为 `/home/codespace/android-sdk/platforms/android-35`。
- 错误原因：依赖版本超出当前本地 SDK 与构建插件能够验证的 API 范围，导致 `:app:checkDebugAarMetadata` 在 Kotlin 编译前失败。
- 修复方案：将 Compose、Core、Activity、Lifecycle、Navigation、Coil 等依赖收敛到 API 35 兼容的稳定版本，保持 `compileSdk = 35`，不为本地构建强行下载尚不存在于容器的更高 Android 平台。
- 修复结果：待下一轮 `clean assembleDebug` 验证。

### 下一轮 Android 构建 root cause

- Root cause：`androidx.core:core-ktx:1.17.0` 及其传递的 `androidx.core:core:1.17.0` 仍要求 `compileSdk 36`，因此 API 35 工程在 `:app:checkDebugAarMetadata` 阶段继续失败。
- 相关文件：`android/app/build.gradle.kts`。
- 错误原因：Core 1.17.0 已按 API 36 构建，不能用于当前仅安装 API 35 的构建环境。
- 修复方案：继续回退到 API 35 时期的 `androidx.core:core-ktx:1.16.0`，保持其他已验证的依赖版本和 R8 跳过配置不变。
- 修复结果：待下一轮 `clean assembleDebug` 验证。

### 下一轮 Android Kotlin 编译 root cause

- Root cause：`android/app/src/main/java/com/dutongjian/app/ui/DutongjianApp.kt` 使用的 `SmallTopAppBar` 与 `TopAppBarDefaults.smallTopAppBarColors` 不存在于当前 Compose Material 3 依赖组合。
- 相关文件：`android/app/src/main/java/com/dutongjian/app/ui/DutongjianApp.kt`。
- 错误原因：顶部栏 API 名称与当前稳定 Material 3 artifact 不匹配，导致顶部栏声明解析失败，并连带产生 Composable 上下文错误。
- 修复方案：统一改用当前稳定的 `TopAppBar` 与 `TopAppBarDefaults.topAppBarColors`，不改变导航、刷新、深色模式和收藏交互。
- 修复结果：待下一轮 `clean assembleDebug` 验证。

### 下一轮 Android Kotlin 编译 root cause

- Root cause：当前 Compose Material 3 版本将 `TopAppBar` 和相关颜色 API 标记为实验 API，而 Kotlin 编译器按项目配置将未处理的 opt-in 警告作为错误。
- 相关文件：`android/app/src/main/java/com/dutongjian/app/ui/DutongjianApp.kt`。
- 错误原因：代码已使用正确的 API 名称，但缺少 `ExperimentalMaterial3Api` 显式 opt-in。
- 修复方案：在 UI 文件中加入 Material 3 实验 API 的文件级 opt-in，保留当前顶部栏实现。
- 修复结果：待下一轮 `clean assembleDebug` 验证。

### 下一轮 Android Hilt 编译 root cause

- Root cause：Hilt 2.59.2 注解处理器在 `:app:hiltJavaCompileDebug` 阶段拒绝 Kotlin metadata 2.4.0，报其内置解析器最高支持 2.3.0。
- 相关文件：`android/app/build.gradle.kts`；触发来源为 Kotlin 2.4.10 与 Hilt compiler 2.59.2 的版本组合。
- 错误原因：Hilt compiler 的 metadata 解析能力落后于当前 Kotlin 编译器生成的 metadata 版本。
- 修复方案：将 Hilt Android runtime、compiler 和 Gradle plugin 统一升级到官方当前构建说明使用的 2.60.1，保持 KSP 与 Kotlin 版本不变。
- 修复结果：待下一轮 `clean assembleDebug` 验证。

### 当前构建约束

- Debug 和 Release 均保持 `isMinifyEnabled = false`、`isShrinkResources = false`，编译时跳过 R8 与资源收缩。

---

## 增量环境更新：Codex 自主开发配置（2026-08-01）

- 已读取 `codex --help`，当前 CLI 版本为 `codex-cli 0.146.0`。
- 已确认支持的高自主配置值：`approval_policy = "never"`、`sandbox_mode = "danger-full-access"`；现有 `network_access = "enabled"` 和当前项目 trusted 配置保留。
- 已将上述配置写入 `/home/codespace/.codex/config.toml`，未读取或输出任何 credential 文件和敏感值。
- `codex --strict-config --help` 返回成功，配置语法有效。
- 未启用 `--dangerously-bypass-approvals-and-sandbox`，因为它会绕过安全边界，超出当前项目自主开发所需范围。
- 无害 `codex exec` 验证受当前 app-server 的只读文件系统初始化错误阻塞；该错误不代表配置解析失败，后续仍以普通 Shell、构建和测试命令验证项目能力。

---

## 增量阶段更新：Android Debug 构建通过（2026-08-01）

本节继续追加当前会话结果，保留此前所有阶段、错误记录和解决记录。

### 本阶段完成

- Android Compose 首页、书架、详情、搜索、分类筛选、收藏、阅读历史和深色模式入口完成首版实现。
- Android 数据层已接入 Retrofit、Room、Hilt 和 ViewModel，保留本地缓存与远端服务边界。
- Gradle wrapper 已生成，构建固定使用 JDK 21；本地安装的 Android API 35 作为 `compileSdk`。
- Debug/Release 均明确设置 `isMinifyEnabled = false`、`isShrinkResources = false`，构建时跳过 R8 和资源收缩。
- Hilt Gradle plugin 已恢复并升级到 2.60.1，兼容当前 Kotlin metadata 和 AGP 9 构建链。

### 当前编译状态

- `clean assembleDebug`：通过。
- 产物：`android/app/build/outputs/apk/debug/app-debug.apk`。
- 关键构建阶段已完成：KSP、Kotlin、Java、Hilt、dex、APK 打包。
- 构建输出中的 `libandroidx.graphics.path.so` 无法 strip 属于 native 库打包警告，Gradle 已按原样打包，不是失败原因。
- 仍存在非阻塞 Kotlin warning：`MenuBook` 自动镜像 API 弃用提示，以及 `ReadingViewModel` 的两个泛型转换 warning；下一轮可做代码质量清理。

### 已解决错误追加

- API 35 依赖收敛：Core 使用 1.16.0，Activity 使用 1.10.1，Lifecycle 使用 2.8.7，Navigation 使用 2.8.9，Compose BOM 使用 2025.08.01，Coil 使用 3.4.0。
- Material 3 顶部栏 API 已改用 `TopAppBar` 和 `topAppBarColors`，并加入文件级实验 API opt-in。
- Hilt 2.59.2 的 Kotlin metadata 不兼容已通过升级到 Hilt 2.60.1 解决。

### 下一步任务追加

1. 执行 Android JVM 单元测试和 Release 构建，确认两个变体都保持 R8 关闭。
2. 清理已确认的 Compose 图标弃用 warning 和 ViewModel 泛型转换 warning。
3. 完善 README、服务部署说明、Android 运行说明和站点分析报告。
4. 在可访问 `dutongjian.com` 的网络环境中继续确认 robots、sitemap、HTML/API 数据结构，并实现真实采集同步命令。

---

## 增量验证更新：Android 双变体与 Backend 验证完成（2026-08-01）

本节为当前会话的最终追加记录，不删除前文任何历史内容。

### 验证结果

- `./gradlew clean assembleDebug`：通过。
- `./gradlew testDebugUnitTest assembleRelease`：通过；当前 Android JVM 测试目录无测试源，因此 `testDebugUnitTest` 为 `NO-SOURCE`。
- `./gradlew assembleDebug`（清理 warning 后）：通过，Debug Kotlin、Java、KSP、Hilt、dex 和 APK 打包全部完成。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`。
- Release APK：`android/app/build/outputs/apk/release/app-release-unsigned.apk`。
- `pytest -q service/tests --cov=service/app --cov-report=term-missing`：6 passed，覆盖率 85%。
- `git diff --check`：通过。

### 本阶段错误处理闭环

- API 35 依赖、Material 3 顶部栏 API、Material 3 opt-in、Hilt AGP 集成和 Kotlin metadata 兼容问题均已解决。
- `ReadingViewModel` 的 unchecked cast 已改为类型安全的分阶段 `combine`。
- `MenuBook` 图标已改为自动镜像 API，相关弃用 warning 已清理。
- R8 和资源收缩在 Debug/Release 两个变体中均保持关闭；本阶段验证没有执行 minify/R8 任务。

### 提交状态

- 本地变更已完成验证，准备创建本地 Git checkpoint commit。
- 当前分支此前已领先 `origin/main` 1 个提交；本次提交完成后仍不自动 push，等待明确的远端操作指令。
