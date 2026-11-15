# App Depot

Install and update my apps in one place. App Depot reads the live catalog at
`https://vuhpquang.com/api/depot/catalog` and installs or updates each app from its GitHub release.

- `android/` — the Android app (Kotlin, Jetpack Compose, `:app` module)
- `docs/superpowers/` — design spec and implementation plans

## Build (Android)

```sh
cd android
export JAVA_HOME=/Users/quangvu/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Release builds are signed with the key in `~/.config/app-depot-signing/` (create it once with
`android/scripts/make-keystore.sh`); without it release falls back to the debug key.

---

Made by Ha Phung Quang Vu · [vuhpquang.com](https://vuhpquang.com) ·
[GitHub](https://github.com/vuhpquang) · [LinkedIn](https://www.linkedin.com/in/vuhpquang/)

© 2026 Ha Phung Quang Vu
