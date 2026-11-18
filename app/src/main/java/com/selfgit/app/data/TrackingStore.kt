package com.selfgit.app.data

import android.content.Context
import org.json.JSONObject

/**
 * 追踪映射（§7）：仓库 → 已安装包信息。
 * 在 App 内发起安装（下载完成）时记录候选映射；实际安装状态实时查 PackageManager。
 */
class TrackingStore(context: Context) {

    data class Entry(
        val packageName: String,
        val versionName: String?,   // 下载时解析出的版本
        val latestTag: String,      // 下载时对应的 Release tag
        val installedAt: Long
    )

    private val prefs =
        context.getSharedPreferences("selfgit_tracking", Context.MODE_PRIVATE)

    fun get(repoFullName: String): Entry? {
        val raw = prefs.getString(repoFullName, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            Entry(
                packageName = o.getString("pkg"),
                versionName = o.optString("version").ifEmpty { null },
                latestTag = o.optString("tag"),
                installedAt = o.optLong("at")
            )
        }.getOrNull()
    }

    fun put(repoFullName: String, entry: Entry) {
        prefs.edit().putString(
            repoFullName,
            JSONObject().apply {
                put("pkg", entry.packageName)
                put("version", entry.versionName ?: "")
                put("tag", entry.latestTag)
                put("at", entry.installedAt)
            }.toString()
        ).apply()
    }
}
