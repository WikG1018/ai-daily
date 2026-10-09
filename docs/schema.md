# 数据格式说明（schema_version = 1）

写给安卓客户端开发。所有数据都是 UTF-8 编码的 JSON 静态文件，放在仓库 `data/` 目录，不需要鉴权。

## 1. 访问地址

| 用途 | 地址 |
|---|---|
| 主地址（GitHub Raw） | `https://raw.githubusercontent.com/WikG1018/ai-daily/main/` + `path` |
| 镜像（jsDelivr CDN） | `https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/` + `path` |

`path` 是相对仓库根目录的路径，例如：

- 索引：`https://raw.githubusercontent.com/WikG1018/ai-daily/main/data/index.json`
- 索引（镜像）：`https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/data/index.json`
- 单期：`https://raw.githubusercontent.com/WikG1018/ai-daily/main/data/2026-10-08.json`
- 单期（镜像）：`https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/data/2026-10-08.json`
- 关注清单：`https://raw.githubusercontent.com/WikG1018/ai-daily/main/data/watchlist.json`
- 关注清单（镜像）：`https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/data/watchlist.json`

实测（2026-10-09 01:16 北京时间）：两个地址的 `index.json` 和 `2026-10-08.json` 都返回 HTTP 200，内容一致。

缓存与容错建议：

- Raw：`Cache-Control: max-age=300`（约 5 分钟），`Content-Type` 是 `text/plain`，**不要依赖 Content-Type，直接按 JSON 解析**。国内网络有时连不上 raw.githubusercontent.com。
- jsDelivr：`Content-Type: application/json`，但响应头是 `Cache-Control: public, max-age=604800, s-maxage=43200`，即客户端可缓存 7 天、CDN 缓存 12 小时。发布脚本每次推送后会主动 purge CDN，通常几分钟内就是新文件；**客户端自己的 HTTP 缓存要绕开**：请求 `index.json` 时带请求头 `Cache-Control: no-cache`（OkHttp 可用 `CacheControl.FORCE_NETWORK`），否则可能一周都拿不到新一期。
- 建议：**先请求主地址，失败或超时（如 8 秒）再请求镜像**；两边都失败时显示本地缓存。
- 单期文件发布后基本不变（极少数情况下会修订重发），可以长期缓存；`index.json` 每次打开、下拉刷新或后台检查时都重新拉取。若单期的 `published_at` 与索引里的不同，说明修订过，可重新拉取。

## 2. 索引 `data/index.json`

列出全部期数，按日期**倒序**（最新在前）。

| 字段 | 类型 | 必有 | 说明 |
|---|---|---|---|
| `schema_version` | int | 是 | 当前为 `1`。字段只增不改；遇到更大的版本号仍可尽量解析 |
| `latest` | string \| null | 是 | 最新一期日期 `YYYY-MM-DD`，等于 `issues[0].date`；没有任何一期时为 `null` |
| `updated_at` | string | 是 | 索引生成时间，ISO 8601 北京时间，带 `+08:00`，如 `2026-10-09T01:30:00+08:00` |
| `issues` | array | 是 | 期数列表，见下表 |

`issues[]` 每项：

| 字段 | 类型 | 必有 | 说明 |
|---|---|---|---|
| `date` | string | 是 | 期号日期 `YYYY-MM-DD`（北京时间） |
| `title` | string | 是 | 标题，通常是 `AI 日报` |
| `highlights` | string | 是 | 今日要点摘要，**纯文本**（已去掉 `**` 标记），开头一两句，约 90 字以内，适合列表和通知正文 |
| `item_count` | int | 是 | 本期新闻条数（含子条目，不含 `group` 容器），与单期页报头一致 |
| `path` | string | 是 | 单期文件相对仓库根目录的路径，如 `data/2026-10-08.json` |
| `published_at` | string | 否 | 该期发布时间，ISO 8601 带 `+08:00` |

示例：

```json
{
  "schema_version": 1,
  "latest": "2026-10-08",
  "updated_at": "2026-10-09T01:30:00+08:00",
  "issues": [
    {
      "date": "2026-10-08",
      "title": "AI 日报",
      "highlights": "Anthropic 凌晨发布 Claude Haiku 5.5，基础 API 价格约为上一代的十分之一。Cursor、GitHub Copilot、Devin 当天就接入了。",
      "item_count": 23,
      "path": "data/2026-10-08.json",
      "published_at": "2026-10-09T01:30:00+08:00"
    }
  ]
}
```

**新一期通知**：后台定时拉取 `index.json`，若 `latest` 比本地记录的大（字符串比较即可），就用 `issues[0].highlights` 发本地通知，然后更新本地记录。每天早上约 8:20（北京时间）发布。

## 3. 单期 `data/YYYY-MM-DD.json`

### 顶层字段

| 字段 | 类型 | 必有 | 说明 |
|---|---|---|---|
| `schema_version` | int | 是 | 当前为 `1` |
| `date` | string | 是 | 期号日期 `YYYY-MM-DD` |
| `title` | string | 否 | 默认 `AI 日报` |
| `window` | object | 否 | 覆盖时间窗：`{ "start": "10-07 23:45", "end": "10-08 23:45", "tz": "北京时间" }`，`start`/`end` 格式 `MM-DD HH:MM` |
| `highlights` | string | 是 | 今日要点正文，可能含 `**粗体**`（见第 4 节） |
| `xiaomi` | object | 否 | 小米专栏，固定显示在今日要点之后：`{ "items": [Item], "empty_text": "..." }`。`items` 为空时显示 `empty_text`（缺省可显示“小米今天无新动态”） |
| `sections` | array | 是 | 栏目列表，按顺序显示，见下表 |
| `notes` | string[] | 否 | 页尾补充说明，每条一段，可含 `**粗体**` |
| `source_note` | string | 否 | 来源说明 |
| `generated_at` | string | 否 | 内容整理时间，格式 `YYYY-MM-DD HH:MM`（北京时间） |
| `published_at` | string | 是 | 发布时间，ISO 8601 带 `+08:00`（由发布脚本写入） |

`sections[]` 每项：

| 字段 | 类型 | 必有 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 栏目标识，小写字母/数字/`-`/`_`。常用的有 `models`（模型）、`harness`（编码 / Agent 框架）、`releases`（版本更新）、`people`（人物动态）、`other`（其他动态）。客户端应按通用栏目渲染**任何** id，不认识的 id 也照常显示 |
| `title` | string | 是 | 栏目名，如 `模型` |
| `icon` | string | 否 | 一个 emoji，如 `🧠` |
| `items` | Item[] | 是 | 新闻条目，可为空数组 |
| `empty_text` | string | 否 | `items` 为空时显示的文字（缺省可显示“本期无相关动态”） |

### Item（一条新闻；子条目结构相同）

| 字段 | 类型 | 必有 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 稳定唯一 id，格式 `YYYY-MM-DD-NNN`（日期 + 三位序号），如 `2026-10-08-001`。**全局唯一**，可直接用作详情页路由参数和已读状态的键。同一期修订重发时已有 id 不变 |
| `vendor` | string | 是 | 厂商或产品标签，如 `Anthropic`、`阶跃星辰` |
| `region` | string | 是 | `cn` 中国（建议红色）、`us` 美国（蓝色）、`intl` 其他（灰色）。遇到未知值按 `intl` 处理 |
| `title` | string | 是 | 标题，可能含 `**粗体**` |
| `summary` | string | 否 | 一两句说明 |
| `time` | string | 否 | 北京时间 `MM-DD HH:MM`，如 `10-08 02:01`（年份取期号所在年；跨年时 12 月的条目属于上一年） |
| `links` | array | 否 | `[{ "label": "原帖", "url": "https://..." }]`，`url` 只会是 http/https；`label` 缺省时显示“原帖”。原帖大多在 x.com |
| `children` | Item[] | 否 | 子条目（后续进展、相关报道），显示在父条目下方 |
| `update` | bool | 否 | `true` 表示这是之前报过的事件出现了实质新进展，建议显示“更新”徽标 |
| `update_note` | string | 否 | 配合 `update`，一句话说明新在哪里 |
| `group` | bool | 否 | `true` 表示只是分组容器（如“GitHub Copilot 与 VS Code 更新”），本身没有链接，内容在 `children` 里；不计入条数 |
| `product` | string | 见下 | 本条的**主要** harness 产品，值为 `watchlist.json` 里 `products[].id`，如 `codex-cli`。`releases` 栏目必填 |
| `products` | string[] | 否 | 其他**相关**产品 id（可多个） |
| `person` | string | 见下 | 本条的**主要**人物 / 账号，值为 `watchlist.json` 里 `people[].id`（小写 handle），如 `thsottiaux`。`people` 栏目必填 |
| `people` | string[] | 否 | 其他相关人物 id（可多个） |
| `version` | string | 见下 | 版本号原文，如 `0.162.0`、`dsh-v0.2.1-alpha.1`。`releases` 栏目必填，其他栏目可选 |

**关注对象匹配规则**（app 自定义关注用）：一条新闻关联的产品集合 = `{product} ∪ products`，人物集合 = `{person} ∪ people`（字段缺省按空处理，单值和数组可能重复，要去重）。用户选了某个产品或人物，凡是集合里包含它的条目都算命中，不论在哪个栏目里。`releases` / `people` 栏目以外的条目只有明确围绕某个产品或人物时才会带这些字段，不带的条目不参与这种过滤。所有 id 都保证存在于发布时的 `watchlist.json`（由 `publish.py` 校验）；客户端遇到清单里没有的 id，按“未知”忽略即可，不要崩溃。子条目（`children`）也可以带这些字段，规则相同。

id 编号顺序：小米专栏 → 各栏目，深度优先（父条目在前，紧接着它的子条目）。`group` 容器和子条目也有 id。

示例（节选）：

```json
{
  "schema_version": 1,
  "title": "AI 日报",
  "date": "2026-10-08",
  "window": { "start": "10-07 23:45", "end": "10-08 23:45", "tz": "北京时间" },
  "highlights": "Anthropic 凌晨发布 **Claude Haiku 5.5**，基础 API 价格约为上一代的十分之一。……",
  "xiaomi": { "items": [], "empty_text": "本期未收录小米相关动态。" },
  "sections": [
    {
      "id": "models", "icon": "🧠", "title": "模型",
      "items": [
        {
          "id": "2026-10-08-001",
          "vendor": "Anthropic",
          "region": "us",
          "title": "Anthropic 发布 Claude Haiku 5.5",
          "summary": "官方称它是旗下最便宜、最快、能力最强的小模型……",
          "time": "10-08 02:01",
          "links": [{ "label": "原帖", "url": "https://x.com/claudeai/status/2107894039626277339" }],
          "children": [
            {
              "id": "2026-10-08-002",
              "vendor": "Artificial Analysis",
              "region": "us",
              "title": "第三方评测：智能指数 43 分",
              "summary": "……",
              "time": "10-08 03:12",
              "links": [{ "label": "原帖", "url": "https://x.com/ArtificialAnlys/status/2107911905822351609" }]
            }
          ]
        }
      ]
    }
  ],
  "notes": ["国内的 DeepSeek、通义、豆包、Kimi、智谱、腾讯和百度，这 24 小时里都没有发布新模型或新产品。"],
  "source_note": "信息来源：X（原帖）及文中注明的媒体报道。……",
  "published_at": "2026-10-09T01:30:00+08:00"
}
```

### 栏目约定（2026-10-09 起）

- 推荐顺序：`models` → `harness` → `releases` → `people` → `other`。没有内容的栏目可以省略，也可以给空 `items` 加 `empty_text`。
- `releases`（📦 版本更新）：各家 harness 的新版本，来自 `tools/check_versions.py`。每个顶层非 `group` 条目**必须**带 `product` 和 `version`。`vendor` 填厂商，`title` 形如 `Codex CLI 0.162.0`，`links` 放 release 页。
- `people`（🗣️ 人物动态）：负责人和核心成员的 X 帖子，名单见 `docs/watchlist.md`。每个顶层非 `group` 条目**必须**带 `person`。`vendor` 填公司，`links` 放原帖。
- 这两个栏目的条目和其他栏目一样计入 `item_count`，id 也按同样的规则连续编号。

示例（节选）：

```json
{ "id": "releases", "icon": "📦", "title": "版本更新", "items": [
  { "id": "2026-10-10-020", "vendor": "OpenAI", "region": "us", "title": "Codex CLI 0.162.0",
    "product": "codex-cli", "version": "0.162.0", "time": "10-09 02:55",
    "summary": "新增托管 Git worktree 工具。",
    "links": [{ "label": "Release", "url": "https://github.com/openai/codex/releases/tag/rust-v0.162.0" }] } ] },
{ "id": "people", "icon": "🗣️", "title": "人物动态", "items": [
  { "id": "2026-10-10-021", "vendor": "OpenAI", "region": "us", "title": "Tibo：Codex cloud 重新上线",
    "person": "thsottiaux", "products": ["codex-cli"], "time": "10-08 14:38",
    "links": [{ "url": "https://x.com/thsottiaux/status/2108084615349170480" }] } ] }
```

## 4. 文本约定

- 所有文本都是纯文本，**唯一的标记是 `**粗体**`**（非贪婪匹配 `\*\*(.+?)\*\*`）。不含 HTML，客户端显示时不要按 HTML 解析。
- 列表页如果不想渲染粗体，直接去掉 `**` 即可。
- 客户端应忽略不认识的字段，以便以后加字段时旧版本 app 照常工作。

## 5. 关注清单 `data/watchlist.json`

app 的“自定义关注”（只看选中的 harness / 人物）用它来列出可选项。由 `tools/build_watchlist.py` 从 `tools/harness_sources.json` 和 `tools/x_people.json` 生成；`publish.py` 每次发布和 `--reindex` 时都会自动重建。内容没变时文件不动，`updated_at` 也不变。地址见第 1 节，缓存策略同 `index.json`：低频变化，建议每天随 `index.json` 拉一次，失败时用本地缓存。

| 字段 | 类型 | 必有 | 说明 |
|---|---|---|---|
| `schema_version` | int | 是 | 当前为 `1` |
| `updated_at` | string | 是 | 清单内容最后一次变化的时间，ISO 8601 带 `+08:00` |
| `products` | array | 是 | 可关注的 harness 产品，见下表 |
| `people` | array | 是 | 可关注的人物和官方账号，见下表 |

`products[]`：

| 字段 | 类型 | 必有 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 稳定 id（小写字母、数字、`-`），如 `codex-cli`、`mimo-code`。条目里的 `product` / `products` 引用它 |
| `name` | string | 是 | 显示名，如 `Codex CLI`、`Qoder CLI（qodercli）` |
| `vendor` | string | 是 | 厂商，如 `OpenAI`、`小米` |
| `region` | string | 是 | `cn` / `us` / `intl`，规则同 Item |
| `kind` | string | 是 | 目前固定为 `harness`，以后可能增加其他类型；不认识的值按 `harness` 处理或忽略 |
| `url` | string | 否 | 版本发布页（GitHub releases / npm / 官方 changelog），可做“查看更新日志”入口 |

`people[]`：

| 字段 | 类型 | 必有 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 小写 X handle，如 `thsottiaux`、`_luofuli`。条目里的 `person` / `people` 引用它 |
| `name` | string | 是 | 显示名，如 `罗福莉 Fuli Luo` |
| `handle` | string | 是 | 原始大小写的 X handle（不带 @），如 `_LuoFuli` |
| `org` | string | 是 | 公司 / 组织，如 `小米` |
| `role` | string | 是 | 身份说明，如 `Xiaomi MiMo 团队负责人` |
| `region` | string | 是 | `cn` / `us` / `intl` |
| `kind` | string | 是 | `person` 是个人，`official` 是官方号。设置页建议分开展示，或默认只列 `person` |
| `url` | string | 否 | 主页 `https://x.com/<handle>` |

客户端约定：

- 用户的关注选择只存 id。清单更新后，已选但不在清单里的 id 保留不显示，以免误删用户设置。
- 清单只增不删是目标，但不保证。下线的产品或人物可能被移除，id 永远不会被复用到别的对象。
- 和厂商专栏（`vendor` 匹配）是两套独立机制，可以并存。

