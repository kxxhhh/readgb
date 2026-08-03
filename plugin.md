# 功能要求与当前实现状态

更新时间：2026-08-03

本文档把原始功能要求和当前源码状态放在一起。它是功能验收清单，不把“有源码入口”误写成“已经完成端到端验收”。

状态约定：

- [x]：已实现并在本地代码/测试中确认，行末添加 ✅。
- [ ]：尚未完成，或只完成部分代码，必须保留下一步。
- 当前数据规模、构建结果和运行中的同步进度以 PROJECT_STATE.md 为准。

## P0：离线底座

- [x] Room/SQLite 保存正文、收藏、最近阅读、笔记和本地状态；ItemDao 提供 FTS 搜索入口。✅
- [ ] 完整 30,989 条正文、294 卷、1,405 纪年进入 APK。当前旧导入已经清理，新同步尚未完成。
- [x] 正文保留 original、translation、notes、tags、volume_id、year_id 和 source_url；导入失败不覆盖已有 Room 数据。✅
- [x] 本地字形映射表和古籍字词提示表放入 android/app/src/main/assets/，应用启动时加载。✅
- [ ] 全量人物、地点、官职、专题百科资产。关联 JSON 会在全量正文校验后生成 offline_knowledge.json。
- [x] App 无网络时可以通过 OfflineSeed、Room 和本地资源启动，不把目标网站作为运行时依赖。✅

## P1：阅读器与声音

- [x] 详情页改为顶部元信息、正文工作区和底部工具入口；支持原文/译文并读及单独模式。✅
- [x] 字号、复制、分享、收藏、划线笔记、历史上下文和字词提示入口已接入。✅
- [x] 繁体、简体和异体字转换只发生在展示层，数据库原文不被改写。✅
- [ ] Android 系统 TTS 的句子高亮、自动滚动、睡眠计时器需要真实设备回归；控制器和 Compose 状态已经接入。
- [ ] Edge-TTS 的网络音频需要真实设备和网络回归；未配置时不影响系统 TTS。
- [ ] 典章制度、经济史专题的全量结构化标注。当前只使用已抓正文的 tags/公开关联字段。
- [x] 学习页朝代柱状图、最近 7 天趋势、人物共现关系图和文章下钻使用动态数据，不再固定展示少量示例节点。✅
- [ ] 全本数据规模下的图表性能、关系准确性和人物/时期覆盖验收。

## P2：个人数据和桌面入口

- [x] 本地阅读统计保存最近阅读时间、阅读时长和字数等状态；学习页展示趋势。✅
- [x] RemoteViews 名句 Widget 已实现，内容来自本地条目。✅
- [ ] “历史上的今天”完整历法匹配和自动刷新尚未完成。
- [ ] 294 卷覆盖率的完整用户看板需要全本目录和设备级行为回归后确认。

## AI 辅助和沙盘

- [ ] OpenAI 兼容接口/本机模型配置、API Key 本地保存和 AI 任务入口已接入，但真实模型调用尚未端到端验收。
- [ ] 古文语法拆解需要实现结构化分析和选中文本高亮。
- [ ] 反事实推演需要实现独立的任务模板、上下文边界和结果保存。
- [ ] 历史人物角色对话需要实现人物事实约束、对话界面和离线/联网边界。
- [ ] 重点战役沙盘需要地图、兵力、粮道和关键决策数据，当前只有阅读上下文/决策入口，不能宣称沙盘已完成。

## 工程、构建和发布

- [x] Android 分层为 data/domain/ui，使用 Compose、MVVM、StateFlow、Repository、Room、Retrofit 和 Hilt。✅
- [x] Gradle 使用 8 GB JVM 参数；CI 使用 JDK 21、SDK 35，并执行 Backend test/coverage、Android test、lint、Debug/Release build。✅
- [x] Release 支持由 KEYSTORE_FILE、KEY_ALIAS、STORE_PASSWORD、KEY_PASSWORD 注入完整环境签名；未配置时明确保留 unsigned 本地产物。✅
- [ ] GitHub Codespace 的真实签名构建和上传尚未在当前环境完成。
- [ ] gh release 自动发布没有纳入当前 CI workflow，发布仍需人工检查 APK、签名和 SHA-256。

## 数据同步和合规

- [x] 公开 API 目录入口和纪年详情入口已经确认并实现：/api/table_of_contents、/api/reign。✅
- [x] 同步器支持 robots.txt、同源限制、缓存、去重、Retry-After、退避、checkpoint、原子写入和受控 worker。✅
- [x] --reset 只用于首次清理；日常恢复脚本和 systemd 开机服务不带 --reset。✅
- [x] 新数据库、原始 JSON 缓存、checkpoint 和旧内容审计快照都位于项目 service/data/；/tmp 不承载新内容。✅
- [x] scripts/resume_crawler.sh 使用项目内锁文件，开机启动和人工恢复不会重复运行。✅
- [ ] 当前同步仍需完成 1,405 个纪年并做正文/目录/关联字段完整性校验。

## 验收顺序

1. 等待或恢复同步，确认 checkpoint 到 1,405/1,405。
2. 校验真实正文数量、唯一 ID、原文/译文、卷/纪年层级和关联字段。
3. 导出 offline_content.ndjson.gz、offline_catalog.json、offline_knowledge.json 到 Android assets。
4. 运行 Android JVM test、lint、Debug/Release build。
5. 在真实设备上检查安装、详情滚动、TTS、Widget、图表下钻和 AI 配置。
6. 将实际数量、命令、结果和未完成项写回 PROJECT_STATE.md，再提交和推送。
