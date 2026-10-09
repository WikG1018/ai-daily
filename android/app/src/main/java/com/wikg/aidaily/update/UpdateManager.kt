package com.wikg.aidaily.update

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.wikg.aidaily.BuildConfig
import com.wikg.aidaily.data.local.Prefs
import com.wikg.aidaily.data.model.SignatureRules
import com.wikg.aidaily.data.model.UpdateInfo
import com.wikg.aidaily.data.model.Versions
import com.wikg.aidaily.data.remote.AppJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data object Checking : UpdatePhase
    data object UpToDate : UpdatePhase
    data object Available : UpdatePhase
    data class Downloading(val bytes: Long, val total: Long, val source: String) : UpdatePhase
    data object Verifying : UpdatePhase
    data class Ready(val file: File) : UpdatePhase
    /** 已下载并校验，等待用户允许「安装未知应用」。 */
    data class NeedsPermission(val file: File) : UpdatePhase
    data class Failed(val message: String, val duringCheck: Boolean = false) : UpdatePhase
}

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    /** 可用的新版本（比当前新）；null = 没有 / 还没检查。 */
    val info: UpdateInfo? = null,
    val sheetOpen: Boolean = false,
    val ignoredVersion: String? = null,
    val bannerDismissed: Boolean = false,
    val lastCheckAt: Long = 0,
) {
    /** 首页横幅：有新版本、没被忽略、本次没关掉。 */
    val bannerVersion: String?
        get() = info?.versionName?.takeIf { it != ignoredVersion && !bannerDismissed }
}

/**
 * 应用内更新的状态机。所有公开方法都吞掉异常（只记日志）：更新功能出任何问题都不能影响 app 启动和使用。
 * 下载在自己的作用域里进行，离开页面也会继续，并以低优先级通知显示进度。
 */
class UpdateManager(
    private val app: Application,
    private val prefs: Prefs,
    private val source: UpdateSource = UpdateSource(),
    private val downloader: ApkDownloader = ApkDownloader(),
    private val currentName: String = BuildConfig.VERSION_NAME.substringBefore('-'),
    private val currentCode: Long = BuildConfig.VERSION_CODE.toLong(),
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t -> Log.w(TAG, "update task failed", t) },
    )
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private val notifier by lazy { UpdateNotifier(app) }
    private var downloadJob: Job? = null
    @Volatile private var autoChecked = false

    val currentVersion: String get() = currentName

    // —— 检查 ——

    /** 启动后调用：恢复上次发现的新版本（横幅），并按「每天最多一次」自动检查。永不抛异常。 */
    suspend fun autoCheckIfDue(nowMs: Long = System.currentTimeMillis()) = guarded("autoCheck") {
        if (autoChecked) return@guarded
        autoChecked = true
        withContext(Dispatchers.IO) { cleanupOldApks() }
        val s = prefs.settings.first()
        _state.update { it.copy(ignoredVersion = s.ignoredUpdateVersion, lastCheckAt = s.lastUpdateCheckAt) }
        prefs.cachedUpdate()?.let { raw ->
            runCatching { AppJson.decodeFromString(UpdateInfo.serializer(), raw) }.getOrNull()
                ?.takeIf { isApplicable(it) }
                ?.let { cached -> _state.update { if (it.info == null) it.copy(info = cached, phase = UpdatePhase.Available) else it } }
        }
        if (!s.autoUpdateCheck) return@guarded
        val age = nowMs - s.lastUpdateCheckAt
        if (age in 0 until AUTO_INTERVAL_MS) return@guarded
        check(manual = false)
    }

    fun checkNow() {
        scope.launch { guarded("check") { check(manual = true) } }
    }

    internal suspend fun check(manual: Boolean) {
        val busy = _state.value.phase.let { it is UpdatePhase.Downloading || it is UpdatePhase.Verifying || it is UpdatePhase.Checking }
        if (busy) {
            if (manual && _state.value.info != null) _state.update { it.copy(sheetOpen = true) }
            return
        }
        val before = _state.value.phase
        if (manual) _state.update { it.copy(phase = UpdatePhase.Checking) }
        val info = try {
            source.fetchLatest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "check failed: ${e.message}")
            _state.update { if (manual) it.copy(phase = UpdatePhase.Failed(e.message ?: "检查失败", duringCheck = true)) else it.copy(phase = before) }
            return
        }
        val newer = isApplicable(info)
        prefs.recordUpdateCheck(if (newer) AppJson.encodeToString(UpdateInfo.serializer(), info) else null)
        val now = System.currentTimeMillis()
        if (newer) {
            val existing = readyFileFor(info)
            _state.update {
                it.copy(
                    info = info,
                    phase = if (existing != null) UpdatePhase.Ready(existing) else UpdatePhase.Available,
                    sheetOpen = manual || it.sheetOpen,
                    lastCheckAt = now,
                    bannerDismissed = if (it.info?.versionName != info.versionName) false else it.bannerDismissed,
                )
            }
        } else {
            _state.update { it.copy(info = null, phase = UpdatePhase.UpToDate, lastCheckAt = now, sheetOpen = false) }
        }
    }

    private fun isApplicable(info: UpdateInfo): Boolean =
        Versions.isNewer(info.versionName, info.versionCode, currentName, currentCode) &&
            (info.minSdk <= 0 || info.minSdk <= Build.VERSION.SDK_INT)

    // —— 下载 / 校验 ——

    fun startDownload() {
        val info = _state.value.info ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            guarded("download") {
                val dir = ApkInstaller.updatesDir(app)
                val dest = File(dir, apkName(info.versionName))
                val reusable = withContext(Dispatchers.IO) { readyFileFor(info) }
                if (reusable != null) { verify(info, reusable, shaVerified = true); return@guarded }
                _state.update { it.copy(phase = UpdatePhase.Downloading(0, info.size, ""), sheetOpen = true) }
                try {
                    downloader.download(info.apkUrls, dest, info.sha256, info.size).collect { ev ->
                        when (ev) {
                            is DownloadEvent.Progress -> {
                                _state.update { it.copy(phase = UpdatePhase.Downloading(ev.bytes, ev.total, ev.source)) }
                                notifier.progress(info.versionName, ev.bytes, ev.total)
                            }
                            is DownloadEvent.Done -> verify(info, ev.file, shaVerified = info.sha256 != null)
                        }
                    }
                } catch (e: CancellationException) {
                    notifier.cancel()
                    throw e
                } catch (e: Exception) {
                    notifier.cancel()
                    val msg = if (e is ChecksumMismatchException) "安装包 SHA-256 校验失败，已删除。可能是网络或镜像异常，请重试"
                    else "下载失败：${e.message ?: e.javaClass.simpleName}"
                    _state.update { it.copy(phase = UpdatePhase.Failed(msg)) }
                }
            }
        }
    }

    private suspend fun verify(info: UpdateInfo, file: File, shaVerified: Boolean) {
        _state.update { it.copy(phase = UpdatePhase.Verifying) }
        val verdict = withContext(Dispatchers.IO) { runCatching { ApkInstaller.verify(app, file) }.getOrDefault(SignatureRules.Verdict.UNREADABLE) }
        if (!SignatureRules.allowInstall(verdict, shaVerified)) {
            file.delete()
            notifier.cancel()
            _state.update { it.copy(phase = UpdatePhase.Failed(SignatureRules.message(verdict))) }
            return
        }
        _state.update { it.copy(phase = UpdatePhase.Ready(file)) }
        notifier.ready(info.versionName)
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        runCatching { notifier.cancel() }
        _state.update { if (it.info != null) it.copy(phase = UpdatePhase.Available) else it.copy(phase = UpdatePhase.Idle) }
    }

    // —— 安装 ——

    /** 在前台（Activity context）调用。没有「安装未知应用」权限时转入 NeedsPermission，由界面引导。 */
    fun install(context: Context) = guardedSync("install") {
        val file = when (val p = _state.value.phase) {
            is UpdatePhase.Ready -> p.file
            is UpdatePhase.NeedsPermission -> p.file
            else -> return@guardedSync
        }
        if (!file.exists()) {
            _state.update { it.copy(phase = UpdatePhase.Available) }
            startDownload()
            return@guardedSync
        }
        if (!ApkInstaller.canRequestInstalls(context)) {
            _state.update { it.copy(phase = UpdatePhase.NeedsPermission(file), sheetOpen = true) }
            return@guardedSync
        }
        try {
            ApkInstaller.launchInstall(context, file)
            _state.update { it.copy(phase = UpdatePhase.Ready(file)) }
            notifier.cancel()
        } catch (e: Exception) {
            _state.update { it.copy(phase = UpdatePhase.Failed("无法打开系统安装器：${e.message ?: e.javaClass.simpleName}")) }
        }
    }

    fun openInstallPermissionSettings(context: Context) = guardedSync("perm") {
        if (!ApkInstaller.openUnknownSourcesSettings(context)) {
            _state.update { it.copy(phase = UpdatePhase.Failed("无法打开系统设置，请在 设置 → 应用 → AI 日报 → 安装未知应用 中手动开启")) }
        }
    }

    /** 从系统设置返回时：已经允许了就直接继续安装。 */
    fun onResume(context: Context) = guardedSync("resume") {
        if (_state.value.phase is UpdatePhase.NeedsPermission && ApkInstaller.canRequestInstalls(context)) install(context)
    }

    // —— 界面状态 ——

    fun openSheet() = _state.update { if (it.info != null) it.copy(sheetOpen = true) else it }
    fun closeSheet() = _state.update {
        it.copy(sheetOpen = false, phase = if (it.phase is UpdatePhase.UpToDate || (it.phase as? UpdatePhase.Failed)?.duringCheck == true) UpdatePhase.Idle else it.phase)
    }
    fun dismissBanner() = _state.update { it.copy(bannerDismissed = true) }

    fun ignoreCurrent() {
        val v = _state.value.info?.versionName ?: return
        cancelDownload()
        _state.update { it.copy(ignoredVersion = v, sheetOpen = false) }
        scope.launch { guarded("ignore") { prefs.setIgnoredUpdate(v) } }
    }

    // —— 文件 ——

    private fun apkName(version: String) = "ai-daily-$version.apk"

    /** 已下载且哈希匹配的安装包（重复点「立即更新」不必重下）。 */
    private fun readyFileFor(info: UpdateInfo): File? {
        val sha = info.sha256 ?: return null
        val f = File(ApkInstaller.updatesDir(app), apkName(info.versionName))
        return f.takeIf { it.isFile && runCatching { ApkDownloader.sha256Of(it) == sha }.getOrDefault(false) }
    }

    /** 删除半截文件和不比当前版本新的旧安装包（升级完成后下次启动即清掉）。 */
    internal fun cleanupOldApks() {
        if (downloadJob?.isActive == true) return
        val dir = ApkInstaller.updatesDir(app)
        dir.listFiles()?.forEach { f ->
            val ver = APK_NAME.matchEntire(f.name)?.groupValues?.get(1)
            val keep = ver != null && Versions.compare(ver, currentName) > 0
            if (!keep) runCatching { f.delete() }
        }
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "$what failed", t)
        }
    }

    private inline fun guardedSync(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "$what failed", t)
        }
    }

    companion object {
        private const val TAG = "UpdateManager"
        const val AUTO_INTERVAL_MS = 20L * 60 * 60 * 1000
        private val APK_NAME = Regex("""^ai-daily-(\d+(?:\.\d+){0,3})\.apk$""")
    }
}
