# 站点分析与数据边界

更新时间：2026-08-03

## 1. 公开站点

公开入口：

- [读通鉴首页](https://www.dutongjian.com/)
- [读通鉴百科](https://wiki.dutongjian.com/)

公开站点的产品结构包含资治通鉴、通鉴纪事本末、读通鉴论，以及按卷、纪年和正文进入的阅读路径。百科侧以人物、地点、战争、官职、政权、典故等关系条目为主。

本项目只消费维护者已经确认的公开接口或公开 HTML。登录、会员、付款、私有数据和受限页面不在数据边界内。

## 2. 已确认的 JSON API

当前同步器实际使用并已成功取得目录/纪年响应：

~~~text
GET https://www.dutongjian.com/api/table_of_contents
GET https://www.dutongjian.com/api/reign?reign_tongjian_id=<公开纪年 ID>
~~~

目录结构：

~~~text
data.juan_list
  └── emperor_list
        └── reign_list
~~~

每个纪年详情在 data.ExtRef_Children_contents 中提供正文内容。当前解析器保留这些公开字段：

- content：来源原文。
- content_jianti_auto：公开简体字段。
- content_fanyi：公开译文。
- class_tags：站点标签。
- ExtRef_Children_topics：专题关联。
- ExtRef_Children_people：人物关联。
- ExtRef_Children_places：地点关联。
- ExtRef_Children_officials：官职关联。
- note_content 和其他公开注释字段：完整放入 Item.notes JSON。

项目从目录得到的目标结构为 294 卷、1,405 个纪年节点；导出校验的正文目标为 30,989 条。实际已完成数量会随同步变化，只读 PROJECT_STATE.md 和 checkpoint，不在本文档写易变的实时数。

## 3. 同步合规边界

TongjianApiClient 具备：

- base URL 同源生成，拒绝跨域。
- 非缓存请求先检查 robots.txt；无法读取时拒绝。
- 请求启动间隔、受控 worker 和共享 Retry-After 冷却。
- 网络错误和 HTTP 429 的退避。
- 原始 JSON 项目内磁盘缓存。
- 稳定公开 ID 去重。
- 每个纪年成功后原子更新 checkpoint。
- 失败纪年保留在 checkpoint 的 failed_reign_ids/last_errors 中，下一次恢复继续重试。

4 个 worker 只是有界执行器。若站点返回较长 Retry-After，实际吞吐量会由站点策略决定；不会通过提高并发、绕过 robots 或删除 checkpoint 来“提速”。

## 4. 项目运行边界

Android 不直接连接原站：

1. Retrofit 只连接本项目 FastAPI 的本地地址。
2. Room 和 APK assets 是阅读时的数据源。
3. source_url 只作为来源记录，不触发 App 网络请求。
4. App 不实现原站登录、会员、支付、收藏同步或阅读历史同步。

维护者数据路径固定为：

~~~text
service/data/dutongjian.db
service/data/tongjian-cache/
service/data/tongjian-progress.json
service/data/resync-archive-20260803/
android/app/src/main/assets/
~~~

新抓取内容不写入 /tmp。普通文本 .tmp 仅表示同一目标目录中的原子替换中间文件，成功后立即 replace，不是数据存储位置。

## 5. 解析器职责

service/app/parsers.py 是纯 HTML 解析代码，不发网络：

- parse_main_catalog：目录、section、volume、year 链接。
- parse_reading_entries：正文、原文、译文、摘要、注释、标签和层级。
- parse_knowledge_index：百科卡片标题、分类、摘要、正文和来源。

service/app/sync.py 负责显式路径的 HTML 同步；service/app/tongjian_sync.py 负责已确认的公开 JSON API 全本同步。两者都通过 ContentStore 入库，不在 Android 生命周期中运行。

## 6. 内容使用和版权边界

目标网站公开展示的内容只能在遵守站点规则和适用法律的前提下准备。项目不尝试解锁受限内容，也不把独立 App 功能描述为原站会员权益。

Android 资产必须经过字段完整性、数量、唯一 ID、卷/纪年关联和来源校验后才能作为正式全量资产。阶段性快照只能标记为阶段性内容，不得称为全本。
