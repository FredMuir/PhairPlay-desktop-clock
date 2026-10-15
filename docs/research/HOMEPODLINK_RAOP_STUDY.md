# HomePodLink: AirPlay audio output study

## Scope and source snapshot

This note records a read-only review of [HelloUnibody/HomePodLink](https://gitee.com/hello_unibody/home-pod-link) at Gitee `master` commit [`47829f6035a1cfb0b53211800cf0877b77896287`](https://gitee.com/hello_unibody/home-pod-link/tree/47829f6035a1cfb0b53211800cf0877b77896287). The repository metadata declares **GPL-2.0**. Its native RAOP implementation also identifies [AirFlash](https://github.com/Ding-Kyoma/AirFlash) as an implementation reference and describes parts as a line-by-line port; any future code reuse would therefore require a separate review of all applicable upstream licenses and compatibility with PhairPlay's Apache-2.0 license.

No source, vendored dependency, or build artifact from HomePodLink was copied into PhairPlay. This is a protocol and architecture study, not a claim that PhairPlay can send audio to a HomePod.

## What it uses

HomePodLink is an **Android AirPlay 2 audio sender using the RAOP service/session path**, aimed at current HomePod software. It discovers receivers advertising `_raop._tcp` with mDNS, then establishes an RTSP session to the receiver (the source documents TCP port 7000). The current native client source describes this HomePod-oriented flow:

1. Query receiver capabilities with `GET /info`.
2. Perform transient HomeKit Accessory Protocol setup using `/pair-pin-start` and `/pair-setup` with SRP-6a.
3. Negotiate event and audio streams with binary-plist `SETUP` requests, then start with `RECORD`/`FLUSH`.
4. Send PCM audio in RTP packets and terminate the session with `TEARDOWN`.
5. Maintain timing and recovery using NTP timing requests, periodic sync packets, packet resend requests, and feedback keepalives.

The source describes HAP record encryption with ChaCha20-Poly1305 on the control and media paths. It uses 44.1 or 48 kHz stereo PCM, with a bounded PCM ring buffer and a paced media thread. The timing, sync, retransmission, and feedback paths are kept distinct from audio production so media callbacks need not perform blocking network work.

## Implemented versus planned in that project

The repository README marks RAOP discovery, RTSP setup, encrypted packet sending, sync/timing, retransmission, and a 440 Hz test tone as implemented. It lists local-file decoding, system-audio capture through `MediaProjection`, metadata/artwork, progress, and playback controls as unfinished.

There is a material documentation difference at the reviewed revision: the README's protocol notes describe legacy RSA-OAEP/AES-CBC or clear audio, while the current `raop_client.c` header describes HAP/SRP setup and ChaCha20-Poly1305 for its HomePod path. This study uses the current source header for that path and does not treat the README's legacy notes as proof that those modes are implemented in the same session.

## What PhairPlay can learn

- Keep a possible sender as an independent, optional service. PhairPlay's existing AirPlay receiver must keep its current discovery, RTSP, and playback lifecycle.
- Keep audio capture/decoding separate from the network sender. A bounded PCM queue, paced packet output, interruptible waits, and per-session counters give the playback path clearer backpressure and recovery behavior.
- Treat timing, resend, and feedback as first-class parts of interoperability. Test clock drift, queue underruns, burst loss, reconnects, and teardown independently from codec conversion.
- Stage input sources: a generated test tone, then local media decoding, then system-audio capture. Each stage has different permissions, latency, and device-compatibility requirements.
- Do not ship a hard-coded pairing secret or infer security from a successful handshake. Pairing credentials, key lifetimes, lockout, and reset behavior need an explicit security design before any production sender feature.

PhairPlay already contains the opposite-direction AirPlay receiver and its own RTP, timing, buffering, and recovery code. These observations are useful as a checklist for future interoperability reviews; they do not justify importing a second protocol stack wholesale.

## PhairPlay decision

**Status: research complete; Android-to-HomePod output is not implemented.** Add an optional RAOP sender only after a separately scoped design and real HomePod/iOS/macOS compatibility plan. Implement it independently against documented protocol behavior unless license review and written permission establish a compatible way to reuse code. Do not add HomePodLink's bundled dependencies to PhairPlay.

## References

- [HomePodLink repository](https://gitee.com/hello_unibody/home-pod-link)
- [Reviewed RAOP client source at the pinned revision](https://gitee.com/hello_unibody/home-pod-link/blob/47829f6035a1cfb0b53211800cf0877b77896287/app/src/main/cpp/raop_client.c)
- [HomePodLink README at the pinned revision](https://gitee.com/hello_unibody/home-pod-link/blob/47829f6035a1cfb0b53211800cf0877b77896287/README.md)
- [Community-maintained AirPlay protocol documentation](https://openairplay.github.io/airplay-spec/)

---

# HomePodLink：AirPlay 音频输出研究

## 范围与源码版本

本文记录对 [HelloUnibody/HomePodLink](https://gitee.com/hello_unibody/home-pod-link) 的只读审查，仓库 `master` 固定在 Gitee 提交 [`47829f6035a1cfb0b53211800cf0877b77896287`](https://gitee.com/hello_unibody/home-pod-link/tree/47829f6035a1cfb0b53211800cf0877b77896287)。仓库元数据显示许可证为 **GPL-2.0**。其原生 RAOP 客户端还注明参考了 [AirFlash](https://github.com/Ding-Kyoma/AirFlash)，并称部分实现为逐行移植。因此，未来若要复用源码，必须另行审查所有上游许可证，并确认与 PhairPlay 的 Apache-2.0 许可证兼容。

没有将 HomePodLink 的源码、依赖或构建产物复制到 PhairPlay。本记录是协议和架构研究，不代表 PhairPlay 已能向 HomePod 发送音频。

## 使用的协议

HomePodLink 是一个**面向 Android、通过 RAOP 服务/会话路径工作的 AirPlay 2 音频发送端**，目标是较新的 HomePod 软件。它通过 mDNS 发现广播 `_raop._tcp` 的接收端，再与接收端建立 RTSP 会话（源码记录的 TCP 端口为 7000）。当前原生客户端描述的 HomePod 流程如下：

1. 使用 `GET /info` 查询接收端能力。
2. 通过 `/pair-pin-start` 和 `/pair-setup` 执行基于 SRP-6a 的临时 HomeKit Accessory Protocol 配对。
3. 使用二进制 plist 格式的 `SETUP` 协商事件流和音频流，再通过 `RECORD`/`FLUSH` 开始传输。
4. 以 RTP 数据包发送 PCM 音频，并通过 `TEARDOWN` 结束会话。
5. 使用 NTP 定时请求、周期同步包、丢包重发请求和反馈保活维护时序与恢复。

源码描述控制与媒体路径使用 HAP 记录层 ChaCha20-Poly1305 加密；音频采用 44.1 或 48 kHz 双声道 PCM，并以有界 PCM 环形缓冲区和定速媒体线程发送。时序、同步、重传和反馈与音频生产相互独立，因此媒体回调无需执行阻塞式网络操作。

## 该项目已实现与未完成的功能

仓库 README 将 RAOP 发现、RTSP 协商、加密数据包发送、同步/定时、丢包重传和 440 Hz 测试音标为已实现；本地音乐解码、通过 `MediaProjection` 捕获系统音频、媒体信息/封面、进度和播放控制仍列为未完成。

审查版本存在一处重要文档差异：README 的协议说明提到旧式 RSA-OAEP/AES-CBC 或明文音频，而当前 `raop_client.c` 文件头描述的 HomePod 路径使用 HAP/SRP 配对及 ChaCha20-Poly1305。本研究对该路径以当前源码说明为准，不把 README 中的旧式说明视为同一会话已实现的证据。

## PhairPlay 值得借鉴的做法

- 将可能新增的发送端设计为独立可选服务，不能影响 PhairPlay 现有接收端的发现、RTSP 和播放生命周期。
- 将音频捕获/解码与网络发送分离。有界 PCM 队列、定速包发送、可中断等待和会话计数器，能让背压和恢复行为更清楚。
- 将时序、重传和反馈作为互操作的核心部分。音频格式转换之外，还要分别验证时钟漂移、队列欠载、突发丢包、重连和会话结束。
- 分阶段支持音频来源：先生成测试音，再解码本地媒体，最后评估系统音频捕获。每个阶段的权限、延迟和设备兼容性不同。
- 不要发布硬编码的配对密钥，也不要把握手成功当作安全性证明。生产级发送功能需要明确设计配对凭据、密钥生命周期、失败锁定和重置流程。

PhairPlay 已有反方向的 AirPlay 接收器，也有自己的 RTP、时序、缓冲和恢复代码。这些观察适合作为今后互操作审查清单，不构成整体引入第二套协议栈的理由。

## PhairPlay 决策

**状态：研究完成；Android 向 HomePod 输出音频尚未实现。** 只有完成独立的功能设计并制定真实 HomePod/iOS/macOS 兼容性测试计划后，才考虑增加可选 RAOP 发送端。除非许可证审查和书面授权确认兼容的源码复用方式，否则应根据公开协议行为独立实现。不要将 HomePodLink 的依赖包加入 PhairPlay。

## 参考资料

- [HomePodLink 仓库](https://gitee.com/hello_unibody/home-pod-link)
- [固定版本的 RAOP 客户端源码](https://gitee.com/hello_unibody/home-pod-link/blob/47829f6035a1cfb0b53211800cf0877b77896287/app/src/main/cpp/raop_client.c)
- [固定版本的 HomePodLink README](https://gitee.com/hello_unibody/home-pod-link/blob/47829f6035a1cfb0b53211800cf0877b77896287/README.md)
- [社区维护的 AirPlay 协议资料](https://openairplay.github.io/airplay-spec/)
