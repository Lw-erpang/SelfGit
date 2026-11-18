package com.selfgit.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfgit.app.data.SettingsStore
import com.selfgit.app.data.TrackingStore
import com.selfgit.app.net.ApiException
import com.selfgit.app.net.ApkInfo
import com.selfgit.app.net.ApkInstaller
import com.selfgit.app.net.ErrKind
import com.selfgit.app.net.GiteaClient
import com.selfgit.app.net.GiteaRelease
import com.selfgit.app.net.GiteaRepo
import com.selfgit.app.ui.components.GlassPanel
import com.selfgit.app.ui.components.GlassBadge
import com.selfgit.app.ui.components.ChoiceChip
import com.selfgit.app.ui.components.GhostButton
import com.selfgit.app.ui.components.GlassTextField
import com.selfgit.app.ui.components.MiniButton
import com.selfgit.app.ui.components.PrimaryButton
import com.selfgit.app.ui.components.ScreenHeader
import com.selfgit.app.ui.components.SectionRow
import com.selfgit.app.ui.components.clickableNoRipple
import com.selfgit.app.ui.navigation.TrackNavBarScroll
import com.selfgit.app.ui.theme.LocalPalette
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 上传执行状态。 */
sealed class UploadUi {
    data object Idle : UploadUi()
    data class Uploading(val step: String, val sent: Long, val total: Long, val startMs: Long) : UploadUi()
    data class Success(val release: GiteaRelease) : UploadUi()
    data class Failed(val message: String, val emptyRelease: GiteaRelease?) : UploadUi()
}

/** tag 查重状态。 */
sealed class TagCheck {
    data object None : TagCheck()
    data object Checking : TagCheck()
    data object Free : TagCheck()
    data class Conflict(val tag: String) : TagCheck()
}

/** 目标仓库模式：新建（默认，一应用一库） / 放入已有仓库（如 apps 孤品档）。 */
enum class TargetMode(val label: String) {
    NEW("新建仓库"),
    EXISTING("已有仓库")
}

private val REPO_NAME_RE = Regex("^[A-Za-z0-9_.-]{1,100}$")
private val CHANGELOG_TEMPLATE = "- 本次更新内容\n- 修复的问题"

/** 由应用名/包名推导合法仓库名（Gitea 仅允许字母数字 ._ -）。 */
private fun defaultRepoName(info: ApkInfo): String {
    val cleaned = (info.label ?: "")
        .replace(Regex("[^A-Za-z0-9_.-]+"), "-")
        .trim('-', '.', '_')
    if (cleaned.isNotEmpty()) return cleaned
    return info.packageName.substringAfterLast('.').ifBlank { "app" }
}

/**
 * S3 上传页（§8.1 P4）：选文件 → 解析 → 归属 → 发布信息 → 上传执行。
 * 上传用 rw 令牌（缺省回退 ro），tag 冲突直接拦截不覆盖。
 * 上传结果写入本地记录并在页底展示（最近 10 条）。
 */
@Composable
fun UploadScreen(
    repos: List<GiteaRepo>,
    tracking: TrackingStore,
    settings: SettingsStore,
    client: GiteaClient,
    activeUrl: String?,
    history: com.selfgit.app.data.UploadHistoryStore,
    onUploaded: () -> Unit,
    shareUri: Uri? = null,
    onShareConsumed: () -> Unit = {}
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    TrackNavBarScroll(scroll)

    // ---- 步骤 1：选文件 ----
    var apkFile by remember { mutableStateOf<File?>(null) }
    var apkInfo by remember { mutableStateOf<ApkInfo?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    // ---- 步骤 2/3：归属 ----
    var targetMode by remember { mutableStateOf(TargetMode.NEW) }
    var targetRepo by remember { mutableStateOf<GiteaRepo?>(null) }
    var newRepoName by remember { mutableStateOf("") }
    var newRepoPrivate by remember { mutableStateOf(true) }
    var tagText by remember { mutableStateOf("") }
    var changelog by remember { mutableStateOf(CHANGELOG_TEMPLATE) }
    var tagCheck by remember { mutableStateOf<TagCheck>(TagCheck.None) }

    // ---- 步骤 4：上传执行 ----
    var upload by remember { mutableStateOf<UploadUi>(UploadUi.Idle) }
    var showCleanup by remember { mutableStateOf(false) }
    var uploadedRepo by remember { mutableStateOf<GiteaRepo?>(null) }
    // 取消支持：持有进行中的 OkHttp Call，取消按钮触发 call.cancel() 中断上传
    var currentCall by remember { mutableStateOf<okhttp3.Call?>(null) }
    var cancelled by remember { mutableStateOf(false) }

    // ---- 上传记录（本地持久化）----
    var records by remember { mutableStateOf(history.list()) }

    // ---- 已装应用选择对话框 ----
    var showAppPicker by remember { mutableStateOf(false) }

    fun recordResult(file: File, info: ApkInfo?, repo: GiteaRepo?, tag: String, ok: Boolean, msg: String) {
        history.add(
            com.selfgit.app.data.UploadHistoryStore.Record(
                fileName = file.name,
                appLabel = info?.label ?: "",
                packageName = info?.packageName ?: "",
                versionName = info?.versionName ?: "",
                tag = tag,
                repoFullName = repo?.fullName ?: "",
                sizeBytes = file.length(),
                timeMs = System.currentTimeMillis(),
                success = ok,
                message = msg
            )
        )
        records = history.list()
    }

    /** 清空表单回初始态（选新文件 / 取消上传 / 取消已选文件共用）。 */
    fun resetForm() {
        apkFile = null
        apkInfo = null
        parseError = null
        targetMode = TargetMode.NEW
        targetRepo = null
        newRepoName = ""
        newRepoPrivate = true
        tagText = ""
        changelog = CHANGELOG_TEMPLATE
        tagCheck = TagCheck.None
        upload = UploadUi.Idle
        showCleanup = false
        uploadedRepo = null
        currentCall = null
        cancelled = false
    }

    /** 解析并填充归属表单（协程内调用）。 */
    suspend fun parseAndFill(dest: File) {
        apkFile = dest
        val info = withContext(Dispatchers.IO) { ApkInstaller.parse(context, dest) }
        if (info == null) {
            parseError = "无法读取应用信息：可能不是标准 APK"
        } else {
            apkInfo = info
            val suggested = repos.firstOrNull {
                tracking.get(it.fullName)?.packageName == info.packageName
            }
            // 追踪过该包名 → 默认放入已有仓库；否则默认新建仓库
            if (suggested != null) {
                targetMode = TargetMode.EXISTING
                targetRepo = suggested
            } else {
                targetMode = TargetMode.NEW
            }
            newRepoName = defaultRepoName(info)
            tagText = defaultTag(info)
        }
    }

    fun handleUri(uri: Uri) {
        resetForm()
        onShareConsumed()
        scope.launch {
            try {
                val dest = withContext(Dispatchers.IO) {
                    copyUriToCache(context, uri)
                }
                parseAndFill(dest)
            } catch (e: Exception) {
                parseError = "读取文件失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /** 从已装应用上传：拷贝其 sourceDir（base.apk）到缓存后走统一解析链。 */
    fun handleInstalledApp(sourceDir: String, label: String) {
        resetForm()
        showAppPicker = false
        scope.launch {
            try {
                val dest = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
                    val target = File(dir, "$label.apk")
                    File(sourceDir).copyTo(target, overwrite = true)
                    target
                }
                parseAndFill(dest)
            } catch (e: Exception) {
                parseError = "读取应用安装包失败：${e.message ?: "未知错误"}（该应用可能为 Split APK，仅能提取 base.apk）"
            }
        }
    }

    // 系统分享（ACTION_SEND APK 流）注入：进入页面即自动装载
    LaunchedEffect(shareUri) {
        if (shareUri != null) handleUri(shareUri)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        handleUri(uri)
    }

    // tag 查重（700ms 防抖）；新建仓库模式 tag 必然可用（auto_init 空库无 tag）
    LaunchedEffect(apkInfo, targetMode, targetRepo, newRepoName, tagText) {
        val info = apkInfo
        val repo = targetRepo
        val url = activeUrl
        val token = settings.rwToken ?: settings.roToken
        if (info == null || url == null || token == null || tagText.isBlank()) {
            tagCheck = TagCheck.None
            return@LaunchedEffect
        }
        if (upload is UploadUi.Uploading) return@LaunchedEffect
        if (targetMode == TargetMode.NEW) {
            tagCheck = if (REPO_NAME_RE.matches(newRepoName.trim())) TagCheck.Free else TagCheck.None
            return@LaunchedEffect
        }
        if (repo == null) {
            tagCheck = TagCheck.None
            return@LaunchedEffect
        }
        tagCheck = TagCheck.Checking
        delay(700)
        try {
            val exists = withContext(Dispatchers.IO) {
                client.getTag(url, token, repo.owner, repo.name, tagText) != null
            }
            tagCheck = if (exists) TagCheck.Conflict(tagText) else TagCheck.Free
        } catch (_: ApiException) {
            tagCheck = TagCheck.None
        }
    }

    /** 取消按钮：上传中 → 中断网络请求；空闲 → 清空表单。 */
    fun onCancelPress() {
        if (upload is UploadUi.Uploading) {
            cancelled = true
            currentCall?.cancel()
        } else {
            resetForm()
        }
    }

    fun startUpload() {
        val info = apkInfo ?: return
        val file = apkFile ?: return
        val url = activeUrl ?: return
        val token = settings.rwToken ?: settings.roToken ?: return
        val tag = tagText.trim()
        if (tag.isBlank()) return
        if (tagCheck is TagCheck.Conflict) return
        if (targetMode == TargetMode.NEW && !REPO_NAME_RE.matches(newRepoName.trim())) return
        if (targetMode == TargetMode.EXISTING && targetRepo == null) return

        val body = buildReleaseBody(changelog, info)
        val startMs = System.currentTimeMillis()
        upload = UploadUi.Uploading("创建 Release…", 0, 0, startMs)
        scope.launch {
            try {
                // 新建仓库（auto_init 生成 README，空仓库无法建 Release）；失败即终止
                val repo: GiteaRepo = if (targetMode == TargetMode.NEW) {
                    upload = UploadUi.Uploading(
                        "创建仓库 ${newRepoName.trim()}…", 0, 0, startMs
                    )
                    withContext(Dispatchers.IO) {
                        client.createRepo(
                            url, token,
                            name = newRepoName.trim(),
                            description = info.label ?: "",
                            isPrivate = newRepoPrivate
                        )
                    }
                } else {
                    targetRepo!!
                }
                uploadedRepo = repo
                upload = UploadUi.Uploading("创建 Release…", 0, 0, startMs)
                val release = withContext(Dispatchers.IO) {
                    client.createRelease(url, token, repo.owner, repo.name, tag, tag, body)
                }
                // 附件上传：失败重试 ≤ 2 次（P4 第 5 步）
                var asset: com.selfgit.app.net.GiteaAsset? = null
                var lastError: ApiException? = null
                for (attempt in 0..2) {
                    if (cancelled) break
                    try {
                        upload = UploadUi.Uploading(
                            if (attempt == 0) "上传附件…" else "重试上传附件（第 $attempt/2 次）…",
                            0, file.length(), startMs
                        )
                        asset = withContext(Dispatchers.IO) {
                            client.uploadAsset(
                                url, token, repo.owner, repo.name, release.id, file,
                                onProgress = { sent, total ->
                                    upload = UploadUi.Uploading(
                                        if (attempt == 0) "上传附件…" else "重试上传附件（第 $attempt/2 次）…",
                                        sent, total, startMs
                                    )
                                },
                                onCall = { currentCall = it }
                            )
                        }
                        break
                    } catch (e: ApiException) {
                        lastError = e
                        if (cancelled || e.kind == ErrKind.AUTH || e.kind == ErrKind.FORBIDDEN) break
                    }
                }
                if (cancelled) {
                    // 用户取消：不记录、清理表单回初始态
                    resetForm()
                    cancelled = false
                    return@launch
                }
                if (asset != null) {
                    upload = UploadUi.Success(release)
                    recordResult(file, info, repo, tag, ok = true, msg = "")
                    onUploaded()
                    // 补写 app.json（已装检测约定文件；已存在则跳过，失败静默）
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            client.ensureAppMeta(
                                url, token, repo.owner, repo.name,
                                packageName = info.packageName,
                                displayName = info.label ?: ""
                            )
                        }
                    }
                } else {
                    val failMsg = lastError?.message ?: "附件上传失败（已重试 2 次）"
                    upload = UploadUi.Failed(failMsg, release)
                    recordResult(file, info, repo, tag, ok = false, msg = failMsg)
                    showCleanup = true
                }
            } catch (e: ApiException) {
                if (cancelled) {
                    resetForm()
                    cancelled = false
                    return@launch
                }
                val hint = when {
                    e.message?.contains("empty", ignoreCase = true) == true ->
                        "仓库为空：请先在网页端初始化仓库（自动创建 README）后重试"
                    e.message?.contains("409", false) == true ||
                        e.message?.contains("already", ignoreCase = true) == true ->
                        "仓库 ${newRepoName.trim()} 已存在：请换个名称，或改用「已有仓库」模式"
                    else -> e.message ?: "上传失败"
                }
                upload = UploadUi.Failed(hint, null)
                recordResult(
                    file, info,
                    if (targetMode == TargetMode.EXISTING) targetRepo else null,
                    tag, ok = false, msg = hint
                )
            }
        }
    }

    fun cleanupEmptyRelease() {
        val failed = upload as? UploadUi.Failed ?: return
        val release = failed.emptyRelease ?: return
        val repo = uploadedRepo ?: targetRepo ?: return
        val url = activeUrl ?: return
        val token = settings.rwToken ?: settings.roToken ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client.deleteRelease(url, token, repo.owner, repo.name, release.id)
                }
            } catch (_: ApiException) { /* 清理失败保留 Release，由用户网页端处理 */ }
            showCleanup = false
            upload = UploadUi.Idle
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        ScreenHeader(
            title = "上传",
            subtitle = "从手机上传 APK 到 Gitea Release，自动聚合历史版本"
        )
        Spacer(Modifier.height(20.dp))

        // ---- 卡片 1：选择文件 ----
        GlassPanel(strong = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            if (apkFile == null) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(palette.primary.copy(alpha = if (palette.dark) 0.24f else 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CloudUpload,
                            contentDescription = null,
                            tint = palette.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "选择要上传的 APK 文件",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支持系统文件选择器（SAF）",
                        fontSize = 12.sp,
                        color = palette.textTertiary
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        "选择 APK 文件",
                        onClick = {
                            picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.CloudUpload
                    )
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        "从已装应用选择",
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Android
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Android,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            apkFile!!.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            fmtSize(apkFile!!.length()),
                            fontSize = 11.5.sp,
                            color = palette.textTertiary
                        )
                    }
                    MiniButton("重选", onClick = {
                        picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                    }, icon = Icons.Filled.Refresh)
                }
            }
        }

        // ---- 卡片 2：解析结果 ----
        if (parseError != null) {
            Spacer(Modifier.height(14.dp))
            GlassPanel(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                SectionRow("解析失败", subtitle = parseError, icon = Icons.Filled.Android)
                Spacer(Modifier.height(12.dp))
                GhostButton("重新选择", onClick = {
                    parseError = null
                    apkFile = null
                }, danger = true)
            }
        } else if (apkInfo != null) {
            Spacer(Modifier.height(14.dp))
            GlassPanel(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                SectionRow(
                    title = apkInfo!!.label ?: "未命名应用",
                    subtitle = apkInfo!!.packageName,
                    icon = Icons.Filled.Android
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassBadge("v${apkInfo!!.versionName ?: "?"}")
                    GlassBadge("versionCode ${apkInfo!!.versionCode}")
                    GlassBadge(
                        apkInfo!!.abis.takeIf { it.isNotEmpty() }?.joinToString("/") ?: "universal"
                    )
                }
            }
        }

        // ---- 卡片 3+4：归属与发布信息（解析成功后展开）----
        if (apkInfo != null && (repos.isNotEmpty() || targetMode == TargetMode.NEW)) {
            Spacer(Modifier.height(14.dp))
            GlassPanel(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                SectionRow(
                    "目标仓库",
                    subtitle = when (targetMode) {
                        TargetMode.NEW -> "默认一应用一库（推荐）"
                        TargetMode.EXISTING ->
                            if (targetRepo != null &&
                                tracking.get(targetRepo!!.fullName)?.packageName == apkInfo!!.packageName
                            ) "按包名匹配已有条目" else "从仓库列表中选择"
                    },
                    icon = Icons.Filled.Android
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetMode.entries.forEach { mode ->
                        ChoiceChip(
                            text = mode.label,
                            selected = targetMode == mode,
                            enabled = upload !is UploadUi.Uploading,
                            onClick = { targetMode = mode }
                        )
                    }
                }

                if (targetMode == TargetMode.NEW) {
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it },
                        label = "新仓库名称",
                        placeholder = "如 DiceRoller",
                        enabled = upload !is UploadUi.Uploading,
                        singleLine = true,
                        error = if (newRepoName.isNotBlank() && !REPO_NAME_RE.matches(newRepoName.trim()))
                            "仅限字母/数字/._-（不超过 100 位）" else null,
                        helper = "自动创建私有仓库并初始化 README，一应用一库"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            text = "私有",
                            selected = newRepoPrivate,
                            enabled = upload !is UploadUi.Uploading,
                            onClick = { newRepoPrivate = true }
                        )
                        ChoiceChip(
                            text = "公开",
                            selected = !newRepoPrivate,
                            enabled = upload !is UploadUi.Uploading,
                            onClick = { newRepoPrivate = false }
                        )
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    RepoPickRow(repos, targetRepo, enabled = upload !is UploadUi.Uploading) { picked ->
                        targetRepo = picked
                    }
                }

                Spacer(Modifier.height(14.dp))
                GlassTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = "Tag（模板：应用名-v版本）",
                    placeholder = "应用名-v1.0.0",
                    enabled = upload !is UploadUi.Uploading,
                    singleLine = true
                )
                TagCheckRow(tagCheck, tagText)
                Spacer(Modifier.height(10.dp))
                GlassTextField(
                    value = changelog,
                    onValueChange = { changelog = it },
                    label = "修改日志（写入 Release 描述）",
                    enabled = upload !is UploadUi.Uploading,
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6
                )
                Spacer(Modifier.height(14.dp))

                when (val u = upload) {
                    is UploadUi.Uploading -> {
                        UploadProgressCard(u)
                        Spacer(Modifier.height(12.dp))
                    }
                    is UploadUi.Success -> {
                        SuccessCard(u)
                        Spacer(Modifier.height(12.dp))
                    }
                    else -> {
                        val canUpload = tagText.isNotBlank() && tagCheck is TagCheck.Free &&
                            upload is UploadUi.Idle &&
                            when (targetMode) {
                                TargetMode.NEW -> REPO_NAME_RE.matches(newRepoName.trim())
                                TargetMode.EXISTING -> targetRepo != null
                            }
                        // 上传 / 取消 双按钮（各占一半宽度）
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryButton(
                                "上传",
                                onClick = { startUpload() },
                                icon = Icons.Filled.CloudUpload,
                                enabled = canUpload,
                                modifier = Modifier.weight(1f)
                            )
                            GhostButton(
                                text = "取消",
                                onClick = { onCancelPress() },
                                enabled = upload is UploadUi.Idle && apkFile != null ||
                                    upload is UploadUi.Uploading ||
                                    upload is UploadUi.Failed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (tagCheck is TagCheck.Checking) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "正在检查 tag…",
                                fontSize = 11.5.sp,
                                color = palette.textTertiary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        } else if (apkInfo != null && repos.isEmpty() && targetMode == TargetMode.EXISTING) {
            Spacer(Modifier.height(14.dp))
            GlassPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                SectionRow(
                    "仓库列表为空",
                    subtitle = "可改用「新建仓库」模式，或先在应用页刷新",
                    icon = Icons.Filled.Android
                )
            }
        }

        // ---- 上传记录（本地留存，最近 10 条）----
        if (records.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            GlassPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionRow(
                        "上传记录",
                        subtitle = "本机留存最近 ${records.size} 条（上限 30）",
                        icon = Icons.Filled.CloudUpload,
                        modifier = Modifier.weight(1f)
                    )
                    MiniButton("清空", onClick = {
                        history.clear()
                        records = emptyList()
                    }, tone = palette.danger)
                }
                Spacer(Modifier.height(10.dp))
                records.take(10).forEachIndexed { index, r ->
                    if (index > 0) {
                        Spacer(Modifier.height(8.dp))
                    }
                    UploadRecordRow(r)
                }
            }
        }

        Spacer(Modifier.height(26.dp))
    }

    // ---- 已装应用选择弹窗 ----
    if (showAppPicker) {
        InstalledAppPickerDialog(
            onPick = { app -> handleInstalledApp(app.sourceDir, app.label) },
            onDismiss = { showAppPicker = false }
        )
    }

    // ---- 失败清理弹窗（P4 第 6 步）----
    if (showCleanup) {
        val failed = upload as? UploadUi.Failed
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCleanup = false },
            title = { Text("附件上传失败") },
            text = {
                Column {
                    Text(failed?.message ?: "")
                    if (failed?.emptyRelease != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Release「${failed.emptyRelease.tagName}」已创建但没有附件。建议删除空 Release 后重试。",
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (failed?.emptyRelease != null) {
                    MiniButton("删除空 Release", onClick = { cleanupEmptyRelease() }, tone = palette.danger)
                }
            },
            dismissButton = {
                MiniButton("保留待补传", onClick = { showCleanup = false })
            }
        )
    }
}

/** 上传记录单行：状态点 + 应用名/tag/仓库 + 时间/大小或失败原因。 */
@Composable
private fun UploadRecordRow(r: com.selfgit.app.data.UploadHistoryStore.Record) {
    val palette = LocalPalette.current
    val timeText = remember(r.timeMs) {
        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(r.timeMs))
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (r.success) palette.success else palette.danger)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = r.appLabel.ifEmpty { r.fileName.removeSuffix(".apk") },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(r.tag)
                    if (r.repoFullName.isNotBlank()) append(" → ").append(r.repoFullName)
                },
                fontSize = 11.sp,
                color = palette.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!r.success && r.message.isNotBlank()) {
                Text(
                    text = r.message,
                    fontSize = 10.5.sp,
                    color = palette.danger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeText,
                fontSize = 10.5.sp,
                color = palette.textTertiary
            )
            Text(
                text = if (r.success) fmtSize(r.sizeBytes) else "失败",
                fontSize = 10.5.sp,
                color = if (r.success) palette.textTertiary else palette.danger
            )
        }
    }
}

@Composable
private fun UploadProgressCard(state: UploadUi.Uploading) {
    val palette = LocalPalette.current
    val percent = if (state.total > 0) (state.sent * 100 / state.total).toInt() else 0
    val elapsed = ((System.currentTimeMillis() - state.startMs).coerceAtLeast(1)) / 1000f
    val speed = if (state.sent > 0) state.sent / elapsed else 0.0
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.step,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$percent%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primary
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.fieldFill)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.primary)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${fmtSize(state.sent)} / ${fmtSize(state.total)} · ${fmtSize(speed.toLong())}/s",
            fontSize = 11.sp,
            color = palette.textTertiary
        )
    }
}

@Composable
private fun SuccessCard(state: UploadUi.Success) {
    val palette = LocalPalette.current
    GlassPanel(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        SectionRow(
            "上传成功",
            subtitle = "${state.release.tagName} 已发布",
            icon = Icons.Filled.CloudUpload
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Release 已进入该仓库的历史版本列表",
            fontSize = 12.sp,
            color = palette.success
        )
    }
}

@Composable
private fun RepoPickRow(
    repos: List<GiteaRepo>,
    selected: GiteaRepo?,
    enabled: Boolean,
    onPick: (GiteaRepo) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repos.take(8).forEach { repo ->
            val isSelected = repo.fullName == selected?.fullName
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) LocalPalette.current.primary.copy(alpha = 0.12f)
                        else LocalPalette.current.fieldFill
                    )
                    .border(
                        1.dp,
                        if (isSelected) LocalPalette.current.primary.copy(alpha = 0.45f)
                        else LocalPalette.current.fieldBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickableNoRipple(enabled = enabled) { onPick(repo) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    repo.fullName,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) LocalPalette.current.primary else LocalPalette.current.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) GlassBadge("目标", tint = LocalPalette.current.primary)
            }
        }
        if (repos.size > 8) {
            Text(
                "其余 ${repos.size - 8} 个仓库未显示，请先在网页端管理",
                fontSize = 11.sp,
                color = LocalPalette.current.textTertiary
            )
        }
    }
}

@Composable
private fun TagCheckRow(check: TagCheck, tag: String) {
    val palette = LocalPalette.current
    when (check) {
        is TagCheck.Conflict -> Row(
            Modifier.padding(start = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "版本 $tag 已存在，不可覆盖（覆盖属 PC 端显式操作）",
                fontSize = 11.5.sp,
                color = palette.danger
            )
        }
        is TagCheck.Free -> Text(
            "tag 可用",
            fontSize = 11.5.sp,
            color = palette.success,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )
        else -> {}
    }
}

/** 已装应用条目（对话框行）。 */
private data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val sourceDir: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?
)

/**
 * 已装应用选择对话框：列出非系统应用（可切换显示系统应用），
 * 选中后提取其 base.apk（sourceDir）上传。Split APK 应用仅能提取 base 包。
 */
@Composable
private fun InstalledAppPickerDialog(
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var includeSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val apps = remember(includeSystem) {
        val pm = context.packageManager
        runCatching {
            pm.getInstalledPackages(android.content.pm.PackageManager.GET_META_DATA)
                .mapNotNull { pi ->
                    val ai = pi.applicationInfo ?: return@mapNotNull null
                    val label = runCatching { ai.loadLabel(pm).toString() }.getOrNull() ?: return@mapNotNull null
                    val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!includeSystem && isSystem) return@mapNotNull null
                    if (pi.packageName == context.packageName) return@mapNotNull null
                    val icon = runCatching {
                        ai.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                    }.getOrNull()
                    InstalledApp(
                        label = label,
                        packageName = pi.packageName,
                        versionName = pi.versionName,
                        sourceDir = ai.sourceDir ?: return@mapNotNull null,
                        icon = icon
                    )
                }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    com.selfgit.app.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "从已装应用选择",
        text = {
            Column {
                // 搜索框（紧凑样式）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.fieldFill)
                        .border(1.dp, palette.fieldBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = palette.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.5.sp,
                            color = palette.textPrimary
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("搜索应用名或包名", fontSize = 13.5.sp, color = palette.textTertiary)
                            }
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChoiceChip(
                        "含系统应用",
                        selected = includeSystem,
                        onClick = { includeSystem = !includeSystem }
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "共 ${apps.size} 个",
                        fontSize = 11.sp,
                        color = palette.textTertiary
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (apps.isEmpty()) {
                    Text(
                        "未获取到应用列表：请在系统设置中允许 SelfGit「获取应用列表」权限\n（MIUI：设置 → 应用设置 → 应用管理 → SelfGit → 权限管理 → 获取应用列表 → 允许）",
                        fontSize = 12.sp,
                        color = palette.danger
                    )
                } else {
                    val filtered = if (query.isBlank()) apps else apps.filter {
                        it.label.contains(query, ignoreCase = true) ||
                            it.packageName.contains(query, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            "没有匹配的应用",
                            fontSize = 12.sp,
                            color = palette.textTertiary,
                            modifier = Modifier.padding(vertical = 14.dp)
                        )
                    }
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filtered.size) { i ->
                            val app = filtered[i]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickableNoRipple { onPick(app) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 应用图标（无图标时占位）
                                if (app.icon != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = app.icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(palette.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Android,
                                            contentDescription = null,
                                            tint = palette.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        app.packageName,
                                        fontSize = 10.5.sp,
                                        color = palette.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (!app.versionName.isNullOrBlank()) {
                                    GlassBadge("v${app.versionName}")
                                }
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

// ---- 辅助 ----

private fun defaultTag(info: ApkInfo): String {
    val name = (info.label ?: "app").replace(Regex("\\s+"), "")
    return "$name-v${info.versionName ?: "1.0"}"
}

private fun buildReleaseBody(changelog: String, info: ApkInfo): String {
    val meta = buildString {
        appendLine("---")
        appendLine("- 包名：${info.packageName}")
        appendLine("- versionCode：${info.versionCode}")
        appendLine("- 架构：${info.abis.takeIf { it.isNotEmpty() }?.joinToString("/") ?: "universal"}")
    }
    return changelog.trim().ifEmpty { CHANGELOG_TEMPLATE } + "\n\n" + meta
}

private fun fmtSize(bytes: Long): String = when {
    bytes >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576f)
    bytes >= 1 shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}

/** SAF Uri → cache/uploads/<display name>，重名追加时间戳。 */
private fun copyUriToCache(context: android.content.Context, uri: Uri): File {
    val resolver = context.contentResolver
    var name = "upload.apk"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val n = cursor.getString(0)
            if (!n.isNullOrBlank()) name = n
        }
    }
    if (!name.endsWith(".apk", ignoreCase = true)) name += ".apk"

    val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
    var dest = File(dir, name)
    if (dest.exists()) {
        dest = File(dir, SimpleDateFormat("HHmmss", Locale.US).format(Date()) + "_" + name)
    }
    resolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    } ?: throw java.io.IOException("无法打开所选文件")
    return dest
}
