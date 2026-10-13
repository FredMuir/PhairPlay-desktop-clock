# Development APK Signing / 开发 APK 签名

**EN:** Lenovo, OnePlus and emulator debug APKs use one Lenovo-derived debug signing certificate. Its SHA-256 fingerprint is shown below.
**中文：** Lenovo、OnePlus 和模拟器的调试 APK 共用一张源自 Lenovo 设备的调试签名证书，SHA-256 指纹如下。

```text
81A2C4315AC57E191F171468E833B68027FAA83DCDC29715310B72FA2F037400
```

## Local setup / 本地配置

**EN:** Keep the keystore outside the Git checkout. Set its path in the current Windows user's Gradle configuration at `%USERPROFILE%\.gradle\gradle.properties`:
**中文：** 密钥库应放在 Git 工作区之外。在当前 Windows 用户的 Gradle 配置 `%USERPROFILE%\.gradle\gradle.properties` 中设置文件路径：

```properties
phairplay.debugKeystore=F:/path/to/lenovo-debug.keystore
```

**EN:** This property contains only a path; it does not contain the key. Android Studio and command-line Gradle builds both read it. If neither this property nor `PHAIRPLAY_DEBUG_KEYSTORE` is set, Android Gradle Plugin's default debug key is used and outputs will not share the canonical identity.
**中文：** 该属性只记录路径，不包含密钥。Android Studio 和命令行 Gradle 构建都会读取它。如果未设置此属性或环境变量 `PHAIRPLAY_DEBUG_KEYSTORE`，Android Gradle Plugin 会使用默认调试密钥，构建产物将无法共用指定签名。

## Handling and recovery / 保管与恢复

- **EN:** Keep the keystore in a user-controlled location outside every repository. Never commit or upload it, attach it to a GitHub release, or place it in `local.properties`.
  **中文：** 将密钥库保存在用户控制且位于所有版本库之外的位置。不要提交或上传密钥，不要把它附加到 GitHub Release，也不要放进 `local.properties`。
- **EN:** Maintain exactly one backup copy in the user's OneDrive sync folder under `CodexBackups/PhAirPlay-signing/`. Its filename includes the certificate fingerprint. If the canonical key changes, verify the new fingerprint, add a separately named backup and update this document; never overwrite a different identity silently.
  **中文：** 在用户 OneDrive 同步目录的 `CodexBackups/PhAirPlay-signing/` 下维护一份备份，文件名包含证书指纹。如果指定密钥变更，应先核对新指纹，再使用不同文件名新增备份并更新本文档；不要静默覆盖不同签名身份。
- **EN:** Losing the key prevents updates to installations signed with it. Reinstalling with another key requires uninstalling first and removes app-local data.
  **中文：** 密钥丢失后将无法更新由该密钥签名的安装包。改用其他密钥时需先卸载旧应用，并会删除应用本地数据。
- **EN:** This key is only for debuggable development APKs. Never use it as a production/store signing key or through release `KEYSTORE_PATH` variables.
  **中文：** 此密钥仅用于可调试的开发 APK。不得将其用作正式版/商店签名，也不要通过发布构建的 `KEYSTORE_PATH` 变量使用。
- **EN:** Compare an APK with `apksigner verify --print-certs <apk>` and match the SHA-256 fingerprint above. Do not share the keystore password or private key.
  **中文：** 使用 `apksigner verify --print-certs <apk>` 检查 APK，并对照上方 SHA-256 指纹。不要分享密钥库密码或私钥。
