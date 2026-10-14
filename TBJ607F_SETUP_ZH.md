# Lenovo TB-J607F 安装与使用

PhairPlay 是 Android 上的 AirPlay 音频接收端。本指南记录 Lenovo TB-J607F（Android 12）上的兼容性设置；其他 Android 设备请以应用内可用选项和项目发布说明为准。

## 使用

1. 安装并打开 PhairPlay，确认首页显示接收服务正在运行。
2. 让发送端和 Android 接收设备连接到允许设备互相发现的同一 Wi-Fi 网络。
3. 在 iPhone、iPad 或 Mac 的 AirPlay 输出列表中选择接收设备。多房间播放时，由发送端选择其他音响。
4. 可在设置中更改接收端名称、开机启动选项和 AirPlay 广播设备类型。
5. “局域网设备”页面通过 DNS-SD 动态发现接收端，不需要预设 IP 地址。设备发现及多房间播放取决于网络是否允许 mDNS/组播。

## 从源码构建

安装 Android Studio（包含 Android SDK）和项目要求的 JDK，然后在仓库根目录运行 Gradle Wrapper：

```powershell
.\gradlew.bat :app:assembleDebug
```

通用 APK 默认输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

所有 Android 项目的调试包与正式包使用同一工作区密钥。请遵守[统一签名策略](../ANDROID_SIGNING_POLICY_ZH.md)，不要创建或切换其他密钥。

## 兼容性记录

- 曾在 Lenovo TB-J607F / Android 12 上验证应用启动和 AirPlay 服务发现。
- 不同 Android 版本、音频输出设备、发送端和网络配置可能影响播放及同步表现；单台设备的成功结果不代表所有设备均通过验证。
- 项目当前对 AirPlay 2 缓冲式音频、受保护内容和多房间控制的支持范围，请以 README、CHANGELOG 和测试说明为准。

## 上游与许可证

本项目基于 PhairPlay，并保留其许可证与来源说明。更多信息见 [`ATTRIBUTION.md`](ATTRIBUTION.md)。
