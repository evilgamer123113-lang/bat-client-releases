# Release `rust-v0.1.17`

- **Date:** 2026-11-27
- **GitHub Release:** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.17` (`--prerelease --latest=false`)
- **Companion Mod Bundles:** `bat-mods-v9` (unchanged)
- **CI Builds (`build/rust-native-0.1.17-release`):**
  - Codemagic (`rust-macos`): `6b0987ec2d51c71f6afab8e5`
  - CircleCI (`linux`): pipeline `c423cb9d-cb61-4db9-8f95-211a5bbc83f0` / workflow `e7e75cbc-e171-4b2b-9978-01d8ad378f91`

## Changes

1. **Preserve user-chosen `maxFps` across restarts (`launcher-rust/src-tauri/src/optimization.rs`):**
   - Only sets `maxFps` (`260` on modern/`26.x`, `999` on legacy) when `maxFps` is missing or invalid (`> 260` on modern/`26.x`), preserving valid user-configured frame rates (e.g. on Minecraft `26.3` Fabric) across launcher restarts.
2. **Full red `Stop` button (`launcher-rust/ui/game-launch.js`, `launcher-rust/ui/styles.css`):**
   - Renamed the active launch/game button text from `Kill Launch` / `Kill Minecraft` to `Stop`.
   - Overrode `border-color` and `box-shadow` on `#page-home #play-button.is-killing` so the green border/bevel no longer shows around the red button.

## Artifacts (`rust-v0.1.17`)

| Platform | File | Size (bytes) | SHA-256 |
|---|---|---|---|
| `windows-x86_64` | `Bat-Client-0.1.17-windows-x64-setup.exe` | `11000130` | `72abb94fcf404172d9705b15c73556179e96b600addb45fa67c57a18d09b0bca` |
| `darwin-aarch64` (updater) | `Bat-Client-0.1.17-macos-apple-silicon.app.tar.gz` | `13427122` | `14e15382e02d8b0dd5ca4b42af2514828c79e2fd24741ac0619d37b8cd5a1514` |
| `darwin-aarch64` (installer) | `Bat-Client-0.1.17-macos-apple-silicon.dmg` | `13689042` | `e45afa2147daf18f0e59e175c7445c3220bcfedc2babeedf9f742bad23f70686` |
| `darwin-x86_64` (updater) | `Bat-Client-0.1.17-macos-intel.app.tar.gz` | `14071773` | `5b8a4462cb56686699ab79f02e67297ccbd4ec34f222aa4b63b7a6fb0f6cdf18` |
| `darwin-x86_64` (installer) | `Bat-Client-0.1.17-macos-intel.dmg` | `14326674` | `2e9f45872775c9ddfdd41e2e764753c6225892767823ce845c230174e91336ae` |
| `linux-x86_64` (updater/installer) | `Bat-Client-0.1.17-linux-x64.AppImage` | `93547000` | `c206c32e4d1b1efb49ed2f2760348ce4176c1d835af99efffec0a6a4d0ab8d25` |
| `linux-x86_64` (.deb) | `Bat-Client-0.1.17-linux-x64.deb` | `15909534` | `048a7b4ddc76b3f172377976adb00225a351821de8f538915a888f956dfc795c` |
