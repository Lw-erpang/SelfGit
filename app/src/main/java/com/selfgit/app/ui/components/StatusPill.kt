package com.selfgit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfgit.app.ui.theme.LocalPalette

/**
 * 状态条（UI 文档 S1）：生效地址 + 数据时间 + 手动刷新入口。
 */
@Composable
fun StatusPill(
    activeUrl: String?,
    dataTimeText: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(CircleShape)
            .background(palette.fieldFill)
            .border(1.dp, palette.fieldBorder, CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        refreshing -> palette.accent
                        activeUrl != null -> palette.success
                        else -> palette.danger
                    }
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = activeUrl ?: "未连接",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "·",
            color = palette.textTertiary,
            fontSize = 11.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = dataTimeText,
            color = palette.textTertiary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.Refresh,
            contentDescription = if (refreshing) "刷新中" else "刷新",
            tint = if (refreshing) palette.textTertiary else palette.primary,
            modifier = Modifier
                .size(16.dp)
                .clickableNoRipple(enabled = !refreshing, onClick = onRefresh)
        )
    }
}

/** 卡片内的提示条（错误 / 信息 / 成功）。 */
@Composable
fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color? = null
) {
    val palette = LocalPalette.current
    val color = tone ?: palette.danger
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(color.copy(alpha = if (palette.dark) 0.14f else 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}
