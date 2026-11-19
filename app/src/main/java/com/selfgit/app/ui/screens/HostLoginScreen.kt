package com.selfgit.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfgit.app.data.AuthMode
import com.selfgit.app.data.HostConfig
import com.selfgit.app.data.SettingsStore
import com.selfgit.app.net.ApiException
import com.selfgit.app.net.ErrKind
import com.selfgit.app.net.GiteaClient
import com.selfgit.app.ui.components.GhostButton
import com.selfgit.app.ui.components.GlassPanel
import com.selfgit.app.ui.components.GlassTextField
import com.selfgit.app.ui.components.InlineNotice
import com.selfgit.app.ui.components.PrimaryButton
import com.selfgit.app.ui.components.SectionRow
import com.selfgit.app.ui.components.VerticalGap
import com.selfgit.app.ui.components.ChoiceChip
import com.selfgit.app.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主机与登录页（UI 文档 S4；流程 P1 首次登录 / P6 重新认证）。
 *
 * 步骤：主机配置 → 免认证探测 → 认证二选一（密码引导 / 粘贴令牌）→ 自校验 → 完成。
 * 密码只在引导请求内存中出现，不落盘；令牌 Keystore 加密存储。
 * 重新认证模式提供「返回设置」取消路径（onCancel），系统返回键同样生效。
 */
@Composable
fun HostLoginScreen(
    settings: SettingsStore,
    reauth: Boolean,
    onDone: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val client = remember { GiteaClient() }
    val context = LocalContext.current

    val saved = settings.host

    // ---- 表单状态（reauth 时预填主机，只走认证）----
    var primaryUrl by remember { mutableStateOf(saved?.primaryUrl ?: "") }
    var backupUrl by remember { mutableStateOf(saved?.backupUrl ?: "") }
    var displayName by remember { mutableStateOf(saved?.displayName ?: "") }
    var username by remember { mutableStateOf(saved?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var pastedToken by remember { mutableStateOf("") }
    var authMode by remember {
        mutableStateOf(if (reauth) saved?.authMode ?: AuthMode.PASSWORD_BOOTSTRAP else AuthMode.PASSWORD_BOOTSTRAP)
    }

    // ---- 流程状态 ----
    var probing by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<String?>(null) }
    // 主/备各自独立探测结果（保存互不覆盖：只有探测通过的地址才落盘）
    var probePrimaryOk by remember { mutableStateOf(false) }
    var probeBackupOk by remember { mutableStateOf(false) }
    var probeActiveUrl by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val normalizedPrimary = normalizeUrl(primaryUrl)
    val normalizedBackup = normalizeUrl(backupUrl)

    val canProbe = !probing && !working && normalizedPrimary != null
    val canAuth = probeResult != null && !working && !probing &&
        (when (authMode) {
            AuthMode.PASSWORD_BOOTSTRAP -> username.isNotBlank() && password.isNotBlank()
            AuthMode.PASTE_TOKEN -> username.isNotBlank() && pastedToken.isNotBlank()
        })

    // 重新认证取消路径：系统返回键（按钮/手势）等效「返回设置」
    if (reauth && onCancel != null) {
        androidx.activity.compose.BackHandler(onBack = onCancel)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        VerticalGap(28.dp)
        androidx.compose.material3.Text(
            text = if (reauth) "重新认证" else "连接你的 Gitea",
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            color = palette.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            text = if (reauth)
                "主机配置已保留，仅需重新建立凭证"
            else
                "密码仅用于一次性创建访问令牌，不会保存在本机",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
        )
        VerticalGap(22.dp)

        // ---- 重新认证：显式返回/取消路径 ----
        if (reauth && onCancel != null) {
            GhostButton(
                text = "返回设置（取消重新认证）",
                onClick = onCancel,
                icon = Icons.AutoMirrored.Filled.ArrowBack
            )
            VerticalGap(16.dp)
        }

        // ---- 主机配置卡片 ----
        GlassPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            SectionRow(
                title = "主机配置",
                subtitle = "主地址必填；备地址可选（DDNS HTTPS）",
                icon = Icons.Filled.Dns
            )
            VerticalGap(14.dp)
            GlassTextField(
                value = primaryUrl,
                onValueChange = {
                    primaryUrl = it
                    probeResult = null
                    probePrimaryOk = false
                    probeBackupOk = false
                    probeActiveUrl = null
                },
                label = "主地址（局域网 HTTP）",
                placeholder = "http://192.168.x.x:3000",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                enabled = !reauth,
                error = if (primaryUrl.isNotBlank() && normalizedPrimary == null)
                    "地址格式无效（需 http:// 或 https:// 开头）" else null
            )
            VerticalGap(10.dp)
            GlassTextField(
                value = backupUrl,
                onValueChange = {
                    backupUrl = it
                    probeResult = null
                    probePrimaryOk = false
                    probeBackupOk = false
                    probeActiveUrl = null
                },
                label = "备地址（DDNS HTTPS，可选）",
                placeholder = "https://gitea.example.com",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                enabled = !reauth,
                error = if (backupUrl.isNotBlank() && normalizedBackup == null)
                    "地址格式无效" else null,
                helper = "仅支持系统信任证书（如 Let's Encrypt）；自签证书不支持"
            )
            VerticalGap(10.dp)
            GlassTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "显示名（可选）",
                placeholder = "NAS Gitea",
                imeAction = ImeAction.Next,
                enabled = !reauth
            )
            VerticalGap(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = if (probing) "探测中…" else "探测主机",
                    onClick = {
                        scope.launch {
                            probing = true
                            error = null
                            probeResult = null
                            try {
                                val outcome = withContext(Dispatchers.IO) {
                                    probeBoth(client, normalizedPrimary!!, normalizedBackup)
                                }
                                probeResult = outcome.version
                                probePrimaryOk = outcome.primaryOk
                                probeBackupOk = outcome.backupOk
                                probeActiveUrl = outcome.activeUrl
                                statusLine = null
                            } catch (e: ApiException) {
                                error = e.message
                            } finally {
                                probing = false
                            }
                        }
                    },
                    enabled = canProbe,
                    modifier = Modifier.weight(1f)
                )
            }
            if (probeResult != null) {
                VerticalGap(10.dp)
                InlineNotice(
                    text = "连接可用（Gitea $probeResult）：主地址${if (probePrimaryOk) "可达" else "不可达"}，备地址${
                        when {
                            normalizedBackup == null -> "未填写"
                            probeBackupOk -> "可达"
                            else -> "不可达"
                        }
                    }",
                    tone = palette.success
                )
                VerticalGap(6.dp)
                androidx.compose.material3.Text(
                    text = "登录时只保存探测通过的地址；不可达的地址不会写入，也不会与另一地址互相覆盖。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = palette.textTertiary
                )
            }
        }

        VerticalGap(16.dp)

        // ---- 认证卡片 ----
        GlassPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            SectionRow(
                title = "认证",
                subtitle = "密码引导自动创建 ro/rw 双令牌；或粘贴已有令牌",
                icon = Icons.Filled.Key
            )
            VerticalGap(14.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChip(
                    text = "密码引导",
                    selected = authMode == AuthMode.PASSWORD_BOOTSTRAP,
                    onClick = { authMode = AuthMode.PASSWORD_BOOTSTRAP },
                    icon = Icons.Filled.Password
                )
                ChoiceChip(
                    text = "粘贴令牌",
                    selected = authMode == AuthMode.PASTE_TOKEN,
                    onClick = { authMode = AuthMode.PASTE_TOKEN },
                    icon = Icons.Filled.Key
                )
            }
            VerticalGap(12.dp)

            GlassTextField(
                value = username,
                onValueChange = { username = it },
                label = "账号",
                placeholder = "your-username",
                leadingIcon = Icons.Filled.Person,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
                enabled = !reauth || authMode == AuthMode.PASTE_TOKEN
            )
            VerticalGap(10.dp)

            if (authMode == AuthMode.PASSWORD_BOOTSTRAP) {
                GlassTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    placeholder = "仅本次引导使用，用完即弃",
                    obscure = true,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    helper = "将创建 selfgit-ro（只读）与 selfgit-rw（读写）两个令牌"
                )
            } else {
                GlassTextField(
                    value = pastedToken,
                    onValueChange = { pastedToken = it },
                    label = "访问令牌",
                    placeholder = "在 Gitea 网页端 设置→应用 生成后粘贴",
                    obscure = true,
                    monospace = true,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                    helper = "浏览需要 repository 读权限；上传另需写权限"
                )
            }

            VerticalGap(16.dp)
            PrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = when {
                    working -> statusLine ?: "处理中…"
                    authMode == AuthMode.PASSWORD_BOOTSTRAP -> "创建令牌并登录"
                    else -> "验证令牌并登录"
                },
                onClick = {
                    scope.launch {
                        working = true
                        error = null
                        try {
                            withContext(Dispatchers.IO) {
                                authenticate(
                                    client = client,
                                    settings = settings,
                                    activeUrl = probeActiveUrl ?: normalizedPrimary!!,
                                    // 只保存探测通过的地址；主/备各自独立，互不覆盖
                                    savePrimaryUrl = if (probePrimaryOk) normalizedPrimary else null,
                                    saveBackupUrl = if (probeBackupOk) normalizedBackup else null,
                                    authMode = authMode,
                                    reauth = reauth,
                                    username = username.trim(),
                                    password = password,
                                    pastedToken = pastedToken.trim(),
                                    displayName = displayName.trim(),
                                    onStatus = { statusLine = it }
                                )
                            }
                            password = ""
                            onDone()
                        } catch (e: ApiException) {
                            error = if (e.kind == ErrKind.AUTH &&
                                authMode == AuthMode.PASSWORD_BOOTSTRAP
                            ) {
                                "密码认证被拒绝：可能密码错误，或账号已开启两步验证（2FA）。请改用「粘贴令牌」方式。"
                            } else {
                                e.message
                            }
                        } finally {
                            working = false
                            statusLine = null
                        }
                    }
                },
                enabled = canAuth
            )
            if (authMode == AuthMode.PASTE_TOKEN) {
                VerticalGap(10.dp)
                GhostButton(
                    text = "去 Gitea 网页端生成令牌",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("${probeActiveUrl ?: normalizedPrimary ?: ""}/user/settings/applications")
                                    ),
                                    "打开 Gitea"
                                )
                            )
                        }
                    },
                    enabled = probeResult != null
                )
            }
        }

        if (error != null) {
            VerticalGap(14.dp)
            InlineNotice(text = error!!)
        }
        VerticalGap(28.dp)
    }
}

// ---- 私有辅助 ----

/** 去尾部斜杠；要求 http/https 开头。 */
private fun normalizeUrl(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        if (trimmed.length > 8) trimmed else null
    } else null
}

/** 主/备各自独立探测结果：仅通过的地址允许落盘，二者互不覆盖。 */
private data class ProbeOutcome(
    val activeUrl: String,
    val primaryOk: Boolean,
    val backupOk: Boolean,
    val version: String
)

/**
 * 分别探测主地址与备地址（不再只探测"最终生效"的那个）：
 * 主通过 → active=主；否则备通过 → active=备；两者都不通过则抛出主地址的异常。
 * 主通过时仍会独立探测备地址，以便保存语义正确。
 */
private fun probeBoth(client: GiteaClient, primary: String, backup: String?): ProbeOutcome {
    var primaryErr: ApiException? = null
    var primaryOk = false
    var version: String? = null
    try {
        version = client.probe(primary)
        primaryOk = true
    } catch (e: ApiException) {
        primaryErr = e
    }
    var backupOk = false
    var backupVersion: String? = null
    if (backup != null) {
        backupVersion = runCatching { client.probe(backup) }.getOrNull()
        backupOk = backupVersion != null
    }
    val active = when {
        primaryOk -> primary
        backupOk && backup != null -> backup
        else -> throw (primaryErr ?: ApiException(ErrKind.CONNECTION, "地址不可达"))
    }
    return ProbeOutcome(active, primaryOk, backupOk, version ?: backupVersion ?: "?")
}

/** 认证核心（P1/P6）：密码引导建 ro/rw 双令牌，或校验粘贴令牌，全部通过后落盘。 */
private fun authenticate(
    client: GiteaClient,
    settings: SettingsStore,
    activeUrl: String,
    savePrimaryUrl: String?,
    saveBackupUrl: String?,
    authMode: AuthMode,
    reauth: Boolean,
    username: String,
    password: String,
    pastedToken: String,
    displayName: String,
    onStatus: (String) -> Unit
) {
    val roToken: String
    val rwToken: String
    var roName = ""
    var rwName = ""

    when (authMode) {
        AuthMode.PASSWORD_BOOTSTRAP -> {
            val stamp = SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date())
            roName = "selfgit-ro-$stamp"
            rwName = "selfgit-rw-$stamp"

            val report: (String) -> Unit = { s -> onStatus("引导中：$s") }

            report("创建只读令牌")
            roToken = createTokenWithRetry(client, activeUrl, username, password, roName,
                listOf("read:repository", "read:user"))

            report("创建读写令牌")
            // write:user：新建仓库 POST /user/repos 要求该 scope（write:repository 只覆盖已有仓库写操作）
            rwToken = createTokenWithRetry(client, activeUrl, username, password, rwName,
                listOf("read:repository", "write:repository", "write:user", "read:user"))
        }
        AuthMode.PASTE_TOKEN -> {
            onStatus("校验令牌")
            client.verifyToken(activeUrl, pastedToken)
            roToken = pastedToken
            rwToken = pastedToken
            roName = "pasted-token"
            rwName = "pasted-token"
        }
    }

    onStatus("自校验令牌")
    client.verifyToken(activeUrl, roToken)

    // 全部成功才落盘（先令牌后配置；密码从未接触存储）
    onStatus("保存凭证")
    settings.roToken = roToken
    settings.rwToken = rwToken
    settings.lastGoodUrl = activeUrl

    if (!reauth) {
        settings.host = HostConfig(
            displayName = displayName.ifBlank { displayName0(activeUrl) },
            // 主/备分离保存：未通过探测的地址不写入，绝不把备地址回填为主地址
            primaryUrl = savePrimaryUrl.orEmpty(),
            backupUrl = saveBackupUrl.orEmpty(),
            username = username,
            authMode = authMode,
            roTokenName = roName,
            rwTokenName = rwName,
            createdAt = System.currentTimeMillis()
        )
    } else {
        // reauth：保留原主机配置（主备地址原样保留），仅当用户改走粘贴令牌时更新认证方式元数据
        settings.host = settings.host?.copy(authMode = authMode, roTokenName = roName, rwTokenName = rwName)
    }
}

/** 创建令牌：重名时自动追加随机后缀重试一次（§3.5）。 */
private fun createTokenWithRetry(
    client: GiteaClient,
    url: String,
    username: String,
    password: String,
    name: String,
    scopes: List<String>
): String {
    return try {
        client.createToken(url, username, password, name, scopes)
    } catch (e: ApiException) {
        if (client.isTokenNameConflict(e)) {
            client.createToken(url, username, password, "$name-${(1000..9999).random()}", scopes)
        } else {
            throw e
        }
    }
}

private fun displayName0(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
