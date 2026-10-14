# Development APK signing / 开发 APK 签名

Debug and release variants use the existing shared Android signing configuration maintained for this workspace. Keep the signing identity stable so authorized devices can install updates without losing app data.

调试版和发布版使用本工作区既有的共享 Android 签名配置。保持签名身份稳定，以便已授权设备可以覆盖更新且不丢失应用数据。

## Handling / 保管要求

- Keep the keystore and passwords outside version control. Never commit, upload, attach, or copy them into the project tree.
- Build with the workspace's existing signing configuration. Do not generate a replacement key or silently switch to a machine-local default key.
- Do not print passwords, private key material, device-specific signing details, or certificate fingerprints in logs, documentation, issues, or release notes.
- If the configured key is unavailable, stop the signing step and recover the existing workspace-managed configuration; do not create a new identity.
- A debug APK remains a development artifact even when its signing identity is stable. Label it clearly and do not describe it as a store-ready production build.

- 密钥库和密码应保存在版本控制之外；不要提交、上传、附加或复制到项目目录。
- 使用工作区现有签名配置构建；不要生成替代密钥，也不要静默切换到机器本地默认密钥。
- 不要在日志、文档、Issue 或发布说明中输出密码、私钥材料、设备专用签名信息或证书指纹。
- 如果配置的密钥不可用，应停止签名并恢复原有工作区托管配置；不要创建新身份。
- 即使签名身份稳定，debug APK 仍是开发产物。应清晰标记，不能描述为可直接上架的正式包。
