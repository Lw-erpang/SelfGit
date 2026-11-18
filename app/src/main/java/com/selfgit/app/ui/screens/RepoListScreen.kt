package com.selfgit.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfgit.app.net.GiteaRepo
import com.selfgit.app.net.VersionCompare
import com.selfgit.app.ui.components.GlassBadge
import com.selfgit.app.ui.components.GlassPanel
import com.selfgit.app.ui.components.HairLine
import com.selfgit.app.ui.components.ScreenHeader
import com.selfgit.app.ui.components.VerticalGap
import com.selfgit.app.ui.components.clickableNoRipple
import com.selfgit.app.ui.navigation.TrackNavBarScroll
import com.selfgit.app.ui.theme.LocalPalette
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** 追踪状态角标：已装 + 与最新 Release 对比。 */
@Composable
private fun TrackBadge(latestTag: String?, installedVersion: String?) {
    if (installedVersion == null) return
    val palette = LocalPalette.current
    if (latestTag == null) {
        GlassBadge(text = "已装", tint = palette.textSecondary)
        return
    }
    val cmp = VersionCompare.compare(
        VersionCompare.extract(installedVersion),
        VersionCompare.extract(latestTag)
    )
    when {
        cmp >= 0 -> GlassBadge(text = "已是最新", tint = palette.success)
        else -> GlassBadge(text = "有更新 $latestTag", tint = palette.accent)
    }
}

/** 相对时间格式化（数据时间标注，§6.1）。 */
fun relativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val t = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        val diff = System.currentTimeMillis() - t
        val minutes = diff / 60_000
        when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 60 * 24 -> "${minutes / 60} 小时前"
            else -> "${minutes / 60 / 24} 天前"
        }
    } catch (_: Exception) {
        iso.take(10)
    }
}

/**
 * 仓库列表页（UI 文档 S1）：登录后的全量仓库。
 * 阶段 1：对已追踪的仓库显示"已是最新 / 有更新"角标（§11.3 更新角标）。
 * 第五轮：关键词搜索 + 置顶/取消置顶（库数量增多后快速查找）。
 */
@Composable
fun RepoListScreen(
    repos: List<GiteaRepo>,
    activeUrl: String?,
    dataTimeText: String,
    refreshing: Boolean,
    latestTags: Map<String, String>,
    installedVersions: Map<String, String>,
    pinned: Set<String>,
    onTogglePin: (GiteaRepo) -> Unit,
    onRefresh: () -> Unit,
    onOpenRepo: (GiteaRepo) -> Unit
) {
    val palette = LocalPalette.current
    val scroll = rememberScrollState()
    TrackNavBarScroll(scroll)
    var query by remember { mutableStateOf("") }
    // 搜索默认隐藏，点标题行搜索图标展开；收起时清空关键词
    var showSearch by remember { mutableStateOf(false) }

    // 关键词过滤（名称/全名/描述，忽略大小写）
    val filtered = remember(repos, query) {
        val q = query.trim()
        if (q.isEmpty()) repos
        else repos.filter { r ->
            r.name.contains(q, true) ||
                r.fullName.contains(q, true) ||
                r.description.contains(q, true)
        }
    }
    // 置顶在前（保持原有更新时间排序），其余在后
    val sorted = remember(filtered, pinned) {
        filtered.sortedBy { if (pinned.contains(it.fullName)) 0 else 1 }
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp)
    ) {
        VerticalGap(18.dp)
        ScreenHeader(
            title = "我的仓库",
            subtitle = "登录账号下的全量项目",
            badge = if (repos.isNotEmpty()) "${repos.size} 个" else null,
            titleTrailing = {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = if (showSearch) "收起搜索" else "搜索仓库",
                    tint = if (showSearch || query.isNotBlank()) palette.primary else palette.textTertiary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickableNoRipple {
                            showSearch = !showSearch
                            if (!showSearch) query = ""
                        }
                )
            }
        )
        VerticalGap(14.dp)
        com.selfgit.app.ui.components.StatusPill(
            activeUrl = activeUrl,
            dataTimeText = dataTimeText,
            refreshing = refreshing,
            onRefresh = onRefresh
        )
        VerticalGap(12.dp)

        // ---- 搜索框（默认隐藏，点搜索图标展开）----
        androidx.compose.animation.AnimatedVisibility(visible = showSearch) {
            Column {
                com.selfgit.app.ui.components.GlassTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "",
                    placeholder = "搜索仓库名或描述…",
                    leadingIcon = Icons.Filled.Search,
                    singleLine = true,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                )
            }
        }
        if (showSearch) VerticalGap(12.dp)

        if (sorted.isEmpty() && !refreshing) {
            GlassPanel(contentPadding = PaddingValues(20.dp)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VerticalGap(12.dp)
                    Text(
                        if (query.isBlank()) "暂无仓库" else "没有匹配「$query」的仓库",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (query.isBlank()) "下拉刷新，或检查账号下是否有项目" else "试试其他关键词",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textTertiary
                    )
                    VerticalGap(12.dp)
                }
            }
        }

        sorted.forEach { repo ->
            val isPinned = pinned.contains(repo.fullName)
            GlassPanel(
                contentPadding = PaddingValues(18.dp),
                onClick = { onOpenRepo(repo) }
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.padding(0.dp).size(22.dp)
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            text = repo.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        // 徽章独立成行（FlowRow 自动换行），避免挤压标题导致错位
                        if (isPinned || repo.isPrivate ||
                            (latestTags[repo.fullName] != null && installedVersions[repo.fullName] != null)
                        ) {
                            Spacer(Modifier.height(4.dp))
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isPinned) {
                                    GlassBadge(text = "置顶", icon = Icons.Filled.PushPin, tint = palette.accent)
                                }
                                if (repo.isPrivate) {
                                    GlassBadge(text = "私有", icon = Icons.Filled.Lock)
                                }
                                TrackBadge(
                                    latestTag = latestTags[repo.fullName],
                                    installedVersion = installedVersions[repo.fullName]
                                )
                            }
                        }
                        if (repo.description.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = repo.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                                maxLines = 2,
                                lineHeight = 17.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "更新于 ${relativeTime(repo.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary
                        )
                    }
                    // 置顶切换（嵌套可点击，消费点击不冒泡给卡片）
                    androidx.compose.material3.Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "取消置顶" else "置顶",
                        tint = if (isPinned) palette.accent else palette.textTertiary,
                        modifier = Modifier
                            .size(22.dp)
                            .clickableNoRipple { onTogglePin(repo) }
                    )
                }
            }
            VerticalGap(12.dp)
        }
        VerticalGap(16.dp)
        HairLine()
        VerticalGap(90.dp) // 底部导航留白
    }
}
