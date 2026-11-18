package com.selfgit.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 玻璃拟态调色板（自 ShuYing 设计体系零漂移移植）。
 * 所有透明色都在这里定义好，组件只消费语义化的名字，亮/暗两套自动切换。
 */
@Immutable
data class SelfGitPalette(
    val dark: Boolean,
    val backgroundTop: Color,
    val backgroundMiddle: Color,
    val backgroundBottom: Color,
    val blobPrimary: Color,
    val blobAccent: Color,
    val blobMint: Color,
    val primary: Color,
    val primaryDeep: Color,
    val onPrimary: Color,
    val accent: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassBorder: Color,
    val glassStrongTop: Color,
    val glassStrongBottom: Color,
    val fieldFill: Color,
    val fieldBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val successContainer: Color,
    val danger: Color,
    val navIndicator: Color,
    val shadow: Color,
    val divider: Color
)

val LightPalette = SelfGitPalette(
    dark = false,
    backgroundTop = Color(0xFFEDF4FF),
    backgroundMiddle = Color(0xFFF9FCFF),
    backgroundBottom = Color(0xFFE6EFFB),
    blobPrimary = Color(0xFF6FA9FF),
    blobAccent = Color(0xFFFFC06B),
    blobMint = Color(0xFF7FDCC0),
    primary = Color(0xFF0B6BD6),
    primaryDeep = Color(0xFF05548F),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFF7A429),
    glassTop = Color(0xCCFFFFFF),
    glassBottom = Color(0x8FFFFFFF),
    glassBorder = Color(0xE6FFFFFF),
    glassStrongTop = Color(0xF0FFFFFF),
    glassStrongBottom = Color(0xD1FFFFFF),
    fieldFill = Color(0x9EFFFFFF),
    fieldBorder = Color(0x1F0B1B2B),
    textPrimary = Color(0xFF0B1B2B),
    textSecondary = Color(0xFF46586F),
    textTertiary = Color(0xFF8598B0),
    success = Color(0xFF0F9D63),
    successContainer = Color(0xFFDEF6EC),
    danger = Color(0xFFD64545),
    navIndicator = Color(0x1F0B6BD6),
    shadow = Color(0x241B3A66),
    divider = Color(0x0F0B1B2B)
)

val DarkPalette = SelfGitPalette(
    dark = true,
    backgroundTop = Color(0xFF060B14),
    backgroundMiddle = Color(0xFF0C1524),
    backgroundBottom = Color(0xFF070D18),
    blobPrimary = Color(0xFF1E63C8),
    blobAccent = Color(0xFFB9711F),
    blobMint = Color(0xFF15755C),
    primary = Color(0xFF5AAEFF),
    primaryDeep = Color(0xFF2D7FD1),
    onPrimary = Color(0xFF04121F),
    accent = Color(0xFFFFB65C),
    glassTop = Color(0xC71B2740),
    glassBottom = Color(0x9E0E1728),
    glassBorder = Color(0x2EFFFFFF),
    glassStrongTop = Color(0xE022304C),
    glassStrongBottom = Color(0xB3131E31),
    fieldFill = Color(0x14FFFFFF),
    fieldBorder = Color(0x29FFFFFF),
    textPrimary = Color(0xFFEAF1FB),
    textSecondary = Color(0xFFA9BAD1),
    textTertiary = Color(0xFF7689A2),
    success = Color(0xFF3ED9A0),
    successContainer = Color(0xFF11322A),
    danger = Color(0xFFFF7A7A),
    navIndicator = Color(0x2958AEFF),
    shadow = Color(0x8C000000),
    divider = Color(0x14FFFFFF)
)
