package com.wikg.aidaily.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * 应用内更新（v1.3）用到的数据模型与纯逻辑（无 Android 依赖，便于单元测试）。
 *
 * 元数据来源顺序（见 android/README.md「应用内更新」）：
 *   1. android/update.json @ GitHub Raw
 *   2. android/update.json @ jsDelivr
 *   3. GitHub Releases API（api.github.com，未鉴权，可能被限流）
 */

/** android/update.json：由发版脚本 android/scripts/release.sh 生成。 */
@Serializable
data class UpdateManifest(
    val versionCode: Long = 0,
    val versionName: String = "",
    val tag: String? = null,
    val apkUrl: String = "",
    val apkMirrors: List<String> = emptyList(),
    val sha256: String? = null,
    val size: Long = 0,
    val notes: String = "",
    val minSdk: Int = 0,
    val publishedAt: String? = null,
    val releaseUrl: String? = null,
)

@Serializable
data class GhAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String = "",
    @SerialName("content_type") val contentType: String? = null,
    /** GitHub 自 2025 年起为 release 资源提供 "sha256:…" 摘要。 */
    val digest: String? = null,
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

/** 统一后的「可用更新」描述。会序列化进 DataStore，供首页横幅跨启动展示。 */
@Serializable
data class UpdateInfo(
    val versionName: String,
    /** update.json 里有；GitHub API 没有（为 null 时只按 versionName 比较）。 */
    val versionCode: Long? = null,
    val notes: String = "",
    /** 按顺序尝试：GitHub 原始地址在前，镜像在后。全部已通过 [UpdateRules.isTrustedApkUrl]。 */
    val apkUrls: List<String>,
    /** 小写 64 位十六进制；null = 来源没有提供（此时必须通过签名校验才允许安装）。 */
    val sha256: String? = null,
    val size: Long = 0,
    val minSdk: Int = 0,
    val publishedAt: String? = null,
    val source: String = "",
    val releaseUrl: String = UpdateRules.RELEASES_PAGE,
)

object Versions {
    /** "v1.3.0" / "1.3" / "1.3.0-beta" → [1,3,0]；无法解析返回 null。 */
    fun parse(raw: String?): List<Int>? {
        val s = raw?.trim()?.removePrefix("v")?.removePrefix("V")?.substringBefore('-')?.substringBefore('+') ?: return null
        if (s.isEmpty()) return null
        val parts = s.split('.')
        if (parts.size > 4) return null
        return parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }

    /** 语义化比较，缺的段按 0 处理（1.3 == 1.3.0）。无法解析的视为最小。 */
    fun compare(a: String?, b: String?): Int {
        val pa = parse(a); val pb = parse(b)
        if (pa == null || pb == null) return (if (pa == null) 0 else 1) - (if (pb == null) 0 else 1)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /**
     * 候选版本是否比当前新：有 versionCode 时以 versionCode 为准（与系统覆盖安装规则一致），
     * 否则按 versionName 语义比较。
     */
    fun isNewer(candidateName: String, candidateCode: Long?, currentName: String, currentCode: Long): Boolean {
        if (parse(candidateName) == null) return false
        if (candidateCode != null && candidateCode > 0) return candidateCode > currentCode
        return compare(candidateName, currentName) > 0
    }
}

object UpdateRules {
    const val REPO = "WikG1018/ai-daily"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"
    const val MANIFEST_RAW = "https://raw.githubusercontent.com/$REPO/main/android/update.json"
    const val MANIFEST_JSDELIVR = "https://cdn.jsdelivr.net/gh/$REPO@main/android/update.json"
    const val GITHUB_API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    /** 元数据来源顺序。 */
    val METADATA_ORDER = listOf(MANIFEST_RAW, MANIFEST_JSDELIVR, GITHUB_API_LATEST)

    /**
     * 内置的 GitHub 下载加速前缀：仅当元数据来自 GitHub API（没有 apkMirrors）时使用。
     * 正常情况下镜像列表来自 update.json，可随时在仓库里调整，不需要发版。
     */
    val BUILTIN_PROXIES = listOf("https://ghfast.top/", "https://gh-proxy.com/")

    private val SHA = Regex("^[0-9a-f]{64}$")

    /** "sha256:ABC…" / "abc…" → 小写十六进制；格式不对返回 null。 */
    fun normalizeSha256(raw: String?): String? {
        val s = raw?.trim()?.lowercase()?.removePrefix("sha256:") ?: return null
        return s.takeIf { SHA.matches(it) }
    }

    /**
     * 只接受本仓库 Release 资源：
     *   - https://github.com/WikG1018/ai-daily/releases/download/…
     *   - 或 https://<代理>/https://github.com/WikG1018/ai-daily/releases/download/…（GitHub 下载加速代理）
     * 一律要求 https、无 userinfo、文件名以 .apk 结尾、不含 ".." 。
     */
    fun isTrustedApkUrl(url: String?): Boolean {
        if (url.isNullOrBlank() || url.length > 2048) return false
        if (url.any { it.isWhitespace() } || ".." in url || '\\' in url) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.host.isNullOrBlank()) return false
        if (uri.rawQuery != null || uri.rawFragment != null) return false
        if (!url.endsWith(".apk")) return false
        if (url.startsWith(DOWNLOAD_PREFIX)) return true
        // 代理形式：https://host[/prefix]/https://github.com/WikG1018/ai-daily/releases/download/…
        val idx = url.indexOf("/" + DOWNLOAD_PREFIX)
        return idx > "https://".length && url.substring(idx + 1).startsWith(DOWNLOAD_PREFIX)
    }

    fun proxied(ghUrl: String, proxies: List<String> = BUILTIN_PROXIES): List<String> =
        proxies.map { it.trimEnd('/') + "/" + ghUrl }

    fun fromManifest(m: UpdateManifest, source: String): UpdateInfo? {
        if (Versions.parse(m.versionName) == null) return null
        val urls = (listOf(m.apkUrl) + m.apkMirrors).filter { isTrustedApkUrl(it) }.distinct()
        if (urls.isEmpty()) return null
        return UpdateInfo(
            versionName = m.versionName.removePrefix("v"),
            versionCode = m.versionCode.takeIf { it > 0 },
            notes = m.notes.trim(),
            apkUrls = urls,
            sha256 = normalizeSha256(m.sha256),
            size = m.size.coerceAtLeast(0),
            minSdk = m.minSdk,
            publishedAt = m.publishedAt,
            source = source,
            releaseUrl = m.releaseUrl?.takeIf { it.startsWith("https://github.com/$REPO/") } ?: RELEASES_PAGE,
        )
    }

    fun fromGitHub(r: GhRelease, source: String = "GitHub API"): UpdateInfo? {
        if (r.draft || r.prerelease) return null
        val name = r.tagName.removePrefix("v")
        if (Versions.parse(name) == null) return null
        val apks = r.assets.filter { it.name.endsWith(".apk") && isTrustedApkUrl(it.downloadUrl) }
        // 有多个 .apk 时优先 ai-daily-<版本>.apk
        val asset = apks.firstOrNull { it.name == "ai-daily-$name.apk" } ?: apks.firstOrNull() ?: return null
        return UpdateInfo(
            versionName = name,
            versionCode = null,
            notes = r.body.orEmpty().trim(),
            apkUrls = (listOf(asset.downloadUrl) + proxied(asset.downloadUrl)).filter { isTrustedApkUrl(it) },
            sha256 = normalizeSha256(asset.digest),
            size = asset.size.coerceAtLeast(0),
            publishedAt = r.publishedAt,
            source = source,
            releaseUrl = r.htmlUrl?.takeIf { it.startsWith("https://github.com/$REPO/") } ?: RELEASES_PAGE,
        )
    }
}

/** 签名校验的纯逻辑部分。证书用 SHA-256 指纹（小写十六进制）表示。 */
object SignatureRules {
    enum class Verdict { OK, PACKAGE_MISMATCH, SIGNATURE_MISMATCH, NOT_NEWER, UNREADABLE, UNKNOWN_SIGNERS }

    fun verdict(
        ourPackage: String,
        ourSigners: Set<String>,
        ourVersionCode: Long,
        archivePackage: String?,
        archiveSigners: Set<String>?,
        archiveVersionCode: Long?,
    ): Verdict = when {
        archivePackage == null -> Verdict.UNREADABLE
        archivePackage != ourPackage -> Verdict.PACKAGE_MISMATCH
        archiveVersionCode != null && archiveVersionCode <= ourVersionCode -> Verdict.NOT_NEWER
        archiveSigners.isNullOrEmpty() || ourSigners.isEmpty() -> Verdict.UNKNOWN_SIGNERS
        archiveSigners.intersect(ourSigners).isEmpty() -> Verdict.SIGNATURE_MISMATCH
        else -> Verdict.OK
    }

    /**
     * 是否允许交给系统安装：签名明确不同 / 包名不同 / 不是新版本 / 读不出包 → 拒绝；
     * 读不到签名（个别 ROM）时，只有 SHA-256 已校验通过才放行（系统安装器本身也会拒绝签名不同的覆盖安装）。
     */
    fun allowInstall(v: Verdict, shaVerified: Boolean): Boolean = when (v) {
        Verdict.OK -> true
        Verdict.UNKNOWN_SIGNERS -> shaVerified
        else -> false
    }

    fun message(v: Verdict): String = when (v) {
        Verdict.OK -> "签名校验通过"
        Verdict.PACKAGE_MISMATCH -> "安装包的包名与本应用不符，已中止"
        Verdict.SIGNATURE_MISMATCH -> "安装包签名与当前应用不一致，已中止（可能被篡改）"
        Verdict.NOT_NEWER -> "安装包版本不比当前版本新，已中止"
        Verdict.UNREADABLE -> "无法解析安装包，文件可能已损坏"
        Verdict.UNKNOWN_SIGNERS -> "无法读取安装包签名"
    }
}
