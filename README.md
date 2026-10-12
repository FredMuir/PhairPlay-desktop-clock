# PhairPlay

PhairPlay is a free, open-source AirPlay receiver for Android. The project began with a living-room experience for Android TV and Fire TV, and is expanding to phones and tablets while preserving the existing large-screen experience. Use an iPhone, iPad, or Mac to send supported audio, photos, and screen mirroring over your local network.

```
 macOS (Monterey+)            Android TV / Fire TV
 iOS / iPadOS (16+)           ┌──────────────────────┐
 ┌────────────────┐  AirPlay  │                      │
 │  [Your Screen] │ ────────► │  [Your TV Screen]    │
 │                │           │                      │
 └────────────────┘           └──────────────────────┘
      Click AirPlay →              PhairPlay
      Select your TV →             (this app)
      Done. ✓
```

---

## Project Status — v1.0.42 development branch

The current source is **1.0.42** (`versionCode` 45). This public repository is an independent development fork; it is not the original project's release channel, and no pull request to the original project is planned. Check this fork's [Releases page](https://github.com/FredMuir/PhairPlay-desktop-clock/releases) for packages; source builds may include changes that are not in a release.

The main development goal is broader Android device compatibility without regressing the established tablet and TV layouts. The existing build variants target Google/Android TV (Android 10+) and Fire TV (Android 7.1+). Phone and tablet adaptation is in progress; the presence of Android support in the shared code does not mean every model has been validated. Playback, mDNS discovery, screen sizing, remote-control behavior, and audio synchronization can vary by Android version and vendor. See [docs/TESTING.md](docs/TESTING.md) for the validation scope and report device-specific results separately.

The AirPlay 2 stack includes mDNS advertising (with standby recovery and Wi-Fi multicast lock), RTSP/HomeKit pairing, FairPlay key decryption, H.264 mirroring, AAC-ELD/AAC-LC/ALAC audio, realtime and buffered audio streams, PTP timing for multi-room playback, artwork/metadata display, and remote-control paths over MediaRemote and DACP. Playback, protocol, and synchronization support vary by Android device and sender; consult the testing guide for validation expectations.

The Android app also includes an AirPlay-device discovery/management page and an optional bedside clock with date, calendar, and weather widgets. These screens are being adapted across phone, tablet, and TV window sizes. Weather requires internet access; AirPlay reception works on the local network without a cloud service.

For portable build guidance, validation boundaries, synchronization and UI lessons, and maintenance recommendations, see the [PhairPlay maintenance playbook](docs/MAINTENANCE_PLAYBOOK_ZH.md).

Miracast and Google Cast receiver stacks are in progress (control-plane implemented; media playback pending). In addition, **UPnP/DLNA MediaRenderer** is planned as an upcoming option for Windows ("Cast to Device"), VLC, and mobile UPnP controllers (see [ADR-004](docs/decisions/ADR-004-dlna-mediarenderer-roadmap.md)).

## Features

### AirPlay 2 (fully implemented)
- Screen mirroring from macOS 12+ and iOS/iPadOS 16+ — H.264 hardware decode
- Configurable screen timeout during audio playback and optional bedside-clock keep-awake/dim behavior
- Automatic mDNS recovery after TV wake from standby or Wi-Fi reconnect
- Wi-Fi Multicast Lock held during advertising for reliable device discovery
- Robust socket binding to IPv4 wildcard (`0.0.0.0`) across all Android TV / Fire TV models
- FairPlay session decryption (fp-setup v2/v3 + legacy rsaaeskey) via native libplayfair
- HomeKit-style pairing (Ed25519/X25519) and legacy SRP PIN pairing
- Mirroring audio: AAC-ELD, AAC-LC, ALAC — with independent A/V start/stop
- System audio streaming (ALAC, unencrypted) — reliable path for app audio
- AirPlay video URL mode (`/play` content) + transport controls (play/pause/scrub)
- Now-playing metadata (DMAP) with album artwork overlay
- DACP reverse remote — TV remote controls the sender's playback
- PTP timing and receiver-side scheduling for multi-room playback; UDP audio retransmit for packet-loss recovery
- AirPlay photo receiver — JPEG/PNG from iOS Photos app displayed full-screen
- Access-control lockout after repeated failed pairing attempts

### App & Platform
- Android app with foreground receiver service and status UI; TV/Fire TV are the established build targets, with phone and tablet adaptation in progress
- Multi-protocol receiver architecture: AirPlay 2 active, Google Cast and Miracast in development, plus UPnP/DLNA MediaRenderer planned as an additional option
- Each protocol can be toggled on/off independently in Settings
- Google TV build target: Android 10+; Fire TV build target: Android 7.1+
- Android phones and tablets are an active compatibility goal; check the testing guide for devices actually validated
- Miracast Wi-Fi Direct / WFD advertisement and RTSP control-plane
- Google TV Cast Connect SDK lifecycle (full testing requires Cast app ID)
- Zero ads and zero analytics; core AirPlay reception is LAN-local, while the optional weather widget uses internet
- Open source — Apache 2.0 license

## What PhairPlay Does NOT Do

- **FairPlay DRM content** (Netflix, Disney+, Apple TV+) — Apple DRM; not decryptable by any open-source receiver
- **Apple Music in-app audio** — protected on every AirPlay path; use system audio output instead
- **Cloud/remote streaming** — local network only
- **Miracast / Cast media playback** — control plane is ready; media decode integration is in progress

---

## Requirements

**On your Android device:**
- For the established TV targets: Google/Android TV (Android 10+) or Amazon Fire TV (Android 7.1+)
- Phone and tablet builds are for development and device validation while compatibility work continues
- Connected to the same Wi-Fi network as your Mac or iOS device
- USB/network debugging or sideloading enabled to install development builds

**On your Mac:**
- macOS 12 (Monterey) or later
- Connected to the same Wi-Fi network as your TV

**Network:**
- Both devices on the same subnet (common home router setup works)
- Multicast/mDNS must not be blocked (most home routers are fine)
- 5 GHz Wi-Fi or Ethernet strongly recommended for best performance

---

## Installation

### Option A: Download a Release APK (easiest)

Go to this fork's [Releases page](https://github.com/FredMuir/PhairPlay-desktop-clock/releases) and download an APK for a listed target device:

| APK | Device |
|-----|--------|
| `PhairPlay-vX.Y.Z-googletv.apk` | Google TV, Android TV (Android 10+) |
| `PhairPlay-vX.Y.Z-firetv.apk` | Amazon Fire TV (Android 7.1+) |

Then install it via ADB (see the Sideloading Guide below) or a sideloading app like *Downloader* on Fire TV.

### Option B: Build from Source

1. **Install prerequisites**
   ```bash
   # Install Android Studio from https://developer.android.com/studio
   # Install JDK 17 or later
   ```

2. **Clone the repository**
   ```bash
   git clone https://github.com/FredMuir/PhairPlay-desktop-clock.git
   cd PhairPlay-desktop-clock
   ```

3. **Build the APK**
   ```bash
   # For Google TV / Android TV; can also be used for phone/tablet development validation:
   ./gradlew assembleGoogletvDebug

   # Google TV with a registered Cast App ID:
   ./gradlew assembleGoogletvDebug -Pphairplay.castAppId=<APP_ID>

   # For Fire TV:
   ./gradlew assembleFiretvDebug
   ```
   The APK will be in `app/build/outputs/apk/`.

   To run the same local checks used by CI before testing on a TV:
   ```bash
   ./gradlew :test-runner:test
   ./gradlew :app:lintGoogletvDebug :app:lintFiretvDebug \
     :app:assembleGoogletvDebug :app:assembleFiretvDebug
   ```

4. **Install via ADB**
   ```bash
   # Connect your Android device over USB or enable network ADB
   adb devices

   # Google TV / Android TV (or a compatible Android phone/tablet for development validation):
   adb install app/build/outputs/apk/googletv/debug/app-googletv-debug.apk

   # Fire TV:
   adb install app/build/outputs/apk/firetv/debug/app-firetv-debug.apk
   ```

---

## Sideloading Guide

### Google TV (e.g., Chromecast with Google TV)

1. Go to **Settings → System → About → Android TV OS build** and click it 7 times to enable Developer Options.
2. Go to **Settings → System → Developer Options** and enable **USB debugging**.
3. Note your TV's IP address from **Settings → Network & Internet**.
4. On your Mac/PC, run:
   ```bash
   adb connect <TV-IP>
   adb install app-googletv-debug.apk
   ```
5. Launch PhairPlay from your app list.

### Fire TV (Fire TV Stick, Fire TV Cube, etc.)

1. Go to **Settings → My Fire TV → About** and click **Build** 7 times to enable Developer Options.
2. Go to **Settings → My Fire TV → Developer Options** and enable:
   - **ADB debugging** → ON
   - **Apps from Unknown Sources** → ON
3. Note your Fire TV's IP address from **Settings → My Fire TV → About → Network**.
4. On your Mac/PC, run:
   ```bash
   adb connect <FireTV-IP>
   adb install app-firetv-debug.apk
   ```
5. Launch PhairPlay from **Apps → Your Apps & Games**.

---

## How to Use

1. Launch PhairPlay on the Android receiver. You will see the Waiting Screen with its device name.
2. On your Mac, click the **AirPlay** icon in the menu bar (or go to **System Preferences → Displays → AirPlay Display**).
3. Select your TV from the list (it should appear as your TV's name).
4. Your Mac's screen will appear on the TV instantly.
5. To stop: click the AirPlay icon on your Mac and select "Turn Off AirPlay Mirroring", or just quit PhairPlay on the TV.

---

## Known Limitations

- **Beta software** — the AirPlay 2 stack is complete but real-device validation with various macOS/iOS senders is ongoing. Please report issues.
- **Apple Music in-app audio is not decryptable.** macOS protects it with FairPlay on every AirPlay path. Route the Mac's system audio output instead (works fine).
- **FairPlay-protected video** (Netflix, Disney+, Apple TV+) cannot be mirrored — this is Apple's DRM, not a PhairPlay limitation.
- **Buffered audio (AirPlay 2 type 103)** is accepted but not yet played back.
- **Google Cast** requires a registered Cast app ID for end-to-end testing; see [docs/guides/CAST_APP_ID.md](docs/guides/CAST_APP_ID.md).
- **Miracast** — Wi-Fi Direct and RTSP control plane work; MPEG-TS media decode is future work.
- If your router has **AP isolation** or **multicast filtering** enabled, PhairPlay may not appear in the AirPlay menu. Disable these settings on your router.
- On very busy 2.4 GHz Wi-Fi networks, you may experience latency above 100 ms. Use 5 GHz or Ethernet for best results.
- **PIN auth is optional.** When disabled (default), any device on the same network can mirror to the TV. Enable PIN auth in Settings if you're on a shared network.

For real-device failures, run `tools/collect-device-logs.sh` before restarting the app. It captures package state, memory, CPU, and filtered PhairPlay logs into `device-test-logs/`.

---

## Contributing

Contributions and device reports are welcome! Please read [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) before opening an issue or pull request on this fork. This project is developed independently from the original repository.

Key points:
- Follow the coding rules in CONTRIBUTING.md (file size ≤400 lines soft / ≤550 lines hard max, class comments, test coverage)
- All PRs require passing CI (build + tests + lint)
- Discuss major changes in a GitHub Issue first

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.

---

## Acknowledgments

- [openairplay/airplay-spec](https://github.com/openairplay/airplay-spec) — Community-maintained AirPlay protocol documentation
- [UxPlay](https://github.com/FDH2/UxPlay) — Open-source AirPlay mirror server (reference implementation)
- [RPiPlay](https://github.com/FD-/RPiPlay) — AirPlay mirroring for Raspberry Pi (reference implementation)
