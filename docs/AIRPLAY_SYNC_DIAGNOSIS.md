# AirPlay multi-room synchronization notes

## Diagnosis

On one Android tablet, a subset of tracks joined a multi-room session noticeably late, then gradually aligned with other AirPlay receivers. Device logs showed that PTP updates continued, but the first eligible AAC frame arrived after its mapped PTP/RTP presentation target. Starting a cold Android `AudioTrack` added further delay. The initial playback phase error could therefore be large even while the shared clock was locked.

The receiver uses a buffered AirPlay audio path and Android `AudioTrack`. The sender-provided PTP/RTP anchor defines the intended timeline, while decoding, queueing, thread scheduling, and the Android audio path add device-dependent latency. A correct PTP mapping alone does not guarantee that the first sample reaches the speaker on time.

Other useful diagnostic findings from that device included duplicate privileged PTP helper processes and a startup sample rejected by an overly narrow discontinuity guard. The helper now uses a kernel file lock to prevent duplicate instances, and phase history is reset at stream epochs.

## Mitigation and evidence

- Wait for two advancing `AudioTimestamp` samples before trusting phase measurements, so a synthetic timestamp during route warm-up does not seed correction.
- When the first stable sample shows that playback has missed its timeline by a large amount, skip the already-late source interval once, bounded to one second. This accepts a short loss at the beginning of a track to restore alignment sooner.
- Let the existing position servo handle the residual error, using faster catch-up for large phase errors and a finer limit near lock.
- Keep the converter rate clamp consistent with the controller limit, and reset servo history on rate/anchor epoch changes.
- Compare PTP mapping, scheduled-start error, `AudioTimestamp`, queue depth, packet arrival timing, and listening results as separate measurements.

One device capture showed a startup phase error of roughly 323 ms. After allowing the first stable measurement and skipping late media time, the measured error converged to within a few milliseconds in about 30 seconds, and correction later returned to zero. This is a single-device software timing observation, not a universal latency guarantee or independent acoustic measurement. The startup-skip change still requires device and group-playback validation in the build that contains it.

## Protocol comparison

The reference receivers advertised realtime audio type 96, while this Android implementation negotiated buffered type 103. Advertising type 96 without implementing its PTP-scheduled output path would be unsafe. Capture and validate the sender's complete setup, anchor, and packet timing before changing supported formats.

Useful public references:

- [airplay-esp32 realtime receiver](https://github.com/rbouteiller/airplay-esp32/blob/main/main/audio/audio_stream_realtime.c)
- [airplay-esp32 audio timing](https://github.com/rbouteiller/airplay-esp32/blob/main/main/audio/audio_timing.c)
- [airplay-esp32 multi-room sync issue](https://github.com/rbouteiller/airplay-esp32/issues/54)
- [AirPlay CLI timing design](https://github.com/music-assistant/airplay-cli/blob/main/DESIGN.md)
- [Shairport Sync AirPlay 2 requirements](https://github.com/mikebrady/shairport-sync/blob/master/AIRPLAY2.md)
- [PhairPlay](https://github.com/mazer666/PhairPlay) provides the upstream Android protocol baseline.

These projects provide implementation comparisons; their behavior does not prove the timing characteristics of a particular Android device or receiver.
