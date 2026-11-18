package com.selfgit.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selfgit.app.ui.theme.LocalPalette

/**
 * 主题化对话框：容器用玻璃强色（亮 0xF0FFFFFF / 暗 0xE022304C），
 * 标题/正文用调色板文字色，替代 M3 默认灰白硬底，与玻璃主题一致。
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        // 键盘弹出时整窗上移，弹窗内输入框不被遮挡
        modifier = modifier.imePadding(),
        containerColor = palette.glassStrongTop,
        tonalElevation = 0.dp,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = {
            Text(title, fontWeight = FontWeight.SemiBold)
        },
        text = { Column(content = text) },
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}
