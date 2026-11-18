package com.selfgit.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfgit.app.data.AuthMode
import com.selfgit.app.data.InstallerType
import com.selfgit.app.data.SettingsStore
import com.selfgit.app.ui.components.ChoiceChip
import com.selfgit.app.ui.components.GlassPanel
import com.selfgit.app.ui.components.GlassBadge
import com.selfgit.app.ui.components.InlineNotice
import com.selfgit.app.ui.components.PrimaryButton
import com.selfgit.app.ui.components.ScreenHeader
import com.selfgit.app.ui.components.SectionRow
import com.selfgit.app.ui.components.VerticalGap
import com.selfgit.app.ui.components.clickableNoRipple
import com.selfgit.app.ui.navigation.TrackNavBarScroll
import com.selfgit.app.ui.theme.LocalPalette
import com.selfgit.app.ui.theme.ThemeMode
import androidx.compose.material3.Text

/**
 * 设置页（UI 文档 S5 的阶段 0 子集）：
 * 主机信息（含备用地址编辑）、令牌管理、主题模式、退出登录/清除数据（输入确认短语）。
 */
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    onThemeChange: (ThemeMode) -> Unit,
    onReauth: () -> Unit,
    onReset: () -> Unit
) {
    val palette = LocalPalette.current
    val host = settings.host
    var showResetDialog by remember { mutableStateOf(false) }
    var showBackupEditor by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    TrackNavBarScroll(scroll)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp)
    ) {
        VerticalGap(18.dp)
        ScreenHeader(
            title = "设置",
            subtitle = "主机、凭证与偏好"
        )
        VerticalGap(18.dp)

        // ---- 主机信息 ----
        GlassPanel(contentPadding = PaddingValues(18.dp)) {
            SectionRow(
                title = "主机",
                subtitle = host?.displayName ?: "未配置",
                icon = Icons.Filled.Shield
            )
            VerticalGap(12.dp)
            InfoRow("主地址", host?.primaryUrl ?: "—")
            InfoRow("备地址", host?.backupUrl?.ifBlank { "未配置" } ?: "—")
            InfoRow("账号", host?.username ?: "—")
            InfoRow("认证方式", when (host?.authMode) {
                AuthMode.PASSWORD_BOOTSTRAP -> "密码引导（双令牌）"
                AuthMode.PASTE_TOKEN -> "粘贴令牌"
                null -> "—"
            })
            VerticalGap(12.dp)
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                com.selfgit.app.ui.components.MiniButton(
                    text = if (host?.backupUrl.isNullOrBlank()) "添加备用地址" else "编辑备用地址",
                    onClick = { showBackupEditor = true },
                    icon = Icons.Filled.Dns
                )
                com.selfgit.app.ui.components.MiniButton(
                    text = "重新认证",
                    onClick = onReauth,
                    icon = Icons.Filled.Key
                )
            }
        }

        VerticalGap(16.dp)

        // ---- 令牌管理 ----
        GlassPanel(contentPadding = PaddingValues(18.dp)) {
            SectionRow(
                title = "令牌",
                subtitle = "Keystore 加密存储，卸载即销毁",
                icon = Icons.Filled.Key
            )
            VerticalGap(12.dp)
            InfoRow("只读令牌", host?.roTokenName?.ifBlank { "未记录" } ?: "—")
            InfoRow("读写令牌", host?.rwTokenName?.ifBlank { "未记录" } ?: "—")
            VerticalGap(8.dp)
            Text(
                text = "吊销请在 Gitea 网页端 设置→应用 中操作；吊销后回本页点「重新认证」。",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = palette.textTertiary
            )
        }

        VerticalGap(16.dp)

        // ---- 安装方式（阶段 3，§11.5）----
        InstallerSettingsCard(settings)

        VerticalGap(16.dp)

        // ---- 主题 ----
        GlassPanel(contentPadding = PaddingValues(18.dp)) {
            SectionRow(title = "主题", subtitle = "浅色 / 深色 / 跟随系统")
            VerticalGap(12.dp)
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                ChoiceChip(
                    text = "跟随系统",
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeChange(ThemeMode.SYSTEM) }
                )
                ChoiceChip(
                    text = "浅色",
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeChange(ThemeMode.LIGHT) }
                )
                ChoiceChip(
                    text = "深色",
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { onThemeChange(ThemeMode.DARK) }
                )
            }
        }

        VerticalGap(16.dp)

        // ---- 危险区：点击先弹二次确认（输入确认短语）----
        GlassPanel(contentPadding = PaddingValues(18.dp)) {
            SectionRow(title = "数据", subtitle = "清除主机配置、令牌与全部本地状态")
            VerticalGap(12.dp)
            PrimaryButton(
                text = "清除全部数据",
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                danger = true
            )
        }

        VerticalGap(90.dp)
    }

    // ---- 备用地址编辑弹窗 ----
    if (showBackupEditor) {
        BackupUrlDialog(
            current = settings.host?.backupUrl.orEmpty(),
            onSave = { normalized ->
                settings.host?.let { h ->
                    settings.host = h.copy(backupUrl = normalized)
                }
                showBackupEditor = false
            },
            onDismiss = { showBackupEditor = false }
        )
    }

    // ---- 清除数据二次确认：必须输入"确认清除" ----
    if (showResetDialog) {
        var confirmText by remember { mutableStateOf("") }
        com.selfgit.app.ui.components.GlassAlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = "清除全部数据",
            text = {
                Column {
                    Text(
                        "将清除主机配置、访问令牌、追踪映射、上传记录、置顶等全部本地数据，且不可撤销。",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    com.selfgit.app.ui.components.GlassTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        label = "输入「确认清除」以继续",
                        placeholder = "确认清除",
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                com.selfgit.app.ui.components.MiniButton(
                    text = "清除",
                    tone = palette.danger,
                    enabled = confirmText.trim() == "确认清除",
                    onClick = {
                        showResetDialog = false
                        onReset()
                    }
                )
            },
            dismissButton = {
                com.selfgit.app.ui.components.MiniButton(
                    text = "取消",
                    onClick = { showResetDialog = false }
                )
            }
        )
    }
}

/** 备用地址编辑弹窗：主地址保持不变，仅补改备用地址。 */
@Composable
private fun BackupUrlDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    val normalized = text.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else null
    }
    val error = if (text.isNotBlank() && normalized == null)
        "地址格式无效（需 http:// 或 https:// 开头）" else null

    com.selfgit.app.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "备用地址",
        text = {
            Column {
                Text(
                    "主地址不可达时自动切换到备地址（如 DDNS HTTPS 外网域名）。",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                com.selfgit.app.ui.components.GlassTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "备地址（留空则清除）",
                    placeholder = "https://gitea.example.com",
                    singleLine = true,
                    error = error,
                    helper = "仅支持系统信任证书（如 Let's Encrypt）"
                )
            }
        },
        confirmButton = {
            com.selfgit.app.ui.components.MiniButton(
                text = "保存",
                enabled = text.isBlank() || (normalized != null && error == null),
                onClick = { onSave(text.trim().trimEnd('/')) }
            )
        },
        dismissButton = {
            com.selfgit.app.ui.components.MiniButton(
                text = "取消",
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = palette.textTertiary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}


/** 安装方式卡片：四后端选择 + Shizuku/Root 就绪状态 + 第三方安装器选择（§11.5）。 */
@Composable
private fun InstallerSettingsCard(settings: SettingsStore) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var showPicker by remember { mutableStateOf(false) }

    // SettingsStore 是普通 SharedPreferences 读写，不是 Compose 状态；
    // 这里用本地状态镜像一次，点击后才能立即重组出反馈。
    var currentType by remember { mutableStateOf(settings.installerType) }
    var thirdPkg by remember { mutableStateOf(settings.thirdPartyPkg) }
    var statusTick by remember { mutableStateOf(0) }

    GlassPanel(contentPadding = PaddingValues(18.dp)) {
        SectionRow(
            title = "安装方式",
            subtitle = "未就绪的后端自动回退系统安装器",
            icon = Icons.Filled.Android
        )
        VerticalGap(12.dp)
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            InstallerType.entries.forEach { type ->
                ChoiceChip(
                    text = type.label.removeSuffix("安装器"),
                    selected = currentType == type,
                    onClick = {
                        settings.installerType = type
                        currentType = type
                        statusTick++
                    }
                )
            }
        }

        VerticalGap(10.dp)
        val status = remember(currentType, statusTick, thirdPkg) {
            backendStatus(context, currentType, settings)
        }
        Text(
            text = status,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = if (status.startsWith("就绪") || status.startsWith("可用")) palette.success else palette.textTertiary
        )

        if (currentType == InstallerType.THIRD_PARTY) {
            VerticalGap(10.dp)
            val name = remember(thirdPkg) {
                if (thirdPkg.isBlank()) "未选择"
                else runCatching {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(thirdPkg, 0)
                    ).toString()
                }.getOrDefault(thirdPkg)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "当前安装器：$name",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                com.selfgit.app.ui.components.MiniButton(
                    text = "选择安装器",
                    onClick = { showPicker = true },
                    icon = Icons.Filled.Apps
                )
            }
        }
    }

    if (showPicker) {
        ThirdPartyPickerDialog(
            selected = thirdPkg,
            onPick = { pkg ->
                settings.thirdPartyPkg = pkg
                thirdPkg = pkg
                statusTick++
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/** 后端就绪状态描述（§11.5：未就绪时明确提示）。 */
private fun backendStatus(context: android.content.Context, type: InstallerType, settings: SettingsStore): String =
    when (type) {
        InstallerType.SYSTEM -> "就绪：由系统安装器处理未知来源授权"
        InstallerType.SHIZUKU -> {
            val ping = try { rikka.shizuku.Shizuku.pingBinder() } catch (_: Throwable) { false }
            val granted = ping && try {
                rikka.shizuku.Shizuku.checkSelfPermission() ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) { false }
            when {
                !ping -> "未就绪：Shizuku 未运行（请打开 Shizuku 并启动服务）"
                !granted -> "待授权：安装时将拉起 Shizuku 授权弹窗"
                else -> "就绪：静默安装（pm install）"
            }
        }
        InstallerType.ROOT -> {
            val granted = try {
                com.topjohnwu.superuser.Shell.getShell()
                com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true
            } catch (_: Throwable) { false }
            if (granted) "就绪：静默安装（libsu）" else "未就绪：无 su 或未授权 Root"
        }
        InstallerType.THIRD_PARTY -> {
            val pkg = settings.thirdPartyPkg
            when {
                pkg.isBlank() -> "未选择第三方安装器"
                com.selfgit.app.net.ApkInstaller.installedVersion(context, pkg) == null ->
                    "未就绪：所选安装器已卸载，将自动回退系统安装器"
                else -> "可用：安装意图将转发给所选安装器"
            }
        }
    }

/** 第三方安装器选择对话框：枚举能处理安装意图的应用。 */
@Composable
private fun ThirdPartyPickerDialog(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalPalette.current

    val candidates = remember {
        val pm = context.packageManager
        val found = mutableMapOf<String, String>()
        listOf(
            Intent(Intent.ACTION_INSTALL_PACKAGE),
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.EMPTY, "application/vnd.android.package-archive")
            }
        ).forEach { probe ->
            runCatching { pm.queryIntentActivities(probe, 0) }
                .getOrDefault(emptyList())
                .forEach { ri ->
                    if (ri.activityInfo.packageName != context.packageName && ri.activityInfo.exported) {
                        found.putIfAbsent(ri.activityInfo.packageName, ri.loadLabel(pm).toString())
                    }
                }
        }
        found.entries.sortedBy { it.value }
    }

    com.selfgit.app.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "选择第三方安装器",
        text = {
            Column {
                if (candidates.isEmpty()) {
                    Text("未找到可用的第三方安装器", fontSize = 13.sp, color = palette.textTertiary)
                } else {
                    candidates.forEach { (pkg, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickableNoRipple { onPick(pkg) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                fontSize = 14.sp,
                                color = if (pkg == selected) palette.primary else palette.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (pkg == selected) {
                                GlassBadge("当前", tint = palette.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            com.selfgit.app.ui.components.MiniButton("关闭", onClick = onDismiss)
        }
    )
}
