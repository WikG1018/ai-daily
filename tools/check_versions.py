#!/usr/bin/env python3
"""查询各家 harness（编码 / Agent 运行框架）在指定时间窗内发布的新版本，输出 JSON。

用法（在仓库根目录）:
    python3 tools/check_versions.py                      # 过去 24 小时
    python3 tools/check_versions.py --hours 48           # 过去 48 小时
    python3 tools/check_versions.py --since 2026-10-08T23:45+08:00 --until 2026-10-09T23:45+08:00
    python3 tools/check_versions.py --latest             # 每个产品的当前最新版本（不看时间窗）
    python3 tools/check_versions.py --only codex-cli,claude-code -o /tmp/v.json

清单在 tools/harness_sources.json（说明见 docs/watchlist.md）。每个产品的 sources 依次尝试，
第一个成功的为准。默认只算正式版，源里写 "include_prerelease": true 或加 --prerelease 才算预发布版。
仅用标准库；GitHub 优先走 `gh api`（已登录则不受匿名 60 次/小时限制），没有 gh 时直接请求 API，
可设 GITHUB_TOKEN。时间一律输出北京时间。
"""
import argparse
import html
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

BJT = timezone(timedelta(hours=8))
UA = "ai-daily-version-check/1.0 (+https://github.com/WikG1018/ai-daily)"
PRE_RE = re.compile(r"(-|alpha|beta|rc\.?\d|nightly|preview|canary|insiders)", re.I)
DEFAULT_CONFIG = Path(__file__).with_name("harness_sources.json")
NOTE_LINES = 6


# ---------- 通用 ----------

def http_get(url, accept=None, timeout=20):
    req = urllib.request.Request(url, headers={"User-Agent": UA, **({"Accept": accept} if accept else {})})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", "replace")


def bjt(dt):
    return dt.astimezone(BJT).strftime("%Y-%m-%d %H:%M")


def parse_iso(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


VER_RE = re.compile(r"(\d+)\.(\d+)\.(\d+)(\S*)")


def is_pre(version):
    """tag 前缀（rust-v、cli-v、@scope/pkg@）不算；只看 X.Y.Z 之后有没有 -alpha/-rc 等后缀。"""
    m = VER_RE.search(version)
    tail = m.group(4) if m else version
    return bool(PRE_RE.search(tail))


def ver_key(r):
    """--latest 用：能解析出 X.Y.Z 的按版本号取最大（防止补发旧版本的 release 排到前面），否则按时间。"""
    m = VER_RE.search(r["version"])
    nums = tuple(int(x) for x in m.groups()[:3]) if m else (-1,)
    return (nums, not r["prerelease"], r["published"])


def note_head(text, n=NOTE_LINES):
    """release notes 前 n 行非空文本：去掉 HTML 标签、图片、多余空白。"""
    if not text:
        return []
    text = re.sub(r"<[^>]+>", "", text)
    out = []
    for line in text.splitlines():
        line = html.unescape(line).strip()
        if not line or line.startswith("![") or set(line) <= set("-=*_|: "):
            continue
        out.append(line[:300])
        if len(out) >= n:
            break
    return out


# ---------- 各类源 ----------

def gh_json(path):
    if shutil.which("gh"):
        p = subprocess.run(["gh", "api", path], capture_output=True, text=True, timeout=60)
        if p.returncode == 0:
            return json.loads(p.stdout)
        if "Not Found" in p.stderr:
            raise RuntimeError(f"GitHub 404: {path}")
    headers = {"User-Agent": UA, "Accept": "application/vnd.github+json"}
    if os.environ.get("GITHUB_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
    req = urllib.request.Request("https://api.github.com/" + path.lstrip("/"), headers=headers)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read())


def src_github(s, pre):
    rels = gh_json(f"repos/{s['repo']}/releases?per_page={s.get('per_page', 50)}")
    tag_re = re.compile(s["tag_regex"]) if s.get("tag_regex") else None
    out = []
    for r in rels:
        if r.get("draft") or not r.get("published_at"):
            continue
        tag = r["tag_name"]
        if tag_re and not tag_re.search(tag):
            continue
        prerelease = bool(r.get("prerelease")) or is_pre(tag)
        if prerelease and not pre:
            continue
        out.append({"version": tag, "published": parse_iso(r["published_at"]), "prerelease": prerelease,
                    "url": r["html_url"], "notes": note_head(r.get("body") or r.get("name"))})
    return out


def src_npm(s, pre):
    pkg = s["package"]
    d = json.loads(http_get("https://registry.npmjs.org/" + urllib.parse.quote(pkg, safe="@"),
                            accept="application/json"))
    out = []
    for v, t in (d.get("time") or {}).items():
        if v in ("created", "modified") or v not in (d.get("versions") or {}):
            continue
        prerelease = is_pre(v)
        if prerelease and not pre:
            continue
        out.append({"version": v, "published": parse_iso(t), "prerelease": prerelease,
                    "url": f"https://www.npmjs.com/package/{pkg}/v/{v}",
                    "notes": [], "changelog": s.get("changelog")})
    return out


def src_rss(s, pre):
    root = ET.fromstring(http_get(s["url"]))
    out = []
    for it in root.iter("item"):
        dt = parsedate_to_datetime(it.findtext("pubDate"))
        desc = it.findtext("description") or ""
        out.append({"version": (it.findtext("title") or "").strip(), "published": dt, "prerelease": False,
                    "url": it.findtext("link"), "notes": note_head(desc),
                    "date_only": bool(s.get("date_only"))})
    return out


def _entries_from_text(text, s, url):
    rx = re.compile(s["entry_regex"])
    ms = list(rx.finditer(text))
    out = []
    for i, m in enumerate(ms):
        d = datetime.strptime(m.group("date"), s["date_format"])
        # 只有日期：按北京时间当天 00:00 计（同 date_only 标记，时间窗判断按日期覆盖）
        dt = d.replace(tzinfo=BJT)
        body = text[m.end(): ms[i + 1].start() if i + 1 < len(ms) else m.end() + 3000]
        out.append({"version": m.group("version"), "published": dt, "prerelease": False,
                    "url": url, "notes": note_head(body), "date_only": True})
    return out


def src_md_changelog(s, pre):
    return _entries_from_text(http_get(s["url"]), s, s.get("html_url") or s["url"])


def src_trae_ssr(s, pre):
    """TRAE 文档站是前端渲染，正文以富文本 delta（"insert":"..."）嵌在 HTML 里。"""
    page = http_get(s["url"])
    parts = re.findall(r'"insert":"((?:[^"\\]|\\.)*)"', page)
    text = "".join(json.loads('"' + p + '"') for p in parts)
    return _entries_from_text(text, s, s["url"])


SOURCES = {"github": src_github, "npm": src_npm, "rss": src_rss,
           "md_changelog": src_md_changelog, "trae_ssr": src_trae_ssr}


def source_label(s):
    return {"github": lambda: "github:" + s["repo"], "npm": lambda: "npm:" + s["package"]}.get(
        s["type"], lambda: s["type"] + ":" + s["url"])()


def fetch_product(p, pre_flag):
    errors = []
    for s in p["sources"]:
        try:
            pre = pre_flag or bool(s.get("include_prerelease"))
            rel = SOURCES[s["type"]](s, pre)
            rel.sort(key=lambda r: r["published"], reverse=True)
            if not rel:
                raise LookupError("没有符合条件的版本")
            return source_label(s), rel, errors
        except Exception as e:  # 换下一个源
            errors.append(f"{source_label(s)}: {type(e).__name__}: {e}"[:300])
    return None, [], errors


def in_window(r, since, until):
    if r.get("date_only"):  # 只有日期的源：日期（北京时间）与时间窗有交集即算
        day0 = r["published"].astimezone(BJT).replace(hour=0, minute=0, second=0, microsecond=0)
        return day0 < until and day0 + timedelta(days=1) > since
    return since <= r["published"] < until


def to_json(p, src, r):
    o = {"product_id": p["id"], "product": p["name"], "vendor": p["vendor"], "region": p["region"],
         "version": r["version"], "published_bjt": (r["published"].astimezone(BJT).strftime("%Y-%m-%d")
                                                   + "（仅日期）") if r.get("date_only") else bjt(r["published"]),
         "prerelease": r["prerelease"], "url": r["url"], "source": src, "notes": r["notes"]}
    if r.get("changelog"):
        o["changelog"] = r["changelog"]
    return o


def main():
    ap = argparse.ArgumentParser(description="harness 新版本检查（JSON 输出）")
    ap.add_argument("--config", default=str(DEFAULT_CONFIG))
    ap.add_argument("--hours", type=float, default=24, help="时间窗：过去 N 小时（默认 24）")
    ap.add_argument("--since", help="时间窗起点 ISO 8601（无时区按北京时间）")
    ap.add_argument("--until", help="时间窗终点 ISO 8601（默认现在）")
    ap.add_argument("--latest", action="store_true", help="改为输出每个产品当前最新版本")
    ap.add_argument("--prerelease", action="store_true", help="所有源都包含预发布版")
    ap.add_argument("--only", help="只查这些产品 id，逗号分隔")
    ap.add_argument("-o", "--output", help="写入文件（默认 stdout）")
    a = ap.parse_args()

    def t(s):
        d = datetime.fromisoformat(s)
        return d if d.tzinfo else d.replace(tzinfo=BJT)

    until = t(a.until) if a.until else datetime.now(BJT)
    since = t(a.since) if a.since else until - timedelta(hours=a.hours)
    products = json.loads(Path(a.config).read_text(encoding="utf-8"))["products"]
    if a.only:
        keep = set(a.only.split(","))
        products = [p for p in products if p["id"] in keep]

    with ThreadPoolExecutor(8) as ex:
        fetched = list(ex.map(lambda p: (p, *fetch_product(p, a.prerelease)), products))

    out = {"generated_at": bjt(datetime.now(BJT)), "tz": "北京时间 (UTC+8)"}
    errors = []
    if a.latest:
        out["latest"] = []
    else:
        out["window"] = {"since": bjt(since), "until": bjt(until)}
        out["releases"] = []
    for p, src, rel, errs in fetched:
        errors += [{"product_id": p["id"], "error": e} for e in errs]
        if src is None:
            continue
        if a.latest:
            dated = [r for r in rel if not r.get("date_only")]
            best = max(dated, key=ver_key) if dated else rel[0]
            out["latest"].append(to_json(p, src, best))
        else:
            out["releases"] += [(r["published"], to_json(p, src, r)) for r in rel if in_window(r, since, until)]
    if not a.latest:
        out["releases"] = [o for _, o in sorted(out["releases"], key=lambda x: x[0], reverse=True)]
    out["errors"] = errors
    s = json.dumps(out, ensure_ascii=False, indent=2)
    if a.output:
        Path(a.output).write_text(s + "\n", encoding="utf-8")
    else:
        print(s)


if __name__ == "__main__":
    sys.exit(main())
