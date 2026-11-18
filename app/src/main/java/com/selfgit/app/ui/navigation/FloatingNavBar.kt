package com.selfgit.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfgit.app.ui.components.GlassPanel
import com.selfgit.app.ui.components.clickableNoRipple
import com.selfgit.app.ui.theme.LocalPalette

/** 底部导航目标。 */
enum class Destination(val label: String, val icon: ImageVector) {
    APPS("应用", Icons.Filled.Home),
    UPLOAD("上传", Icons.Filled.Upload),
    SETTINGS("设置", Icons.Filled.Settings)
}

/**
 * 导航栏显隐控制器：向下滚动隐藏，向上滚动显示。
 * 由各页面通过 [TrackNavBarScroll] 上报滚动位置。
 */
class NavBarController {
    var hidden by mutableStateOf(false)
        private set

    private var lastOffset = 0f
    private var accumulated = 0f

    fun onScroll(offset: Int) {
        // 回到顶部永远显示
        if (offset <= 0) {
            lastOffset = 0f
            accumulated = 0f
            if (hidden) hidden = false
            return
        }
        val delta = offset - lastOffset
        lastOffset = offset.toFloat()
        accumulated = (accumulated + delta).coerceIn(-300f, 300f)
        when {
            accumulated > THRESHOLD && !hidden -> {
                hidden = true
                accumulated = 0f
            }

            accumulated < -THRESHOLD && hidden -> {
                hidden = false
                accumulated = 0f
            }
        }
    }

    /** 切 Tab / 进出二级页时复位。 */
    fun show() {
        hidden = false
        lastOffset = 0f
        accumulated = 0f
    }

    private companion object {
        const val THRESHOLD = 22f
    }
}

val LocalNavBarController = compositionLocalOf<NavBarController?> { null }

/** 把某个列表的滚动状态接到导航栏显隐上（页面内一行调用即可）。 */
@Composable
fun TrackNavBarScroll(scroll: ScrollState) {
    val controller = LocalNavBarController.current ?: return
    LaunchedEffect(scroll, controller) {
        snapshotFlow { scroll.value }.collect { controller.onScroll(it) }
    }
    DisposableEffect(scroll, controller) {
        onDispose { controller.show() }
    }
}

/**
 * 悬浮玻璃导航栏：宽度按内容自适应，每个按钮宽度等于最宽标签所需宽度，
 * 因此切换选中项时整条栏不会伸缩。
 */
@Composable
fun FloatingNavBar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        strong = true,
        contentPadding = PaddingValues(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Destination.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = destination == selected,
                    onClick = { onSelect(destination) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val width by animateDpAsState(
        targetValue = if (selected) 76.dp else 46.dp,
        animationSpec = tween(220),
        label = "navWidth"
    )

    Row(
        Modifier
            .width(width)
            .height(46.dp)
            .clip(CircleShape)
            .background(if (selected) palette.navIndicator else Color.Transparent)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = if (selected) palette.primary else palette.textTertiary,
            modifier = Modifier.size(19.dp)
        )

        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(190)) + expandHorizontally(tween(220), expandFrom = Alignment.Start),
            exit = fadeOut(tween(110)) + shrinkHorizontally(tween(150), shrinkTowards = Alignment.Start)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = destination.label,
                    color = palette.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/** 页面标题栏里的返回胶囊（子页面使用）。 */
@Composable
fun BackPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier
            .clip(CircleShape)
            .background(palette.fieldFill)
            .clickableNoRipple(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.ArrowBack,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = text,
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
