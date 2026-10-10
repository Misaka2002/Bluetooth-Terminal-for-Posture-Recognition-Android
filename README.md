# 姿态观察台 · Android

[English](README.en.md)

把手机变成姿态识别设备的蓝牙观察台。连接 HC-06 后，应用会用 3D 人物示意展示当前姿态，并记录每种姿态的累计时间、最长连续时间和出现次数。你可以随时查看原始接收信息，也可以把统计结果导出，留作实验记录。

本项目是 [Windows 姿态蓝牙终端](https://github.com/Misaka2002/Bluetooth-Terminal-for-Posture-Recognition)的安卓版本，沿用同一套八类姿态协议。应用支持 Android 8.0 及以上版本，目前界面为中文。

[查看完整界面预览](docs/images/android-demo.png)（使用模拟数据，由 Android 原生图形测试环境生成）。

## 下载安装

这是测试版。前往 [Release 页面](https://github.com/Misaka2002/Bluetooth-Terminal-for-Posture-Recognition-Android-/releases/tag/v0.1.0)，或直接[下载 APK](https://github.com/Misaka2002/Bluetooth-Terminal-for-Posture-Recognition-Android-/releases/download/v0.1.0/PostureDashboard-Android-v0.1.0.apk)，传到 Android 8.0 及以上的手机安装即可使用，无需从源码构建。

## 开始使用

安装 APK 后，可以先点“演示模式”体验界面，无需连接硬件。演示记录与真实设备记录分别保存。

连接真实设备时，先在手机系统的蓝牙设置中配对 HC-06，再回到应用刷新设备列表，选择已配对设备并连接。Android 12 及以上需要允许“附近的设备”权限。应用使用经典蓝牙 SPP，不支持 BLE；它不扫描附近设备，也不请求定位权限。

手机端不用设置波特率。HC-06 与 STM32 之间的 UART 设置仍需一致，本项目的硬件链路使用 9600、8N1。

连接后，首页会显示当前姿态、连续维持时间、正坐占比、姿态切换次数，以及八种姿态的统计卡片。“显示原始终端”可以展开或收起接收信息，界面宽度保持不变。“清屏”只清除屏幕上的文字，不删除日志或重置统计。

点击“新统计会话”会先保存当前记录，再从零开始统计。蓝牙连接可以继续使用，上一会话未接收完整的半行数据会被丢弃。

## 时间是怎样统计的

统计从手机创建会话时开始。收到完整、有效的姿态数据行后，应用根据到达时间开始或更新当前姿态；相同姿态连续上报会合并成一段。切换姿态时，上一段结束，下一段开始。

断开连接会立即结束当前姿态。如果超过 2 秒没有收到有效分类，此后的时间记为“未识别”，不会一直累加到最后一次姿态上。各姿态占比以有效姿态总时长为分母，不包含未识别时间。出现次数统计具有实际持续时间的姿态段。

设备没有发送 MCU 启动时间，因此应用无法补回连接前的记录。统计使用手机的单调时钟，实际精度仍受发送周期和蓝牙传输延迟影响。

连接期间，前台服务负责接收数据，通知栏会显示记录状态，并提供断开操作。应用会使用部分唤醒锁维持接收线程，断开时释放。不同手机的后台限制可能影响熄屏接收，需要在使用的手机上确认；演示模式主要用于前台体验。

## 保存实验记录

会话保存在手机本机。应用每 30 秒保存一次统计，也会在切换会话、断开连接、离开界面和服务正常结束时保存。强制终止进程可能丢失最近尚未保存的统计；再次启动会建立新会话。

“导出记录”会生成当前会话的 ZIP，可以保存到你选择的位置，或分享给其他应用。“历史记录”可以导出之前的会话。

| 文件 | 内容 |
| --- | --- |
| `summary.csv` | 各姿态累计时长、占比、最长连续时长和段数 |
| `timeline.csv` | 每个姿态段的起止时刻与持续时间 |
| `session.json` | 会话来源、总时长和有效帧数等信息 |
| `received.log` | 带时间戳的完整接收日志 |

CSV 使用带 BOM 的 UTF-8，方便 Excel 显示中文。原始终端最多显示最近 200 行，完整日志仍保存在本机。应用没有网络权限，也不会上传记录。卸载应用会删除私有会话，重要数据请先导出。

## 设备协议

设备发送文本行，以 LF 或 CRLF 结尾。例如：

```text
POSTURE=upright
POSTURE=forward_lean
```

| 设备发送的名称 | 显示姿态 |
| --- | --- |
| `upright` | 正坐 |
| `forward_lean` | 前倾 |
| `backward_lean` | 后仰 |
| `left_lean` | 左倾 |
| `right_lean` | 右倾 |
| `forward_hunch` | 前弯含胸 |
| `left_hunch` | 左弯含胸 |
| `right_hunch` | 右弯含胸 |

左右以人物自身为准。程序也接受 `LABEL=<名称>` 或单独的姿态名称，大小写不敏感。仅包含数字 `ID` 的消息不会计入统计；同一行包含多个分类字段时也不会计入。超过 1024 字符的整行会被丢弃，下一行恢复接收。

## 从源码构建

用 Android Studio 打开项目，准备 JDK 21、Android SDK 35 和 Build Tools 35.0.0。项目已包含 Gradle 8.11.1 Wrapper，使用 Android Gradle Plugin 8.9.2，首次构建需要下载依赖。本机 SDK 路径由 Android Studio 写入 `local.properties`，不提交到 Git。

项目已启用中文路径兼容配置，并在包含中文的 Windows 目录中使用 JDK 21 完成构建验证。Gradle 使用系统编码生成启动参数，Java 源码和测试仍使用 UTF-8。

在 Windows 项目目录中运行：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

macOS / Linux 使用：

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

构建完成后，可将 `app/build/outputs/apk/debug/app-debug.apk` 安装到安卓手机上使用。

`core/` 存放协议解析、计时和报告逻辑；`app/` 存放安卓界面、蓝牙服务、本地保存与导出。自动测试覆盖解析、分片接收、超时、断开和统计一致性。本版本已完成实机功能测试。不同设备的蓝牙与后台运行表现仍需分别确认。

3D 人物图集内置于 APK，复用 Windows 版本素材，详见[素材说明](docs/assets.md)。
