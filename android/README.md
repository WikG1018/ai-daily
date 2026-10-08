# Android 客户端（待开发）

这里将放置“AI 日报”安卓客户端源码，界面参考 IT 之家安卓版：首页按期展示新闻列表，点进去是详情页和原帖链接，每天早上自动拉取新一期并发通知。

- 数据来源：本仓库 `data/` 目录，格式见 [`docs/schema.md`](../docs/schema.md)
- 主地址：`https://raw.githubusercontent.com/WikG1018/ai-daily/main/data/index.json`
- 镜像：`https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/data/index.json`

不需要任何服务器、账号或推送服务（Firebase 等）：app 定时读取 `index.json`，发现 `latest` 比本地记录的新就发本地通知。
