package com.selfgit.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.selfgit.app.data.SettingsStore
import com.selfgit.app.data.TrackingStore
import com.selfgit.app.net.ApiException
import com.selfgit.app.net.ApkInstaller
import com.selfgit.app.net.GiteaClient
import com.selfgit.app.net.GiteaRelease
import com.selfgit.app.net.GiteaRepo
import androidx.compose.material3.Text
import com.selfgit.app.ui.components.AuroraBackground
import com.selfgit.app.ui.navigation.Destination
import com.selfgit.app.ui.navigation.FloatingNavBar
import com.selfgit.app.ui.navigation.LocalNavBarController
import com.selfgit.app.ui.navigation.NavBarController
import com.selfgit.app.ui.screens.HostLoginScreen
import com.selfgit.app.ui.screens.ReleaseListScreen
import com.selfgit.app.ui.screens.RepoListScreen
import com.selfgit.app.ui.screens.SettingsScreen
import com.selfgit.app.ui.screens.UploadScreen
import com.selfgit.app.ui.theme.LocalPalette
import com.selfgit.app.ui.theme.SelfGitTheme
import com.selfgit.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 附件下载状态（keyed by assetId）。 */
sealed class DownloadUi {
    data class Downloading(val received: Long, val total: Long) : DownloadUi()
    data class Done(val file: File) : DownloadUi()
    data class Failed(val message: String) : DownloadUi()
}

/**
 * App 壳：无后台同步（§6）——
 * 启动/回前台防抖刷新（60s），前台手动刷新；导航为 应用/设置 双 Tab + Release 子页。
 * 阶段 1：附件下载 → APK 解析 → 系统安装 → 追踪映射；系统返回（按钮/手势）可逐级退出。
 */
@Composable
fun SelfGitApp() {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context.applicationContext) }
    val tracking = remember { TrackingStore(context.applicationContext) }
    val uploadHistory = remember { com.selfgit.app.data.UploadHistoryStore(context.applicationContext) }
    val pinnedStore = remember { com.selfgit.app.data.PinnedStore(context.applicationContext) }
    var pinnedRepos by remember { mutableStateOf(pinnedStore.list()) }
    val iconCacheDir = remember { File(context.cacheDir, "repo_icons") }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    val client = remember { GiteaClient() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Destination.APPS) }
    var showReauth by remember { mutableStateOf(false) }
    var openedRepo by remember { mutableStateOf<GiteaRepo?>(null) }

    // 系统分享 APK → 自动切到上传页并装载
    val shareUri by com.selfgit.app.ShareInbox.uri.collectAsState()
    LaunchedEffect(shareUri) {
        if (shareUri != null) tab = Destination.UPLOAD
    }

    // ---- 数据状态 ----
    var repos by remember { mutableStateOf<List<GiteaRepo>>(emptyList()) }
    var releases by remember { mutableStateOf<List<GiteaRelease>>(emptyList()) }
    var releasesLoading by remember { mutableStateOf(false) }
    var releasesError by remember { mutableStateOf<String?>(null) }
    // 应用详情板块：仓库 README
    var readme by remember { mutableStateOf<com.selfgit.app.net.GiteaReadme?>(null) }
    var readmeLoading by remember { mutableStateOf(false) }
    var readmeError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var activeUrl by remember { mutableStateOf<String?>(null) }
    var lastRefreshAt by remember { mutableStateOf(settings.lastRefreshAt) }
    var needsAuth by remember { mutableStateOf(settings.roToken == null || settings.host == null) }

    // ---- 阶段 1：追踪与下载 ----
    var latestTags by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var installedVersions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // 仓库 → 本机包名（app.json 约定文件）：让外部安装的应用也能显示已装/更新角标
    var appMeta by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val downloads = remember { mutableStateMapOf<Long, DownloadUi>() }

    // ---- 阶段 3：安装前校验弹窗（签名不一致 / 降级，§11.5）----
    data class InstallWarning(val file: File, val lines: List<String>)
    var installWarning by remember { mutableStateOf<InstallWarning?>(null) }

    val dataTimeText: String = remember(lastRefreshAt) {
        if (lastRefreshAt == 0L) "无缓存" else
            "数据 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastRefreshAt)) +
                if (lastError != null) "（刷新失败，缓存）" else ""
    }

    /** 实时核对安装状态：包名 = 追踪映射（SelfGit 下载过）→ app.json（外部安装），
     *  已装（含外部安装）即显示版本，供主页"已装 / 有更新"角标判定。 */
    fun refreshInstalled() {
        val map = mutableMapOf<String, String>()
        for (repo in repos) {
            val tracked = tracking.get(repo.fullName)?.packageName
            val pkg = tracked ?: appMeta[repo.fullName] ?: continue
            ApkInstaller.installedVersion(context, pkg)?.let { map[repo.fullName] = it }
        }
        installedVersions = map
    }

    /** 并行拉取各仓库最新 Release tag + app.json 包名（失败静默，不影响主列表）。 */
    fun fetchLatestTags(list: List<GiteaRepo>, url: String, token: String) {
        scope.launch {
            val deferred = list.map { repo ->
                async(Dispatchers.IO) {
                    val tag = try {
                        client.latestRelease(url, token, repo.owner, repo.name)?.tagName
                    } catch (_: ApiException) {
                        null
                    }
                    val meta = if (tracking.get(repo.fullName) == null) {
                        client.getAppMeta(url, token, repo.owner, repo.name)
                            ?.optString("packageName")
                            ?.takeIf { it.isNotBlank() }
                    } else null
                    Triple(repo.fullName, tag, meta)
                }
            }
            val results = deferred.awaitAll()
            latestTags = results.mapNotNull { (k, v, _) -> v?.let { k to it } }.toMap()
            appMeta = results.mapNotNull { (k, _, m) -> m?.let { k to it } }.toMap()
            android.util.Log.d("SelfGit", "fetchLatestTags done: tags=$latestTags meta=$appMeta")
            refreshInstalled()
        }
    }

    // ---- 刷新管线（防抖 60s，§6.2）----
    fun refresh(force: Boolean) {
        val host = settings.host
        val token = settings.roToken
        android.util.Log.d("SelfGit", "refresh(force=$force): host=${host != null} token=${token != null} lastRefreshAt=$lastRefreshAt")
        if (host == null || token == null) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAt < SettingsStore.REFRESH_DEBOUNCE_MS) return

        scope.launch {
            refreshing = true
            lastError = null
            try {
                val url = withContext(Dispatchers.IO) {
                    resolveUrl(client, settings, host)
                }
                android.util.Log.d("SelfGit", "refresh: resolved url=$url")
                activeUrl = url
                val list = withContext(Dispatchers.IO) {
                    client.listMyRepos(url, token)
                }
                android.util.Log.d("SelfGit", "refresh: got ${list.size} repos")
                repos = list
                lastRefreshAt = now
                settings.lastRefreshAt = now
                fetchLatestTags(list, url, token)
            } catch (e: ApiException) {
                android.util.Log.d("SelfGit", "refresh failed: ${e.message}")
                lastError = e.message
                if (e.kind == com.selfgit.app.net.ErrKind.AUTH) {
                    needsAuth = true
                    showReauth = true
                }
            } catch (e: Exception) {
                android.util.Log.d("SelfGit", "refresh crashed: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                refreshing = false
            }
        }
    }

    // 启动刷新：进程重启后无内存缓存，列表为空时强制刷新（防抖只对有缓存时生效）
    LaunchedEffect(needsAuth) {
        if (!needsAuth) refresh(force = repos.isEmpty())
    }

    // 回前台刷新：仅在离开前台超过防抖阈值后触发（§6.4）；
    // 短暂回前台（如从安装器返回）只核对追踪安装状态，不打网络。
    val lifecycleOwner = LocalLifecycleOwner.current
    var backgroundedAt by remember { mutableStateOf(0L) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME -> {
                    refreshInstalled()
                    if (backgroundedAt > 0 &&
                        System.currentTimeMillis() - backgroundedAt > SettingsStore.REFRESH_DEBOUNCE_MS
                    ) {
                        refresh(force = false)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- 系统返回（按钮/手势统一处理）----
    // 在 Release 子页时返回应回到仓库列表；重新认证页返回取消回设置。
    BackHandler(enabled = openedRepo != null) {
        openedRepo = null
    }
    BackHandler(enabled = showReauth && openedRepo == null) {
        // 取消重新认证：令牌仍在，直接回到原界面
        showReauth = false
        needsAuth = false
    }

    // ---- 阶段 3：安装路由（先校验签名/降级，再按所选后端分发）----

    /** 按设置中的安装方式分发；结果用 Toast 反馈（回退/静默结果/失败点）。 */
    fun doInstall(file: File) {
        val type = settings.installerType
        val thirdPkg = settings.thirdPartyPkg
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                com.selfgit.app.net.InstallRouter.install(context, file, type, thirdPkg)
            }
            android.util.Log.d("SelfGit", "install: requested=${outcome.requested} used=${outcome.used} ok=${outcome.success} detail=${outcome.detail}")
            when {
                outcome.fellBack ->
                    Toast.makeText(context, "已回退系统安装器：${outcome.detail}", Toast.LENGTH_LONG).show()
                !outcome.success ->
                    Toast.makeText(context, outcome.detail ?: "安装失败", Toast.LENGTH_LONG).show()
                outcome.used == com.selfgit.app.data.InstallerType.SHIZUKU ||
                    outcome.used == com.selfgit.app.data.InstallerType.ROOT ->
                    Toast.makeText(context, "静默安装成功", Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }
    }
    /** 安装入口：签名不一致或降级时先弹警告，确认后再执行安装。 */
    fun installApk(file: File) {
        scope.launch {
            val checks = withContext(Dispatchers.IO) {
                val info = ApkInstaller.parse(context, file)
                val lines = mutableListOf<String>()
                if (info != null) {
                    val installedSig = ApkInstaller.installedSignatureSha256(context, info.packageName)
                    val apkSig = ApkInstaller.apkSignatureSha256(context, file)
                    if (installedSig != null && apkSig != null && installedSig != apkSig) {
                        lines += "签名不一致：覆盖安装将失败，需先卸载已装版本（对应仓库内该应用的其他安装包也可能同签名）"
                    }
                    val installed = ApkInstaller.installedVersion(context, info.packageName)
                    if (installed != null && info.versionName != null) {
                        val cmp = com.selfgit.app.net.VersionCompare.compare(
                            com.selfgit.app.net.VersionCompare.extract(installed),
                            com.selfgit.app.net.VersionCompare.extract(info.versionName)
                        )
                        if (cmp > 0) lines += "降级安装：已装 v$installed，目标 v${info.versionName} 更低，系统可能拒绝安装"
                    }
                }
                lines
            }
            if (checks.isNotEmpty()) {
                installWarning = InstallWarning(file, checks)
            } else {
                doInstall(file)
            }
        }
    }


    // ---- 附件下载（阶段 1，P3 流程）----

    /**
     * 复制文件到公共下载目录 Downloads/SelfGit/（MediaStore，API 29+）。
     * 同名旧文件先删除再写入（每次下载视为覆盖）。低版本返回 null（保留私有目录）。
     * @return 展示用相对路径（如 "Download/SelfGit/xxx.apk"），失败 null。
     */
    fun copyToPublicDownloads(src: File, name: String): String? {
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        return runCatching {
            val resolver = context.contentResolver
            val downloads = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val relPath = android.os.Environment.DIRECTORY_DOWNLOADS + "/SelfGit"
            val proj = arrayOf(android.provider.MediaStore.MediaColumns._ID)
            // 清除同名旧条目（覆盖语义）
            resolver.query(
                downloads, proj,
                "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${android.provider.MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf("$relPath/", name), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    runCatching {
                        resolver.delete(
                            android.content.ContentUris.withAppendedId(downloads, id), null, null
                        )
                    }
                }
            }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            }
            val uri = resolver.insert(downloads, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: return null
            "$relPath/$name"
        }.getOrNull()
    }

    fun downloadAsset(repo: GiteaRepo, release: GiteaRelease, assetId: Long, assetName: String) {
        val url = activeUrl ?: return
        val token = settings.roToken ?: return
        if (downloads[assetId] is DownloadUi.Downloading) return
        val dest = File(context.getExternalFilesDir("apks"), assetName)

        downloads[assetId] = DownloadUi.Downloading(0, 0)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client.downloadAsset(
                        url, token, repo.owner, repo.name, release.id, assetId, dest
                    ) { received, total ->
                        downloads[assetId] = DownloadUi.Downloading(received, total)
                    }
                }
                // 解析并记录追踪映射（安装状态以 PackageManager 实时核对为准）
                val info = ApkInstaller.parse(context, dest)
                if (info != null) {
                    tracking.put(
                        repo.fullName,
                        TrackingStore.Entry(
                            packageName = info.packageName,
                            versionName = info.versionName,
                            latestTag = release.tagName,
                            installedAt = System.currentTimeMillis()
                        )
                    )
                }
                // 同步一份到公共 Downloads/SelfGit/（API 29+），方便文件管理器查看与备份
                val publicPath = withContext(Dispatchers.IO) {
                    copyToPublicDownloads(dest, assetName)
                }
                downloads[assetId] = DownloadUi.Done(dest)
                withContext(Dispatchers.Main) {
                    if (publicPath != null) {
                        Toast.makeText(context, "已保存到 $publicPath", Toast.LENGTH_LONG).show()
                    }
                    installApk(dest)
                }
            } catch (e: ApiException) {
                downloads[assetId] = DownloadUi.Failed(e.message ?: "下载失败")
            } catch (e: Exception) {
                downloads[assetId] = DownloadUi.Failed("下载失败：${e.message ?: "未知错误"}")
            }
        }
    }


    fun reDownload(repo: GiteaRepo, release: GiteaRelease, assetId: Long, assetName: String) {
        downloads.remove(assetId)
        downloadAsset(repo, release, assetId, assetName)
    }

    /** 应用详情图标远端回退：avatar_url / 根目录 icon.*，落盘缓存后返回文件（失败 null）。 */
    suspend fun resolveRepoIcon(repo: GiteaRepo): File? {
        val url = activeUrl ?: return null
        val token = settings.roToken ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                client.getRepoIconFile(url, token, repo.owner, repo.name, repo.avatarUrl, iconCacheDir)
            }.getOrNull()
        }
    }

    SelfGitTheme(mode = themeMode) {
        val navController = remember { NavBarController() }
        // 切 Tab / 进出二级页时导航栏复位为显示
        LaunchedEffect(tab, openedRepo) { navController.show() }

        CompositionLocalProvider(LocalNavBarController provides navController) {
        Box(
            Modifier
                .fillMaxSize()
        ) {
            AuroraBackground()

            if (needsAuth) {
                // 登录 / 重新认证（重新认证可取消返回原界面）
                HostLoginScreen(
                    settings = settings,
                    reauth = showReauth,
                    onDone = {
                        needsAuth = false
                        showReauth = false
                        refresh(force = true)
                    },
                    onCancel = if (showReauth) {
                        {
                            showReauth = false
                            needsAuth = false
                        }
                    } else null
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        val repo = openedRepo
                        if (repo != null) {
                            ReleaseListScreen(
                                repo = repo,
                                releases = releases,
                                loading = releasesLoading,
                                error = releasesError,
                                readme = readme,
                                readmeLoading = readmeLoading,
                                readmeError = readmeError,
                                tracking = tracking,
                                appMetaPkg = appMeta[repo.fullName],
                                downloads = downloads,
                                onBack = { openedRepo = null },
                                onDownload = { release, asset ->
                                    downloadAsset(repo, release, asset.id, asset.name)
                                },
                                onRetry = { release, asset ->
                                    reDownload(repo, release, asset.id, asset.name)
                                },
                                onInstall = { file -> installApk(file) },
                                resolveIcon = { repo -> resolveRepoIcon(repo) }
                            )
                        } else {
                            when (tab) {
                                Destination.APPS -> RepoListScreen(
                                    repos = repos,
                                    activeUrl = activeUrl,
                                    dataTimeText = dataTimeText,
                                    refreshing = refreshing,
                                    latestTags = latestTags,
                                    installedVersions = installedVersions,
                                    pinned = pinnedRepos,
                                    onTogglePin = { r ->
                                        pinnedRepos = pinnedStore.list()
                                        pinnedStore.toggle(r.fullName)
                                        pinnedRepos = pinnedStore.list()
                                    },
                                    onRefresh = { refresh(force = true) },
                                    onOpenRepo = { r ->
                                        openedRepo = r
                                        releases = emptyList()
                                        releasesError = null
                                        releasesLoading = true
                                        readme = null
                                        readmeError = null
                                        readmeLoading = true
                                        scope.launch {
                                            try {
                                                val url = activeUrl
                                                    ?: withContext(Dispatchers.IO) {
                                                        resolveUrl(client, settings, settings.host!!)
                                                    }
                                                activeUrl = url
                                                val token = settings.roToken ?: return@launch
                                                // Release 列表与 README 并行拉取
                                                val relDeferred = async(Dispatchers.IO) {
                                                    client.listReleases(url, token, r.owner, r.name)
                                                }
                                                val rmDeferred = async(Dispatchers.IO) {
                                                    runCatching {
                                                        client.getReadme(url, token, r.owner, r.name)
                                                    }.getOrNull()
                                                }
                                                releases = relDeferred.await()
                                                readme = rmDeferred.await()
                                            } catch (e: ApiException) {
                                                releasesError = e.message
                                            } finally {
                                                releasesLoading = false
                                                readmeLoading = false
                                            }
                                        }
                                    }
                                )
                                Destination.UPLOAD -> UploadScreen(
                                    repos = repos,
                                    tracking = tracking,
                                    settings = settings,
                                    client = client,
                                    activeUrl = activeUrl,
                                    history = uploadHistory,
                                    onUploaded = { refresh(force = true) },
                                    shareUri = shareUri,
                                    onShareConsumed = { com.selfgit.app.ShareInbox.uri.value = null }
                                )
                                Destination.SETTINGS -> SettingsScreen(
                                    settings = settings,
                                    onThemeChange = {
                                        themeMode = it
                                        settings.themeMode = it
                                    },
                                    onReauth = {
                                        showReauth = true
                                        needsAuth = true
                                    },
                                    onReset = {
                                        settings.clearAll()
                                        repos = emptyList()
                                        releases = emptyList()
                                        activeUrl = null
                                        lastRefreshAt = 0
                                        needsAuth = true
                                        showReauth = false
                                    }
                                )
                            }
                        }
                    }

                    // 真悬浮：导航栏叠在内容之上，向下滚动隐藏、向上滚动显示
                    AnimatedVisibility(
                        visible = !navController.hidden,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp),
                        enter = slideInVertically(tween(240)) { it } + fadeIn(tween(200)),
                        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(140))
                    ) {
                        FloatingNavBar(
                            selected = tab,
                            onSelect = {
                                tab = it
                                openedRepo = null
                                navController.show()
                            },
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                    }
                }
            }
        }
        }
    }

    // ---- 安装前警告弹窗（签名不一致 / 降级，§11.5）----
    installWarning?.let { warn ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { installWarning = null },
            title = { Text("安装前提示") },
            text = {
                Column {
                    warn.lines.forEach {
                        Text(it, fontSize = 13.sp, color = Color(0xFFB3261E))
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val f = warn.file
                    installWarning = null
                    doInstall(f)
                }) { Text("仍要安装") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { installWarning = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 地址解析（§5.2 记忆优先）：最后成功地址 → 主 → 备。
 * 仅连接类失败触发切换；AUTH 失败直接抛出（P5）。
 */
private suspend fun resolveUrl(
    client: GiteaClient,
    settings: SettingsStore,
    host: com.selfgit.app.data.HostConfig
): String = withContext(Dispatchers.IO) {
    val candidates = buildList {
        settings.lastGoodUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
        // 主/备独立：任一为空白（未配置或未通过探测）时跳过，不影响另一个
        if (host.primaryUrl.isNotBlank()) add(host.primaryUrl)
        if (host.backupUrl.isNotBlank()) add(host.backupUrl)
    }.distinct()

    var lastConnError: ApiException? = null
    for (url in candidates) {
        try {
            client.probe(url)
            settings.lastGoodUrl = url
            return@withContext url
        } catch (e: ApiException) {
            when (e.kind) {
                com.selfgit.app.net.ErrKind.AUTH,
                com.selfgit.app.net.ErrKind.FORBIDDEN -> throw e
                else -> lastConnError = e
            }
        }
    }
    throw lastConnError ?: ApiException(
        com.selfgit.app.net.ErrKind.CONNECTION,
        "主机不可达：主地址与备地址均失败"
    )
}
