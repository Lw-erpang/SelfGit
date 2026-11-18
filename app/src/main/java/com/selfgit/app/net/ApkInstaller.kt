package com.selfgit.app.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** 从下载完成的 APK 中解析出的标识信息。 */
data class ApkInfo(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val label: String?,
    val abis: List<String>
)

/** 下载与安装的辅助逻辑（阶段 1）。 */
object ApkInstaller {

    /**
     * 解析本地 APK：包名 / versionName / versionCode / 应用名 / ABI 架构。
     * 解析失败返回 null（上传页据此提示"可能不是标准 APK"）。
     */
    fun parse(context: Context, apk: File): ApkInfo? {
        return runCatching {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apk.absolutePath, 0) ?: return null
            val appInfo = info.applicationInfo ?: return null
            appInfo.sourceDir = apk.absolutePath
            appInfo.publicSourceDir = apk.absolutePath
            ApkInfo(
                packageName = info.packageName,
                versionName = info.versionName,
                versionCode = if (android.os.Build.VERSION.SDK_INT >= 28)
                    info.longVersionCode else info.versionCode.toLong(),
                label = appInfo.loadLabel(pm)?.toString(),
                abis = parseAbis(apk)
            )
        }.getOrNull()
    }

    /** 从 lib/ 目录推断 ABI（universal 包返回空列表 → 展示 "universal"）。 */
    private fun parseAbis(apk: File): List<String> {
        return runCatching {
            java.util.zip.ZipFile(apk).use { zip ->
                val re = Regex("^lib/([^/]+)/.+")
                val set = mutableSetOf<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    re.find(entries.nextElement().name)?.groupValues?.get(1)?.let { set.add(it) }
                }
                set.toList()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 发起系统安装（ACTION_INSTALL_PACKAGE）。
     * 未授予"安装未知应用"时系统会引导授权页（§11.5）。
     */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "com.selfgit.app.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 查询某包名是否已安装；已装时返回 versionName。 */
    fun installedVersion(context: Context, packageName: String): String? {
        return runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            info.versionName
        }.getOrNull()
    }

    // ---- 阶段 3：安装前校验（§11.5 签名/降级提示）----

    /** APK 安装证书 SHA-256（安装包侧）。 */
    fun apkSignatureSha256(context: Context, apk: File): String? {
        return runCatching {
            val info = context.packageManager.getPackageArchiveInfo(
                apk.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES
            ) ?: return null
            info.signatures?.firstOrNull()?.let { sha256Hex(it.toByteArray()) }
        }.getOrNull()
    }

    /** 已装应用的安装证书 SHA-256（设备侧）。 */
    fun installedSignatureSha256(context: Context, packageName: String): String? {
        return runCatching {
            val info = context.packageManager.getPackageInfo(
                packageName, android.content.pm.PackageManager.GET_SIGNATURES
            )
            info.signatures?.firstOrNull()?.let { sha256Hex(it.toByteArray()) }
        }.getOrNull()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it) }
}

/** 版本比较（追踪角标用）：从 tag/版本串中提取最后一段数字版本并逐段比较。 */
object VersionCompare {

    /** 提取版本：`小费计算百分比-v1.0` → [1,0]；`v1.9.4` → [1,9,4]。 */
    fun extract(s: String): List<Int> {
        val matches = Regex("\\d+(\\.\\d+)*").findAll(s).toList()
        val last = matches.lastOrNull()?.value ?: return emptyList()
        return last.split('.').map { it.toIntOrNull() ?: 0 }
    }

    /** 返回负数 = a < b；0 = 相等；正数 = a > b。 */
    fun compare(a: List<Int>, b: List<Int>): Int {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
