# AI 日报 · ai-daily

每天早上（北京时间约 8:17）发布一期**中文 AI 新闻简报**：覆盖过去 24 小时全球 AI 动态，重点是中美主要厂商的模型，以及 Codex、Claude Code、Cursor、Gemini CLI、TRAE、Qwen Code 等编码 / Agent 框架（harness）的进展，并单独设有**小米专栏**。

- 严格按时效：只收覆盖时间窗内的消息，每条标注北京时间和原帖链接
- 跨天去重：已报过的事件只有出现实质新进展才以“更新”标出
- 数据开放：每期都是一份结构化 JSON，任何客户端都可以直接读取

本仓库包含**数据**和**安卓客户端**两部分。安卓 app「AI 日报」见 [android/](android/README.md)，安装包在 [Releases](https://github.com/WikG1018/ai-daily/releases)。

## 目录结构

```
ai-daily/
├── data/
│   ├── index.json          # 期数索引（按日期倒序）
│   └── YYYY-MM-DD.json     # 每期简报
├── docs/
│   └── schema.md           # 数据格式说明（字段、类型、示例、访问地址）
├── tools/
│   ├── publish.py          # 发布脚本：校验 → 补 id → 写入 data/ → 重建索引 → 提交推送 → 刷新 CDN
│   └── build_brief.py      # 把一期 JSON 渲染成单文件 HTML（响应式、暗色模式）
├── android/                # 安卓客户端（Kotlin + Jetpack Compose）
├── LICENSE                 # MIT
└── README.md
```

## 数据地址

| | 地址 |
|---|---|
| 索引（主） | https://raw.githubusercontent.com/WikG1018/ai-daily/main/data/index.json |
| 索引（镜像） | https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/data/index.json |
| 单期（主） | `https://raw.githubusercontent.com/WikG1018/ai-daily/main/data/YYYY-MM-DD.json` |
| 单期（镜像） | `https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/data/YYYY-MM-DD.json` |

格式详见 [docs/schema.md](docs/schema.md)。

## 工具用法

只依赖 Python 3.9+ 标准库和 git。

```bash
# 发布一期（校验、补 id、写入 data/、重建 index.json、git commit + push、请求 jsDelivr purge）
python3 tools/publish.py /path/to/2026-10-09.json

python3 tools/publish.py brief.json --dry-run    # 只校验并预览 id 和索引摘要，不写文件
python3 tools/publish.py brief.json --no-push    # 写入并本地提交，不推送
python3 tools/publish.py brief.json --force      # 覆盖已存在的同日期一期（修订重发；已有 id 保持不变）
python3 tools/publish.py --reindex               # 只重建 index.json 并提交推送

# 生成 HTML 版（out/ 已在 .gitignore 中）
python3 tools/build_brief.py data/2026-10-08.json -o out/2026-10-08.html
```

## 许可证

[MIT](LICENSE)。简报内容为对公开信息的摘要整理，原始内容版权归各原帖作者及媒体所有。
