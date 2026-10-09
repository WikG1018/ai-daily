#!/usr/bin/env python3
"""由 tools/harness_sources.json 和 tools/x_people.json 生成 data/watchlist.json（app 自定义关注用）。

用法（在仓库根目录）:
    python3 tools/build_watchlist.py            # 重建 data/watchlist.json（内容没变则不改 updated_at）
    python3 tools/build_watchlist.py --check    # 只检查是否需要重建，需要时退出码 1
publish.py 每次发布和 --reindex 时都会自动调用。格式见 docs/schema.md。仅用标准库。
"""
import argparse
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

BJT = timezone(timedelta(hours=8))
SCHEMA_VERSION = 1
ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]*$")
TOOLS = Path(__file__).resolve().parent


def _source_url(src):
    t = src["type"]
    if t == "github":
        return f"https://github.com/{src['repo']}/releases"
    if t == "npm":
        return f"https://www.npmjs.com/package/{src['package']}"
    return src.get("html_url") or src["url"]


def build_content(tools_dir=TOOLS):
    hs = json.loads((tools_dir / "harness_sources.json").read_text(encoding="utf-8"))
    xp = json.loads((tools_dir / "x_people.json").read_text(encoding="utf-8"))
    products, people, seen = [], [], set()
    for p in hs["products"]:
        if not ID_RE.match(p["id"]) or p["id"] in seen:
            raise ValueError(f"产品 id 非法或重复: {p['id']!r}")
        seen.add(p["id"])
        products.append({"id": p["id"], "name": p["name"], "vendor": p["vendor"], "region": p["region"],
                         "kind": "harness", "url": _source_url(p["sources"][0])})
    seen = set()
    for a in xp["accounts"]:
        pid = a["handle"].lower()
        if pid in seen:
            raise ValueError(f"人物 id 重复: {pid}")
        seen.add(pid)
        people.append({"id": pid, "name": a["name"], "handle": a["handle"], "org": a["org"],
                       "role": a["role"], "region": a["region"],
                       "kind": "official" if a.get("tier") == "official" else "person",
                       "url": f"https://x.com/{a['handle']}"})
    return {"products": products, "people": people}


def build(repo):
    """返回 (watchlist dict, 是否有变化)。内容不变时沿用旧的 updated_at，避免每次发布都产生无意义的 diff。"""
    repo = Path(repo)
    content = build_content(repo / "tools")
    target = repo / "data" / "watchlist.json"
    old = json.loads(target.read_text(encoding="utf-8")) if target.exists() else None
    if old and old.get("schema_version") == SCHEMA_VERSION and \
            old.get("products") == content["products"] and old.get("people") == content["people"]:
        return old, False
    wl = {"schema_version": SCHEMA_VERSION,
          "updated_at": datetime.now(BJT).replace(microsecond=0).isoformat(), **content}
    return wl, True


def write(repo):
    wl, changed = build(repo)
    if changed:
        p = Path(repo) / "data" / "watchlist.json"
        p.parent.mkdir(exist_ok=True)
        p.write_text(json.dumps(wl, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return wl, changed


def main():
    ap = argparse.ArgumentParser(description="生成 data/watchlist.json")
    ap.add_argument("--repo", default=str(TOOLS.parent))
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    if a.check:
        _, changed = build(a.repo)
        print("需要重建" if changed else "已是最新")
        return 1 if changed else 0
    wl, changed = write(a.repo)
    print(f"data/watchlist.json：{len(wl['products'])} 个产品，{len(wl['people'])} 个账号"
          + ("（已更新）" if changed else "（无变化）"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
