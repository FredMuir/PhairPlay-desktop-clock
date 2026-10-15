# Cross-platform Project Study / 跨平台项目调研

**Review date / 调研日期:** 2026-10-16

**Scope / 范围:** Public projects related to always-on clock/information displays, Home Assistant wall panels, and AirPlay audio senders. This is a read-only design survey; it does not claim independent verification of every upstream feature.
公开项目范围包括常显时钟/信息屏、Home Assistant 墙面控制屏和 AirPlay 音频发送端。本页是只读设计调研，不代表已独立验证上游项目宣称的全部功能。

## Executive findings / 调研结论

- **EN:** For PhairPlay’s display experience, the most relevant patterns are system-managed ambient/screen-saver entry, distinct night and daytime layouts, explicit stale-data and recovery states, and restrained burn-in mitigation. These complement PhairPlay’s existing clock and dashboard pages; they do not require copying a kiosk application.
- **中文：** 对 PhairPlay 信息显示最有参考价值的是：利用系统常显/屏保入口、区分白天和夜间布局、明确显示数据过期与恢复状态，并谨慎降低静态内容烧屏风险。这些经验可补充现有时钟与仪表盘页面，无需复制一整套 kiosk 应用。
- **EN:** For AirPlay, Android and desktop senders repeatedly separate discovery, pairing/session control, audio capture/encoding, packet timing, recovery, and user controls. A future sender should be an optional, isolated feature with its own real-receiver validation. The existing receiver must remain independent.
- **中文：** AirPlay 发送端项目普遍将设备发现、配对/会话控制、音频采集/编码、数据包定时、恢复和用户控制分开。未来若增加发送端，应作为可选、隔离的功能，并单独进行真实接收设备验证，不能影响现有接收服务。
- **EN:** No upstream code or dependency was copied. Several attractive projects use copyleft or non-commercial terms. Protocol and product behavior are useful study material; any code reuse needs a separate compatibility and provenance review.
- **中文：** 本次没有复制上游代码或依赖。若干值得研究的项目采用强 copyleft 或非商业许可。协议与产品行为可用于学习；复用代码前仍需单独审查许可证兼容性和代码来源。

## Project comparison / 项目对照

| Project / 项目 | Platform and focus / 平台与用途 | Useful observations / 可借鉴之处 | License shown by project / 项目标注许可证 |
|---|---|---|---|
| [Clock ScreenSaver](https://github.com/slimani-dev/clock-screensaver-native) | Android native clock / Android 原生时钟屏保 | Android system screen-saver integration; separately tunable hours/minutes/seconds and date; preview; configurable small circular drift. Study as optional clock settings and activation patterns. / 系统屏保接入；时分秒和日期分别调节；实时预览；可调的小幅环形漂移。可研究其可选时钟设置和启动方式。 | MIT |
| [WallMode](https://github.com/rvbcrs/WallMode) | Android dashboard kiosk / Android 仪表盘 kiosk | WebView recovery states, dim/ambient screens, profile scheduling, transitions, and temporary priority views. Reference for handling idle and urgent information separately. / WebView 恢复状态、调暗/环境屏、配置排程、转场及高优先级临时页面；适合参考空闲与紧急信息的分层。 | GPL-3.0 |
| [Sideboard](https://github.com/drkpxl/sideboard) | Home Assistant day dashboard / HA 日程信息屏 | Day-part modes (morning/evening/night), alerts only when needed, stale-data age, server-pushed view models, and separate refresh cadence for clock, calendar, and forecast. / 按早晚夜切换模式；仅在有警报时打扰；标注数据陈旧时间；服务端推送视图模型；时钟、日历、天气采用不同刷新频率。 | Repository page did not expose a license badge during review; design reference only until checked. / 调研时页面未显示许可证标识，确认前仅作设计参考。 |
| [ha-paneld / Panel Assistant](https://github.com/panel-assistant/android) | Android wall panels + Home Assistant / Android 墙面屏与 HA | Limits dashboard updates to entities used by the view, reducing work on low-power panels; hardware profiles, diagnostics and guided installation. / 只订阅当前视图用到的实体，减少低功耗屏幕负担；使用硬件配置、诊断和引导安装。 | Apache-2.0 |
| [superdash](https://github.com/supergillis/superdash) | Android HA kiosk / Android HA kiosk | Separates screensaver, kiosk, camera and voice capabilities into modules; first-run sign-in avoids manually creating a long-lived token. Useful setup-flow reference, but broader than PhairPlay. / 将屏保、kiosk、摄像头和语音拆分；初次配置可通过登录授权，减少手动处理长期令牌。适合作为配置流程参考，但功能范围更广。 | GPL-3.0 |
| [CenturyPlay](https://github.com/g8row/centuryplay) | Android AirPlay sender / Android AirPlay 发送端 | README describes system playback capture, Android media output routing, quick settings/widget entry points, transient pairing, NTP-style timing, packet resend, multi-room sync, and reconnect. / README 描述系统播放采集、系统媒体输出路由、快捷设置/小组件、临时配对、NTP 式定时、丢包重发、多房间同步和自动重连。 | AGPL-3.0 |
| [AirSonic](https://github.com/chunguangwei/AirSonic) | Android AirPlay/DLNA sender / Android AirPlay/DLNA 发送端 | Broad from-scratch sender with protocol modules and setup/compatibility notes. Its README explicitly documents device/network limitations; useful as an interoperability checklist. / 自研 AirPlay/DLNA 发送端，包含协议模块和配置/兼容性说明；README 明确列出设备与网络限制，可作为互操作检查表。 | PolyForm Noncommercial 1.0.0 |
| [AirFlash](https://github.com/Ding-Kyoma/AirFlash) | Windows AirPlay 2 sender / Windows AirPlay 2 发送端 | Separates desktop controls from a native audio/protocol engine; documents interface selection, latency tradeoffs, diagnostics, reconnect and stereo-pair behavior. / 桌面控制界面与原生音频/协议引擎分离；说明网卡选择、延迟取舍、诊断、重连和立体声组合行为。 | GPL-3.0 or commercial terms are listed upstream; recheck before reuse. / 上游列有 GPL-3.0 或商业条款；复用前须核对。 |
| [AirPlay 2 Sender C++](https://github.com/akustikrausch/airplay2-sender-cpp) | Cross-platform native sender / 跨平台原生发送端 | Protocol state machine is independent from sockets/timers/threads, making it easier to test with a fake receiver. README discloses code provenance and third-party notices. / 协议状态机与 socket、定时器、线程分离，便于用模拟接收端测试；README 披露代码来源及第三方声明。 | Apache-2.0 for `src/`; portions and bundled components have separate notices. |
| [AirPlay Relay](https://github.com/Celestial5952/ubuntu_air_play) | Ubuntu/Linux sender / Ubuntu/Linux 发送端 | Exposes system audio as a selectable PipeWire/PulseAudio output and continues in a user service after closing the UI. / 将系统音频呈现为 PipeWire/PulseAudio 可选输出；关闭界面后由用户服务继续运行。 | MIT |
| [HomePodLink](https://gitee.com/hello_unibody/home-pod-link) | Android RAOP/AirPlay sender / Android RAOP/AirPlay 发送端 | A protocol-level reference for mDNS, RTSP/HAP pairing, RTP pacing, NTP timing and loss recovery; its README/source crypto descriptions disagree, documented in the separate study. / 可参考 mDNS、RTSP/HAP 配对、RTP 定速发送、NTP 定时和丢包恢复；README 与源码对加密的描述不一致，详见独立研究记录。 | GPL-2.0; no code/dependencies copied. / GPL-2.0；未复制代码或依赖。 |
| [天气墨水屏 / Weather Ink Screen](https://gitee.com/Lichengjiez/weather-ink-screen) | ESP8266 e-paper clock/weather display / ESP8266 墨水屏时钟天气 | Glanceable weather + clock, day/night display modes, low-power/night update controls, NTP clock correction, battery and simple display modes. Hardware-specific and marked GPL-3.0; project notes it is no longer maintained. / 天气与时钟一眼可读；昼夜模式、夜间低功耗更新控制、NTP 校时、电量与简洁显示模式。硬件专用，声明 GPL-3.0，且项目标注已停止维护。 | GPL-3.0 |
| [hass-panel](https://gitee.com/mayue1020/hass-panel) | React Home Assistant dashboard / React HA 仪表盘 | Responsive cards with visibility/order/size controls, custom columns, mobile/desktop layouts and dark mode. Relevant to future HA dashboard presentation and the user's preference to page only for richer views. / 响应式卡片支持显隐、排序、尺寸调整、自定义列、移动端/桌面布局和深色模式。可供未来 HA 页面和“丰富视图再换页”的偏好参考。 | AGPL-3.0 per README; no code reuse. / README 声明 AGPL-3.0，不复用代码。 |
| [Airplay-SDK](https://gitee.com/halo-x/Airplay-SDK) | Android/Windows/Linux AirPlay receiver SDK / Android、Windows、Linux AirPlay 接收 SDK | README advertises portrait signage, reverse control, multi-source/multi-receiver and wired casting. These are product-scope ideas only; repository explicitly says source is for sale and Gitee says no license specified. Claims are unverified. / README 宣称竖屏数字标牌、反向控制、多源多接收端及有线投屏。这些仅作为产品功能范围参考；仓库说明源码需购买，Gitee 标注未指定许可证，相关能力未经验证。 | No license specified; treat as proprietary and do not reuse code. / 未指定许可证，按专有项目处理，不复用代码。 |
| [misti/hass-config](https://gitee.com/misti/hass) | Personal Home Assistant configuration / 个人 HA 配置 | Search-indexed config shows separate tablet and phone dashboards plus swipe paging; a lightweight configuration idea rather than a reusable application. / 检索到的配置展示了平板/手机独立仪表盘及滑动分页；属于轻量配置思路，不是可复用应用。 | License not confirmed. / 许可证未确认。 |

Project feature descriptions above come from their public repository pages and are author-reported. A listed license is not blanket approval to reuse the project, and does not necessarily cover every bundled asset or dependency. / 表中功能来自项目公开页面，属于作者自述。列出的许可证不等于对整个项目及其所有资源/依赖的自动复用许可。

## Practical lessons for PhairPlay / 对 PhairPlay 的实际启发

### Clock and information display / 时钟与信息展示

1. **Keep overview glanceable; make detail optional.** Retain the compact clock/agenda/weather overview. A full HA dashboard or week/month forecast can live on a separate page; avoid turning the default clock into a dense dashboard. / **概览保持一眼可读，详情按需查看。** 保留简洁时钟/日程/天气概览；完整 HA 仪表盘或周/月预报可以单独分页，不把默认时钟变成信息墙。
2. **Treat idle, night, and urgent states as distinct.** Research configurable dimming and a quiet night layout, while allowing genuine severe alerts to appear with clear source and time. Keep alert capability in settings when a data source does not supply alerts. / **区分空闲、夜间和紧急状态。** 研究可配置调暗与夜间简洁布局；真正的严重预警需说明来源与时间。数据源不提供预警时，应继续在设置中说明能力，而非在主屏提示缺失。
3. **Show freshness and recovery.** When HA or weather data is stale/offline, preserve the last good value only with a visible age/state; provide retry/recovery instead of a blank or endlessly spinning panel. / **标明数据新鲜度并提供恢复状态。** HA 或天气离线时，如保留上次数据，应同时显示数据时间/状态；提供明确重试或恢复提示，避免空白页或一直转圈。
4. **Use adaptive refresh, not constant polling.** The clock can tick locally; calendars and forecast can refresh more slowly; HA state should update only as required by the selected view. Verify battery and network impact before changing cadence. / **按数据类型调整刷新频率。** 时钟可本地更新；日历和天气可较慢刷新；HA 状态仅按当前视图需要更新。调整频率前验证电量与网络影响。
5. **Keep burn-in mitigation subtle and honest.** Small slow motion, dimming, and avoiding fixed bright accents are useful options. Do not promise motion prevents OLED aging; check that seconds and changing content do not distract or clip. / **轻量降低烧屏风险并准确描述效果。** 小幅慢速移动、调暗和避免固定高亮元素可作为选项。不要承诺移动能避免 OLED 老化；检查秒钟和内容变化是否造成干扰或裁切。
6. **Treat HA as its own information surface.** Preserve user-authored dashboard layout where possible, avoid overlaying music metadata, and offer a clear path back to the clock/player. For weak tablets, investigate limiting state updates to the selected dashboard. / **将 HA 作为独立信息页面。** 尽量保留用户仪表盘布局；不要叠加歌曲信息；明确提供返回时钟/播放页的操作。低性能平板可研究按当前仪表盘限制状态更新。
7. **Keep display modes purposeful.** The e-paper and wall-panel projects distinguish compact glanceable content from detail views and night mode. PhairPlay can use the same principle while keeping its existing swipe/top-tab navigation: only detailed HA or long-range calendar/weather should require a page change. / **让显示模式各有用途。** 墨水屏和墙面屏项目区分简洁概览、详情和夜间模式。PhairPlay 可借鉴这一点并保留当前滑动/顶端选项卡导航：只在 HA 仪表盘或长周期日程/天气详情时换页。

### AirPlay engineering / AirPlay 工程

1. **Keep protocol stages observable.** Expose separate diagnostics for discovery, pairing, RTSP/session, media transport, timing and recovery, without logging tokens, private keys, pairing secrets or personal media data. / **让协议阶段可诊断。** 分别诊断发现、配对、RTSP/会话、媒体传输、定时和恢复；不得记录令牌、私钥、配对密钥或个人媒体信息。
2. **Test network transitions and discovery recovery on actual devices.** PhairPlay already restarts advertising after Wi-Fi/network loss. Compare across Android versions and vendors; record callbacks, collisions and recovery time. Do not change implementation based only on another app’s report. / **在真实设备上测试网络切换与发现恢复。** PhairPlay 已在 Wi-Fi/网络变化后尝试恢复广播；应跨 Android 版本和厂商记录注册回调、名称冲突及恢复时间。不要只依据其他应用报告改动实现。
3. **Audit parallel Android NSD registrations as a targeted risk.** PhairPlay currently requests `_airplay._tcp` and `_raop._tcp` registrations back-to-back. A PhairPlay-derived project reports overlapping registrations may fail on Android NSD. Treat this as an unverified hypothesis: reproduce on supported OS/device combinations, capture callbacks, then decide whether to serialize registrations. / **针对 Android NSD 并行注册做专项审查。** PhairPlay 当前连续请求注册 `_airplay._tcp` 与 `_raop._tcp`。一个 PhairPlay 衍生项目报告多项注册重叠时 Android NSD 可能失败；应视为未验证假设，先在支持的系统/设备上复现并记录回调，再决定是否串行注册。
4. **Preserve receiver/sender separation.** Any future sender should have separate lifecycle, settings, permissions and validation so sender setup cannot break playback/mirroring reception. / **保持接收端与发送端隔离。** 未来发送功能应有独立生命周期、设置、权限和验证，避免发送端配置失败影响现有播放/镜像接收。
5. **Validate synchronization with measurable evidence.** Compare drift, latency, packet loss, reconnect and track transitions on real devices. A successful handshake alone does not establish stable playback or multi-room sync. / **用可测量证据验证同步。** 在真实设备上比较漂移、延迟、丢包、重连和切歌行为。握手成功本身不能证明播放稳定或多房间同步正常。
6. **Keep dependency weight intentional.** Prefer platform APIs and small maintained components where practical; review transitive native codecs, crypto, licenses, ABI coverage and APK size before adopting anything. Do not copy build caches or entire example apps. / **有意识地控制依赖体积。** 可行时优先使用平台 API 和精简、维护中的组件；采用前检查传递依赖、原生编解码器、密码库、许可、ABI 覆盖和 APK 体积。不要复制构建缓存或整套示例应用。

## Recommended follow-up / 建议后续工作

- **Now / 近期:** Add real-device checks for screen-saver entry/exit, night dimming, screen wake, long-idle stability and visible stale-data state on the Lenovo tablet and OnePlus phone. Record dimensions, OS version and observed behavior. / 在联想平板和 OnePlus 手机上检查屏保进出、夜间调暗、亮屏、长时间运行及数据过期状态，并记录尺寸、系统版本和实际表现。
- **Now / 近期:** Reproduce Android NSD overlapping-registration behavior on supported devices before changing `MdnsService`; include both service types and Wi-Fi reconnect. / 修改 `MdnsService` 前，先在支持设备上复现 Android NSD 多项注册重叠问题；测试两类服务以及 Wi-Fi 重连。
- **Later / 后续:** If Android-to-HomePod output is still desired, first document receiver matrix, capture source, latency target, pairing/security model, privacy, licensing, optional UI and acceptance criteria. Keep this separate from the receiver release path. / 若仍计划增加 Android→HomePod 输出，先记录接收设备范围、采集来源、延迟目标、配对/安全模型、隐私、许可、可选界面和验收标准；研究路线与接收端发布隔离。
- **Later / 后续:** Compare dashboard options with HA users, then improve the native HA page only where it solves a concrete problem such as low-power performance, offline behavior or fast controls. / 与 HA 用户比较仪表盘使用方式，仅在解决低功耗性能、离线表现或快捷控制等明确问题时改进原生 HA 页面。

## Sources / 来源

- [Clock ScreenSaver](https://github.com/slimani-dev/clock-screensaver-native)
- [WallMode](https://github.com/rvbcrs/WallMode)
- [Sideboard](https://github.com/drkpxl/sideboard)
- [ha-paneld / Panel Assistant](https://github.com/panel-assistant/android)
- [superdash](https://github.com/supergillis/superdash)
- [CenturyPlay](https://github.com/g8row/centuryplay)
- [AirSonic](https://github.com/chunguangwei/AirSonic)
- [AirFlash](https://github.com/Ding-Kyoma/AirFlash)
- [AirPlay 2 Sender C++](https://github.com/akustikrausch/airplay2-sender-cpp)
- [AirPlay Relay](https://github.com/Celestial5952/ubuntu_air_play)
- [Weather Ink Screen](https://gitee.com/Lichengjiez/weather-ink-screen)
- [hass-panel](https://gitee.com/mayue1020/hass-panel)
- [Airplay-SDK](https://gitee.com/halo-x/Airplay-SDK)
- [misti/hass-config](https://gitee.com/misti/hass)
- [DeskClock for Dynamic Light Waves](https://gitee.com/hellotool/DeskClockForDynamicLightWavesForAndroid) — surfaced in search, but the page exposed too little README/license detail to include in the comparison. / 搜索中发现，但页面可见的 README 和许可证信息不足，因此未列入对照。
- [iMirror (PhairPlay-derived project; NSD report)](https://github.com/prat3ik/iMirror)
- [HomePodLink RAOP study in this repository](HOMEPODLINK_RAOP_STUDY.md)

This is an exploratory snapshot, not an endorsement, security audit or guarantee that upstream projects remain active. Recheck repository state, license, dependency provenance and device compatibility before implementation. / 本文是阶段性调研，不构成推荐、代码安全审计或上游持续维护保证。实施前重新核对项目状态、许可证、依赖来源和设备兼容性。
