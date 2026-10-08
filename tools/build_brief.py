#!/usr/bin/env python3
"""AI 日报 HTML 生成器：读取简报 JSON，输出单文件自包含 HTML。

用法（在仓库根目录）:
    python3 tools/build_brief.py data/2026-10-08.json -o out/2026-10-08.html
    python3 tools/build_brief.py data/2026-10-08.json      # 输出到 JSON 同目录同名 .html
数据结构见 docs/schema.md。仅使用标准库。
每条新闻的 id 字段（如 "2026-10-08-001"）会渲染成卡片锚点 #i-<id> 和 data-id 属性。
"""
import argparse
import html
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

BJT = timezone(timedelta(hours=8))
REGION_LABEL = {"cn": "中", "us": "美", "intl": "国际"}
WEEKDAYS = "一二三四五六日"


def esc(s):
    return html.escape(str(s or ""), quote=True)


def rich(s):
    """转义后支持 **粗体**。"""
    out = esc(s)
    return re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", out)


def safe_url(u):
    u = str(u or "").strip()
    if not re.match(r"^https?://", u, re.I):
        raise ValueError(f"非法链接（仅允许 http/https）: {u!r}")
    return esc(u)


def count_items(items):
    n = 0
    for it in items or []:
        n += (0 if it.get("group") else 1) + count_items(it.get("children"))
    return n


def render_item(it, depth=0):
    region = it.get("region", "intl")
    if region not in REGION_LABEL:
        region = "intl"
    vendor = esc(it.get("vendor", ""))
    parts = []
    head = [f'<span class="tag tag-{region}" title="{REGION_LABEL[region]}">'
            f'<i>{REGION_LABEL[region]}</i>{vendor}</span>']
    if it.get("update"):
        head.append('<span class="badge-update">更新</span>')
    if it.get("time"):
        head.append(f'<span class="time"><svg viewBox="0 0 16 16" aria-hidden="true">'
                    f'<circle cx="8" cy="8" r="6.5"/><path d="M8 4.5V8l2.5 1.5"/></svg>'
                    f'{esc(it["time"])}</span>')
    parts.append(f'<div class="item-head">{"".join(head)}</div>')
    parts.append(f'<h3 class="item-title">{rich(it.get("title", ""))}</h3>')
    if it.get("update") and it.get("update_note"):
        parts.append(f'<p class="update-note">↻ {rich(it["update_note"])}</p>')
    if it.get("summary"):
        parts.append(f'<p class="item-summary">{rich(it["summary"])}</p>')
    links = it.get("links") or []
    if links:
        btns = "".join(
            f'<a class="btn" href="{safe_url(l.get("url"))}" target="_blank" rel="noopener noreferrer">'
            f'{esc(l.get("label") or "原帖")}<span aria-hidden="true"> ↗</span></a>'
            for l in links)
        parts.append(f'<div class="links">{btns}</div>')
    kids = it.get("children") or []
    if kids:
        parts.append('<div class="children">' +
                     "".join(render_item(k, depth + 1) for k in kids) + "</div>")
    cls = "card" if depth == 0 else "sub"
    if it.get("update"):
        cls += " is-update"
    attrs = ""
    if it.get("id"):
        iid = esc(it["id"])
        attrs = f' id="i-{iid}" data-id="{iid}"'
    return f'<article class="{cls}"{attrs}>{"".join(parts)}</article>'


def build(data):
    title = data.get("title") or "AI 日报"
    date = data["date"]
    try:
        d = datetime.strptime(date, "%Y-%m-%d")
        date_cn = f"{d.year} 年 {d.month} 月 {d.day} 日 · 星期{WEEKDAYS[d.weekday()]}"
    except ValueError:
        date_cn = date
    w = data.get("window") or {}
    window = (f'覆盖 {esc(w.get("start"))} 至 {esc(w.get("end"))}（{esc(w.get("tz") or "北京时间")}）'
              if w else "")
    gen = data.get("generated_at") or datetime.now(BJT).strftime("%Y-%m-%d %H:%M")

    xiaomi = data.get("xiaomi") or {}
    sections = data.get("sections") or []
    total = count_items(xiaomi.get("items")) + sum(count_items(s.get("items")) for s in sections)

    nav = [("highlights", "今日要点"), ("xiaomi", "小米")]
    nav += [(s["id"], s["title"]) for s in sections]
    nav.append(("footer", "说明"))
    nav_html = "".join(f'<a href="#{esc(i)}">{esc(t)}</a>' for i, t in nav)

    body = []
    body.append(f'''<section id="highlights" class="highlights">
  <div class="hl-label">⚡ 今日要点</div>
  <p>{rich(data.get("highlights", ""))}</p>
</section>''')

    xi_items = xiaomi.get("items") or []
    if xi_items:
        xi_body = '<div class="cards">' + "".join(render_item(i) for i in xi_items) + "</div>"
    else:
        xi_body = f'<div class="empty">{esc(xiaomi.get("empty_text") or "小米今天无新动态")}</div>'
    body.append(f'''<section id="xiaomi" class="section section-xiaomi">
  <h2><span class="sec-icon mi">mi</span>小米专栏<span class="count">{count_items(xi_items)}</span></h2>
  {xi_body}
</section>''')

    for s in sections:
        items = s.get("items") or []
        inner = ('<div class="cards">' + "".join(render_item(i) for i in items) + "</div>") if items \
            else f'<div class="empty">{esc(s.get("empty_text") or "本期无相关动态")}</div>'
        body.append(f'''<section id="{esc(s["id"])}" class="section">
  <h2><span class="sec-icon">{esc(s.get("icon", ""))}</span>{esc(s["title"])}<span class="count">{count_items(items)}</span></h2>
  {inner}
</section>''')

    notes = "".join(f"<li>{rich(n)}</li>" for n in data.get("notes") or [])
    notes_html = f'<ul class="notes">{notes}</ul>' if notes else ""
    footer = f'''<footer id="footer" class="footer">
  {notes_html}
  <p>{rich(data.get("source_note", ""))}</p>
  <p class="legend"><span class="tag tag-cn"><i>中</i>中国厂商</span><span class="tag tag-us"><i>美</i>美国厂商</span><span class="tag tag-intl"><i>国际</i>其他</span><span class="badge-update">更新</span><span>跨天后续进展</span></p>
  <p class="gen">生成时间：{esc(gen)}（北京时间）</p>
</footer>'''

    return TEMPLATE.format(
        page_title=esc(f"{title}｜{date}"), title=esc(title), date_cn=esc(date_cn),
        window=window, total=total, nav=nav_html, body="\n".join(body), footer=footer, css=CSS)


CSS = r"""
:root{--bg:#f4f5f8;--card:#fff;--ink:#1d2129;--muted:#6b7280;--line:#e6e8ee;--accent:#4f46e5;--accent-soft:#eef0ff;
--cn:#d92d20;--cn-bg:#fdecea;--us:#1d64d8;--us-bg:#e8f0fe;--intl:#5b6472;--intl-bg:#eef0f3;--upd:#c2410c;--upd-bg:#fff1e6;
--mi:#ff6900;--hl1:#4f46e5;--hl2:#7c3aed;--shadow:0 1px 2px rgba(16,24,40,.05),0 2px 8px rgba(16,24,40,.05)}
@media (prefers-color-scheme:dark){:root{--bg:#0f1115;--card:#181b22;--ink:#e6e8ee;--muted:#9aa3b2;--line:#2a2f3a;--accent:#8b8cff;
--accent-soft:#23264a;--cn:#ff7a6e;--cn-bg:#3a1d1b;--us:#7fb0ff;--us-bg:#1a2a45;--intl:#b3bcc9;--intl-bg:#262b35;--upd:#ffad6e;--upd-bg:#3a2617;
--mi:#ff8a33;--hl1:#3b36b0;--hl2:#5b2bb3;--shadow:none}}
*{box-sizing:border-box}html{scroll-behavior:smooth;-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.75 -apple-system,BlinkMacSystemFont,"PingFang SC","Hiragino Sans GB","Microsoft YaHei","Noto Sans CJK SC","Noto Sans SC","Source Han Sans SC",sans-serif;
letter-spacing:.01em}
a{color:var(--accent)}
.wrap{max-width:880px;margin:0 auto;padding:0 20px 40px}
.masthead{background:linear-gradient(135deg,var(--hl1),var(--hl2));color:#fff;padding:36px 0 30px}
.masthead .wrap{padding-bottom:0}
.kicker{font-size:13px;letter-spacing:.2em;opacity:.8;text-transform:uppercase}
.masthead h1{margin:4px 0 6px;font-size:40px;line-height:1.2;font-weight:800;letter-spacing:.04em}
.meta{display:flex;flex-wrap:wrap;gap:6px 14px;font-size:14px;opacity:.92}
.meta span{white-space:nowrap}
nav.toc{position:sticky;top:0;z-index:10;background:color-mix(in srgb,var(--bg) 88%,transparent);backdrop-filter:blur(8px);
-webkit-backdrop-filter:blur(8px);border-bottom:1px solid var(--line)}
nav.toc .wrap{display:flex;gap:6px;overflow-x:auto;padding:10px 20px;scrollbar-width:none}
nav.toc .wrap::-webkit-scrollbar{display:none}
nav.toc a{flex:none;text-decoration:none;color:var(--ink);font-size:14px;padding:4px 12px;border-radius:999px;background:var(--card);border:1px solid var(--line)}
nav.toc a:hover{border-color:var(--accent);color:var(--accent)}
section,article[id]{scroll-margin-top:60px}
.highlights{margin:24px 0 8px;background:var(--accent-soft);border:1px solid color-mix(in srgb,var(--accent) 25%,transparent);
border-left:5px solid var(--accent);border-radius:14px;padding:18px 22px}
.hl-label{font-weight:700;color:var(--accent);font-size:15px;margin-bottom:4px}
.highlights p{margin:0;font-size:16.5px}
.highlights strong{color:var(--accent)}
.section h2{display:flex;align-items:center;gap:10px;font-size:21px;margin:34px 0 14px;padding-bottom:8px;border-bottom:2px solid var(--line)}
.sec-icon{font-size:20px}
.sec-icon.mi{display:inline-grid;place-items:center;width:28px;height:28px;border-radius:8px;background:var(--mi);color:#fff;
font:700 14px/1 Arial,sans-serif}
.count{margin-left:auto;font-size:13px;font-weight:500;color:var(--muted);background:var(--card);border:1px solid var(--line);border-radius:999px;padding:0 10px}
.section-xiaomi h2{border-bottom-color:color-mix(in srgb,var(--mi) 45%,transparent)}
.empty{background:var(--card);border:1px dashed var(--line);border-radius:12px;padding:14px 18px;color:var(--muted);font-size:15px}
.cards{display:grid;gap:14px}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 20px;box-shadow:var(--shadow)}
.card.is-update{border-color:color-mix(in srgb,var(--upd) 45%,transparent)}
.item-head{display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin-bottom:4px}
.tag{display:inline-flex;align-items:center;gap:5px;font-size:12.5px;font-weight:600;line-height:1;padding:4px 9px 4px 4px;border-radius:999px}
.tag i{font-style:normal;font-size:11px;padding:3px 5px;border-radius:999px;color:#fff}
.tag-cn{color:var(--cn);background:var(--cn-bg)}.tag-cn i{background:var(--cn)}
.tag-us{color:var(--us);background:var(--us-bg)}.tag-us i{background:var(--us)}
.tag-intl{color:var(--intl);background:var(--intl-bg)}.tag-intl i{background:var(--intl)}
@media (prefers-color-scheme:dark){.tag i{color:#111}}
.badge-update{font-size:12px;font-weight:700;color:var(--upd);background:var(--upd-bg);border:1px solid color-mix(in srgb,var(--upd) 40%,transparent);
border-radius:6px;padding:1px 7px;line-height:1.5}
.time{display:inline-flex;align-items:center;gap:4px;margin-left:auto;font-size:13px;color:var(--muted);font-variant-numeric:tabular-nums}
.time svg{width:14px;height:14px;fill:none;stroke:currentColor;stroke-width:1.4;stroke-linecap:round}
.item-title{font-size:17.5px;line-height:1.5;margin:6px 0 4px;font-weight:700}
.item-summary{margin:0 0 4px;color:var(--ink);opacity:.88;font-size:15.5px}
.update-note{margin:0 0 4px;color:var(--upd);font-size:14.5px}
.links{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}
.btn{display:inline-flex;align-items:center;font-size:13px;font-weight:600;text-decoration:none;color:var(--accent);background:var(--accent-soft);
border:1px solid color-mix(in srgb,var(--accent) 22%,transparent);padding:3px 12px;border-radius:8px}
.btn:hover{background:var(--accent);color:#fff}
.children{margin-top:12px;display:grid;gap:10px;padding-left:14px;border-left:3px solid var(--line)}
.sub .item-title{font-size:15.5px;margin-top:2px}
.sub .item-summary{font-size:14.5px}
.sub .tag{font-size:11.5px}
.sub+.sub{padding-top:10px;border-top:1px dashed var(--line)}
.footer{margin-top:40px;padding-top:18px;border-top:1px solid var(--line);color:var(--muted);font-size:13.5px}
.footer p{margin:6px 0}
.notes{margin:0 0 10px;padding:12px 16px 12px 32px;background:var(--card);border:1px solid var(--line);border-radius:12px;color:var(--ink);font-size:14.5px}
.legend{display:flex;flex-wrap:wrap;align-items:center;gap:8px}
.gen{font-variant-numeric:tabular-nums}
@media (max-width:600px){body{font-size:15px}.wrap{padding:0 14px 32px}nav.toc .wrap{padding:8px 14px}
.masthead{padding:26px 0 22px}.masthead h1{font-size:30px}.meta{font-size:13px}
.highlights{padding:14px 16px;margin-top:18px}.highlights p{font-size:15.5px}
.card{padding:14px 15px}.item-title{font-size:16.5px}.section h2{font-size:19px;margin-top:28px}
.children{padding-left:10px}}
@media print{nav.toc{display:none}.card{break-inside:avoid}}
"""

TEMPLATE = """<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>{page_title}</title>
<style>{css}</style>
</head>
<body>
<header class="masthead"><div class="wrap">
  <div class="kicker">Daily AI Brief</div>
  <h1>{title}</h1>
  <div class="meta"><span>📅 {date_cn}</span><span>🕘 {window}</span><span>📰 共 {total} 条</span></div>
</div></header>
<nav class="toc" aria-label="目录"><div class="wrap">{nav}</div></nav>
<main class="wrap">
{body}
{footer}
</main>
</body>
</html>
"""


def main():
    ap = argparse.ArgumentParser(description="AI 日报 JSON → HTML")
    ap.add_argument("json", help="简报数据 JSON 文件")
    ap.add_argument("-o", "--output", help="输出 HTML 路径（默认与 JSON 同名 .html）")
    a = ap.parse_args()
    src = Path(a.json)
    data = json.loads(src.read_text(encoding="utf-8"))
    out = Path(a.output) if a.output else src.with_suffix(".html")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(build(data), encoding="utf-8")
    print(out)


if __name__ == "__main__":
    sys.exit(main())
