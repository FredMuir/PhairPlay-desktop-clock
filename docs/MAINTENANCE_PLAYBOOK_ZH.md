# PhairPlay 维护手册

本文记录可复用的 Android 开发与维护流程。当前功能和版本状态以 `README.md`、`CHANGELOG.md`、`docs/TESTING.md` 与 `docs/spec/PROJECT_PLAN.md` 为准。

## 项目结构

| 领域 | 主要位置 | 维护要点 |
|---|---|---|
| RTSP、配对与 AirPlay 会话 | `app/src/main/kotlin/com/phairplay/airplay/` | 核对消息顺序、字段与兼容分支；日志不得包含密钥或配对材料 |
| mDNS 与接收服务 | `MdnsService.kt`、`AirPlayReceiver.kt`、`PhairPlayService.kt` | 分别考虑屏幕休眠、网络恢复、应用升级与服务生命周期 |
| 音频输出与同步 | `AudioStreamServer.kt`、`BufferedAudioServer.kt`、`PtpClock.kt` | 区分 PTP 时钟、远端锚点、解码队列及设备输出延迟 |
| 设置与界面 | `app/src/main/kotlin/com/phairplay/ui/`、`app/src/main/res/` | 让界面状态来自服务状态；用户可见文案同步维护翻译 |
| 规格与决定 | `docs/spec/`、`docs/decisions/` | 记录原因、证据、限制与回退办法 |

## 推荐工作流程

1. 检查实际仓库根目录、当前分支和 `git status --short`；保留现有未提交工作，不用清理命令代替审查。
2. 限定改动层。界面工作尽量不触及协议与音频时序；协议工作记录影响的发送端、会话阶段和回滚方式。
3. 先复现并测量，再调参。同步问题分别观察 PTP 偏差、起播/位置误差、音频队列和输出结果。
4. 每个可安装版本递增 `versionCode`，在 `CHANGELOG.md` 中写清代码变化和验证范围。
5. 按改动风险选择检查。构建成功只证明构建任务通过，不代表真实播放或多房间同步通过。
6. 记录 APK、构建变体和验证范围；避免把本机路径、设备序列号、网络地址、账户信息、密钥和个人调试记录写入通用文档。

## 构建与签名

优先使用仓库 Gradle Wrapper、项目指定的 JDK 和 Android SDK：

```powershell
.\gradlew.bat :app:assembleGoogletvDebug
```

APK 默认位于 `app/build/outputs/apk/googletv/debug/`。具体任务名以项目 Gradle 配置为准。发布构建应依照项目的签名配置进行，并在提交前确认密钥库、密码、签名属性和生成 APK 均未纳入版本控制。

Android 覆盖安装要求签名证书匹配；调试密钥只适用于本地开发，不应发布到仓库。验证签名时使用 Android SDK 中的 `apksigner`，不要公开设备专用的调试证书指纹。

## 多房间音频同步

- 将时钟估计、远端起播时间、解码缓冲、`AudioTrack` 排队和实际输出延迟分开诊断。
- 区分启动误差与稳态漂移；每次先验证一项假设，避免同时叠加固定延迟、队列阈值和伺服参数。
- PTP 应保持单一权威实例；避免多个辅助进程竞争相同时间服务。
- 日志应使用稳定单位并限频；不要因界面刷新而重建时钟、播放器或专辑图。
- 报告结果时说明设备、系统、流格式与验证范围；一次听感或软件时间戳都不是所有设备的声学保证。

## 媒体状态与界面

- 专辑图以歌曲身份为更新边界；无变化时复用当前图，避免元数据心跳造成重绘。
- 音量更新要标记来源，避免设备、AirPlay 与本地播放器之间产生反馈循环。
- 媒体控件反映实际支持的命令和播放状态；请求已发送不代表执行成功。
- 平板和 TV 界面应适应窗口尺寸、方向、字体缩放、触控目标、无障碍描述和遥控器焦点。
- 颜色、间距、圆角等共用样式尽量集中维护；界面预览、协议测试和实机播放分别报告结果。

## 可借鉴的 Android 案例

这些项目适合参考设计方法，不要求把其完整架构搬入当前应用：

| 项目 | 可借鉴内容 |
|---|---|
| [Now in Android](https://github.com/android/nowinandroid) | UI、数据层职责划分与模块化学习材料 |
| [Android Architecture Samples](https://github.com/android/architecture-samples) | 架构对比、测试替身和测试分层 |
| [Jetpack Compose Samples](https://github.com/android/compose-samples) | 自适应布局、导航和界面组件示例 |
| [AndroidX Media](https://github.com/androidx/media) | 后台播放服务与媒体会话边界 |
| [Android Views 架构建议](https://developer.android.com/topic/architecture/views/recommendations-views) | 现有 XML/Views 工程的渐进整理 |
| [Android 大屏 UI 指引](https://developer.android.com/guide/topics/large-screens/user-interface) | 大屏布局、焦点和无障碍行为 |

## 参考依据

- [Android 架构指南](https://developer.android.com/topic/architecture)
- [Android 自适应应用指南](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps)
- [Android UI 截图测试](https://developer.android.com/training/testing/ui-tests/screenshot)
