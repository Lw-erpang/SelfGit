package com.selfgit.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 令牌加密存储（设计文档 §3.3 / §12）：
 * - AES/GCM 密钥保存在 Android Keystore，不可导出
 * - 密文存 SharedPreferences；卸载 App 即随 Keystore 销毁
 * - 密码不经过本类——密码只在引导请求的内存中出现，不落盘
 */
object TokenStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "selfgit_master"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val PREFS = "selfgit_secure"
    private const val GCM_TAG_BITS = 128

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun save(context: Context, name: String, plain: String) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs(context).edit().putString(name, packed).apply()
    }

    fun load(context: Context, name: String): String? {
        val packed = prefs(context).getString(name, null) ?: return null
        val parts = packed.split(":", limit = 2)
        if (parts.size != 2) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                masterKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun delete(context: Context, name: String) {
        prefs(context).edit().remove(name).apply()
    }
}
