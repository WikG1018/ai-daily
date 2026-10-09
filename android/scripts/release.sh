#!/usr/bin/env bash
# AI 日报 Android 发版脚本：构建签名 APK → 创建 GitHub Release → 生成并推送 android/update.json → 刷新 jsDelivr。
#
# 用法（在仓库任意位置运行）：
#   export AIDAILY_SIGNING_PROPERTIES=/path/to/ai-daily-signing.properties
#   android/scripts/release.sh path/to/release-notes.md            # 全流程
#   android/scripts/release.sh --manifest-only path/to/notes.md    # Release 已发好，只重写 update.json
#
# 前提：已在 app/build.gradle.kts 改好 versionCode / versionName 并提交推送；gh CLI 已登录；装有 python3。
# 镜像列表写在 update.json 的 apkMirrors 里，app 不需要发版即可调整（改 MIRRORS 后用 --manifest-only 重新生成）。
set -euo pipefail

MANIFEST_ONLY=0
if [[ "${1:-}" == "--manifest-only" ]]; then MANIFEST_ONLY=1; shift; fi
NOTES_FILE="${1:?用法: release.sh [--manifest-only] <release-notes.md>}"
NOTES_FILE="$(realpath "$NOTES_FILE")"

REPO="WikG1018/ai-daily"
MIRRORS=(${AIDAILY_APK_MIRRORS:-https://ghfast.top/ https://gh-proxy.com/})
ANDROID_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$ANDROID_DIR/.." && pwd)"
GRADLE_FILE="$ANDROID_DIR/app/build.gradle.kts"

VERSION_NAME="$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$GRADLE_FILE" | head -1)"
VERSION_CODE="$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' "$GRADLE_FILE" | head -1)"
MIN_SDK="$(sed -nE 's/^[[:space:]]*minSdk = ([0-9]+).*/\1/p' "$GRADLE_FILE" | head -1)"
TAG="v$VERSION_NAME"
APK_NAME="ai-daily-$VERSION_NAME.apk"
DIST="$ANDROID_DIR/build/dist"
APK="$DIST/$APK_NAME"
echo "==> $TAG (versionCode $VERSION_CODE, minSdk $MIN_SDK)"

if [[ $MANIFEST_ONLY == 0 ]]; then
  [[ -n "${AIDAILY_SIGNING_PROPERTIES:-}${AIDAILY_KEYSTORE:-}" ]] || { echo "缺少签名配置（AIDAILY_SIGNING_PROPERTIES 或 AIDAILY_KEYSTORE）" >&2; exit 1; }
  (cd "$ANDROID_DIR" && ./gradlew --console=plain :app:testDebugUnitTest :app:assembleRelease -Pmisans.required)
  mkdir -p "$DIST"
  cp "$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk" "$APK"
  # 签名检查：必须是发布证书
  APKSIGNER="$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"/build-tools/*/apksigner | sort -V | tail -1)"
  "$APKSIGNER" verify --print-certs "$APK" | tee /dev/stderr | grep -qi "SHA-256 digest: 77a57d2f0f077d90b40ddb821af45084e9ae594da4086e1225dc91848cb5f0a2" \
    || { echo "APK 不是用发布证书签名的！" >&2; exit 1; }
  gh release create "$TAG" "$APK" --repo "$REPO" --title "AI 日报 $TAG" --notes-file "$NOTES_FILE" --target main
else
  mkdir -p "$DIST"
  [[ -f "$APK" ]] || gh release download "$TAG" --repo "$REPO" --pattern "$APK_NAME" --dir "$DIST" --clobber
fi

SHA256="$(sha256sum "$APK" | cut -d' ' -f1)"
SIZE="$(stat -c %s "$APK")"
GH_URL="https://github.com/$REPO/releases/download/$TAG/$APK_NAME"

# 与 GitHub 上的资源核对（防止本地文件和已上传的不一致）
REMOTE_DIGEST="$(gh api "repos/$REPO/releases/tags/$TAG" --jq ".assets[] | select(.name==\"$APK_NAME\") | .digest" 2>/dev/null || true)"
if [[ -n "$REMOTE_DIGEST" && "$REMOTE_DIGEST" != "sha256:$SHA256" ]]; then
  echo "本地 APK 与 Release 资源的 SHA-256 不一致：$REMOTE_DIGEST vs $SHA256" >&2; exit 1
fi

python3 - "$ANDROID_DIR/update.json" "$NOTES_FILE" "$VERSION_CODE" "$VERSION_NAME" "$TAG" "$GH_URL" "$SHA256" "$SIZE" "$MIN_SDK" "$REPO" "${MIRRORS[@]}" <<'PY'
import json, sys, datetime
out, notes, code, name, tag, url, sha, size, min_sdk, repo, *mirrors = sys.argv[1:]
data = {
    "versionCode": int(code),
    "versionName": name,
    "tag": tag,
    "apkUrl": url,
    "apkMirrors": [m.rstrip("/") + "/" + url for m in mirrors],
    "sha256": sha,
    "size": int(size),
    "minSdk": int(min_sdk),
    "publishedAt": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    "releaseUrl": f"https://github.com/{repo}/releases/tag/{tag}",
    "notes": open(notes, encoding="utf-8").read().strip(),
}
with open(out, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
    f.write("\n")
print(json.dumps({k: v for k, v in data.items() if k != "notes"}, ensure_ascii=False, indent=2))
PY

cd "$ROOT"
git add android/update.json
git commit -m "android: update.json → $TAG" && git push origin HEAD:main || echo "update.json 无变化"

echo "==> 刷新 jsDelivr 缓存"
curl -fsS "https://purge.jsdelivr.net/gh/$REPO@main/android/update.json" || true; echo
sleep 5
for u in "https://raw.githubusercontent.com/$REPO/main/android/update.json" "https://cdn.jsdelivr.net/gh/$REPO@main/android/update.json"; do
  got="$(curl -fsSL -H 'Cache-Control: no-cache' "$u" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["versionName"], d["sha256"])' || echo "FAILED")"
  echo "$u -> $got"
done
for m in "${MIRRORS[@]}"; do
  echo "镜像 $m: $(curl -sIL -o /dev/null -w '%{http_code}' -m 20 -r 0-0 "${m%/}/$GH_URL")"
done
echo "完成：$GH_URL  sha256=$SHA256  size=$SIZE"
