package com.selfgit.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.selfgit.app.net.InstallRouter
import com.selfgit.app.ui.SelfGitApp
import com.selfgit.app.ui.theme.SelfGitTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    /** Shizuku 授权结果（阶段 3 安装后端）。 */
    private val shizukuListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == InstallRouter.SHIZUKU_REQUEST_CODE) {
                val msg = if (grantResult == PackageManager.PERMISSION_GRANTED)
                    "Shizuku 已授权，再次安装即静默安装"
                else "Shizuku 授权被拒绝，安装将回退系统安装器"
                runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不启用 edge-to-edge：配合 windowSoftInputMode=adjustResize 的经典窗口收缩机制，
        // 键盘弹出由系统 resize 窗口实现，不依赖 IME insets 上报（部分国产输入法如微信输入法不上报）。
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        setContent {
            SelfGitApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }
}
