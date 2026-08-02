# 📖 读通鉴开发文档

[⬅️ 返回首页](./README.md)

## 📌 文档范围

本文档以当前仓库源码为准，覆盖 Android 客户端、Python 数据服务、公开内容同步器、接口契约、本地调试和贡献流程。项目仍处于 MVP/基础架构阶段，文中明确标记的“未确认”或“当前失败”不是实现承诺。

## 🏛️ 架构设计

### 整体设计思路

项目采用“原生客户端 + 本地内容服务 + 双层缓存”的单仓库结构：

```text
人工指定的公开 HTML
        │
        ▼
RobotsAwareFetcher
  同源 / robots / 限速 / 重试 / 内存缓存
        │
        ▼
parsers.py
  CatalogNode / Item / KnowledgeEntry
        │
        ▼
ContentStore ───────────────► SQLite 内容索引 + HTTP cache
        │
        ▼
FastAPI /api/*
        │ Retrofit + Kotlin Serialization
        ▼
ReadingRepositoryImpl
        ├── Room reading_items：内容、收藏、最近阅读
        └── OfflineSeed：无网络时的本地演示内容
        │
        ▼
ReadingViewModel / StateFlow
        │
        ▼
Jetpack Compose UI
```

Android 端的依赖方向是 `ui → domain ← data`：

- `ui` 只依赖领域模型和 `ReadingRepository` 接口，不直接操作 Retrofit 或 Room。
- `domain` 保存跨实现的业务模型、Repository contract 和 `OfflineSeed`。
- `data` 负责 API DTO、Room Entity、数据库、网络客户端和 Repository 实现。
- `di` 在应用启动时组装 Hilt、Retrofit、OkHttp、Room 和 Repository。
- `MainActivity` 收集 `ReadingViewModel.state`，将状态和事件回调传入 Compose 根组件。

服务端路由保持薄层：路由负责参数校验、调用 `ContentStore` 和组装 envelope；持久化、查询、种子数据和 cache 逻辑集中在 `store.py`。解析器只转换已经传入的 HTML，不发起网络请求。

### 模块划分

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| Android UI | `android/app/src/main/java/.../ui/` | Compose 页面、导航状态、阅读模式和加载/空/错误状态 |
| Android ViewModel | `ui/ReadingViewModel.kt` | `ReadingUiState`、搜索、目录级联、收藏、阅读记录、百科查询 |
| Android Domain | `domain/model/`、`domain/repository/` | `ReadingItem`、目录模型、百科模型和稳定接口 |
| Android Network | `data/network/` | `ApiEnvelope<T>`、DTO 和 Retrofit endpoints |
| Android Local | `data/local/` | Room `ItemEntity`、`ItemDao`、数据库版本迁移 |
| Android Data | `data/ReadingRepositoryImpl.kt` | 远端请求、本地 upsert、Room Flow、离线 fallback |
| Android DI | `di/AppModule.kt` | API base URL、OkHttp 超时、JSON、Room 和绑定关系 |
| API | `service/app/main.py` | FastAPI 路由和统一 JSON 响应 |
| Domain Store | `service/app/store.py` | SQLite 表初始化、种子、查询、upsert、HTTP cache |
| Crawler | `service/app/crawler.py` | 同源检查、robots 检查、请求间隔、退避和缓存 |
| Parser | `service/app/parsers.py` | 目录、阅读条目、百科卡片的 HTML 解析 |
| HTML Sync | `service/app/sync.py` | 公开 HTML 单页同步 orchestration 和命令行入口 |
| API Sync | `service/app/tongjian_sync.py` | 公开《资治通鉴》 API 的限速、磁盘缓存、checkpoint、断点续传和去重导入 |

### 数据流与缓存策略

1. App 启动时 `ReadingViewModel` 先以 `OfflineSeed` 填充初始展示状态，同时订阅 `ReadingRepository.observeItems()`。
2. Repository 尝试调用 FastAPI；成功后将内容转换为 Room Entity 并 upsert，UI 由 Room `Flow` 更新。
3. 网络请求抛出普通异常时，Repository 返回本地 Room/`OfflineSeed` 内容；`CancellationException` 会继续抛出，不会被当成业务失败吞掉。
4. 收藏和最近阅读只写入 Room：`setFavorite()` 更新布尔值，`recordOpened()` 写入毫秒时间戳。
5. 服务端启动时初始化 SQLite 表并写入演示种子。公开 HTML 同步由 CLI 显式触发；《资治通鉴》 API 同步器先拉取卷/纪年目录，再按纪年节点请求正文，成功记录按 ID upsert。
6. HTTP cache 表保存 `cache_key`、正文、过期时间和 SHA-256；当前 fetcher 使用内存 cache，`ContentStore` 提供 SQLite cache API 供同步链路扩展。

### 内容与合规边界

- 数据源必须是调用方明确指定的公开 URL/path。
- URL 必须与 `base_url` 的 hostname 一致。
- 不能读取 robots 规则时，`RobotsAwareFetcher` 默认拒绝访问。
- 请求间隔默认至少 1 秒，失败最多重试 3 次，退避上限 30 秒。
- 不登录、不执行 JavaScript、不访问隐藏 API、不绕过验证码、权限或付费墙。
- `tongjian_sync.py` 需要显式 `--allow-public-api`；它只访问已经确认的公开、未登录 JSON API，并使用磁盘 cache 与 checkpoint 支持中断后续跑。
- `source_url` 应保留来源溯源信息；当前种子数据中的 URL 是演示来源，不等于实时同步完成。

## 🔌 核心 API 与模块接口

### 统一响应格式

所有 FastAPI 成功响应都采用：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

详情不存在时仍保持同一 envelope，HTTP status 为 `404`，例如：

```json
{"code": 404, "message": "item not found", "data": null}
```

### FastAPI REST API

| Method | Path | 输入参数 | `data` 返回值 |
| --- | --- | --- | --- |
| `GET` | `/api/home` | 无 | `{items, categories, sections}`；Android 当前忽略未知的 `sections` 字段 |
| `GET` | `/api/search` | `q` 必填，长度 1-80；`limit` 1-50，默认 20 | `{query, items}` |
| `GET` | `/api/items` | `category`、`year_id` 可选；`limit` 1-50，默认 20 | `{category, year_id, items}` |
| `GET` | `/api/detail/{item_id}` | 路径参数 `item_id` | 单个 `Item`；不存在返回 404 |
| `GET` | `/api/sections` | 无 | `{sections: LibrarySection[]}` |
| `GET` | `/api/sections/{section_id}/volumes` | 路径参数 `section_id` | `{section_id, volumes: Volume[]}` |
| `GET` | `/api/volumes/{volume_id}/years` | 路径参数 `volume_id` | `{volume_id, years: ReadingYear[]}` |
| `GET` | `/api/years/{year_id}/items` | 路径参数 `year_id`；`limit` 1-100，默认 50 | `{year_id, items}` |
| `GET` | `/api/knowledge` | `q` 可选，长度 1-80；`category` 可选；`limit` 1-50，默认 20 | `{category, query, items: KnowledgeEntry[], categories}` |
| `GET` | `/api/knowledge/{entry_id}` | 路径参数 `entry_id` | 单个 `KnowledgeEntry`；不存在返回 404 |

#### `Item` 字段

`service/app/models.py:Item` 和 Android `ItemDto` 使用以下字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 稳定内容 ID，主键 |
| `title` | `string` | 标题 |
| `category` | `string` | 内容分类 |
| `dynasty` | `string` | 纪/朝代/史论分类信息 |
| `summary` | `string` | 摘要 |
| `content` | `string` | 正文或 fallback 正文 |
| `source_url` | `string` | 来源 URL |
| `updated_at` | `string` | 来源更新时间或同步时间 |
| `section` | `string` | 所属栏目，默认“资治通鉴” |
| `volume_id`、`year_id` | `string?` | 目录层级关联 |
| `original`、`translation`、`notes` | `string` | 原文、白话、注释 |
| `tags` | `string[]` | 主题标签 |

#### 目录模型

- `LibrarySection`：`id`、`title`、`description`、`source_url`、`sort_order`。
- `Volume`：`id`、`section_id`、`title`、`dynasty`、`sort_order`。
- `ReadingYear`：`id`、`volume_id`、`title`、`era`、`sort_order`。
- 调用顺序为 `/api/sections` → `/api/sections/{section_id}/volumes` → `/api/volumes/{volume_id}/years` → `/api/years/{year_id}/items`。

### Python 模块接口

#### `ContentStore`

`ContentStore(path: str | Path = "data/dutongjian.db")` 会创建父目录、初始化 SQLite 表并在空库中写入本地种子。

| 方法 | 输入 | 返回值 |
| --- | --- | --- |
| `list_items(category=None, query=None, limit=20, year_id=None)` | 分类、模糊搜索、数量上限、年份 ID | `list[Item]` |
| `get_item(item_id)` | 条目 ID | `Item | None` |
| `categories()` | 无 | 排序后的 `list[str]` |
| `sections()` / `volumes(section_id)` / `years(volume_id)` | 目录 ID 按需传入 | 对应领域模型列表 |
| `knowledge(category=None, query=None, limit=20)` | 百科分类、关键词、数量上限 | `list[KnowledgeEntry]` |
| `get_knowledge(entry_id)` | 百科 ID | `KnowledgeEntry | None` |
| `upsert_items(items)` / `upsert_knowledge(entries)` | 领域模型列表 | `None`，写入 SQLite |
| `get_cache(key)` | cache key | 未过期正文 `str | None` |
| `put_cache(key, body, content_hash, ttl_seconds)` | key、正文、hash、TTL 秒数 | `None` |

#### 抓取器、解析器与同步器

- `RobotsAwareFetcher(base_url, opener=urlopen, robots_checker=None, sleep=time.sleep, retries=3, min_interval=1.0, timeout=15.0)`
  - `fetch(path: str) -> str | None`：把相对路径转换为绝对 URL；若跨域、robots 不允许、请求重试耗尽或 robots 无法读取，返回 `None`；成功返回 UTF-8 HTML，并缓存同 URL 结果。
- `parse_main_catalog(html: str, base_url: str) -> list[CatalogNode]`
  - 解析目录链接，生成 `id/title/level/source_url/metadata`；支持 `section`、`volume`、`year` 语义标记及基于上下文的 fallback 推断。
- `parse_reading_entries(html, base_url, section="资治通鉴", volume_id=None, year_id=None) -> list[Item]`
  - 解析阅读卡片或文章块；提取标题、摘要、原文、白话、注释、正文、朝代、标签和来源 URL；没有独立正文时按 `translation → original → 节点文本` fallback。
- `parse_knowledge_index(html: str, base_url: str) -> list[KnowledgeEntry]`
  - 解析百科卡片，生成百科 ID、标题、分类、摘要、正文、来源 URL 和更新时间。
- `PublicContentSync(fetcher, store)`
  - `sync_reading(path, section="资治通鉴", volume_id=None, year_id=None) -> SyncResult`。
  - `sync_knowledge(path) -> SyncResult`。
  - `SyncResult` 返回 `path`、`records`、`fetched`；抓取失败时 `records=0, fetched=False`。

#### `tongjian_sync.py` 公开 API 同步器

- `TongjianApiClient(base_url="https://www.dutongjian.com", cache_dir="data/tongjian-cache", retries=3, min_interval=1.0, timeout=30.0)`
  - `fetch_catalog() -> dict[str, Any]`：请求 `/api/table_of_contents`，结果缓存为 `catalog.json`。
  - `fetch_reign(reign_id: str) -> dict[str, Any]`：请求 `/api/reign?reign_tongjian_id=...`，按纪年 ID 缓存 JSON。
- `flatten_catalog(payload: dict[str, Any]) -> list[ReignRef]`
  - 将 `juan_list → emperor_list → reign_list` 展平为卷和纪年引用；缺少稳定 ID 或结果为空时抛出 `ValueError`。
- `parse_reign_items(payload: dict[str, Any], ref: ReignRef, source_url: str) -> list[Item]`
  - 保留繁体原文 `content`、简体正文 `content_jianti_auto`、译文 `content_fanyi`，并把公开关联字段序列化到 `notes`，人物/地点/主题等字段合并到 `tags`。
- `TongjianSync(api, store, checkpoint_path="data/tongjian-progress.json", on_progress=None)`
  - `run() -> SyncProgress`：导入目录，跳过 checkpoint 中已完成的纪年节点，按节点 upsert 内容；全部完成后才清理演示 `资治通鉴` 条目和目录。
  - `SyncProgress` 返回 `total_reigns`、`completed_reigns`、`content_records`。
- CLI 入口：`PYTHONPATH=service python -m app.tongjian_sync --allow-public-api [--base-url URL] [--database PATH] [--cache-dir PATH] [--checkpoint PATH] [--min-interval SECONDS]`。
- 当前限制：公开 API 客户端已有重试和节流路径，但尚未满足 429 `Retry-After` 响应头测试；真实全本同步尚未完成。

### Android 模块接口

`ReadingRepository` 是 UI 层使用的核心 contract：

| 方法 | 输入 | 返回值 / 副作用 |
| --- | --- | --- |
| `observeItems()` | 无 | `Flow<List<ReadingItem>>`，由 Room 持续发射 |
| `refreshHome()` | 无 | `Result<HomeFeed>`；成功 upsert Room，失败回退本地内容 |
| `search(query)` | 非空搜索词 | `Result<List<ReadingItem>>`；结果 ID 用于 UI 过滤 |
| `setFavorite(itemId, favorite)` | 条目 ID、收藏状态 | 更新 Room `isFavorite` |
| `recordOpened(itemId)` | 条目 ID | 更新 Room `lastOpenedAt` 毫秒时间戳 |
| `loadSections()` | 无 | `Result<List<LibrarySection>>` |
| `loadVolumes(sectionId)` | 栏目 ID | `Result<List<Volume>>` |
| `loadYears(volumeId)` | 卷 ID | `Result<List<ReadingYear>>` |
| `loadYearItems(yearId)` | 年份 ID | `Result<List<ReadingItem>>`，成功后写 Room |
| `loadKnowledge(query?, category?)` | 可选关键词和分类 | `Result<List<KnowledgeEntry>>` |

`DutongjianApi` 是 Retrofit 接口，方法和服务端路径一一对应。`ApiEnvelope<T>` 的 `code`、`message`、`data` 是解析入口；JSON 配置使用 `ignoreUnknownKeys = true`，因此后端新增字段不会让旧客户端直接失败。

## 🛠️ 本地开发与调试

### Backend 开发

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r service/requirements.txt
uvicorn app.main:app --app-dir service --reload --log-level debug
```

从仓库根目录启动时使用 `--app-dir service`；从 `service/` 目录启动时可以使用：

```bash
cd service
uvicorn app.main:app --reload --log-level debug
```

### Android 开发

```bash
cd android
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

API 地址在 `android/app/build.gradle.kts` 中读取 Gradle property `apiBaseUrl`：

| 配置项 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `apiBaseUrl` | 否 | `http://10.0.2.2:8000/` | Retrofit base URL，必须以 `/` 结尾 |
| `DUTONGJIAN_DB` | 否 | `data/dutongjian.db` | FastAPI 使用的 SQLite 路径 |
| `CORS_ORIGINS` | 否 | `*` | 逗号分隔的 CORS 来源；服务端当前只允许 GET |

示例：

```bash
cd android
./gradlew assembleDebug -PapiBaseUrl=http://192.168.1.20:8000/
```

`AndroidManifest.xml` 当前开启 `INTERNET` 和 `usesCleartextTraffic`，方便本地 HTTP 联调。生产环境应使用 HTTPS，并重新评估 cleartext 配置。

### 测试

```bash
# 服务端：当前 15 项通过，1 项失败
python3 -m pytest -q service/tests
python3 -m compileall -q service/app

# Android：单元测试与静态检查
cd android
./gradlew testDebugUnitTest
./gradlew lintDebug
```

测试约定：

- 服务端测试放在 `service/tests/`，文件使用 `test_*.py` 命名；API 使用 `httpx.ASGITransport`，采集器和同步器通过 fake opener/fetcher 隔离网络。
- Android 测试放在 `android/app/src/test/`，当前以 `ReadingViewModelTest` 验证搜索行为和离线内容 contract。
- 新增接口时同时增加成功 envelope、参数边界、404 和核心过滤条件测试。
- 新增同步或解析逻辑时必须测试同源/robots 边界、HTML 字段映射、去重和失败返回。

#### 当前验证结果

- `python3 -m pytest -q service/tests`：当前 **15 passed, 1 failed**。失败项是 `test_api_client_respects_retry_after_for_rate_limit`；测试通过 429 响应提供 `Retry-After: 17`，但 `TongjianApiClient` 当前未将该值加入等待时间。
- `python3 -m compileall -q service/app`：**通过**。
- `./gradlew testDebugUnitTest`：当前 **2 tests completed, 1 failed**。失败项是 `ReadingViewModelTest.uiStateStartsWithOfflineReadingContent`，原因是测试直接构造 `ReadingUiState()`，而离线种子目前只在 `ReadingViewModel` 构造时写入 state；这不是 Gradle 配置失败。
- Android 测试编译、Hilt 代码生成和 Kotlin 编译阶段已经执行成功；未把该失败误报为 Android 测试全绿。

### Log 调试

服务端：

- Uvicorn 使用 `--log-level debug` 查看启动和请求级日志。
- `python -m app.sync ...` 会在标准输出打印 `path`、`records`、`fetched` 字典；返回 `fetched=False` 时 CLI 退出码为 `1`。
- `python -m app.tongjian_sync --allow-public-api ...` 会逐节点输出 `completed x/y reigns, n content records`，并在结束时输出 `SyncProgress`。
- 当前代码没有独立 `/health` 路由，调试服务可使用 `curl http://127.0.0.1:8000/api/home` 验证启动和数据库初始化。

Android：

- `AppModule` 当前启用 `HttpLoggingInterceptor.Level.BASIC`，可在 Android Studio Logcat 中过滤 `OkHttp`、`AndroidRuntime` 或应用进程查看请求摘要和异常。
- 常见命令：

  ```bash
  adb logcat -c
  adb logcat | rg -i 'OkHttp|AndroidRuntime|dutongjian'
  ```

- Repository 会把普通网络异常转换为本地 fallback；调试网络问题时应同时观察 BASIC 请求日志和 UI 是否显示已有缓存。
- `CancellationException` 会继续传播，避免 ViewModel 协程取消被错误显示成业务错误。

### 数据同步调试

```bash
cd service
python -m app.sync \
  --base-url https://www.dutongjian.com \
  --path /public/path \
  --kind reading \
  --section 资治通鉴 \
  --volume-id zizhi-volume-001 \
  --year-id zizhi-year-001 \
  --database ../data/dutongjian.db \
  --min-interval 1.0
```

问题定位顺序：

1. 确认 DNS、HTTPS 和目标页面公开可访问。
2. 确认 `robots.txt` 可读取且允许 `dutongjian-app/1.0`。
3. 确认 `--base-url` 与页面 hostname 相同；跨域 URL 会在网络请求前被拒绝。
4. 用 parser 测试中的 HTML fixture 验证 selector 和字段映射，不要直接扩大抓取范围。
5. 检查 `SyncResult` 的 `fetched` 与 `records`，再查询 SQLite 内容。

公开 API 同步还应检查 `service/data/tongjian-progress.json` 的 `completed_reign_ids`，确认重跑时只请求未完成节点；不要在同步未完成时手动删除 checkpoint 或演示数据。

## 🧩 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| Android 访问不到 `127.0.0.1` | Emulator 中的 `localhost` 指向模拟器自身；使用 `10.0.2.2`，实体设备使用宿主机局域网 IP。 |
| Retrofit 报 base URL 错误 | `apiBaseUrl` 必须是合法 URL 并以 `/` 结尾。 |
| 同步器不发请求 | robots 无法读取、规则拒绝或 URL 跨域时会直接返回 `None`；这是默认安全行为。 |
| 公开 API 同步拒绝启动 | `tongjian_sync.py` 要求显式传入 `--allow-public-api`，用于确认只访问公开未登录 API。 |
| API 遇到 429 后测试失败 | 当前实现尚未消费 `Retry-After` 响应头；修复时应尊重服务端指定等待时间，并保留最小请求间隔。 |
| 服务端找不到数据库 | `DUTONGJIAN_DB` 是相对路径时相对于当前进程工作目录；Docker Compose 使用 `/app/data/dutongjian.db`。 |
| Android 测试显示离线列表为空 | 区分 `ReadingUiState()` 默认值与 `ReadingViewModel` 初始化逻辑；当前已知测试失败正是这个 contract 不一致。 |
| Release APK 无法安装到正式环境 | `assembleRelease` 产物当前 unsigned，需要由 CI 或发布环境配置 keystore 后签名。 |

## 🤝 贡献指南

### 分支规范

从 `main` 创建短生命周期分支，推荐使用：

```text
feature/<scope>-<description>
fix/<scope>-<description>
test/<scope>-<description>
docs/<scope>-<description>
chore/<scope>-<description>
```

例如：`feature/android-reading-history`、`fix/service-robots-cache`。一个分支尽量只解决一个主题，避免把无关格式化或构建产物带入提交。

### Commit 规范

仓库现有历史使用 Conventional Commits 风格，并在需要时附带 scope：

```text
<type>(<scope>): <imperative summary>
```

常用 `type`：

- `feat`：新增能力，例如 `feat(android): add catalog screen`
- `fix`：修复行为，例如 `fix(service): reject cross-origin fetch`
- `test`：新增或调整测试，例如 `test(android): cover offline content`
- `docs`：文档变更，例如 `docs: update development guide`
- `chore`：构建、依赖或工程维护

提交标题使用英文、祈使语气、简洁描述；不要把完整日志或临时调试输出提交到仓库。

### Pull Request 检查清单

- [ ] 说明变更目的、影响模块和已知限制。
- [ ] Backend 变更已运行 `python3 -m pytest -q service/tests`。
- [ ] Android 变更已运行相关 `./gradlew` 测试；若失败，说明第一个真实 root cause。
- [ ] API 字段、`ApiEnvelope`、Room schema 或迁移变更已同步更新文档和测试。
- [ ] 抓取相关变更没有扩大到未授权页面，没有绕过 robots、登录或访问控制。
- [ ] 没有提交 `.env`、keystore、APK、SQLite 运行库、`.gradle/` 或 `build/` 产物。
- [ ] 新增来源内容保留 `source_url` 和必要的更新时间/哈希信息。

## 📄 协议与发布注意事项

项目采用 [MIT License](./LICENSE)，完整许可文本见仓库根目录的 [LICENSE](./LICENSE)。Release 当前关闭 R8/minify 与资源收缩，并输出 unsigned APK；签名凭据只能通过发布环境注入。

[⬅️ 返回首页](./README.md)
