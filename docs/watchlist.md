# 监控清单：harness 版本源 + 人物 X 账号

每日简报里有两块内容靠这份清单来做：

- **版本更新**（分区 id `releases`）：各家 harness，也就是编码 / Agent 运行框架的新版本
- **人物动态**（分区 id `people`）：各家负责人、核心成员在 X 上发的内容

机器可读的清单有两份：`tools/harness_sources.json`（版本源）和 `tools/x_people.json`（X 账号）。这份文档是给人看的说明和核实记录，两边改动要同步。

核实时间：2026-10-09 12:45 前后（北京时间）。

---

## A. harness 版本源

### 怎么查

```bash
python3 tools/check_versions.py                  # 过去 24 小时的新版本（JSON）
python3 tools/check_versions.py --hours 48       # 过去 48 小时
python3 tools/check_versions.py --since 2026-10-08T23:45 --until 2026-10-09T23:45   # 和简报时间窗对齐（无时区按北京时间）
python3 tools/check_versions.py --latest         # 每个产品当前最新版本，不看时间窗
python3 tools/check_versions.py --only codex-cli,claude-code -o /tmp/v.json
python3 tools/check_versions.py --prerelease     # 连 alpha / beta / nightly / preview 一起算
```

输出字段：`product_id`、`product`、`vendor`、`region`、`version`、`published_bjt`（北京时间 `YYYY-MM-DD HH:MM`；源里只有日期时写成 `YYYY-MM-DD（仅日期）`）、`prerelease`、`url`（release 页或 npm 版本页）、`source`、`notes`（release notes 前 6 行，npm 源为空）、`changelog`（npm 源附带的更新日志地址）。查询失败的源放进 `errors`，不影响其他产品。

规则：

- 每个产品的 `sources` 按顺序尝试，前一个出错或没有符合条件的版本才换下一个。
- 默认只统计正式版。DeepSeek Harness 目前只发 alpha / rc，所以在源里单独开了 `include_prerelease`。
- GitHub 优先走 `gh api`（已登录就不受匿名每小时 60 次的限制），没有 gh 时直接请求 API，可以设 `GITHUB_TOKEN`。
- 只有日期的源（Cursor RSS、Qoder / Step Code / TRAE 文档页）：只要这一天（北京时间）和时间窗有交集就算进来，所以跨天时可能和前一期重复，入选前要按版本号去重。
- `--latest` 优先按版本号取最大值，这样补发的旧版本 release 不会被当成最新版（例：MiMo Code v0.1.14 的 release 比 v0.1.15 晚一天发布）。

### 美国侧

| 厂商 | 产品 | id | 版本源（按顺序） | 当前最新 | 发布时间（北京时间） |
|---|---|---|---|---|---|
| OpenAI | Codex CLI | `codex-cli` | GitHub releases `openai/codex` （tag `^rust-v\d+\.\d+\.\d+$`）<br>npm `@openai/codex` | [rust-v0.162.0](https://github.com/openai/codex/releases/tag/rust-v0.162.0) | 2026-10-09 02:55 |
| Anthropic | Claude Code | `claude-code` | GitHub releases `anthropics/claude-code`<br>npm `@anthropic-ai/claude-code` | [v2.1.295](https://github.com/anthropics/claude-code/releases/tag/v2.1.295) | 2026-10-09 03:48 |
| Cursor | Cursor | `cursor` | Changelog RSS https://cursor.com/changelog/rss.xml | [Remote control for local agents](https://cursor.com/changelog/remote-control-local-agents) | 2026-10-06（仅日期） |
| Google | Gemini CLI | `gemini-cli` | GitHub releases `google-gemini/gemini-cli` （tag `^v\d+\.\d+\.\d+$`）<br>npm `@google/gemini-cli` | [v0.63.0](https://github.com/google-gemini/gemini-cli/releases/tag/v0.63.0) | 2026-10-07 04:38 |
| GitHub | GitHub Copilot CLI | `copilot-cli` | GitHub releases `github/copilot-cli`<br>npm `@github/copilot` | [v1.0.94](https://github.com/github/copilot-cli/releases/tag/v1.0.94) | 2026-10-09 04:28 |
| Microsoft | VS Code（含 Copilot Chat / Agent） | `vscode` | GitHub releases `microsoft/vscode` | [1.141.0](https://github.com/microsoft/vscode/releases/tag/1.141.0) | 2026-10-07 19:01 |
| OpenCode | OpenCode | `opencode` | GitHub releases `anomalyco/opencode`<br>npm `opencode-ai` | [v1.18.35](https://github.com/anomalyco/opencode/releases/tag/v1.18.35) | 2026-10-07 04:18 |
| Cline | Cline（VS Code 扩展） | `cline` | GitHub releases `cline/cline` （tag `^v\d+\.\d+\.\d+$`） | [v4.1.23](https://github.com/cline/cline/releases/tag/v4.1.23) | 2026-10-07 15:28 |
| Cline | Cline CLI | `cline-cli` | GitHub releases `cline/cline` （tag `^cli-v\d+\.\d+\.\d+$`）<br>npm `cline` | [cli-v3.0.70](https://github.com/cline/cline/releases/tag/cli-v3.0.70) | 2026-10-08 13:55 |

### 中国侧

| 厂商 | 产品 | id | 版本源（按顺序） | 当前最新 | 发布时间（北京时间） |
|---|---|---|---|---|---|
| 小米 | MiMo Code | `mimo-code` | GitHub releases `XiaomiMiMo/MiMo-Code`<br>npm `@mimo-ai/cli` | [v0.1.15](https://github.com/XiaomiMiMo/MiMo-Code/releases/tag/v0.1.15) | 2026-09-22 22:58 |
| 字节跳动 | TRAE / TraeCode（国际版 IDE） | `trae` | 官方 Changelog 页 https://docs.trae.ai/ide/changelog | [3.5.89 ~ 3.5.91](https://docs.trae.ai/ide/changelog) | 2026-08-19（仅日期） |
| 阿里 | Qwen Code | `qwen-code` | npm `@qwen-code/qwen-code`<br>GitHub releases `QwenLM/qwen-code` （tag `^v\d+\.\d+\.\d+$`） | [0.25.0](https://www.npmjs.com/package/@qwen-code/qwen-code/v/0.25.0) | 2026-10-05 17:55 |
| 阿里 | Qoder CLI（qodercli） | `qoder-cli` | 官方版本记录 https://docs.qoder.com/release-notes/qoder-cli<br>npm `@qoder-ai/qodercli` | [1.1.66](https://docs.qoder.com/release-notes/qoder-cli) | 2026-10-08（仅日期） |
| 阿里 | Qoder IDE | `qoder-ide` | 官方版本记录 https://docs.qoder.com/release-notes/desktop | [1.32.0](https://docs.qoder.com/release-notes/desktop) | 2026-09-23（仅日期） |
| 腾讯 | CodeBuddy Code（CLI） | `codebuddy-code` | npm `@tencent-ai/codebuddy-code` | [2.163.0](https://www.npmjs.com/package/@tencent-ai/codebuddy-code/v/2.163.0) | 2026-10-09 02:37 |
| 百度 | 文心快码 Comate CLI（comatecli） | `comate-cli` | npm `@comate/comatecli` | [2.0.0](https://www.npmjs.com/package/@comate/comatecli/v/2.0.0) | 2026-09-16 14:55 |
| 智谱 | ZCode | `zcode` | GitHub releases `zai-org/ZCode` | [v3.14.3](https://github.com/zai-org/ZCode/releases/tag/v3.14.3) | 2026-09-24 18:54 |
| 月之暗面 | Kimi Code CLI | `kimi-code` | GitHub releases `MoonshotAI/kimi-code` （tag `^@moonshot-ai/kimi-code@\d+\.\d+\.\d+$`）<br>npm `@moonshot-ai/kimi-code` | [@moonshot-ai/kimi-code@2.1.1](https://github.com/MoonshotAI/kimi-code/releases/tag/%40moonshot-ai/kimi-code%402.1.1) | 2026-09-24 15:24 |
| 月之暗面 | kimi-cli（Python 旧版） | `kimi-cli-legacy` | GitHub releases `MoonshotAI/kimi-cli` | [1.52.0](https://github.com/MoonshotAI/kimi-cli/releases/tag/1.52.0) | 2026-09-22 18:02 |
| DeepSeek | DeepSeek Harness（dsh） | `deepseek-harness` | GitHub releases `deepseek-ai/deepseek-harness`<br>npm `@deepseek-ai/dsh` | [dsh-v0.2.1-alpha.1](https://github.com/deepseek-ai/deepseek-harness/releases/tag/dsh-v0.2.1-alpha.1) | 2026-10-03 14:42 |
| MiniMax | MiniMax Code CLI（mcode） | `minimax-code` | npm `@minimax-ai/code` | [0.6.5](https://www.npmjs.com/package/@minimax-ai/code/v/0.6.5) | 2026-10-08 21:55 |
| 阶跃星辰 | Step Code | `step-code` | 官方版本记录 https://platform.stepfun.com/docs/zh/step-code/release-notes | [0.1.2](https://platform.stepfun.com/docs/zh/step-code/release-notes) | 2026-09-30（仅日期） |

### 各源的说明和已知限制

- **Codex CLI**：同一个仓库里还有 `rust-v*-alpha.*` 预发布，靠 tag 正则过滤掉。npm `@openai/codex` 的版本和它同步。
- **Gemini CLI**：每天都有 nightly 和 preview（都标了 prerelease），默认不算。npm 的 `latest` 是正式版。
- **Cline**：一个仓库发好几条产品线。`v*` 是 VS Code 扩展，`cli-v*` 是 CLI，另外还有 `desktop-v*`、`sdk/*`，目前不跟。
- **Cursor**：没有 GitHub 也没有 npm，用官方 changelog 的 RSS。条目标题是功能名，不是版本号，`pubDate` 也只到日期。
- **OpenCode**：仓库从 `sst/opencode` 迁到了 `anomalyco/opencode`，旧地址会自动跳转。
- **MiMo Code**：GitHub release 和 npm `@mimo-ai/cli`（维护者 mimo@xiaomi.com，仓库指向 XiaomiMiMo/MiMo-Code）两边都能用。
- **TRAE**：只能解析国际版文档页 https://docs.trae.ai/ide/changelog，这个页面是前端渲染的，正文嵌在 HTML 里。页面目前最新只写到 **2026-08-19 的 TraeCode v3.5.89–3.5.91**，看起来更新滞后。国内版 docs.trae.cn 不允许脚本抓取（返回 400 UA Forbidden）。开源的 `bytedance/trae-agent` 没有 release，最后一次 push 是 2026-02，已经从清单里移除。TRAE 的动态主要靠 X 上的 @Trae_ai 来补。
- **Qwen Code**：GitHub 上最近只有 nightly / preview 和 SDK 的 release，正式版只在 npm 上能看到，所以 npm 放在第一位。
- **Qoder**：CLI 和 IDE 的官方 release notes 是 Mintlify 格式的 markdown（docs.qoder.com/release-notes/*.md），带版本号和日期。npm `@qoder-ai/qodercli`（维护者 dev@qoder.com）能拿到精确时间，比如 1.1.66 是 10-08 20:20。
- **CodeBuddy Code**：npm `@tencent-ai/codebuddy-code`，主页是 cnb.cool/codebuddy/codebuddy-code。几乎每天都发版，入选简报时建议只收 minor 及以上的版本，或者有明确新功能的版本。
- **文心快码 Comate CLI**：npm `@comate/comatecli`（命令 `comatecli`，描述“Comate of Terminal”）。同一个 `@comate` scope 下的 `@comate/zulu` 仓库指向 icode.baidu.com，据此判断是百度官方。另有报道说 Comate 9 月已经并入“百度搭子（DuMate）”。
- **ZCode**：`zai-org/ZCode` 是 9 月 21 日开源后的官方仓库，主页 zcode.z.ai。
- **Kimi Code**：新版是 TypeScript 写的 `MoonshotAI/kimi-code`，tag 形如 `@moonshot-ai/kimi-code@2.1.1`。旧的 Python 版 `MoonshotAI/kimi-cli` 也还留在清单里。
- **DeepSeek Harness**：官方仓库是 `deepseek-ai/deepseek-harness`，tag 是 `dsh-v*`，全部是预发布。npm 主包 `@deepseek-ai/dsh`，维护者里有 tianyi@deepseek.com。
- **MiniMax Code（mcode）**：npm `@minimax-ai/code`，命令 `mcode`，和官方文档 agent.minimax.io/docs/cli 一致，维护者里有 @minimax.io 邮箱。官方 changelog（https://agent.minimax.io/docs/changelog）CLI 那一栏只更新到 0.5.10，比 npm 慢。
- **Step Code**：GitHub `stepfun-ai/Step-Code` 没有 release 和 tag，安装脚本从自家 CDN 下载。版本号以官方“版本记录”页为准，页面只到日期。注意 npm 上的 `@stepfun-ai/cli` 是第三方个人发布的，不是官方包。

---

## B. 人物 / 官方 X 账号

核实方法：用 X API 查账号资料（简介、创建时间、粉丝数），再结合简介里的自述、发帖内容和公开报道交叉确认。**下面只列已经核实的账号**，没把握的单独放在后面。

### 美国侧

| 公司 / 产品 | 姓名 | 身份 | X 账号 | 核实依据 |
|---|---|---|---|---|
| OpenAI / Codex | Tibo（Thibault Sottiaux） | Codex 负责人 | [@thsottiaux](https://x.com/thsottiaux) | 简介“Codex & ChatGPT @OpenAI”，82 万粉，持续发 Codex 额度和上线消息 |
| OpenAI / Codex | Alexander Embiricos | Codex 产品 | [@embirico](https://x.com/embirico) | 简介“Codex @OpenAI” |
| OpenAI | Romain Huet | 开发者体验负责人 | [@romainhuet](https://x.com/romainhuet) | 简介“Head of Developer Experience @OpenAI, working on Codex and our API” |
| Anthropic / Claude Code | Boris Cherny | Claude Code 创建者 | [@bcherny](https://x.com/bcherny) | 简介“Claude @anthropicai”，60 万粉，公开报道称他是 Claude Code 创建者 |
| Anthropic / Claude Code | Cat Wu | Claude Code + Cowork 产品 | [@_catwu](https://x.com/_catwu) | 简介“claude code + cowork @anthropicai” |
| Cursor（8 月起归 SpaceX） | Michael Truell | CEO / 联合创始人 | [@mntruell](https://x.com/mntruell) | 简介“Building @SpaceXAI”，个人站 mntruell.com。Cursor 8 月 14 日并入 SpaceX（Cursor 官方博客、TechCrunch） |
| Cursor | Aman Sanger | 联合创始人 | [@amanrsanger](https://x.com/amanrsanger) | 简介“Building @SpaceXAI \| @cursor_ai founder” |
| Google / Gemini CLI | N. Taylor Mullen | Gemini CLI 创建者 | [@ntaylormullen](https://x.com/ntaylormullen) | 简介“Google DeepMind \| Antigravity SDK \| Creator of Gemini CLI” |
| Google / Antigravity | Varun Mohan | 在 Google DeepMind 做 Antigravity | [@_mohansolo](https://x.com/_mohansolo) | 简介“building @antigravity @GoogleDeepMind” |
| Google / Gemini API | Logan Kilpatrick | Gemini API / AI Studio | [@OfficialLoganK](https://x.com/OfficialLoganK) | 简介“working on Gemini, @GoogleAIStudio, the Gemini API” |
| GitHub / Copilot | Mario Rodriguez | GitHub CPO，负责 Copilot | [@mariorod1](https://x.com/mariorod1) | 简介“CPO @github, Building Copilot” |
| GitHub / VS Code | Pierce Boggan | GitHub 开发者工具 | [@pierceboggan](https://x.com/pierceboggan) | 简介“developer tools @github” |
| GitHub | Kyle Daigle | GitHub COO | [@kdaigle](https://x.com/kdaigle) | 简介“COO at GitHub” |
| OpenCode | dax | OpenCode 核心开发 | [@thdxr](https://x.com/thdxr) | 简介“building @opencode at @anomalyco” |

美国侧官方号：[@OpenAIDevs](https://x.com/OpenAIDevs)、[@ClaudeDevs](https://x.com/ClaudeDevs)、[@claudeai](https://x.com/claudeai)、[@cursor_ai](https://x.com/cursor_ai)、[@geminicli](https://x.com/geminicli)（注意 @Gemini_CLI 已被封禁，不是官方号）、[@antigravity](https://x.com/antigravity)、[@github](https://x.com/github)、[@code](https://x.com/code)、[@cline](https://x.com/cline)、[@opencode](https://x.com/opencode)。

### 中国侧

| 公司 / 产品 | 姓名 | 身份 | X 账号 | 核实依据 |
|---|---|---|---|---|
| 小米 MiMo | 罗福莉（Fuli Luo） | Xiaomi MiMo 团队负责人 | [@_LuoFuli](https://x.com/_LuoFuli) | 简介“Now building @XiaomiMiMo. Previously @deepseek_ai”，8.7 万粉。华尔街见闻等报道她在 X 上官宣出任 MiMo 负责人。她发帖少（总共 27 条），但每条分量都重 |
| 智谱 Z.ai | Zixuan Li | Z.ai 负责人（海外） | [@ZixuanLi_](https://x.com/ZixuanLi_) | 简介“Lead z.ai @Zai_org”，邮箱 zixuan.li@z.ai。ZCode 风波期间用户集中 @ 他，Z.ai 员工也在同一串里回复 ZCode 问题 |
| 智谱 Z.ai | 唐杰 | 创始人 / 首席科学家 | [@jietang](https://x.com/jietang) | 简介“Professor @ Tsinghua, Founder of Z.ai”，主页链到清华 KEG 实验室。ZCode 整改期间是他公开出面 |
| 月之暗面 / Kimi Code | Young | Kimi Code 团队负责人 | [@Young_AGI](https://x.com/Young_AGI) | 简介为空，靠发帖内容核实：2026-05-26 以团队身份发长文《关于Kimi Code升级风控策略及影响，想和各位开发者聊聊》，还回复说“我们定制风控策略时……已经恢复”。媒体报道称其为“Kimi Code负责人” |
| 月之暗面 / Kimi | Crystal | Kimi 团队成员 | [@crystalsssup](https://x.com/crystalsssup) | 简介“@ Kimi”，2.7 万粉。具体岗位没有核实，可信度中等 |

中国侧官方号：

- 小米：[@XiaomiMiMo](https://x.com/XiaomiMiMo)、[@XiaomiMiMoDevs](https://x.com/XiaomiMiMoDevs)（@XiaomiMiMo 简介里写了这是 API 和产品更新号）
- 智谱：[@zcode_ai](https://x.com/zcode_ai)（ZCode 官方，已发 443 条）、[@Zai_org](https://x.com/Zai_org)
- 月之暗面：[@KimiDevs](https://x.com/KimiDevs)（“for developers building with Kimi Code and the Kimi API”）、[@Kimi_Moonshot](https://x.com/Kimi_Moonshot)
- 阿里：[@Alibaba_Qwen](https://x.com/Alibaba_Qwen)、[@qoder_ai_ide](https://x.com/qoder_ai_ide)（Qoder 官方，主页 qoder.com）
- DeepSeek：[@deepseek_ai](https://x.com/deepseek_ai)
- MiniMax：[@MiniMax_AI](https://x.com/MiniMax_AI)、[@MiniMaxAgent](https://x.com/MiniMaxAgent)（@MiniMax_AI 简介写明“Code: @MiniMaxAgent”）
- 字节跳动：[@Trae_ai](https://x.com/Trae_ai)
- 阶跃星辰：[@StepFun_ai](https://x.com/StepFun_ai)
- 腾讯：[@TencentAI_News](https://x.com/TencentAI_News)（腾讯 AI 官方新闻号，CodeBuddy 相关的反馈也会 @ 它）

### 没有收录的人和原因

| 对象 | 情况 |
|---|---|
| ZCode 的专职产品负责人 | 公开报道和 X 上都没找到明确署名的负责人。目前能对上 ZCode 的只有 @zcode_ai、@ZixuanLi_、@jietang。**不猜测** |
| 千问 / Qwen Code 现任负责人 | 林俊旸 @JustinLin610 已于 2026-03 离开千问，简介现为“building p7k @pragmatik_labs”。惠彬原 @huybery 简介写“Formerly @Alibaba_Qwen”。两人都不再代表千问。现任通义实验室负责人周靖人、后训练负责人周浩都没找到 X 账号。目前只跟官方号 |
| DeepSeek 个人 | 没有核实到在职成员的公开 X 账号。npm 维护者 tianyi@deepseek.com 对应的几个常见用户名在 X 上都不存在。只跟 @deepseek_ai |
| MiniMax 个人 | Skyler Miao @SkylerMiao7 简介写“ex-Head of Engineering @MiniMax_AI”，已经离职，不收。没有核实到其他在职负责人。只跟官方号 |
| TRAE 负责人 | 石扬据脉脉报道已离职。SOLO 产品经理王可只在 TRAE 中文社区和直播中露面，没找到 X 账号。只跟 @Trae_ai |
| 阶跃星辰个人 | 没找到 Step Code 负责人的 X 账号。只跟 @StepFun_ai |
| 腾讯 CodeBuddy / 百度 Comate | 两款产品都没有独立的官方 X 号：@CodebuddyAI 是加拿大的同名产品，和腾讯无关；百度 Comate 主要在国内渠道（公众号 / 社区）发布。腾讯侧只跟 @TencentAI_News |
| Eric Zakariasson @ericzakariasson | 简介写“prev @cursor_ai”，已经离开 Cursor，不收 |
| stdrc @istdrc | 简介写“former author of Kimi CLI”，已经离开，不收 |
| @Gemini_CLI | 已被封禁，不是官方号。官方号是 @geminicli |

### 搜索建议

- X 单条查询长度有限，按 `tools/x_people.json` 里的 region 和 tier 分批拼 `from:a OR from:b ...`，每批 10 到 15 个 handle。可以加上 `-is:retweet`，回复用 `-is:reply` 过滤，也可以保留。
- 人物动态只收和产品、模型、harness 有关的实质内容，比如发布、路线图、额度和价格、事故回应。闲聊不收。
- 已经在“版本更新”或其他分区报过的事件，人物动态里只作为 `children` 补充，不另起条目。

---

## C. 分区约定（新增）

| id | 标题 | icon | 内容 |
|---|---|---|---|
| `releases` | 版本更新 | 📦 | `check_versions.py` 筛出的新版本。每条的 `vendor` 填厂商，`title` 形如“Codex CLI 0.162.0”，`summary` 写 1 到 2 个要点，`links` 放 release 页 |
| `people` | 人物动态 | 🗣️ | 名单里的人发的帖子。`vendor` 填公司，`title` 可写“Tibo：……”，`links` 放原帖 |

分区格式和其他分区相同，见 `docs/schema.md`。
