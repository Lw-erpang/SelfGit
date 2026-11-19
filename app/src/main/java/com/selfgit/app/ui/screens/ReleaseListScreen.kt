package com.selfgit.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.selfgit.app.data.TrackingStore
import com.selfgit.app.net.GiteaAsset
import com.selfgit.app.net.GiteaReadme
import com.selfgit.app.net.GiteaRelease
import com.selfgit.app.net.GiteaRepo
import com.selfgit.app.net.VersionCompare
import com.selfgit.app.ui.DownloadUi
import com.selfgit.app.ui.components.DetailTopBar
import com.selfgit.app.ui.components.GlassBadge
import com.selfgit.app.ui.components.GlassPanel
import com.selfgit.app.ui.components.HairLine
import com.selfgit.app.ui.components.MarkdownText
import com.selfgit.app.ui.components.VerticalGap
import com.selfgit.app.ui.components.clickableNoRipple
import com.selfgit.app.ui.navigation.TrackNavBarScroll
import com.selfgit.app.ui.theme.LocalPalette
import java.io.File

/** 附件大小格式化。 */
fun formatSize(bytes: Long): String = when {
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun isApk(name: String): Boolean = name.endsWith(".apk", ignoreCase = true)

/** 仓库详情页板块。 */
enum class RepoSection(val label: String) {
    DETAIL("应用详情"),
    RELEASES("版本")
}

/**
 * 仓库详情页（UI 文档 S2）：固定标题栏 + 板块切换（应用详情 / 版本）。
 * 应用详情：描述、仓库信息、README（Markdown 渲染）；
 * 版本：版本时间线，默认展开第一个（最新），描述按 Markdown 渲染。
 *
 * resolveIcon：本机未安装该应用时的远端图标回退（avatar_url / 根目录 icon.png），
 * 返回缓存文件或 null；图标获取失败静默降级为占位符。
 */
@Composable
fun ReleaseListScreen(
    repo: GiteaRepo,
    releases: List<GiteaRelease>,
    loading: Boolean,
    error: String?,
    readme: GiteaReadme?,
    readmeLoading: Boolean,
    readmeError: String?,
    tracking: TrackingStore,
    appMetaPkg: String? = null,
    downloads: Map<Long, DownloadUi>,
    onBack: () -> Unit,
    onDownload: (GiteaRelease, GiteaAsset) -> Unit,
    onRetry: (GiteaRelease, GiteaAsset) -> Unit,
    onInstall: (File) -> Unit,
    resolveIcon: (suspend (GiteaRepo) -> File?)? = null,
    imageLoader: (suspend (String) -> ByteArray?)? = null
) {
    val palette = LocalPalette.current
    val detailScroll = rememberScrollState()
    val releasesScroll = rememberScrollState()
    var section by remember { mutableStateOf(RepoSection.DETAIL) }
    TrackNavBarScroll(if (section == RepoSection.DETAIL) detailScroll else releasesScroll)

    // 展开状态：默认只展开第一个（最新）版本
    val expanded = remember(releases) {
        mutableStateMapOf<String, Boolean>().apply {
            releases.firstOrNull()?.let { put(it.tagName, true) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = repo.name,
            subtitle = repo.fullName,
            onBack = onBack,
            trailing = {
                if (releases.isNotEmpty()) {
                    GlassBadge(text = "${releases.size} 个版本", tint = palette.primary)
                }
            }
        )

        // ---- 板块切换（均分两栏）----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            RepoSection.entries.forEachIndexed { index, sec ->
                if (index > 0) Spacer(Modifier.width(10.dp))
                SectionTab(
                    label = sec.label,
                    selected = section == sec,
                    onClick = { section = sec },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (section) {
            RepoSection.DETAIL -> DetailSection(
                repo = repo,
                releases = releases,
                tracking = tracking,
                appMetaPkg = appMetaPkg,
                readme = readme,
                readmeLoading = readmeLoading,
                readmeError = readmeError,
                scroll = detailScroll,
                resolveIcon = resolveIcon,
                imageLoader = imageLoader
            )

            RepoSection.RELEASES -> ReleasesSection(
                releases = releases,
                loading = loading,
                error = error,
                expanded = expanded,
                downloads = downloads,
                scroll = releasesScroll,
                onDownload = onDownload,
                onRetry = onRetry,
                onInstall = onInstall
            )
        }
    }
}

/** 板块切换 Tab（均分两栏，玻璃分段样式）。 */
@Composable
private fun SectionTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val background by animateColorAsState(
        targetValue = if (selected) {
            palette.primary.copy(alpha = if (palette.dark) 0.28f else 0.15f)
        } else {
            palette.fieldFill
        },
        label = "sectionTabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) palette.primary else palette.textSecondary,
        label = "sectionTabFg"
    )

    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(
                1.dp,
                if (selected) palette.primary.copy(alpha = 0.35f) else palette.fieldBorder,
                RoundedCornerShape(14.dp)
            )
            .clickableNoRipple(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/** 应用详情板块：应用头卡（图标+名称）、仓库信息、README。 */
@Composable
private fun DetailSection(
    repo: GiteaRepo,
    releases: List<GiteaRelease>,
    tracking: TrackingStore,
    appMetaPkg: String? = null,
    readme: GiteaReadme?,
    readmeLoading: Boolean,
    readmeError: String?,
    scroll: ScrollState,
    resolveIcon: (suspend (GiteaRepo) -> File?)? = null,
    imageLoader: (suspend (String) -> ByteArray?)? = null
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val latestTag = remember(releases) { releases.firstOrNull()?.tagName }
    val appInfo = remember(repo.fullName, appMetaPkg) {
        (tracking.get(repo.fullName)?.packageName ?: appMetaPkg)?.let { pkg ->
            runCatching {
                val pm = context.packageManager
                val ai = pm.getApplicationInfo(pkg, 0)
                Triple(
                    pm.getApplicationIcon(ai),
                    pm.getApplicationLabel(ai).toString(),
                    pm.getPackageInfo(pkg, 0).versionName
                )
            }.getOrNull()
        }
    }

    // 图标三级回退：1) 本机已装应用（追踪映射 / app.json 包名）→ 2) 远端（avatar_url / 根目录 icon.*）→ 3) 占位符
    var iconBitmap by remember(repo.fullName) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(repo.fullName, appMetaPkg) {
        // 外部安装的应用（app.json 匹配）同样能拿到本机图标
        val installedPkg = tracking.get(repo.fullName)?.packageName ?: appMetaPkg
        val installed = installedPkg?.let { pkg ->
            runCatching {
                context.packageManager.getApplicationIcon(pkg).toBitmap()
            }.getOrNull()
        }
        if (installed != null) {
            iconBitmap = installed
            return@LaunchedEffect
        }
        val cached = resolveIcon?.invoke(repo)
        if (cached != null) {
            iconBitmap = android.graphics.BitmapFactory.decodeFile(cached.absolutePath)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp)
    ) {
        VerticalGap(16.dp)

        // ---- 应用卡（图标 + 名称 + 仓库信息，合并为一张，占满宽）----
        GlassPanel(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.fieldFill)
                        .border(1.dp, palette.fieldBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = iconBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "应用图标",
                            modifier = Modifier.size(38.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Android,
                            contentDescription = null,
                            tint = palette.textTertiary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = appInfo?.second ?: repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = repo.fullName,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    // 徽章独立一行：已装版本 + 最新标识（已是最新 / 最新 vX），FlowRow 防错位挤压
                    if (appInfo?.third != null || releases.isNotEmpty()) {
                        Spacer(Modifier.height(5.dp))
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            appInfo?.third?.let { installed ->
                                GlassBadge(text = "已装 v$installed", tint = palette.success)
                            }
                            latestTag?.let { latest ->
                                val installedV = appInfo?.third
                                val isUpToDate = installedV != null && VersionCompare.compare(
                                    VersionCompare.extract(installedV),
                                    VersionCompare.extract(latest)
                                ) >= 0
                                if (installedV == null) {
                                    GlassBadge(text = "最新 $latest", tint = palette.primary)
                                } else if (isUpToDate) {
                                    GlassBadge(text = "最新", tint = palette.success)
                                } else {
                                    GlassBadge(text = "最新 $latest", tint = palette.accent)
                                }
                            }
                        }
                    }
                }
            }
            VerticalGap(12.dp)
            HairLine()
            VerticalGap(12.dp)
            InfoRow("最新版本", releases.firstOrNull()?.tagName ?: "暂无发布")
            VerticalGap(8.dp)
            InfoRow("版本数", if (releases.isEmpty()) "0" else "${releases.size}")
            VerticalGap(8.dp)
            InfoRow("最近更新", relativeTime(repo.updatedAt))
            VerticalGap(8.dp)
            InfoRow("可见性", if (repo.isPrivate) "私有" else "公开")
        }
        VerticalGap(12.dp)

        // ---- 仓库描述 ----
        if (repo.description.isNotBlank()) {
            GlassPanel(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    lineHeight = 18.sp
                )
            }
            VerticalGap(12.dp)
        }

        // ---- README（直接渲染内容，不显示标题字样）----
        GlassPanel(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
            when {
                readmeLoading -> Text(
                    "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textTertiary
                )

                readmeError != null -> Text(
                    readmeError,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.danger
                )

                readme == null -> Text(
                    "该仓库没有 README 文档",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textTertiary
                )

                else -> MarkdownText(
                    markdown = readme.content,
                    imageLoader = imageLoader
                )
            }
        }
        VerticalGap(96.dp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.textTertiary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textPrimary
        )
    }
}

/** 版本板块：原版本时间线。 */
@Composable
private fun ReleasesSection(
    releases: List<GiteaRelease>,
    loading: Boolean,
    error: String?,
    expanded: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    downloads: Map<Long, DownloadUi>,
    scroll: ScrollState,
    onDownload: (GiteaRelease, GiteaAsset) -> Unit,
    onRetry: (GiteaRelease, GiteaAsset) -> Unit,
    onInstall: (File) -> Unit
) {
    val palette = LocalPalette.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp)
    ) {
        VerticalGap(16.dp)

        when {
            loading -> Text(
                "加载中…",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textTertiary
            )

            error != null -> GlassPanel(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.danger
                )
            }

            releases.isEmpty() -> GlassPanel(
                contentPadding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "该仓库还没有任何 Release",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textTertiary
                )
            }

            else -> releases.forEach { release ->
                val isExpanded = expanded[release.tagName] == true
                ReleaseCard(
                    release = release,
                    expanded = isExpanded,
                    onToggle = { expanded[release.tagName] = !isExpanded },
                    downloads = downloads,
                    onDownload = onDownload,
                    onRetry = onRetry,
                    onInstall = onInstall
                )
                VerticalGap(12.dp)
            }
        }
        VerticalGap(96.dp)
    }
}

@Composable
private fun ReleaseCard(
    release: GiteaRelease,
    expanded: Boolean,
    onToggle: () -> Unit,
    downloads: Map<Long, DownloadUi>,
    onDownload: (GiteaRelease, GiteaAsset) -> Unit,
    onRetry: (GiteaRelease, GiteaAsset) -> Unit,
    onInstall: (File) -> Unit
) {
    val palette = LocalPalette.current
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "chevron"
    )
    val hasBody = release.body.isNotBlank()

    GlassPanel(
                contentPadding = PaddingValues(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
        // ---- 卡片头：点击折叠/展开 ----
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple(enabled = hasBody, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = release.tagName,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (hasBody) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (expanded) "收起" else "更新内容",
                            fontSize = 10.5.sp,
                            color = palette.textTertiary
                        )
                    }
                }
                if (release.name != release.tagName) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = release.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "发布于 ${relativeTime(release.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary
                )
            }
            if (hasBody) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起更新内容" else "展开更新内容",
                    tint = palette.textTertiary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevron)
                )
            }
        }

        // ---- 更新内容（Markdown）----
        if (hasBody) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(100))
            ) {
                Column {
                    VerticalGap(10.dp)
                    HairLine()
                    VerticalGap(10.dp)
                    MarkdownText(markdown = release.body)
                }
            }
        }

        // ---- 附件（常驻可见）----
        if (release.assets.isNotEmpty()) {
            VerticalGap(12.dp)
            HairLine()
            VerticalGap(10.dp)
            release.assets.forEach { asset ->
                AssetRow(
                    release = release,
                    asset = asset,
                    state = downloads[asset.id],
                    onDownload = onDownload,
                    onRetry = onRetry,
                    onInstall = onInstall
                )
                VerticalGap(6.dp)
            }
        }
    }
}

@Composable
private fun AssetRow(
    release: GiteaRelease,
    asset: GiteaAsset,
    state: DownloadUi?,
    onDownload: (GiteaRelease, GiteaAsset) -> Unit,
    onRetry: (GiteaRelease, GiteaAsset) -> Unit,
    onInstall: (File) -> Unit
) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassBadge(text = asset.name, tint = if (isApk(asset.name)) palette.primary else null)
                Text(
                    text = " · " + formatSize(asset.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            when (val s = state) {
                is DownloadUi.Downloading -> {
                    val pct = if (s.total > 0) (s.received * 100 / s.total).toInt() else 0
                    Text(
                        text = if (pct > 0) "下载中 $pct%" else "下载中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                is DownloadUi.Failed -> {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.danger,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                else -> {}
            }
        }

        val s = state
        when {
            s is DownloadUi.Downloading -> {
                val pct = if (s.total > 0) (s.received * 100 / s.total).toInt() else 0
                GlassBadge(
                    text = if (pct > 0) "$pct%" else "…",
                    tint = palette.primary
                )
            }

            s is DownloadUi.Failed -> {
                GlassBadge(
                    text = "重试",
                    tint = palette.danger,
                    modifier = Modifier.clickableNoRipple { onRetry(release, asset) }
                )
            }

            s is DownloadUi.Done -> {
                GlassBadge(
                    text = "安装",
                    tint = palette.success,
                    modifier = Modifier.clickableNoRipple { onInstall(s.file) }
                )
            }

            !isApk(asset.name) -> {
                GlassBadge(text = "不可安装", tint = palette.textTertiary)
            }

            else -> {
                GlassBadge(
                    text = "下载",
                    tint = palette.primary,
                    modifier = Modifier.clickableNoRipple { onDownload(release, asset) }
                )
            }
        }
    }
}
