# Development history, design principles, and roadmap

This document consolidates the recent development direction and records what is implemented, experimental, and still planned. It is a project note, not a claim that every device or protocol path has passed end-to-end validation.

## Project purpose and core capabilities

PhairPlay is an open-source Android receiver and ambient display. Its main purpose is to let a phone, tablet, or TV act as a local-network AirPlay destination, while also providing an always-visible clock and optional information cards. The existing AirPlay receiver includes discovery, pairing/session handling, audio and mirroring paths, media metadata/artwork, and receiver-side volume. Protocol support and performance can vary across senders and Android devices; see [Testing](TESTING.md) and [Architecture](ARCHITECTURE.md).

The current product direction is one adaptive Android app for phones, tablets, Google/Android TV, and Fire TV. The clock, device, playback, information, and settings screens should remain legible and navigable across portrait, landscape, large text, touch, and TV remote input. AirPlay reception and playback stability remain the compatibility baseline when adding display and integration features.

PhairPlay is an independent fork. Keep project scope and compatibility statements current in the README and release notes. Never infer support solely from a successful build.

## Recent development history

| Area | Direction and work recorded | Current status |
|---|---|---|
| Phone and tablet support | Consolidated toward a universal package, responsive layouts, and tablet-specific spacing; retained TV focus and remote navigation. | Implemented in the current development line; device behavior still requires per-device validation. |
| Clock and information display | Added more display options, page switching in playback, system-following 12/24-hour time, seconds, and screen-size/orientation checks. | UI and automated checks exist; seconds and clipping should continue to be checked on physical devices and font scales. |
| Playback screen | Centered portrait content, moved volume to a horizontal bottom control, aligned return and page controls, and made carousel edges easier to read. | Implemented in the development line; keep the screen usable with long titles and narrow windows. |
| Visual system | Unified control shape, spacing, focus treatment, contrast, icons, app naming, and round/square launcher icon support. | Ongoing refinement; use the rules below for new screens. |
| Ambient wallpaper | Designed a slow continuous color transition, subtle position drift to reduce static pixel exposure, and album-art-derived colors while music plays. | Present in the development line; animation smoothness, visibility, and burn-in mitigation require ongoing device checks. This is not a guarantee against display wear. |
| Information sources | Added direct read-only iCloud Calendar (CalDAV) configuration and Home Assistant REST access; Mi Home can be reached through Home Assistant entities. | Integration paths are opt-in and require account/device testing. Credentials and calendar content must remain private. |
| AirPlay reverse control | Explored event-channel MediaRemote commands and legacy DACP. Added command availability/state handling and sender-reported supported-command interpretation. | Transport and parsing paths are implemented; a successful write is not proof of sender action. Test play, pause, previous, and next against real senders before calling the path verified. |
| Additional receiver protocols | Documented Google Cast, Miracast, and a possible UPnP/DLNA MediaRenderer. | Keep unfinished media paths explicitly marked **Experimental / 实验**; the control plane alone is not full media support. |

For the detailed reverse-control evidence, command mapping, volume behavior, and live verification checklist, see [AirPlay remote-control research](AIRPLAY_REMOTE_CONTROL_RESEARCH.md). For rationale behind the adaptive visual direction, see [ADR-003](decisions/ADR-003-ui-design.md). For the separate DLNA study, see [ADR-004](decisions/ADR-004-dlna-mediarenderer-roadmap.md).

## UI design principles and copy rules

1. **Keep the primary task clear.** AirPlay connection and playback are first-class; the ambient clock and optional information should not obscure them. Do not describe the app as “adding a tablet to a sound space.” Explain what a control does in direct, compact language.
2. **Adapt structure, not just scale.** Use available width and orientation to choose sensible layouts. Check phone, tablet, TV, portrait, landscape, and large font configurations. Keep controls reachable and information readable.
3. **Use one component language.** Buttons, cards, focus rings, icons, spacing, corner treatment, and emphasis colors should behave consistently. Keep the return action stable while secondary page choices can scroll; fade clipped horizontal edges where content continues.
4. **Respect the platform and input.** Support touch, keyboard, and TV remote focus. Use system time conventions for AM/PM or 24-hour display. Provide clear focus and state feedback.
5. **Protect ambient displays.** Keep movement and color changes subtle and continuous, avoid rapid flashing, and provide a dim/idle mode. Slow motion may reduce static-image exposure but cannot promise burn-in prevention.
6. **Write for the screen.** Support Chinese and English first. Use concise labels; put explanations in secondary text. Clearly label unfinished controls, integrations, and receiver paths “Experimental / 实验,” and explain their limits nearby.
7. **Treat privacy as part of the UI.** Ask only for credentials needed for a selected integration, explain that direct iCloud access uses Apple CalDAV, store secrets with Android Keystore-backed protection, and provide a way to disable and clear account configuration.
8. **Design icons for launcher masks.** Keep the main symbol centered and recognizable in both round and square adaptive-icon masks.

These principles draw on Android guidance for adaptive layouts and large screens and Apple’s published Human Interface Guidelines for simplicity, consistency, flexibility, and clarity. They are references for a native Android app, not a claim that PhairPlay implements Apple’s interface. At the time of writing, the project uses currently published guidance rather than claiming an unpublished iOS 27-specific design specification.

## Integration research and status

### Home Assistant and Mi Home

Home Assistant is the preferred bridge for smart-home data. The direct integration uses the HA REST API and a long-lived access token; users can select suitable entities for display. Mi Home information can be exposed through Home Assistant integrations/entities where the user's Xiaomi region and device support allow it. Direct Mi Home account/cloud coupling is not required for this route. Keep all integrations optional and tolerate unavailable entities or an offline HA server.

**Experimental / 实验:** Validate representative sensors, climate states, and supported control actions against real HA installations. Clearly distinguish a display-only sensor from an actionable control. Do not promise that every Mi Home device or region is available through HA.

### Direct iCloud Calendar

PhairPlay includes a read-only CalDAV client for iCloud Calendar. Setup uses the Apple ID address and an app-specific password, discovers calendars, and lets the user choose calendars to display. This avoids requiring Home Assistant for calendar display. It does not sync or edit events. Calendar data and credentials must not appear in logs, screenshots, analytics, or release notes.

**Experimental / 实验:** Complete real-account tests for app-specific password setup, multi-calendar selection, recurring events, time zones, daylight-saving transitions, revoked credentials, and error recovery. Reminders are not included in the current direct iCloud Calendar path; assess whether Apple exposes an appropriate supported CalDAV feed before planning them as a feature.

### Other data sources

Weather remains an optional network-backed display. Keep source adapters small and isolated from the playback service. Any future source should state permissions, network requirements, refresh behavior, cached/offline behavior, and whether it can control a device.

## Roadmap

### Next

- Verify the current universal APK on representative phone, tablet, and TV hardware in both orientations; include larger system font settings and clock seconds.
- Run live AirPlay sessions with iOS/iPadOS and macOS senders to confirm reverse controls change sender state, not merely that a command is sent.
- Exercise iCloud Calendar setup and display with a real Apple account using an app-specific password; review error messages and secret clearing.
- Validate HA entity refresh and optional display behavior, then test Mi Home devices only through HA integrations available to the user.
- Check album-art color extraction and continuous wallpaper transitions during playback and after disconnect; verify idle/dim settings.
- Keep release notes tied to actual code and device evidence; separate build, UI, connection, playback, and synchronization results.

### Later

- Assess a richer information-card system with user-selected cards, sensible page grouping, and persistence of page order.
- Evaluate iCloud Reminders access separately from read-only calendar display, based on supported interfaces and account-security constraints.
- Complete media playback paths for Google Cast and Miracast before presenting them as ready; evaluate UPnP/DLNA after documenting a focused interoperability test plan.
- Improve accessibility, localization completeness, and adaptive icon polish without increasing dependency footprint unnecessarily.

Do not copy whole third-party projects or vendor dependency caches to add an integration. Prefer documented protocols, minimal maintained libraries when necessary, and clear attribution/license notices.

## Release 1.0.86 (versionCode 89)

This is a universal device-validation **debug** APK distributed as a GitHub prerelease. It is intended for compatibility feedback, not presented as a production/Play Store release.

### Changes

- Add a Home Assistant management page with local discovery, guided long-lived-token setup, entity browsing, and common controls.
- Show the configured Home Assistant dashboard as its own playback page, without album, lyric, or track details.
- Combine compact clock, agenda, and weather details on the overview page; reserve page switching for larger information views.
- Add charging state and estimated time to full charge, plus a 24-hour precipitation outlook and expected rain time. Weather-source alert availability is explained in Settings.
- Simplify app and playback navigation labels, and improve album-art changes between tracks.
- Center launcher icon artwork across adaptive, monochrome, and legacy vector resources.

### Validation

- Built version 1.0.86 (`versionCode` 89) as a universal debug APK. The revised navigation, overview, playback page, and Home Assistant dashboard presentation were checked on the Lenovo TB-J607F tablet on the immediately preceding 1.0.85 build; 1.0.86 itself has not been separately installed.
- This check does not establish complete AirPlay playback/control, iCloud account, weather-alert, or cross-device validation; see [Testing](TESTING.md).
- Artifact: `PhairPlay-1.0.86-universal-debug.apk` (universal debug package). No signing secrets or device-specific certificate details belong in the repository or release text.

## Release 1.0.85 (versionCode 88)

This release candidate is a universal **debug** APK signed using the workspace’s existing shared signing configuration. It is intended for installation and compatibility feedback, not presented as a production/Play Store release.

### Changes

- Adapt the app and clock/playback surfaces for phone and tablet use while retaining TV navigation and a universal package.
- Improve playback-page layout, page navigation, bottom volume control, control styling, app icon resources, and English/Chinese user-facing strings.
- Add ambient display color behavior tied to album artwork and slow background movement.
- Add optional Home Assistant and direct iCloud Calendar integration settings and clients.
- Improve AirPlay event-channel/DACP remote-control handling and synchronization/service behavior.
- Add or update tests and maintenance/testing documentation; clarify unfinished Cast/Miracast/DLNA work.

### Validation

- The 1.0.85 (`versionCode` 88) debug package was built and signed with the workspace’s existing signing identity, then installed on a Lenovo TB-J607F tablet. The receiver service, RTSP endpoint, and mDNS advertisement were checked on that device.
- The project build completed. Unit, instrumented UI, real AirPlay sender control, calendar account, and full playback/synchronization validation are not implied by that build or install; see [Testing](TESTING.md) and the follow-up list above.
- Artifact: `PhairPlay-1.0.85-unified-debug.apk` (universal debug package). No signing secrets or device-specific certificate details belong in the repository or release text.

## References

- [Android adaptive app guidance](https://developer.android.com/develop/adaptive-apps/guides/support-different-display-sizes)
- [Android large-screen media guidance](https://developer.android.com/media/implement/surfaces/large_screens)
- [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Apple Human Interface Guidelines: Design principles](https://developer.apple.com/design/human-interface-guidelines/design-principles/)
- [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/)
- [Project requirements](spec/REQUIREMENTS.md), [project plan](spec/PROJECT_PLAN.md), [testing guide](TESTING.md), [maintenance playbook](MAINTENANCE_PLAYBOOK_ZH.md)

---

# 开发历史、设计原则与路线图

本文汇总近期开发方向，并标明已实现、实验中和计划中的内容。它是项目记录，不表示每种设备或协议路径都已完成端到端验证。

## 项目目标与核心功能

PhairPlay 是一款开源 Android 接收器和环境信息屏。核心目标是让手机、平板或电视成为局域网 AirPlay 接收端，同时提供常显时钟和可选信息卡片。现有 AirPlay 接收端包含设备发现、配对/会话处理、音频与镜像路径、媒体信息/专辑图以及接收端音量。协议支持和性能会因发送端与 Android 设备而异；详见[测试说明](TESTING.md)和[架构文档](ARCHITECTURE.md)。

当前产品方向是为手机、平板、Google/Android TV 和 Fire TV 提供一个自适应 Android 应用。时钟、设备、播放、信息和设置页面都应适应竖屏、横屏、大字号、触控和电视遥控器输入。增加显示和信息源功能时，AirPlay 接收与播放稳定性仍是兼容性基线。

PhairPlay 是独立 fork。README 和发布说明中的范围及兼容性描述要保持准确；构建成功不能单独作为功能已支持的依据。

## 近期开发历史

| 领域 | 开发方向与记录 | 当前状态 |
|---|---|---|
| 手机与平板 | 整合为通用安装包，采用响应式布局和平板间距，同时保留电视焦点与遥控器导航。 | 已在当前开发版本实现；仍需逐设备验证。 |
| 时钟与信息展示 | 增加显示选项、播放页切换、跟随系统的 12/24 小时制、秒钟显示和不同方向/尺寸的检查。 | 已有界面与自动化检查；秒钟和裁切仍应在实机及大字体设置下持续检查。 |
| 播放页面 | 竖屏内容居中、音量滑块移至底部横放、返回与页面按钮排布调整，并改善横向选项的边缘提示。 | 当前开发线已实现；长标题和窄窗口仍需检查。 |
| 视觉系统 | 统一控件形状、间距、焦点、对比度、图标、名称和圆形/方形启动器图标。 | 持续完善；新增页面遵循下方规范。 |
| 环境壁纸 | 设计连续缓慢的颜色过渡、轻微位置漂移，并在播放时依据专辑图取色。 | 当前开发线已包含；动画流畅度、可见性和减轻烧屏效果仍需持续实机检查，不能保证屏幕永不老化。 |
| 信息来源 | 增加只读 iCloud 日历（CalDAV）直连配置和 Home Assistant REST 接入；Mi Home 可经 Home Assistant 实体接入。 | 可选集成路径，需要账号/设备测试；凭据和日历内容应保持私密。 |
| AirPlay 反向控制 | 探索事件通道 MediaRemote 命令和旧版 DACP；增加命令可用状态处理及发送端支持命令解析。 | 传输和解析路径已实现；写入成功不代表发送端执行。将该路径标记为验证完成前，需用真实发送端测试播放、暂停、上一首和下一首。 |
| 其他接收协议 | 记录 Google Cast、Miracast 和 UPnP/DLNA MediaRenderer 的研究方向。 | 未完成的媒体路径应明确标为“实验”；控制面可用不等于媒体接收已完成。 |

反向控制的协议证据、命令映射、音量行为和实机检查表见 [AirPlay 反向控制研究](AIRPLAY_REMOTE_CONTROL_RESEARCH.md)。自适应视觉方向见 [ADR-003](decisions/ADR-003-ui-design.md)，DLNA 单独研究见 [ADR-004](decisions/ADR-004-dlna-mediarenderer-roadmap.md)。

## UI 设计原则与文案规范

1. **突出主要任务。** AirPlay 连接和播放是核心；环境时钟和可选信息不能遮挡它们。不要使用“把平板加入声音空间”描述应用，直接说明控件用途。
2. **改变布局结构以适应设备，而不只缩放。** 根据可用宽度和方向采用合适布局。检查手机、平板、电视、竖屏、横屏和大字号；控件应易于触达，信息清晰可读。
3. **统一控件语言。** 按钮、卡片、焦点框、图标、间距、圆角和强调色的视觉与行为保持一致。返回操作保持稳定，次级页面选项可横向滚动；内容延伸出屏幕时用柔和渐隐提示。
4. **尊重平台习惯和输入方式。** 支持触控、键盘和电视遥控器焦点。AM/PM 或 24 小时制跟随系统设置。清楚呈现焦点与状态反馈。
5. **保护常显屏幕。** 动画轻柔连续，避免快速闪烁，并提供调暗/空闲模式。缓慢移动可能减少静态画面暴露，但不能承诺避免烧屏。
6. **文案适合屏幕阅读。** 当前优先支持中英文；标签简洁，解释放在次级小字中。未完成的控件、集成和接收路径标注“实验”，并在附近说明限制。
7. **把隐私纳入界面设计。** 仅在用户启用集成时索取必要凭据，说明直连 iCloud 使用 Apple CalDAV；使用 Android Keystore 保护凭据，并提供禁用和清除账号配置的途径。
8. **图标适配启动器蒙版。** 主图形应居中，并能在圆形和方形自适应图标蒙版中识别。

这些原则参考 Android 自适应布局和大屏指南，以及 Apple 已公开的 Human Interface Guidelines 中关于简洁、一致、灵活和清晰的建议。它们是 Android 原生应用的设计参考，不表示 PhairPlay 实现了 Apple 界面。本文依据当前已发布的设计指南，不宣称依据尚未公开的 iOS 27 专属规范。

## 集成研究与状态

### Home Assistant 与米家

Home Assistant 是智能家居信息的首选桥接方式。当前直连接入使用 HA REST API 和长期访问令牌，用户选择适合展示的实体。若用户所在地区和设备支持，可通过 Home Assistant 集成/实体展示米家信息；此路径不要求应用直接绑定米家账号或云服务。集成保持可选，并兼容实体不可用或 HA 离线的情况。

**实验：** 使用真实 HA 环境验证常见传感器、温控状态和支持的控制操作。清楚区分只读信息与可操作控件，不承诺每个米家设备或地区都可通过 HA 接入。

### iCloud 日历直连

PhairPlay 包含只读 iCloud CalDAV 日历客户端。设置使用 Apple 账户邮箱和 app 专用密码，发现日历后由用户选择要展示的日历。不需要 Home Assistant 即可展示日历；此功能不编辑或同步事件。日历内容和凭据不得出现在日志、截图、分析或发布说明中。

**实验：** 使用真实账户完成 app 专用密码设置、多日历选择、重复事件、时区、夏令时切换、凭据撤销和错误恢复测试。当前直连 iCloud 日历路径不包含提醒事项；后续应先确认 Apple 是否提供适用且受支持的 CalDAV 数据接口，再决定是否规划。

### 其他信息源

天气仍是可选的联网信息显示。新增信息源应与播放服务隔离，明确权限、网络依赖、刷新行为、缓存/离线表现，以及是否能够控制设备。

## 路线图

### 下一步

- 在有代表性的手机、平板和电视硬件上，横竖屏验证当前通用 APK；覆盖较大的系统字体和时钟秒钟显示。
- 使用 iOS/iPadOS 与 macOS 发送端进行真实 AirPlay 会话，确认反向控制确实改变发送端状态，而非仅成功发送命令。
- 使用真实 Apple 账户及 app 专用密码测试 iCloud 日历配置和显示，检查错误提示与凭据清除。
- 验证 HA 实体刷新和可选信息显示，再根据用户可用的 HA 集成测试米家设备。
- 检查播放和断开后的专辑图取色及连续壁纸过渡，并验证空闲调暗选项。
- 发布说明应对应实际代码和设备证据，并分别报告构建、界面、连接、播放和同步检查结果。

### 后续方向

- 评估更完善的信息卡片系统，允许用户选择卡片、分组页面并保存页面顺序。
- 根据支持的接口和账户安全约束，单独评估 iCloud 提醒事项接入，不与只读日历混为一谈。
- 完成 Google Cast 与 Miracast 媒体接收路径后再标为可用；UPnP/DLNA 应先制定有针对性的互操作测试计划。
- 持续改善无障碍、本地化完整度和自适应图标，同时避免不必要地增加依赖体积。

增加集成时不要复制整个第三方项目或厂商依赖缓存。优先使用有文档的协议；确需依赖时选择精简且维护中的库，并清楚注明归属与许可证。

## 1.0.86 版本（versionCode 89）

该版本是通用设备验证 **debug 调试 APK**，以 GitHub 预发布版形式提供，用于兼容性反馈，不应描述为正式商店版。

### 功能改动

- 新增 Home Assistant 管理页，支持局域网发现、长期令牌引导、实体浏览和常用控制。
- 在播放页独立显示已配置的 Home Assistant 仪表盘，不混入专辑、歌词或歌曲详情。
- 将简要时钟、日程和天气信息合并到概览页；页面切换用于较大的信息视图。
- 显示充电状态、预计充满时间、未来 24 小时降水情况及预计降雨时间；天气源预警能力在设置中说明。
- 精简应用和播放页导航文案，并改善歌曲切换时的专辑图过渡。
- 调整自适应、单色和旧版矢量启动器图标的图形，使其整体居中。

### 验证信息

- 已构建 1.0.86（`versionCode` 89）通用调试 APK。新版导航、概览、播放页和 Home Assistant 仪表盘展示已在前一版 1.0.85 上通过 Lenovo TB-J607F 平板检查；1.0.86 尚未单独安装。
- 此检查不代表 AirPlay 播放/控制、iCloud 账号、天气预警或其他设备均已验证；详见[测试说明](TESTING.md)。
- 安装包：`PhairPlay-1.0.86-universal-debug.apk`（通用调试版）。仓库和发布说明不得包含签名密钥或设备专用证书信息。

## 1.0.85 版本（versionCode 88）

该版本是使用工作区现有共享签名配置签名的通用 **debug 调试 APK**，用于安装和兼容性反馈，不应描述为正式商店版。

### 功能改动

- 调整应用及时钟/播放界面以适配手机和平板，同时保留电视导航和通用安装包。
- 改善播放页布局、页面切换、底部音量控制、控件样式、应用图标资源及中英文用户文案。
- 增加根据专辑图变化的环境屏幕颜色，以及缓慢的背景移动效果。
- 增加可选 Home Assistant 和 iCloud 日历直连接入设置及客户端。
- 改善 AirPlay 事件通道/DACP 反向控制处理，以及同步/服务行为。
- 增加或更新测试、维护和验证文档；明确 Cast/Miracast/DLNA 尚未完成的工作。

### 验证信息

- 已构建并用工作区现有签名身份签名 1.0.85（`versionCode` 88）调试包，并安装至 Lenovo TB-J607F 平板；检查了接收服务、RTSP 端点与 mDNS 广播。
- 项目构建已完成。该构建或安装不代表单元测试、仪器化界面测试、真实 AirPlay 发送端控制、日历账号、完整播放及同步均已验证；详见[测试说明](TESTING.md)和上方后续计划。
- 安装包：`PhairPlay-1.0.85-unified-debug.apk`（通用调试版）。仓库和发布说明不得包含签名密钥或设备专用证书信息。

## 参考资料

- [Android 自适应应用指南](https://developer.android.com/develop/adaptive-apps/guides/support-different-display-sizes)
- [Android 大屏媒体指南](https://developer.android.com/media/implement/surfaces/large_screens)
- [Android 自适应图标](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Apple Human Interface Guidelines：设计原则](https://developer.apple.com/design/human-interface-guidelines/design-principles/)
- [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/)
- [项目需求](spec/REQUIREMENTS.md)、[项目计划](spec/PROJECT_PLAN.md)、[测试说明](TESTING.md)、[维护手册](MAINTENANCE_PLAYBOOK_ZH.md)
