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
import http.client
import ipaddress
import json
import os
import re
import shutil
import socket
import ssl
import subprocess
import sys
import time
import urllib.error
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

# Clash 等透明代理常用的 fake-ip 段；解析到这里时 TLS 容易间歇 EOF。
_FAKE_IP_NETS = (ipaddress.ip_network("198.18.0.0/15"),)
# DoH 走 1.1.1.1 字面地址，避免 DoH 域名本身也被 fake-ip。
_DOH_IP = "1.1.1.1"
_DOH_HOST = "cloudflare-dns.com"


def _is_fake_ip(ip):
    try:
        addr = ipaddress.ip_address(ip)
    except ValueError:
        return False
    return any(addr in net for net in _FAKE_IP_NETS)


def _system_ips(host):
    ips = []
    try:
        for *_, sockaddr in socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM):
            ip = sockaddr[0]
            if ip not in ips:
                ips.append(ip)
    except socket.gaierror:
        pass
    return ips


def _doh_ips(host, timeout=10):
    """Cloudflare DoH（连 1.1.1.1 + SNI），返回 A 记录。"""
    path = "/dns-query?name=" + urllib.parse.quote(host) + "&type=A"
    status, body, _ = _https_via_ip(_DOH_HOST, path, _DOH_IP, {
        "User-Agent": UA, "Accept": "application/dns-json",
    }, timeout=timeout)
    if status != 200:
        raise RuntimeError(f"DoH HTTP {status} for {host}")
    data = json.loads(body.decode("utf-8", "replace"))
    ips = [a["data"] for a in (data.get("Answer") or []) if a.get("type") == 1]
    if not ips:
        raise RuntimeError(f"DoH 无 A 记录: {host}")
    return ips


def _resolve_ips(host):
    """系统 DNS 若落在 fake-ip 段，改走 DoH；否则用系统解析。"""
    ips = _system_ips(host)
    if ips and not all(_is_fake_ip(ip) for ip in ips):
        return [ip for ip in ips if not _is_fake_ip(ip)] or ips
    return _doh_ips(host)


def _https_via_ip(host, path, ip, headers, timeout=20):
    """连真实 IP，SNI/证书校验仍用域名（不关闭 SSL 校验）。"""
    ctx = ssl.create_default_context()
    sock = socket.create_connection((ip, 443), timeout=timeout)
    try:
        ssock = ctx.wrap_socket(sock, server_hostname=host)
    except Exception:
        sock.close()
        raise
    conn = http.client.HTTPSConnection(host, timeout=timeout, context=ctx)
    conn.sock = ssock
    try:
        hdrs = {"Host": host, "Connection": "close", **headers}
        conn.request("GET", path, headers=hdrs)
        resp = conn.getresponse()
        return resp.status, resp.read(), resp.getheader("Location")
    finally:
        conn.close()


def _http_get_urllib(url, headers, timeout):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", "replace")


def _http_get_direct(url, headers, timeout):
    """绕过 fake-ip：DoH 解析后 IP+SNI 直连，跟随同域重定向。"""
    current = url
    for _ in range(5):
        u = urllib.parse.urlparse(current)
        if u.scheme != "https" or not u.hostname:
            raise RuntimeError(f"直连仅支持 https: {current}")
        path = u.path or "/"
        if u.query:
            path += "?" + u.query
        last_err = None
        for ip in _resolve_ips(u.hostname):
            try:
                status, body, loc = _https_via_ip(u.hostname, path, ip, headers, timeout=timeout)
            except Exception as e:
                last_err = e
                continue
            if status in (301, 302, 303, 307, 308) and loc:
                current = urllib.parse.urljoin(current, loc)
                break
            if status >= 400:
                raise RuntimeError(f"HTTP {status} for {current}")
            return body.decode("utf-8", "replace")
        else:
            raise last_err or RuntimeError(f"无法连接 {u.hostname}")
    raise RuntimeError(f"重定向过多: {url}")


def http_get(url, accept=None, timeout=20):
    """拉取 URL。cursor.com 等被 fake-ip 时 urllib 会间歇 SSL EOF，自动改 DoH+SNI 直连并重试。"""
    headers = {"User-Agent": UA, **({"Accept": accept} if accept else {})}
    host = urllib.parse.urlparse(url).hostname or ""
    prefer_direct = any(_is_fake_ip(ip) for ip in _system_ips(host)) if host else False
    last_err = None
    for attempt in range(4):
        try:
            if prefer_direct or attempt >= 2:
                return _http_get_direct(url, headers, timeout)
            return _http_get_urllib(url, headers, timeout)
        except (ssl.SSLError, socket.timeout, TimeoutError, ConnectionError,
                urllib.error.URLError, OSError, RuntimeError) as e:
            last_err = e
            # 一旦出现 SSL/连接错误，后续轮次优先直连
            prefer_direct = True
            time.sleep(0.4 * (attempt + 1))
    raise last_err


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


def src_html_changelog(s, pre):
    """解析 Cursor 等 changelog HTML 列表页（RSS 不可用时的备用源）。

    匹配 time[datetime] + 邻近的 /changelog/<slug> 链接与标题。
    """
    page = http_get(s["url"])
    base = s.get("base_url") or urllib.parse.urljoin(s["url"], "/")
    pat = re.compile(
        r'datetime="(?P<dt>\d{4}-\d{2}-\d{2}T[^"]+)".{0,1200}?'
        r'href="(?P<path>/changelog/[a-z0-9][a-z0-9-]*)"[^>]*>'
        r'(?P<title>[^<]{3,240})</a>',
        re.I | re.S,
    )
    out, seen = [], set()
    for m in pat.finditer(page):
        path = m.group("path")
        if path in seen:
            continue
        seen.add(path)
        dt = parse_iso(m.group("dt"))
        title = html.unescape(m.group("title")).strip()
        # 标题后到下一条目之间的纯文本作摘要
        tail = page[m.end(): m.end() + 1200]
        notes = note_head(re.sub(r"<[^>]+>", "\n", tail))
        out.append({"version": title, "published": dt, "prerelease": False,
                    "url": urllib.parse.urljoin(base, path), "notes": notes,
                    "date_only": bool(s.get("date_only", True))})
    if not out:
        raise LookupError("HTML changelog 未解析到条目")
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
           "html_changelog": src_html_changelog,
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
