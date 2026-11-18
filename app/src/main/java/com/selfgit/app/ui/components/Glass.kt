package com.selfgit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfgit.app.ui.theme.LocalPalette

/** 无涟漪点击，避免破坏玻璃质感。 */
@Composable
fun Modifier.clickableNoRipple(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * 玻璃面板：半透明渐变 + 高光描边 + 柔和投影 + 顶部反光。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    strong: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPalette.current
    val top = if (strong) palette.glassStrongTop else palette.glassTop
    val bottom = if (strong) palette.glassStrongBottom else palette.glassBottom

    val borderBrush = Brush.linearGradient(
        listOf(
            palette.glassBorder,
            palette.glassBorder.copy(alpha = palette.glassBorder.alpha * 0.25f)
        )
    )

    var surface = modifier
        .shadow(
            elevation = if (palette.dark) 12.dp else 18.dp,
            shape = shape,
            clip = false,
            ambientColor = palette.shadow,
            spotColor = palette.shadow
        )
    if (onClick != null) {
        surface = surface.clickableNoRipple(onClick = onClick)
    }
    surface = surface
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        // 顶部高光线：drawBehind 只画不参与测量，面板宽度由内容决定（可 wrap-content）
        .drawBehind {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = if (palette.dark) 0.18f else 0.75f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, 1.dp.toPx())
            )
        }
        .border(1.dp, borderBrush, shape)

    Box(surface) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** 小圆角标签。 */
@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    icon: ImageVector? = null
) {
    val palette = LocalPalette.current
    val color = tint ?: palette.textSecondary
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (palette.dark) 0.16f else 0.10f))
            .border(1.dp, color.copy(alpha = 0.18f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp
        )
    }
}

/** 页面顶部标题区。 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    eyebrow: String = "SelfGit · 私有应用管理",
    badge: String? = null,
    badgeTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** 标题同行右侧插槽（如搜索入口图标）。 */
    titleTrailing: (@Composable () -> Unit)? = null,
    subtitleContent: (@Composable () -> Unit)? = null
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = eyebrow,
                color = palette.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .height(1.dp)
                    .width(26.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(palette.primary.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
            )
            Spacer(Modifier.weight(1f))
            if (badge != null) GlassBadge(text = badge, tint = badgeTint)
            if (trailing != null) trailing()
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (titleTrailing != null) {
                Spacer(Modifier.width(12.dp))
                titleTrailing()
            }
        }
        Spacer(Modifier.height(6.dp))
        if (subtitleContent != null) {
            subtitleContent()
        } else {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
    }
}

/** 卡片内的小标题 + 可选的右侧操作。 */
@Composable
fun SectionRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    val palette = LocalPalette.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(palette.primary.copy(alpha = if (palette.dark) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textTertiary
                )
            }
        }
        if (action != null) action()
    }
}

@Composable
fun HairLine(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.divider)
    )
}

@Composable
fun VerticalGap(height: Dp) = Spacer(Modifier.height(height))
