package com.wikg.aidaily.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wikg.aidaily.data.model.SignatureRules
import java.io.File
import java.security.MessageDigest

/** 专用 FileProvider（避免与其他库的 androidx FileProvider 冲突），只暴露 cacheDir/updates/。 */
class UpdateFileProvider : FileProvider()

/**
 * 安装：FileProvider + ACTION_VIEW（application/vnd.android.package-archive）。
 * 选它而不是 PackageInstaller Session：在 MIUI / HyperOS 上会交给系统（小米）安装器，带安全扫描与确认页，
 * 兼容性最好；Session API 在部分国产 ROM 上会被拦截或静默失败，且 Android 14+ 后台启动确认页受限。
 */
object ApkInstaller {
    fun authority(context: Context) = context.packageName + ".updates"

    fun updatesDir(context: Context): File = File(context.cacheDir, "updates")

    fun canRequestInstalls(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** 跳到「安装未知应用」开关页（本应用）。返回是否成功打开。 */
    fun openUnknownSourcesSettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 交给系统安装器。抛出的异常由调用方转成界面提示。 */
    fun launchInstall(context: Context, apk: File) {
        context.startActivity(installIntent(context, apk))
    }

    // —— 签名校验 ——

    data class ArchiveFacts(val packageName: String?, val versionCode: Long?, val signers: Set<String>?)

    fun inspectArchive(context: Context, apk: File): ArchiveFacts {
        val pm = context.packageManager
        val info: PackageInfo? = runCatching {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull()
        return ArchiveFacts(info?.packageName, info?.longVersionCode, info?.let { signersOf(it) })
    }

    fun ourSigners(context: Context): Set<String> = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        signersOf(info).orEmpty()
    }.getOrDefault(emptySet())

    fun ourVersionCode(context: Context): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }.getOrDefault(0L)

    private fun signersOf(info: PackageInfo): Set<String>? {
        val si = info.signingInfo ?: return null
        val certs = buildList {
            runCatching { si.apkContentsSigners }.getOrNull()?.let { addAll(it) }
            if (!si.hasMultipleSigners()) runCatching { si.signingCertificateHistory }.getOrNull()?.let { addAll(it) }
        }
        if (certs.isEmpty()) return null
        return certs.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    fun verify(context: Context, apk: File): SignatureRules.Verdict {
        val facts = inspectArchive(context, apk)
        return SignatureRules.verdict(
            ourPackage = context.packageName,
            ourSigners = ourSigners(context),
            ourVersionCode = ourVersionCode(context),
            archivePackage = facts.packageName,
            archiveSigners = facts.signers,
            archiveVersionCode = facts.versionCode,
        )
    }
}
