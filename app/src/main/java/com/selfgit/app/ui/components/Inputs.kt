package com.selfgit.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfgit.app.ui.theme.LocalPalette

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    error: String? = null,
    helper: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    monospace: Boolean = false,
    obscure: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done
) {
    val palette = LocalPalette.current
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = palette.fieldFill,
        unfocusedContainerColor = palette.fieldFill,
        disabledContainerColor = palette.fieldFill.copy(alpha = 0.45f),
        errorContainerColor = palette.danger.copy(alpha = 0.06f),
        focusedBorderColor = palette.primary,
        unfocusedBorderColor = palette.fieldBorder,
        disabledBorderColor = palette.fieldBorder.copy(alpha = 0.45f),
        errorBorderColor = palette.danger,
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
        disabledTextColor = palette.textTertiary,
        errorTextColor = palette.textPrimary,
        cursorColor = palette.primary,
        focusedPlaceholderColor = palette.textTertiary,
        unfocusedPlaceholderColor = palette.textTertiary
    )

    val textStyle = MaterialTheme.typography.bodyLarge.merge(
        if (monospace) {
            TextStyle(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            TextStyle()
        }
    )

    Column(modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (error != null) palette.danger else palette.textSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = if (placeholder.isEmpty()) null else {
                { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = palette.textTertiary) }
            },
            leadingIcon = if (leadingIcon == null) null else {
                { Icon(leadingIcon, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(18.dp)) }
            },
            trailingIcon = trailing,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            maxLines = if (singleLine) 1 else maxLines,
            textStyle = textStyle,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            colors = colors
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = palette.danger,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
            )
        } else if (helper != null) {
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textTertiary,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    shape: Shape = RoundedCornerShape(18.dp)
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "btnScale")

    val gradient = when {
        !enabled -> Brush.horizontalGradient(
            listOf(palette.textTertiary.copy(alpha = 0.35f), palette.textTertiary.copy(alpha = 0.22f))
        )
        danger -> Brush.horizontalGradient(
            listOf(palette.danger.copy(alpha = 0.85f), palette.danger)
        )
        else -> Brush.horizontalGradient(listOf(palette.primaryDeep, palette.primary))
    }

    Box(
        modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = palette.shadow, spotColor = palette.shadow)
            .clip(shape)
            .background(gradient)
            .clickableNoRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) palette.onPrimary else palette.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) palette.onPrimary else palette.textTertiary
            )
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp)
) {
    val palette = LocalPalette.current
    val contentColor = when {
        !enabled -> palette.textTertiary.copy(alpha = 0.6f)
        danger -> palette.danger
        else -> palette.textSecondary
    }

    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(palette.fieldFill)
            .border(1.dp, if (danger) palette.danger.copy(alpha = 0.35f) else palette.fieldBorder, shape)
            .clickableNoRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

/** 小尺寸胶囊按钮，用于卡片内的次级操作。 */
@Composable
fun MiniButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Color? = null,
    enabled: Boolean = true
) {
    val palette = LocalPalette.current
    val color = when {
        !enabled -> palette.textTertiary.copy(alpha = 0.65f)
        else -> tone ?: palette.primary
    }

    Row(
        modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (palette.dark) 0.16f else 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), CircleShape)
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 38.dp,
    tint: Color? = null,
    background: Color? = null,
    enabled: Boolean = true
) {
    val palette = LocalPalette.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background ?: palette.fieldFill)
            .border(1.dp, palette.fieldBorder, CircleShape)
            .clickableNoRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint ?: palette.textSecondary,
            modifier = Modifier.size(size * 0.48f)
        )
    }
}

/** 选择胶囊（如认证方式、安装器类型）。 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val palette = LocalPalette.current
    val background by animateColorAsState(
        targetValue = if (selected) {
            palette.primary.copy(alpha = if (palette.dark) 0.26f else 0.14f)
        } else {
            palette.fieldFill
        },
        label = "chipBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> palette.textTertiary.copy(alpha = 0.6f)
            selected -> palette.primary
            else -> palette.textSecondary
        },
        label = "chipFg"
    )

    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .border(
                1.dp,
                if (selected) palette.primary.copy(alpha = 0.45f) else palette.fieldBorder,
                CircleShape
            )
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
