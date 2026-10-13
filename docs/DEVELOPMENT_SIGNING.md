# Development APK Signing

All local debug APKs for Lenovo, OnePlus, and Android emulators use the same Lenovo-derived debug signing certificate. Its SHA-256 fingerprint is:

```text
81A2C4315AC57E191F171468E833B68027FAA83DCDC29715310B72FA2F037400
```

## Local setup

The keystore is private local material and must stay outside the Git checkout. Configure the path in the current Windows user's Gradle configuration (`%USERPROFILE%\.gradle\gradle.properties`):

```properties
phairplay.debugKeystore=F:/path/to/lenovo-debug.keystore
```

The file contains only a path, not the keystore or private key. The Android debug keystore uses the conventional local debug alias and password. The same configuration is read by Gradle builds launched from Android Studio and the command line. If the Gradle property is absent, Android Gradle Plugin's default debug key is used, so outputs will not share the canonical identity.

## Handling and recovery

- Keep the keystore in a user-controlled directory outside every repository. Do not upload it, add it to Git, attach it to a GitHub release, or store it in the project's `local.properties`.
- Maintain one backup copy of this long-lived signing file in the user's OneDrive sync folder at `CodexBackups/PhAirPlay-signing/`. The backup filename includes the certificate fingerprint. When the canonical key changes, verify the new fingerprint, add a new backup file, and update this document; do not silently overwrite a different signing identity.
- Back up the keystore securely. Losing it means future APKs cannot update installations signed with this identity; users would need to uninstall and lose app-local data.
- Changing the certificate also requires uninstalling existing installations signed with another key before installing the new build.
- This key is only for debuggable development APKs. Do not use it as a production/store signing key or configure it through the release `KEYSTORE_PATH` variables.
- To compare an APK certificate, use Android SDK Build Tools `apksigner verify --print-certs <apk>` and compare the SHA-256 fingerprint above. Never print or share the keystore password or private key.
