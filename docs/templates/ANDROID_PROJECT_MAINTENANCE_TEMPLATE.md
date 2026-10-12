# Android Project Maintenance Template

> Copy this file into a project and replace every `[placeholder]`. Delete sections that do not apply. Keep project-specific commands, device details, and decisions in the project copy; this template contains reusable practices only.

Last reviewed: `[YYYY-MM-DD]`
Maintainers: `[team or owner]`

## 1. Project snapshot

| Item | Current value |
|---|---|
| Purpose and supported users | `[one or two sentences]` |
| Supported Android versions / form factors | `[API range; phone, tablet, TV, foldable, etc.]` |
| Current stable release | `[version, build, release link]` |
| Current development baseline | `[branch/commit or version]` |
| Main modules and their responsibilities | `[module → responsibility]` |
| Build variants and their purpose | `[variant → purpose]` |
| Known risks or deferred work | `[risk, impact, owner, next review]` |

Keep the last accepted release separate from work in progress. A successful build, install, or screenshot only verifies that step; state explicitly what behavior was exercised and on which configuration.

## 2. Local development and reproducibility

- Required tools: `[Android Studio / JDK / SDK / NDK versions, if pinned]`.
- Standard build command: `[Gradle Wrapper command and variant]`.
- Standard validation commands: `[targeted checks]`.
- Local setup: `[link to setup guide; keep secrets and machine-specific paths out of Git]`.
- CI workflow: `[link and required checks]`.

Use the repository's Gradle Wrapper and documented toolchain. Keep credentials, signing keys, local cache paths, and private device data out of tracked files. If a local workaround is needed, document how another developer can reproduce the environment without relying on that machine's cache.

## 3. Architecture and critical boundaries

| Area | Owner / entry point | Boundary to preserve |
|---|---|---|
| UI and navigation | `[screen / module]` | Render state and report user events; avoid owning durable application state. |
| Domain and data | `[repository / use case / module]` | Keep data access and business rules behind explicit interfaces. |
| Background work and services | `[service / worker]` | Handle lifecycle restart, cancellation, and process death deliberately. |
| Media / hardware / network protocol (if applicable) | `[component]` | Separate transport/control state from output timing and device-specific behavior. |
| Persistence and settings | `[component]` | Define ownership, migration behavior, and defaults. |

Prefer clear responsibilities and narrow interfaces over splitting modules for its own sake. Android components can be recreated or stopped by the OS; keep durable state outside short-lived Activities, Fragments, and UI elements. Keep one authoritative owner for each shared state, and have screens observe immutable state and send events through defined interfaces.

## 4. Change workflow

1. Read this guide, the architecture overview, relevant decision records, and the issue or bug report. Inspect `git status` and preserve existing user changes.
2. Reproduce the problem or capture the current behavior, affected versions/devices, logs, and a measurable baseline.
3. Identify the owning layer and downstream effects. Keep UI, persistence, background service, network/protocol, and media-output changes separable where practical.
4. State the change's hypothesis, expected result, risks, and rollback path before adjusting critical behavior.
5. Make a small, reviewable change. Update API, migration, user-facing strings, accessibility, and documentation where affected.
6. Run checks matched to the changed behavior (Section 5). Record exact commands, environment, results, and gaps.
7. Update release notes and project status. Do not describe untested behavior as verified.

For real-time audio/video or synchronized playback, distinguish clock estimation, scheduling, buffering, decoding, and physical output latency. Record units and sample windows; separate startup offset from steady-state drift. Change one timing mechanism at a time and preserve a rollback point.

## 5. Validation matrix

| Change type | Minimum relevant evidence | Not proven by this evidence |
|---|---|---|
| Pure UI/layout | Build; preview or screenshot on representative size, orientation, font scale, and input method | Service, network, or media behavior |
| State/business logic | Focused unit tests or deterministic fake-based checks; failure and boundary cases | Device lifecycle or external integration behavior |
| Persistence/schema | Migration and existing-data checks; install/upgrade path where relevant | Unrelated UI or service behavior |
| Background service/lifecycle | Start/stop, process recreation, permissions, notification, and OS-version checks as applicable | External protocol interoperability unless exercised |
| Network/protocol | Valid, malformed, timeout, retry, and compatibility cases; sanitized traces | Real device interoperability unless exercised |
| Audio/video/timing | Controlled stream plus device output checks; startup and steady-state measurements across supported routes | Other devices/routes not covered |
| Release/signing | Variant, package/application ID, version, signature, and install/upgrade verification | Runtime correctness of features not exercised |

Prefer deterministic fakes for logic boundaries and real devices for OS, hardware, and output behavior. Choose representative configurations based on the support matrix. Note skipped checks and why; do not treat a green build as a full regression pass.

## 6. UI, media, and accessibility checks

- Derive related UI surfaces from a single state owner; distinguish the displayed state from a command that was merely requested.
- Avoid expensive work (image decoding, player/service recreation, repeated subscriptions) on frequent metadata or progress updates.
- Check window resizing, rotation, large screens, font scaling, localization length, touch target size, TalkBack labels, and keyboard/remote focus when relevant.
- Treat artwork and other media metadata as updates with identity and lifecycle; reuse unchanged content and handle missing or late data.
- Make unsupported actions visibly unavailable and report command failures without silently displaying success.

## 7. Release and handoff record

For each release or meaningful handoff, record:

- Version / build identifier and source revision: `[value]`
- Build variant and artifact location: `[value]`
- Signing and package identity verified: `[method/result; never include secrets]`
- Devices, OS versions, routes, and scenarios exercised: `[value]`
- Checks passed, failed, skipped, and remaining limits: `[value]`
- Known issues, rollback procedure, and follow-up owner: `[value]`

## 8. Prioritized maintenance backlog

| Priority | Item | User or engineering impact | Evidence / acceptance criteria | Owner / review date |
|---|---|---|---|---|
| P0 | `[release blocker, data loss, security, or core-path failure]` | `[impact]` | `[reproducible pass condition]` | `[owner/date]` |
| P1 | `[important reliability, accessibility, compatibility, or recurring defect]` | `[impact]` | `[observable completion condition]` | `[owner/date]` |
| P2 | `[maintainability, performance, or quality improvement]` | `[impact]` | `[measurable or reviewable condition]` | `[owner/date]` |

Prioritize by user impact, likelihood, and scope of failure; attach evidence and a completion condition to each item. Revisit priorities when the supported devices or product risks change.

## 9. Reference projects and guidance

Use examples to learn a pattern that fits the problem; do not transplant an entire sample architecture without checking the project's constraints.

- [Android app architecture guide](https://developer.android.com/topic/architecture): lifecycle-aware components, separation of concerns, and state ownership.
- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations): practical guidance for UI, data, testing, and performance.
- [Now in Android](https://github.com/android/nowinandroid): a maintained sample for Kotlin, Compose, modularization, and unidirectional data flow.
- [Android Architecture Samples](https://github.com/android/architecture-samples): compare architectural approaches and testing seams using a common example.
- [Jetpack Compose Samples](https://github.com/android/compose-samples): Compose patterns and examples; useful for UI exploration even when a project uses Views.
- [AndroidX Media](https://github.com/androidx/media): Media3 libraries and demos for playback, sessions, and media integration.
- [Adaptive app guidance](https://developer.android.com/develop/adaptive-apps): design for changing window sizes and Android form factors.
- [Screenshot testing guidance](https://developer.android.com/training/testing/ui-tests/screenshot): visual regression checks for stable, representative UI states.
