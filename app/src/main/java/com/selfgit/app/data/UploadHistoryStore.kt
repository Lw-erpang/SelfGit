package com.selfgit.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 上传记录（本地留存，最近 30 条，新记录在前）。
 * 记录最终结果：成功，或附件重试耗尽 / Release 创建失败。
 */
class UploadHistoryStore(context: Context) {

    data class Record(
        val fileName: String,       // APK 文件名
        val appLabel: String,       // 解析出的应用名（可能为空）
        val packageName: String,
        val versionName: String,
        val tag: String,            // 发布使用的 tag
        val repoFullName: String,   // 目标仓库
        val sizeBytes: Long,
        val timeMs: Long,
        val success: Boolean,
        val message: String         // 失败原因（成功时为空）
    )

    private val prefs =
        context.getSharedPreferences("selfgit_upload_history", Context.MODE_PRIVATE)

    fun list(): List<Record> {
        val raw = prefs.getString("records", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Record(
                    fileName = o.optString("file"),
                    appLabel = o.optString("label"),
                    packageName = o.optString("pkg"),
                    versionName = o.optString("version"),
                    tag = o.optString("tag"),
                    repoFullName = o.optString("repo"),
                    sizeBytes = o.optLong("size"),
                    timeMs = o.optLong("at"),
                    success = o.optBoolean("ok"),
                    message = o.optString("msg")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(record: Record) {
        val list = list().toMutableList()
        list.add(0, record)
        if (list.size > MAX) list.subList(MAX, list.size).clear()
        save(list)
    }

    fun clear() = save(emptyList())

    private fun save(list: List<Record>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("file", r.fileName)
                put("label", r.appLabel)
                put("pkg", r.packageName)
                put("version", r.versionName)
                put("tag", r.tag)
                put("repo", r.repoFullName)
                put("size", r.sizeBytes)
                put("at", r.timeMs)
                put("ok", r.success)
                put("msg", r.message)
            })
        }
        prefs.edit().putString("records", arr.toString()).apply()
    }

    private companion object {
        const val MAX = 30
    }
}
