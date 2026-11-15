# App Depot for macOS

Installs and updates my macOS apps (Padlet, LaunchApp, Quickcast, ZenTask; shows Wisp and DevKit) from
the live catalog at `https://vuhpquang.com/api/depot/catalog?platform=macos`. Every download is checked
(size, SHA-256, bundle id, `codesign --strict`, and the signer: the installed copy's designated
requirement, or the catalog's `signer` for a first install) before it replaces anything.

Swift Package, macOS 26+. The shared About / Settings / self-update code is
[app-shell](https://github.com/vuhpquang/app-shell), a submodule at `Vendor/app-shell`.

```sh
git submodule update --init --recursive
swift build && swift test
./build.sh                 # build/App Depot.app + build/AppDepot-X.Y.Z-macos.zip
```

---

Made by Ha Phung Quang Vu · [vuhpquang.com](https://vuhpquang.com) ·
[GitHub](https://github.com/vuhpquang) · [LinkedIn](https://www.linkedin.com/in/vuhpquang/)

© 2026 Ha Phung Quang Vu
