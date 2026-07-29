# ohmyssh (Kotlin Multiplatform)

Simple, reliable SSH/SFTP client. A 1:1 port of the Flutter app to Kotlin
Multiplatform + Compose Multiplatform.

Targets: **Android · iOS · macOS · Windows · Linux**.

## Compatibility with the Flutter app

* **Vault format is identical** — `ohmyssh.vault` (JSON envelope,
  PBKDF2-HMAC-SHA256 · 120 000 rounds · AES-256-GCM). A vault created by the
  Flutter app opens here with the same master password, and vice versa.
* **Vault location is identical per platform**, so an existing vault is picked
  up in place:
  * macOS: `~/Library/Application Support/com.example.ohmyssh/ohmyssh.vault`
  * Linux: `$XDG_DATA_HOME/com.example.ohmyssh/ohmyssh.vault`
  * Windows: `%APPDATA%\com.example\ohmyssh\ohmyssh.vault`
  * Android/iOS: the app's private files / Application Support directory
* **Host key pins are identical** — OpenSSH-style `SHA256:<base64>` over the
  host key blob, stored in the vault, so pinned systems stay pinned.

## Architecture

```
shared/        Kotlin Multiplatform module — all app code and Compose UI
  commonMain/    models · vault crypto · store · session/ssh/serial logic ·
                 terminal emulator · every page and component
  jvmShared/     code shared by Android + desktop (sshj SSH engine, JDK crypto)
  androidMain/   USB-host serial scanner, Keystore secure storage
  desktopMain/   jSerialComm + /dev sweep serial scanner, desktop keychains,
                 desktop entry point (macOS/Windows/Linux)
  iosMain/       Apple file/keychain/crypto actuals (SSH engine pending)
androidApp/    Android application (activity + session foreground service)
iosApp/        Xcode project embedding the shared framework
```

Key dependencies: sshj (SSH/SFTP on JVM targets), jSerialComm + 
usb-serial-for-android (serial), cryptography-kotlin (vault crypto),
FileKit (file dialogs), multiplatform-settings (preferences). The terminal is
an in-repo VT100/xterm emulator rendered with Compose.

Serial support mirrors the Flutter app's current state: the layer is wired up
end to end but still being finished; iOS has no serial at all (platform
limitation).

## Build

```
./gradlew :shared:run                    # desktop app (current OS)
./gradlew :androidApp:assembleDebug      # Android APK
open iosApp/iosApp.xcodeproj             # iOS (build & run from Xcode)
./gradlew :shared:desktopTest            # tests (vault format compatibility)
./gradlew :shared:packageDistributionForCurrentOS   # dmg / msi / deb
```
