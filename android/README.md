# AI 日报 · Android 客户端

原生安卓 app，用来阅读本仓库每天发布的「AI 日报」。Kotlin + Jetpack Compose（Material 3），单 Activity，无服务器、无账号、**不依赖 FCM / 任何推送服务**。

| 首页（浅色） | 首页（深色） | 栏目列表 | 详情 | 设置与后台引导 |
|---|---|---|---|---|
| ![](screenshots/home_light.png) | ![](screenshots/home_dark.png) | ![](screenshots/home_sections_light.png) | ![](screenshots/detail_light.png) | ![](screenshots/settings_light.png) |

> 截图由 Robolectric 原生图形渲染真实的 Compose 界面生成（真实主题、MiSans 字体、`data/2026-10-08.json` 样例数据），见下文「截图」。

## 功能

- **首页**：日期条快速切换最近 30 期 + 「全部」往期面板；渐变主卡展示「今日要点」（`**粗体**` 高亮）、条数与已读数；吸顶栏目条（小米 / 模型 / 编码 Agent / 其他）随滚动高亮、点击定位；**小米专栏**固定在要点之后、橙色强调；条目以分组圆角卡片呈现，子条目以内嵌列表显示，`group` 容器显示为「专题」。
- **详情**：厂商标签（中国红 / 美国蓝 / 国际灰）+ 区域、北京时间、标题、摘要、「更新」徽标与更新说明、原帖按钮（Custom Tabs，没有支持的浏览器时退回系统浏览器）、相关进展、下一条、分享。
- **已读**：打开详情即标记已读（DataStore 持久化），列表中已读标题变灰；顶栏可一键全部已读。
- **离线**：`index.json` 与每期 JSON 原样缓存在 app 私有目录；断网时显示缓存并提示。单期若在索引中的 `published_at` 变化（修订重发）会自动重新拉取。
- **下拉刷新**；冷启动先显示缓存，再联网。
- **每日自动检查 + 本地通知**（见下）。
- **深色模式**：跟随系统 / 浅色 / 深色。
- 只认 `**粗体**`（非贪婪 `\*\*(.+?)\*\*`），**从不按 HTML 解析**；忽略未知 JSON 字段，未知 `region` 按 `intl` 处理。

## 数据获取

- 先请求 `https://raw.githubusercontent.com/WikG1018/ai-daily/main/<path>`，**8 秒内失败**（`callTimeout`）再请求 `https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/<path>`。
- 不看 `Content-Type`（Raw 返回 `text/plain`），直接按 JSON 解析。
- OkHttp 不配置 HTTP 缓存；请求 `index.json` 额外带 `CacheControl.FORCE_NETWORK` 与 `Cache-Control: no-cache`，绕开 jsDelivr 的 7 天 `max-age`。
- 镜像返回的索引如果比本地已有的旧（`latest` 更小），不会把本地降级。

## 每天早上自动提醒（无 FCM）

简报每天北京时间约 8:17 开始生成、8:30 前发布。app 用 WorkManager 两条腿走路：

1. **早间链**（一次性任务，自己排下一次）：8:35 首次检查；没发布就每 20 分钟重试到 12:00，之后每 2 小时，22:00 后停到第二天 8:35；拿到当天这期后直接排到明天 8:35。
2. **兜底周期任务**：每 3 小时一次，顺带把断掉的早间链接上。
3. **打开 app 时**也会检查。

检查逻辑：拉 `index.json`，若 `latest` 比本地记录的大（字符串比较），就预取该期（保证点开通知时离线也能看），并用 `issues[0].highlights` 作为正文发本地通知，点击直达当期（`aidaily://issue/YYYY-MM-DD`）。前台已看过的期数不会再通知；首次安装只建立基线、不打扰。

**小米 / 澎湃 OS**：系统默认限制后台，app 的「设置与提醒」页有分步引导和跳转按钮：自启动管理、省电策略「无限制」、系统电池优化白名单、锁定最近任务、通知横幅。首页也会提示未完成的设置。

> 厂商推送（小米推送 MiPush / 统一推送联盟 UPS）**v1 不集成**，可作为以后的可选模块：只需在 `work/` 旁新增一个推送入口，复用 `DailyChecker` 的去重与通知逻辑即可。

## 字体：MiSans

按《MiSans 字体知识产权许可协议》，可以免费商用、可以把字体用在 app 里分发，但**不得单独再分发字体文件**，且需在软件中注明使用了 MiSans。因此：

- 仓库里**不放任何字体文件**（`.gitignore` 已忽略 `*.otf/*.ttf`）。
- 构建时 Gradle 任务 `fetchMiSans` 从小米官网 `https://hyperos.mi.com/font-download/MiSans.zip` 下载（约 230 MB，缓存在 `~/.gradle/caches/ai-daily/misans/`，只下一次），取 Regular / Medium / Semibold 三个字重打进 APK 的 `assets/fonts/`，并附带官方许可协议 PDF。
- 下载失败时自动退回系统字体（`-Pmisans.required` 可让下载失败直接构建失败，发版时建议加上；`-Pmisans.skip` 跳过下载）。
- 「设置 → 关于」中注明了「本应用使用 MiSans 字体（© 小米科技）」。

## 构建

环境：JDK 17+（已用 JDK 21 验证）、Android SDK（platform 35、build-tools 35）。

```bash
cd android
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # 或设置 ANDROID_HOME
./gradlew :app:assembleDebug          # 调试包：app/build/outputs/apk/debug/app-debug.apk（包名 com.wikg.aidaily.debug）
./gradlew :app:testDebugUnitTest      # 单元测试 + Robolectric 端到端冒烟测试
```

### 签名发布包

签名密钥**不入库**。通过环境变量提供（二选一）：

```bash
# 方式 1：指向一个 properties 文件（storeFile / storePassword / keyAlias / keyPassword）
export AIDAILY_SIGNING_PROPERTIES=/home/box/secrets/ai-daily-signing.properties

# 方式 2：直接给环境变量（适合 CI）
export AIDAILY_KEYSTORE=/path/to/ai-daily-release.jks
export AIDAILY_KEYSTORE_PASSWORD=...
export AIDAILY_KEY_ALIAS=aidaily
export AIDAILY_KEY_PASSWORD=...

./gradlew :app:assembleRelease -Pmisans.required
# 产物：app/build/outputs/apk/release/app-release.apk（R8 混淆 + 资源压缩，v2/v3 签名）
```

没有提供签名信息时 `assembleRelease` 仍能成功，但产出未签名的 APK。

生成新密钥（只需一次，务必备份，丢了就无法覆盖升级）：

```bash
keytool -genkeypair -keystore ai-daily-release.jks -storetype PKCS12 -alias aidaily \
  -keyalg RSA -keysize 4096 -validity 36500
```

当前发布用证书 SHA-256：`77:A5:7D:2F:0F:07:7D:90:B4:0D:DB:82:1A:F4:50:84:E9:AE:59:4D:A4:08:6E:12:25:DC:91:84:8C:B5:F0:A2`

## 安装

从 [Releases](https://github.com/WikG1018/ai-daily/releases) 下载 `ai-daily-*.apk`，在手机上打开安装（小米手机需允许「安装未知应用」）。装好后：

1. 允许通知；
2. 进入「设置与提醒」，按小米后台设置的几步操作（自启动、省电策略无限制）。

## 截图

```bash
./gradlew :app:testDebugUnitTest -Pscreenshots --tests '*ScreenshotTest*'
```

用 Roborazzi + Robolectric（`GraphicsMode.NATIVE`）把真实的 Compose 界面渲染成 PNG，写到 `android/screenshots/`。

## 目录结构

```
app/src/main/java/com/wikg/aidaily/
├── AiDailyApp.kt          # Application + 手写依赖容器，启动时排定后台任务
├── MainActivity.kt        # 单 Activity、深链（aidaily://issue/… / aidaily://item/…）
├── data/
│   ├── model/             # index.json / 单期 JSON 数据模型（kotlinx.serialization，忽略未知字段）
│   ├── remote/DailyApi.kt # OkHttp：Raw → jsDelivr 回退、绕开缓存
│   ├── local/             # 离线缓存（文件）+ DataStore（已读、最新期、设置）
│   └── DailyRepository.kt
├── work/                  # WorkManager 调度、检查逻辑、本地通知
├── ui/                    # Compose：theme / components / home / detail / settings / 导航
└── util/                  # 粗体解析、北京时间格式化、Custom Tabs、小米后台设置跳转
```

包名 `com.wikg.aidaily`，minSdk 26，target/compile SDK 35。
