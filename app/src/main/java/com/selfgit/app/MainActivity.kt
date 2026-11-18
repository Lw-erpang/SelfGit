package com.selfgit.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.selfgit.app.net.InstallRouter
import com.selfgit.app.ui.SelfGitApp
import com.selfgit.app.ui.theme.SelfGitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import rikka.shizuku.Shizuku

/** 系统分享（ACTION_SEND APK 流）接收中转：MainActivity → SelfGitApp 上传页。 */
object ShareInbox {
    val uri = MutableStateFlow<Uri?>(null)
}

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
        handleShareIntent(intent)
        setContent {
            SelfGitApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** 接收系统分享的 APK 流（如小米分享已装应用），转交上传页装载。 */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (stream != null) {
                ShareInbox.uri.value = stream
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }
}
