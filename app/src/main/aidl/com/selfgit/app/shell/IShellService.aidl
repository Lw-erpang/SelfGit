package com.selfgit.app.shell;

// Shizuku UserService 接口：在 Shizuku 服务进程（shell uid）内执行命令，
// 用于 pm install 静默安装（阶段 3）。
interface IShellService {
    String exec(String cmd);
}
