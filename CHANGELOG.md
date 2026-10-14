# Changelog

All notable changes to PhairPlay will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [1.0.85] - 2026-10-14

> Universal debug prerelease for device validation / 面向设备验证的通用调试预发布版

### Changed / 变更
- Adapt clock and playback surfaces for phones and tablets while retaining TV navigation; improve playback page switching, bottom volume control, shared control styling, and launcher icon resources. / 适配手机和平板时钟与播放界面，同时保留电视导航；改进播放页切换、底部音量控制、控件视觉和启动器图标资源。
- Add slow ambient background movement and album-art-derived color behavior. / 增加缓慢的环境背景移动和专辑图取色效果。
- Add optional Home Assistant and direct read-only iCloud Calendar (CalDAV) integration paths. / 增加可选 Home Assistant 与只读 iCloud 日历（CalDAV）直连接入路径。
- Improve AirPlay event-channel/DACP remote-control handling and service/synchronization behavior. / 改进 AirPlay 事件通道/DACP 反向控制处理及服务/同步行为。
- Update English and Chinese UI copy, tests, and project documentation; mark unfinished receiver paths and real-account/device validation as experimental or pending. / 更新中英文界面文案、测试和项目文档；将未完成的接收路径和真实账号/设备验证标为实验或待验证。

### Validation / 验证
- Built and installed version 1.0.85 (`versionCode` 88) on Lenovo TB-J607F; checked receiver service, RTSP endpoint, and mDNS advertising. / 已构建并安装 1.0.85（`versionCode` 88）至 Lenovo TB-J607F；检查接收服务、RTSP 端点和 mDNS 广播。
- Build completion does not imply complete AirPlay sender-control, iCloud account, playback, synchronization, or all-device UI validation. See [testing](docs/TESTING.md) and the [development roadmap](docs/DEVELOPMENT_HISTORY_AND_ROADMAP.md). / 构建完成不代表 AirPlay 发送端控制、iCloud 账号、播放、同步或所有设备界面均已完整验证。详见[测试说明](docs/TESTING.md)和[开发路线图](docs/DEVELOPMENT_HISTORY_AND_ROADMAP.md)。
- Artifact: `PhairPlay-1.0.85-unified-debug.apk` (universal debug APK). / 安装包：`PhairPlay-1.0.85-unified-debug.apk`（通用调试 APK）。

## [1.0.61-tbj607f] - 2026-10-13

> GitHub prerelease for device testing. It is a development build, not a production or store release.

### Changed
- Add responsive layouts and dimensions for portrait phones, landscape layouts, and sw600dp tablets; keep screen content centered and reserve space for clock seconds and volume controls.
- Improve receiver UI adaptation and service/network behavior for Android phone and tablet hardware.
- Increase Android `versionCode` to 64 (`1.0.61-tbj607f`).

### Fixed
- Prevent the seconds digit from being clipped at the edge of the clock on narrow displays.
- Improve AirPlay service discovery and foreground receiver stability on the OnePlus 7 Pro.

### Validation
- Lenovo TB-J607F physical tablet: all 9 `MainActivityTest` instrumented UI tests passed on v64.
- OnePlus 7 Pro (GM1910), Android 14: all 9 `MainActivityTest` instrumented UI tests passed on v64; the Lenovo device list discovered the OnePlus receiver.
- API 31 emulator: targeted clock-layout test passed. A full v64 phone-emulator run was interrupted when the AVD disconnected from ADB during test execution.
- On the OnePlus, the foreground service remained active and AirPlay mDNS registrations remained visible after the screen entered doze.
- End-to-end AirPlay audio playback was not revalidated as part of this UI/device compatibility milestone.

### Fixed
- Reduce AirPlay multi-room join lag by waiting for advancing `AudioTrack` timestamps before applying phase feedback, then skipping up to one second of already-late startup audio in a bounded phase correction. Small residual errors still use the existing servo.
- Keep the PCM converter's rate clamp aligned with the temporary catch-up limit.

### Documentation
- Add a portable Chinese maintenance playbook covering build, validation, and AirPlay troubleshooting practices.
- Align contributor and testing guidance with the project's CI tasks and documented validation scope.

## [1.0.38-tbj607f] - 2026-10-12

> Development build created as `googletvDebug`; installation and post-fix playback validation were not completed.

### Changed
- Keep the documented receiver defaults when settings have never been saved, and advertise/enforce PIN protection consistently when enabled.
- On large song-start phase error, wait for two advancing AudioTrack timestamps and skip up to one second of already-late source audio once; retain feedback correction for any residual error.

### Validation
- `assembleGoogletvDebug` succeeded. The candidate uses `versionCode 41` and `minSdk 29`.
- The candidate's debug signing key did not match the key on the reference installation, so it was not installed and post-fix playback was not validated.
- The startup diagnosis was based on previously captured device logs; no new playback session was started for this build.
- Increased Android `versionCode` to 41.

## [1.0.37-tbj607f] - 2026-10-11

> Development build; device installation and runtime validation were not completed.

### Fixed
- Preserve the documented default of enabling all three receiver protocols when a preference has not been saved; explicit user choices remain unchanged.
- When PIN authentication is enabled, advertise `pw=true` and the `PINRequired` status bit in AirPlay discovery, and reject HKP 4 transient pairing because it uses a fixed code instead of the PIN shown on screen.

### Validation
- The build did not complete: Kotlin compilation passed, then Java compilation hit a local Gradle cache JAR access error. No APK matching this source/version was produced.
- `:test-runner:test` could not start because required test dependencies were unavailable in the offline build environment.
- Protocol playback, UI behavior, and multi-room synchronization remain unverified.
- Increased Android `versionCode` to 40.

## [1.0.36-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Use the landscape bedside display for a two-column clock and information layout, with a larger clock and more room for date, weather, and agenda.
- Increased Android `versionCode` to 39.

## [1.0.35-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Replace legacy platform media glyphs with consistent vector artwork for previous, play, pause, and next controls.
- Increased Android `versionCode` to 38.

## [1.0.34-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Keep previous, play/pause, and next controls visible throughout an active audio card; disable unsupported controls and explain remote-control connection status.
- Increased Android `versionCode` to 37.

## [1.0.33-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Complete the grouped settings list treatment for the audio sync adjustment row, including its divider and focus surface.
- Increased Android `versionCode` to 36.

## [1.0.32-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Restyle settings as a grouped list with subtle row separators and a full-row high-contrast focus treatment for remote navigation.
- Increased Android `versionCode` to 35.

## [1.0.31-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Match Home screen service button availability and emphasis to the current service state, preventing redundant start/stop actions.
- Increased Android `versionCode` to 34.

## [1.0.30-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Keep the now-playing card responsive to available landscape width and reserve a gap beside the volume control.
- Increased Android `versionCode` to 33.

## [1.0.29-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Adapt the now-playing card width to the available landscape space and reserve a clear touch gap beside the volume rail in compact layouts.
- Keep album-art sizing tied to available height while centering the composition on wide tablet screens.
- Increased Android `versionCode` to 32.

## [1.0.28-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Restyle the LAN device discovery refresh action as a rounded mint control with a high-contrast TV remote focus ring.
- Increased Android `versionCode` to 31.

## [1.0.27-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Clarify the Home screen service-action hierarchy with a high-contrast primary start button, restrained destructive stop styling, and neutral restart styling.
- Use rounded controls and a distinct, high-contrast focus ring for TV remote navigation.
- Increased Android `versionCode` to 30.

## [1.0.26-tbj607f] - 2026-10-11

> UI-focused development build; playback protocol and timing paths are unchanged.

### Changed
- Restyle top-level navigation as a floating glass-like capsule with consistent mint selection and a legible remote-control focus state.
- Expanded navigation pill spacing and use bright text for both focused and selected destinations.
- Increased Android `versionCode` to 29.

## [1.0.25-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

> Functional validation baseline. Subsequent work was UI-focused; playback regression testing was not repeated for each UI-only build.

### Fixed
- Keep AirPlay's lowest non-muted volume above Android's mute index, and map Android's first audible media-volume step back to the playback slider's lowest non-muted position.

### Changed
- Increased Android `versionCode` to 28.

## [1.0.24-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Enable play/pause when the sender supports separate Play or Pause commands but not the Toggle command; choose the direction from the sender's reported playback state.

### Changed
- Increased Android `versionCode` to 27.

## [1.0.23-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Serialize outgoing AirPlay 2 event-channel commands so CSeq assignment and socket write order stay aligned during rapid button presses.

### Changed
- Increased Android `versionCode` to 26.

## [1.0.22-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Replace the solid-square settings-row graphic with a slim rounded chevron for display-name and device-mode navigation.

### Changed
- Increased Android `versionCode` to 25.

## [1.0.21-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Localize bedside weather and agenda status text for Chinese, German, and French instead of embedding Chinese UI strings in the playback view.
- Use the device locale for weather place search and date formatting; use an explicit weather failure state for retries.

### Changed
- Increased Android `versionCode` to 24.

## [1.0.20-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Changed
- Require confirmation before restoring all settings to defaults, with translated dialog copy.
- Increased Android `versionCode` to 23.

## [1.0.19-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Retry weather immediately when the widget is re-enabled after its scheduled retry was skipped while disabled.

### Changed
- Increased Android `versionCode` to 22.

## [1.0.18-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Retry optional bedside weather after a transient network/API failure and log the cause for diagnosis.
- Keep weather results associated with the selected city when settings change during an in-flight request.

### Changed
- Increased Android `versionCode` to 21.

## [1.0.17-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Added
- Parse sender-advertised MediaRemote commands and disable unsupported playback-card controls.
- Reflect sender `PlaybackRate` in the playback-card play/pause icon instead of toggling it optimistically.

### Changed
- Increased Android `versionCode` to 20.

## [1.0.16-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Declare `MODIFY_AUDIO_SETTINGS` explicitly for AirPlay and Android `STREAM_MUSIC` volume synchronization.
- Log each AirPlay-to-Android and Android-to-AirPlay volume update to make device verification observable.

### Changed
- Increased Android `versionCode` to 19.

## [1.0.15-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Keep Android media-volume changes synchronized with the AirPlay receiver and playback-card slider while the app is backgrounded or the screen is off.

### Changed
- Increased Android `versionCode` to 18.

## [1.0.14-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Round AirPlay slider values to the nearest Android media-volume step to avoid a systematic low bias.

### Changed
- Increased Android `versionCode` to 17.

## [1.0.13-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Added
- Optional bedside agenda widget that shows up to two remaining events from today's Android calendar, only after the user enables it and grants calendar access.

### Changed
- Increased Android `versionCode` to 16.

## [1.0.12-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Prevent duplicate Android media-volume writes when the playback slider or iOS changes AirPlay volume.

### Changed
- Increased Android `versionCode` to 15.

## [1.0.11-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Keep the receiver's active AirPlay volume state aligned when Android media volume changes, and report that shared value for RTSP volume queries.

### Changed
- Increased Android `versionCode` to 14.

## [1.0.10-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Map AirPlay's linear -30..0 dB slider consistently to the playback card and Android media-volume index; preserve -144 dB as mute.

### Changed
- Increased Android `versionCode` to 13.

## [1.0.9-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Open and advertise an encrypted AirPlay event port for transient-paired buffered audio sessions so iOS can connect remote controls.

### Changed
- Increased Android `versionCode` to 12.

## [1.0.8-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Allow the position servo to recover from large but valid startup phase errors instead of rejecting them indefinitely.
- Move the volume percentage label above the vertical slider so it no longer overlaps the track.

### Changed
- Increased Android `versionCode` to 11.

## [1.0.7-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Fixed
- Prevent a closing event socket from clearing the state of a newer AirPlay session.
- Close any prior event listener and connection before creating a fresh session event port.

### Changed
- Increased Android `versionCode` to 10.

---

## [1.0.6-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Changed
- Include the receiver's current DNS-SD TXT record fields in the event channel's initial `updateInfo` message.
- Increased Android `versionCode` to 9.

---

## [1.0.5-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Changed
- Send the receiver's AirPlay `updateInfo` on event-channel connection before waiting for sender events or remote commands.
- Include the configured receiver name and display dimensions in that initialization payload.
- Increased Android `versionCode` to 8.

---

## [1.0.4-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Added
- AirPlay 2 MediaRemote play/pause, next, and previous commands over the encrypted event channel, with DACP retained as a legacy fallback.
- Event-channel request handling and RTSP 200 responses to keep the encrypted session alive.
- Event-channel availability now exposes the playback controls even when iOS omits DACP headers.

### Changed
- Increased Android `versionCode` to 7.

---

## [1.0.3-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Changed
- Centered the live volume percentage over the vertical slider while preserving native SeekBar touch handling.
- Widened the slider surface slightly for clearer spacing around the centered value.
- Increased Android `versionCode` to 6.

---

## [1.0.2-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Changed
- Centered the playback-card volume label in its glass control.
- Crossfade album artwork and the cover-derived background on actual track changes while ignoring routine metadata refreshes.
- Increased Android `versionCode` to 5.

---

## [1.0.1-tbj607f] - 2026-10-11

> Device-validation development build for the Google TV flavor; not a GitHub release.

### Changed
- Restored the bedside clock / now-playing entry and refined the player screen controls, artwork identity handling, and display settings.
- Replaced the custom rotated volume touch control with Android's themed native `SeekBar`, preserving the existing media-stream volume integration.
- Increased Android `versionCode` to 4. Google TV and Fire TV APK names include their flavor suffix.

---

## [1.0.0-beta.2] - 2026-10-09

### Fixed
- **Screen awake during playback**: Prevent TV screensaver / sleep from activating during active AirPlay mirroring or audio streaming (`FLAG_KEEP_SCREEN_ON`). Fixes session teardown after ~19 minutes on Fire TV and Google TV (PR #13).
- **mDNS recovery after standby / network drop**: Automatically re-advertise AirPlay via mDNS when network connectivity returns after device standby or Wi-Fi reconnect (PR #13).
- **Wi-Fi multicast packet loss**: Acquire `WifiManager.MulticastLock` while advertising to ensure sender discovery packets are received reliably in power-saving mode (PR #13).
- **iOS 17+ AirPlay 2 mirroring SETUP limit**: Raised RTSP message size cap (`MAX_MESSAGE_BYTES`) from 64 KB to 1 MB, resolving `Request body too large` errors during binary plist negotiation on newer iOS senders (PR #11).
- **IPv4 wildcard socket binding**: Explicitly bind RTSP, event, audio, and mirror sockets to `0.0.0.0` to ensure compatibility with Android TV network stacks (e.g. Philips/Sony) where dual-stack IPv6 sockets dropped IPv4 SYNs (PR #15).

---

## [1.0.0-beta.1] - 2026-06-14

### Added

**AirPlay 2 receiver — full stack**
- Screen mirroring (H.264) from macOS 12+ and iOS/iPadOS 16+ via RTSP on port 7000
- FairPlay session decryption: fp-setup v2 (RAOP audio) and v3 (mirroring/Safari) via native libplayfair (JNI); legacy rsaaeskey RSA-OAEP recovery for AirPort Express compatibility
- HomeKit-style pairing: Ed25519 identity, X25519 ECDH key agreement, controller key persistence (`PairingStore`), failed-attempt lockout
- Legacy SRP-6a PIN pairing with on-screen PIN entry screen (`LegacyPairSetupPin`, `PinScreen`)
- `MirrorStreamServer` + `MirrorCrypto` — interleaved RTP reassembly, AES-128-CTR stream decryption (keystream always advanced to prevent reuse)
- `AudioStreamServer` — mirror realtime audio (type 96): UDP RTP, AES-128-CBC, AAC-ELD/AAC-LC decode via MediaCodec, RAOP retransmit, AudioTrack with volume
- `AlacDecoder` + native libalac — RAOP/SDP audio path: AES-128-CBC (per-packet IV) + Apple's ALAC decoder; decode-health mute guard (wrong key → silence, not static)
- `BufferedAudioServer` — AirPlay 2 buffered audio (type 103) accepted and instrumented
- `AirPlayVideoPlayer` — AirPlay video URL mode (`/play`) + transport controls (play/pause/scrub/stop)
- `NowPlayingInfo` (DMAP parser) + album artwork → `NowPlayingScreen` overlay
- `DacpClient` — `_dacp._tcp` discovery + reverse transport control from TV remote to sender (play/pause/skip/volume)
- `AirPlayNtpClient` — Apple NTP for A/V synchronisation
- `InfoResponder` — `GET /info` capability advertisement (plist)
- `PlistCodec` — Apple binary plist encode/decode
- `RaopRsa` — legacy rsaaeskey recovery (RSA-OAEP, AirPort Express key)
- `StreamStats` — per-session RTP statistics (packet count, duplicates, queue drops)
- `Base64Util` — pure-JVM Base64 so SDP parsing is testable without Android framework
- `SdpParser` — extended: codec/encryption/channel/rate parsing for all AirPlay audio types
- Aspect-fit (letterbox/pillarbox) video rendering with black background in `StreamingScreen`
- Real PNG bitmap launcher icon and TV banner (replaces placeholder XML)
- Mirror Audio toggle and PIN-auth toggle in Settings
- Receiver survives app restart/relaunch; mirroring and audio stop cleanly on app exit

**Native layer**
- CMake build for all ABIs (armeabi-v7a, arm64-v8a, x86, x86_64)
- `fairplay_jni.c` — JNI bridge for `playfair_decrypt` with full null/length/OOM validation
- Apple ALAC decoder (C++, vendored) + JNI bridge (`alac_jni.cpp`)
- Reverse-engineered FairPlay (C, `playfair/`) compiled for all ABIs
- Strict-aliasing fix in `modified_md5.c` (union type-punning) and `sap_hash.c` (memcpy + union)

**Test suite**
- 247 unit tests, 0 failures: FairPlay, RaopRsa, Base64Util, ALAC cookie, DMAP, legacy PIN SRP, audio stream server, RTSP handler, service controller
- Robolectric added for framework-dependent tests (Android Base64, Intent, etc.)

**Release infrastructure**
- `scripts/release.sh` — local release script: builds signed GoogleTV + FireTV APKs, creates git tag, publishes GitHub Release via `gh` CLI (no CI minutes consumed)
- First signed GitHub Release: [v1.0.0-beta.1](https://github.com/mazer666/PhairPlay/releases/tag/v1.0.0-beta.1)

### Changed
- `VideoDecoder`: SPS/PPS-driven reinit on resolution change, self-heal on decoder error, keyframe resync after drops, decoupled network reader (bounded queue, drop-under-load), re-attach to Surface after backgrounding
- `AudioPlayer`: extended to support ALAC and new audio stream types from `AudioStreamServer`
- `RtspHandler`: extended to 700+ lines — handles all AirPlay 2 verbs (ANNOUNCE, SETUP plist+SDP, RECORD, TEARDOWN stream-scoped, GET/SET_PARAMETER, FLUSH, PAUSE, photo PUT/DELETE, `/play`, `/rate`, `/scrub`, `/stop`, `/feedback`, buffered-audio control)
- `AirPlayReceiver`: event channel socket now closed via `use {}` block (fixes file-descriptor leak)
- `SettingsFragment`: mirror audio and PIN-auth toggles added

### Fixed
- `DatagramPacket` length reset before each `receive()` call in `AudioStreamServer` — prevented packet truncation when a smaller packet arrived first
- JNI bridge (`fairplay_jni.c`) now validates input arrays for null, length, and OOM before native access — prevents out-of-bounds reads and native crashes
- Strict-aliasing UB in `modified_md5.c` and `sap_hash.c` — union + memcpy replaces direct `uint32_t*` cast of `unsigned char*`
- `Cipher.getInstance()` moved out of hot path in `AudioStreamServer` (~92 allocations/s → 1 per session)

---

<!-- Format:
## [X.Y.Z] - YYYY-MM-DD

### Added
### Changed
### Fixed
### Removed
-->
