# PhairPlay

**EN:** PhairPlay is a free, open-source AirPlay receiver for Android. It began as a living-room app for Android TV and Fire TV and is expanding to phones and tablets while preserving the large-screen experience. Send supported audio, photos, and screen mirroring from an iPhone, iPad, or Mac over your local network.

**中文：** PhairPlay 是一款免费的开源 Android AirPlay 接收器。项目最初面向 Android TV 和 Fire TV，当前正逐步适配手机和平板，同时保持大屏设备上的使用体验。你可以通过局域网从 iPhone、iPad 或 Mac 发送受支持的音频、照片和屏幕镜像。

```
 macOS (Monterey+)            Android TV / Fire TV
 iOS / iPadOS (16+)           ┌──────────────────────┐
 ┌────────────────┐  AirPlay  │                      │
 │  [Your Screen] │ ───────►  │  [Your TV Screen]    │
 │                │           │                      │
 └────────────────┘           └──────────────────────┘
      Click AirPlay →              PhairPlay
      Select your TV →             (this app)
      Done. ✓
```

---

## Project Status / 项目状态

**EN:** The latest milestone on this fork's `codex/public-release` branch is **1.0.61-tbj607f** (`versionCode` 64), published as a development prerelease with device-specific debug-signed APKs. It has passed instrumented UI checks on the Lenovo TB-J607F and OnePlus 7 Pro; see the [device validation record](docs/TESTING.md) and [release notes](docs/releases/1.0.61-tbj607f.md). This public repository is an independent development fork, not the original project's release channel; development is not intended to merge back into the original project. Check this fork's [Releases page](https://github.com/FredMuir/PhairPlay-desktop-clock/releases) for packages and installation cautions.

**中文：** 当前 fork 的 `codex/public-release` 分支最新阶段为 **1.0.61-tbj607f**（`versionCode` 64），已作为开发预发布版提供按设备签名的 APK。Lenovo TB-J607F 和 OnePlus 7 Pro 已通过界面仪器测试；详见[设备验证记录](docs/TESTING.md)和[本版说明](docs/releases/1.0.61-tbj607f.md)。这是独立开发的公开 fork，不是原始项目的发布渠道；开发成果也不计划合并回原始项目。请查看本 fork 的[发布页面](https://github.com/FredMuir/PhairPlay-desktop-clock/releases)下载，并先阅读安装和签名注意事项。

**EN:** The main goal is to support more Android devices without regressing the established tablet and TV experience. Current build variants target Google/Android TV (Android 10+) and Fire TV (Android 7.1+). Phone and tablet adaptation remains in progress; the Lenovo TB-J607F and OnePlus 7 Pro have passed the UI and discovery checks listed in [docs/TESTING.md](docs/TESTING.md). Playback, mDNS discovery, screen sizing, remote controls, and audio synchronization can vary by Android version and manufacturer.

**中文：** 项目的主要目标是在保持现有平板和电视体验的同时，支持更多 Android 设备。当前构建变体面向 Google/Android TV（Android 10+）和 Fire TV（Android 7.1+）。手机和平板适配仍在进行；Lenovo TB-J607F 和 OnePlus 7 Pro 已通过 [docs/TESTING.md](docs/TESTING.md) 列出的界面及设备发现检查。播放、mDNS 发现、界面尺寸、遥控器操作和音频同步可能因 Android 版本及厂商而异。

**EN:** The AirPlay 2 implementation includes mDNS advertising, RTSP/HomeKit pairing, FairPlay key decryption, H.264 mirroring, AAC-ELD/AAC-LC/ALAC audio, PTP timing for multi-room playback, artwork and metadata, and remote-control paths over MediaRemote and DACP. Actual support varies by device and sender.

**中文：** AirPlay 2 实现包含 mDNS 广播、RTSP/HomeKit 配对、FairPlay 密钥解密、H.264 镜像、AAC-ELD/AAC-LC/ALAC 音频、多房间播放的 PTP 定时、专辑图与媒体信息，以及通过 MediaRemote 和 DACP 实现的遥控路径。实际支持情况因设备和发送端而异。

**EN:** The app also includes AirPlay-device discovery and management, plus an optional bedside clock with date, calendar, and weather widgets. These screens are being adapted for phone, tablet, and TV window sizes. Weather requires internet access; AirPlay reception itself works over the local network without a cloud service.

**中文：** 应用还提供 AirPlay 设备发现与管理页面，以及可选的床头时钟、日期、日程和天气组件。相关界面正在适配手机、平板和电视窗口尺寸。天气功能需要互联网；AirPlay 接收本身通过局域网运行，不依赖云服务。

**EN:** For build guidance, validation boundaries, synchronization and UI lessons, and maintenance recommendations, see the [PhairPlay maintenance playbook](docs/MAINTENANCE_PLAYBOOK_ZH.md).

**中文：** 构建指南、验证边界、同步与界面经验以及维护建议，见 [PhairPlay 维护手册](docs/MAINTENANCE_PLAYBOOK_ZH.md)。

**EN:** Miracast and Google Cast receiver stacks are in progress (control plane implemented; media playback pending). UPnP/DLNA MediaRenderer is a planned option for Windows “Cast to Device,” VLC, and mobile UPnP controllers; see [ADR-004](docs/decisions/ADR-004-dlna-mediarenderer-roadmap.md).

**中文：** Miracast 和 Google Cast 接收器仍在开发中（控制面已实现，媒体播放尚未完成）。UPnP/DLNA MediaRenderer 计划支持 Windows“投射到设备”、VLC 和手机 UPnP 控制器，详见 [ADR-004](docs/decisions/ADR-004-dlna-mediarenderer-roadmap.md)。

## Features / 功能

### AirPlay 2 (implementation in progress) / AirPlay 2（实现中）

- **EN:** Screen mirroring from macOS 12+ and iOS/iPadOS 16+ using H.264 hardware decoding.
  **中文：** 支持从 macOS 12+ 和 iOS/iPadOS 16+ 镜像屏幕，并使用 H.264 硬件解码。
- **EN:** Configurable screen timeout during audio playback; optional keep-awake and dim behavior for the bedside clock.
  **中文：** 可配置音频播放时的屏幕休眠时间；床头时钟可选保持唤醒和调暗屏幕。
- **EN:** Automatic mDNS recovery after TV standby or Wi-Fi reconnect, with a Wi-Fi Multicast Lock during advertising.
  **中文：** 电视从待机恢复或 Wi-Fi 重连后自动恢复 mDNS；广播期间持有 Wi-Fi 多播锁。
- **EN:** IPv4 wildcard socket binding across Android TV and Fire TV devices.
  **中文：** 在 Android TV 和 Fire TV 设备上使用 IPv4 通配地址绑定套接字。
- **EN:** FairPlay session decryption (`fp-setup` v2/v3 and legacy `rsaaeskey`) through native `libplayfair`.
  **中文：** 通过原生 `libplayfair` 解密 FairPlay 会话（`fp-setup` v2/v3 和旧版 `rsaaeskey`）。
- **EN:** HomeKit-style (Ed25519/X25519) pairing and legacy SRP PIN pairing.
  **中文：** 支持 HomeKit 风格（Ed25519/X25519）配对和旧版 SRP PIN 配对。
- **EN:** Mirroring audio in AAC-ELD, AAC-LC, and ALAC formats, plus unencrypted ALAC system audio.
  **中文：** 支持 AAC-ELD、AAC-LC、ALAC 镜像音频，以及未加密的 ALAC 系统音频。
- **EN:** AirPlay video URL playback with play, pause, and scrub controls.
  **中文：** 支持 AirPlay 视频 URL 播放及播放、暂停和拖动控制。
- **EN:** Now-playing metadata and album artwork; DACP remote control can send TV remote commands back to the sender.
  **中文：** 显示正在播放的媒体信息和专辑图；DACP 遥控可将电视遥控命令发送回发送端。
- **EN:** PTP timing and receiver-side scheduling for multi-room audio, with UDP audio retransmission for packet-loss recovery.
  **中文：** 通过 PTP 定时和接收端调度实现多房间音频，并通过 UDP 音频重传恢复丢包。
- **EN:** Full-screen JPEG/PNG photo reception from iOS Photos and lockout after repeated failed pairing attempts.
  **中文：** 支持全屏接收 iOS“照片”发送的 JPEG/PNG，并在多次配对失败后锁定访问。

### App & Platform / 应用与平台

- **EN:** Android app with a foreground receiver service and status UI. TV and Fire TV are the established build targets; phone and tablet adaptation is in progress.
  **中文：** Android 应用包含前台接收服务和状态界面。电视和 Fire TV 是现有构建目标；手机和平板适配仍在进行。
- **EN:** AirPlay 2 is active; Google Cast and Miracast are in development; UPnP/DLNA MediaRenderer is planned.
  **中文：** AirPlay 2 已启用；Google Cast 和 Miracast 正在开发；UPnP/DLNA MediaRenderer 已列入计划。
- **EN:** Protocols can be enabled or disabled independently in Settings.
  **中文：** 可在设置中分别启用或停用各协议。
- **EN:** Google/Android TV build target: Android 10+; Fire TV build target: Android 7.1+.
  **中文：** Google/Android TV 构建目标：Android 10+；Fire TV 构建目标：Android 7.1+。
- **EN:** Phone and tablet compatibility is an active goal; see the testing guide for devices validated so far.
  **中文：** 手机和平板兼容性是当前开发目标；已验证设备请查看测试指南。
- **EN:** Miracast Wi-Fi Direct/WFD advertising and RTSP control plane; Google Cast Connect SDK lifecycle (end-to-end testing requires a Cast app ID).
  **中文：** Miracast Wi-Fi Direct/WFD 广播和 RTSP 控制面；Google Cast Connect SDK 生命周期（端到端测试需要 Cast 应用 ID）。
- **EN:** No ads or analytics. AirPlay reception stays on the local network; the optional weather widget uses the internet.
  **中文：** 不含广告或分析功能。AirPlay 接收仅使用局域网；可选天气组件需要互联网。
- **EN:** Open source under the Apache 2.0 license.
  **中文：** 基于 Apache 2.0 许可证开源。

## What PhairPlay Does Not Do / PhairPlay 不支持的功能

- **EN:** FairPlay DRM content (Netflix, Disney+, Apple TV+) cannot be decrypted by this open-source receiver.
  **中文：** 本开源接收器无法解密 FairPlay DRM 内容（Netflix、Disney+、Apple TV+）。
- **EN:** Apple Music in-app audio is protected on AirPlay paths; use system audio output instead.
  **中文：** Apple Music 应用内音频在 AirPlay 传输中受保护；请改用系统音频输出。
- **EN:** Cloud or remote-network streaming is not supported; the receiver is for local networks.
  **中文：** 不支持云端或跨互联网远程串流；接收器面向局域网使用。
- **EN:** Miracast and Google Cast media playback are not ready; their control planes are implemented and media decoding remains in progress.
  **中文：** Miracast 和 Google Cast 媒体播放尚未完成；控制面已实现，媒体解码仍在开发。

## Requirements / 使用要求

**Android device / Android 设备**

- **EN:** Established TV targets are Google/Android TV (Android 10+) and Amazon Fire TV (Android 7.1+). Phone and tablet builds are for development and validation while compatibility work continues.
  **中文：** 现有电视目标为 Google/Android TV（Android 10+）和 Amazon Fire TV（Android 7.1+）。兼容性开发期间，手机和平板构建用于开发和验证。
- **EN:** Connect the receiver and sender to the same Wi-Fi network. Enable USB/network debugging or sideloading to install development builds.
  **中文：** 接收器和发送设备需连接同一 Wi-Fi 网络。安装开发版本时，请启用 USB/网络调试或侧载功能。

**Sender / 发送设备**

- **EN:** Mac running macOS 12 (Monterey) or later, or an iPhone/iPad with iOS/iPadOS 16 or later.
  **中文：** 运行 macOS 12（Monterey）或更高版本的 Mac，或运行 iOS/iPadOS 16 或更高版本的 iPhone/iPad。
- **EN:** Connect the sender to the same Wi-Fi network as the Android receiver.
  **中文：** 发送设备需连接到与 Android 接收器相同的 Wi-Fi 网络。

**Network / 网络**

- **EN:** Both devices should be on the same subnet. The router must allow multicast/mDNS; 5 GHz Wi-Fi or Ethernet is recommended.
  **中文：** 两台设备应位于同一子网，路由器需允许多播/mDNS；建议使用 5 GHz Wi-Fi 或以太网。

## Installation / 安装

### Option A: Release APK / 方式 A：下载发布版 APK

**EN:** Visit this fork's [Releases page](https://github.com/FredMuir/PhairPlay-desktop-clock/releases) and download an APK for a listed target device. If there are no releases, build from source using Option B.

**中文：** 前往本 fork 的[发布页面](https://github.com/FredMuir/PhairPlay-desktop-clock/releases)，下载页面列出的目标设备安装包。如果目前没有发布版本，请按方式 B 从源码构建。

| APK | Device / 设备 |
|-----|--------|
| `PhairPlay-vX.Y.Z-googletv.apk` | Google TV / Android TV (Android 10+) |
| `PhairPlay-vX.Y.Z-firetv.apk` | Amazon Fire TV (Android 7.1+) |

**EN:** Install via ADB or a sideloading app such as Downloader on Fire TV.

**中文：** 可通过 ADB 安装；Fire TV 也可使用 Downloader 等侧载应用安装。

### Option B: Build from Source / 方式 B：从源码构建

**EN:** Install Android Studio and JDK 17 or later, then clone this fork:

**中文：** 安装 Android Studio 和 JDK 17 或更高版本，然后克隆本 fork：

```bash
git clone https://github.com/FredMuir/PhairPlay-desktop-clock.git
cd PhairPlay-desktop-clock
```

**EN:** Build the Google TV or Fire TV variant. The Google TV variant may also be used for development validation on compatible Android phones and tablets; this does not imply full device support.

**中文：** 构建 Google TV 或 Fire TV 变体。Google TV 变体也可用于兼容 Android 手机和平板上的开发验证，但这不代表所有设备都已获得完整支持。

```bash
# Google TV / Android TV; also for compatible phone/tablet development validation
./gradlew assembleGoogletvDebug

# Google TV with a registered Cast App ID
./gradlew assembleGoogletvDebug -Pphairplay.castAppId=<APP_ID>

# Fire TV
./gradlew assembleFiretvDebug
```

**EN:** APKs are written under `app/build/outputs/apk/`. The repository's CI checks can be run with:

**中文：** APK 输出到 `app/build/outputs/apk/`。可运行以下命令执行仓库 CI 使用的检查：

```bash
./gradlew :test-runner:test
./gradlew :app:lintGoogletvDebug :app:lintFiretvDebug \
  :app:assembleGoogletvDebug :app:assembleFiretvDebug
```

**EN:** Connect the device over USB (or enable network ADB), confirm it appears in `adb devices`, then install the matching APK:

**中文：** 通过 USB 连接设备（或启用网络 ADB），确认设备出现在 `adb devices` 列表中，然后安装对应 APK：

```bash
adb devices

# Google/Android TV, or a compatible Android phone/tablet for development validation
adb install app/build/outputs/apk/googletv/debug/app-googletv-debug.apk

# Fire TV
adb install app/build/outputs/apk/firetv/debug/app-firetv-debug.apk
```

## Sideloading Guide / 侧载指南

### Google TV (e.g., Chromecast with Google TV) / Google TV（例如 Chromecast with Google TV）

1. **EN:** Open **Settings → System → About → Android TV OS build** and click it seven times to enable Developer Options.
   **中文：** 打开“设置 → 系统 → 关于 → Android TV OS 版本”，连续点击七次以启用开发者选项。
2. **EN:** Open **Settings → System → Developer Options** and enable **USB debugging**.
   **中文：** 打开“设置 → 系统 → 开发者选项”，启用“USB 调试”。
3. **EN:** Find the TV's IP address under **Settings → Network & Internet**, then run:
   **中文：** 在“设置 → 网络和互联网”中查找电视 IP 地址，然后运行：
   ```bash
   adb connect <TV-IP>
   adb install app-googletv-debug.apk
   ```
4. **EN:** Launch PhairPlay from the app list.
   **中文：** 从应用列表启动 PhairPlay。

### Fire TV (Fire TV Stick, Fire TV Cube, etc.) / Fire TV（Fire TV Stick、Fire TV Cube 等）

1. **EN:** Open **Settings → My Fire TV → About** and click **Build** seven times to enable Developer Options.
   **中文：** 打开“设置 → 我的 Fire TV → 关于”，连续点击“版本”七次以启用开发者选项。
2. **EN:** In **Settings → My Fire TV → Developer Options**, enable **ADB debugging** and **Apps from Unknown Sources**.
   **中文：** 在“设置 → 我的 Fire TV → 开发者选项”中启用“ADB 调试”和“安装未知来源应用”。
3. **EN:** Find the Fire TV's IP address under **Settings → My Fire TV → About → Network**, then run:
   **中文：** 在“设置 → 我的 Fire TV → 关于 → 网络”中查找 Fire TV 的 IP 地址，然后运行：
   ```bash
   adb connect <FireTV-IP>
   adb install app-firetv-debug.apk
   ```
4. **EN:** Launch PhairPlay from **Apps → Your Apps & Games**.
   **中文：** 从“应用 → 您的应用和游戏”启动 PhairPlay。

## How to Use / 使用方法

1. **EN:** Launch PhairPlay on the Android receiver. The waiting screen shows the receiver's device name.
   **中文：** 在 Android 接收设备上启动 PhairPlay；等待界面会显示接收器名称。
2. **EN:** On a Mac, click the AirPlay icon in the menu bar or open **System Settings → Displays → Add Display**. On iPhone/iPad, open Control Center and choose **Screen Mirroring** or an AirPlay output.
   **中文：** 在 Mac 上点击菜单栏的 AirPlay 图标，或打开“系统设置 → 显示器 → 添加显示器”。在 iPhone/iPad 上打开控制中心，选择“屏幕镜像”或 AirPlay 音频输出。
3. **EN:** Select the PhairPlay receiver from the device list.
   **中文：** 在设备列表中选择 PhairPlay 接收器。
4. **EN:** To stop, disconnect from the sender's AirPlay menu or close the receiver app.
   **中文：** 如需停止，在发送端的 AirPlay 菜单中断开连接，或关闭接收器应用。

## Known Limitations / 已知限制

- **EN:** Beta software. Real-device validation with different Android devices and macOS/iOS senders is ongoing.
  **中文：** 软件仍处于 Beta 阶段，正在使用不同 Android 设备和 macOS/iOS 发送端进行实机验证。
- **EN:** Apple Music in-app audio is protected on AirPlay paths; route Mac system audio instead.
  **中文：** Apple Music 应用内音频在 AirPlay 传输中受保护；请改用 Mac 系统音频输出。
- **EN:** FairPlay-protected video (Netflix, Disney+, Apple TV+) cannot be mirrored.
  **中文：** 无法镜像受 FairPlay 保护的视频（Netflix、Disney+、Apple TV+）。
- **EN:** Buffered audio (AirPlay 2 type 103) is accepted but not yet played back.
  **中文：** 可以接收 AirPlay 2 type 103 缓冲音频，但尚未实现播放。
- **EN:** Google Cast end-to-end testing requires a registered Cast app ID; see [docs/guides/CAST_APP_ID.md](docs/guides/CAST_APP_ID.md).
  **中文：** Google Cast 端到端测试需要已注册的 Cast 应用 ID，详见 [docs/guides/CAST_APP_ID.md](docs/guides/CAST_APP_ID.md)。
- **EN:** Miracast Wi-Fi Direct and RTSP control plane work; MPEG-TS media decoding is future work.
  **中文：** Miracast Wi-Fi Direct 和 RTSP 控制面可用；MPEG-TS 媒体解码仍待开发。
- **EN:** AP isolation or multicast filtering may prevent discovery. On busy 2.4 GHz Wi-Fi, latency may increase; 5 GHz Wi-Fi or Ethernet is recommended.
  **中文：** AP 隔离或多播过滤可能导致设备无法被发现。繁忙的 2.4 GHz Wi-Fi 可能增加延迟，建议使用 5 GHz Wi-Fi 或以太网。
- **EN:** PIN authentication is optional and disabled by default. Enable it on shared networks.
  **中文：** PIN 验证为可选功能，默认关闭。在共享网络中建议启用。

**EN:** For device failures, run `tools/collect-device-logs.sh` before restarting the app. It writes package state, resource usage, and filtered PhairPlay logs to `device-test-logs/`.

**中文：** 如遇设备故障，请在重启应用前运行 `tools/collect-device-logs.sh`。该脚本会将应用包状态、资源使用情况和筛选后的 PhairPlay 日志写入 `device-test-logs/`。

## Contributing / 参与贡献

**EN:** Contributions and device reports are welcome. Read [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) before opening an issue or pull request on this fork. This project is developed independently from the original repository.

**中文：** 欢迎提交代码和设备测试报告。在本 fork 中创建 Issue 或 Pull Request 前，请先阅读 [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md)。本项目独立于原始仓库开发。

- **EN:** Follow the code and test guidance in `CONTRIBUTING.md`.
  **中文：** 遵循 `CONTRIBUTING.md` 中的代码和测试要求。
- **EN:** Pull requests should pass CI (build, tests, and lint).
  **中文：** Pull Request 应通过 CI（构建、测试和 lint）。
- **EN:** Discuss major changes in a GitHub Issue first.
  **中文：** 较大的改动请先通过 GitHub Issue 讨论。

## License / 许可证

**EN:** Apache License 2.0 — see [LICENSE](LICENSE).

**中文：** Apache License 2.0，详见 [LICENSE](LICENSE)。

## Acknowledgments / 致谢

- [openairplay/airplay-spec](https://github.com/openairplay/airplay-spec) — **EN:** Community-maintained AirPlay protocol documentation. **中文：** 社区维护的 AirPlay 协议文档。
- [UxPlay](https://github.com/FDH2/UxPlay) — **EN:** Open-source AirPlay mirror server used as a reference. **中文：** 开源 AirPlay 镜像服务器，供实现参考。
- [RPiPlay](https://github.com/FD-/RPiPlay) — **EN:** AirPlay mirroring for Raspberry Pi, used as a reference. **中文：** 面向 Raspberry Pi 的 AirPlay 镜像项目，供实现参考。
