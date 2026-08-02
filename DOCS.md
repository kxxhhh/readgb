# 📖 读通鉴开发文档

[⬅️ 返回首页](./README.md)

本文档描述当前源码的真实架构、接口、运行方式、数据同步边界和贡献规则。内容以 guide.txt、源码、测试和 PROJECT_STATE.md 为准；若文档与代码冲突，以可运行代码和最新测试结果为准。

## 1. 架构设计

### 1.1 总体设计

项目采用“维护者联网准备数据，Android 端离线消费”的单仓库结构：

~~~text
公开 HTML / JSON API
        │ 仅维护者环境
        ▼
RobotsAwareFetcher / TongjianApiClient
        │ 同源、限速、重试、缓存
        ▼
parsers.py / tongjian_sync.py
        │ Item、KnowledgeEntry、目录层级
        ▼
ContentStore(SQLite)
        │ 完整性校验
        ▼
export_android.py
        │ offline_content.ndjson.gz
        │ offline_catalog.json
        ▼
Android APK assets
        ▼
ReadingRepositoryImpl
        ├── Room reading_items
        ├── OfflineSeed fallback
        └── StateFlow
        ▼
ReadingViewModel
        ▼
Jetpack Compose UI
~~~

Android 依赖方向为 ui -> domain <- data：

- ui 只依赖领域模型和 ReadingRepository，不直接调用 Retrofit 或 Room。
- domain 保存 ReadingItem、目录模型、百科模型和稳定 Repository contract。
- data 负责 DTO、Room Entity、资产导入、网络适配和 Repository 实现。
- di 负责 Hilt 依赖组装。
- service 与 Android 运行时解耦；同步器不会在 App 生命周期中运行。

### 1.2 模块划分

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| Android UI | android/app/src/main/java/com/dutongjian/app/ui/ | Compose 页面、导航状态、详情、搜索、目录和加载状态 |
| ViewModel | ui/ReadingViewModel.kt | ReadingUiState、搜索、分类、目录级联、收藏、历史、百科 |
| Domain | domain/model/、domain/repository/ | 业务数据类型与 Repository 接口 |
| Network | data/network/ | Retrofit endpoint、ApiEnvelope<T>、DTO；只服务本地联调 |
| Local | data/local/ | ItemEntity、ItemDao、Room schema 和迁移 |
| Repository | data/ReadingRepositoryImpl.kt | Room Flow、APK 资产、远程 fallback、本地种子 |
| DI | di/AppModule.kt | Hilt、JSON、OkHttp、Retrofit、Room |
| FastAPI | service/app/main.py | REST 路由、参数校验和统一响应 envelope |
| Store | service/app/store.py | SQLite 表、种子、查询、upsert、HTTP cache |
| Crawler | service/app/crawler.py | robots、同源、限速、重试、HTML cache |
| Parser | service/app/parsers.py | 纯 HTML 结构化解析，不发网络请求 |
| Sync | service/app/sync.py | 指定 HTML 页面同步 |
| Tongjian Sync | service/app/tongjian_sync.py | 公开《资治通鉴》 API 的完整层级导入 |
| Export | service/app/export_android.py | 校验并生成 Android offline assets |

### 1.3 离线运行策略

1. ReadingUiState() 默认携带 OfflineSeed，所以无网络启动不会出现空状态。
2. ReadingRepositoryImpl.observeItems() 确保种子存在，再观察 Room。
3. 若 APK 内含打包后的 `offline_content.ndjson`，首次准备本地内容时按 500 条批量 upsert；Repository 同时兼容未被 Android 打包工具展开的 `offline_content.ndjson.gz`。
4. 若资产缺失或导入失败，保留种子和已有 Room 缓存，不阻塞应用启动。
5. 目录 fallback 优先读取 offline_catalog.json；资产缺失时退回 OfflineSeed 的少量目录。
6. Retrofit 默认地址是 http://10.0.2.2:8000/，只用于本地联调；连接失败后 Repository 返回本地数据。
7. source_url 是内容来源字段，不代表 App 运行时会访问目标网站。

当前 Android 网络适配仍保留 INTERNET 权限和 cleartext 本地联调配置。产品离线契约依靠“本地数据优先 + 普通网络异常 fallback”保证；若未来要从权限层面完全移除网络能力，必须同步删除 Retrofit 路径、更新 Manifest 并增加断网设备测试。

### 1.4 服务端数据流

FastAPI 路由是薄编排层：参数由 FastAPI Query 校验，数据读取交给 ContentStore，返回值由 envelope() 包装。同步器先获取目录，再逐个获取纪年，解析后 upsert SQLite，并在每个成功节点之后写 checkpoint。

同步完成前不清除演示种子；只有 completed_reigns == total_reigns 时才清理 zizhi-tongjian-* 演示条目和旧的 zizhi-volume-* / zizhi-year-* 演示记录。真实导入条目使用 zztj-* 前缀，因此不会被这一步误删。

## 2. 核心 API 与接口

### 2.1 统一响应格式

成功：

~~~json
{
  "code": 0,
  "message": "success",
  "data": {}
}
~~~

资源不存在：

~~~json
{
  "code": 404,
  "message": "item not found",
  "data": null
}
~~~

### 2.2 FastAPI REST API

| Method | Path | 输入 | 返回 data |
| --- | --- | --- | --- |
| GET | /api/home | 无 | {items, categories, sections} |
| GET | /api/search | q：1-80 字符；limit：1-50，默认 20 | {query, items} |
| GET | /api/items | category?、year_id?、limit：1-50 | {category, year_id, items} |
| GET | /api/detail/{item_id} | item_id | 单个 Item；不存在为 404 |
| GET | /api/sections | 无 | {sections} |
| GET | /api/sections/{section_id}/volumes | section_id | {section_id, volumes} |
| GET | /api/volumes/{volume_id}/years | volume_id | {volume_id, years} |
| GET | /api/years/{year_id}/items | year_id；limit：1-100，默认 50 | {year_id, items} |
| GET | /api/knowledge | q?：1-80；category?；limit：1-50 | {category, query, items, categories} |
| GET | /api/knowledge/{entry_id} | entry_id | 单个 KnowledgeEntry；不存在为 404 |

服务环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| DUTONGJIAN_DB | data/dutongjian.db | SQLite 文件路径 |
| CORS_ORIGINS | * | 逗号分隔的允许来源；当前路由只开放 GET |

### 2.3 数据模型

Item / Android ItemDto 字段：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| id | string | 稳定主键；真实通鉴正文使用 zztj-<tongjian_id> |
| title | string | 正文段落标题 |
| category | string | 例如 资治通鉴 |
| dynasty | string | 纪或时代信息 |
| summary | string | 展示摘要 |
| content | string | 简体正文 fallback |
| original | string | 来源繁体原文 |
| translation | string | 白话译文 |
| notes | string | 公开注释和关联对象 JSON |
| tags | string[] | 主题、人物、地点、官职标签 |
| volume_id / year_id | string? | 卷和纪年层级 |
| source_url / updated_at | string | 来源和同步时间 |

目录模型：

- LibrarySection：id、title、description、source_url、sort_order。
- Volume：id、section_id、title、dynasty、sort_order。
- ReadingYear：id、volume_id、title、era、sort_order。
- 读取顺序：sections -> volumes -> years -> items。

### 2.4 Python 核心模块

#### ContentStore

ContentStore(path: str | Path = data/dutongjian.db) 初始化父目录、SQLite schema 和本地演示种子。

| 方法 | 输入 | 返回值或副作用 |
| --- | --- | --- |
| list_items(category?, query?, limit=20, year_id?) | 筛选条件 | list[Item] |
| get_item(item_id) | 内容 ID | Item 或 None |
| sections() / volumes(section_id) / years(volume_id) | 目录 ID | 对应模型列表 |
| knowledge(category?, query?, limit=20) | 百科过滤 | list[KnowledgeEntry] |
| get_knowledge(entry_id) | 百科 ID | KnowledgeEntry 或 None |
| upsert_items(items) | list[Item] | 写入 SQLite |
| upsert_knowledge(entries) | list[KnowledgeEntry] | 写入 SQLite |
| upsert_volumes(volumes) / upsert_years(years) | 目录模型列表 | 写入真实目录 |
| count_items(category?) | 可选分类 | int |
| get_cache(key) / put_cache(...) | cache key、正文、hash、TTL | HTTP cache 读写 |

#### RobotsAwareFetcher 与 HTML 同步

- RobotsAwareFetcher(base_url, opener=urlopen, robots_checker=None, sleep=time.sleep, retries=3, min_interval=1.0, timeout=15.0)
  - fetch(path: str) -> str | None：跨域或 robots 拒绝返回 None；失败重试后返回 None；成功返回 UTF-8 HTML 并缓存同 URL。
- parse_main_catalog(html, base_url) -> list[CatalogNode]：纯函数，解析目录链接。
- parse_reading_entries(html, base_url, section=资治通鉴, volume_id=None, year_id=None) -> list[Item]：纯函数，解析文章块、原文、译文、注释和标签。
- parse_knowledge_index(html, base_url) -> list[KnowledgeEntry]：纯函数，解析百科卡片。
- PublicContentSync.sync_reading(...)、sync_knowledge(...)：返回 SyncResult(path, records, fetched)。

#### TongjianApiClient 与 TongjianSync

TongjianApiClient 默认配置：

~~~python
TongjianApiClient(
    base_url="https://www.dutongjian.com",
    cache_dir="data/tongjian-cache",
    retries=3,
    min_interval=5.0,
    timeout=30.0,
)
~~~

- fetch_catalog() -> dict：缓存 /api/table_of_contents。
- fetch_reign(reign_id: str) -> dict：缓存 /api/reign?reign_tongjian_id=...。
- flatten_catalog(payload) -> list[ReignRef]：展开 juan_list -> emperor_list -> reign_list；缺 ID 或空目录抛出 ValueError。
- parse_reign_items(payload, ref, source_url) -> list[Item]：保留 content、content_jianti_auto、content_fanyi，将完整公开关联对象写入 notes，并从主题/人物/地点/官职形成 tags。
- TongjianSync.run() -> SyncProgress：导入目录，跳过 checkpoint 已完成的纪年，按成功节点写入 SQLite；全部完成才清理演示数据。
- SyncProgress：total_reigns、completed_reigns、content_records。

重试策略：普通错误指数退避，HTTP 429 优先读取 Retry-After；磁盘 cache 命中不发请求；checkpoint 使用临时文件写入后 replace，避免中断产生半文件。

#### export_android.py

- export_content(database, output, expected_count=30989) -> int：只导出数量严格匹配的 zztj-* 内容为 gzip NDJSON。
- export_catalog(database, output, expected_volumes=294, expected_years=1405) -> dict[str, int]：只导出数量严格匹配的真实目录 JSON。
- export_partial_content(database, output, checkpoint) -> int：只导出 checkpoint 中已完成纪年的真实 zztj-* 内容，并逐条校验正文、原文、译文和层级字段。
- export_partial_catalog(database, output, checkpoint) -> dict[str, int]：只导出部分正文对应的卷/纪年目录，和同一次 CLI 调用中的内容快照保持一致。
- 任一完整性校验失败都会抛出 ValueError，目标文件不会被替换。

命令行默认执行严格全量导出；只有显式传入 `--allow-partial --checkpoint ...` 才允许生成阶段性 Android 资源。阶段性资源代表已完成内容快照，不代表 30,989 条全本已经完成。

### 2.5 Android ReadingRepository

| 方法 | 输入 | 返回值 / 副作用 |
| --- | --- | --- |
| observeItems() | 无 | Flow<List<ReadingItem>>，确保资产/种子后观察 Room |
| refreshHome() | 无 | 成功写 Room；异常回退本地内容 |
| search(query) | 搜索词 | Result<List<ReadingItem>>，异常时本地过滤 |
| setFavorite(itemId, favorite) | ID、布尔值 | 更新 Room |
| recordOpened(itemId) | ID | 写入时间戳 |
| loadSections() | 无 | 远程成功或资产/种子目录 |
| loadVolumes(sectionId) | 栏目 ID | 卷列表 |
| loadYears(volumeId) | 卷 ID | 纪年列表 |
| loadYearItems(yearId) | 纪年 ID | 正文列表并写 Room |
| loadKnowledge(query?, category?) | 可选过滤 | 百科列表或种子回退 |

withOfflineFallback() 会重新抛出 CancellationException，只把普通异常转换为本地成功结果，避免取消协程被误报为业务错误。

## 3. 本地开发与调试

### 3.1 Backend

~~~bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r service/requirements.txt
python3 -m pytest -q service/tests
python3 -m compileall -q service/app
uvicorn app.main:app --app-dir service --reload --log-level debug
~~~

验证服务：

~~~bash
curl http://127.0.0.1:8000/api/home
curl 'http://127.0.0.1:8000/api/search?q=周威烈王'
curl http://127.0.0.1:8000/api/sections
~~~

环境变量：

~~~bash
export DUTONGJIAN_DB=/absolute/path/dutongjian.db
export CORS_ORIGINS=http://localhost:3000,http://10.0.2.2:8000
~~~

### 3.2 Android

~~~bash
cd android
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
~~~

本地 API 地址由 apiBaseUrl Gradle property 注入：

~~~bash
./gradlew assembleDebug -PapiBaseUrl=http://10.0.2.2:8000/
~~~

默认值为 http://10.0.2.2:8000/，这只适合 Android Emulator 访问宿主机。真机需要局域网 IP。若没有 Backend，连接会超时并回退到本地内容；不应把该错误理解为离线数据不存在。

Logcat：

~~~bash
adb logcat -c
adb logcat | rg -i 'OkHttp|AndroidRuntime|dutongjian'
~~~

HttpLoggingInterceptor 当前为 BASIC，只用于本地联调。正常离线阅读不应出现目标站点请求。

### 3.3 数据同步与断点恢复

~~~bash
PYTHONPATH=service python -m app.tongjian_sync \
  --allow-public-api \
  --database service/data/dutongjian.db \
  --cache-dir service/data/tongjian-cache \
  --checkpoint service/data/tongjian-progress.json \
  --min-interval 5.0
~~~

检查进度：

~~~bash
jq '{total_reigns, completed: (.completed_reign_ids | length), updated_at}' \
  service/data/tongjian-progress.json
find service/data/tongjian-cache/reigns -type f -name '*.json' | wc -l
~~~

安全恢复原则：不要删除 checkpoint、cache 或未完成数据库；重新运行会读取同一目录并跳过已完成纪年。出现 429 时允许进程按 Retry-After 等待；不要改成并发请求绕过限流。

### 3.4 测试策略

- API 测试使用 httpx.ASGITransport，不依赖真实网络。
- crawler 测试使用 fake opener，覆盖跨域/robots 拒绝、重试和 cache。
- sync 测试使用 fake API，覆盖目录展开、完整字段保留、checkpoint 恢复和 429 退避。
- Android JVM 测试覆盖搜索命中集合、短查询清除和 ReadingUiState() 离线初始内容。
- 新增接口必须补 envelope、边界参数、404 和核心过滤测试。

当前验证基线：

- python3 -m pytest -q service/tests：20 passed。
- python3 -m compileall -q service/app：通过。
- ./gradlew testDebugUnitTest：通过。
- Release APK：默认 unsigned，签名属于发布环境责任。

## 4. 常见问题

| 现象 | 处理 |
| --- | --- |
| 首页显示 failed to connect to 10.0.2.2:8000 | 这是本地联调 Backend 不可达；停止依赖网络的路径，App 应继续显示 APK/Room/种子内容。检查是否使用了最新构建。 |
| Emulator 无法访问 127.0.0.1 | Emulator 中 localhost 指向自身，使用 10.0.2.2；真机使用开发机局域网 IP。 |
| Retrofit base URL 报错 | apiBaseUrl 必须是合法 URL 且以 / 结尾，只填写本地服务。 |
| 同步器没有发请求 | 可能是 cache 命中、跨域或 robots 拒绝；检查命令参数和进度文件。 |
| 同步器遇到 429 | 降低请求频率，保留 checkpoint，等待 Retry-After；不要并发绕过限制。 |
| 导出脚本拒绝生成 assets | 数据量或目录层级未达到预期；这是保护机制，先完成同步和校验。 |
| Release APK 无法正式发布 | 当前产物 unsigned，需要 CI/发布环境注入签名配置。 |

## 5. 贡献指南

### 5.1 分支规范

从 main 创建短生命周期分支：

~~~text
feature/<scope>-<description>
fix/<scope>-<description>
test/<scope>-<description>
docs/<scope>-<description>
chore/<scope>-<description>
~~~

一个分支只处理一个主题；不要提交 build/、.gradle/、SQLite 运行库、cache、checkpoint、APK、keystore 或 .env。

### 5.2 Commit 规范

仓库采用 Conventional Commits：

~~~text
<type>(<scope>): <简洁的英文祈使句摘要>
~~~

常用类型：

- feat：新增能力，例如 feat(android): add offline catalog import。
- fix：修复行为，例如 fix(service): respect retry-after header。
- test：测试或回归契约。
- docs：README、开发文档或状态记录。
- chore：构建、依赖和工程维护。

### 5.3 提交前检查

~~~bash
python3 -m pytest -q service/tests
python3 -m compileall -q service/app
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
~~~

涉及内容同步时，还要确认：

- 未扩大到未确认的私有、登录或付费路径。
- 保留同源、robots、限速、重试和 checkpoint 约束。
- 测试覆盖重复、失败、中断恢复和字段完整性。
- PROJECT_STATE.md 记录真实数量、问题和下一步。

## 6. 发布说明

APK 构建：

~~~bash
cd android
./gradlew assembleDebug
./gradlew assembleRelease
~~~

发布前必须检查 APK SHA-256、签名状态和构建日志。只有生成可用 APK 后，才按项目约定使用 gh release create 或 gh release upload 上传附件；不把 unsigned 产物误标为正式签名版本。

[⬅️ 返回首页](./README.md)
