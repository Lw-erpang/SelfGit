package com.selfgit.app.shell

/**
 * Shizuku UserService 实现：运行在 Shizuku 服务进程（shell uid），
 * 执行 pm install 等命令并返回 "退出码\n输出摘要"。
 */
class ShellService : IShellService.Stub() {

    override fun exec(cmd: String): String {
        val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        val out = p.inputStream.bufferedReader().use { it.readText() }
        val err = p.errorStream.bufferedReader().use { it.readText() }
        p.waitFor()
        val body = if (err.isBlank()) out else out + "\n" + err
        return "${p.exitValue()}\n${body.trim()}"
    }
}
