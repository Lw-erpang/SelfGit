# SelfGit UI 设计文档

| 项 | 内容 |
|---|---|
| 工作代号 | SelfGit（单 Forge 私有应用管理器） |
| 版本 | v0.1（设计稿，未开工） |
| 日期 | 2026-11-18 |
| 上游文档 | 《需求与技术设计文档 v0.1》（同目录） |
| 设计参考 | **ShuYing 项目**（同工作空间 `E:\Android\work\ShuYing`，自有项目）——设计语言与组件直接移植复用 |
| 范围声明 | 本文档描述 UI/UX 设计，不包含代码实现 |

---

## 0. 文档结构导航

1. 设计来源与复用声明
2. 设计语言总则
3. 设计令牌（Design Tokens，浅色 / 深色双主题）
4. 全局框架与导航结构
5. 页面规格（S1~S6）
6. 组件库清单与规格
7. 状态与反馈规范
8. 深色模式
9. UI 验收标准
10. 开放问题

---

## 1. 设计来源与复用声明

SelfGit 的 UI 完全采用 ShuYing 已验证的设计语言。ShuYing 为本工作空间自有项目，以下组件**代码级直接移植**（非借鉴，无任何许可问题），移植清单：

| ShuYing 组件 | 位置（`ui/` 下） | SelfGit 用途 |
|---|---|---|
| `AuroraBackground` | components/AuroraBackground.kt | 全局极光渐变背景（含三色 blob 光斑） |
| `GlassPanel` | components/Glass.kt | 一切卡片容器（毛玻璃面板） |
| `FloatingNavBar` | navigation/FloatingNavBar.kt | 底部悬浮胶囊导航 |
| `ElasticScroll` | components/ElasticScroll.kt | 列表弹性滚动 |
| `Inputs` | components/Inputs.kt | 表单输入（玻璃质感输入框） |
| `ResultPanel` | components/ResultPanel.kt | 结果展示面板（改造为"解析结果/上传进度"卡） |
| `AppIcons` | icons/AppIcons.kt | 线性图标体系（按需补充仓库/上传类图标） |
| `LocalPalette` | theme/Theme.kt、Color.kt | 双主题色板（本文件第 3 章全量搬移） |

移植原则：组件的视觉参数（圆角、投影、渐变、动画曲线）**原样保留**，仅按 SelfGit 的信息架构重组页面；新增组件（第 6 章标注"新增"）必须以现有玻璃体系实现，不引入新的视觉语言。

---

## 2. 设计语言总则

1. **三层视觉结构**：底层极光渐变背景（含模糊光斑）→ 中层毛玻璃卡片（半透明白/深面板 + 高光描边 + 柔投影）→ 顶层内容（三级文字色阶）。
2. **无涟漪点击**：全部可点区域禁用 Material 涟漪（保留玻璃质感），按压反馈用轻微缩放/透明度变化表达。
3. **弹性滚动**：列表两端带回弹（ElasticScroll），禁止生硬截停。
4. **悬浮导航**：底部悬浮胶囊导航，不占用内容区背景，随滚动淡入淡出。
5. **系统栏透明**：状态栏/导航栏透明 + 浅色前景图标（浅色主题），内容延伸至屏幕边缘。
6. **克制的信息密度**：每屏一个主任务；卡片内二级信息用 tertiary 色 12~13sp，不与主信息争抢。

---

## 3. 设计令牌（Design Tokens）

### 3.1 浅色主题（默认，与 ShuYing 完全一致）

| 令牌 | 值 | 用途 |
|---|---|---|
| backgroundTop / Middle / Bottom | `#EDF4FF` / `#F9FCFF` / `#E6EFFB` | 背景三段垂直渐变（bg_window 同源） |
| blobPrimary / Accent / Mint | `#6FA9FF` / `#FFC06B` / `#7FDCC0` | 极光光斑三色 |
| primary | `#0B6BD6` | 主色（按钮、选中态、链接） |
| primaryDeep | `#05548F` | 主按钮渐变深端 |
| onPrimary | `#FFFFFF` | 主色上的文字/图标 |
| accent | `#F7A429` | 强调（更新角标、警告性高亮） |
| glassTop / Bottom | `#CCFFFFFF` / `#8FFFFFFF` | 玻璃面板上下渐变 |
| glassBorder | `#E6FFFFFF`（描边下缘透明度 ×0.25） | 玻璃高光描边 |
| glassStrongTop / Bottom | `#F0FFFFFF` / `#D1FFFFFF` | 强玻璃面板（表单、弹层） |
| fieldFill / fieldBorder | `#9EFFFFFF` / `#1F0B1B2B` | 输入框填充/描边 |
| textPrimary / Secondary / Tertiary | `#0B1B2B` / `#46586F` / `#8598B0` | 三级文字 |
| success / Container | `#0F9D63` / `#DEF6EC` | 成功态 |
| danger | `#D64545` | 危险/错误 |
| navIndicator | `#1F0B6BD6` | 导航选中胶囊底色 |
| shadow | `#241B3A66` | 卡片投影色（浅色投影 18dp，深色 12dp） |
| divider | `#0F0B1B2B` | 分隔线 |

### 3.2 深色主题（全量对应，切换跟随系统）

| 令牌 | 值 |
|---|---|
| backgroundTop / Middle / Bottom | `#060B14` / `#0C1524` / `#070D18` |
| blobPrimary / Accent / Mint | `#1E63C8` / `#B9711F` / `#15755C` |
| primary / primaryDeep | `#5AAEFF` / `#2D7FD1` |
| onPrimary | `#04121F` |
| accent | `#FFB65C` |
| glassTop / Bottom | `#C71B2740` / `#9E0E1728` |
| glassBorder | `#2EFFFFFF` |
| glassStrongTop / Bottom | `#E022304C` / `#B3131E31` |
| fieldFill / fieldBorder | `#14FFFFFF` / `#29FFFFFF` |
| textPrimary / Secondary / Tertiary | `#EAF1FB` / `#A9BAD1` / `#7689A2` |
| success / Container | `#3ED9A0` / `#11322A` |
| danger | `#FF7A7A` |
| navIndicator | `#2958AEFF` |

### 3.3 形状与字阶

| 令牌 | 值 | 来源 |
|---|---|---|
| 卡片圆角 | 26dp | GlassPanel 默认值 |
| 小控件圆角（输入框、按钮、徽标） | 12~14dp / 徽标全圆 | ShuYing 表单组件 |
| 导航胶囊 | 全圆角（高度 ~64dp） | FloatingNavBar |
| 投影 | shadow 色，浅色 18dp / 深色 12dp elevation | GlassPanel 实现 |
| 页面大标题 | 22~24sp，粗体，textPrimary | ShuYing 页首样式 |
| 卡片标题 | 15~16sp，medium，textPrimary | 同上 |
| 正文 | 14sp，textPrimary/Secondary | 同上 |
| 辅助说明 | 12~13sp，textTertiary | 同上 |
| 主按钮 | 高 52dp，primary→primaryDeep 横向渐变，白字，26dp 圆角 | ShuYing"生成密码/保存"按钮样式 |
| 次按钮 | 高 44~48dp，玻璃面板质感 + 主色文字/图标 | ShuYing"隐藏/复制"按钮样式 |

---

## 4. 全局框架与导航结构

### 4.1 导航结构

底部悬浮胶囊导航（FloatingNavBar 移植），四个主目的地：

| Tab | 名称 | 图标（AppIcons 风格线性） | 内容 |
|---|---|---|---|
| 1 | 应用 | 网格/仓库图标 | S1 应用列表（首页） |
| 2 | 追踪 | 心跳/铃铛图标 | 追踪中的应用 + 更新状态汇总 |
| 3 | 上传 | 上传箭头图标 | S3 上传流程入口 |
| 4 | 设置 | 齿轮图标 | S5 设置 |

- 未完成首次配置（无主机）时：除"设置"外所有 Tab 显示引导空态；导航不隐藏（保留出口感）。
- 二级页面（应用详情 S2、主机编辑、令牌管理 S6）从列表推入，顶部返回；不占用底部导航。

### 4.2 全局状态条（新增组件 StatusPill）

主界面顶部（页面大标题下方）显示一行玻璃状态条：

```
[生效地址徽标: 主(局域网) / 备(DDNS)] · 数据更新于 5 分钟前 · [刷新 ⟳]
```

- 主备切换中显示"切换中…"；刷新失败显示 danger 色提示并保留点击重试。
- 刷新进行中：状态条内嵌细进度条，不阻塞页面滚动。

---

## 5. 页面规格

### S1 应用（首页，我的仓库）

- **布局**：页面标题"应用" → StatusPill → 仓库卡片列表（ElasticScroll + GlassPanel）。
- **仓库卡片**（GlassPanel，26dp 圆角，高 ~84dp）：
  - 左：应用图标（48dp，圆角 12dp；无图标时用应用名首字圆形占位）
  - 中：应用名（16sp medium）＋ 一行辅助信息（最新版本 vX.Y.Z · 更新于相对时间，tertiary 13sp）
  - 右：更新角标（accent 色圆点/数字徽标）或"已安装"成功色小徽标
  - 点击 → S2 应用详情
- **三态**：
  - 加载：缓存立即渲染 + StatusPill 刷新中
  - 空态：玻璃卡居中——"还没有可显示的仓库"+ 主按钮"去添加主机"（未配置）或"检查主机连接"（已配置但列表为空）
  - 错误：缓存照常展示，StatusPill 转 danger 提示；卡片列表顶部插入一条"重试"玻璃横幅

### S2 应用详情 / 历史版本

- **头卡**（GlassPanel strong）：应用图标 + 名称 + 包名（tertiary，可长按复制）+ 当前已装版本 vs 最新版本对比行 + 主按钮"更新"（有更新时）/ "重新下载"（无更新时）
- **版本时间线**：Release 倒序列表，每条为玻璃卡：
  - 头行：tag 名（primary 色，粗体）+ 发布日期（tertiary）+ 右侧"已安装"徽标（对应版本时）
  - 修改日志：Markdown 基础渲染（标题/列表/代码/换行），超 6 行折叠，"展开"链接
  - 元数据行：包名 · versionCode · 架构（tertiary 12sp）
  - 附件行：APK 文件名 + 大小 + 下载按钮（次按钮样式）；下载中变进度态
- **签名提醒**（新增组件 InlineNotice）：检测到目标版本与本机已装版本签名不一致时，头卡下方插入 danger 色 InlineNotice："签名不一致，覆盖安装将失败，需先卸载"。

### S3 上传

单页分步（同一页内渐进展开，不弹窗式向导）：

1. **选择文件卡**（GlassPanel strong，虚线内边框强调"待选择"）：大号上传图标 + "选择 APK 文件"主按钮；已选文件后此卡折叠为文件名一行。
2. **解析结果卡**（复用 ResultPanel 骨架）：应用图标 + 应用名 + 包名 + 版本（versionName/versionCode）+ 架构 + 大小；解析失败卡转 danger + "重新选择"。
3. **归属卡**：显示"将上传到条目：X（匹配包名）"或"将新建条目：X"；tag 冲突时此卡转 danger 显示冲突详情。
4. **发布信息卡**：tag 输入（按模板预填，可编辑）+ 修改日志多行输入（模板预填）+ "上传前确认"开关（默认开，读取全局设置）。
5. **上传执行**：主按钮"开始上传" → 按钮变为玻璃进度卡（百分比 + 速度 + 取消）；成功 → success 色对勾卡 + "查看条目"；失败 → danger 卡 + 失败点说明 + "重试 / 删除空 Release 并退出"两个次按钮。

### S4 主机与登录

- **主机卡片列表**（设置内或独立页）：每主机一张 GlassPanel——显示名 + 主地址/备地址两行（生效地址带 success 色小徽标"当前"）+ 认证方式 + 编辑/删除。
- **添加/编辑主机表单**（GlassPanel strong）：
  - 显示名输入 → 主地址输入（协议选择 http/https + 地址 + 端口）→ 备地址（可折叠，默认收起）
  - 认证方式分段控件：`密码引导` | `粘贴令牌`
  - 密码引导：账号 + 密码输入 + 主按钮"登录并创建令牌"；说明行（tertiary 12sp）："密码仅用于创建令牌，不会被保存"
  - 粘贴令牌：令牌输入（密码框）+ scope 自检结果徽标行
  - 探测结果区：主/备两行各带状态徽标（可达 success / 不可达 danger / 探测中 loading）
- **重新认证页**（P6）：极简——显示主机名 + 认证方式分段控件 + 对应表单 + "取消（保留缓存）"文字链接。

### S5 设置

分组玻璃卡（SectionHeader：13sp tertiary 大写风格标题）：

1. **主机**：主机列表入口（S4）
2. **认证与令牌**：双令牌开关、rw 上传前确认开关、令牌管理页入口（S6）
3. **安装**：安装器类型四选一（系统默认/Shizuku/Root/第三方）+ 选择第三方安装器 App（包名行 + "选择应用"按钮）+ 各后端就绪状态徽标（Shizuku：就绪/未运行/未安装）
4. **下载与刷新**：仅 WiFi 下载开关、刷新防抖阈值、探测超时
5. **配置**：导出配置（含令牌显式开关）、导入配置
6. **关于**：版本、Gitea 实例信息、开源说明（组件来源声明）

### S6 令牌管理

- 令牌卡片列表：令牌名 + scope 徽标（ro=success / rw=accent）+ 创建时间 + "吊销并重建"次按钮
- 页顶说明卡（GlassPanel）：双令牌机制一句话说明 + Gitea 网页端令牌管理路径提示

---

## 6. 组件库清单与规格

| 组件 | 类型 | 规格 |
|---|---|---|
| GlassPanel / GlassPanelStrong | 移植 | 26dp 圆角、渐变描边、shadow 18/12dp；strong 用于表单/弹层 |
| GlassListCard | 移植改造 | GlassPanel + 高 ~84dp 行布局 + 右侧槽位（徽标/按钮） |
| GlassField | 移植 | fieldFill 填充 + fieldBorder 描边 + 12dp 圆角；错误态描边转 danger |
| PrimaryButton | 移植 | 52dp 高，primary→primaryDeep 渐变，26dp 圆角，白字 |
| SecondaryButton | 移植 | 44~48dp 玻璃质感，主色文字 |
| StatusPill | **新增** | 44dp 玻璃横条：地址徽标 + 数据时间 + 刷新按钮；内嵌 2dp 细进度条 |
| Badge / Dot | **新增** | accent 圆点（有更新）、success 文字徽标（已安装/当前）、danger 徽标（错误）；全圆角，高 22dp |
| InlineNotice | **新增** | 卡内嵌警告条：danger/Container 底色 + 12sp 文字，用于签名冲突等 |
| UploadProgressCard | **新增** | 玻璃卡：环形/条形进度 + 百分比 + 速度 + 取消按钮 |
| EmptyState | **新增** | 玻璃卡居中：线性插画图标 + 标题 + 说明 + 一个主按钮 |
| SectionHeader | 移植样式 | 13sp tertiary，字距略宽 |
| SwitchChip / Segmented | 移植 Inputs | 玻璃底 + primary 选中态 |
| TimelineCard | **新增** | S2 版本卡：左缘 3dp primary 竖线 + 玻璃卡体 |
| AppIconPlaceholder | **新增** | 无图标 APK 占位：primary 浅底圆形 + 首字母 |

---

## 7. 状态与反馈规范

| 维度 | 规范 |
|---|---|
| 点击反馈 | 无涟漪；按压缩放至 0.97 + alpha 0.9，spring 回弹 |
| 列表滚动 | ElasticScroll 两端回弹 |
| 页面切换 | Compose Navigation 默认 + 淡入淡出（150~200ms） |
| 加载 | 优先展示缓存（秒开）；StatusPill 细进度条表达刷新中；首次无缓存用骨架屏（玻璃卡灰脉动） |
| 空态 | 三要素：图标 + 一句话说明 + 行动按钮；不出现在正文流中间 |
| 错误 | 就地显示（StatusPill/InlineNotice/卡片转 danger），不用全屏错误页；所有错误必须可重试 |
| 成功 | 上传/安装成功用 successContainer 底色卡片短暂展示（3 秒）后折叠 |
| 危险操作 | 删除主机/吊销令牌：玻璃确认弹层（strong 面板），危险按钮用 danger 色文字按钮 |

---

## 8. 深色模式

- 深浅主题切换跟随系统（设置中可强制固定）。
- 第 3.2 节深色色板为 ShuYing 已验证的完整映射，**逐令牌对应替换**，不做局部混用。
- 深色下投影 elevation 降至 12dp（ShuYing GlassPanel 现有行为），blob 光斑透明度相应降低（沿用其 AuroraBackground 实现）。
- 深色验收：所有玻璃卡在 `#060B14`~`#070D18` 渐变背景上均有可辨识边界；三级文字对比度达标。

---

## 9. UI 验收标准

- [ ] 视觉一致性：任意页面截图中，背景渐变、玻璃卡、按钮、导航与 ShuYing 预览图（`preview-final.png`）风格肉眼一致；并排对比无明显风格漂移
- [ ] 组件来源：第 6 章标注"移植"的组件参数（圆角/投影/色值/动画曲线）与 ShuYing 源码逐项一致，未引入新视觉语言
- [ ] 四个底部导航 Tab 全部可用，导航胶囊选中态动画与 ShuYing 一致（spring 缩放 + 淡入指示）
- [ ] 未配置主机时：应用/追踪/上传三 Tab 显示引导空态，且空态提供到设置的唯一明确路径
- [ ] StatusPill 正确反映：生效地址（主/备）、数据时间、刷新中/失败四态，且刷新不阻塞滚动
- [ ] S1 列表缓存秒开；三态（加载/空/错误）均按第 7 章规范呈现，无白屏
- [ ] S2 修改日志 Markdown 折叠/展开流畅；签名不一致 InlineNotice 在头卡正确出现
- [ ] S3 五步上传单页渐进展开；进度/取消/失败清理三个交互全部可用
- [ ] 深浅主题切换无残留浅色卡片/深色文字错配；深色下全部文字对比度可读
- [ ] 无涟漪点击全局生效（长按列表项无涟漪波纹）
- [ ] 72dp 以下点击目标不出现（遵循 48dp 最小触达）

---

## 10. 开放问题

1. "追踪"Tab 与"应用"Tab 信息有重叠（都是应用列表视角）——候选方案：Tab 2 改为"更新中心"（仅显示有更新的应用 + 一键全部更新），待原型评审定夺。
2. 首页仓库卡片是否需要支持"按仓库折叠多应用"（apps 仓库一个仓库含 10 个应用条目）——倾向卡片内第二层"条目 chips"，待确认交互成本。
3. 应用图标取自 APK 解析结果，远端 Release 附件不含图标时详情页头卡的图标来源（本地已装缓存 vs 占位图）——倾向本地缓存优先。
4. 深色模式是否首版就交付（色板已备齐，工作量增量小）——建议首版包含。

---

## 附：ShuYing 设计资产索引（移植源）

- 色板：`ShuYing/app/src/main/java/com/shuying/app/ui/theme/Color.kt`（浅色/深色两套，本文件第 3 章为其全量快照）
- 主题：`ui/theme/Theme.kt`（LocalPalette）
- 背景渐变：`res/drawable/bg_window.xml`（#EDF4FF → #F8FBFF → #E7F0FC）与 AuroraBackground（动态 blob）
- 玻璃面板：`ui/components/Glass.kt`（GlassPanel、clickableNoRipple）
- 悬浮导航：`ui/navigation/FloatingNavBar.kt`
- 界面实拍参照：`ShuYing/preview-final.png`、`preview-more.png`（密码生成页，展示了玻璃卡、滑杆、开关、主/次按钮、悬浮导航的最终效果）
