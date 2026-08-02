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

---

## 增量阶段更新：完整网站原生移植启动（2026-08-01）

本阶段目标已由“Android MVP”升级为“完整网站功能的原生 Android 重构”。当前已有的 Compose MVP 只是基础壳层，不视为最终交付；本节只追加新事实，不删除任何历史描述。

### 真实站点分析结果

- 主站名称为“读通鉴”，首页可见四个阅读入口：读通鉴、资治通鉴、纪事本末、读通鉴论。
- 资治通鉴入口按卷、纪、年组织目录；搜索索引可见“卷第一（周纪一）”到更后卷的目录项，完整卷数仍需通过站点页面或 sitemap 逐步确认。
- 年页面按条目分页展示；已观察到每条内容包含原文、白话译文、注释/胡注、标签/主题，并有“沙盘态势”“古本”“张居正直解”等阅读辅助入口或切换项。
- 站点存在登录页，支持邮箱登录和手机登录；当前重构不读取或模拟用户凭据，个人空间功能先保留为本地收藏/历史。
- `wiki.dutongjian.com` 是独立的“通鉴百科”知识库，公开索引显示已收录约 115760 条数据，分类包括人物、战争、官职、地点、政权、典故等。
- 当前没有确认到稳定的官方 JSON/GraphQL API；原生 App 使用自建数据服务，数据服务通过合规 HTML 解析器同步公开页面，不伪造官方接口。

### 完整移植的数据边界

1. 主阅读站：入口、卷目录、年份目录、历史条目、原文/白话/注释/标签阅读器。
2. 纪事本末与读通鉴论：使用同一内容模型，通过 `section` 区分来源和目录。
3. 通鉴百科：分类、关键词搜索、知识条目详情和与主阅读内容的来源关联。
4. 本地能力：收藏、历史、离线缓存、阅读设置；登录/同步在确认站点公开认证协议前不实现账号代理。

### 当前阶段实现计划

- Backend 新增 section/volume/year/entry/knowledge 领域模型及目录 API。
- Android 新增原生目录、条目阅读器、百科页面和底部导航；继续沿用 Compose、Room、Hilt、StateFlow 分层。
- 抓取器新增针对主站目录/年页/条目/百科列表的解析入口；仅在 robots 允许且限速的情况下工作。
- 每完成一个页面层级和数据链路，追加记录真实接口、测试和构建结果。

---

## 增量分析更新：公开高级阅读功能与付费边界（2026-08-01）

### 已确认的公开功能

- 主站公开内容展示原文与白话译文对照。
- 年页公开显示“沙盘态势”“古本”“张居正直解”等阅读模式或辅助入口。
- 条目公开显示标签、主题和部分“决策卡”提示，可作为原生 App 的阅读辅助模块。
- 登录页公开提供邮箱登录、手机验证码登录和密码重置入口，但未确认公开会员价格、权益清单、支付回调或授权 API。

### 合法移植边界

- 将公开可见的阅读模式、标签/主题、决策卡、沙盘入口和原文/白话/注释对照实现为原生 Android 功能。
- 为未来正版账号体系预留 `entitlement`/会员权益接口边界，但不模拟账号、不读取凭据、不绕过登录、验证码、权限或付费墙。
- 只采集 robots 允许的公开页面；付费内容必须由用户通过合法授权后由官方接口提供，当前不抓取、不复制、不解锁。

### 本阶段状态

- Backend 已追加 sections/volumes/years/knowledge 数据模型、SQLite 兼容迁移和 REST 路由；新增 API 测试待本轮执行确认。
- Android 尚待把目录层级、百科入口和公开高级阅读模式接入原生导航。

---

## 增量决策更新：App 与原网站运行时隔离（2026-08-01）

- 用户明确要求：该 Android App 不与 `dutongjian.com` 或 `wiki.dutongjian.com` 发生运行时数据交流。
- Android App 的唯一网络目标是本项目自己的 FastAPI 服务；离线模式只读本地 Room 缓存。
- App 不向原网站登录、不上传阅读记录/收藏、不调用原网站账号或付费接口，也不在运行时直接打开原网站页面。
- `source_url` 仅用于记录内容来源和后台同步溯源，不是 Android 运行时请求地址。
- 后台公开内容同步若启用，属于独立的人工/受控数据准备流程，不属于 App 与原网站的数据交换；付费内容不采集、不复制、不解锁。

---

## 增量功能决策：独立高级阅读工作区（2026-08-01）

- 用户要求尽量根据公开描述开发付费/高级功能；本项目将其实现为独立 App 的本地高级阅读能力，不复刻原站会员鉴权。
- 计划实现：原文/白话并排阅读、古本视图、注释/直解、主题标签、决策卡、沙盘时间线、阅读字号/主题设置、收藏/历史和本地笔记入口。
- 所有权益判断由本项目自己的本地/Backend 数据决定；当前不向原网站请求会员状态，不绕过或解锁原站受限内容。
- 未从公开页面确认到价格、套餐、支付、会员 API 或付费字段，因此这些部分不虚构为原站权益；将作为独立 App 的可配置 entitlement 能力保留。

### 本轮 Backend 测试 root cause

- Root cause：已有 `data/dutongjian.db` 中的旧种子记录没有新增 `volume_id`/`year_id`，目录测试通过层级查询后返回空条目。
- 相关文件：`service/app/store.py`，以及本地运行时数据库 `data/dutongjian.db`。
- 错误原因：初始化逻辑只在 `items` 为空时插入新种子；旧数据库虽然完成了列迁移，但没有按稳定 ID 回填新增目录字段。
- 修复方案：在 schema 迁移后按稳定种子 ID 幂等回填 section、volume、year、原文、译文、注释和 tags，不删除用户已有数据。
- 修复结果：已重新执行 Backend 全量测试，8 passed，覆盖率 90%，目录层级测试通过。

## 增量阶段更新：原生目录、百科与高级阅读工作区编译通过（2026-08-01）

### 已完成

- Android 原生 Compose 已接入 section -> volume -> year -> item 四级目录导航。
- Android 原生 Compose 已接入通鉴百科列表、分类筛选、关键词搜索和知识条目详情。
- 阅读详情已加入原文、译文、注释/直解、并行阅读模式，以及标签、古本、沙盘态势和决策卡等独立 App 阅读工作区入口。
- Room 从 schema 1 迁移到 schema 2，新增内容分层字段，并保留已有本地收藏数据。
- `MainActivity` 已将目录和百科回调连接到 `ReadingViewModel`；App 运行时仍只请求本项目 FastAPI，不请求原网站。

### 验证结果

- `python3 -m pytest -q service/tests --cov=service/app --cov-report=term-missing`：8 passed，覆盖率 90%。
- `./gradlew clean assembleDebug`：通过。
- `./gradlew testDebugUnitTest assembleRelease`：通过；Android 当前无 JVM 测试源，因此 `testDebugUnitTest` 为 `NO-SOURCE`。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`。
- Release APK：`android/app/build/outputs/apk/release/app-release-unsigned.apk`。
- 本轮 Debug/Release 均未执行 R8/minify；native library strip 提示不影响 APK 构建结果。
- `git diff --check`：待本轮最终提交前再次执行。

### 已解决错误追加

- Kotlin 编译 warning：Room `Migration.migrate` 参数名与超类型不一致；已将参数统一为 `db`，下一轮构建通过且不再出现该 warning。

### 下一步任务追加

1. 为公开主站目录、阅读条目和百科索引实现结构化 HTML 解析器及小型 fixture 测试。
2. 增加受 robots 和限速约束的独立同步命令；Android 运行时不调用该同步器，也不与原网站交换数据。
3. 完善 README、服务部署、Android 运行和网站分析文档。
4. 增加 CI 的 Backend 测试和 Android Debug/Release 构建检查。

## 增量阶段更新：公开 HTML 解析与受控同步器完成（2026-08-01）

### 已完成

- 新增 `service/app/parsers.py`，使用 BeautifulSoup 解析目录、阅读条目和百科索引，不执行网络请求、不执行脚本、不访问认证页面。
- 新增 `service/app/sync.py`，只接受人工明确指定的公开路径，复用 robots 检查、同源限制、缓存、最小间隔和退避；不会递归爬取站点。
- `ContentStore` 新增知识条目幂等写入，采集结果可以进入本项目 SQLite 数据服务。
- 新增 parser/sync fixture 测试，覆盖目录级别、原文/译文/注释/标签、百科分类、来源 URL 和本地 upsert。
- README、站点分析报告和 GitHub Actions 已补齐；CI 包含 Backend pytest、Android JVM 测试、lint、Debug 和 Release 构建。

### 验证结果

- `python3 -m compileall -q service/app`：通过。
- `python3 -m pytest -q service/tests --cov=service/app --cov-report=term-missing`：13 passed，覆盖率 87%。
- `git diff --check`：通过。
- Android 本阶段代码未改变，上一阶段已验证 Debug/Release APK；R8/minify 继续关闭。

### 当前限制和下一步

- 真实站点页面的 HTML 选择器仍需在 robots 允许且网络可访问的环境中逐页校准；未确认到可复用的官方 JSON/GraphQL API。
- 需要补充真实公开页面 fixture、签名发布配置说明和 Android UI/Room 集成测试。
- 需要在本地 checkpoint commit 前再次执行完整 Android 构建和 Backend 验证，并确认工作树只包含本项目变更。

## 增量验证更新：解析器阶段 APK 回归与本地 checkpoint 准备（2026-08-01）

### 最终验证

- `./gradlew clean assembleDebug`：通过。
- `./gradlew testDebugUnitTest assembleRelease`：通过；`testDebugUnitTest` 为 `NO-SOURCE`。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`。
- unsigned Release APK：`android/app/build/outputs/apk/release/app-release-unsigned.apk`。
- 两个 Android 变体均未启用 `minify`、资源收缩或 R8。
- Backend 最近一次全量结果：13 passed，覆盖率 87%。
- `python3 -m compileall -q service/app`：通过。
- `git diff --check`：通过。

### 当前提交边界

- 本地变更包含原生目录/百科/阅读器、Room schema 迁移、公开 HTML parser/sync、Backend API、文档和 CI。
- 本轮准备创建本地 checkpoint commit；未执行新的远端 push。

## 增量提交更新：本阶段本地 checkpoint 已建立（2026-08-01）

- 本地提交：`feat: extend native catalog and public sync`。
- 提交内容包含本阶段全部已验证的 Android、Backend、公开 HTML parser/sync、文档和 CI 变更。
- 该 checkpoint 已在本地创建；本轮没有新的远端 push，远端操作需由后续明确指令触发。

## 增量验证更新：搜索状态修复与 Android 构建闭环（2026-08-02）

### 本轮完成

- 修复首页搜索展示逻辑：ViewModel 现在记录服务端命中的条目 ID 集合，首页只显示命中结果，并在查询少于两个字符时恢复完整 Room 缓存。
- 修复详情页收藏状态滞后：详情过渡按目标 ID 解析实时 Room 条目，收藏切换后图标和内容状态能够随 StateFlow 更新。
- 新增 `ReadingViewModelTest`，覆盖搜索命中集合和短查询清除行为；先验证了缺少状态字段的 RED，再完成 GREEN。
- 为 `service/data/*.db`、SQLite 和 SQLite3 运行文件补充忽略规则，测试产生的本地数据库不进入提交。

### 最终验证

- Backend：`python3 -m pytest -q service/tests --cov=service/app --cov-report=term-missing`，13 passed，覆盖率 87%。
- Android：JDK 21 下执行 `./gradlew clean test assembleDebug assembleRelease lintDebug`，全部通过。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`，约 19.6 MB。
- unsigned Release APK：`android/app/build/outputs/apk/release/app-release-unsigned.apk`，约 12.9 MB。
- `lintDebug` 已通过；构建过程中仅有 `libandroidx.graphics.path.so` 无法 strip 的打包提示，不影响 APK 生成。

### 本轮根因记录

- 搜索根因：首页此前只读取全量 `state.items`，Repository 虽然请求并写入搜索结果，但 UI 没有保存命中集合，因此搜索后仍显示所有缓存条目。
- Lint 根因：外层 Compose `AnimatedContent` 未使用 target state 参数；已改为按目标 ID 解析条目/百科详情，使过渡内容与目标状态一致。

### 当前限制

- Release APK 仍为 unsigned 产物，正式发布需要由部署环境注入签名配置。
- 真实目标站点的 robots、sitemap 和页面选择器仍需在可解析 `dutongjian.com` DNS 的环境中逐页确认；当前同步器保持人工指定路径和合规拒绝策略。
- 尚未配置 Android 真机/模拟器 UI 运行测试；当前已覆盖 ViewModel JVM 测试、Backend 测试、编译、lint 和 APK 打包。

### 提交状态

- 本轮变更已完成验证，下一步创建本地 Git checkpoint commit。
- 不执行远端 push。

## 增量交付更新：v0.1.0 APK Release 已发布（2026-08-02）

### Release 结果

- 已使用 `gh release create v0.1.0 --target main` 创建 GitHub Release。
- 已使用 `gh release upload v0.1.0` 上传两个 APK 附件。
- Release 地址：`https://github.com/kxxhhh/-app/releases/tag/v0.1.0`。
- `app-debug.apk`：19,643,208 bytes；SHA-256：`9e7ef419c043d31081c475364fe46927645fd8a66205427f167b97ac64ec377b`。
- `app-release-unsigned.apk`：12,940,054 bytes；SHA-256：`c181c1e0d750f599ce428813dd9c33498c9ade7e1fc34aa4ac945dfd7ce74f31`。

### CI 状态

- GitHub Actions run `30728072092` 的 backend job 已通过。
- Android job 长时间停留在 `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`，截至本记录仍为 `in_progress` 且没有新增日志；未取消远端运行。
- 本地 JDK 21 已独立通过同一组测试、lint 和 Debug/Release 构建，因此 Release 资产基于本地已验证产物发布；远端 CI 后续结论仍需关注。

### 当前提交状态

- 本节变更待按默认流程提交并 push 到 `main`。

## 增量进度更新：离线优先契约 checkpoint 与站点复查（2026-08-02）

### 已完成

- 已按离线使用要求先创建并 push checkpoint：`91280c7 test(android): define offline content contract`。
- 已新增 Android 离线种子模型 `OfflineSeed.kt`，准备承载阅读条目、目录层级和百科初始数据。
- 已新增离线内容回归测试契约：初始 UI 状态应包含阅读条目、目录和百科数据；该测试当前处于 RED，表示生产代码尚未接入离线种子。
- 已确认目标站点当前可解析：`www.dutongjian.com` DNS 指向 `120.55.124.123`，首页和前端静态资源可以读取。
- 已确认主站是 Vite/SPA：首页入口引用 `/assets/index-9a984a99.js` 和 `/assets/index-1b0be633.css`，正文由前端脚本渲染。

### 当前问题

- 已发布的 `v0.1.0` APK 仍是网络优先版本，默认请求 `10.0.2.2:8000`；这只适用于启动了本地服务的模拟器，真机和未启动服务会出现连接失败。
- `robots.txt`、`sitemap.xml` 和 `sitemap_index.xml` 在主站返回前端入口 HTML，不是标准 robots/sitemap 文档；`wiki.dutongjian.com/robots.txt` 也返回站点的未找到页面。
- 目前尚未开始批量抓取或把演示种子宣称为全本；必须先从公开前端 bundle、公开 XHR/API 和真实阅读路由确认数据入口、访问边界和分页规则。
- 全本离线内容尚未进入 APK；当前离线 checkpoint 只定义了数据模型和失败测试，不能作为可用离线版本发布。

### 下一步执行

1. 分析主站和百科公开 JavaScript bundle，定位公开阅读目录、条目详情、分页和数据请求。
2. 在确认 robots/公开权限边界后，实现可恢复、限速、缓存、去重和断点续传的全本同步器。
3. 将同步结果导入预置 SQLite/Room 数据库，接入 Android 启动时离线读取和用户主动同步。
4. 以真实数据规模、解析失败数、重复数和测试结果为依据持续更新本文件。

## 增量进度更新：公开 API 同步器 GREEN（2026-08-02）

### 已完成

- 已复核公开目录接口：`资治通鉴` 共 `294` 卷、`1405` 个纪年节点；结构文件声明内容 ID 连续范围为 `1..30989`。
- 已确认纪年接口返回原文 `content`、简体原文 `content_jianti_auto`、译文 `content_fanyi`，以及胡三省注、章注、人物、地点、官职、主题等关联字段。
- 新增 `service/app/tongjian_sync.py`：只访问公开未登录 API，默认每次请求间隔 1 秒，带重试退避、磁盘缓存、原子 checkpoint、断点续传和去重 ID。
- 同步完成后会写入真实卷/纪年目录，并移除本地演示种子目录和演示条目；同步中断时不会执行清理。
- 新增同步器测试，覆盖完整字段保留和断点重跑；后端测试当前为 `15 passed`。
- RED 测试 checkpoint 已提交并 push：`73c9b06 test(service): define resumable tongjian import contract`。

### 当前问题与边界

- 真实全本尚未抓取完成；当前数据库仍是演示数据加同步器代码，不能声称已经拥有 `30989` 段离线正文。
- 目标站点的 robots/sitemap 路径返回前端 HTML，未提供可解析的标准许可声明。同步命令因此要求显式 `--allow-public-api`，并且只使用已经确认可公开访问的 JSON 接口，不访问登录、付费或私有路径。
- Android 当前已经离线优先显示演示种子并把网络超时缩短；全本数据仍需同步完成后再生成 Room 预置数据库并构建新 APK。

### 下一步执行

1. 用 `--allow-public-api` 启动限速同步，持续记录已完成纪年、内容数、失败节点和缓存位置。
2. 校验最终内容 ID 集合、卷/年计数及文本字段完整性，再导出 Android 可读取的 Room 数据库。
3. 运行 Android 全量验证，提交、push，并按默认流程上传 APK Release 附件。

### 实际抓取进度（启动后）

- 已启动命令：`PYTHONPATH=service python -m app.tongjian_sync --allow-public-api --database service/data/dutongjian.db --cache-dir service/data/tongjian-cache --checkpoint service/data/tongjian-progress.json --min-interval 1.0`。
- 当前观测：`8/1405` 个纪年节点已完成，节点请求均成功；SQLite 当前 `47` 条资治通鉴条目，其中 `5` 条是待最终清理的演示种子。
- 缓存和 checkpoint 写入 `service/data/tongjian-cache/`、`service/data/tongjian-progress.json`，均为本地生成文件，不进入 Git。

### 限流复盘

- 抓取曾推进到 `51/1405`，随后站点返回 HTTP `429`；同步器已按 `Retry-After` 退避并安全停止，重启后 checkpoint 推进到 `54/1405`。
- 已检查前端 bundle：正文请求只有按单个 `reign_tongjian_id` 调用的 `/api/reign`，没有发现批量正文接口；公开 `zztj_df/index.json` 仅是专题图片清单。
- 为降低再次触发站点限流，后续实际运行参数调整为每 5 秒最多一个新请求；已有缓存节点仍直接读取本地文件。

## 增量进度更新：Android 全量资产链路与限流恢复（2026-08-02）

### 已完成

- Android `ReadingUiState()` 默认即显示离线种子，避免无网络启动时出现空白状态；Room 读取流程继续保留收藏和历史字段。
- 新增压缩 NDJSON 资产导入链路：存在 `offline_content.ndjson.gz` 时按 500 条批量写入 Room，没有资产时自动回退到种子和已有缓存。
- 新增 `service/app/export_android.py`，只允许在真实 `30989` 条 `zztj-*` 内容校验通过后导出 Android 资产，避免把部分抓取结果打进 APK。
- 后端回归：`17 passed`；Android `testDebugUnitTest`：`BUILD SUCCESSFUL`。

### 实际抓取进度

- 限速恢复后已推进至 `68/1405` 个纪年节点，SQLite 统计 `216` 条资治通鉴内容；未出现新的失败节点。
- 站点仍按约每分钟 2 个节点完成，后续保持单进程、5 秒最小间隔和服务端退避，不以并发方式绕过限流。

### 当前问题

- 全本仍未完成，Android 资产尚未生成，当前 APK 不能宣称包含完整 `30989` 段正文。
- README 中部分历史验证数字仍需在本轮最终构建完成后统一更新；本状态文件以本轮实时结果为准。

## 增量进度更新：README 与 DOCS 重写（2026-08-02）

### 已完成

- 已按当前 Android、FastAPI、SQLite、crawler、parser、TongjianSync 和资产导出实现重写 `README.md` 与 `DOCS.md`。
- README 包含项目定位、快速导航、`[📖 开发者文档](./DOCS.md)`、核心特性、技术栈、环境要求、离线构建、Backend 联调、维护者同步、目录结构、测试基线、当前状态和 MIT License。
- DOCS 包含整体架构图、模块边界、离线运行策略、FastAPI endpoint 参数与返回值、Python/Android 核心接口、本地环境变量、调试命令、同步断点恢复、测试策略、FAQ、分支和 Conventional Commits 规范，并提供 `[⬅️ 返回首页](./README.md)`。
- 已校准旧文档中的测试数字和 429 退避描述：当前后端基线为 `18 passed`，Android `testDebugUnitTest` 已通过。

### 并行任务实时进度

- 全本同步进程仍在运行，checkpoint 当前为 `82/1405`，缓存 `82` 个纪年 JSON。
- SQLite 当前查询到 `266` 条正文、`84` 个纪年关联，其中额外记录来自尚未完成前保留的演示种子；同步完成后才执行演示清理。
- 当前没有生成 Android 全量资产，也没有把部分数据库或 APK 宣称为完整全本。

## 增量验证更新：远端整合、Android 构建与同步续跑（2026-08-02）

### Git 状态

- 已执行 `git add -A`；工作树原本没有未提交改动，最新本地文档提交为 `32b7b84`。
- 远端 `main` 当时包含删除 `README.md` 和 `DOCS.md` 的两个提交；已通过普通 merge 保留指南要求的两份文档，合并提交为 `44b093c`。
- `44b093c` 已推送到 `origin/main`，当前本地与远端分支一致；没有使用 force push 或改写共享历史。

### 本轮验证

- `PYTHONPATH=service python -m pytest -q service/tests`：`18 passed`。
- `python3 -m compileall -q service/app`：通过。
- `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew clean testDebugUnitTest lintDebug assembleDebug`：通过。
- Debug APK 已生成于 `android/app/build/outputs/apk/debug/app-debug.apk`；构建仅有 `libandroidx.graphics.path.so` 无法 strip 的已知非阻塞提示。
- `git diff --check`：通过。

### 实际同步进度

- 已用独立 session 重新启动单进程公开 API 同步，参数保持 `--allow-public-api`、`--min-interval 5`、磁盘 cache 和原子 checkpoint。
- 本次记录时 checkpoint 为 `98/1405`，缓存为 `98` 个纪年 JSON，真实 `zztj-*` 正文为 `318` 条；SQLite 总正文为 `323` 条，其中 `5` 条为同步完成前保留的演示种子。
- 当前同步进程仍在运行；遇到 HTTP 429 时继续保留 checkpoint/cache 并按 Retry-After 退避，不改为并发请求。

### 当前问题与下一步

- 全本尚未完成，不能执行完整 Android 内容资产导出，也不能将当前 Debug APK 宣称为包含 `30989` 条正文的离线版本。
- 继续监控同步至 `1405/1405`，随后校验正文 ID、字段完整性、卷/纪年数量和演示数据清理结果。
- 完整校验通过后生成 `offline_content.ndjson.gz` 与 `offline_catalog.json`，再运行 Android Debug/Release 全量验证并记录 APK SHA-256。

## 增量验证更新：Release 变体通过（2026-08-02）

- `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew testDebugUnitTest assembleRelease`：通过；Release 继续显式关闭 R8 和资源收缩。
- unsigned Release APK 位于 `android/app/build/outputs/apk/release/app-release-unsigned.apk`，SHA-256 为 `2d0dbced56b583320a6b0c04cca8e7fc5a4deecaa466eb505b165742be3bb883`。
- 本次观察时同步 checkpoint 已推进到 `100/1405`，真实 `zztj-*` 正文为 `323` 条；同步进程仍在独立 session 中运行。

## 增量阶段更新：离线资产字段校验与安装检查 APK（2026-08-02）

### 导出校验

- 新增 RED 测试：不完整正文必须被 Android 资产导出拒绝，且不能覆盖已有目标文件；测试 checkpoint 为 `88a44c7`。
- 修复 `service/app/export_android.py`：读取完整分类记录，校验标题、正文、来源、卷/纪年层级、原文和译文等必填字段后才写入 gzip NDJSON；GREEN 修复提交为 `864e95f`。
- 后端回归结果为 `19 passed`，`python3 -m compileall -q service/app` 通过。

### APK 编译

- `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew clean assembleDebug`：`BUILD SUCCESSFUL`。
- 可安装 Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`，大小约 `19 MB`。
- Debug APK SHA-256：`3b5484904710c6cc10b1dc7b41e7b038ba011b3d2ef93614220fab2e8516560c`。
- 当前容器没有 `adb`，未执行设备安装；该 APK 仍是全本同步完成前的 OfflineSeed/本地缓存版本，不包含完整 `30989` 条正文。

### 同步快照

- 本次记录时 checkpoint 为 `115/1405`，真实 `zztj-*` 正文为 `375` 条；同步进程仍以 `--min-interval 5` 在独立 session 中运行。

## 增量交付更新：v0.1.1 APK GitHub Release（2026-08-02）

### Release

- 已将当前 `main` 的 Debug/Release 构建发布为 GitHub 预发布：<https://github.com/kxxhhh/-app/releases/tag/v0.1.1>。
- Release tag 为 `v0.1.1`，目标分支为 `main`，两个 APK 资产状态均为 `uploaded`。
- `app-debug.apk`：`19,659,588` bytes，可用于设备安装检查；SHA-256 为 `3b5484904710c6cc10b1dc7b41e7b038ba011b3d2ef93614220fab2e8516560c`。
- `app-release-unsigned.apk`：`12,956,434` bytes，unsigned Release 构建；SHA-256 为 `2d0dbced56b583320a6b0c04cca8e7fc5a4deecaa466eb505b165742be3bb883`。
- GitHub Actions CI 的 Backend 和 Android job 均通过；构建仍保留已知的 `libandroidx.graphics.path.so` 无法 strip 非阻塞提示。

### Release 边界

- 该预发布包含当前 OfflineSeed、Room fallback 和原生阅读功能，不包含完整 `30,989` 条正文资产。
- 全本同步仍在后台以单进程、5 秒最小间隔运行；本次记录时 checkpoint 为 `118/1405`，真实正文为 `381` 条。

### v0.1.2 修复版 Release

- 因 `v0.1.1` 同时提供 unsigned Release APK，新增并发布签名有效的设备检查版本：<https://github.com/kxxhhh/-app/releases/tag/v0.1.2>。
- `app-inspection.apk` 使用 Android debug keystore 签名，仅用于设备安装检查；本地 `apksigner verify` 已确认 APK v2 有效。
- Release 资产 SHA-256：`6271e57ba986075e2391425f249baddb77d516837c2219dd08969b07d3091b86`。
- GitHub Actions 对 `5f0444d` 的 Backend 和 Android job 均通过；当前仓库 `main` 已推进至 `bf56640`。
- 本次记录时全本同步 checkpoint 为 `129/1405`，真实 `zztj-*` 正文为 `410` 条。

## 增量修复更新：安装检查签名变体（2026-08-02）

### 安装问题分析

- 已验证原 `app-debug.apk` 的 APK v2 签名有效，包名为 `com.dutongjian.app`，`minSdkVersion` 为 `26`，Manifest 入口正常。
- `app-release-unsigned.apk` 确实没有签名；设备安装器对 unsigned APK 可能只返回 Binder NPE，不能作为用户安装包。
- 本地环境没有已连接 Android 设备，未能通过 adb 复现该设备侧安装异常。

### 修复结果

- 新增 `inspection` 构建变体：沿用 Release 配置，但使用 Debug keystore 签名，专门用于当前设备安装检查；正式 `release` 仍保持 unsigned，避免将测试签名误当生产签名。
- `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleInspection assembleRelease`：通过。
- `app-inspection.apk` 已通过 `apksigner verify`，APK v2 签名有效；SHA-256 为 `6271e57ba986075e2391425f249baddb77d516837c2219dd08969b07d3091b86`。
- 新修复版 GitHub Release 使用 `v0.1.2`，只提供明确命名的 `app-inspection.apk`；安装检查时不要选择 unsigned Release APK。

## 增量阶段更新：已完成章节验证并编入 APK（2026-08-02）

### 阶段性内容审计

- 已允许将当前 checkpoint 已完成内容显式编入 Android，不改变默认严格全量导出保护。
- 导出快照时 checkpoint 为 `162/1405` 个纪年节点，生成 `529` 条真实 `zztj-*` 正文，覆盖 `6` 卷、`162` 个纪年；目录资源包含 `1` 个 section、`6` 个 volume、`162` 个 year。
- 529 条记录的标题、正文、来源 URL、卷/纪年关联、原文和译文必填字段均完整；正文长度为 `4..1005` 字。
- 内容中未发现“暂无”“待补”“未抓”“placeholder”“示例”“框架”“内容为空”“敬请期待”等占位模式；已确认不是只有目录或空壳框架。
- Android 资源文件为 `android/app/src/main/assets/offline_content.ndjson.gz` 和 `offline_catalog.json`。Android 打包后实际资源条目为 `offline_content.ndjson`，APK 内读取校验仍得到 `529` 条记录和 `162` 个纪年目录。

### 编译与静态验证

- `PYTHONPATH=service python -m pytest -q service/tests`：`20 passed`。
- `python3 -m compileall -q service/app`：通过；`git diff --check`：通过。
- `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleInspection assembleRelease`：`BUILD SUCCESSFUL`。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `a1a5640e40ec8da21da577aa933a42894fd31520bda0daaa4062da42a542432f`。
- 安装检查 APK：`android/app/build/outputs/apk/inspection/app-inspection.apk`，SHA-256 `8cb7a9439bfca0c49d57577503dc578e4d58cd1beb8a18533ca6c05d43f9bf23`；`apksigner verify` 已确认 APK v2 签名有效。
- Debug 和 Inspection APK 均已从 APK 内部读取并核对上述 `529` 条正文和 `162` 个目录节点；Release 变体也已成功编译，但仍是 unsigned 输出。

### 当前进度与边界

- 记录本节时爬虫 checkpoint 已继续推进到 `164/1405`，同步进程仍以单进程、5 秒最小间隔运行；不要删除 checkpoint/cache，也不要启动并行抓取。
- 当前 APK 是“已完成章节快照 + OfflineSeed fallback”，不是完整 `30,989` 条全本；后续每次重新导出前仍需重复字段和占位内容校验。
- 当前环境没有模拟器/实体设备，因此无法完成 Runtime 验证；本轮只完成了资源、构建和 APK 签名的静态验证。

## 增量交付更新：阶段性内容 APK 版本化（2026-08-02）

- Android 版本已递增为 `versionCode 3`、`versionName 0.1.2`，对应发布标签为 `v0.1.3`，用于和旧的 `v0.1.2` 安装检查包区分。
- 重新执行 `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleInspection assembleRelease`：`BUILD SUCCESSFUL`。
- 新 Debug APK SHA-256：`1622b10c31f7e28ca51fe3a28db43b33fed7c339c7abfa5fda94db505a7de2ed`。
- 新 Inspection APK SHA-256：`8a75791e445c689d69ed96e7b94c35391d6c2b1efad843c48c2f7dfa1c533e3d`；`apksigner verify` 已确认 v2 签名有效。
- 新 APK 内部资源仍核对为 `529` 条正文、`162` 个年份目录；Release 变体成功编译但仍为 unsigned，不作为设备安装包。
- 当前爬虫 checkpoint 已推进到 `166/1405`；APK 是本次导出时的 `162` 年快照，后续抓取内容需要重新导出并重新编译才会进入 APK。
- GitHub 预发布已创建：<https://github.com/kxxhhh/-app/releases/tag/v0.1.3>；只上传签名有效的 `app-inspection.apk`，避免用户误装 unsigned Release APK。

## 增量修复更新：APK 正文导入失败（2026-08-02）

### 已确认根因

- APK 内实际资源条目是 `assets/offline_content.ndjson`，且 Android 打包后内容已是普通 NDJSON；源码此前固定打开 `offline_content.ndjson.gz` 并使用 `GZIPInputStream`。
- 该资源名不匹配和格式不匹配导致导入失败；旧代码捕获异常后静默回退到 OfflineSeed/Room，所以用户能看到目录标题，但很多正文只有标题。
- 从 APK 静态读取确认资源仍有 `529` 条记录，529/529 条的正文、原文、译文均非空；问题不在爬取数据内容。

### 修复与验证

- Repository 现在优先读取 Android 实际条目 `offline_content.ndjson`，兼容回退到旧 `.gz` 名称，并按 gzip magic header 自动选择解码器。
- 导入失败不再静默吞掉：会通过 `ReadingRepository` 错误日志记录异常，同时继续保留离线 fallback。
- 新增 `AssetContentReaderTest`，覆盖普通 NDJSON 和 gzip NDJSON 两种输入；专门测试和完整 Android 单元测试均通过。
- 修复版版本为 `versionCode 4`、`versionName 0.1.3`；需重新安装新 APK，不能覆盖判断为旧版本的缓存结果。
- 修复版 GitHub 预发布已创建：<https://github.com/kxxhhh/-app/releases/tag/v0.1.4>；`app-inspection.apk` SHA-256 为 `36783b130bd1cff58a6ba2ca779e20198653e1fac7fbf673f487053c256ee4ab`。
- 修复版执行 `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleInspection assembleRelease`：`BUILD SUCCESSFUL`；当前环境无模拟器/实体设备，仍未进行 Runtime 安装验证。

## 增量阶段更新：目录加载竞态与离线百科资产（2026-08-02）

- 修复目录加载期间先显示 OfflineSeed 两卷/两年的竞态；请求未完成时现在显示加载状态，不再允许用户进入过时样例目录。
- 新增 `offline_knowledge.json` 阶段性资产，从已完成正文的真实人物、地点、官职、主题和决策关联中去重生成百科条目；不再只依赖四条 OfflineSeed。
- 本次资源快照包含 `1601` 条正文、`20` 卷、`271` 个纪年和 `3643` 条百科条目；正文、原文、译文及百科摘要/正文均非空。
- 百科分类统计：人物 `1820`、地点 `915`、官职 `580`、决策 `250`、主题 `78`。这些是已抓内容的阶段性关联索引，不是完整百科全量。
- 新版本为 `versionCode 5`、`versionName 0.1.4`；后续发布使用 `v0.1.5`，旧 `v0.1.4` 仍只有正文资产。
- `v0.1.5` 已发布：<https://github.com/kxxhhh/-app/releases/tag/v0.1.5>；`app-inspection.apk` SHA-256 为 `c2f5acd93341282270960e1ecd9928a675237e74b445493b426645d9d6683859`。
- 新 APK 执行 `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleInspection assembleRelease`：`BUILD SUCCESSFUL`；APK 内部核对正文 `1601`、目录纪年 `271`、百科 `3643`，并确认 APK v2 签名有效。

## 增量验证更新：无头模拟器闭环与古籍字号控制（2026-08-02）

### 首轮感知与评估

- 已执行 `./run_codex_autodev.sh`：无头模拟器已运行，APK 安装成功，Inspection 构建成功，初始运行无 Crash。
- 首轮 `01_home.png` 正常显示首页、搜索、分类筛选和底部导航；初始 `02_detail.png`/`03_scroll.png` 仍停留首页，原因是脚本使用了超出 `320x640` 屏幕的点击/滑动坐标。
- 初始 `window_dump.xml` 确认 Compose 节点、首页正文卡片和四个底部导航项均存在；初始运行未生成 crash.log 文件，说明脚本只在发现错误时写文件。

### 本次迭代

- 在 [DutongjianApp.kt](/workspaces/-app/android/app/src/main/java/com/dutongjian/app/ui/DutongjianApp.kt) 的详情页新增字号控制：`80%..130%`，以 `- / +` 图标按钮调整，统一作用于原文、白话和注释，状态使用 `rememberSaveable` 保持旋转/重组期间稳定。
- 修正 `run_codex_autodev.sh`：优先安装本轮刚构建的 `app-inspection.apk`，点击真实正文卡片坐标 `160,440`，使用 `160,580 -> 160,180` 的有效滑动坐标，并在无错误时生成明确的 `crash.log`。

### 最终闭环验证

- 再次执行 `./run_codex_autodev.sh`：`BUILD SUCCESSFUL`、模拟器安装 `Success`、运行无 Crash，已导出 [app-autodev.apk](/workspaces/-app/build/outputs/app-autodev.apk)。
- 最终 `02_detail.png` 已进入实际正文详情页；最终 `03_scroll.png` 显示 `字号 - 100% +`、对照/原文/白话/注释切换和正文内容。
- 最终 `window_dump.xml` 包含 `字号`、`100%`、`content-desc="增大字号"`、`content-desc="减小字号"`；`crash.log` 内容为 `No Fatal, AndroidRuntime, or NullPointer errors detected.`。
- `./gradlew testDebugUnitTest`：`BUILD SUCCESSFUL`；无头模拟器闭环已覆盖安装、启动、详情导航、滚动和 Compose 控件渲染。
- `app-autodev.apk`：`16,839,917` bytes，SHA-256 `f350dce6bdc104662b75166346b3c667f9ebaf1f370d1551e3bdc61de337e679`，包版本 `versionCode 5 / versionName 0.1.4`，APK v2 签名有效。

## 增量迭代更新：古籍阅读工具集、闭环验证与应用图标（2026-08-02）

### 远端同步与图标

- 已执行 `git pull --ff-only`，远端快进到 `98b9c28`，同步得到设计源 `1785654469420~2.png`；该文件实际为 `741x741` JPEG，保留在仓库根目录作为原始设计源。
- 新增 `android/app/src/main/res/drawable/app_icon.jpg`，并在 Manifest 的 `android:icon` 与 `android:roundIcon` 中引用；`aapt dump badging` 已确认 APK 图标资源为 `res/kl.jpg`。
- 应用版本递增为 `versionCode 6`、`versionName 0.1.5`，用于区分已发布的 `v0.1.5` 阶段性内容 APK。

### 本轮选择并实现的 15 项阅读能力

1. 首页最近阅读条目恢复卡片。
2. 首页精选条目“换一批”轮换，状态由 ViewModel 管理。
3. 首页正文搜索与一键清除。
4. 首页分类筛选。
5. 书架收藏列表。
6. 书架最近阅读列表。
7. 目录卷册/年代/条目层级浏览。
8. 目录当前层级筛选与清除。
9. 百科关键词搜索与一键清除。
10. 百科分类筛选及分类结果计数。
11. 详情页收藏。
12. 原文、白话、注释和对照四种阅读模式。
13. 详情页 `80%..130%` 动态字号调节。
14. 详情页阅读进度、回到顶部和滚动阅读反馈。
15. 篇内检索、复制当前文本、分享史料、主题标签、沙盘态势和决策卡。

### 构建与闭环证据

- `./gradlew :app:testDebugUnitTest :app:compileInspectionKotlin`：`BUILD SUCCESSFUL`；新增精选轮换 ViewModel 单元测试通过。
- 已按要求再次执行 `./run_codex_autodev.sh`：Inspection 构建成功，模拟器安装返回 `Success`，首页进入真实正文详情页并完成滚动截图。
- `build/ui_checks/01_home.png` 显示首页、分类筛选、`1606` 篇内容和“换一批”；`02_detail.png` 显示真实长标题、导读和正文；`03_scroll.png` 显示四种模式、字号、阅读进度、篇内搜索、复制和分享。
- `window_dump.xml` 包含 `阅读进度`、`复制当前文本`、`分享`、`content-desc="回到顶部"`、`content-desc="增大字号"` 和 `content-desc="减小字号"`；额外点击增大字号后 `window_dump_after_font.xml` 显示 `110%`。
- `build/ui_checks/crash.log` 为 `No Fatal, AndroidRuntime, or NullPointer errors detected.`；点击字号后再次检查错误级 Logcat，未发现 Fatal、AndroidRuntime 或 NullPointer。
- APK 已导出至 [app-autodev.apk](/workspaces/-app/build/outputs/app-autodev.apk)，大小 `16,958,125` bytes，SHA-256 `0c9a9cd66c1b794d2a439521d104730d28a246cd19fef8549d455cb957dfd705`，包版本 `versionCode 6 / versionName 0.1.5`，`apksigner` 已确认 APK v2 签名有效。
- GitHub 预发布已创建：<https://github.com/kxxhhh/-app/releases/tag/v0.1.6>，上传的 `app-autodev.apk` 与本地闭环产物 SHA-256 一致。

### 爬虫状态

- 全本同步进程 PID `114706` 仍在运行，当前 checkpoint 为 `298/1405`，约 `21.2%`；本轮 APK 仍是已完成阶段性内容快照，不宣称为完整全本。

## 增量修复更新：当前爬虫快照导入、短篇阅读与可配置 AI（2026-08-02）

### 当前内容导入

- 在 checkpoint `329/1405` 时重新导出 Android 阶段性资产：`2107` 条正文、`26` 卷、`329` 个纪年、`4987` 条百科关联。
- 更新了 `offline_content.ndjson.gz`、`offline_catalog.json` 和 `offline_knowledge.json`；Repository 增加离线资产版本标记 `2026-08-02-329`，旧 Room 数据库升级后会重新导入，不再只按总数跳过更新。
- 首页在资产导入完成前显示加载态，不再把 5 条 OfflineSeed 当作已完成正文；最终延迟采样显示 `2112` 条，即当前资产 `2107` 条加离线兜底种子 `5` 条。
- 本次 APK 是 `329/1405` 的内容快照；爬虫随后继续推进，记录时 checkpoint 为 `333/1405`，进程 PID `114706` 仍在运行。

### 阅读体验修复

- 字号改为按条目保存的 `80%..160%` 整数档位，并增加 Slider；字号同时作用于标题、导读、原文、白话、注释、沙盘和 AI 结果。无头模拟器点击后 DOM 已从 `100%` 变为 `110%`。
- 删除短篇场景中价值有限的篇内查找和阅读进度条，保留原文/白话/注释/对照、复制和分享。
- “沙盘态势”现在解析正文结构化注释，展示事件主线、关键人物、地点、官职和史料注释。
- “决策卡”提供可选择的先观其变、主动出击、稳守边境策略；若来源有决策关系则优先展示来源关系。
- “古本原文”现在展开真实繁体原文和来源 URL，不再是无动作按钮。

### AI 接入

- 新增 `AiRepository`、OpenAI Chat Completions 兼容客户端和 Hilt wiring，支持自定义 API URL、模型和 API Key。
- AI 设置页面支持 URL、模型、Key 保存；Key 使用 Android Keystore 的 AES/GCM 加密后写入本机 SharedPreferences，不进入源码、Logcat 或普通 UI 文本。
- URL 校验要求 HTTPS；本机调试允许 `localhost`、`127.0.0.1`、`10.0.2.2`。未配置 Key 的远程接口会明确提示，不会静默失败。
- 详情页新增 `AI总结`、`AI逐句对照`、`AI词语对照`：分别输出人物/事件/因果、古文与白话分句表、古文词语与白话对应表。请求限制输入长度并使用明确的“只依据史料”提示词。
- 本轮只验证了 AI 设置页面、配置校验和客户端编译；没有使用真实 API Key，因此没有虚报第三方 AI 网络请求已成功。填写配置后可在详情页触发请求。

### 构建与运行验证

- `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleInspection`：`BUILD SUCCESSFUL`；新增 `HistoricalContextTest` 通过。
- 再次执行 `./run_codex_autodev.sh`：Inspection 构建成功、模拟器安装 `Success`、等待离线导入后首页/详情/滚动截图完成，`crash.log` 为 `No Fatal, AndroidRuntime, or NullPointer errors detected.`。
- `01_home.png` 显示 `2112` 条内容和设置入口；`02_detail.png` 显示真实正文；`03_scroll.png` 显示四种模式、字号 Slider、原文/白话短篇排版。
- 交互检查确认：字号 `100% -> 110%`；AI 设置页显示 API URL、模型、API Key 和保存按钮；沙盘展开后 DOM 包含事件主线、关键人物、相关地点；全程未发现 Fatal、AndroidRuntime 或 NullPointer。
- APK 已导出至 [app-autodev.apk](/workspaces/-app/build/outputs/app-autodev.apk)，大小 `18,412,697` bytes，SHA-256 `fd746c74c5006ab52126ed269fba7ab8ca5d0f8e0536e179def24258ad4a3c07`，版本 `versionCode 7 / versionName 0.1.6`；`apksigner` 已确认 APK v2 签名有效。
- GitHub 预发布已创建：<https://github.com/kxxhhh/-app/releases/tag/v0.1.7>，上传 APK 与本地闭环产物 SHA-256 一致。

## 增量迭代更新：阅读工具模块整合、百科性能与注释互链（2026-08-02）

### 本轮实现

- 新增 `TTSEngine` 抽象、SharedPreferences 引擎设置、古文多音字预处理、标点分句队列、暂停/继续/停止、句子进度和条目结束自动进入下一条；设置页提供“微软 Edge-TTS”和“Sherpa-onnx”选项。
- 新增本地 Room `historical_places` 表和古地名目录，正文中的已知古地名带下划线；点击后显示古今对照、坐标和说明，并可打开带 Marker 的轻量地图视图。
- 新增历史年表页，按已导入条目的纪、年字段展示时间轴，并支持年份/纪年筛选及点击进入正文。
- 新增 Room `reading_notes` 表、划线/记笔记入口、笔记颜色、正文高亮和“书架 > 笔记”列表；笔记点击可重新打开对应正文并保留高亮。
- 服务端 `ExtRef_Children_hu_notes` 注释 JSON 改为可读注释卡片，保留人物/地点关联与原文位置；点击注释可返回原文标记，点击原文标记可打开对应注释。
- 百科搜索和分类切换改为本地缓存即时筛选、250ms 去抖、取消旧请求；主 Tab 移除不必要的 `AnimatedContent`，降低点击百科时的迟滞。

### 构建与模拟器验证

- 已提交并推送：`17e789a feat(android): add reading toolkit modules`。
- 推送后的 `./run_codex_autodev.sh`：Inspection 编译成功、模拟器安装成功、首页/详情/滚动截图成功，`crash.log` 为 `No Fatal, AndroidRuntime, or NullPointer errors detected.`。
- 定向 UI dump 确认百科已载入 `4987 条结果`，底部导航可见首页、年表、目录、百科、书架；年表、书架和设置页均可进入。
- `./gradlew :app:testDebugUnitTest` 和 `:app:compileDebugKotlin`：`BUILD SUCCESSFUL`；Compose 仅有 `ClickableText` API 弃用警告，无编译错误。
- APK `build/outputs/app-autodev.apk` 大小 `18,478,233` bytes，SHA-256 `bbe3e929241ee83a098bea6bac6768962e619ad9ef212551215d638278a5e5b6`。
- GitHub 预发布已创建：<https://github.com/kxxhhh/-app/releases/tag/v0.1.8>，上传 `app-autodev.apk`。

### 当前边界

- 当前环境已经有无头模拟器，可完成编译、安装和 Compose 交互检查；没有真实 Android 设备，不能替代实体设备兼容性验证。
- Sherpa 模型文件尚未进入 `assets/sherpa-onnx-tts/`，Edge 音频传输端点也未配置；两个引擎类、播放队列和设置契约已接入，未具备外部模型/传输时使用系统中文语音回退，不能宣称真实 Edge 网络合成或 Sherpa 本地模型已验证。
- 本轮 APK 仍使用已导出的 `2026-08-02-329` 离线内容快照；爬虫进程 PID `114706` 仍在运行，checkpoint 已推进到 `361/1405`。后续完整抓取结束后需要重新导出资源、编译并发布新 APK。

## 进行中：Sherpa-onnx 完整本地引擎与阅读交互修复（2026-08-02）

### 当前目标

- 将官方 `k2-fsa/sherpa-onnx` 的 Android AAR/JNI、中文 VITS 模型和词典资产真正打入 APK，使用 `OfflineTts` + `AudioTrack` 完成本地合成，不再调用 Google/系统 TTS 兜底。
- 修复朗读悬浮球、自动连播时详情页跟随并带横向动画、笔记按实际选中文本划线、首页双击返回退出、非首页常规返回。
- 将详情页顶部唯一正文标题改为“原文”，移除重复正文；有注释的原文范围显示虚线下划线，注释与正文相互点击并高亮定位。
- 编译通过后执行无头模拟器闭环，确认安装、Compose 渲染、Sherpa JNI 资产和 APK 产物；再更新日志、提交、推送和 GitHub Release。

### 已完成但待构建确认

- 已下载官方 `vits-icefall-zh-aishell3` 模型至 `android/app/src/main/assets/sherpa-onnx-tts/`，包含 `model.onnx`、`lexicon.txt`、FST 词典和 `rule.far`；`rule.far` 约 180 MB，将由 Git LFS 管理。
- `SherpaOnnxEngine` 已改为调用官方 `OfflineTts`，首次朗读时异步初始化，使用 `AudioTrack` 播放生成的 PCM；系统 Google TTS 类已删除。
- `EdgeTTSEngine` 已改为真实 Edge-TTS WebSocket + MP3 播放，连接失败直接展示错误。
- 已加入可锁定的 JitPack 上游依赖版本和 `scripts/update_sherpa_onnx.sh`，用于查询上游提交并在编译/模拟器验证后显式升级。
- Compose 侧已写入悬浮球、章节自动跟随和滑入/滑出动画、双击返回、选中文本笔记输入、注释虚线和单一“原文”布局。

### 当前阻塞与证据

- 首次 `./gradlew :app:compileDebugKotlin --stacktrace` 未进入 Kotlin 编译：JitPack 产物 POM 声明版本为 `master-SNAPSHOT`，而当前坐标使用 `master-116a44e72c-1`，Gradle 报 `inconsistent module metadata`。
- 因此当前不能宣称 APK 已包含 Sherpa JNI，也不能宣称新 UI 或 TTS 已通过运行验证；需要先修正依赖坐标，再执行完整构建和模拟器闭环。
- 爬虫 PID `114706` 仍在运行，最近 checkpoint 为 `361/1405`；本阶段仍使用现有内容快照，抓取进度与本次 Sherpa/UI 修复互不覆盖。

### 本轮完成与发布结果

- 官方 Sherpa AAR 已成功解析并验证内容：包含 `libsherpa-onnx-jni.so`、`libsherpa-onnx-c-api.so`、`libsherpa-onnx-cxx-api.so` 和 ONNX Runtime；应用 APK 同时包含 `model.onnx`、`lexicon.txt`、`rule.far` 等中文模型资产。当前锁定 JitPack 上游构建 `master-116a44e72c-1`，可用 `scripts/update_sherpa_onnx.sh` 查询更新。
- `./gradlew clean assembleDebug`：`BUILD SUCCESSFUL`；APK 检查确认 native JNI 与模型均已打包。
- `./run_codex_autodev.sh`：Inspection 构建成功、模拟器安装返回 `Success`、首页/详情/滚动截图和 DOM dump 已生成；`crash.log` 为 `No Fatal, AndroidRuntime, or NullPointer errors detected.`。
- 最新模拟器截图已提交至 `build/ui_checks/01_home.png`、`build/ui_checks/02_detail.png`、`build/ui_checks/03_scroll.png`；详情截图确认顶部只保留“原文”，注释标记按词语显示虚线下划线。
- 代码、截图和 LFS 模型已提交并推送：`54eb515 feat(android): integrate sherpa onnx reading controls`。Sherpa 模型 9 个 LFS 对象已上传。
- APK 已导出至 [app-autodev.apk](/workspaces/-app/build/outputs/app-autodev.apk)，SHA-256 `3f9d1d6540456a97a3b261d02905e8a0c80129b6790912dd7c598870c7a050c8`，并发布 GitHub 预发布 [v0.1.9](https://github.com/kxxhhh/-app/releases/tag/v0.1.9)。
- 当前闭环验证覆盖编译、安装、启动、详情导航、滚动、Compose 渲染和错误日志；自动脚本没有主动触发一次完整 Sherpa 音频播放，因此真实设备上的扬声器输出仍需安装 APK 后检查。Edge-TTS 仍依赖网络，连接失败会显示错误，不会回退系统 TTS。

## 增量修复：音频自检、地图补全与正文条目标号清洗（2026-08-02）

### 正文数据修复

- 定位到详情页开头的 `5`：它来自 `offline_content.ndjson.gz` 的 `content`/`original` 数据，是条目标号，不是正文；当前资产中有 `1,379` 条记录带有这类数字前缀，去掉后均与条目标题对齐。
- 导入映射增加受控清洗：仅处理“数字前缀后紧接汉字，且正文去掉前缀后匹配标题”的简体内容；繁体 `original` 允许异体字匹配，但仍要求前缀后是汉字。普通正文数字不会被盲删。
- 离线资产版本递增为 `2026-08-02-329-content-clean-2`，已有 Room 数据会自动重导入。闭环 DOM 已确认从 `烏孫...`/`乌孙...` 开始，不再显示 `5烏孫` 或 `5乌孙`。

### 历史地图联动

- 古地名目录补充渭水、彭城、许昌、江陵、赤壁、函谷关、会稽、蜀郡、河东等现代对应、说明和坐标；范围型地名保留“一带/流域”等不确定性描述。
- `historical_places` 已存在的数据库不再只在空表时初始化，启动时会 upsert 最新目录，确保安装过旧版本的用户也能收到新增地点。
- 正文已知古地名继续以带下划线的可点击标记呈现；BottomSheet 展示古今对照、历史说明和坐标；“在地图中查看”打开带选中红色 Marker 的轻量地图视图，并显示当前 Marker 数量。
- 地点核对参考灵宝市政府关于函谷关位于灵宝的资料、运城市政府关于古称河东的资料，以及地方志关于会稽与绍兴沿革的资料；赤壁保留传统定位与具体战场存在异说的说明。

### TTS 音频自检与 Edge 403

- `TTSEngine` 增加 `AudioDiagnosticSnapshot`；Sherpa `AudioTrack.play()` 后严格检查初始化/播放状态，记录负写入、连续三次零写入，并抛出 `AudioHardwareException`，不通过 catch 静默吞掉异常。
- Sherpa 开启 `DEBUG_DUMP_PCM`，异步生成 `cacheDir/debug_tts_output.pcm`；每个异常或状态变化用 `AUDIO_DIAGNOSTIC` 输出 JSON。新增 `androidTest` 的 `testTTSAudioOutputDataNotZero` 验证非零 PCM 与正向写入。
- `connectedDebugAndroidTest`：`BUILD SUCCESSFUL`，`TTSEngineSelfTest` 通过。模拟器日志记录 `writtenBytes=49904`、`pcmBytes=49904`、`pcmNonZero=true`、`AUDIO_PLAYBACK_COMPLETE`。这证明合成和 AudioTrack 写入管线工作，不等同于实体扬声器已经发声。
- Edge-TTS WebSocket 补齐 `Sec-MS-GEC`、版本、Origin、MUID、桌面 UA、时间戳和配置帧 CRLF；HTTP 403 现在会输出 `EDGE_HTTP_403` 的结构化音频错误并提示切换离线引擎。当前模拟器网络状态为 not connected，因此不能宣称 Edge 网络请求已恢复；公共端点仍可能主动拒绝握手。

### 构建、模拟器和体积

- `./gradlew clean assembleDebug`：`BUILD SUCCESSFUL`；原生 sherpa/ONNX 库无法 strip 的 Gradle 提示已保留为诊断信息，不影响打包。
- `./gradlew :app:testDebugUnitTest`：`BUILD SUCCESSFUL`，包含条目前缀清洗回归测试。
- 再次执行 `./run_codex_autodev.sh`：Inspection 编译、安装、启动、截图、DOM dump 均成功；`build/ui_checks/crash.log` 为 `No Fatal, AndroidRuntime, or NullPointer errors detected.`。
- 最终 APK [app-autodev.apk](/workspaces/-app/build/outputs/app-autodev.apk)：`118,523,302` bytes，SHA-256 `072f31b92b53a0dcb0bd5ae19093f624eed00a3880f89408e257b58d5890dde8`，`versionCode 8 / versionName 0.1.7`；相比之前同时打包四 ABI 的约 `192 MB` debug APK，当前双 ABI Inspection APK 约 `114 MB`，保留实体 ARM64 与当前 x86_64 模拟器。
- 本轮截图 `build/ui_checks/01_home.png`、`02_detail.png`、`03_scroll.png` 已重新生成，随本轮提交。

### 当前边界

- 当前没有实体 Android 设备，无法完成真实扬声器听感和 Edge-TTS 网络可用性验证；安装后若 Edge 仍返回 403，应使用 Sherpa-onnx 离线引擎或提供可用网络环境。

## 内容恢复纠正（2026-08-02）

- 用户确认需要以爬取资产为准，撤销条目标号清洗及任何简繁替换加工；`ReadingRepositoryImpl` 现在原样导入 `content`、`original` 和 `translation`。
- 离线资产版本更新为 `2026-08-02-329-raw-crawled-1`，已有 Room 数据会重导入原始抓取字段。正文中出现的数字前缀将按爬取文本保留，不再擅自判断或删除。
- 已重新执行 Debug/Inspection 编译和无头模拟器闭环，DOM 确认正文恢复为抓取字段，`5烏孫...` 按原始资产保留；详情顶部标题已统一为“原文”。

## 本轮交互与朗读修复（2026-08-02）

### 已完成

- 默认朗读引擎切回 Android 本地 `TextToSpeech`，通过 SharedPreferences 一次性迁移旧的 Sherpa 默认值；设置页仍保留 Edge-TTS 和 Sherpa-onnx 手动切换项。
- 空选区不再生成覆盖全文的划线；正文中的已有划线可以点击并确认删除，书架笔记列表继续支持删除。
- 增加标签页按前进/后退方向的横向淡入淡出转场；详情页增加左边缘右滑返回，跟随手指平移并以弹簧动画完成退出，避免抢占正文滚动和横向控件；朗读悬浮球增加展开/收起的淡入、缩放和位移动画。
- 详情正文唯一标题改为“原文”，抓取资产的原始数字前缀不再被导入层擅自清洗。
- 修正 `run_codex_autodev.sh` 的变体回退逻辑，避免 Inspection 构建失败后误复制旧 Inspection APK。

### 验证结果

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`。
- `./gradlew :app:connectedDebugAndroidTest`：`BUILD SUCCESSFUL`，模拟器上的 `TTSEngineSelfTest` 通过。
- `./run_codex_autodev.sh`：Inspection 编译成功、安装返回 `Success`，首页/详情/滚动截图、DOM 和 `crash.log` 均生成；`crash.log` 为 `No Fatal, AndroidRuntime, or NullPointer errors detected.`。
- 最终 APK [app-autodev.apk](/workspaces/-app/build/outputs/app-autodev.apk)：`118,539,690` bytes，SHA-256 `96b9641002f804744bcc0e042cb9263e7248e553852be805bdca5f0d7bc701fe`，版本 `versionCode 9 / versionName 0.1.8`，APK v2 签名验证通过。
- GitHub Release `v0.1.11`：<https://github.com/kxxhhh/-app/releases/tag/v0.1.11>，已上传上述 `app-autodev.apk`。
- 模拟器手工检查：详情左边缘右滑返回首页成功；设置页显示默认 Android 本地 TTS；点击正文朗读后出现“重新朗读/暂停”和朗读悬浮球。当前环境无实体扬声器，以上是系统 TTS 调度和界面状态验证，不等同于实际听感验证。

### 当前边界

- Sherpa-onnx 和 Edge-TTS 仍可由设置手动选择，但本轮默认使用 Android 本地 TTS；Edge-TTS 是否可用取决于网络端点，Sherpa 听感取决于模型和设备音频输出。
- 当前没有实体 Android 设备，无法完成真实扬声器音量、音色和硬件兼容性验证。

## Sherpa 移除、内容同步与目录性能修复（2026-08-02）

### 变更

- Sherpa-onnx 的 AAR/JNI 依赖、VITS 模型/FST assets、`SherpaOnnxEngine`、专用音频硬件异常类和 instrumented 自检已从 Android 工程移除；设置页只保留 Android 本地 TTS 与 Edge-TTS，Edge 403 提示改为切换本地 TTS。
- 当前离线快照按 checkpoint `541/1405` 导出：正文 `4552` 条，目录 `1` 个 section、`53` 卷、`541` 个年，百科关联 `10167` 条；App 导入版本更新为 `2026-08-02-541-raw-crawled-2`。抓取进程仍在后台继续推进，后续完整抓取结束后需再次导出资产。
- 服务端 `ReadingYear` 保存公开目录的 `year_int`，Android 年表按数字年份排序并显示公元/公元前格式；加入 `service/data/year-calibration-sources.json` 记录校准锚点。网络复核锚点：通鉴起于前403年、刘宋永初元年为420年、唐武德元年为618年，分别参考 [北京化工大学图书馆](https://lib2.buct.edu.cn/bookInfo_01h1339348.html)、[中国历史纪年表](https://ytliu0.github.io/ChineseCalendar/era_names.html)、[故宫博物院武德词条](https://www.dpm.org.cn/lemmas/243111.html)。
- 目录页改为先显示已有本地数据、后台刷新远程目录；卷/年/条目增加内存缓存，过滤结果使用 `remember`，列表使用稳定 key，避免每次进入目录等待网络回退和重复构建列表。
- `run_codex_autodev.sh` 改为轮询离线导入完成后再采样，并确认详情页进入“原文”，避免内容量增长后固定等待时间造成误判。

### 验证与产物

- `service`: `python -m pytest -q`，`21 passed`。
- Android: `./gradlew :app:testDebugUnitTest :app:assembleDebug`，`BUILD SUCCESSFUL`；`connectedDebugAndroidTest` 无 instrumented 用例，任务成功完成。
- `./run_codex_autodev.sh`：Inspection 编译成功、模拟器安装成功，首页显示 `4557` 篇；详情截图进入真实正文页；目录点击后立即显示“阅读目录”，进入资治通鉴可见多卷；年表卡片显示 `公元前403年`；`crash.log` 为 `No Fatal, AndroidRuntime, or NullPointer errors detected.`。
- 最终 APK [app-autodev.apk](/workspaces/-app/build/outputs/app-autodev.apk)：`24,369,577` bytes，SHA-256 `d6d5db2a88e93b6444cf527383c236105520da9ab1ed07de1b694e94f6fa4fb5`，版本 `versionCode 10 / versionName 0.1.9`。APK 内未发现 Sherpa、ONNX、`model.onnx` 或 `.far` 文件，相比上一版约 `118 MB` 明显缩小。
- 当前没有实体 Android 设备，无法验证真实扬声器输出；本轮只验证了本地 TTS 选项、目录/年表渲染、安装和 Runtime 日志。
