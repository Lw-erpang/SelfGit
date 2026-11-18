package com.selfgit.app.data

import android.content.Context
import com.selfgit.app.ui.theme.ThemeMode
import org.json.JSONObject

/** 认证方式：密码引导 / 直接粘贴令牌。 */
enum class AuthMode { PASSWORD_BOOTSTRAP, PASTE_TOKEN }

/** 安装方式（§11.5）：系统 / Shizuku / Root / 第三方安装器。 */
enum class InstallerType(val label: String) {
    SYSTEM("系统安装器"),
    SHIZUKU("Shizuku"),
    ROOT("Root"),
    THIRD_PARTY("第三方安装器")
}

/**
 * 主机配置（设计文档 §5）：主备地址 + 认证元数据。
 * 令牌本体不在此对象中——由 TokenStore 加密隔离存储。
 */
data class HostConfig(
    val displayName: String,
    val primaryUrl: String,
    val backupUrl: String,
    val username: String,
    val authMode: AuthMode,
    val roTokenName: String,
    val rwTokenName: String,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("displayName", displayName)
        put("primaryUrl", primaryUrl)
        put("backupUrl", backupUrl)
        put("username", username)
        put("authMode", authMode.name)
        put("roTokenName", roTokenName)
        put("rwTokenName", rwTokenName)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): HostConfig = HostConfig(
            displayName = json.optString("displayName"),
            primaryUrl = json.getString("primaryUrl"),
            backupUrl = json.optString("backupUrl"),
            username = json.getString("username"),
            authMode = runCatching { AuthMode.valueOf(json.getString("authMode")) }
                .getOrDefault(AuthMode.PASSWORD_BOOTSTRAP),
            roTokenName = json.optString("roTokenName"),
            rwTokenName = json.optString("rwTokenName"),
            createdAt = json.optLong("createdAt")
        )
    }
}

/**
 * 全局设置持久化（SharedPreferences）。
 * 含设计文档 §7 配置项中的阶段 0/1 子集。
 */
class SettingsStore(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences("selfgit_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HOST = "host_json"
        private const val KEY_LAST_GOOD = "last_good_url"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LAST_REFRESH = "last_refresh_at"
        private const val KEY_RO_TOKEN = "token_ro"
        private const val KEY_RW_TOKEN = "token_rw"
        private const val KEY_INSTALLER = "installer_type"
        private const val KEY_THIRD_PARTY_PKG = "third_party_pkg"
        const val REFRESH_DEBOUNCE_MS = 60_000L
    }

    // ---- 主机 ----

    var host: HostConfig?
        get() = prefs.getString(KEY_HOST, null)?.let {
            runCatching { HostConfig.fromJson(JSONObject(it)) }.getOrNull()
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_HOST).apply()
            } else {
                prefs.edit().putString(KEY_HOST, value.toJson().toString()).apply()
            }
        }

    /** 最后一次成功连接的地址（记忆优先策略，§5.2）。 */
    var lastGoodUrl: String?
        get() = prefs.getString(KEY_LAST_GOOD, null)
        set(value) = prefs.edit().putString(KEY_LAST_GOOD, value).apply()

    // ---- 令牌（Keystore 加密落盘） ----

    var roToken: String?
        get() = TokenStore.load(appContext, KEY_RO_TOKEN)
        set(value) { setToken(KEY_RO_TOKEN, value) }

    var rwToken: String?
        get() = TokenStore.load(appContext, KEY_RW_TOKEN)
        set(value) { setToken(KEY_RW_TOKEN, value) }

    private fun setToken(name: String, value: String?) {
        if (value == null) TokenStore.delete(appContext, name) else TokenStore.save(appContext, name, value)
    }

    // ---- 偏好 ----

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var lastRefreshAt: Long
        get() = prefs.getLong(KEY_LAST_REFRESH, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REFRESH, value).apply()

    // ---- 安装方式（阶段 3，§11.5）----

    /** 安装器类型切换即时生效（§11.5）。 */
    var installerType: InstallerType
        get() = runCatching { InstallerType.valueOf(prefs.getString(KEY_INSTALLER, null) ?: "") }
            .getOrDefault(InstallerType.SYSTEM)
        set(value) = prefs.edit().putString(KEY_INSTALLER, value.name).apply()

    /** 第三方安装器包名（THIRD_PARTY 时使用）。 */
    var thirdPartyPkg: String
        get() = prefs.getString(KEY_THIRD_PARTY_PKG, null) ?: ""
        set(value) = prefs.edit().putString(KEY_THIRD_PARTY_PKG, value).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
        TokenStore.delete(appContext, KEY_RO_TOKEN)
        TokenStore.delete(appContext, KEY_RW_TOKEN)
    }
}
