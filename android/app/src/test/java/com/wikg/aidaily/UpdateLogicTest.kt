package com.wikg.aidaily

import com.wikg.aidaily.data.model.GhAsset
import com.wikg.aidaily.data.model.GhRelease
import com.wikg.aidaily.data.model.SignatureRules
import com.wikg.aidaily.data.model.SignatureRules.Verdict
import com.wikg.aidaily.data.model.UpdateManifest
import com.wikg.aidaily.data.model.UpdateRules
import com.wikg.aidaily.data.model.Versions
import com.wikg.aidaily.ui.update.NoteBlock
import com.wikg.aidaily.ui.update.parseNotes
import com.wikg.aidaily.update.UpdateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateLogicTest {
    private val gh = "https://github.com/WikG1018/ai-daily/releases/download/v1.3.0/ai-daily-1.3.0.apk"
    private val sha = "a".repeat(64)

    @Test fun versionCompare() {
        assertTrue(Versions.compare("1.3.0", "1.2.0") > 0)
        assertTrue(Versions.compare("v1.10.0", "1.9.9") > 0)
        assertEquals(0, Versions.compare("1.3", "1.3.0"))
        assertEquals(0, Versions.compare("v1.3.0", "1.3.0-debug"))
        assertTrue(Versions.compare("1.2.10", "1.2.9") > 0)
        assertTrue(Versions.compare("2.0", "10.0") < 0)
        assertNull(Versions.parse("abc"))
        assertNull(Versions.parse(""))
        assertTrue(Versions.compare("junk", "1.0") < 0)
    }

    @Test fun isNewerPrefersVersionCode() {
        assertTrue(Versions.isNewer("1.3.0", 5, "1.2.0", 4))
        assertFalse(Versions.isNewer("1.3.0", 4, "1.2.0", 4))      // 同 versionCode：系统不允许覆盖为「新版」
        assertTrue(Versions.isNewer("1.3.0", null, "1.2.0", 4))     // GitHub API 没有 versionCode：按名字
        assertFalse(Versions.isNewer("1.2.0", null, "1.2.0", 4))
        assertFalse(Versions.isNewer("1.1.9", null, "1.2.0", 4))
        assertFalse(Versions.isNewer("garbage", 99, "1.2.0", 4))
    }

    @Test fun trustedUrls() {
        assertTrue(UpdateRules.isTrustedApkUrl(gh))
        assertTrue(UpdateRules.isTrustedApkUrl("https://ghfast.top/$gh"))
        assertTrue(UpdateRules.isTrustedApkUrl("https://gh-proxy.com/$gh"))
        assertFalse(UpdateRules.isTrustedApkUrl(gh.replace("https://", "http://")))
        assertFalse(UpdateRules.isTrustedApkUrl("http://ghfast.top/$gh"))
        assertFalse(UpdateRules.isTrustedApkUrl("https://github.com/evil/ai-daily/releases/download/v1.3.0/x.apk"))
        assertFalse(UpdateRules.isTrustedApkUrl("https://evil.com/x.apk"))
        assertFalse(UpdateRules.isTrustedApkUrl("https://evil.com/x.apk?u=$gh"))
        assertFalse(UpdateRules.isTrustedApkUrl("https://user@ghfast.top/$gh"))
        assertFalse(UpdateRules.isTrustedApkUrl("https://github.com/WikG1018/ai-daily/releases/download/../../x.apk"))
        assertFalse(UpdateRules.isTrustedApkUrl(gh.removeSuffix(".apk") + ".zip"))
        assertFalse(UpdateRules.isTrustedApkUrl(null))
    }

    @Test fun sha256Normalize() {
        assertEquals(sha, UpdateRules.normalizeSha256("sha256:" + sha.uppercase()))
        assertEquals(sha, UpdateRules.normalizeSha256(" $sha "))
        assertNull(UpdateRules.normalizeSha256("sha1:abcd"))
        assertNull(UpdateRules.normalizeSha256(null))
    }

    @Test fun parseManifest() {
        val body = """
            {"versionCode":5,"versionName":"1.3.0","tag":"v1.3.0","apkUrl":"$gh",
             "apkMirrors":["https://ghfast.top/$gh","http://insecure.example/$gh","https://evil.example/a.apk"],
             "sha256":"${sha.uppercase()}","size":123,"notes":"- 新功能","minSdk":31,"unknownField":true}
        """.trimIndent()
        val info = UpdateSource.parse(UpdateRules.MANIFEST_RAW, body)!!
        assertEquals("1.3.0", info.versionName)
        assertEquals(5L, info.versionCode)
        assertEquals(listOf(gh, "https://ghfast.top/$gh"), info.apkUrls)   // 不可信地址被过滤
        assertEquals(sha, info.sha256)
        assertEquals(123L, info.size)
        assertEquals(31, info.minSdk)
        assertEquals("GitHub Raw", info.source)
        // 主地址和镜像全不可信 → 无效
        assertNull(UpdateSource.parse(UpdateRules.MANIFEST_RAW, """{"versionName":"1.3.0","apkUrl":"https://evil.example/a.apk"}"""))
        assertNull(UpdateSource.parse(UpdateRules.MANIFEST_RAW, """{"versionName":"x","apkUrl":"$gh"}"""))
        assertNull(UpdateSource.parse(UpdateRules.MANIFEST_RAW, "not json"))
    }

    @Test fun repoUpdateJsonIsValid() {
        val f = File("../update.json")
        if (!f.exists()) return
        val info = UpdateSource.parse(UpdateRules.MANIFEST_RAW, f.readText())
        assertNotNull("android/update.json 必须能被 app 解析", info)
        assertNotNull(info!!.sha256)
        assertTrue(info.size > 0)
        assertTrue(info.apkUrls.size >= 2)
    }

    @Test fun parseGitHubRelease() {
        val body = """
            {"tag_name":"v1.3.0","name":"AI 日报 v1.3.0","draft":false,"prerelease":false,
             "published_at":"2026-10-09T06:00:00Z","html_url":"https://github.com/WikG1018/ai-daily/releases/tag/v1.3.0",
             "body":"## 新增\n- 应用内更新",
             "assets":[
               {"name":"notes.txt","size":1,"browser_download_url":"https://github.com/WikG1018/ai-daily/releases/download/v1.3.0/notes.txt"},
               {"name":"ai-daily-1.3.0.apk","size":22000000,"digest":"sha256:$sha","browser_download_url":"$gh"}
             ]}
        """.trimIndent()
        val info = UpdateSource.parse(UpdateRules.GITHUB_API_LATEST, body)!!
        assertEquals("1.3.0", info.versionName)
        assertNull(info.versionCode)
        assertEquals(gh, info.apkUrls.first())
        assertEquals(UpdateRules.proxied(gh), info.apkUrls.drop(1))   // 内置加速前缀兜底
        assertEquals(sha, info.sha256)
        assertEquals(22000000L, info.size)
        assertEquals("GitHub API", info.source)
        assertTrue(info.releaseUrl.endsWith("/tag/v1.3.0"))

        assertNull(UpdateRules.fromGitHub(GhRelease(tagName = "v1.4.0", prerelease = true, assets = listOf(GhAsset("a.apk", 1, gh)))))
        assertNull(UpdateRules.fromGitHub(GhRelease(tagName = "v1.4.0", assets = emptyList())))
        val noDigest = UpdateRules.fromGitHub(GhRelease(tagName = "v1.4.0", assets = listOf(GhAsset("ai-daily-1.4.0.apk", 1, gh))))!!
        assertNull(noDigest.sha256)
    }

    @Test fun metadataOrder() {
        assertEquals(
            listOf(UpdateRules.MANIFEST_RAW, UpdateRules.MANIFEST_JSDELIVR, UpdateRules.GITHUB_API_LATEST),
            UpdateRules.METADATA_ORDER,
        )
        assertEquals("https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/android/update.json", UpdateRules.MANIFEST_JSDELIVR)
    }

    @Test fun signatureVerdicts() {
        val ours = setOf("cert-a")
        fun v(pkg: String?, signers: Set<String>?, code: Long?) =
            SignatureRules.verdict("com.wikg.aidaily", ours, 4, pkg, signers, code)
        assertEquals(Verdict.OK, v("com.wikg.aidaily", setOf("cert-a"), 5))
        assertEquals(Verdict.SIGNATURE_MISMATCH, v("com.wikg.aidaily", setOf("cert-evil"), 5))
        assertEquals(Verdict.PACKAGE_MISMATCH, v("com.evil", setOf("cert-a"), 5))
        assertEquals(Verdict.NOT_NEWER, v("com.wikg.aidaily", setOf("cert-a"), 4))
        assertEquals(Verdict.UNREADABLE, v(null, null, null))
        assertEquals(Verdict.UNKNOWN_SIGNERS, v("com.wikg.aidaily", null, 5))
        // 轮换后的签名历史里包含当前证书也算通过
        assertEquals(Verdict.OK, v("com.wikg.aidaily", setOf("cert-old", "cert-a"), 5))

        assertTrue(SignatureRules.allowInstall(Verdict.OK, shaVerified = false))
        assertTrue(SignatureRules.allowInstall(Verdict.UNKNOWN_SIGNERS, shaVerified = true))
        assertFalse(SignatureRules.allowInstall(Verdict.UNKNOWN_SIGNERS, shaVerified = false))
        assertFalse(SignatureRules.allowInstall(Verdict.SIGNATURE_MISMATCH, shaVerified = true))
        assertFalse(SignatureRules.allowInstall(Verdict.PACKAGE_MISMATCH, shaVerified = true))
        assertFalse(SignatureRules.allowInstall(Verdict.NOT_NEWER, shaVerified = true))
        assertFalse(SignatureRules.allowInstall(Verdict.UNREADABLE, shaVerified = true))
    }

    @Test fun releaseNotesMarkdownLite() {
        val blocks = parseNotes("## 新增\r\n\n- **应用内更新**：检查 / 下载\n* 镜像 [ghfast](https://ghfast.top)\n1. 第一\n---\n普通 `code` 段落\n#")
        assertEquals(
            listOf(
                NoteBlock.Heading("新增"),
                NoteBlock.Bullet("**应用内更新**：检查 / 下载"),
                NoteBlock.Bullet("镜像 ghfast"),
                NoteBlock.Bullet("第一"),
                NoteBlock.Para("普通 code 段落"),
            ),
            blocks,
        )
        assertTrue(parseNotes("").isEmpty())
    }
}
