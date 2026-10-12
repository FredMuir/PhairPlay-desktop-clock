# Contributing to PhairPlay

Thank you for your interest in contributing to PhairPlay! This document explains how to contribute and the coding standards we follow.

---

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally
3. **Create a branch** for your change: `git checkout -b feature/your-feature-name`
4. Make a focused change and update the relevant documentation
5. Run the checks relevant to the change (see [Testing Guide](TESTING.md) and the repository CI workflow)
6. Run the flavor-specific Android lint/build tasks when changing Android app code
7. **Commit** with a clear message
8. **Push** to your fork and **open a Pull Request**

---

## Before You Start

- **Check existing Issues** — your idea may already be discussed
- **Open an Issue** before starting large features to discuss the approach
- **Read the spec docs** in `docs/spec/` to understand the requirements

---

## Coding Rules

These rules are enforced in code review. PRs that violate them will be asked to fix before merging.

### RULE 1 — Cohesion and File Size

There is no hard file-length gate. Several existing protocol and UI files are larger than 400 lines because they hold mature behavior with tightly related state.

For new code, keep responsibilities cohesive and avoid adding unrelated behavior to large classes. Split a file when the new boundary makes state ownership, testing, or reuse clearer; avoid mechanical extraction during a protocol or timing fix.

```kotlin
// BAD: One giant class doing everything
class AirPlayStuff { ... }  // 800 lines

// GOOD: Separate classes with clear responsibilities
class MdnsService { ... }   // registers mDNS
class RtspHandler { ... }   // handles RTSP protocol
class VideoDecoder { ... }  // decodes H.264
```

### RULE 2 — Comments

Document public APIs and non-obvious protocol, lifecycle, timing, security, or performance constraints. Explain why unusual behavior is required; do not add boilerplate to obvious code.

```kotlin
/**
 * MdnsService — Advertises PhairPlay as an AirPlay 2 receiver on the local network.
 *
 * WHY: For macOS to show PhairPlay in the AirPlay menu, the device must announce
 * itself using mDNS. Without this, macOS would never know PhairPlay exists.
 *
 * Example:
 *   val mdns = MdnsService(context)
 *   mdns.start()   // device appears in macOS AirPlay menu
 *   mdns.stop()    // device disappears from macOS AirPlay menu
 */
class MdnsService(private val context: Context) {
```

**Avoid** comments that only repeat the code:
```kotlin
// BAD: obvious
// increment i by 1
i++

// GOOD: explains the "why" behind a protocol detail
// AirPlay uses CTR mode specifically because it supports arbitrary-length
// audio frames without padding, which is critical for real-time streaming
```

### RULE 3 — Tests

Add or update tests for meaningful behavior, especially protocol parsing, crypto boundaries, state transitions, and regression-prone timing logic. Not every public method needs a separate test. Choose the narrowest runner that covers the changed behavior; the actual CI gates are listed in [Testing Guide](TESTING.md).

Test files must be named `[ClassName]Test.kt` and placed in the matching test directory:
- Unit tests: `app/src/test/kotlin/com/phairplay/`
- Instrumented tests: `app/src/androidTest/kotlin/com/phairplay/`

Tests should make their intent clear through names and assertions. Add a short comment when a protocol invariant or edge case is not obvious.

```kotlin
/**
 * Test: RECORD triggers the onStreamingStarted callback.
 *
 * WHY: The UI switch from WaitingScreen to StreamingScreen depends on this
 * callback being called when RECORD is received.
 */
@Test
fun `RECORD triggers onStreamingStarted callback`() {
```

### RULE 4 — Security

- All **network input must be validated** before processing (length checks, format checks)
- **No hardcoded** IP addresses, port numbers (except standard AirPlay ports), or secrets
- **No unnecessary Android permissions** — use only what is in the manifest
- **All exceptions must be caught and logged** — never silently swallowed
- **Buffer overflow prevention**: always check array lengths before indexing

### RULE 5 — Performance

- **Video decode**: Hardware MediaCodec only — never add a software fallback
- **Thread safety**: Network I/O on IO dispatcher, UI updates on Main dispatcher
- **No blocking calls on Main thread** — use coroutines for all async work
- **No memory leaks**: all resources (MediaCodec, AudioTrack, sockets) must be released in the appropriate lifecycle method

### RULE 6 — Build Flavors

Changes that affect platform-specific behavior must be implemented in the appropriate flavor directory:
- `app/src/main/`: Shared code
- `app/src/googletv/`: Google TV specific (API 29+ only)
- `app/src/firetv/`: Fire TV specific (API 25+, no Google APIs)

If you use an API that requires API level 26+, wrap it in a version check:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    // API 26+ code
}
```

---

## Pull Request Checklist

Before submitting a PR, verify:

- [ ] Code follows the focused-change, security, lifecycle, and performance guidance above
- [ ] Relevant tests/checks pass, or the PR explains why a hardware/manual check is still needed
- [ ] Relevant flavor lint and build tasks pass
- [ ] User-visible strings and docs are updated
- [ ] Device-only or timing-sensitive behavior is clearly marked with the evidence collected
- [ ] PR description explains what changed and why

---

## Commit Message Style

Use conventional commits format:

```
type: short description (max 72 chars)

Longer explanation if needed (optional).
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `chore`

Examples:
```
feat: add NTP timing synchronization for A/V sync
fix: prevent crash when RTSP ANNOUNCE has empty SDP body
docs: add Fire TV sideloading instructions to README
test: add unit tests for AES-128-CTR decryption
```

---

## Questions?

Open a GitHub Issue with the `question` label, or start a Discussion.
