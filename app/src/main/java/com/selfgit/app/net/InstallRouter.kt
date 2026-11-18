package com.selfgit.app.net

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.selfgit.app.data.InstallerType
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import java.io.File

/** 安装路由结果：requested=所选后端，used=实际使用后端，detail=回退原因/执行输出摘要。 */
data class InstallOutcome(
    val requested: InstallerType,
    val used: InstallerType,
    val success: Boolean,
    val detail: String?
) {
    /** 是否发生了回退（所选后端未就绪 → 系统安装器）。 */
    val fellBack: Boolean get() = requested != used
}

/**
 * 安装后端路由（§11.5，阶段 3）：
 * 系统默认 / Shizuku（pm install）/ Root(libsu, pm install) / 第三方安装器（定向安装意图）。
 * 任一后端未就绪时明确提示并回退系统安装器（§11.5 验收）。
 */
object InstallRouter {

    const val SHIZUKU_REQUEST_CODE = 43961
    private const val AUTHORITY = "com.selfgit.app.fileprovider"

    fun install(
        context: Context,
        file: File,
        type: InstallerType,
        thirdPartyPkg: String
    ): InstallOutcome = when (type) {
        InstallerType.SYSTEM -> systemInstall(context, file, InstallerType.SYSTEM, null)
        InstallerType.SHIZUKU -> shizukuInstall(context, file)
        InstallerType.ROOT -> rootInstall(context, file)
        InstallerType.THIRD_PARTY -> thirdPartyInstall(context, file, thirdPartyPkg)
    }

    // ---- 后端：系统 ----

    private fun systemInstall(
        context: Context,
        file: File,
        requested: InstallerType,
        detail: String?
    ): InstallOutcome {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = FileProvider.getUriForFile(context, AUTHORITY, file)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            InstallOutcome(requested, InstallerType.SYSTEM, true, detail)
        } catch (e: Exception) {
            InstallOutcome(requested, InstallerType.SYSTEM, false, "无法启动系统安装器：${e.message ?: "未知"}")
        }
    }

    // ---- 后端：Shizuku ----

    private fun shizukuInstall(context: Context, file: File): InstallOutcome {
        val pingOk = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        if (!pingOk) {
            return fallback(InstallerType.SHIZUKU, "Shizuku 未运行（请先打开 Shizuku 应用并启动服务）")
        }
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
        if (!granted) {
            // 拉起 Shizuku 授权弹窗（MainActivity 监听结果）；本次回退系统安装器
            try { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) } catch (_: Throwable) { }
            return fallback(InstallerType.SHIZUKU, "未授予 Shizuku 权限：已拉起授权弹窗，本次先用系统安装器，授权后重试即静默安装")
        }
        // 绑定 Shizuku UserService（运行于 shell uid），执行 pm install 静默安装
        val latch = java.util.concurrent.CountDownLatch(1)
        var service: com.selfgit.app.shell.IShellService? = null
        var bindError: String? = null
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, binder: android.os.IBinder) {
                service = com.selfgit.app.shell.IShellService.Stub.asInterface(binder)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: android.content.ComponentName) { }
        }
        return try {
            val args = Shizuku.UserServiceArgs(
                android.content.ComponentName(context, com.selfgit.app.shell.ShellService::class.java)
            ).version(1)
            Shizuku.bindUserService(args, connection)
            if (!latch.await(15, java.util.concurrent.TimeUnit.SECONDS)) {
                return fallback(InstallerType.SHIZUKU, "Shizuku 服务连接超时（服务未启动或版本过旧）")
            }
            val svc = service
                ?: return fallback(InstallerType.SHIZUKU, bindError ?: "Shizuku 服务连接失败")
            val out = svc.exec("pm install -r '${file.absolutePath}'")
            val codeLine = out.lineSequence().firstOrNull()?.trim() ?: ""
            val body = out.lineSequence().drop(1).joinToString("\n")
            if (codeLine == "0" && body.contains("Success", ignoreCase = true)) {
                InstallOutcome(InstallerType.SHIZUKU, InstallerType.SHIZUKU, true, "静默安装成功")
            } else {
                InstallOutcome(
                    InstallerType.SHIZUKU, InstallerType.SHIZUKU, false,
                    "Shizuku 安装失败：${body.trim().take(160)}"
                )
            }
        } catch (e: Throwable) {
            fallback(InstallerType.SHIZUKU, "Shizuku 执行异常：${e.message ?: "未知"}")
        }
    }

    // ---- 后端：Root（libsu）----

    private fun rootInstall(context: Context, file: File): InstallOutcome {
        return try {
            Shell.getShell()
            val granted = Shell.isAppGrantedRoot()
            if (granted != true) {
                fallback(InstallerType.ROOT, "未获得 Root 权限（无 su 或未在管理器中授权）")
            } else {
                val result = Shell.cmd("pm install -r '${file.absolutePath}'").exec()
                val out = result.out.joinToString("\n")
                val err = result.err.joinToString("\n")
                if (result.isSuccess && out.contains("Success", ignoreCase = true)) {
                    InstallOutcome(InstallerType.ROOT, InstallerType.ROOT, true, "静默安装成功")
                } else {
                    InstallOutcome(
                        InstallerType.ROOT, InstallerType.ROOT, false,
                        "Root 安装失败：${(err.ifBlank { out }).trim().take(120)}"
                    )
                }
            }
        } catch (e: Throwable) {
            fallback(InstallerType.ROOT, "Root 执行异常：${e.message ?: "未知"}")
        }
    }

    // ---- 后端：第三方安装器 ----

    /**
     * 接管型安装器（LSPosed 模块，hook 系统安装器实现接管）：
     * 自身不注册安装 intent filter，无法定向唤起；选中后走系统安装路径，
     * 由 hook 改变安装行为（如爱玩机-安装卸载器接管）。
     */
    val HOOKED_INSTALLERS = setOf("com.modify.installer")

    private fun thirdPartyInstall(context: Context, file: File, pkg: String): InstallOutcome {
        if (pkg.isBlank()) {
            return fallback(InstallerType.THIRD_PARTY, "未选择第三方安装器（请在设置页中选择）")
        }
        if (ApkInstaller.installedVersion(context, pkg) == null) {
            // §11.5：第三方安装器包名被卸载后自动回退系统默认并提示
            return fallback(InstallerType.THIRD_PARTY, "第三方安装器（$pkg）已卸载，自动回退系统安装器")
        }
        // 接管型：无安装 activity，走系统安装器（被 hook 接管）
        if (pkg in HOOKED_INSTALLERS) {
            return systemInstall(context, file, InstallerType.THIRD_PARTY, "已唤起系统安装器（由接管型安装器处理）")
        }
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            setPackage(pkg)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
            InstallOutcome(InstallerType.THIRD_PARTY, InstallerType.THIRD_PARTY, true, null)
        } catch (e: Exception) {
            fallback(InstallerType.THIRD_PARTY, "无法唤起第三方安装器：${e.message ?: "未知"}")
        }
    }

    // ---- 回退 ----

    private fun fallback(requested: InstallerType, reason: String): InstallOutcome =
        InstallOutcome(requested, InstallerType.SYSTEM, true, reason)
}
