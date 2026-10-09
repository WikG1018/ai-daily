package com.wikg.aidaily.ui.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wikg.aidaily.data.model.UpdateInfo
import com.wikg.aidaily.ui.components.Pill
import com.wikg.aidaily.ui.theme.AppTheme
import com.wikg.aidaily.update.UpdateManager
import com.wikg.aidaily.update.UpdatePhase
import com.wikg.aidaily.update.UpdateUiState
import com.wikg.aidaily.update.formatBytes
import com.wikg.aidaily.util.isoToBeijingLabel
import com.wikg.aidaily.util.openUrl
import com.wikg.aidaily.util.richText

class UpdateSheetActions(
    val update: () -> Unit = {},
    val later: () -> Unit = {},
    val ignore: () -> Unit = {},
    val cancel: () -> Unit = {},
    val install: () -> Unit = {},
    val grant: () -> Unit = {},
    val retry: () -> Unit = {},
    val openRelease: () -> Unit = {},
)

/** 全局挂在导航层：状态里 sheetOpen 为真时弹出。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheetHost(manager: UpdateManager) {
    val context = LocalContext.current
    val state by manager.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        manager.onResume(context)
        onPauseOrDispose { }
    }
    val info = state.info
    if (!state.sheetOpen || info == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { manager.closeSheet() },
        sheetState = sheetState,
        containerColor = AppTheme.extra.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        UpdateSheetContent(
            state = state,
            info = info,
            currentVersion = manager.currentVersion,
            actions = UpdateSheetActions(
                update = manager::startDownload,
                later = manager::closeSheet,
                ignore = manager::ignoreCurrent,
                cancel = manager::cancelDownload,
                install = { manager.install(context) },
                grant = { manager.openInstallPermissionSettings(context) },
                retry = {
                    if ((state.phase as? UpdatePhase.Failed)?.duringCheck == true) manager.checkNow() else manager.startDownload()
                },
                openRelease = { openUrl(context, info.releaseUrl) },
            ),
        )
    }
}

@Composable
fun UpdateSheetContent(state: UpdateUiState, info: UpdateInfo, currentVersion: String, actions: UpdateSheetActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding()
            .testTag("update_sheet"),
    ) {
        // —— 头部：渐变徽标 + 版本 ——
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Brush.linearGradient(listOf(AppTheme.extra.heroStart, AppTheme.extra.heroEnd))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.RocketLaunch, null, Modifier.size(28.dp), tint = Color.White) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("发现新版本", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "v${info.versionName}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        "当前 v$currentVersion",
                        bg = MaterialTheme.colorScheme.surfaceContainerHighest,
                        fg = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val meta = buildList {
            if (info.size > 0) add(formatBytes(info.size))
            isoToBeijingLabel(info.publishedAt)?.let { add(it) }
            add("Android 12+")
        }
        Text(
            meta.joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // —— 更新内容 ——
        Spacer(Modifier.height(16.dp))
        Text("更新内容", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Notes(info.notes)

        Spacer(Modifier.height(18.dp))
        AnimatedContent(
            targetState = state.phase,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "updatePhase",
        ) { phase ->
            when (phase) {
                is UpdatePhase.Downloading -> DownloadingPanel(phase, actions)
                UpdatePhase.Verifying -> BusyRow("正在校验 SHA-256 与签名…")
                UpdatePhase.Checking -> BusyRow("正在检查…")
                is UpdatePhase.Ready -> ReadyPanel(actions)
                is UpdatePhase.NeedsPermission -> PermissionPanel(actions)
                is UpdatePhase.Failed -> FailedPanel(phase.message, actions)
                else -> AvailablePanel(actions)
            }
        }
    }
}

@Composable
private fun Notes(raw: String) {
    val blocks = parseNotes(raw)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (AppTheme.extra.isDark) 0.7f else 0.55f))
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (blocks.isEmpty()) {
            Text("修复问题，提升稳定性。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        blocks.forEachIndexed { i, b ->
            when (b) {
                is NoteBlock.Heading -> Text(
                    b.text,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = if (i == 0) 0.dp else 6.dp),
                )
                is NoteBlock.Bullet -> Row {
                    Box(
                        Modifier.padding(top = 8.dp, end = 10.dp).size(5.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    )
                    Text(richText(b.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                is NoteBlock.Para -> Text(richText(b.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AvailablePanel(actions: UpdateSheetActions) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = actions.later, modifier = Modifier.weight(1f).height(50.dp)) { Text("稍后") }
            Button(
                onClick = actions.update,
                modifier = Modifier.weight(1.8f).height(50.dp).testTag("update_now"),
            ) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("立即更新")
            }
        }
        TextButton(onClick = actions.ignore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("忽略此版本", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DownloadingPanel(p: UpdatePhase.Downloading, actions: UpdateSheetActions) {
    val frac = if (p.total > 0) (p.bytes.toFloat() / p.total).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(frac, tween(250), label = "dl")
    Column(Modifier.testTag("update_progress")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (p.total > 0) "${(frac * 100).toInt()}%" else "下载中",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (p.total > 0) "${formatBytes(p.bytes)} / ${formatBytes(p.total)}" else formatBytes(p.bytes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        // 自绘圆角进度条：渐变填充
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier.fillMaxWidth(animated).height(10.dp).clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            if (AppTheme.extra.isDark) listOf(Color(0xFF7A6CFF), Color(0xFFB48CFF))
                            else listOf(AppTheme.extra.heroStart, AppTheme.extra.heroEnd),
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            (if (p.source.isNotEmpty()) "来自 ${p.source} · " else "") + "可以离开此页，下载会在后台继续，完成后通知你",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = actions.cancel, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("取消下载") }
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadyPanel(actions: UpdateSheetActions) {
    Column {
        StatusLine(Icons.Rounded.VerifiedUser, AppTheme.extra.update, "已下载，SHA-256 与签名校验通过")
        Spacer(Modifier.height(12.dp))
        Button(onClick = actions.install, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("update_install")) {
            Icon(Icons.Rounded.InstallMobile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("安装")
        }
    }
}

@Composable
private fun PermissionPanel(actions: UpdateSheetActions) {
    Column {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (AppTheme.extra.isDark) 0.16f else 0.08f))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("需要允许「安装未知应用」", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "系统要求为 AI 日报单独开启这项权限才能安装更新。安装包只来自本项目的 GitHub Release，已校验 SHA-256 与签名。" +
                    "在打开的页面里允许「AI 日报」，返回后会自动继续安装。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = actions.grant, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("update_grant")) { Text("去开启") }
    }
}

@Composable
private fun FailedPanel(message: String, actions: UpdateSheetActions) {
    Column {
        StatusLine(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error, message)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = actions.openRelease, modifier = Modifier.weight(1f).height(50.dp)) { Text("浏览器下载") }
            Button(onClick = actions.retry, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors()) { Text("重试") }
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = if (AppTheme.extra.isDark) 0.16f else 0.09f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start)
    }
}
