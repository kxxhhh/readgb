# dutongjian Android native refactor

这是一个独立的原生 Android 阅读应用和本地数据服务，用 Compose 重建读通鉴的公开阅读工作流。Android 端不是 WebView 套壳，也不会在运行时请求 `dutongjian.com` 或 `wiki.dutongjian.com`。

## 当前交付

- 原生 Jetpack Compose 首页、目录、搜索、百科和阅读详情。
- 资治通鉴、纪事本末、读通鉴论的 section -> volume -> year -> entry 目录模型。
- 原文、白话、注释、标签、古本、沙盘态势和决策卡阅读工作区。
- Room 离线缓存、收藏和阅读状态；FastAPI 提供统一 JSON REST API。
- 受 robots、域名限制、缓存、限速和退避约束的公开 HTML 解析/同步器。
- Debug 和 unsigned Release APK 构建；两个变体均关闭 R8/minify 和资源收缩。

高级阅读工作区是本项目独立实现的本地功能，不代表原网站会员权益。项目不模拟原网站账号、不绕过登录/验证码/权限/付费墙，也不采集或解锁受限内容。

## 架构

```text
公开 HTML --(人工指定路径的受控同步)--> FastAPI + SQLite
                                         ^
Android Compose -> Repository -> Retrofit --+
       |
       +-> Room offline cache
```

Android 按 `data / domain / ui` 分层，使用 MVVM、StateFlow、Repository、Retrofit、Room、Hilt 和 Coil。App 运行时唯一的网络目标是项目自己的 FastAPI 服务，`source_url` 只保存来源溯源信息。

## 环境要求

- Linux Dev Container / GitHub Codespace。
- JDK 21；Android Gradle Plugin 使用 Java 17 target。
- Android SDK platform 35 和 build-tools 35。
- Gradle wrapper 9.4.0，使用 `android/gradlew`。
- Python 3.12+；建议为 `service` 创建虚拟环境。
- 构建建议至少 8 GB 可用内存。

## Backend

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r service/requirements.txt
python -m pytest -q service/tests --cov=service/app --cov-report=term-missing
uvicorn app.main:app --app-dir service --reload
```

默认 SQLite 文件为 `data/dutongjian.db`。主要接口：

- `GET /api/home`
- `GET /api/search?q=`
- `GET /api/items?category=&year_id=`
- `GET /api/detail/{id}`
- `GET /api/sections`
- `GET /api/sections/{section_id}/volumes`
- `GET /api/volumes/{volume_id}/years`
- `GET /api/years/{year_id}/items`
- `GET /api/knowledge?q=&category=`
- `GET /api/knowledge/{id}`

### 公开数据同步

同步器不会自动遍历站点，只处理人工明确指定的单个公开路径。它会复用 robots 检查、同源限制、缓存、最小请求间隔和指数退避：

```bash
cd service
python -m app.sync \
  --base-url https://www.dutongjian.com \
  --path /公开阅读路径 \
  --kind reading \
  --database ../data/dutongjian.db
```

百科页面使用 `--kind knowledge` 和百科站点的 `--base-url`。只有在站点 robots 明确允许、页面公开可访问且请求频率合理时才运行同步。同步器不登录、不执行 JavaScript、不访问隐藏 API。

## Android

```bash
cd android
./gradlew clean assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew assembleRelease
```

本地服务运行在宿主机 `8000` 端口时，模拟器通过 `10.0.2.2:8000` 访问。Debug APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`；Release APK 输出到 `android/app/build/outputs/apk/release/app-release-unsigned.apk`。

Release 当前为 unsigned 产物，签名配置应由部署环境注入，不把 keystore 或凭据提交到仓库。

## 文档和状态

- [PROJECT_STATE.md](PROJECT_STATE.md)：长期增量开发状态、构建根因和下一步任务。
- [docs/site-analysis.md](docs/site-analysis.md)：站点结构、公开功能、数据边界和同步决策。
- `guide.txt`：原始工程约束和交付要求。

每次构建失败只在 `PROJECT_STATE.md` 记录第一个真实 root cause、相关文件、原因、修复方案和结果，不写入完整日志。
