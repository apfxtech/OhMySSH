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
artwork/       app icon source + the generator that fans it out per platform
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

## Release

Pushing a tag that contains a semantic version — `1.6.1`, `v1.6.1`,
`alpha-1.6.1` — builds all five platforms and publishes a GitHub prerelease
([.github/workflows/auto-release.yml](.github/workflows/auto-release.yml)):

```
ohmyssh_1.6.1_android_universal.apk    signed release APK (no ABI splits: no native code)
ohmyssh_1.6.1_ios_arm64.ipa            unsigned IPA
ohmyssh_1.6.1_linux_x64                self-extracting executable, bundled runtime
ohmyssh_1.6.1_macos_arm64.dmg          drag-to-Applications DMG
ohmyssh_1.6.1_windows_x64.exe          self-extracting executable, bundled runtime
ohmyssh_1.6.1_windows_x64_raw.zip      the same app image, unwrapped
```

Every asset comes from a script under
[.github/scripts/build/](.github/scripts/build/) that also runs by hand:

```
OHMYSSH_VERSION_NAME=1.6.1 .github/scripts/build/macos.sh
```

`ohmysshVersionName` / `ohmysshVersionCode` in `gradle.properties` are the
version of record — they set the Android version, the desktop bundle version and
the version the app itself reports. The workflow overrides them from the tag,
then commits the tag's version back to the default branch. Android signing needs
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
`ANDROID_KEY_PASSWORD` as repository secrets; without them the Android job
fails. Local release builds fall back to the debug key.

## App icon

`artwork/app-icon-source.png` is the only file to edit; everything else is
generated from it and checked in:

```
python3 artwork/generate-app-icons.py    # needs Pillow
```

Each platform gets the shape it expects, so no system ever masks an
already-rounded image: Android an adaptive icon (background colour + foreground
+ monochrome for themed icons), iOS a full-bleed 1024 with dark and tinted
variants, macOS an `.icns` on Apple's inset grid, Windows a multi-size `.ico`,
Linux a PNG. Under 40px the "SSH" lettering is dropped, since it only smudges at
that size. `artwork/preview-*.png` show the result under each system's mask.
