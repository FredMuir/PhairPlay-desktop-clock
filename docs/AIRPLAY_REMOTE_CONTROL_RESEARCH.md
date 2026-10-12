# AirPlay remote-control research

## Protocol evidence

Shairport Sync's [AirPlay 2 remote-control discussion](https://github.com/mikebrady/shairport-sync/discussions/2262)
describes the working reverse-control message on the session event channel:

```text
POST /command RTSP/1.0
Content-Type: application/x-apple-binary-plist

{ "type": "sendMediaRemoteCommand", "modernMediaRemoteCommand": <integer> }
```

The command values follow Apple's MediaRemote numbering:

| Action | Value |
|---|---:|
| Play | 0 |
| Pause | 1 |
| Toggle play/pause | 2 |
| Stop | 3 |
| Next track | 4 |
| Previous track | 5 |

The Shairport Sync author reports testing these commands with an iPhone 15 Pro Max and
Shairport Sync 5.2.2.2. The same discussion warns that the command must go through the event
channel opened for the main audio session. A separate remote-control stream uses a different
protocol and is not interchangeable. This is useful implementation evidence, not proof that every
iOS/macOS version behaves identically.

For older AirPlay senders, the [Unofficial AirPlay specification](https://openairplay.github.io/airplay-spec/audio/remote_control.html)
documents DACP commands such as `playpause`, `nextitem`, and `previtem`. DACP discovery uses
`_dacp._tcp` and commands carry the sender's `Active-Remote` token. This remains the fallback path.

## PhairPlay implementation

- `AirPlayReceiver.startEventChannel()` serves the encrypted session event channel and sends its
  port in the setup response, including transient-paired buffered-audio sessions.
- `AirPlayReceiver.sendEventRemoteCommand()` maps play/pause to 2, next to 4, and previous to 5,
  then sends the binary-plist command on that event connection.
- If a sender advertises separate Play/Pause commands but not Toggle, PhairPlay selects Play (0)
  or Pause (1) from the sender-reported playback rate, and the card enables that control accordingly.
- Outgoing event-channel commands are serialized so their CSeq values follow actual socket-write order.
- `sendRemoteCommand()` falls back to `DacpClient` when no usable event channel is connected.
- The playback controls are exposed only when the event channel or DACP path is available.

These mappings agree with the community protocol evidence. Until a live iOS session confirms the
receiver-side behavior, treat command delivery logs as transport evidence only; they do not prove
that the sender applied the command.

## Sender state and supported-command announcements

Music Assistant's [AirPlay CLI design notes](https://github.com/music-assistant/airplay-cli/blob/main/DESIGN.md)
report that Apple senders also post `updateMRSupportedCommands` and `updateMRPlaybackState` on the
event channel. The supported-command payload is under
`params.mrSupportedCommandsFromSender`; the entries are serialized command-info property lists.
OpenAirplay's [receiver event handler](https://github.com/openairplay/airplay2-receiver/blob/master/ap2-receiver.py)
confirms that each supported-command item is itself a serialized binary plist, with command ID and
enabled fields. A separate [wire example](https://gist.github.com/MinshuG/20225d1923999c980d8545f7ac46fe6f)
lists Apple's command IDs and command-info field names. PhairPlay now parses these nested property
lists and dims card buttons that the sender marks unsupported. It reads `PlaybackRate` from the
`updateMRNowPlayingInfo` envelope to reflect actual play/pause state instead of optimistically
flipping the icon after a tap. The distinct `updateMRPlaybackState` payload remains unparsed until a
captured sender payload establishes its exact shape.

## Device verification checklist

During a live iOS audio-only AirPlay session, confirm:

1. The type-103 SETUP response contains the event port and the sender connects to it.
2. Logs show `Encrypted AirPlay event channel connected` and remote-control availability becomes
   true, making the controls visible.
3. Tapping play/pause, next, and previous emits values 2, 4, and 5 respectively.
4. The iOS sender actually pauses/resumes or changes track. A successful socket write alone is not
   enough to call the control feature verified.

## Volume expectations

AirPlay control-channel `SET_PARAMETER volume` carries the sender's local output level and
`GET_PARAMETER volume` queries the receiver's current value. Shairport Sync's [sample local volume
commands](https://github.com/mikebrady/shairport-sync/blob/master/documents/sample%20dbus%20commands)
explicitly note that receiver-side volume changes are local and do not move the source device's
volume slider. PhairPlay therefore synchronizes inbound AirPlay volume with Android's
`STREAM_MUSIC` volume and its own playback-card slider; Android hardware-volume changes update the
card and receiver state, but should not be represented as changing the iOS sender's slider.

The [OpenAirplay AirPlay 2 receiver](https://github.com/openairplay/airplay2-receiver) routes
`SET_PARAMETER` to an OS-specific volume backend, and its project history documents removing
platform-specific soft-volume behavior as a portability issue. The Rust
[openairplay2](https://github.com/st3fan/openairplay2) receiver exposes a selectable ALSA mixer
backend as an alternative to software sample scaling. These implementations reinforce that the
volume command must reach the actual output backend; changing only receiver metadata is not enough.
PhairPlay uses Android's shared media stream as that backend and leaves each `AudioTrack` at unity
gain to avoid applying the same volume twice. The service listens for media-volume broadcasts so
hardware/system changes continue to update AirPlay's reported value when the Activity is not visible.

On a reference Android device, Android media volume was changed both with the app foregrounded and
after returning to the launcher. The service logged the resulting Android-index-to-AirPlay-dB
mapping in both cases. This confirms the Android-originated path and background listener on that
configuration; it does not verify an active iOS `SET_PARAMETER` update or audible level change
during AirPlay playback. Those require a live sender session.
