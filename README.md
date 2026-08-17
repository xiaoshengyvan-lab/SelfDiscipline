# 自律助手（SelfDiscipline）

一款基于 **Kotlin** 开发的 Android 自律型应用：后台检测手机使用时长，超限时弹出自律提醒，并告诉你**该去做什么**。

## 功能特性

| 需求 | 实现 |
| --- | --- |
| 后台检测手机使用时长 | `UsageStatsManager` 统计当日 0 点起的屏幕前台使用时长，由**前台服务**每 60 秒轮询一次（Android 14+ 使用 `specialUse` 前台服务类型） |
| 使用时长过多时弹出自律提醒 | 使用时长超过阈值后，通过**全屏通知**（full-screen intent）弹出提醒页，锁屏时也能亮屏显示 |
| 提醒显示该去做什么 | 自动推荐**优先级最高**的未完成任务并显示名称与预计时长；无任务时提示添加 |
| 可关闭 / 可开启的开关 | 主界面「自律提醒开关」，关闭后服务停止；开机后若开关为开则自动恢复 |
| 自行输入要做的事情（目录） | 任务清单：添加 / 编辑 / 删除 / 标记完成 / 重置，字段含名称、预计时长、优先级 |
| 自行安排任务时长 | 每个任务可设置预计时长（分钟），提醒页「开始专注」按此时长倒计时 |
| 设置提醒阈值 | 「提醒设置」中可配置每日使用时长阈值（分钟）与两次提醒的最小间隔（分钟） |
| 手机兼容性 | minSdk 26（Android 8.0+），覆盖绝大多数设备；适配 Android 13 通知权限、Android 14 前台服务类型 |

## 技术栈

- 语言：Kotlin 1.9.24
- UI：Material 3（Material Components 1.12）、ViewBinding
- 数据库：Room 2.6.1（KSP 注解处理）
- 异步：Kotlin 协程 + Flow
- 构建：AGP 8.5.2 / Gradle 8.9 / JDK 17

## 获取 APK：GitHub Actions 云构建（免装 Android Studio）

不想在本机装 Android Studio？工程内置了云构建工作流（`.github/workflows/build.yml`），把工程推到 GitHub 即可在云端自动编译出 APK：

1. 在 GitHub 新建仓库（Public/Private 均可），把 `SelfDiscipline` 工程推送上去；
2. 打开仓库 **Actions** 页面，选择 **Build APK** 工作流 → **Run workflow**（或在 `main`/`master` 分支推送、打 `v*` 标签时自动触发）；
3. 运行成功后，在本次运行底部 **Artifacts** 中下载 `app-debug-apk`；
4. 解压得到 `app-debug.apk`，传到手机安装即可（Debug 包已自动签名，个人自用无限制）。

> 说明：仓库未包含 `gradle-wrapper.jar`（二进制），工作流会自动从 Gradle 官方仓库恢复后再编译，无需手动处理。

## 环境要求

- Android Studio **Hedgehog (2023.1.1)** 或更高版本（自带 JDK 17）
- 一台 Android 8.0+ 的真机（模拟器对使用时长统计支持不完整，建议真机测试）

## 打开与构建

1. 用 Android Studio 打开本目录（`File → Open`，选择 `SelfDiscipline` 文件夹）。
2. 等待首次 Gradle 同步完成（会自动下载 Gradle 8.9 与依赖，需联网）。
3. 若提示缺少 `gradle-wrapper.jar`（命令行构建报错时）：
   - 在 Android Studio 中执行一次 `Sync` 即可自动生成；或
   - 在项目根目录用本机 Gradle 执行 `gradle wrapper` 生成。
4. 连接手机（开启 USB 调试）→ 点击 ▶ 运行。
5. 命令行构建：`./gradlew assembleDebug`（Windows 下 `gradlew.bat assembleDebug`），产物在 `app/build/outputs/apk/debug/`。

> 说明：首次同步需要从 Google Maven / Maven Central 下载依赖，请确保网络可访问 `google()` / `mavenCentral()`。

## 首次使用（3 步）

1. **授予「使用情况访问」权限**：点击主界面权限卡片的「去授权」，在系统设置中为本应用打开开关。
2. **添加任务**：进入「任务清单」，点右下角 `+`，输入任务名称、预计时长、优先级（数字越小越优先）。
3. **打开开关并设置阈值**：回到主界面打开「自律提醒开关」；到「提醒设置」中设置阈值（如 60 分钟）与提醒间隔（如 30 分钟），建议同时开启「忽略电池优化」保证后台稳定。

验证提醒效果：主界面底部有「模拟触发自律提醒（测试）」入口，可随时查看提醒页效果。

## 使用说明

### 主界面
- 实时显示今日使用时长与进度条（进度 = 已用时长 / 阈值）。
- 自律提醒开关：开 → 启动前台监控服务；关 → 停止监控。
- 任务清单、提醒设置、模拟提醒入口。

### 提醒页（全屏弹出）
- 显示今日已使用时长，并推荐「下一个最该做的任务」。
- **开始专注**：按任务设置的预计时长倒计时，进度条实时更新。
- **我已完成**：标记任务完成并退出。
- **换一个任务**：跳过当前任务，推荐下一个待办。
- **稍后再说**：关闭提醒（间隔时间内不再打扰）。
- 无待办任务时提示去添加。

### 提醒设置
- 每日使用时长阈值（5–1440 分钟）：达到后触发提醒。
- 提醒最小间隔（5–720 分钟）：避免频繁打扰。
- 忽略本应用自身使用时间：可选。
- 使用情况访问权限 / 电池优化引导。

## 权限说明

| 权限 | 用途 | 说明 |
| --- | --- | --- |
| 使用情况访问（PACKAGE_USAGE_STATS） | 读取手机使用时长 | 系统特殊权限，需在系统设置中手动授予 |
| 通知（POST_NOTIFICATIONS，Android 13+） | 提醒弹出 | 首次启动时申请 |
| 前台服务 / specialUse（Android 14+） | 后台持续监控 | 常驻通知可见 |
| 全屏通知（USE_FULL_SCREEN_INTENT） | 提醒页全屏弹出 | 清单声明 |
| 电池优化 | 后台不被系统杀掉 | 可选，建议开启 |
| 开机自启（RECEIVE_BOOT_COMPLETED） | 重启后恢复监控 | 开关为开时生效 |

## HyperOS / MIUI 专属设置

本应用基于标准 Android API，可在 HyperOS（澎湃 OS，基于 Android 14/15）与 MIUI 上正常运行。但小米系 ROM 对后台服务管控较严（Powerkeeper 省电策略），为保证**后台监控不被杀掉、提醒页正常弹出**，请完成以下设置：

1. **自启动**：设置 → 应用设置 → 应用管理 → 自律助手 → 自启动 → **允许**
   （否则开机自启收不到，且后台服务易被清理）
2. **后台弹出界面**：设置 → 应用设置 → 应用管理 → 自律助手 → 权限管理 → **后台弹出界面 → 允许**
   （否则使用超限时提醒页可能弹不出来）
3. **省电策略**：设置 → 应用设置 → 应用管理 → 自律助手 → 省电策略 → **无限制**
   （默认"智能限制"可能冻结前台服务）
4. **通知权限**：保持开启（Android 13+ 首次启动时申请）。
5. **使用情况访问**：设置 → 应用设置 → 授权管理 → 使用情况访问权限 → 允许自律助手
   （App 内「去授权」按钮可直接跳转）。

> App 已内置「系统适配」卡片（仅小米系设备显示）：进入「提醒设置」页即可看到，提供
> **自启动 / 后台弹出界面 / 省电策略** 一键跳转按钮（无法直达时自动回退到应用详情页）。
> 检测逻辑见 `util/DeviceCompat.kt`（依据 Build 信息与 `ro.miui.ui.version.name` /
> `ro.mi.os.version.name` 系统属性）。

参考：[小米官方 Powerkeeper 管控说明](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1628)、[MIUI→HyperOS 后台服务迁移指南](https://coldfusion-example.blogspot.com/2026/03/migrating-app-background-services-from.html?m=0)

## 常见问题

- **使用时长偏少 / 为 0**：未授予「使用情况访问」权限，或刚安装（统计数据需等待系统生成，一般几分钟后开始累计）。
- **提醒不弹出**：检查通知权限是否授予；检查开关是否打开；检查阈值是否已超过；部分国产 ROM 需在系统设置中允许自启动 / 忽略电池优化。
- **后台被系统杀掉**：在「提醒设置」中开启「允许后台运行」（忽略电池优化），部分 ROM 还需在系统「电池 → 应用管理」中设为不限制。
- **想清空当日统计**：时长按自然日 0 点自动重置，无需手动操作。

## 目录结构

```
SelfDiscipline/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/selfdiscipline/app/
│       │   ├── MainActivity.kt            # 主界面：开关 / 使用时长 / 入口
│       │   ├── data/                      # Room 任务 + SharedPreferences 设置
│       │   │   ├── Task.kt / TaskDao.kt / TaskRepository.kt / AppDatabase.kt / AppSettings.kt
│       │   ├── service/                   # 后台监控
│       │   │   ├── UsageMonitorService.kt # 前台服务：轮询 + 阈值判定
│       │   │   ├── UsageStatsHelper.kt    # 使用时长统计工具
│       │   │   ├── ReminderNotifier.kt    # 全屏提醒通知
│       │   │   └── BootReceiver.kt        # 开机自启
│       │   ├── reminder/ReminderActivity.kt  # 提醒页：建议任务 + 专注倒计时
│       │   ├── task/                      # 任务清单 / 编辑 / 适配器
│       │   ├── settings/SettingsActivity.kt  # 阈值 / 间隔 / 权限
│       │   └── util/TimeFormat.kt
│       └── res/                           # 布局 / 主题 / 矢量图标
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── gradlew(.bat) + gradle/wrapper/
```

## 扩展建议

- 每周使用报告（Room 存储每日汇总）。
- 按 App 分类统计（微信/抖音分别统计）。
- 自律失败惩罚机制（如设置“监督伙伴”）。
- 提醒页接系统日历，推荐任务转为日历事件。
