# PhairPlay Testing Guide / PhairPlay 测试指南

**EN:** This guide explains automated tests, validation coverage and manual checks on real devices. Select checks based on the change and target device. UI-only changes need build and visual inspection; protocol, audio, volume or synchronization changes need targeted playback checks. A successful build or screenshot alone does not establish runtime compatibility.
**中文：** 本指南说明自动化测试、验证范围及实体设备手动检查方法。请根据变更内容和目标设备选择检查项：仅修改界面时应构建并目视检查；修改协议、音频输出、音量或同步时还需专项播放检查。构建成功或有截图都不能单独证明运行兼容性。

---

## Device Validation Record / 设备验证记录

### 2026-10-14 — Version 1.0.86 / 1.0.86 版本

Build: `versionCode 89`, `versionName 1.0.86` (`universalDebug`).

| Device / environment | Result |
|---|---|
| Lenovo TB-J607F physical tablet | The revised app navigation, overview, playback controls, and Home Assistant dashboard page were visually checked on the immediately preceding 1.0.85 build. Version 1.0.86 was built but not separately installed. Receiver/service or end-to-end playback validation is not claimed for this release entry. |

**中文对照：**

| 设备 / 环境 | 结果 |
|---|---|
| Lenovo TB-J607F 实体平板 | 应用导航、概览、播放控制和 Home Assistant 仪表盘页面已在前一版 1.0.85 上目视检查。1.0.86 已构建，但未单独安装。本次发布记录不声称接收服务或端到端播放已验证。|

This UI validation does not establish complete AirPlay playback or sender-control behavior, iCloud account setup, weather alerts, or compatibility on other device classes. Use the release-specific notes and open issues below to interpret the results.

**中文：** 此界面验证不代表 AirPlay 播放或发送端控制、iCloud 账号配置、天气预警或其他设备类型的兼容性已完整验证。请结合版本说明和待解决问题理解测试结果。

### 2026-10-13 — Phone and tablet compatibility milestone / 手机与平板兼容性阶段

Build: `versionCode 64`, `versionName 1.0.61-tbj607f` (`googletvDebug`).

| Device / environment | Result |
|---|---|
| Lenovo TB-J607F physical tablet | Full `MainActivityTest` instrumented suite: 9/9 passed. |
| OnePlus 7 Pro (GM1910), Android 14 | Full `MainActivityTest` instrumented suite: 9/9 passed. The Lenovo device list discovered the OnePlus receiver. |
| API 31 tablet emulator | Targeted clock fit test: 1/1 passed. |
| API 31 phone emulator | A full v64 run was interrupted when the AVD disconnected from ADB during test execution; the targeted clock test had passed before disconnect. |

**中文对照：**

| 设备 / 环境 | 结果 |
|---|---|
| Lenovo TB-J607F 实体平板 | 完整 `MainActivityTest` 仪器测试：9/9 通过。 |
| OnePlus 7 Pro（GM1910），Android 14 | 完整 `MainActivityTest` 仪器测试：9/9 通过。Lenovo 设备列表可发现 OnePlus 接收器。 |
| API 31 平板模拟器 | 时钟适配专项测试：1/1 通过。 |
| API 31 手机模拟器 | 完整 v64 测试期间 AVD 与 ADB 断开，测试被中止；断开前时钟专项测试已通过。 |

**EN:** The OnePlus foreground receiver service kept advertising `_airplay._tcp` and `_raop._tcp` while the display was in Doze. This record covers UI, installation and discovery checks; it does not establish end-to-end AirPlay audio playback on v64.
**中文：** OnePlus 屏幕进入 Doze 时，前台接收服务仍持续广播 `_airplay._tcp` 和 `_raop._tcp`。本记录覆盖界面、安装及设备发现检查，但不代表 v64 的 AirPlay 音频端到端播放已验证。

### Release scope and open issues / 本版范围与待解决问题

- The API 31 phone emulator's full v64 run lost its ADB connection during test 7. The targeted clock test passed before the disconnect; this is an incomplete run, not a pass for the full suite.
- AirPlay sender-to-receiver media playback, mirroring, codec interoperability, and multi-room synchronization were not revalidated on v64. Discovery and UI results must not be treated as playback acceptance.
- AirPlay 2 buffered audio (type 103) is accepted by the control path but is not yet played back.
- Google Cast and Miracast media playback remain incomplete; only their control/setup paths are implemented.
- Manufacturer-specific multicast filtering, battery management, unusual aspect ratios, split-screen, and non-default display/font scaling are not exhaustively validated. Discovery may vary by Wi-Fi access point and Android vendor.
- The v64 device-test APKs were signed by different debug identities. Installing an APK signed by a different identity may require uninstalling the existing app, which removes its local app data. Later development APKs use the workspace's shared signing configuration; see [development signing](DEVELOPMENT_SIGNING.md).

**中文对照：**

- API 31 手机模拟器的完整 v64 测试在第 7 项期间失去 ADB 连接。断开前时钟专项测试已通过；完整测试未完成，不能视为整套测试通过。
- v64 未重新验证 AirPlay 发送端到接收端的媒体播放、镜像、编码器互通或多房间同步。设备发现和界面结果不能代替播放验收。
- AirPlay 2 缓冲音频（type 103）会被控制路径接受，但目前不会播放。
- Google Cast 和 Miracast 媒体播放尚未完成，目前实现的是控制/设置路径。
- 厂商多播过滤、电池管理、特殊宽高比、分屏及非默认显示/字体缩放尚未全面验证。设备发现可能因 Wi-Fi 接入点和 Android 厂商而异。
- v64 设备测试 APK 曾使用不同的调试签名身份。安装由其他身份签名的 APK 可能需要卸载现有应用，卸载会删除应用本地数据。后续开发 APK 使用工作区共享签名配置；详见[开发签名说明](DEVELOPMENT_SIGNING.md)。

## How to Run Tests / 如何运行测试

**中文：** 下方命令及详细测试步骤是本仓库自动化测试和实体设备手动验证的操作说明。运行 Android 仪器测试前，请先连接设备或启动模拟器，并确认 `adb devices` 能识别目标设备。

### Unit Tests (no device required)

Unit tests run on your development machine. They mock Android APIs and test the logic in isolation.

```bash
# Run the protocol and parser tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.phairplay.airplay.RtspHandlerTest"
```

Test results are in: `app/build/reports/tests/`

### Instrumented Tests (requires a device or emulator)

Instrumented tests run on a real Android TV device or emulator. They test UI and system integration.

```bash
# Run all instrumented tests (requires connected device via ADB)
./gradlew connectedAndroidTest

# Verify ADB connection first:
adb devices
```

### Lint

```bash
# Run Android Lint
./gradlew lint

# Lint results: app/build/reports/lint-results-googletv-debug.html
```

### Full CI Check (same as GitHub Actions)

```bash
./gradlew :test-runner:test
./gradlew :app:lintDebug :app:assembleDebug
```

GitHub Actions runs the same checks on `main`:

- `JVM protocol tests`: fast protocol/parser/media-unit coverage through `:test-runner:test`
- `Android lint & debug APK`: Android lint and the universal debug APK

The Android Gradle unit-test tasks are not used as CI gates because several tests
exercise Android framework classes that are unstable on the host JVM. Use
`:test-runner:test` for CI-grade protocol/unit coverage and real devices for
Android framework behavior.

---

## What Is Tested

### Unit Tests

| Test Class | Tests | What Is Verified |
|---|---|---|
| `MdnsServiceTest` | 5 | mDNS registration, correct number of services, idempotency, stop behavior, port number |
| `RtspHandlerTest` | 5 | OPTIONS/RECORD/TEARDOWN responses, callback triggering, unknown method handling, SDP parsing |
| `VideoDecoderTest` | 4 | Pre-init safety, double-release safety, empty input handling, basic construction |
| `NetworkUtilsTest` | 5 | Device name reading, fallback behavior, special character sanitization, MAC address format |
| `MainActivityTest` | 3 | Activity startup, WaitingScreen visibility, StreamingScreen hidden at startup |

### What Is NOT Unit Tested (and why)

| Component | Reason | How It's Tested Instead |
|---|---|---|
| MediaCodec H.264 decode | Requires GPU hardware — cannot run on CI | Manual testing on real device (see below) |
| AudioTrack playback | Requires audio hardware | Manual testing on real device |
| NsdManager mDNS broadcast | Requires real network stack | Manual testing: observe macOS AirPlay menu |
| Full RTSP session (network) | Requires real TCP socket pair | Integration tested manually with a Mac |
| AES-CTR encryption (end-to-end) | Requires real audio stream | Manual: verify audio plays correctly |

---

## Manual Test Scenarios

For acceptance testing before a release, perform all scenarios below on both **Google TV** and **Fire TV**.

### Before You Start

Capture a clean baseline before each device run:

```bash
adb devices -l
adb logcat -c
```

Install the correct debug APK:

```bash
# Google TV with AirPlay/Miracast only
./gradlew :app:assembleDebug

# Google TV with Cast enabled for real testing
./gradlew :app:assembleDebug -Pphairplay.castAppId=<APP_ID>

# Google TV
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To get the Cast App ID, register the receiver in the Google Cast SDK Developer
Console and associate the Android TV package `com.phairplay.googletv`. See
[Google Cast App ID](guides/CAST_APP_ID.md).

After a failed run, collect diagnostics before restarting the app:

```bash
tools/collect-device-logs.sh

# Optional: force-stop and relaunch the universal package
PHAIRPLAY_PACKAGE=com.phairplay.firetv tools/collect-device-logs.sh
```

The script writes ADB device details, package info, memory stats, process CPU, and filtered logcat output under `device-test-logs/`.

### Scenario 1: App Startup (Milestone 1)
**Goal:** App starts and shows the WaitingScreen without crashing.

1. Install the debug APK via `adb install`
2. Launch PhairPlay from the TV app list
3. **Expected:** WaitingScreen appears with the TV's device name
4. **Check:** No crash dialog, no FATAL in `adb logcat`
5. **Pass condition:** App remains stable for 30 seconds after launch

---

### Scenario 2: mDNS Discovery (Milestone 2)
**Goal:** macOS discovers PhairPlay in the AirPlay menu within 3 seconds.

1. Ensure Mac and TV are on the same Wi-Fi network
2. Launch PhairPlay on the TV
3. Start a timer on your phone
4. On your Mac, click the AirPlay icon in the menu bar (or System Preferences → Displays → AirPlay Display)
5. **Expected:** TV name appears in the AirPlay menu within 3 seconds
6. Close PhairPlay (press Back on TV)
7. **Expected:** TV name disappears from the AirPlay menu within 10 seconds

---

### Scenario 3: Screen Mirroring Connection (Milestone 3)
**Goal:** Successful RTSP handshake — macOS connects without errors.

1. Launch PhairPlay on the TV
2. On your Mac, select the TV from the AirPlay menu
3. **Expected:** StreamingScreen appears on TV (transitions from WaitingScreen)
4. Check `adb logcat` for RTSP messages:
   - You should see: OPTIONS → ANNOUNCE → SETUP → SETUP → RECORD (all `200 OK`)
   - **Fail condition:** Any `4xx` or `5xx` RTSP error codes in the log
5. On your Mac, stop screen sharing
6. **Expected:** WaitingScreen reappears on TV within 2 seconds

---

### Scenario 4: Video Quality (Milestone 4)
**Goal:** Video plays at ≥25fps with ≤100ms latency.

1. Connect macOS to PhairPlay as in Scenario 3
2. On your Mac, open a terminal and run: `watch -n 0.1 date +%T.%3N` (shows a millisecond clock)
3. Look at the TV screen
4. **Expected:**
   - Frame rate: ≥25fps (video looks smooth, not choppy)
   - Latency: the time shown on TV is ≤100ms behind the time on your Mac
   - No black flashes, no frozen frames during 5 minutes

**Tip:** You can also use macOS built-in Screen Sharing quality tool:
- Hold Option while clicking the AirPlay icon → "Quality" diagnostics

---

### Scenario 5: Audio Sync (Milestone 5)
**Goal:** Audio plays in sync with video.

1. Find an A/V sync test video (search for "A/V sync test clapper board")
2. Connect macOS to PhairPlay
3. Play the test video on your Mac (it should be mirrored to the TV)
4. **Expected:** When the clapper board snaps shut, the sound happens at the same moment visually
5. **Fail condition:** Audio is more than ~40ms ahead or behind the video

---

### Scenario 6: Stability (Milestone 6)
**Goal:** 30-minute continuous stream without disconnect or crash.

1. Connect macOS to PhairPlay
2. Start a timer
3. Keep the Mac active and streaming (move the mouse occasionally, watch something)
4. **Expected after 30 minutes:**
   - TV still shows the streaming screen
   - No crash on TV
   - `adb logcat` shows no FATAL or reconnection events
5. Check RAM: `adb shell dumpsys meminfo com.phairplay.*` should show < 150MB

---

### Scenario 7: Reconnect After Disconnect (Milestone 6)
**Goal:** Automatic reconnect works.

**Test 7a: Sender disconnects:**
1. Connect macOS to PhairPlay
2. On your Mac, click the AirPlay icon and select "Turn Off AirPlay Mirroring"
3. **Expected:** TV shows WaitingScreen within 2 seconds
4. Wait 5 seconds
5. On your Mac, select the TV from AirPlay again
6. **Expected:** Streaming resumes without restarting the app

**Test 7b: Network interruption:**
1. Connect macOS to PhairPlay
2. Briefly disable and re-enable Wi-Fi on your Mac (or unplug/replug Ethernet)
3. **Expected:** PhairPlay reappears in the macOS AirPlay menu within 5 seconds of network restoration

---

## Performance Benchmarks

Run these measurements and record them in the release notes:

```bash
# RAM usage during streaming
adb shell dumpsys meminfo com.phairplay.googletv | grep "TOTAL"

# CPU usage (5-second average) — replace PID with actual process ID
adb shell top -n 5 -p $(adb shell pidof com.phairplay.googletv) | tail -5
```

Target values:
- RAM peak: ≤ 150 MB
- CPU average: ≤ 30%
- Frame rate: ≥ 25 fps
- Latency: ≤ 100 ms

---

## Known Test Limitations

| Limitation | Impact | Workaround |
|---|---|---|
| No CI hardware for video tests | MediaCodec tests can't run in CI | Manual testing required before release |
| FairPlay content can't be tested | Protected content (Netflix, etc.) is blocked by Apple — expected behavior | Document as known limitation in README |
| Wi-Fi quality varies | Latency tests may fail on congested 2.4 GHz | Test on 5 GHz or Ethernet |

## 中文测试操作对照

本节对应上方英文自动化和手动测试说明。测试结果应记录在[设备验证记录](#device-validation-record--设备验证记录)和版本记录中；以下手动场景是发布验收流程，不代表本次预发布版已经全部通过。

### 自动化检查

- **单元测试：** `./gradlew test` 运行两种产品风味的单元测试；也可运行 `./gradlew testGoogletvDebugUnitTest` 或 `./gradlew testFiretvDebugUnitTest`，或用 `--tests` 指定测试类。报告位于 `app/build/reports/tests/`。CI 协议/单元测试以 `:test-runner:test` 为准；部分依赖 Android 框架的测试在主机 JVM 上不稳定。
- **仪器测试：** 连接实体 Android 设备或启动模拟器，然后运行 `adb devices` 确认连接，再运行 `./gradlew connectedAndroidTest`。这类测试覆盖界面及系统集成。
- **Lint 和 CI 构建：** `./gradlew lint` 执行 Android Lint。完整本地 CI 检查命令见上方英文步骤；它会运行协议测试、两个风味的 Lint 和调试 APK 构建。

### 手动测试前准备

每台设备开始测试前保存干净基线：运行 `adb devices -l` 检查设备，并用 `adb logcat -c` 清空旧日志。分别为 Google TV/Android TV 与 Fire TV 构建并安装对应调试 APK。若需要真实 Google Cast 测试，先在 Google Cast SDK Developer Console 注册接收器并配置 Cast App ID；详情见 [Google Cast App ID 指南](guides/CAST_APP_ID.md)。失败后先运行 `tools/collect-device-logs.sh` 收集设备、包信息、内存、CPU 和筛选后的 logcat，再重启应用。若两个产品风味都已安装，可设置 `PHAIRPLAY_PACKAGE` 指定收集哪个包。

### 发布前手动验收场景

下列场景是通用验收流程；发布前应在 Google TV/Android TV 和 Fire TV 上执行。本次 v64 发布记录中未声称这些场景全部通过。

1. **应用启动：** 安装并从电视应用列表启动 PhairPlay；确认 WaitingScreen 显示设备名称、无崩溃对话框或 FATAL 日志，并稳定运行 30 秒。
2. **mDNS 发现：** Mac 与电视接入同一 Wi-Fi，启动应用后检查 AirPlay 菜单；目标设备应在 3 秒内出现。退出应用后应在 10 秒内消失。
3. **屏幕镜像连接：** 从 Mac 选择电视；预期电视切换到 StreamingScreen。日志应记录 OPTIONS → ANNOUNCE → SETUP → SETUP → RECORD 且全部返回 `200 OK`，不得出现 RTSP `4xx`/`5xx`。停止镜像后，电视应在 2 秒内回到 WaitingScreen。
4. **视频质量：** 镜像毫秒计时画面 5 分钟；预期帧率至少 25 fps、延迟不超过 100 ms，且无黑屏闪烁或画面冻结。也可查看 macOS 的屏幕共享质量诊断。
5. **音画同步：** 播放拍板音画同步测试视频；拍板画面闭合时声音应同步，偏差不应超过约 40 ms。
6. **稳定性：** 连续串流 30 分钟；预期无断开或崩溃、没有 FATAL 或重连日志，电视仍显示串流界面，内存低于 150 MB。
7. **断线重连：** 分别测试发送端关闭镜像，以及 Mac 暂时断开再恢复 Wi-Fi/以太网。发送端关闭后电视应在 2 秒内回到 WaitingScreen，5 秒后重新连接无需重启应用；网络恢复后设备应在 5 秒内重新出现在 AirPlay 菜单。

### 性能目标与限制

串流时记录内存和 CPU（英文步骤提供 ADB 命令），目标为峰值内存不超过 150 MB、平均 CPU 不超过 30%、帧率至少 25 fps、延迟不超过 100 ms。CI 没有视频硬件，无法运行 MediaCodec 实测；FairPlay 保护内容无法用于播放验证，这是预期限制；拥挤的 2.4 GHz Wi-Fi 可能导致延迟测试失败，建议改用 5 GHz Wi-Fi 或以太网。
