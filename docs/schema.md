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
| `id` | string | 是 | 栏目标识，小写字母/数字/`-`/`_`。目前固定有 `models`（模型）、`harness`（编码 / Agent 框架）、`other`（其他动态），以后可能增加 |
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

## 4. 文本约定

- 所有文本都是纯文本，**唯一的标记是 `**粗体**`**（非贪婪匹配 `\*\*(.+?)\*\*`）。不含 HTML，客户端显示时不要按 HTML 解析。
- 列表页如果不想渲染粗体，直接去掉 `**` 即可。
- 客户端应忽略不认识的字段，以便以后加字段时旧版本 app 照常工作。
