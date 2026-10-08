#!/usr/bin/env python3
"""AI 日报发布脚本：校验一期简报 JSON → 补 id → 写入 data/ → 重建 data/index.json
→ git commit & push → 请求 jsDelivr purge。仅使用 Python 标准库（另需 git）。

用法（任意目录均可，默认操作本脚本所在仓库）:
    python3 tools/publish.py brief.json               # 完整发布：校验、写入、提交、推送、刷新 CDN
    python3 tools/publish.py brief.json --dry-run     # 只校验并预览 id / 索引，不写任何文件
    python3 tools/publish.py brief.json --no-push     # 写入并本地提交，不推送、不 purge
    python3 tools/publish.py brief.json --force       # 覆盖已存在的同日期一期（默认拒绝覆盖）
    python3 tools/publish.py --reindex                # 只按 data/ 现有文件重建 index.json 并提交推送
可选参数: --repo PATH（仓库根目录）、--no-purge、--no-commit、-m "提交信息"

格式说明见 docs/schema.md。
"""
import argparse
import json
import re
import subprocess
import sys
import urllib.request
from datetime import date as _date, datetime, timedelta, timezone
from pathlib import Path

BJT = timezone(timedelta(hours=8))
GH_REPO = "WikG1018/ai-daily"
BRANCH = "main"
PURGE_BASE = f"https://purge.jsdelivr.net/gh/{GH_REPO}@{BRANCH}/"
SCHEMA_VERSION = 1
REGIONS = {"cn", "us", "intl"}
RESERVED_SECTION_IDS = {"highlights", "xiaomi", "footer"}
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
TIME_RE = re.compile(r"^\d{2}-\d{2} \d{2}:\d{2}$")
SECTION_ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]*$")
ISSUE_FILE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}\.json$")
ITEM_KEYS = {"id", "vendor", "region", "title", "summary", "time", "links",
             "children", "update", "update_note", "group"}
TOP_KEYS = {"schema_version", "title", "date", "window", "generated_at", "published_at",
            "highlights", "xiaomi", "sections", "notes", "source_note"}


def now_iso():
    return datetime.now(BJT).replace(microsecond=0).isoformat()


# ---------------------------------------------------------------- 校验
class Validator:
    def __init__(self):
        self.errors, self.warnings = [], []

    def err(self, path, msg):
        self.errors.append(f"{path}: {msg}")

    def warn(self, path, msg):
        self.warnings.append(f"{path}: {msg}")

    def opt_str(self, obj, key, path, required=False):
        v = obj.get(key)
        if v is None:
            if required:
                self.err(f"{path}.{key}", "必填")
            return
        if not isinstance(v, str) or (required and not v.strip()):
            self.err(f"{path}.{key}", "应为非空字符串" if required else "应为字符串")

    def opt_bool(self, obj, key, path):
        if key in obj and not isinstance(obj[key], bool):
            self.err(f"{path}.{key}", "应为 true/false")

    def item(self, it, path, date):
        if not isinstance(it, dict):
            self.err(path, "应为对象")
            return
        for k in it:
            if k not in ITEM_KEYS:
                self.warn(f"{path}.{k}", "未知字段（会原样保留）")
        self.opt_str(it, "vendor", path, required=True)
        self.opt_str(it, "title", path, required=True)
        if it.get("region") not in REGIONS:
            self.err(f"{path}.region", f"必填，取值 {sorted(REGIONS)}，当前 {it.get('region')!r}")
        self.opt_str(it, "summary", path)
        self.opt_str(it, "update_note", path)
        self.opt_bool(it, "update", path)
        self.opt_bool(it, "group", path)
        t = it.get("time")
        if t is not None and (not isinstance(t, str) or not TIME_RE.match(t)):
            self.err(f"{path}.time", f"应为 'MM-DD HH:MM'（北京时间），当前 {t!r}")
        if "id" in it:
            iid = it["id"]
            if not isinstance(iid, str) or not re.match(rf"^{re.escape(date)}-\d{{3,}}$", iid):
                self.err(f"{path}.id", f"应为 '{date}-NNN'，当前 {iid!r}")
        links = it.get("links")
        if links is not None:
            if not isinstance(links, list):
                self.err(f"{path}.links", "应为数组")
            else:
                for i, l in enumerate(links):
                    lp = f"{path}.links[{i}]"
                    if not isinstance(l, dict):
                        self.err(lp, "应为对象 {label, url}")
                        continue
                    self.opt_str(l, "label", lp)
                    u = l.get("url")
                    if not isinstance(u, str) or not re.match(r"^https?://\S+$", u.strip(), re.I):
                        self.err(f"{lp}.url", f"必填，仅允许 http/https，当前 {u!r}")
        kids = it.get("children")
        if kids is not None:
            if not isinstance(kids, list):
                self.err(f"{path}.children", "应为数组")
            else:
                for i, k in enumerate(kids):
                    self.item(k, f"{path}.children[{i}]", date)
        if it.get("group") and not kids:
            self.warn(path, "group=true 但没有 children")

    def issue(self, d):
        if not isinstance(d, dict):
            self.err("$", "顶层应为对象")
            return
        for k in d:
            if k not in TOP_KEYS:
                self.warn(f"$.{k}", "未知顶层字段（会原样保留）")
        date = d.get("date")
        if not isinstance(date, str) or not DATE_RE.match(date):
            self.err("$.date", f"必填，格式 YYYY-MM-DD，当前 {date!r}")
            date = "0000-00-00"
        else:
            try:
                _date.fromisoformat(date)
            except ValueError:
                self.err("$.date", f"不是有效日期: {date}")
        self.opt_str(d, "title", "$")
        self.opt_str(d, "highlights", "$", required=True)
        self.opt_str(d, "generated_at", "$")
        self.opt_str(d, "source_note", "$")
        w = d.get("window")
        if w is not None:
            if not isinstance(w, dict):
                self.err("$.window", "应为对象 {start, end, tz}")
            else:
                for k in ("start", "end"):
                    self.opt_str(w, k, "$.window", required=True)
                self.opt_str(w, "tz", "$.window")
        notes = d.get("notes")
        if notes is not None and (not isinstance(notes, list) or not all(isinstance(n, str) for n in notes)):
            self.err("$.notes", "应为字符串数组")
        x = d.get("xiaomi")
        if x is not None:
            if not isinstance(x, dict):
                self.err("$.xiaomi", "应为对象 {items, empty_text}")
            else:
                self.opt_str(x, "empty_text", "$.xiaomi")
                items = x.get("items", [])
                if not isinstance(items, list):
                    self.err("$.xiaomi.items", "应为数组")
                else:
                    for i, it in enumerate(items):
                        self.item(it, f"$.xiaomi.items[{i}]", date)
        secs = d.get("sections")
        if not isinstance(secs, list) or not secs:
            self.err("$.sections", "必填，非空数组")
            return
        seen = set()
        for si, s in enumerate(secs):
            sp = f"$.sections[{si}]"
            if not isinstance(s, dict):
                self.err(sp, "应为对象")
                continue
            sid = s.get("id")
            if not isinstance(sid, str) or not SECTION_ID_RE.match(sid):
                self.err(f"{sp}.id", f"必填，小写字母/数字/-/_，当前 {sid!r}")
            elif sid in RESERVED_SECTION_IDS or sid in seen:
                self.err(f"{sp}.id", f"重复或保留字: {sid}")
            seen.add(sid)
            self.opt_str(s, "title", sp, required=True)
            self.opt_str(s, "icon", sp)
            self.opt_str(s, "empty_text", sp)
            items = s.get("items")
            if not isinstance(items, list):
                self.err(f"{sp}.items", "必填，数组（可为空）")
                continue
            for i, it in enumerate(items):
                self.item(it, f"{sp}.items[{i}]", date)


# ---------------------------------------------------------------- id / 统计
def walk(items):
    """深度优先（父在前、子在后）遍历条目。"""
    for it in items or []:
        yield it
        yield from walk(it.get("children"))


def all_items(d):
    yield from walk((d.get("xiaomi") or {}).get("items"))
    for s in d.get("sections") or []:
        yield from walk(s.get("items"))


def assign_ids(d):
    """已有 id 保持不变；缺 id 的条目按遍历顺序接着最大序号编号。返回新分配的数量。"""
    date = d["date"]
    items = list(all_items(d))
    used, dup = set(), []
    for it in items:
        if "id" in it:
            if it["id"] in used:
                dup.append(it["id"])
            used.add(it["id"])
    if dup:
        raise ValueError(f"id 重复: {sorted(set(dup))}")
    nxt = max([int(i.rsplit("-", 1)[1]) for i in used] or [0]) + 1
    added = 0
    for it in items:
        if "id" not in it:
            it_id = f"{date}-{nxt:03d}"
            # 把 id 放在对象最前面，便于阅读
            rest = dict(it)
            it.clear()
            it["id"] = it_id
            it.update(rest)
            nxt += 1
            added += 1
    return added


def count_items(items):
    """与 HTML 报头一致：含子条目，不含 group 容器。"""
    return sum((0 if it.get("group") else 1) + count_items(it.get("children")) for it in items or [])


def total_count(d):
    return count_items((d.get("xiaomi") or {}).get("items")) + \
        sum(count_items(s.get("items")) for s in d.get("sections") or [])


def plain(s):
    return re.sub(r"\*\*(.+?)\*\*", r"\1", s or "").strip()


def short_highlights(s, limit=90):
    """去掉 ** 标记，取开头若干整句，总长不超过 limit 字。"""
    text = plain(s)
    sents = re.findall(r"[^。！？!?]+[。！？!?]?", text)
    out = ""
    for st in sents:
        if len(out) + len(st) > limit:
            break
        out += st
    if not out:
        out = text[: limit - 1] + "…" if len(text) > limit else text
    return out.strip()


def build_index(data_dir):
    issues = []
    for f in sorted(data_dir.glob("*.json"), reverse=True):
        if not ISSUE_FILE_RE.match(f.name):
            continue
        d = json.loads(f.read_text(encoding="utf-8"))
        entry = {
            "date": d["date"],
            "title": d.get("title") or "AI 日报",
            "highlights": short_highlights(d.get("highlights", "")),
            "item_count": total_count(d),
            "path": f"data/{f.name}",
        }
        if d.get("published_at"):
            entry["published_at"] = d["published_at"]
        issues.append(entry)
    return {
        "schema_version": SCHEMA_VERSION,
        "latest": issues[0]["date"] if issues else None,
        "updated_at": now_iso(),
        "issues": issues,
    }


def dump(obj, path):
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- git / CDN
def git(repo, *args, check=True):
    r = subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} 失败:\n{r.stdout}{r.stderr}")
    return r


def purge(paths):
    ok = True
    for p in paths:
        url = PURGE_BASE + p
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "ai-daily-publish/1"})
            with urllib.request.urlopen(req, timeout=30) as r:
                body = json.loads(r.read().decode("utf-8") or "{}")
            status = body.get("status")
            print(f"  purge {p}: HTTP {r.status}, status={status}")
        except Exception as e:  # noqa: BLE001
            ok = False
            print(f"  purge {p}: 失败 {e}")
    return ok


# ---------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description="发布一期 AI 日报到 data/ 并推送")
    ap.add_argument("json", nargs="?", help="当期简报 JSON 文件")
    ap.add_argument("--repo", default=str(Path(__file__).resolve().parent.parent), help="仓库根目录")
    ap.add_argument("--force", action="store_true", help="覆盖已存在的同日期一期")
    ap.add_argument("--dry-run", action="store_true", help="只校验和预览，不写文件")
    ap.add_argument("--no-commit", action="store_true", help="只写文件，不提交（隐含 --no-push）")
    ap.add_argument("--no-push", action="store_true", help="本地提交但不推送（隐含 --no-purge）")
    ap.add_argument("--no-purge", action="store_true", help="推送后不请求 jsDelivr purge")
    ap.add_argument("--reindex", action="store_true", help="只重建 index.json")
    ap.add_argument("-m", "--message", help="提交信息")
    a = ap.parse_args()
    if not a.json and not a.reindex:
        ap.error("需要提供 JSON 文件，或使用 --reindex")

    repo = Path(a.repo).resolve()
    data_dir = repo / "data"
    push = not (a.no_push or a.no_commit or a.dry_run)
    if push:  # 先同步远端，避免推送冲突
        git(repo, "pull", "--ff-only", "--quiet", "origin", BRANCH)

    changed = []
    date = None
    if a.json:
        d = json.loads(Path(a.json).read_text(encoding="utf-8"))
        v = Validator()
        v.issue(d)
        for w in v.warnings:
            print("警告", w)
        if v.errors:
            print(f"校验失败（{len(v.errors)} 处）:", file=sys.stderr)
            for e in v.errors:
                print("  ", e, file=sys.stderr)
            return 1
        added = assign_ids(d)
        date = d["date"]
        # 用 HTML 生成器试渲染一遍，确保数据可用
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        import build_brief  # noqa: E402
        build_brief.build(d)
        d["schema_version"] = SCHEMA_VERSION
        d["published_at"] = now_iso()
        target = data_dir / f"{date}.json"
        print(f"校验通过：{date}，共 {total_count(d)} 条，新分配 id {added} 个 → {target.relative_to(repo)}")
        if target.exists() and not a.force:
            print(f"{target.relative_to(repo)} 已存在；如确认要覆盖请加 --force", file=sys.stderr)
            return 2
        if a.dry_run:
            for it in all_items(d):
                print(f"  {it['id']}  [{it['vendor']}] {plain(it['title'])}")
            print("索引摘要:", short_highlights(d.get("highlights", "")))
            return 0
        data_dir.mkdir(exist_ok=True)
        dump(d, target)
        changed.append(f"data/{date}.json")

    if a.dry_run:
        print(json.dumps(build_index(data_dir), ensure_ascii=False, indent=2))
        return 0
    dump(build_index(data_dir), data_dir / "index.json")
    changed.append("data/index.json")
    print("已写入:", ", ".join(changed))

    if a.no_commit:
        return 0
    git(repo, "add", "--", *changed)
    if git(repo, "diff", "--cached", "--quiet", check=False).returncode == 0:
        print("没有变化，跳过提交")
    else:
        msg = a.message or (f"data: publish {date}" if date else "data: rebuild index")
        git(repo, "commit", "--quiet", "-m", msg)
        print("已提交:", git(repo, "log", "-1", "--format=%h %s").stdout.strip())
    if not push:
        return 0
    git(repo, "push", "--quiet", "origin", f"HEAD:{BRANCH}")
    print("已推送到", f"https://github.com/{GH_REPO}")
    if not a.no_purge:
        print("请求 jsDelivr purge:")
        purge(changed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
