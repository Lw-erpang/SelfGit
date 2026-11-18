package com.selfgit.app.data

import android.content.Context

/**
 * 仓库置顶（本地留存）：置顶的仓库在主页列表排在最前。
 * key 为仓库 fullName。
 */
class PinnedStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("selfgit_pinned", Context.MODE_PRIVATE)

    fun list(): Set<String> =
        prefs.getStringSet("pinned", emptySet()) ?: emptySet()

    fun contains(fullName: String) = list().contains(fullName)

    /** 置顶/取消置顶切换，返回切换后是否置顶。 */
    fun toggle(fullName: String): Boolean {
        val now = list().toMutableSet()
        val added = if (now.contains(fullName)) {
            now.remove(fullName); false
        } else {
            now.add(fullName); true
        }
        prefs.edit().putStringSet("pinned", now).apply()
        return added
    }
}
